package com.elio.jianyu.network

import android.content.Context
import com.elio.jianyu.BuildConfig
import com.elio.jianyu.network.keys.ApiKeyLease
import com.elio.jianyu.roundtable.RequestBudgetTracker
import com.elio.jianyu.telemetry.PrivacySafeLogger
import com.elio.jianyu.telemetry.TelemetryInterceptor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String,
)

@Serializable
data class DeepSeekChatCompletionRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
)

@Serializable
data class DeepSeekChatCompletionResponse(
    val id: String,
    val model: String? = null,
    val choices: List<DeepSeekChoice> = emptyList(),
)

@Serializable
data class DeepSeekChoice(
    val message: DeepSeekMessage? = null,
)

@Serializable
data class DeepSeekModelListResponse(
    val data: List<DeepSeekModel> = emptyList(),
)

@Serializable
data class DeepSeekModel(
    val id: String,
)

private interface DeepSeekApiService {
    @GET("models")
    suspend fun listModels(
        @Header("Authorization") authorization: String,
    ): Response<DeepSeekModelListResponse>

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: DeepSeekChatCompletionRequest,
    ): Response<DeepSeekChatCompletionResponse>
}

/** DeepSeek 官方 OpenAI 兼容 REST transport；只处理一次请求，不承担 Key 轮换。 */
object DeepSeekTransport {
    private const val BASE_URL = "https://api.deepseek.com/"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(TelemetryInterceptor())
        .addInterceptor(HttpLoggingInterceptor { rawMessage ->
            PrivacySafeLogger.d("DeepSeekHttp", rawMessage)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .build()

    private val service: DeepSeekApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(DeepSeekApiService::class.java)
    }

    suspend fun validateKey(apiKey: String): ApiKeyValidationState = try {
        when (service.listModels(bearer(apiKey)).code()) {
            in 200..299 -> ApiKeyValidationState.AVAILABLE
            400, 401, 403 -> ApiKeyValidationState.INVALID
            429 -> ApiKeyValidationState.RATE_LIMITED
            else -> ApiKeyValidationState.NETWORK_ERROR
        }
    } catch (_: Exception) {
        ApiKeyValidationState.NETWORK_ERROR
    }

    suspend fun createChatCompletion(
        context: Context,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        model: AiModel,
        systemInstruction: String?,
        userContent: String,
        maxOutputTokens: Int? = null,
        operationName: String,
        tracker: RequestBudgetTracker,
        onAttemptStarted: suspend () -> Unit = {},
        onTextUpdate: suspend (String) -> Unit = {},
    ): DeepSeekChatCompletionResponse {
        require(model.provider == AiProvider.DEEPSEEK) { "DeepSeek transport 只能使用 DeepSeek 模型" }
        val messages = buildList {
            systemInstruction?.takeIf(String::isNotBlank)?.let { add(DeepSeekMessage("system", it)) }
            add(DeepSeekMessage("user", userContent))
        }
        val response = AiManager.requests(context, AiProvider.DEEPSEEK).execute(
            sessionId = sessionId,
            attemptPlan = attemptPlan,
            operationName = operationName,
            onAttemptStarted = {
                tracker.tryConsumeRequired()
                onAttemptStarted()
            },
        ) { secret ->
            val httpResponse = service.createChatCompletion(
                authorization = bearer(secret),
                request = DeepSeekChatCompletionRequest(
                    model = model.modelId,
                    messages = messages,
                    maxTokens = maxOutputTokens,
                ),
            )
            if (!httpResponse.isSuccessful) throw HttpException(httpResponse)
            httpResponse.body() ?: throw IllegalStateException("DeepSeek 未返回响应正文")
        }
        val outputText = response.choices.firstOrNull()?.message?.content?.takeIf(String::isNotBlank)
        if (outputText != null) onTextUpdate(outputText)
        return response
    }

    private fun bearer(apiKey: String): String = "Bearer $apiKey"
}
