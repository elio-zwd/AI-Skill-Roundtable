package com.elio.jianyu.network

import android.content.Context
import com.elio.jianyu.execution.NoExecutionApiKeyException
import com.elio.jianyu.roundtable.RequestBudgetTracker
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

const val GEMINI_SKILL_KNOWLEDGE_EMBEDDING_MODEL = "gemini-embedding-2"
const val SKILL_KNOWLEDGE_EMBEDDING_DIMENSION = 768

private const val GEMINI_EMBEDDING_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/" +
        "$GEMINI_SKILL_KNOWLEDGE_EMBEDDING_MODEL:embedContent"

@Serializable
internal data class SkillKnowledgeEmbeddingRequest(
    val content: Content,
    @SerialName("output_dimensionality")
    val outputDimensionality: Int,
)

internal fun formatSkillKnowledgeQuery(text: String): String {
    val query = text.trim()
    require(query.isNotBlank()) { "Skill Knowledge query 不能为空" }
    return "task: search result | query: $query"
}

internal fun buildSkillKnowledgeEmbeddingRequest(text: String): SkillKnowledgeEmbeddingRequest =
    SkillKnowledgeEmbeddingRequest(
        content = Content(
            parts = listOf(
                Part(text = formatSkillKnowledgeQuery(text)),
            ),
        ),
        outputDimensionality = SKILL_KNOWLEDGE_EMBEDDING_DIMENSION,
    )

internal fun parseSkillKnowledgeEmbedding(rawJson: String): FloatArray {
    val response = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }.decodeFromString<EmbedContentResponse>(rawJson)
    require(response.embedding.values.size == SKILL_KNOWLEDGE_EMBEDDING_DIMENSION) {
        "Gemini Embedding 2 返回维度不匹配"
    }
    return response.embedding.values.toFloatArray()
}

/**
 * Skill Knowledge 专用 Gemini Embedding 2 边界。
 *
 * 复用统一 Gemini Key 池与请求重试器；只向调用方返回固定 768 维向量。
 */
object GeminiEmbeddingTransport {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun embedQuery(
        context: Context,
        sessionId: Long,
        query: String,
        onAttemptStarted: suspend () -> Unit = {},
    ): FloatArray {
        val appContext = context.applicationContext
        val attemptPlan = AiManager.keys(appContext, AiProvider.GEMINI)
            .createAttemptPlan(sessionId)
        if (attemptPlan.isEmpty()) throw NoExecutionApiKeyException()

        val requestBody = json
            .encodeToString(buildSkillKnowledgeEmbeddingRequest(query))
            .toRequestBody("application/json".toMediaType())

        return GeminiRestTransport.executeWithBudgetAndRetry(
            context = appContext,
            sessionId = sessionId,
            attemptPlan = attemptPlan,
            tracker = RequestBudgetTracker(),
            operationName = "SkillKnowledgeEmbedding",
            isRequired = false,
            onAttemptStarted = onAttemptStarted,
        ) { secret ->
            val httpRequest = Request.Builder()
                .url(GEMINI_EMBEDDING_ENDPOINT)
                .header("x-goog-api-key", secret)
                .post(requestBody)
                .build()
            GeminiRestTransport.okHttpClient
                .newCall(httpRequest)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        throw IOException(
                            "Gemini Embedding request failed with HTTP ${response.code}",
                        )
                    }
                    val body = response.body?.string()
                        ?: throw IOException("Gemini Embedding response body is empty")
                    parseSkillKnowledgeEmbedding(body)
                }
        }
    }
}
