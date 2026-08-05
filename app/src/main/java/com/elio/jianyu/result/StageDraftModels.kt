package com.elio.jianyu.result

import com.elio.jianyu.data.ConfirmedArtifactEntity

data class StageDraftSeed(
    val content: String,
    val defaultArtifactType: ArtifactType = ArtifactType.DEFAULT,
    val sourceMessages: List<StageMessageCandidate> = emptyList(),
    val confirmed: Boolean = false,
)

data class StageMessageCandidate(
    val messageId: Long,
    val runId: String?,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val pending: Boolean,
)

enum class StageMessageSelectionError {
    DUPLICATE_MESSAGE_ID,
    MESSAGE_NOT_FOUND,
    MESSAGE_PENDING,
    MESSAGE_SCOPE_MISMATCH,
}

sealed interface StageMessageSelectionResult {
    data class Selected(
        val messages: List<StageMessageCandidate>,
    ) : StageMessageSelectionResult

    data class Rejected(
        val error: StageMessageSelectionError,
    ) : StageMessageSelectionResult
}

sealed interface StageDraftSavePlan {
    data object Unchanged : StageDraftSavePlan

    data class Persist(
        val revisionNumber: Int,
    ) : StageDraftSavePlan
}

enum class StageDraftSaveFailure {
    REVISION_CONFLICT,
    STORAGE_FAILURE,
}

data class ArtifactSourcePlan(
    val messageIds: List<Long>,
    val runIds: List<String>,
    val draftRevisionIds: List<String>,
    val materialUsageSnapshotIds: List<String>,
)

enum class ArtifactSourceError {
    DRAFT_SCOPE_MISMATCH,
    DUPLICATE_MESSAGE_ID,
    MESSAGE_PENDING,
    MESSAGE_SCOPE_MISMATCH,
    MATERIAL_SCOPE_MISMATCH,
}

sealed interface ArtifactSourceBuildResult {
    data class Ready(
        val plan: ArtifactSourcePlan,
    ) : ArtifactSourceBuildResult

    data class Rejected(
        val error: ArtifactSourceError,
    ) : ArtifactSourceBuildResult
}

enum class ArtifactRevisionProblemCode {
    ORPHAN_REFERENCE,
    SELF_CYCLE,
    CYCLE,
    CROSS_ISSUE,
    CROSS_STAGE,
    FORK,
}

data class ArtifactRevisionProblem(
    val code: ArtifactRevisionProblemCode,
    val artifactIds: Set<String>,
)

data class ArtifactRevisionChain(
    val rootArtifactId: String,
    val versions: List<ConfirmedArtifactEntity>,
)

data class ArtifactRevisionResolution(
    val allArtifacts: List<ConfirmedArtifactEntity>,
    val chains: List<ArtifactRevisionChain>,
    val latestArtifactIds: Set<String>,
    val problems: List<ArtifactRevisionProblem>,
)

data class ArtifactLibraryItem(
    val artifactId: String,
    val issueId: String,
    val issueTitle: String,
    val stageId: String,
    val stageTitle: String,
    val title: String,
    val contentSummary: String,
    val artifactType: ArtifactType?,
    val rawArtifactType: String,
    val confirmedAt: Long,
    val revisionOfArtifactId: String?,
    val revisionNumber: Int,
    val latest: Boolean,
    val content: String = contentSummary,
)

data class ArtifactLibrarySnapshot(
    val items: List<ArtifactLibraryItem>,
    val revisionProblems: List<ArtifactRevisionProblem>,
)
