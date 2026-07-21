package com.example.skillroundtable.network

import android.content.Context
import android.util.Log
import com.example.skillroundtable.BuildConfig
import com.example.skillroundtable.network.keys.ApiKeyLease
import com.example.skillroundtable.network.keys.ApiKeyScheduler
import com.example.skillroundtable.network.retry.ApiCallFailure
import com.example.skillroundtable.network.retry.ApiRetryDecision
import com.example.skillroundtable.network.retry.ApiRetryPolicy
import com.example.skillroundtable.roundtable.RequestBudgetTracker
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement



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

// Interactions API 相关的实体类
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
    val summary: String? = null,
    val result: List<GoogleSearchResultItem>? = null
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
 *
 * 一次交互结尾可能包含多个连续的 model_output 步骤，且单个步骤也可能拆分为多个内容块。
 * 不能只读取第一个内容块，否则 API 已成功返回时仍会被误判为“空响应”。
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

// Embedding 接口相关的实体类
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

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(TelemetryInterceptor())
        .addInterceptor(HttpLoggingInterceptor { rawMessage ->
            val redactedMessage = rawMessage.replace(
                Regex("([?&]key=)[^&\\s]+"),
                "$1[REDACTED]"
            )
            logD("OkHttp", redactedMessage)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
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
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
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
        delayProvider: com.example.skillroundtable.roundtable.DelayProvider = com.example.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0,
        block: suspend (ApiKeyLease) -> T
    ): T {
        var lastException: Exception? = null
        var lastFailure: ApiCallFailure? = null
        val attempts = mutableListOf<String>()

        if (attemptPlan.isEmpty()) {
            val emptyFailure = ApiCallFailure.Unknown(Exception("No available keys in plan"))
            throw com.example.skillroundtable.network.retry.ApiExecutionException(
                failure = emptyFailure,
                operationName = operationName,
                keyId = null,
                cause = Exception("操作 [$operationName] 失败：可用的 API 密钥列表为空。")
            )
        }

        val reserveForOtherRequired = (reserveForRequired - 1).coerceAtLeast(0)
        for (lease in attemptPlan) {
            // 开始请求前，首先校验并原子消费预算 (REQUIRED 与 OPTIONAL 区别消费，且遵守预留)
            val consumed = if (isRequired) {
                tracker.tryConsumeRequired(1, reserveForOtherRequired)
            } else {
                tracker.tryConsumeOptional(1, reserveForRequired)
            }
            if (!consumed) {
                val budgetFailure = ApiCallFailure.Unknown(Exception("本问题 API 请求预算已耗尽或未满足预留配额。"))
                throw com.example.skillroundtable.network.retry.ApiExecutionException(
                    failure = budgetFailure,
                    operationName = operationName,
                    keyId = lease.keyId,
                    cause = Exception("操作 [$operationName] 超过了本轮请求预算。已使用: ${tracker.getUsed()}, 预留主回答: $reserveForRequired")
                )
            }

            var sameKeyAttemptCount = 0
            while (true) {
                try {
                    logD(TAG, "[$operationName] 正在使用 Key ${lease.keyId} 执行请求...")
                    val result = block(lease)
                    ApiRetryPolicy.resetRateLimitCount(context, lease.keyId)
                    ApiKeyPool.bindSessionKey(context, sessionId, lease.keyId)
                    ApiKeyPool.setLastUsedKeyId(context, lease.keyId)
                    return result
                } catch (e: Exception) {
                    val failure = when (e) {
                        is retrofit2.HttpException -> {
                            val code = e.code()
                            val retryAfterHeader = e.response()?.headers()?.get("Retry-After")
                            val retryAfterMs = ApiRetryPolicy.parseRetryAfterMs(retryAfterHeader)
                            ApiCallFailure.Http(code, retryAfterMs)
                        }
                        is java.io.IOException -> ApiCallFailure.Network(e)
                        is kotlinx.serialization.SerializationException -> ApiCallFailure.Serialization(e)
                        else -> ApiCallFailure.Unknown(e)
                    }

                    attempts.add("${lease.keyId}: ${e.message ?: e.javaClass.simpleName}")
                    lastException = e
                    lastFailure = failure

                    val decision = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount)
                    ApiRetryPolicy.handleKeyStatusUpdate(context, lease.keyId, failure)

                    when (decision) {
                        ApiRetryDecision.STOP_REQUEST -> {
                            throw com.example.skillroundtable.network.retry.ApiExecutionException(
                                failure = failure,
                                operationName = operationName,
                                keyId = lease.keyId,
                                cause = e
                            )
                        }
                        ApiRetryDecision.RETRY_SAME_KEY -> {
                            sameKeyAttemptCount++
                            // HTTP 5xx 指数退避：第一次重试退避 1000ms，第二次退避 2000ms；其它网络异常默认 1000ms。
                            val backoffMs = if (failure is ApiCallFailure.Http && failure.code in 500..599) {
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
                                throw com.example.skillroundtable.network.retry.ApiExecutionException(
                                    failure = ApiCallFailure.Unknown(Exception("重试时超出预算预留限制")),
                                    operationName = operationName,
                                    keyId = lease.keyId,
                                    cause = Exception("操作 [$operationName] 重试时超过了本轮请求预算或预留配额。")
                                )
                            }
                            continue
                        }
                        ApiRetryDecision.TRY_NEXT_KEY,
                        ApiRetryDecision.COOLDOWN_AND_TRY_NEXT_KEY -> {
                            break
                        }
                    }
                }
            }
        }

        val detail = attempts.joinToString(", ")
        throw com.example.skillroundtable.network.retry.ApiExecutionException(
            failure = lastFailure ?: ApiCallFailure.Unknown(Exception("所有 Key 均尝试失败")),
            operationName = operationName,
            keyId = null,
            cause = lastException ?: Exception("操作 [$operationName] 失败，已尝试所有 Key。细节: [$detail]")
        )
    }

    suspend fun createInteraction(
        context: Context,
        request: CreateInteractionRequest,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String = "CreateInteraction",
        delayProvider: com.example.skillroundtable.roundtable.DelayProvider = com.example.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
    ): Interaction {
        return executeWithBudgetAndRetry(context, sessionId, attemptPlan, tracker, operationName, delayProvider, isRequired, reserveForRequired) { lease ->
            service.createInteraction(apiKey = lease.secret, request = request)
        }
    }

    suspend fun generateContent(
        context: Context,
        model: String,
        request: GenerateContentRequest,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        operationName: String = "GenerateContent",
        delayProvider: com.example.skillroundtable.roundtable.DelayProvider = com.example.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
    ): GenerateContentResponse {
        return executeWithBudgetAndRetry(context, sessionId, attemptPlan, tracker, operationName, delayProvider, isRequired, reserveForRequired) { lease ->
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
        delayProvider: com.example.skillroundtable.roundtable.DelayProvider = com.example.skillroundtable.roundtable.DefaultDelayProvider,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
    ): List<Float> {
        val request = EmbedContentRequest(
            content = Content(parts = listOf(Part(text = text)))
        )
        return executeWithBudgetAndRetry(context, sessionId, attemptPlan, tracker, operationName, delayProvider, isRequired, reserveForRequired) { lease ->
            service.embedContent(apiKey = lease.secret, request = request).embedding.values
        }
    }
}

class TelemetryInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val url = request.url
        val startTime = System.currentTimeMillis()

        val path = url.encodedPath
        var model = "unknown"
        val modelRegex = "models/([^:/]+)".toRegex()
        val matchResult = modelRegex.find(path)
        if (matchResult != null) {
            model = matchResult.groupValues[1]
        }

        val apiKey = url.queryParameter("key") ?: ""
        val keyId = if (apiKey.isBlank()) "none" else ApiKeyPool.findKeyId(apiKey)

        var prompt = ""
        try {
            val requestBody = request.body
            if (requestBody != null) {
                val buffer = okio.Buffer()
                requestBody.writeTo(buffer)
                val bodyStr = buffer.readUtf8()
                if (bodyStr.isNotBlank()) {
                    val json = org.json.JSONObject(bodyStr)
                    val sb = java.lang.StringBuilder()

                    // 解析 Interactions API 中的 model 或 agent
                    if (model == "unknown") {
                        if (json.has("model")) {
                            model = json.getString("model")
                        } else if (json.has("agent")) {
                            model = json.getString("agent")
                        }
                    }

                    // 1. 解析旧版 systemInstruction
                    if (json.has("systemInstruction")) {
                        val sysInst = json.getJSONObject("systemInstruction")
                        if (sysInst.has("parts")) {
                            val parts = sysInst.getJSONArray("parts")
                            sb.append("=== System Instruction ===\n")
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("text")) {
                                    sb.append(part.getString("text")).append("\n")
                                } else if (part.has("inlineData")) {
                                    val inlineData = part.getJSONObject("inlineData")
                                    val mimeType = inlineData.optString("mimeType", "")
                                    val base64Data = inlineData.optString("data", "")
                                    if (base64Data.isNotBlank()) {
                                        val decodedText = try {
                                            val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                            java.lang.String(decodedBytes, Charsets.UTF_8).toString()
                                        } catch (e: Exception) {
                                            "[Base64 Decode Error]"
                                        }
                                        sb.append("\n[附件 (MIME: $mimeType)]:\n").append(decodedText).append("\n")
                                    }
                                }
                            }
                            sb.append("\n==========================\n\n")
                        }
                    }
                    
                    // 2. 解析 Interactions API 中的 system_instruction (String)
                    if (json.has("system_instruction")) {
                        sb.append("=== System Instruction ===\n")
                        sb.append(json.getString("system_instruction")).append("\n")
                        sb.append("\n==========================\n\n")
                    }

                    // 3. 解析 previous_interaction_id
                    if (json.has("previous_interaction_id")) {
                        sb.append("[Previous ID]: ").append(json.getString("previous_interaction_id")).append("\n")
                    }

                    // 4. 解析旧版 contents
                    if (json.has("contents")) {
                        val contents = json.getJSONArray("contents")
                        for (i in 0 until contents.length()) {
                            val turn = contents.getJSONObject(i)
                            val role = turn.optString("role", "user")
                            val parts = turn.getJSONArray("parts")
                            sb.append("【").append(role).append("】: ")
                            for (j in 0 until parts.length()) {
                                val part = parts.getJSONObject(j)
                                if (part.has("text")) {
                                    sb.append(part.getString("text"))
                                } else if (part.has("inlineData")) {
                                    sb.append("[Inline Data Attachment]")
                                }
                            }
                            sb.append("\n")
                        }
                    } 
                    // 5. 解析 Interactions API 中的 input
                    else if (json.has("input")) {
                        val inputVal = json.get("input")
                        if (inputVal is org.json.JSONArray) {
                            for (i in 0 until inputVal.length()) {
                                val step = inputVal.getJSONObject(i)
                                val type = step.optString("type", "")
                                val contentArr = step.optJSONArray("content")
                                sb.append("【").append(type).append("】: ")
                                if (contentArr != null) {
                                    for (j in 0 until contentArr.length()) {
                                        val part = contentArr.getJSONObject(j)
                                        if (part.has("text")) {
                                            sb.append(part.getString("text"))
                                        }
                                    }
                                }
                                sb.append("\n")
                            }
                        } else {
                            sb.append("【input】: ").append(inputVal.toString()).append("\n")
                        }
                    }
                    // 6. 解析 Embedding input
                    else if (json.has("content")) {
                        val content = json.getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        sb.append("【Embedding Input】: ")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                sb.append(part.getString("text"))
                            }
                        }
                        sb.append("\n")
                    }

                    prompt = sb.toString().trim()
                }
            }
        } catch (e: Exception) {
            logE("TelemetryInterceptor", "解析请求 Prompt 失败", e)
        }

        var response: okhttp3.Response? = null
        var lastException: Exception? = null
        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            lastException = e
        }

        val responseTime = System.currentTimeMillis()
        val statusCode = response?.code ?: -1
        val errorMsg = lastException?.message ?: response?.message

        var responseText: String? = null
        if (response != null && response.isSuccessful) {
            try {
                val responseBody = response.body
                if (responseBody != null) {
                    val peekedBody = response.peekBody(1024 * 512)
                    val bodyStr = peekedBody.string()
                    if (bodyStr.isNotBlank()) {
                        val json = org.json.JSONObject(bodyStr)
                        // 解析旧版 candidates
                        if (json.has("candidates")) {
                            val candidates = json.getJSONArray("candidates")
                            if (candidates.length() > 0) {
                                val content = candidates.getJSONObject(0).optJSONObject("content")
                                val parts = content?.optJSONArray("parts")
                                if (parts != null && parts.length() > 0) {
                                    responseText = parts.getJSONObject(0).optString("text", "")
                                }
                            }
                        } 
                        // 解析 Interactions steps
                        else if (json.has("steps")) {
                            val steps = json.getJSONArray("steps")
                            val sbResponse = java.lang.StringBuilder()
                            for (i in 0 until steps.length()) {
                                val step = steps.getJSONObject(i)
                                val type = step.optString("type", "")
                                if (type == "model_output") {
                                    val contentArr = step.optJSONArray("content")
                                    if (contentArr != null) {
                                        for (j in 0 until contentArr.length()) {
                                            val part = contentArr.getJSONObject(j)
                                            if (part.has("text")) {
                                                sbResponse.append(part.getString("text"))
                                            }
                                            if (part.has("annotations")) {
                                                val annotations = part.getJSONArray("annotations")
                                                if (annotations.length() > 0) {
                                                    sbResponse.append("\n[Citations:\n")
                                                    for (k in 0 until annotations.length()) {
                                                        val ann = annotations.getJSONObject(k)
                                                        val title = ann.optString("title", "Link")
                                                        val url = ann.optString("url", "")
                                                        if (url.isNotBlank()) {
                                                            sbResponse.append("- $title ($url)\n")
                                                        }
                                                    }
                                                    sbResponse.append("]\n")
                                                }
                                            }
                                        }
                                    }
                                } else if (type == "thought") {
                                    val summary = step.optString("summary", "")
                                    if (summary.isNotBlank()) {
                                        sbResponse.append("\n[Thought Summary: $summary]\n")
                                    }
                                } else if (type == "google_search_result") {
                                    val searchResult = step.optJSONArray("result")
                                    if (searchResult != null && searchResult.length() > 0) {
                                        sbResponse.append("\n[Google Search Results:\n")
                                        for (j in 0 until searchResult.length()) {
                                            val item = searchResult.getJSONObject(j)
                                            sbResponse.append("- ").append(item.optString("title", "")).append(" (").append(item.optString("url", "")).append(")\n")
                                        }
                                        sbResponse.append("]\n")
                                    }
                                }
                            }
                            responseText = sbResponse.toString().trim()
                        }
                        // 解析 embedding
                        else if (json.has("embedding")) {
                            val embedding = json.getJSONObject("embedding")
                            val values = embedding.optJSONArray("values")
                            if (values != null) {
                                responseText = "Embedding Vector: [${values.length()} values]"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logE("TelemetryInterceptor", "解析响应文本失败", e)
            }
        }

        ApiKeyPool.addLog(ApiLog(
            keyId = keyId,
            model = model,
            requestTime = startTime,
            responseTime = responseTime,
            statusCode = statusCode,
            errorMessage = errorMsg,
            prompt = prompt,
            responseText = responseText ?: ""
        ))

        if (lastException != null) {
            throw lastException
        }
        return response!!
    }
}

private fun logD(tag: String, msg: String) {
    try {
        android.util.Log.d(tag, msg)
    } catch (_: Throwable) {
        println("[$tag] $msg")
    }
}

private fun logE(tag: String, msg: String, tr: Throwable? = null) {
    try {
        android.util.Log.e(tag, msg, tr)
    } catch (_: Throwable) {
        println("[$tag] ERROR: $msg ${tr?.message ?: ""}")
    }
}
