package com.elio.jianyu.network

import android.content.Context
import com.elio.jianyu.network.keys.ApiKeyLease
import com.elio.jianyu.network.retry.ApiCallFailure
import com.elio.jianyu.network.retry.ApiRetryPolicy
import com.elio.jianyu.roundtable.DefaultDelayProvider
import com.elio.jianyu.roundtable.DelayProvider
import com.elio.jianyu.roundtable.RequestBudgetTracker
import com.elio.jianyu.telemetry.CloudInteractionRequestPolicy
import com.elio.jianyu.telemetry.CloudInteractionSettings
import com.elio.jianyu.telemetry.InteractionChainStore
import com.elio.jianyu.telemetry.PrivacySafeLogger
import com.elio.jianyu.telemetry.TelemetryRepository
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class StreamedInteraction(
    val id: String,
    val outputText: String,
    val model: String? = null,
)

/**
 * Interactions API 的 SSE 客户端。
 *
 * 仅把 model_output 步骤中的 text delta 暴露给界面，thought 等内部步骤不会进入聊天记录。
 * 每次重试开始前都会通知调用方重置当前 Pending 文本，避免不同尝试的内容相互拼接。
 */
object GeminiInteractionsTransport {
    private const val TAG = "InteractionStreaming"
    internal const val STREAMING_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/interactions?alt=sse"
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
        interactionChainKey: String? = null,
        delayProvider: DelayProvider = DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0,
        onAttemptStarted: suspend () -> Unit = {},
        onTextUpdate: suspend (String) -> Unit = {}
    ): StreamedInteraction {
        TelemetryRepository.init(context)
        val cloudEnabled = CloudInteractionSettings.isEnabled(context)
        val characterId = interactionChainKey?.takeIf(String::isNotBlank)
            ?: interactionCharacterId(operationName)
        val requestedPreviousId = request.previousInteractionId
            ?.takeIf(String::isNotBlank)
            ?: if (cloudEnabled && characterId != null) {
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
        ) { secret ->
            streamSingleAttempt(
                apiKey = secret,
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
        block: suspend (String) -> StreamedInteraction,
    ): StreamedInteraction {
        return AiManager.requests(context, AiProvider.GEMINI).execute(
            sessionId = sessionId,
            attemptPlan = attemptPlan,
            operationName = operationName,
            delayProvider = delayProvider,
            onAttemptStarted = {
                if (isRequired) tracker.tryConsumeRequired() else tracker.tryConsumeOptional()
                onAttemptStarted()
            },
            failureClassifier = ::classifyFailure,
            block = block,
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
            .url(STREAMING_ENDPOINT)
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
        return StreamedInteraction(interactionId, outputText, accumulator.interactionModel)
    }

    private fun streamFrames(request: Request): Flow<String> = callbackFlow {
        val call = GeminiRestTransport.okHttpClient.newCall(request)
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
    var interactionModel: String? = null
        private set
    var completed: Boolean = false
        private set
    val outputText: String
        get() = output.toString()

    fun accept(data: String): InteractionStreamProgress {
        val envelope = json.parseToJsonElement(data) as? JsonObject
            ?: throw SerializationException("Interaction stream event must be a JSON object")
        val eventType = envelope.string("event_type") ?: envelope.string("type")
        val interaction = envelope.objectValue("interaction")
        interactionId = interaction?.string("id")
            ?: envelope.string("interaction_id")
            ?: envelope.string("id")
            ?: interactionId
        interactionModel = interaction?.string("model") ?: interactionModel
        var textChanged = false
        var flushSuggested = false

        when (eventType) {
            "step.start" -> {
                val step = envelope.objectValue("step")
                val index = envelope.intValue("index")
                if (step?.string("type") == "model_output" && index != null) {
                    modelOutputStepIndexes.add(index)
                    val initialText = step.arrayValue("content")
                        ?.mapNotNull { item ->
                            (item as? JsonObject)
                                ?.takeIf { it.string("type") == "text" }
                                ?.string("text")
                        }
                        .orEmpty()
                        .joinToString(separator = "")
                    if (initialText.isNotEmpty()) {
                        output.append(initialText)
                        textChanged = true
                    }
                }
            }

            "step.delta" -> {
                val index = envelope.intValue("index")
                val delta = envelope.objectValue("delta")
                if (
                    index != null &&
                    index in modelOutputStepIndexes &&
                    delta?.string("type") == "text"
                ) {
                    delta.string("text")?.takeIf(String::isNotEmpty)?.let { text ->
                        output.append(text)
                        textChanged = true
                    }
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

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

    private fun JsonObject.intValue(name: String): Int? =
        (this[name] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.objectValue(name: String): JsonObject? =
        this[name] as? JsonObject

    private fun JsonObject.arrayValue(name: String): JsonArray? =
        this[name] as? JsonArray
}

private class StreamingHttpException(
    val code: Int,
    val retryAfterMs: Long?
) : IOException("Interaction streaming HTTP failure: $code")
