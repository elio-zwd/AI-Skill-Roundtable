package com.elio.jianyu.skill.knowledge

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SkillKnowledgeAssetRepositoryTest {
    @Test
    fun repositoryScopesDocumentsToRequestedSkillAndReads768Vector() {
        val manifest = manifestForTwoSkills(
            feynmanHash = sha256("费曼正文"),
            mungerHash = sha256("芒格正文"),
        )
        val floats = FloatArray(768) { index -> index / 1000f }
        val indexBytes = ByteBuffer.allocate(768 * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { floats.forEach(::putFloat) }
            .array()
        val reader = FakeSkillKnowledgeAssetReader(
            mapOf(
                "skill_knowledge/manifest.json" to manifest.toByteArray(),
                "skill_knowledge/index-v1.bin" to indexBytes,
                "skills/feynman-skill-main/references/research.md" to "费曼正文".toByteArray(),
                "skills/munger-skill-main/references/research.md" to "芒格正文".toByteArray(),
            ),
        )
        val repository = SkillKnowledgeAssetRepository(reader)

        assertEquals(
            listOf("feynman-doc"),
            repository.listDocuments("richard_feynman").map { it.documentId },
        )
        assertEquals("费曼正文", repository.loadDocumentContent("richard_feynman", "feynman-doc"))
        val vector = repository.loadVector(
            repository.listDocuments("richard_feynman").single().chunks.single(),
        )
        assertEquals(768, vector.size)
        assertEquals(floats.last(), vector.last(), 0.0001f)
    }

    @Test
    fun repositoryRejectsChangedMarkdownContent() {
        val manifest = manifestForSingleDocument(
            contentHash = sha256("原始正文"),
            vectorLength = 768,
        )
        val reader = FakeSkillKnowledgeAssetReader(
            mapOf(
                "skill_knowledge/manifest.json" to manifest.toByteArray(),
                "skill_knowledge/index-v1.bin" to ByteArray(768 * 4),
                "skills/feynman-skill-main/references/research.md" to "被修改正文".toByteArray(),
            ),
        )
        val repository = SkillKnowledgeAssetRepository(reader)

        assertThrows(IllegalStateException::class.java) {
            repository.loadDocumentContent("richard_feynman", "doc")
        }
    }

    @Test
    fun repositoryRejectsNon768ChunkVector() {
        val manifest = manifestForSingleDocument(
            contentHash = sha256("正文"),
            vectorLength = 767,
        )
        val reader = FakeSkillKnowledgeAssetReader(
            mapOf(
                "skill_knowledge/manifest.json" to manifest.toByteArray(),
                "skill_knowledge/index-v1.bin" to ByteArray(768 * 4),
                "skills/feynman-skill-main/references/research.md" to "正文".toByteArray(),
            ),
        )
        val repository = SkillKnowledgeAssetRepository(reader)
        val chunk = repository.listDocuments("richard_feynman").single().chunks.single()

        assertThrows(IllegalStateException::class.java) {
            repository.loadVector(chunk)
        }
    }

    private fun manifestForTwoSkills(feynmanHash: String, mungerHash: String): String = """
        {
          "schemaVersion": 1,
          "model": "gemini-embedding-2",
          "vectorDimension": 768,
          "vectorEncoding": "float32-le",
          "skills": [
            {
              "skillId": "richard_feynman",
              "skillName": "理查德·费曼",
              "assetRoot": "skills/feynman-skill-main",
              "documents": [
                {
                  "documentId": "feynman-doc",
                  "skillId": "richard_feynman",
                  "relativePath": "references/research.md",
                  "title": "费曼研究",
                  "type": "KNOWLEDGE",
                  "contentHash": "$feynmanHash",
                  "retrievalEligible": true,
                  "chunks": [
                    {
                      "chunkId": "feynman-chunk",
                      "documentId": "feynman-doc",
                      "headingPath": "教学",
                      "startCharacter": 0,
                      "endCharacter": 4,
                      "vectorOffsetBytes": 0,
                      "vectorLength": 768,
                      "embeddingTextHash": "hash"
                    }
                  ]
                }
              ]
            },
            {
              "skillId": "charlie_munger",
              "skillName": "查理·芒格",
              "assetRoot": "skills/munger-skill-main",
              "documents": [
                {
                  "documentId": "munger-doc",
                  "skillId": "charlie_munger",
                  "relativePath": "references/research.md",
                  "title": "芒格研究",
                  "type": "KNOWLEDGE",
                  "contentHash": "$mungerHash",
                  "retrievalEligible": true,
                  "chunks": []
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun manifestForSingleDocument(contentHash: String, vectorLength: Int): String = """
        {
          "schemaVersion": 1,
          "model": "gemini-embedding-2",
          "vectorDimension": 768,
          "vectorEncoding": "float32-le",
          "skills": [
            {
              "skillId": "richard_feynman",
              "skillName": "理查德·费曼",
              "assetRoot": "skills/feynman-skill-main",
              "documents": [
                {
                  "documentId": "doc",
                  "skillId": "richard_feynman",
                  "relativePath": "references/research.md",
                  "title": "研究",
                  "type": "KNOWLEDGE",
                  "contentHash": "$contentHash",
                  "retrievalEligible": true,
                  "chunks": [
                    {
                      "chunkId": "chunk",
                      "documentId": "doc",
                      "headingPath": "主题",
                      "startCharacter": 0,
                      "endCharacter": 2,
                      "vectorOffsetBytes": 0,
                      "vectorLength": $vectorLength,
                      "embeddingTextHash": "hash"
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class FakeSkillKnowledgeAssetReader(
        private val assets: Map<String, ByteArray>,
    ) : SkillKnowledgeAssetReader {
        override fun readBytes(path: String): ByteArray =
            assets[path] ?: error("Missing fake asset: $path")
    }
}
