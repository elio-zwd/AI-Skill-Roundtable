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
     * 实现 API Key 的轮询、熔断与重试。
     * 策略：仅使用指定的 model 进行调用，如果调用失败或遇到 429 报错，
     * 将该 Key 熔断 24 小时，并依次重试下一个可用 Key。
     */
    suspend fun generateContentWithFallback(
        context: Context,
        model: String,
        request: GenerateContentRequest
    ): GenerateContentResponse {
        val attemptOrder = ApiKeyPool.getKeyAttemptOrder(context)
        if (attemptOrder.isEmpty()) {
            throw Exception("所有内置 API Key 均处于熔断禁用状态，请稍后再试。")
        }

        var lastException: Exception? = null
        val attempts = mutableListOf<String>()

        for (keyInfo in attemptOrder) {
            try {
                Log.d(TAG, "正在使用 Key ${keyInfo.id} 尝试调用 $model...")
                val response = service.generateContent(
                    model = model,
                    apiKey = keyInfo.key,
                    request = request
                )
                // 成功调用，更新 lastUsedKeyId 并返回响应
                ApiKeyPool.setLastUsedKeyId(context, keyInfo.id)
                Log.d(TAG, "Key ${keyInfo.id} / $model 调用成功！")
                return response
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                Log.w(TAG, "Key ${keyInfo.id} / $model 调用失败，HTTP 状态码: $code")
                attempts.add("${keyInfo.id}/$model: HTTP $code")
                if (code == 429) {
                    // 遇到 429，触发熔断 24 小时
                    ApiKeyPool.banKey(context, keyInfo.id)
                    Log.w(TAG, "已熔断 Key ${keyInfo.id} 24小时")
                }
                lastException = e
            } catch (e: Exception) {
                Log.w(TAG, "Key ${keyInfo.id} / $model 调用失败，非 HTTP 异常: ${e.message}")
                attempts.add("${keyInfo.id}/$model: ${e.message ?: "未知错误"}")
                lastException = e
            }
        }
        val detail = attempts.joinToString(", ")
        throw Exception("请求失败，已尝试切换 API Key 轮询。细节: [$detail]. 错误: ${lastException?.message ?: "未知错误"}")
    }
}
