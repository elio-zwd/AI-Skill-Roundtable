package com.elio.jianyu.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.CreateRelatedIssueCommand
import com.elio.jianyu.data.IssueArchiveEventEntity
import com.elio.jianyu.data.IssuePurgeOperationEntity
import com.elio.jianyu.data.IssuePurgeState
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RequestIssuePurgeOperationCommand
import com.elio.jianyu.data.ResumeArchivedIssueCommand
import com.elio.jianyu.lifecycle.IssueArchivePreparation
import com.elio.jianyu.lifecycle.IssueArchiveStopResult
import com.elio.jianyu.lifecycle.IssuePurgeExecutionResult
import com.elio.jianyu.lifecycle.IssuePurgeImpactHasher
import com.elio.jianyu.lifecycle.IssuePurgeRequestResult
import com.elio.jianyu.lifecycle.JianyuLifecycleRuntime
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PendingLifecycleAction {
    ARCHIVE,
    MOVE_TO_TRASH,
}

class IssueLifecycleViewModel(
    private val runtime: JianyuLifecycleRuntime,
    private val now: () -> Long = System::currentTimeMillis,
    private val stableId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _state = MutableStateFlow<IssueLifecycleUiState?>(null)
    val state: StateFlow<IssueLifecycleUiState?> = _state.asStateFlow()

    private val _events = MutableSharedFlow<IssueLifecycleUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<IssueLifecycleUiEvent> = _events.asSharedFlow()

    private var pendingAction: PendingLifecycleAction? = null
    private var currentPreparation: IssueArchivePreparation? = null
    private var currentArchiveEvent: IssueArchiveEventEntity? = null
    private var currentIssueId: String? = null
    private var purgeObservationJob: Job? = null
    private var purgeSubmissionInFlight: Boolean = false

    fun dismiss() {
        purgeObservationJob?.cancel()
        purgeObservationJob = null
        purgeSubmissionInFlight = false
        _state.value = null
        pendingAction = null
        currentPreparation = null
        currentArchiveEvent = null
    }

    fun beginArchive(issueId: String) {
        pendingAction = PendingLifecycleAction.ARCHIVE
        prepare(issueId)
    }

    fun beginMoveToTrash(issueId: String) {
        pendingAction = PendingLifecycleAction.MOVE_TO_TRASH
        prepare(issueId)
    }

    private fun prepare(issueId: String) {
        currentIssueId = issueId
        _state.value = IssueLifecycleUiState.ArchiveImpactLoading
        viewModelScope.launch {
            when (val result = runtime.archiveCoordinator.prepare(issueId)) {
                is RepositoryResult.Success -> applyPreparation(result.value)
                is RepositoryResult.Failure -> failArchive(result.error.stableCode("archive_prepare_failed"))
            }
        }
    }

    private fun applyPreparation(preparation: IssueArchivePreparation) {
        currentPreparation = preparation
        _state.value = when {
            preparation.activeTasks.hasActiveWork ->
                IssueLifecycleUiState.ArchiveNeedsTaskDecision(preparation)
            pendingAction == PendingLifecycleAction.MOVE_TO_TRASH ->
                IssueLifecycleUiState.TrashImpact(preparation)
            else -> IssueLifecycleUiState.ArchiveEditingSummary(
                preparation,
                preparation.generatedSummaryMarkdown,
            )
        }
    }

    fun waitForTasks() {
        val preparation = currentPreparation ?: return
        _state.value = IssueLifecycleUiState.ArchiveWaiting(preparation)
    }

    fun refreshWaiting() {
        val issueId = currentIssueId ?: return
        prepare(issueId)
    }

    fun stopTasks() {
        val preparation = currentPreparation ?: return
        _state.value = IssueLifecycleUiState.ArchiveStopping(preparation)
        viewModelScope.launch {
            when (val result = runtime.archiveCoordinator.stopActiveWork(preparation)) {
                is IssueArchiveStopResult.Ready -> applyPreparation(result.preparation)
                is IssueArchiveStopResult.Failure -> failArchive(result.code)
            }
        }
    }

    fun updateArchiveSummary(value: String) {
        val current = _state.value as? IssueLifecycleUiState.ArchiveEditingSummary ?: return
        _state.value = current.copy(summaryMarkdown = value)
    }

    fun confirmArchive() {
        val current = _state.value as? IssueLifecycleUiState.ArchiveEditingSummary ?: return
        if (current.summaryMarkdown.isBlank()) {
            failArchive("archive_summary_required")
            return
        }
        _state.value = IssueLifecycleUiState.Archiving(current.preparation)
        viewModelScope.launch {
            when (
                val result = runtime.archiveCoordinator.confirmArchive(
                    preparation = current.preparation,
                    eventId = stableId(),
                    operationId = stableId(),
                    editedSummaryMarkdown = current.summaryMarkdown,
                    archivedAt = now(),
                )
            ) {
                is RepositoryResult.Success -> _state.value =
                    IssueLifecycleUiState.Archived(result.value.archiveEvent)
                is RepositoryResult.Failure -> failArchive(result.error.stableCode("archive_failed"))
            }
        }
    }

    fun confirmMoveToTrash() {
        val preparation = when (val current = _state.value) {
            is IssueLifecycleUiState.TrashImpact -> current.preparation
            else -> currentPreparation
        } ?: return
        _state.value = IssueLifecycleUiState.MovingToTrash(preparation)
        viewModelScope.launch {
            when (val result = runtime.archiveCoordinator.moveToTrash(preparation, now())) {
                is RepositoryResult.Success -> _state.value = IssueLifecycleUiState.Trashed(result.value)
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.TrashFailure(result.error.stableCode("trash_failed"))
            }
        }
    }

    fun restoreFromTrash(issueId: String) {
        currentIssueId = issueId
        _state.value = IssueLifecycleUiState.RestoringFromTrash
        viewModelScope.launch {
            when (val result = runtime.archiveCoordinator.restoreFromTrash(issueId, now())) {
                is RepositoryResult.Success -> _state.value =
                    IssueLifecycleUiState.TrashRestored(result.value)
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.TrashFailure(result.error.stableCode("trash_restore_failed"))
            }
        }
    }

    fun beginResume(issueId: String) {
        currentIssueId = issueId
        viewModelScope.launch {
            when (val events = runtime.repository.listArchiveEvents(issueId)) {
                is RepositoryResult.Success -> {
                    val archive = events.value.lastOrNull()
                    if (archive == null) {
                        _state.value = IssueLifecycleUiState.ResumeFailure("archive_event_missing")
                    } else {
                        currentArchiveEvent = archive
                        _state.value = IssueLifecycleUiState.ResumeEditingChanges(
                            archiveEvent = archive,
                            changeNote = "",
                            noChangeConfirmed = false,
                        )
                    }
                }
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.ResumeFailure(events.error.stableCode("resume_load_failed"))
            }
        }
    }

    fun updateResumeChangeNote(value: String) {
        val current = _state.value as? IssueLifecycleUiState.ResumeEditingChanges ?: return
        _state.value = current.copy(changeNote = value, noChangeConfirmed = false)
    }

    fun selectNoChange() {
        val current = _state.value as? IssueLifecycleUiState.ResumeEditingChanges ?: return
        _state.value = current.copy(changeNote = "", noChangeConfirmed = true)
    }

    fun confirmResume() {
        val current = _state.value as? IssueLifecycleUiState.ResumeEditingChanges ?: return
        _state.value = IssueLifecycleUiState.Resuming(current.archiveEvent)
        viewModelScope.launch {
            when (
                val result = runtime.archiveCoordinator.resume(
                    ResumeArchivedIssueCommand(
                        eventId = stableId(),
                        issueId = current.archiveEvent.issueId,
                        archiveEventId = current.archiveEvent.id,
                        operationId = stableId(),
                        changeNote = current.changeNote,
                        noChangeConfirmed = current.noChangeConfirmed,
                        resumedAt = now(),
                    ),
                )
            ) {
                is RepositoryResult.Success -> {
                    _state.value = IssueLifecycleUiState.Resumed(result.value.resumeEvent)
                    _events.tryEmit(IssueLifecycleUiEvent.NavigateToIssue(result.value.lifecycle.issueId))
                }
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.ResumeFailure(result.error.stableCode("resume_failed"))
            }
        }
    }

    fun beginRelatedIssue(issueId: String) {
        currentIssueId = issueId
        viewModelScope.launch {
            when (val events = runtime.repository.listArchiveEvents(issueId)) {
                is RepositoryResult.Success -> {
                    val archive = events.value.lastOrNull()
                    if (archive == null) {
                        _state.value = IssueLifecycleUiState.RelatedIssueFailure("archive_event_missing")
                    } else {
                        currentArchiveEvent = archive
                        _state.value = IssueLifecycleUiState.RelatedIssueEditing(
                            archiveEvent = archive,
                            title = "",
                            objective = "",
                        )
                    }
                }
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.RelatedIssueFailure(
                        events.error.stableCode("related_issue_load_failed"),
                    )
            }
        }
    }

    fun updateRelatedTitle(value: String) {
        val current = _state.value as? IssueLifecycleUiState.RelatedIssueEditing ?: return
        _state.value = current.copy(title = value)
    }

    fun updateRelatedObjective(value: String) {
        val current = _state.value as? IssueLifecycleUiState.RelatedIssueEditing ?: return
        _state.value = current.copy(objective = value)
    }

    fun confirmRelatedIssue() {
        val current = _state.value as? IssueLifecycleUiState.RelatedIssueEditing ?: return
        if (current.title.isBlank() || current.objective.isBlank()) {
            _state.value = IssueLifecycleUiState.RelatedIssueFailure("related_issue_fields_required")
            return
        }
        _state.value = IssueLifecycleUiState.RelatedIssueCreating
        viewModelScope.launch {
            val targetIssueId = stableId()
            when (
                val result = runtime.archiveCoordinator.createRelated(
                    CreateRelatedIssueCommand(
                        relationId = stableId(),
                        operationId = stableId(),
                        sourceIssueId = current.archiveEvent.issueId,
                        sourceArchiveEventId = current.archiveEvent.id,
                        targetIssueId = targetIssueId,
                        targetIssueTitle = current.title,
                        targetStageId = stableId(),
                        targetStageTitle = "初始阶段",
                        targetObjective = current.objective,
                        createdAt = now(),
                    ),
                )
            ) {
                is RepositoryResult.Success -> {
                    _state.value = IssueLifecycleUiState.RelatedIssueCreated(result.value.relation)
                    _events.tryEmit(IssueLifecycleUiEvent.NavigateToRelatedIssue(targetIssueId))
                }
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.RelatedIssueFailure(
                        result.error.stableCode("related_issue_failed"),
                    )
            }
        }
    }

    fun beginPurge(issueId: String) {
        currentIssueId = issueId
        purgeObservationJob?.cancel()
        _state.value = IssueLifecycleUiState.PurgeImpactLoading
        viewModelScope.launch {
            when (val operations = runtime.repository.listRecoverableIssuePurgeOperations()) {
                is RepositoryResult.Success -> {
                    val existing = operations.value.firstOrNull { it.issueId == issueId }
                    if (existing != null) {
                        renderPurgeOperation(existing)
                        observePurge(existing.id)
                        return@launch
                    }
                }
                is RepositoryResult.Failure -> {
                    _state.value = IssueLifecycleUiState.PurgeStorageFailure(
                        operations.error.stableCode("purge_operation_load_failed"),
                    )
                    return@launch
                }
            }
            when (val result = runtime.impactCalculator.inspect(issueId)) {
                is RepositoryResult.Success -> _state.value = IssueLifecycleUiState.PurgeImpactReady(result.value)
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.PurgeStorageFailure(
                        result.error.stableCode("purge_impact_failed"),
                    )
            }
        }
    }

    fun confirmPurgeImpact() {
        val current = _state.value as? IssueLifecycleUiState.PurgeImpactReady ?: return
        _state.value = IssueLifecycleUiState.PurgeConfirming(
            impact = current.impact,
            firstConfirmationCompleted = true,
        )
    }

    fun confirmPurgeFinal() {
        val current = _state.value as? IssueLifecycleUiState.PurgeConfirming ?: return
        if (!current.firstConfirmationCompleted || purgeSubmissionInFlight) return
        purgeSubmissionInFlight = true
        val operationId = stableId()
        viewModelScope.launch {
            try {
                when (
                    val result = runtime.purgeCoordinator.request(
                        RequestIssuePurgeOperationCommand(
                            id = operationId,
                            issueId = current.impact.issueId,
                            operationId = stableId(),
                            impactHash = IssuePurgeImpactHasher.hash(current.impact),
                            firstConfirmation = true,
                            finalConfirmation = true,
                            requestedAt = now(),
                        ),
                    )
                ) {
                    is IssuePurgeRequestResult.Scheduled -> {
                        renderPurgeOperation(result.operation)
                        observePurge(operationId)
                    }
                    is IssuePurgeRequestResult.Failure -> _state.value =
                        IssueLifecycleUiState.PurgeStorageFailure(result.code)
                }
            } finally {
                purgeSubmissionInFlight = false
            }
        }
    }

    fun retryPurge(operationId: String) {
        viewModelScope.launch {
            when (val result = runtime.purgeCoordinator.retry(operationId)) {
                is IssuePurgeExecutionResult.RetryableFailure -> {
                    if (result.code == "purge_retry_scheduled") {
                        observePurge(operationId)
                    } else {
                        _state.value = IssueLifecycleUiState.PurgeStorageFailure(result.code)
                    }
                }
                is IssuePurgeExecutionResult.Rejected ->
                    _state.value = IssueLifecycleUiState.PurgeStorageFailure(result.code)
                IssuePurgeExecutionResult.Completed ->
                    _state.value = IssueLifecycleUiState.PurgeCompleted(currentIssueId.orEmpty())
            }
        }
    }

    fun cancelPurge(operationId: String) {
        viewModelScope.launch {
            when (val result = runtime.purgeCancellationService.cancel(operationId, now())) {
                is RepositoryResult.Success -> {
                    purgeObservationJob?.cancel()
                    purgeObservationJob = null
                    _state.value = IssueLifecycleUiState.TrashRestored(result.value)
                }
                is RepositoryResult.Failure -> _state.value =
                    IssueLifecycleUiState.PurgeStorageFailure(
                        result.error.stableCode("purge_cancel_failed"),
                    )
            }
        }
    }

    private fun observePurge(operationId: String) {
        purgeObservationJob?.cancel()
        purgeObservationJob = viewModelScope.launch {
            while (true) {
                when (val result = runtime.repository.getIssuePurgeOperation(operationId)) {
                    is RepositoryResult.Success -> {
                        renderPurgeOperation(result.value)
                        if (result.value.state == IssuePurgeState.FAILED_RETRYABLE) return@launch
                    }
                    is RepositoryResult.Failure -> {
                        if (result.error is RepositoryError.NotFound) {
                            _state.value = IssueLifecycleUiState.PurgeCompleted(currentIssueId.orEmpty())
                        } else {
                            _state.value = IssueLifecycleUiState.PurgeStorageFailure(
                                result.error.stableCode("purge_operation_load_failed"),
                            )
                        }
                        return@launch
                    }
                }
                delay(500L)
            }
        }
    }

    private fun renderPurgeOperation(operation: IssuePurgeOperationEntity) {
        _state.value = when (operation.state) {
            IssuePurgeState.REQUESTED,
            IssuePurgeState.WAITING_FOR_TASKS,
            -> IssueLifecycleUiState.PurgeRequested(operation)
            IssuePurgeState.CANCELING_TASKS -> IssueLifecycleUiState.PurgeCancelingTasks(operation)
            IssuePurgeState.DELETING_FILES -> IssueLifecycleUiState.PurgeDeletingFiles(operation)
            IssuePurgeState.READY_FOR_DATABASE_PURGE,
            IssuePurgeState.DATABASE_PURGING,
            -> IssueLifecycleUiState.PurgeDatabaseCleanup(operation)
            IssuePurgeState.FAILED_RETRYABLE -> IssueLifecycleUiState.PurgeRetryableFailure(operation)
            IssuePurgeState.COMPLETED -> IssueLifecycleUiState.PurgeCompleted(operation.issueId)
        }
    }

    private fun failArchive(code: String) {
        _state.value = if (pendingAction == PendingLifecycleAction.MOVE_TO_TRASH) {
            IssueLifecycleUiState.TrashFailure(code)
        } else {
            IssueLifecycleUiState.ArchiveFailure(code)
        }
    }

    companion object {
        fun factory(runtime: JianyuLifecycleRuntime): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    IssueLifecycleViewModel(runtime) as T
            }
    }
}

private fun RepositoryError.stableCode(fallback: String): String = when (this) {
    is RepositoryError.InvalidState -> stateCode
    is RepositoryError.ConstraintViolation -> constraintCode
    is RepositoryError.CompatibilityFailure -> compatibilityCode
    is RepositoryError.StorageFailure -> fallback
    is RepositoryError.IdempotencyConflict -> "idempotency_conflict"
    is RepositoryError.NotFound -> "not_found"
    is RepositoryError.AlreadyExists -> "already_exists"
}
