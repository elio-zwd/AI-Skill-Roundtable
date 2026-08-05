package com.elio.jianyu.ui.screens.execution

import com.elio.jianyu.data.CrossDiscussionStatus

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
        val input: String = "",
        val roster: List<CollaborationParticipantUi> = emptyList(),
        val messages: List<CollaborationMessageUi> = emptyList(),
        val dialogMode: CollaborationDialogMode? = null,
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

        val canOpenDirected: Boolean
            get() = hasRoster && input.isNotBlank() && !operationInProgress

        val canOpenCross: Boolean
            get() = roster.size >= 2 && input.isNotBlank() && !operationInProgress

        val canConfirmDirected: Boolean
            get() = dialogMode == CollaborationDialogMode.DIRECTED &&
                input.isNotBlank() && selectedParticipants.size == 1 && !operationInProgress

        val canConfirmCross: Boolean
            get() = dialogMode == CollaborationDialogMode.CROSS &&
                input.isNotBlank() && selectedParticipants.size >= 2 && !operationInProgress

        val estimatedCrossCalls: Int
            get() = selectedParticipants.size + 1
    }
}
