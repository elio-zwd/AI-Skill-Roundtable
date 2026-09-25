package com.elio.jianyu.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeminiEmbeddingTransportTest {
    @Test
    fun formatSkillKnowledgeQueryUsesAsymmetricSearchPrefix() {
        assertEquals(
            "task: search result | query: AI 教育应该怎么做？",
            formatSkillKnowledgeQuery("  AI 教育应该怎么做？  "),
        )
    }

    @Test
    fun requestUsesGeminiEmbedding2And768Dimensions() {
        assertEquals("gemini-embedding-2", GEMINI_SKILL_KNOWLEDGE_EMBEDDING_MODEL)
        assertEquals(768, SKILL_KNOWLEDGE_EMBEDDING_DIMENSION)

        val request = buildSkillKnowledgeEmbeddingRequest("test")
        val body = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject

        assertEquals(
            "768",
            body.getValue("output_dimensionality").jsonPrimitive.content,
        )
        assertEquals(
            "task: search result | query: test",
            request.content.parts.single().text,
        )
    }

    @Test
    fun parseEmbeddingResponseRequiresExactly768Values() {
        val json = """
            {
              "embedding": {
                "values": [${List(768) { "0.25" }.joinToString(",")}]
              }
            }
        """.trimIndent()

        val vector = parseSkillKnowledgeEmbedding(json)

        assertEquals(768, vector.size)
        assertEquals(0.25f, vector.first(), 0.0001f)
    }

    @Test
    fun parseEmbeddingResponseRejectsWrongDimensions() {
        val json = """
            {
              "embedding": {
                "values": [${List(767) { "0.25" }.joinToString(",")}]
              }
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            parseSkillKnowledgeEmbedding(json)
        }
    }
}
