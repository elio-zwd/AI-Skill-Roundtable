package com.elio.jianyu.ui.screens.execution

import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.Material
import com.elio.jianyu.data.StageAdvancementMeasure
import com.elio.jianyu.data.StageAdvancementSkillPlan
import com.elio.jianyu.data.StageEntity

data class AdvanceIssueCandidates(
    val issueId: String,
    val viewedStageId: String?,
    val currentStage: StageEntity,
    val stages: List<StageEntity>,
    val roster: List<StageAdvancementSkillPlan>,
    val materials: List<Material>,
    val artifacts: List<ConfirmedArtifactEntity>,
    val defaultMaterialIds: Set<String>,
    val defaultArtifactIds: Set<String>,
    val hasBlockingRun: Boolean,
    val hasUnfinishedDiscussion: Boolean,
    val currentStageHasDraft: Boolean,
    val undoAvailable: Boolean,
)

data class AdvanceIssueDraft(
    val operationId: String,
    val newStageId: String,
    val realitySupport: Boolean = false,
    val thinkingExpansion: Boolean = false,
    val measures: Set<StageAdvancementMeasure> = emptySet(),
    val objective: String = "",
    val expectedOutput: String = "行动计划",
    val selectedMaterialIds: Set<String> = emptySet(),
    val selectedArtifactIds: Set<String> = emptySet(),
    val roster: List<StageAdvancementSkillPlan> = emptyList(),
    val summaryRevision: Long = 0L,
    val confirmedRevision: Long? = null,
) {
    val hasDirection: Boolean
        get() = realitySupport || thinkingExpansion

    val canEnterSummary: Boolean
        get() = hasDirection && measures.isNotEmpty() && objective.isNotBlank() &&
            expectedOutput.isNotBlank() && roster.isNotEmpty()

    val summaryIsCurrent: Boolean
        get() = confirmedRevision == summaryRevision

    fun edited(transform: (AdvanceIssueDraft) -> AdvanceIssueDraft): AdvanceIssueDraft {
        val updated = transform(this)
        return updated.copy(
            summaryRevision = summaryRevision + 1,
            confirmedRevision = null,
        )
    }
}

sealed interface AdvanceIssueUiState {
    data object Idle : AdvanceIssueUiState
    data object LoadingCandidates : AdvanceIssueUiState

    data class DirectionStep(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
        val restored: Boolean = false,
    ) : AdvanceIssueUiState

    data class MeasureStep(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
    ) : AdvanceIssueUiState

    data class SummaryStep(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
    ) : AdvanceIssueUiState

    data class WaitingForRun(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
    ) : AdvanceIssueUiState

    data class StoppingCurrentRun(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
    ) : AdvanceIssueUiState

    data class CreatingStage(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
    ) : AdvanceIssueUiState

    data class Created(
        val issueId: String,
        val stageId: String,
        val idempotent: Boolean,
    ) : AdvanceIssueUiState

    data class CreateFailure(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
        val message: String,
    ) : AdvanceIssueUiState

    data class IdempotencyConflict(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
    ) : AdvanceIssueUiState

    data class UndoAvailable(
        val candidates: AdvanceIssueCandidates,
    ) : AdvanceIssueUiState

    data class Undoing(
        val candidates: AdvanceIssueCandidates,
    ) : AdvanceIssueUiState

    data class UndoFailure(
        val candidates: AdvanceIssueCandidates,
        val message: String,
    ) : AdvanceIssueUiState

    data class RestoredDraft(
        val candidates: AdvanceIssueCandidates,
        val draft: AdvanceIssueDraft,
    ) : AdvanceIssueUiState

    data class StorageFailure(
        val message: String,
    ) : AdvanceIssueUiState
}

sealed interface AdvanceIssueEvent {
    data class NavigateToStage(
        val issueId: String,
        val stageId: String,
    ) : AdvanceIssueEvent

    data object RequestStopCurrentRun : AdvanceIssueEvent
}
