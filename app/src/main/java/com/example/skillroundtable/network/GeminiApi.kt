package com.example.skillroundtable.network

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String? = null,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String
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
    val content: Content
)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val TAG = "RetrofitClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
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
     * 实现 API Key 的轮询、熔断与降级重试。
     * 策略：同 Key 先切模型（主力 gemini-3.5-flash -> 备用 gemini-3.1-flash-lite-preview），
     * 若遇到 429 则将该 Key 熔断 24 小时，并切换到下一个可用 Key。
     */
    suspend fun generateContentWithFallback(
        context: Context,
        preferredModel: String,
        request: GenerateContentRequest
    ): GenerateContentResponse {
        val modelOrder = listOf(preferredModel, "gemini-3.1-flash-lite-preview").distinct()
        val attemptOrder = ApiKeyPool.getKeyAttemptOrder(context)
        if (attemptOrder.isEmpty()) {
            throw Exception("所有内置 API Key 均处于熔断禁用状态，请稍后再试。")
        }

        var lastException: Exception? = null
        val attempts = mutableListOf<String>()

        for (keyInfo in attemptOrder) {
            for (modelName in modelOrder) {
                try {
                    Log.d(TAG, "正在使用 Key ${keyInfo.id} 尝试调用 $modelName...")
                    val response = service.generateContent(
                        model = modelName,
                        apiKey = keyInfo.key,
                        request = request
                    )
                    // 成功调用，更新 lastUsedKeyId 并返回响应
                    ApiKeyPool.setLastUsedKeyId(context, keyInfo.id)
                    Log.d(TAG, "Key ${keyInfo.id} / $modelName 调用成功！")
                    return response
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    Log.w(TAG, "Key ${keyInfo.id} / $modelName 调用失败，HTTP 状态码: $code")
                    attempts.add("${keyInfo.id}/$modelName: HTTP $code")
                    if (code == 429) {
                        // 遇到 429，触发熔断 24 小时
                        ApiKeyPool.banKey(context, keyInfo.id)
                        Log.w(TAG, "已熔断 Key ${keyInfo.id} 24小时")
                        // 429 频控直接跳过该 Key 的其他模型，进入下一个 Key
                        break
                    }
                    lastException = e
                } catch (e: Exception) {
                    Log.w(TAG, "Key ${keyInfo.id} / $modelName 调用失败，非 HTTP 异常: ${e.message}")
                    attempts.add("${keyInfo.id}/$modelName: ${e.message ?: "未知错误"}")
                    lastException = e
                }
            }
        }
        val detail = attempts.joinToString(", ")
        throw Exception("请求失败，已尝试切换模型和 API Key。细节: [$detail]. 错误: ${lastException?.message ?: "未知错误"}")
    }
}
