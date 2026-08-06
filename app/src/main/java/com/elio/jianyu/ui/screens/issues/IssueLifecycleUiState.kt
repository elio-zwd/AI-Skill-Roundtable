package com.elio.jianyu.ui.screens.issues

import com.elio.jianyu.data.IssueArchiveEventEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssuePurgeOperationEntity
import com.elio.jianyu.data.IssueRelationEntity
import com.elio.jianyu.data.IssueResumeEventEntity
import com.elio.jianyu.lifecycle.IssueArchivePreparation
import com.elio.jianyu.lifecycle.IssuePurgeImpactSnapshot

/** 生命周期稳定页面状态；导航与提示等一次性动作由独立 Event Flow 发送。 */
sealed interface IssueLifecycleUiState {
    data object LifecycleLoading : IssueLifecycleUiState

    data class LifecycleContent(
        val lifecycle: IssueLifecycleEntity,
        val archiveEvents: List<IssueArchiveEventEntity>,
        val resumeEvents: List<IssueResumeEventEntity>,
        val relations: List<IssueRelationEntity>,
        val purgeOperation: IssuePurgeOperationEntity?,
    ) : IssueLifecycleUiState

    data object ArchiveImpactLoading : IssueLifecycleUiState
    data class ArchiveNeedsTaskDecision(val preparation: IssueArchivePreparation) : IssueLifecycleUiState
    data class ArchiveWaiting(val preparation: IssueArchivePreparation) : IssueLifecycleUiState
    data class ArchiveStopping(val preparation: IssueArchivePreparation) : IssueLifecycleUiState
    data class ArchiveEditingSummary(
        val preparation: IssueArchivePreparation,
        val summaryMarkdown: String,
    ) : IssueLifecycleUiState
    data class Archiving(val preparation: IssueArchivePreparation) : IssueLifecycleUiState
    data class Archived(val event: IssueArchiveEventEntity) : IssueLifecycleUiState
    data class ArchiveFailure(val code: String) : IssueLifecycleUiState

    data class ResumeEditingChanges(
        val archiveEvent: IssueArchiveEventEntity,
        val changeNote: String,
        val noChangeConfirmed: Boolean,
    ) : IssueLifecycleUiState
    data class Resuming(val archiveEvent: IssueArchiveEventEntity) : IssueLifecycleUiState
    data class Resumed(val event: IssueResumeEventEntity) : IssueLifecycleUiState
    data class ResumeFailure(val code: String) : IssueLifecycleUiState

    data class RelatedIssueEditing(
        val archiveEvent: IssueArchiveEventEntity,
        val title: String,
        val objective: String,
    ) : IssueLifecycleUiState
    data object RelatedIssueCreating : IssueLifecycleUiState
    data class RelatedIssueCreated(val relation: IssueRelationEntity) : IssueLifecycleUiState
    data class RelatedIssueFailure(val code: String) : IssueLifecycleUiState

    data class TrashImpact(val preparation: IssueArchivePreparation) : IssueLifecycleUiState
    data class MovingToTrash(val preparation: IssueArchivePreparation) : IssueLifecycleUiState
    data class Trashed(val lifecycle: IssueLifecycleEntity) : IssueLifecycleUiState
    data class TrashFailure(val code: String) : IssueLifecycleUiState
    data object RestoringFromTrash : IssueLifecycleUiState
    data class TrashRestored(val lifecycle: IssueLifecycleEntity) : IssueLifecycleUiState

    data object PurgeImpactLoading : IssueLifecycleUiState
    data class PurgeImpactReady(val impact: IssuePurgeImpactSnapshot) : IssueLifecycleUiState
    data class PurgeConfirming(
        val impact: IssuePurgeImpactSnapshot,
        val firstConfirmationCompleted: Boolean,
    ) : IssueLifecycleUiState
    data class PurgeRequested(val operation: IssuePurgeOperationEntity) : IssueLifecycleUiState
    data class PurgeCancelingTasks(val operation: IssuePurgeOperationEntity) : IssueLifecycleUiState
    data class PurgeDeletingFiles(val operation: IssuePurgeOperationEntity) : IssueLifecycleUiState
    data class PurgeDatabaseCleanup(val operation: IssuePurgeOperationEntity) : IssueLifecycleUiState
    data class PurgeRetryableFailure(val operation: IssuePurgeOperationEntity) : IssueLifecycleUiState
    data class PurgeCompleted(val issueId: String) : IssueLifecycleUiState
    data class PurgeStorageFailure(val code: String) : IssueLifecycleUiState
}

sealed interface IssueLifecycleUiEvent {
    data class NavigateToIssue(val issueId: String) : IssueLifecycleUiEvent
    data class NavigateToRelatedIssue(val issueId: String) : IssueLifecycleUiEvent
    data class ShowStableError(val code: String) : IssueLifecycleUiEvent
}
