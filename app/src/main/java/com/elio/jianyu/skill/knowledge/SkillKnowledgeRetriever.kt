package com.elio.jianyu.skill.knowledge

import com.elio.jianyu.network.SKILL_KNOWLEDGE_EMBEDDING_DIMENSION
import com.elio.jianyu.telemetry.PrivacySafeLogger
import kotlinx.coroutines.CancellationException
import kotlin.math.sqrt

class SkillKnowledgeRetriever(
    private val repository: SkillKnowledgeRepository,
    private val embedder: SkillKnowledgeQueryEmbedder,
    private val embeddingDimension: Int = SKILL_KNOWLEDGE_EMBEDDING_DIMENSION,
    private val candidateLimit: Int = 12,
    private val hitLimit: Int = 8,
    private val maxPerDocument: Int = 2,
    private val maxContextCharacters: Int = SKILL_KNOWLEDGE_CONTEXT_BUDGET_CHARACTERS,
) : SkillKnowledgeRetrievalGateway {
    init {
        require(embeddingDimension > 0)
        require(candidateLimit > 0)
        require(hitLimit > 0)
        require(maxPerDocument > 0)
        require(maxContextCharacters > 0)
    }

    override suspend fun retrieve(
        ownerSkillId: String,
        sessionId: Long,
        currentUserInput: String,
        onAttemptStarted: suspend () -> Unit,
    ): SkillKnowledgeRetrievalResult {
        if (ownerSkillId.isBlank() || currentUserInput.isBlank()) {
            return SkillKnowledgeRetrievalResult.Unavailable("invalid_query")
        }
        return try {
            val documents = repository.listDocuments(ownerSkillId)
            val knowledgeMap = buildKnowledgeMap(documents)
            val candidates = documents
                .asSequence()
                .filter {
                    it.skillId == ownerSkillId &&
                        it.retrievalEligible &&
                        it.type == SkillKnowledgeDocumentType.KNOWLEDGE
                }
                .flatMap { document ->
                    document.chunks.asSequence().map { chunk -> document to chunk }
                }
                .toList()

            if (candidates.isEmpty()) {
                return SkillKnowledgeRetrievalResult.Available(
                    knowledgeMap = knowledgeMap,
                    hits = emptyList(),
                )
            }

            val queryVector = embedder.embed(
                sessionId,
                currentUserInput,
                onAttemptStarted,
            )
            check(queryVector.size == embeddingDimension) {
                "Skill Knowledge query vector 维度不匹配"
            }

            val ranked = candidates
                .map { (document, chunk) ->
                    val vector = repository.loadVector(chunk)
                    check(vector.size == embeddingDimension) {
                        "Skill Knowledge index vector 维度不匹配"
                    }
                    RankedChunk(
                        document = document,
                        chunk = chunk,
                        score = cosineSimilarity(queryVector, vector),
                    )
                }
                .sortedWith(
                    compareByDescending<RankedChunk> { it.score }
                        .thenBy { it.chunk.chunkId },
                )
                .take(candidateLimit)

            val countsByDocument = mutableMapOf<String, Int>()
            val hits = mutableListOf<SkillKnowledgeHit>()
            var usedCharacters = 0
            for (rankedChunk in ranked) {
                if (hits.size >= hitLimit) break
                val document = rankedChunk.document
                val usedFromDocument = countsByDocument[document.documentId] ?: 0
                if (usedFromDocument >= maxPerDocument) continue

                val content = repository.loadChunkContent(
                    ownerSkillId,
                    document,
                    rankedChunk.chunk,
                ).trim()
                if (content.isBlank()) continue
                if (usedCharacters + content.length > maxContextCharacters) continue

                hits += SkillKnowledgeHit(
                    skillId = ownerSkillId,
                    documentId = document.documentId,
                    relativePath = document.relativePath,
                    title = document.title,
                    headingPath = rankedChunk.chunk.headingPath,
                    content = content,
                    score = rankedChunk.score,
                    retrievalOrder = hits.size,
                )
                countsByDocument[document.documentId] = usedFromDocument + 1
                usedCharacters += content.length
            }

            SkillKnowledgeRetrievalResult.Available(
                knowledgeMap = knowledgeMap,
                hits = hits,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PrivacySafeLogger.e(
                "SkillKnowledgeRetriever",
                "Skill Knowledge retrieval unavailable",
                error,
            )
            SkillKnowledgeRetrievalResult.Unavailable("skill_knowledge_unavailable")
        }
    }

    private fun buildKnowledgeMap(documents: List<SkillKnowledgeDocument>): String =
        documents
            .sortedWith(compareBy({ it.type.ordinal }, { it.relativePath }))
            .joinToString("\n") { document ->
                "- ${document.title} [${document.type.name}] · ${document.relativePath}"
            }

    private data class RankedChunk(
        val document: SkillKnowledgeDocument,
        val chunk: SkillKnowledgeChunk,
        val score: Float,
    )
}

internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size)
    require(a.isNotEmpty())
    var dot = 0.0
    var aa = 0.0
    var bb = 0.0
    for (index in a.indices) {
        val av = a[index].toDouble()
        val bv = b[index].toDouble()
        dot += av * bv
        aa += av * av
        bb += bv * bv
    }
    if (aa == 0.0 || bb == 0.0) return 0f
    return (dot / sqrt(aa * bb)).toFloat()
}
