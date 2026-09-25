package com.elio.jianyu.skill.knowledge

import kotlinx.serialization.Serializable

const val SKILL_KNOWLEDGE_CONTEXT_BUDGET_CHARACTERS = 9_000

@Serializable
enum class SkillKnowledgeDocumentType {
    CORE,
    KNOWLEDGE,
    SUPPORTING,
}

@Serializable
data class SkillKnowledgeChunk(
    val chunkId: String,
    val documentId: String,
    val headingPath: String,
    val startCharacter: Int,
    val endCharacter: Int,
    val vectorOffsetBytes: Int,
    val vectorLength: Int,
    val embeddingTextHash: String,
)

@Serializable
data class SkillKnowledgeDocument(
    val documentId: String,
    val skillId: String,
    val relativePath: String,
    val title: String,
    val type: SkillKnowledgeDocumentType,
    val contentHash: String,
    val retrievalEligible: Boolean,
    val chunks: List<SkillKnowledgeChunk> = emptyList(),
)

@Serializable
data class SkillKnowledgeSkill(
    val skillId: String,
    val skillName: String,
    val assetRoot: String,
    val documents: List<SkillKnowledgeDocument>,
)

@Serializable
internal data class SkillKnowledgeManifest(
    val schemaVersion: Int,
    val model: String,
    val vectorDimension: Int,
    val vectorEncoding: String,
    val skills: List<SkillKnowledgeSkill>,
)

data class SkillKnowledgeSelection(
    val skillId: String,
    val documentId: String,
    val title: String,
    val relativePath: String,
    val content: String,
    val contentHash: String,
)

data class SkillKnowledgeHit(
    val skillId: String,
    val documentId: String,
    val relativePath: String,
    val title: String,
    val headingPath: String,
    val content: String,
    val score: Float,
    val retrievalOrder: Int,
)

fun interface SkillKnowledgeQueryEmbedder {
    suspend fun embed(
        sessionId: Long,
        currentUserInput: String,
        onAttemptStarted: suspend () -> Unit,
    ): FloatArray
}

fun interface SkillKnowledgeRetrievalGateway {
    suspend fun retrieve(
        ownerSkillId: String,
        sessionId: Long,
        currentUserInput: String,
        onAttemptStarted: suspend () -> Unit = {},
    ): SkillKnowledgeRetrievalResult
}

sealed interface SkillKnowledgeRetrievalResult {
    data class Available(
        val knowledgeMap: String,
        val hits: List<SkillKnowledgeHit>,
    ) : SkillKnowledgeRetrievalResult

    data class Unavailable(
        val reasonCode: String,
    ) : SkillKnowledgeRetrievalResult
}
