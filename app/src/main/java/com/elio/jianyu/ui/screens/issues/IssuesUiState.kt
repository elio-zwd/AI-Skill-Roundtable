package com.elio.jianyu.ui.screens.issues

import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueNavigationItem
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.RepositoryError

data class IssueNavigationUiItem(
    val issueId: String,
    val title: String,
    val lifecycleState: IssueLifecycleState,
    val currentStageId: String?,
    val currentStageTitle: String?,
    val activeOrRecoverableRunCount: Int,
    val updatedAt: Long,
)

sealed interface IssuesUiState {
    data object Loading : IssuesUiState

    data object Empty : IssuesUiState

    data class Content(
        val active: List<IssueNavigationUiItem>,
        val archived: List<IssueNavigationUiItem>,
        val trashed: List<IssueNavigationUiItem>,
    ) : IssuesUiState

    data class Failure(
        val message: String,
    ) : IssuesUiState
}

sealed interface IssueRecoveryUiState {
    data object Loading : IssueRecoveryUiState

    data class Content(
        val issueId: String,
        val title: String,
        val lifecycleState: IssueLifecycleState,
        val selectedStageId: String?,
        val selectedStageTitle: String?,
        val stageCount: Int,
        val activeOrRecoverableRunCount: Int,
    ) : IssueRecoveryUiState

    data class Failure(
        val message: String,
    ) : IssueRecoveryUiState
}

internal fun mapIssueNavigation(items: List<IssueNavigationItem>): IssuesUiState {
    if (items.isEmpty()) return IssuesUiState.Empty

    val mapped = items
        .map { item ->
            IssueNavigationUiItem(
                issueId = item.issue.id,
                title = item.issue.title,
                lifecycleState = item.lifecycle.state,
                currentStageId = item.currentStage?.id,
                currentStageTitle = item.currentStage?.title,
                activeOrRecoverableRunCount = item.activeRunCount,
                updatedAt = item.issue.updatedAt,
            )
        }
        .sortedByDescending { item -> item.updatedAt }

    return IssuesUiState.Content(
        active = mapped.filter { it.lifecycleState == IssueLifecycleState.ACTIVE },
        archived = mapped.filter { it.lifecycleState == IssueLifecycleState.ARCHIVED },
        trashed = mapped.filter { it.lifecycleState == IssueLifecycleState.TRASHED },
    )
}

internal fun mapIssueRecovery(
    snapshot: IssueRecoverySnapshot,
    requestedStageId: String?,
): IssueRecoveryUiState {
    val selectedStage = when {
        requestedStageId == null -> snapshot.core.currentStage
        else -> snapshot.core.stages.firstOrNull { stage -> stage.id == requestedStageId }
            ?: return IssueRecoveryUiState.Failure("指定阶段不属于该议题")
    }

    return IssueRecoveryUiState.Content(
        issueId = snapshot.core.issue.id,
        title = snapshot.core.issue.title,
        lifecycleState = snapshot.core.lifecycle.state,
        selectedStageId = selectedStage?.id,
        selectedStageTitle = selectedStage?.title,
        stageCount = snapshot.core.stages.size,
        activeOrRecoverableRunCount = snapshot.core.activeOrRecoverableRuns.size,
    )
}

internal fun repositoryErrorMessage(error: RepositoryError): String = when (error) {
    is RepositoryError.NotFound -> "未找到对应议题"
    is RepositoryError.StorageFailure -> if (error.retryable) {
        "议题读取失败，请重试"
    } else {
        "议题数据暂时不可用"
    }
    is RepositoryError.InvalidState -> "议题当前状态无法恢复"
    else -> "议题暂时无法读取"
}
