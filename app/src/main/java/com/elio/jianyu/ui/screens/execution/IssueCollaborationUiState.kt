package com.elio.jianyu.ui.screens.execution

import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.data.ExecutionRunStatus

enum class CollaborationDialogMode {
    DIRECTED,
    CROSS,
}

data class CollaborationParticipantUi(
    val skillId: String,
    val displayName: String,
    val avatar: String,
    val responsibility: String,
    val position: Int,
    val selected: Boolean,
)

data class CollaborationMessageUi(
    val messageId: Long,
    val senderName: String,
    val preview: String,
    val selected: Boolean,
)

data class DirectedResponseRunUi(
    val runId: String,
    val skillId: String,
    val displayName: String,
    val question: String,
    val status: ExecutionRunStatus,
    val hasIncompleteOutput: Boolean,
) {
    val canRetry: Boolean
        get() = status == ExecutionRunStatus.RETRYABLE ||
            status == ExecutionRunStatus.STOPPED
}

data class CrossDiscussionSessionUi(
    val sessionId: String,
    val status: CrossDiscussionStatus,
    val focus: String,
    val responseRunId: String,
    val synthesisRunId: String?,
    val integratorSkillId: String,
    val successfulSkillIds: List<String>,
    val failedSkillIds: List<String>,
) {
    val canRetryFailed: Boolean
        get() = status in setOf(
            CrossDiscussionStatus.PARTIAL_SUCCESS,
            CrossDiscussionStatus.FAILED,
            CrossDiscussionStatus.STOPPED,
        ) && failedSkillIds.isNotEmpty()

    val canSynthesize: Boolean
        get() = status == CrossDiscussionStatus.AWAITING_SYNTHESIS ||
            (status == CrossDiscussionStatus.PARTIAL_SUCCESS && successfulSkillIds.isNotEmpty())

    val canRetrySynthesis: Boolean
        get() = status == CrossDiscussionStatus.SYNTHESIS_RETRYABLE
}

sealed interface IssueCollaborationUiState {
    data object Loading : IssueCollaborationUiState

    data class Failure(
        val message: String,
        val catalogUnavailable: Boolean = false,
        val storageFailure: Boolean = false,
    ) : IssueCollaborationUiState

    data class Content(
        val issueId: String,
        val stageId: String,
        val isCurrentStage: Boolean = true,
        val input: String = "",
        val roster: List<CollaborationParticipantUi> = emptyList(),
        val messages: List<CollaborationMessageUi> = emptyList(),
        val dialogMode: CollaborationDialogMode? = null,
        val directedRuns: List<DirectedResponseRunUi> = emptyList(),
        val sessions: List<CrossDiscussionSessionUi> = emptyList(),
        val integratorDisplayName: String = "会议行动助手（meeting-to-action）",
        val operationInProgress: Boolean = false,
        val errorMessage: String? = null,
    ) : IssueCollaborationUiState {
        val hasRoster: Boolean
            get() = roster.isNotEmpty()

        val selectedParticipants: List<CollaborationParticipantUi>
            get() = roster.filter(CollaborationParticipantUi::selected)

        val selectedMessageIds: List<Long>
            get() = messages.filter(CollaborationMessageUi::selected).map { it.messageId }

        val canSubmitStandard: Boolean
            get() = isCurrentStage && hasRoster && input.isNotBlank() && !operationInProgress

        val canOpenDirected: Boolean
            get() = isCurrentStage && hasRoster && input.isNotBlank() && !operationInProgress

        val canOpenCross: Boolean
            get() = isCurrentStage && roster.size >= 2 && input.isNotBlank() && !operationInProgress

        val canConfirmDirected: Boolean
            get() = isCurrentStage && dialogMode == CollaborationDialogMode.DIRECTED &&
                input.isNotBlank() && selectedParticipants.size == 1 && !operationInProgress

        val canConfirmCross: Boolean
            get() = isCurrentStage && dialogMode == CollaborationDialogMode.CROSS &&
                input.isNotBlank() && selectedParticipants.size >= 2 && !operationInProgress

        val estimatedCrossCalls: Int
            get() = selectedParticipants.size + 1
    }
}
