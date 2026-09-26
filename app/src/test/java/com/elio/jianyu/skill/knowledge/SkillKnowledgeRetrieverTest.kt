package com.elio.jianyu.skill.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillKnowledgeRetrieverTest {
    @Test
    fun retrievalStaysInsideOwnerSkillAndCapsHitsPerDocumentAndBudget() = runBlocking {
        val repository = FakeRepository(
            documents = buildList {
                addAll(documentsFor("richard_feynman", "a", 6, baseScore = 1.0f))
                addAll(documentsFor("richard_feynman", "b", 6, baseScore = 0.9f))
                addAll(documentsFor("charlie_munger", "m", 4, baseScore = 2.0f))
            },
        )
        val embedder = SkillKnowledgeQueryEmbedder { _, _, _ ->
            floatArrayOf(1f, 0f)
        }
        val retriever = SkillKnowledgeRetriever(
            repository = repository,
            embedder = embedder,
            embeddingDimension = 2,
            candidateLimit = 12,
            hitLimit = 8,
            maxPerDocument = 2,
            maxContextCharacters = 140,
        )

        val result = retriever.retrieve(
            ownerSkillId = "richard_feynman",
            sessionId = 1L,
            currentUserInput = "怎么解释复杂概念？",
            onAttemptStarted = {},
        ) as SkillKnowledgeRetrievalResult.Available

        assertTrue(result.hits.isNotEmpty())
        assertTrue(result.hits.all { it.skillId == "richard_feynman" })
        assertFalse(result.hits.any { it.skillId == "charlie_munger" })
        assertTrue(
            result.hits.groupingBy { it.documentId }.eachCount().values.all { it <= 2 },
        )
        assertTrue(result.hits.sumOf { it.content.length } <= 140)
        assertTrue(result.hits.size <= 8)
        assertEquals(
            result.hits.map { it.score }.sortedDescending(),
            result.hits.map { it.score },
        )
    }

    @Test
    fun embeddingFailureBecomesUnavailableInsteadOfThrowing() = runBlocking {
        val repository = FakeRepository(
            documents = documentsFor("richard_feynman", "a", 1, baseScore = 1f),
        )
        val retriever = SkillKnowledgeRetriever(
            repository = repository,
            embedder = SkillKnowledgeQueryEmbedder { _, _, _ -> error("network down") },
            embeddingDimension = 2,
        )

        val result = retriever.retrieve(
            ownerSkillId = "richard_feynman",
            sessionId = 1L,
            currentUserInput = "问题",
            onAttemptStarted = {},
        )

        assertTrue(result is SkillKnowledgeRetrievalResult.Unavailable)
    }

    private fun documentsFor(
        skillId: String,
        documentId: String,
        count: Int,
        baseScore: Float,
    ): List<SkillKnowledgeDocument> {
        val chunks = (0 until count).map { index ->
            SkillKnowledgeChunk(
                chunkId = "$skillId-$documentId-$index",
                documentId = documentId,
                headingPath = "主题 $index",
                startCharacter = index * 40,
                endCharacter = index * 40 + 30,
                vectorOffsetBytes = index * 8,
                vectorLength = 2,
                embeddingTextHash = "hash-$index",
            )
        }
        return listOf(
            SkillKnowledgeDocument(
                documentId = documentId,
                skillId = skillId,
                assetPath = "skills/$skillId/references/$documentId.md",
                relativePath = "references/$documentId.md",
                title = "$documentId title",
                type = SkillKnowledgeDocumentType.KNOWLEDGE,
                contentHash = "content-$documentId",
                retrievalEligible = true,
                chunks = chunks,
            ),
        )
    }

    private class FakeRepository(
        private val documents: List<SkillKnowledgeDocument>,
    ) : SkillKnowledgeRepository {
        override fun listSkills(): List<SkillKnowledgeSkill> =
            documents.groupBy { it.skillId }.map { (skillId, docs) ->
                SkillKnowledgeSkill(skillId, skillId, "skills/$skillId", docs)
            }

        override fun listDocuments(skillId: String): List<SkillKnowledgeDocument> =
            documents.filter { it.skillId == skillId }

        override fun loadDocumentContent(skillId: String, documentId: String): String =
            buildString {
                repeat(20) { index ->
                    append("chunk-$documentId-$index 内容内容内容。")
                    append('\n')
                }
            }

        override fun loadChunkContent(
            skillId: String,
            document: SkillKnowledgeDocument,
            chunk: SkillKnowledgeChunk,
        ): String = "内容-${document.documentId}-${chunk.chunkId}".repeat(2)

        override fun loadVector(chunk: SkillKnowledgeChunk): FloatArray {
            val score = when {
                chunk.chunkId.contains("-a-") -> 1f - chunk.startCharacter / 1000f
                chunk.chunkId.contains("-b-") -> 0.9f - chunk.startCharacter / 1000f
                else -> 2f
            }
            return floatArrayOf(score, 1f - score.coerceAtMost(1f))
        }
    }
}
