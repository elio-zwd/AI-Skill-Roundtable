package com.elio.skillroundtable.network

import android.content.Context
import com.elio.skillroundtable.network.keys.ApiKeyLease
import com.elio.skillroundtable.network.retry.ApiCallFailure
import com.elio.skillroundtable.network.retry.ApiExecutionException
import com.elio.skillroundtable.network.retry.ApiRetryDecision
import com.elio.skillroundtable.network.retry.ApiRetryPolicy
import com.elio.skillroundtable.network.retry.safeCategory
import com.elio.skillroundtable.roundtable.DefaultDelayProvider
import com.elio.skillroundtable.roundtable.DelayProvider
import com.elio.skillroundtable.roundtable.RequestBudgetTracker
import com.elio.skillroundtable.telemetry.CloudInteractionRequestPolicy
import com.elio.skillroundtable.telemetry.CloudInteractionSettings
import com.elio.skillroundtable.telemetry.InteractionChainStore
import com.elio.skillroundtable.telemetry.PrivacySafeLogger
import com.elio.skillroundtable.telemetry.TelemetryRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class StreamedInteraction(
    val id: String,
    val outputText: String
)

/**
 * Interactions API 的 SSE 客户端。
 *
 * 仅把 model_output 步骤中的 text delta 暴露给界面，thought 等内部步骤不会进入聊天记录。
 * 每次重试开始前都会通知调用方重置当前 Pending 文本，避免不同尝试的内容相互拼接。
 */
object InteractionStreamingClient {
    private const val TAG = "InteractionStreaming"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
    private const val API_REVISION = "2026-05-20"
    private const val MAIN_ANSWER_PREFIX = "MainAnswer-"
    private const val CONTINUE_ANSWER_PREFIX = "ContinueAnswer-"
    private const val MIN_UI_UPDATE_INTERVAL_NS = 75_000_000L
    private const val MIN_UI_UPDATE_GROWTH = 64

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
    }

    suspend fun createInteraction(
        context: Context,
        request: CreateInteractionRequest,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String,
        delayProvider: DelayProvider = DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0,
        onAttemptStarted: suspend () -> Unit = {},
        onTextUpdate: suspend (String) -> Unit = {}
    ): StreamedInteraction {
        TelemetryRepository.init(context)
        val cloudEnabled = CloudInteractionSettings.isEnabled(context)
        val characterId = interactionCharacterId(operationName)
        val requestedPreviousId = request.previousInteractionId
            ?.takeIf(String::isNotBlank)
            ?: if (cloudEnabled && operationName.startsWith(MAIN_ANSWER_PREFIX) && characterId != null) {
                InteractionChainStore.get(sessionId, characterId)
            } else {
                null
            }
        val cloudPolicy = CloudInteractionRequestPolicy.apply(
            enabled = cloudEnabled,
            requestedStore = request.store,
            requestedPreviousInteractionId = requestedPreviousId
        )
        val streamingRequest = request.copy(
            store = cloudPolicy.store,
            stream = true,
            previousInteractionId = cloudPolicy.previousInteractionId
        )

        val result = executeWithBudgetAndRetry(
            context = context,
            sessionId = sessionId,
            attemptPlan = attemptPlan,
            tracker = tracker,
            operationName = operationName,
            delayProvider = delayProvider,
            isRequired = isRequired,
            reserveForRequired = reserveForRequired,
            onAttemptStarted = onAttemptStarted
        ) { lease ->
            streamSingleAttempt(
                apiKey = lease.secret,
                request = streamingRequest,
                onTextUpdate = onTextUpdate
            )
        }

        if (
            cloudPolicy.store &&
            characterId != null &&
            CloudInteractionSettings.isEnabled(context)
        ) {
            InteractionChainStore.put(sessionId, characterId, result.id)
        }
        return result
    }

    private suspend fun executeWithBudgetAndRetry(
        context: Context,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String,
        delayProvider: DelayProvider,
        isRequired: Boolean,
        reserveForRequired: Int,
        onAttemptStarted: suspend () -> Unit,
        block: suspend (ApiKeyLease) -> StreamedInteraction
    ): StreamedInteraction {
        val safeOperationName = sanitizeOperationName(operationName)
        var lastFailure: ApiCallFailure? = null

        if (attemptPlan.isEmpty()) {
            val failure = ApiCallFailure.Unknown(Exception("No available keys in plan"))
            throw ApiExecutionException(
                failure = failure,
                operationName = safeOperationName,
                keyId = null,
                cause = Exception("操作 [$safeOperationName] 失败：可用的 API 密钥列表为空。")
            )
        }

        val reserveForOtherRequired = (reserveForRequired - 1).coerceAtLeast(0)
        for (lease in attemptPlan) {
            val consumed = if (isRequired) {
                tracker.tryConsumeRequired(1, reserveForOtherRequired)
            } else {
                tracker.tryConsumeOptional(1, reserveForRequired)
            }
            if (!consumed) {
                val failure = ApiCallFailure.Unknown(Exception("Request budget unavailable"))
                throw ApiExecutionException(
                    failure = failure,
                    operationName = safeOperationName,
                    keyId = lease.keyId,
                    cause = Exception("操作 [$safeOperationName] 超过了本轮请求预算。")
                )
            }

            var sameKeyAttemptCount = 0
            while (true) {
                try {
                    onAttemptStarted()
                    PrivacySafeLogger.d(TAG, "Starting $safeOperationName stream with keyId=${lease.keyId}")
                    val result = block(lease)
                    ApiRetryPolicy.resetRateLimitCount(context, lease.keyId)
                    ApiKeyPool.bindSessionKey(context, sessionId, lease.keyId)
                    ApiKeyPool.setLastUsedKeyId(context, lease.keyId)
                    return result
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val failure = classifyFailure(error)
                    PrivacySafeLogger.e(
                        TAG,
                        "Streaming API call failed (operation=$safeOperationName, category=${failure.safeCategory()})"
                    )
                    lastFailure = failure
                    val decision = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount)
                    ApiRetryPolicy.handleKeyStatusUpdate(context, lease.keyId, failure)

                    when (decision) {
                        ApiRetryDecision.STOP_REQUEST -> {
                            throw ApiExecutionException(
                                failure = failure,
                                operationName = safeOperationName,
                                keyId = lease.keyId,
                                cause = sanitizedFailureException(safeOperationName, failure)
                            )
                        }

                        ApiRetryDecision.RETRY_SAME_KEY -> {
                            sameKeyAttemptCount++
                            val backoffMs = if (
                                failure is ApiCallFailure.Http && failure.code in 500..599
                            ) {
                                sameKeyAttemptCount * 1000L
                            } else {
                                1000L
                            }
                            delayProvider.delay(backoffMs)

                            val retryConsumed = if (isRequired) {
                                tracker.tryConsumeRequired(1, reserveForOtherRequired)
                            } else {
                                tracker.tryConsumeOptional(1, reserveForRequired)
                            }
                            if (!retryConsumed) {
                                val budgetFailure = ApiCallFailure.Unknown(
                                    Exception("Retry budget unavailable")
                                )
                                throw ApiExecutionException(
                                    failure = budgetFailure,
                                    operationName = safeOperationName,
                                    keyId = lease.keyId,
                                    cause = Exception("操作 [$safeOperationName] 的重试超过本轮请求预算。")
                                )
                            }
                            continue
                        }

                        ApiRetryDecision.TRY_NEXT_KEY,
                        ApiRetryDecision.COOLDOWN_AND_TRY_NEXT_KEY -> break
                    }
                }
            }
        }

        val failure = lastFailure ?: ApiCallFailure.Unknown(Exception("All keys failed"))
        throw ApiExecutionException(
            failure = failure,
            operationName = sanitizeOperationName(operationName),
            keyId = null,
            cause = sanitizedFailureException(sanitizeOperationName(operationName), failure)
        )
    }

    private suspend fun streamSingleAttempt(
        apiKey: String,
        request: CreateInteractionRequest,
        onTextUpdate: suspend (String) -> Unit = {}
    ): StreamedInteraction {
        val body = json.encodeToString(request)
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val httpRequest = Request.Builder()
            .url(ENDPOINT)
            .header("x-goog-api-key", apiKey)
            .header("Api-Revision", API_REVISION)
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val accumulator = InteractionSseAccumulator()
        var lastDeliveredText = ""
        var lastDeliveryNanos = 0L

        streamFrames(httpRequest).collect { data ->
            val progress = accumulator.accept(data)
            if (
                (progress.textChanged || progress.flushSuggested) &&
                progress.text != lastDeliveredText
            ) {
                val now = System.nanoTime()
                val growth = progress.text.length - lastDeliveredText.length
                val shouldDeliver = progress.flushSuggested ||
                    growth >= MIN_UI_UPDATE_GROWTH ||
                    now - lastDeliveryNanos >= MIN_UI_UPDATE_INTERVAL_NS
                if (shouldDeliver) {
                    onTextUpdate(progress.text)
                    lastDeliveredText = progress.text
                    lastDeliveryNanos = now
                }
            }
        }

        if (!accumulator.completed) {
            throw IOException("Interaction stream closed before completion")
        }
        val outputText = accumulator.outputText.trim()
        if (outputText.isBlank()) {
            throw SerializationException("Interaction stream returned no model text")
        }
        if (outputText != lastDeliveredText) {
            onTextUpdate(outputText)
        }
        val interactionId = accumulator.interactionId
            ?.takeIf(String::isNotBlank)
            ?: throw SerializationException("Interaction stream returned no interaction id")
        return StreamedInteraction(interactionId, outputText)
    }

    private fun streamFrames(request: Request): Flow<String> = callbackFlow {
        val call = RetrofitClient.okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                close(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        close(
                            StreamingHttpException(
                                code = response.code,
                                retryAfterMs = ApiRetryPolicy.parseRetryAfterMs(
                                    response.header("Retry-After")
                                )
                            )
                        )
                        return
                    }

                    val responseBody = response.body
                    if (responseBody == null) {
                        close(IOException("Interaction stream response body is empty"))
                        return
                    }

                    try {
                        val source = responseBody.source()
                        val dataLines = mutableListOf<String>()

                        fun dispatchFrame() {
                            if (dataLines.isEmpty()) return
                            val data = dataLines.joinToString("\n")
                            dataLines.clear()
                            if (data != "[DONE]" && trySend(data).isFailure) {
                                throw IOException("Interaction stream consumer is unavailable")
                            }
                        }

                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.isEmpty() -> dispatchFrame()
                                line.startsWith("data:") -> {
                                    dataLines += line.removePrefix("data:").trimStart()
                                }
                            }
                        }
                        dispatchFrame()
                        close()
                    } catch (error: Exception) {
                        close(error)
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    private fun classifyFailure(error: Exception): ApiCallFailure = when (error) {
        is StreamingHttpException -> ApiCallFailure.Http(error.code, error.retryAfterMs)
        is IOException -> ApiCallFailure.Network(error)
        is SerializationException -> ApiCallFailure.Serialization(error)
        else -> ApiCallFailure.Unknown(error)
    }

    private fun sanitizeOperationName(operationName: String): String {
        val base = operationName.substringBefore('-')
            .filter { it.isLetterOrDigit() || it == '_' }
            .take(80)
        return base.ifBlank { "ApiOperation" }
    }

    private fun interactionCharacterId(operationName: String): String? {
        val raw = when {
            operationName.startsWith(MAIN_ANSWER_PREFIX) -> operationName.removePrefix(MAIN_ANSWER_PREFIX)
            operationName.startsWith(CONTINUE_ANSWER_PREFIX) -> operationName.removePrefix(CONTINUE_ANSWER_PREFIX)
            else -> return null
        }
        return raw.takeIf { characterId ->
            characterId.isNotBlank() &&
                characterId.length <= 100 &&
                characterId.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        }
    }

    private fun sanitizedFailureException(
        operationName: String,
        failure: ApiCallFailure
    ): Exception {
        val category = when (failure) {
            is ApiCallFailure.Http -> "HTTP_${failure.code}"
            is ApiCallFailure.Network -> "NETWORK"
            is ApiCallFailure.Serialization -> "SERIALIZATION"
            is ApiCallFailure.Unknown -> "UNKNOWN"
        }
        return Exception("操作 [$operationName] 失败（$category）。")
    }
}

internal data class InteractionStreamProgress(
    val text: String,
    val textChanged: Boolean,
    val flushSuggested: Boolean
)

internal class InteractionSseAccumulator {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val modelOutputStepIndexes = mutableSetOf<Int>()
    private val output = StringBuilder()

    var interactionId: String? = null
        private set
    var completed: Boolean = false
        private set
    val outputText: String
        get() = output.toString()

    fun accept(data: String): InteractionStreamProgress {
        val envelope = json.decodeFromString<InteractionSseEnvelope>(data)
        interactionId = envelope.interaction?.id?.takeIf(String::isNotBlank) ?: interactionId
        var textChanged = false
        var flushSuggested = false

        when (envelope.eventType) {
            "step.start" -> {
                if (envelope.step?.type == "model_output") {
                    envelope.index?.let(modelOutputStepIndexes::add)
                }
            }

            "step.delta" -> {
                val index = envelope.index
                val delta = envelope.delta
                if (
                    index != null &&
                    index in modelOutputStepIndexes &&
                    delta?.type == "text" &&
                    !delta.text.isNullOrEmpty()
                ) {
                    output.append(delta.text)
                    textChanged = true
                }
            }

            "step.stop" -> flushSuggested = true
            "interaction.completed" -> {
                completed = true
                flushSuggested = true
            }

            "interaction.failed",
            "interaction.cancelled",
            "error" -> throw IOException("Interaction stream reported failure")
        }

        return InteractionStreamProgress(
            text = output.toString(),
            textChanged = textChanged,
            flushSuggested = flushSuggested
        )
    }
}

@Serializable
private data class InteractionSseEnvelope(
    @SerialName("event_type") val eventType: String? = null,
    val index: Int? = null,
    val step: InteractionSseStep? = null,
    val delta: InteractionSseDelta? = null,
    val interaction: InteractionSseInteraction? = null
)

@Serializable
private data class InteractionSseStep(
    val type: String? = null
)

@Serializable
private data class InteractionSseDelta(
    val type: String? = null,
    val text: String? = null
)

@Serializable
private data class InteractionSseInteraction(
    val id: String? = null,
    val status: String? = null
)

private class StreamingHttpException(
    val code: Int,
    val retryAfterMs: Long?
) : IOException("Interaction streaming HTTP failure: $code")
