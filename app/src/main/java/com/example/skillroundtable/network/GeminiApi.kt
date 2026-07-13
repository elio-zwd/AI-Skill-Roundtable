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
    val content: Content,
    val groundingMetadata: GroundingMetadata? = null
)

@Serializable
data class Tool(
    val google_search: GoogleSearch? = null
)

@Serializable
class GoogleSearch

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

// Embedding 接口相关的实体类
@Serializable
data class EmbedContentRequest(
    val model: String = "models/text-embedding-004",
    val content: Content
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
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/text-embedding-004:embedContent")
    suspend fun embedContent(
        @Query("key") apiKey: String,
        @Body request: EmbedContentRequest
    ): EmbedContentResponse
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
     * 对话级 API Key 绑定，在出错熔断时自动换绑。
     */
    suspend fun generateContentWithFallback(
        context: Context,
        model: String,
        request: GenerateContentRequest,
        sessionId: Long
    ): GenerateContentResponse {
        var lastException: Exception? = null
        val attempts = mutableListOf<String>()

        for (attempt in 0 until ApiKeyPool.API_KEYS.size) {
            val keyInfo = ApiKeyPool.getOrBindSessionKey(context, sessionId)
            if (keyInfo == null) {
                throw Exception("当前无可用内置 API Key，全部已被频控熔断。")
            }

            try {
                Log.d(TAG, "正在使用会话级 Key ${keyInfo.id} 尝试调用 $model...")
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

            // 出错时，自动换绑下一个可用的 Key
            val nextKey = ApiKeyPool.getAvailableKeys(context).firstOrNull { it.id != keyInfo.id }
            if (nextKey != null) {
                Log.d(TAG, "会话 $sessionId 失败，换绑下一个 Key: ${nextKey.id}")
                ApiKeyPool.bindSessionKey(context, sessionId, nextKey.id)
            } else {
                Log.w(TAG, "无其他备用可用 Key 可为会话 $sessionId 换绑")
            }
        }
        val detail = attempts.joinToString(", ")
        throw Exception("请求失败，已尝试轮询会话换绑 API Key。细节: [$detail]. 错误: ${lastException?.message ?: "未知错误"}")
    }

    /**
     * 调用 Gemini 1M Context Broker 路由器。
     * 策略：使用指定的 model（通常为 gemini-3.1-flash-lite-preview），
     * 在失败时换绑下一个 Key 重新请求。
     */
    suspend fun callBrokerRouterWithFallback(
        context: Context,
        model: String,
        request: GenerateContentRequest,
        sessionId: Long
    ): GenerateContentResponse {
        var lastException: Exception? = null
        val attempts = mutableListOf<String>()

        for (attempt in 0 until ApiKeyPool.API_KEYS.size) {
            val keyInfo = ApiKeyPool.getOrBindSessionKey(context, sessionId)
            if (keyInfo == null) {
                throw Exception("当前无可用内置 API Key，全部已被熔断。")
            }

            try {
                Log.d(TAG, "正在使用会话级 Key ${keyInfo.id} 尝试调用 Broker $model...")
                val response = service.generateContent(
                    model = model,
                    apiKey = keyInfo.key,
                    request = request
                )
                ApiKeyPool.setLastUsedKeyId(context, keyInfo.id)
                Log.d(TAG, "Key ${keyInfo.id} / Broker $model 调用成功！")
                return response
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                Log.w(TAG, "Key ${keyInfo.id} / Broker $model 失败，状态码: $code")
                attempts.add("${keyInfo.id}/$model: HTTP $code")
                if (code == 429) {
                    ApiKeyPool.banKey(context, keyInfo.id)
                    Log.w(TAG, "已熔断 Key ${keyInfo.id} 24小时")
                }
                lastException = e
            } catch (e: Exception) {
                Log.w(TAG, "Key ${keyInfo.id} / Broker $model 失败，非 HTTP 异常: ${e.message}")
                attempts.add("${keyInfo.id}/$model: ${e.message ?: "未知错误"}")
                lastException = e
            }

            // 出错时，自动换绑下一个可用的 Key
            val nextKey = ApiKeyPool.getAvailableKeys(context).firstOrNull { it.id != keyInfo.id }
            if (nextKey != null) {
                Log.d(TAG, "会话 $sessionId Broker 失败，换绑下一个 Key: ${nextKey.id}")
                ApiKeyPool.bindSessionKey(context, sessionId, nextKey.id)
            } else {
                Log.w(TAG, "无其他备用可用 Key 可为会话 $sessionId 换绑")
            }
        }
        val detail = attempts.joinToString(", ")
        throw Exception("Broker 路由器请求失败。细节: [$detail]. 错误: ${lastException?.message ?: "未知错误"}")
    }

    /**
     * 提取输入文本的 Embedding 向量。
     * 策略：使用会话级 API Key 绑定，在出错熔断时换绑。
     */
    suspend fun embedContentWithFallback(
        context: Context,
        text: String,
        sessionId: Long
    ): List<Float> {
        val request = EmbedContentRequest(
            content = Content(parts = listOf(Part(text = text)))
        )

        var lastException: Exception? = null
        val attempts = mutableListOf<String>()

        for (attempt in 0 until ApiKeyPool.API_KEYS.size) {
            val keyInfo = ApiKeyPool.getOrBindSessionKey(context, sessionId)
            if (keyInfo == null) {
                throw Exception("当前无可用内置 API Key 获取 Embedding，全部已被熔断。")
            }

            try {
                Log.d(TAG, "正在使用会话级 Key ${keyInfo.id} 获取 Embedding...")
                val response = service.embedContent(
                    apiKey = keyInfo.key,
                    request = request
                )
                ApiKeyPool.setLastUsedKeyId(context, keyInfo.id)
                Log.d(TAG, "Key ${keyInfo.id} 获取 Embedding 成功！")
                return response.embedding.values
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                Log.w(TAG, "Key ${keyInfo.id} 获取 Embedding 失败，HTTP 状态码: $code")
                attempts.add("${keyInfo.id}: HTTP $code")
                if (code == 429) {
                    ApiKeyPool.banKey(context, keyInfo.id)
                    Log.w(TAG, "已熔断 Key ${keyInfo.id} 24小时")
                }
                lastException = e
            } catch (e: Exception) {
                Log.w(TAG, "Key ${keyInfo.id} 获取 Embedding 失败，非 HTTP 异常: ${e.message}")
                attempts.add("${keyInfo.id}: ${e.message ?: "未知错误"}")
                lastException = e
            }

            // 出错时，自动换绑下一个可用的 Key
            val nextKey = ApiKeyPool.getAvailableKeys(context).firstOrNull { it.id != keyInfo.id }
            if (nextKey != null) {
                Log.d(TAG, "会话 $sessionId Embedding 失败，换绑下一个 Key: ${nextKey.id}")
                ApiKeyPool.bindSessionKey(context, sessionId, nextKey.id)
            } else {
                Log.w(TAG, "无其他备用可用 Key 可为会话 $sessionId 换绑")
            }
        }
        val detail = attempts.joinToString(", ")
        throw Exception("获取 Embedding 失败，已尝试轮询 Key。细节: [$detail]. 错误: ${lastException?.message ?: "未知错误"}")
    }
}
