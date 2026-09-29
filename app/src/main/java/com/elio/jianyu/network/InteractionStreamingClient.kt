package com.elio.jianyu.network

import android.content.Context
import com.elio.jianyu.BuildConfig
import com.elio.jianyu.network.keys.ApiKeyLease
import com.elio.jianyu.network.retry.ApiCallFailure
import com.elio.jianyu.network.retry.ApiRetryPolicy
import com.elio.jianyu.roundtable.DefaultDelayProvider
import com.elio.jianyu.roundtable.DelayProvider
import com.elio.jianyu.roundtable.RequestBudgetTracker
import com.elio.jianyu.telemetry.CloudInteractionRequestPolicy
import com.elio.jianyu.telemetry.InteractionChainStore
import com.elio.jianyu.telemetry.PrivacySafeLogger
import com.elio.jianyu.telemetry.TelemetryEventFactory
import com.elio.jianyu.telemetry.TelemetryLevel
import com.elio.jianyu.telemetry.TelemetryPreviewExtractor
import com.elio.jianyu.telemetry.TelemetryRepository
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
import retrofit2.HttpException

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
        val characterId = interactionChainKey?.takeIf(String::isNotBlank)
            ?: interactionCharacterId(operationName)
        val requestedPreviousId = request.previousInteractionId
            ?.takeIf(String::isNotBlank)
            ?: if (characterId != null) {
                InteractionChainStore.get(sessionId, characterId)
            } else {
                null
            }
        val cloudPolicy = CloudInteractionRequestPolicy.apply(
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
            try {
                streamSingleAttempt(
                    apiKey = secret,
                    request = streamingRequest,
                    onTextUpdate = onTextUpdate,
                )
            } catch (streamError: CancellationException) {
                throw streamError
            } catch (streamError: Exception) {
                if (!shouldFallbackToNonStreaming(streamError)) throw streamError

                PrivacySafeLogger.w(
                    TAG,
                    "SSE 流式传输异常（${streamError.javaClass.simpleName}），使用当前 Key 降级为非流式请求",
                )

                if (isRequired) tracker.tryConsumeRequired() else tracker.tryConsumeOptional()
                onAttemptStarted()

                try {
                    val restResponse = GeminiRestTransport.service.createInteraction(
                        apiKey = secret,
                        request = streamingRequest.copy(stream = false),
                    )
                    val outputText = restResponse.outputText.trim()
                    if (outputText.isBlank()) {
                        throw SerializationException("Non-streaming fallback returned no model text")
                    }
                    onTextUpdate(outputText)
                    StreamedInteraction(
                        id = restResponse.id,
                        outputText = outputText,
                        model = restResponse.model,
                    )
                } catch (fallbackError: CancellationException) {
                    throw fallbackError
                } catch (fallbackError: Exception) {
                    PrivacySafeLogger.e(TAG, "SSE 非流式降级请求失败", fallbackError)
                    throw fallbackError
                }
            }
        }

        if (
            cloudPolicy.store &&
            characterId != null
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
            failureClassifier = ::classifyInteractionFailure,
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
            .header("Cache-Control", "no-cache")
            .post(body)
            .build()

        val accumulator = InteractionSseAccumulator()
        val telemetryLevel = TelemetryRepository.currentLevel()
        val startedAt = System.currentTimeMillis()
        val contentDebugAtStart = telemetryLevel == TelemetryLevel.CONTENT_DEBUG && BuildConfig.DEBUG
        val requestPreview = if (contentDebugAtStart) {
            TelemetryPreviewExtractor.requestPreview(httpRequest)
        } else {
            null
        }
        var statusCode: Int? = null
        var streamFailure: Throwable? = null
        var lastDeliveredText = ""
        var lastDeliveryNanos = 0L

        try {
            streamFrames(
                request = httpRequest,
                onResponseStatus = { code -> statusCode = code },
            ).collect { data ->
                val progress = try {
                    accumulator.accept(data)
                } catch (error: SerializationException) {
                    throw InteractionStreamProtocolException(
                        "Interaction stream frame could not be parsed",
                        error,
                    )
                }
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
                throw InteractionStreamProtocolException("Interaction stream closed before completion")
            }
            val outputText = accumulator.outputText.trim()
            if (outputText.isBlank()) {
                throw InteractionStreamProtocolException("Interaction stream returned no model text")
            }
            if (outputText != lastDeliveredText) {
                onTextUpdate(outputText)
            }
            val interactionId = accumulator.interactionId
                ?.takeIf(String::isNotBlank)
                ?: throw InteractionStreamProtocolException("Interaction stream returned no interaction id")
            return StreamedInteraction(interactionId, outputText, accumulator.interactionModel)
        } catch (error: CancellationException) {
            streamFailure = error
            throw error
        } catch (error: Exception) {
            streamFailure = error
            throw error
        } finally {
            recordStreamingTelemetry(
                level = telemetryLevel,
                startedAt = startedAt,
                statusCode = statusCode,
                apiKey = apiKey,
                model = request.model,
                requestPreview = requestPreview,
                responsePreview = accumulator.outputText.takeIf {
                    contentDebugAtStart && it.isNotBlank()
                },
                hasThoughtStep = accumulator.hasThoughtStep,
                failure = streamFailure,
            )
        }
    }

    private fun streamFrames(
        request: Request,
        onResponseStatus: (Int) -> Unit,
    ): Flow<String> = callbackFlow {
        val call = GeminiRestTransport.okHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                close(
                    InteractionStreamTransportException(
                        "Interaction stream transport failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                        error,
                    )
                )
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    onResponseStatus(response.code)
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
                        close(InteractionStreamProtocolException("Interaction stream response body is empty"))
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
                                throw InteractionStreamConsumerException("Interaction stream consumer is unavailable")
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
                        close(
                            when (error) {
                                is InteractionStreamConsumerException -> error
                                is IOException -> InteractionStreamTransportException(
                                    "Interaction stream body read failed: ${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                                    error,
                                )
                                else -> error
                            }
                        )
                    }
                }
            }
        })
        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    private fun recordStreamingTelemetry(
        level: TelemetryLevel,
        startedAt: Long,
        statusCode: Int?,
        apiKey: String,
        model: String?,
        requestPreview: String?,
        responsePreview: String?,
        hasThoughtStep: Boolean,
        failure: Throwable?,
    ) {
        if (level == TelemetryLevel.OFF) return
        val completedAt = System.currentTimeMillis()
        val failureType = when (failure) {
            null -> when {
                statusCode == 429 -> "RATE_LIMITED"
                statusCode != null && statusCode in 400..499 -> "HTTP_4XX"
                statusCode != null && statusCode in 500..599 -> "HTTP_5XX"
                else -> null
            }
            is CancellationException -> "CANCELLED"
            is InteractionStreamTransportException -> "NETWORK"
            is InteractionStreamProtocolException -> "SERIALIZATION"
            is StreamingHttpException -> when {
                failure.code == 429 -> "RATE_LIMITED"
                failure.code in 400..499 -> "HTTP_4XX"
                failure.code in 500..599 -> "HTTP_5XX"
                else -> "HTTP"
            }
            is InteractionStreamTerminalException -> "STREAM_TERMINAL"
            else -> failure.javaClass.simpleName.take(80)
        }
        val errorMessage = when (failure) {
            null -> null
            is InteractionStreamTransportException,
            is InteractionStreamProtocolException,
            is StreamingHttpException,
            is InteractionStreamTerminalException -> {
                val message = failure.message?.takeIf(String::isNotBlank)
                if (message != null) "${failure.javaClass.simpleName}: $message" else failure.javaClass.simpleName
            }
            else -> failure.javaClass.simpleName
        }
        val event = TelemetryEventFactory.create(
            level = level,
            id = UUID.randomUUID().toString(),
            timestamp = startedAt,
            durationMs = (completedAt - startedAt).coerceAtLeast(0L),
            endpoint = "POST /v1beta/interactions?alt=sse",
            model = model,
            keyId = AiManager.findKeyIdOrNull(apiKey),
            statusCode = statusCode,
            failureType = failureType,
            errorMessage = errorMessage,
            requestPreview = requestPreview,
            responsePreview = responsePreview,
            hasThoughtStep = hasThoughtStep,
            contentExpiresAt = if (level == TelemetryLevel.CONTENT_DEBUG) {
                TelemetryRepository.contentDebugExpiresAtOrNull()
            } else {
                null
            },
        )
        if (event != null) TelemetryRepository.record(event)
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
    var hasThoughtStep: Boolean = false
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
                if (step?.string("type") == "thought") {
                    hasThoughtStep = true
                }
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
            "error" -> throw InteractionStreamTerminalException(
                "Interaction stream reported terminal event: ${eventType ?: "unknown"}"
            )
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

internal class StreamingHttpException(
    val code: Int,
    val retryAfterMs: Long?,
) : IOException("Interaction streaming HTTP failure: $code")

internal class InteractionStreamTransportException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

internal class InteractionStreamProtocolException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal class InteractionStreamTerminalException(
    message: String,
) : Exception(message)

internal class InteractionStreamConsumerException(
    message: String,
) : Exception(message)

internal fun shouldFallbackToNonStreaming(error: Exception): Boolean =
    error is InteractionStreamTransportException ||
        error is InteractionStreamProtocolException

internal fun classifyInteractionFailure(error: Exception): ApiCallFailure = when (error) {
    is StreamingHttpException -> ApiCallFailure.Http(error.code, error.retryAfterMs)
    is HttpException -> ApiCallFailure.Http(
        code = error.code(),
        retryAfterMs = ApiRetryPolicy.parseRetryAfterMs(error.response()?.headers()?.get("Retry-After")),
    )
    is InteractionStreamProtocolException,
    is SerializationException -> ApiCallFailure.Serialization(error)
    is IOException -> ApiCallFailure.Network(error)
    else -> ApiCallFailure.Unknown(error)
}
