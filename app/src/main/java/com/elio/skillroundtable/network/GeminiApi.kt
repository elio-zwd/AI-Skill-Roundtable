package com.elio.skillroundtable.network

import android.content.Context
import com.elio.skillroundtable.BuildConfig
import com.elio.skillroundtable.network.keys.ApiKeyLease
import com.elio.skillroundtable.network.retry.ApiCallFailure
import com.elio.skillroundtable.network.retry.ApiRetryDecision
import com.elio.skillroundtable.network.retry.ApiRetryPolicy
import com.elio.skillroundtable.network.retry.safeCategory
import com.elio.skillroundtable.roundtable.RequestBudgetTracker
import com.elio.skillroundtable.telemetry.CloudInteractionRequestPolicy
import com.elio.skillroundtable.telemetry.CloudInteractionSettings
import com.elio.skillroundtable.telemetry.InteractionChainStore
import com.elio.skillroundtable.telemetry.PrivacySafeLogger
import com.elio.skillroundtable.telemetry.TelemetryInterceptor
import com.elio.skillroundtable.telemetry.TelemetryRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: Blob? = null
)

@Serializable
data class Blob(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val thinkingConfig: ThinkingConfig? = null
)

@Serializable
data class ThinkingConfig(
    val thinkingLevel: String
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content,
    val finishReason: String? = null,
    val groundingMetadata: GroundingMetadata? = null
)

@Serializable
data class Tool(
    val type: String
)

@Serializable
data class GroundingMetadata(
    val webSearchQueries: List<String>? = null,
    val groundingChunks: List<GroundingChunk>? = null
)

@Serializable
data class GroundingChunk(
    val web: WebSource? = null
)

@Serializable
data class WebSource(
    val uri: String? = null,
    val title: String? = null
)

@Serializable
data class CreateInteractionRequest(
    val model: String? = null,
    val agent: String? = null,
    val input: JsonElement? = null,
    @SerialName("system_instruction") val systemInstruction: String? = null,
    val tools: List<Tool>? = null,
    val store: Boolean? = null,
    @SerialName("previous_interaction_id") val previousInteractionId: String? = null,
    @SerialName("generation_config") val generationConfig: InteractionGenerationConfig? = null
)

@Serializable
data class InteractionGenerationConfig(
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    val temperature: Float? = null,
    @SerialName("thinking_level") val thinkingLevel: String? = null,
    @SerialName("thinking_summaries") val thinkingSummaries: String? = null
)

@Serializable
data class Interaction(
    val id: String,
    val model: String? = null,
    @SerialName("object") val objType: String? = null,
    val steps: List<InteractionStep> = emptyList(),
    val status: String? = null,
    val usage: InteractionUsage? = null
)

@Serializable
data class InteractionStep(
    val type: String,
    val content: List<InteractionContent> = emptyList(),
    val signature: String? = null,
    val summary: List<InteractionContent> = emptyList(),
    val result: JsonElement? = null
)

@Serializable
data class InteractionAnnotation(
    val type: String? = null,
    val url: String? = null,
    val title: String? = null,
    @SerialName("start_index") val startIndex: Int? = null,
    @SerialName("end_index") val endIndex: Int? = null
)

@Serializable
data class InteractionContent(
    val type: String,
    val text: String? = null,
    val annotations: List<InteractionAnnotation>? = null
)

/**
 * 按 Interactions SDK 的 output_text 语义汇总最终文本。
 * 一次交互结尾可能包含多个连续的 model_output 步骤，且单个步骤也可能拆分为多个内容块。
 */
val Interaction.outputText: String
    get() = steps.asReversed()
        .takeWhile { it.type == "model_output" }
        .asReversed()
        .asSequence()
        .flatMap { it.content.asSequence() }
        .filter { it.type == "text" }
        .mapNotNull { it.text?.takeIf(String::isNotBlank) }
        .joinToString(separator = "")
        .trim()

@Serializable
data class GoogleSearchResultItem(
    val title: String? = null,
    val url: String? = null,
    val snippet: String? = null
)

@Serializable
data class InteractionUsage(
    @SerialName("total_input_tokens") val totalInputTokens: Int? = null,
    @SerialName("total_output_tokens") val totalOutputTokens: Int? = null,
    @SerialName("total_thought_tokens") val totalThoughtTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)

@Serializable
data class EmbedContentConfig(
    @SerialName("output_dimensionality") val outputDimensionality: Int? = 768
)

@Serializable
data class EmbedContentRequest(
    val model: String = "models/gemini-embedding-001",
    val content: Content,
    val config: EmbedContentConfig = EmbedContentConfig()
)

@Serializable
data class EmbedContentResponse(
    val embedding: Embedding
)

@Serializable
data class Embedding(
    val values: List<Float>
)

interface GeminiApiService {
    @GET("v1beta/models/gemini-3.1-flash-lite")
    suspend fun validateApiKey(
        @Query("key") apiKey: String
    ): retrofit2.Response<JsonElement>

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/interactions")
    suspend fun createInteraction(
        @Query("key") apiKey: String,
        @Header("Api-Revision") apiRevision: String = "2026-05-20",
        @Body request: CreateInteractionRequest
    ): Interaction

    @POST("v1beta/models/gemini-embedding-001:embedContent")
    suspend fun embedContent(
        @Query("key") apiKey: String,
        @Body request: EmbedContentRequest
    ): EmbedContentResponse
}

object RetrofitClient {
    private const val TAG = "RetrofitClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val MAIN_ANSWER_PREFIX = "MainAnswer-"
    private const val CONTINUE_ANSWER_PREFIX = "ContinueAnswer-"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(TelemetryInterceptor())
        .addInterceptor(HttpLoggingInterceptor { rawMessage ->
            PrivacySafeLogger.d("OkHttp", rawMessage)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .build()

    val service: GeminiApiService by lazy {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApiService::class.java)
    }

    /**
     * 底层通用网络请求执行引擎，实现确定的租约尝试、错误分类与重试策略、预算控制与指数退避。
     */
    suspend fun <T> executeWithBudgetAndRetry(
        context: Context,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String,
        delayProvider: com.elio.skillroundtable.roundtable.DelayProvider = com.elio.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0,
        block: suspend (ApiKeyLease) -> T
    ): T {
        val safeOperationName = sanitizeOperationName(operationName)
        var lastFailure: ApiCallFailure? = null

        if (attemptPlan.isEmpty()) {
            val failure = ApiCallFailure.Unknown(Exception("No available keys in plan"))
            throw com.elio.skillroundtable.network.retry.ApiExecutionException(
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
                throw com.elio.skillroundtable.network.retry.ApiExecutionException(
                    failure = failure,
                    operationName = safeOperationName,
                    keyId = lease.keyId,
                    cause = Exception("操作 [$safeOperationName] 超过了本轮请求预算。")
                )
            }

            var sameKeyAttemptCount = 0
            while (true) {
                try {
                    PrivacySafeLogger.d(
                        TAG,
                        "Executing $safeOperationName with keyId=${lease.keyId}"
                    )
                    val result = block(lease)
                    ApiRetryPolicy.resetRateLimitCount(context, lease.keyId)
                    ApiKeyPool.bindSessionKey(context, sessionId, lease.keyId)
                    ApiKeyPool.setLastUsedKeyId(context, lease.keyId)
                    return result
                } catch (error: Exception) {
                    val failure = classifyFailure(error)
                    PrivacySafeLogger.e(
                        TAG,
                        "API call failed (operation=$safeOperationName, category=${failure.safeCategory()})"
                    )
                    lastFailure = failure
                    val decision = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount)
                    ApiRetryPolicy.handleKeyStatusUpdate(context, lease.keyId, failure)

                    when (decision) {
                        ApiRetryDecision.STOP_REQUEST -> {
                            throw com.elio.skillroundtable.network.retry.ApiExecutionException(
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
                                throw com.elio.skillroundtable.network.retry.ApiExecutionException(
                                    failure = budgetFailure,
                                    operationName = safeOperationName,
                                    keyId = lease.keyId,
                                    cause = Exception("操作 [$safeOperationName] 重试时超过了本轮请求预算。")
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
        throw com.elio.skillroundtable.network.retry.ApiExecutionException(
            failure = failure,
            operationName = safeOperationName,
            keyId = null,
            cause = sanitizedFailureException(safeOperationName, failure)
        )
    }

    suspend fun createInteraction(
        context: Context,
        request: CreateInteractionRequest,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String = "CreateInteraction",
        delayProvider: com.elio.skillroundtable.roundtable.DelayProvider = com.elio.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
    ): Interaction {
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
        val privacySafeRequest = request.copy(
            store = cloudPolicy.store,
            previousInteractionId = cloudPolicy.previousInteractionId
        )
        val response = executeWithBudgetAndRetry(
            context,
            sessionId,
            attemptPlan,
            tracker,
            operationName,
            delayProvider,
            isRequired,
            reserveForRequired
        ) { lease ->
            service.createInteraction(apiKey = lease.secret, request = privacySafeRequest)
        }
        if (
            cloudPolicy.store &&
            characterId != null &&
            CloudInteractionSettings.isEnabled(context)
        ) {
            InteractionChainStore.put(sessionId, characterId, response.id)
        }
        return response
    }

    suspend fun generateContent(
        context: Context,
        model: String,
        request: GenerateContentRequest,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String = "GenerateContent",
        delayProvider: com.elio.skillroundtable.roundtable.DelayProvider = com.elio.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
    ): GenerateContentResponse {
        TelemetryRepository.init(context)
        return executeWithBudgetAndRetry(
            context,
            sessionId,
            attemptPlan,
            tracker,
            operationName,
            delayProvider,
            isRequired,
            reserveForRequired
        ) { lease ->
            service.generateContent(model = model, apiKey = lease.secret, request = request)
        }
    }

    suspend fun embedContent(
        context: Context,
        text: String,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String = "EmbedContent",
        delayProvider: com.elio.skillroundtable.roundtable.DelayProvider = com.elio.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
    ): List<Float> {
        TelemetryRepository.init(context)
        val request = EmbedContentRequest(
            content = Content(parts = listOf(Part(text = text)))
        )
        return executeWithBudgetAndRetry(
            context,
            sessionId,
            attemptPlan,
            tracker,
            operationName,
            delayProvider,
            isRequired,
            reserveForRequired
        ) { lease ->
            service.embedContent(apiKey = lease.secret, request = request).embedding.values
        }
    }

    private fun classifyFailure(error: Exception): ApiCallFailure = when (error) {
        is retrofit2.HttpException -> {
            val retryAfterMs = ApiRetryPolicy.parseRetryAfterMs(
                error.response()?.headers()?.get("Retry-After")
            )
            ApiCallFailure.Http(error.code(), retryAfterMs)
        }
        is java.io.IOException -> ApiCallFailure.Network(error)
        is kotlinx.serialization.SerializationException -> ApiCallFailure.Serialization(error)
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
            operationName.startsWith(MAIN_ANSWER_PREFIX) -> {
                operationName.removePrefix(MAIN_ANSWER_PREFIX)
            }
            operationName.startsWith(CONTINUE_ANSWER_PREFIX) -> {
                operationName.removePrefix(CONTINUE_ANSWER_PREFIX)
            }
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
