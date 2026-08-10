package com.elio.jianyu.ui.screens.execution

import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionThinkingLevel
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.execution.SearchMode
import com.elio.jianyu.ui.screens.context.ContextConfirmationUiState

enum class IssueExecutionPhase {
    IDLE,
    READY,
    RUNNING,
    PARTIAL_SUCCESS,
    SUCCEEDED,
    RETRYABLE,
    STOPPED,
    FAILED,
    RECOVERING,
    NO_API_KEY,
    OFFLINE,
    RATE_LIMITED,
    BUDGET_EXHAUSTED,
}

data class IssueExecutionParticipantUi(
    val snapshotId: String,
    val displayName: String,
    val position: Int,
    val status: ExecutionParticipantStatus,
    val attemptCount: Int,
    val text: String?,
    val isPending: Boolean,
    val hasIncompleteOutput: Boolean,
    val errorCode: String?,
    val errorMessage: String?,
)

data class IssueExecutionBudgetUi(
    val maxApiCalls: Int,
    val usedApiCalls: Int,
    val reservedRequiredCalls: Int,
    val closed: Boolean,
) {
    val remainingApiCalls: Int
        get() = (maxApiCalls - usedApiCalls).coerceAtLeast(0)
}

sealed interface IssueExecutionUiState {
    data object Loading : IssueExecutionUiState

    data class Failure(
        val title: String,
        val message: String,
        val storageFailure: Boolean,
    ) : IssueExecutionUiState

    data class Content(
        val issueId: String,
        val issueTitle: String,
        val stageId: String?,
        val stageTitle: String?,
        val phase: IssueExecutionPhase,
        val runId: String?,
        val runStatus: ExecutionRunStatus?,
        val participants: List<IssueExecutionParticipantUi>,
        val budget: IssueExecutionBudgetUi?,
        val failureCode: String?,
        val failureMessage: String?,
        val executionAvailable: Boolean,
        val canStop: Boolean,
        val canRetry: Boolean,
        val canRecoverInterrupted: Boolean,
        val issueDefaultThinkingPolicy: IssueThinkingPolicy = IssueThinkingPolicy.AUTO,
        val thinkingOverride: IssueThinkingPolicy? = null,
        val canChangeIssueDefaultThinkingPolicy: Boolean = false,
        val actualModelId: String? = null,
        val actualThinkingLevel: ExecutionThinkingLevel? = null,
        val thinkingLevelSource: ExecutionThinkingSource? = null,
        /** 仅影响尚未创建的下一次 Run；当前 Run 的请求已经冻结。 */
        val searchMode: SearchMode = SearchMode.AUTO,
        val contextConfirmation: ContextConfirmationUiState? = null,
        val operationInProgress: Boolean = false,
    ) : IssueExecutionUiState
}
