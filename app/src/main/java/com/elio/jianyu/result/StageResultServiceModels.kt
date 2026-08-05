package com.elio.jianyu.result

import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.MaterialUsageSnapshotEntity
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.StageSummaryDraftEntity
import com.elio.jianyu.data.StageSummaryDraftRevisionEntity

data class SaveStageResultDraftCommand(
    val issueId: String,
    val stageId: String,
    val draftId: String,
    val revisionId: String,
    val expectedCurrentRevision: Int,
    val content: String,
    val savedAt: Long,
)

sealed interface StageDraftWriteResult {
    data class Saved(
        val draft: StageSummaryDraftEntity,
        val revision: StageSummaryDraftRevisionEntity,
    ) : StageDraftWriteResult

    data class Unchanged(
        val draft: StageSummaryDraftEntity,
    ) : StageDraftWriteResult

    data object Conflict : StageDraftWriteResult

    data class Failure(
        val errorCode: String,
    ) : StageDraftWriteResult
}

sealed interface StageDraftAbandonResult {
    data object Abandoned : StageDraftAbandonResult

    data class Failure(
        val errorCode: String,
    ) : StageDraftAbandonResult
}

data class ConfirmStageArtifactCommand(
    val artifactId: String,
    val issueId: String,
    val stageId: String,
    val draftRevisionId: String,
    val title: String,
    val artifactType: ArtifactType,
    val selectedMessageIds: List<Long>,
    val revisionOfArtifactId: String? = null,
    val confirmedAt: Long,
)

sealed interface StageArtifactConfirmationResult {
    data class Confirmed(
        val artifact: ConfirmedArtifactEntity,
    ) : StageArtifactConfirmationResult

    data class Failure(
        val errorCode: String,
    ) : StageArtifactConfirmationResult
}

data class StageResultWorkspace(
    val issueId: String,
    val stageId: String,
    val draft: StageSummaryDraftEntity?,
    val draftRevisions: List<StageSummaryDraftRevisionEntity>,
    val artifacts: List<ConfirmedArtifactEntity>,
    val selectableMessages: List<Message>,
    val materialUsages: List<MaterialUsageSnapshotEntity>,
    val artifactRevisionResolution: ArtifactRevisionResolution,
)

sealed interface StageResultLoadResult {
    data class Ready(
        val workspace: StageResultWorkspace,
    ) : StageResultLoadResult

    data class Failure(
        val errorCode: String,
    ) : StageResultLoadResult
}
