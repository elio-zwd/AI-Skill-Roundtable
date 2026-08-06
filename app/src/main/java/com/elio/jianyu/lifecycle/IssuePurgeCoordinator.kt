package com.elio.jianyu.lifecycle

import com.elio.jianyu.data.IssueLifecycleV12Repository
import com.elio.jianyu.data.IssuePurgeDatabaseCleaner
import com.elio.jianyu.data.IssuePurgeFailurePhase
import com.elio.jianyu.data.IssuePurgeOperationEntity
import com.elio.jianyu.data.IssuePurgeState
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RequestIssuePurgeOperationCommand
import com.elio.jianyu.data.TransitionIssuePurgeOperationCommand

sealed interface IssuePurgeRequestResult {
    data class Scheduled(val operation: IssuePurgeOperationEntity) : IssuePurgeRequestResult
    data class Failure(val code: String) : IssuePurgeRequestResult
}

sealed interface IssuePurgeExecutionResult {
    data object Completed : IssuePurgeExecutionResult
    data class RetryableFailure(val code: String) : IssuePurgeExecutionResult
    data class Rejected(val code: String) : IssuePurgeExecutionResult
}

class IssuePurgeCoordinator(
    private val repository: IssueLifecycleV12Repository,
    private val impactCalculator: IssuePurgeImpactCalculator,
    private val taskController: IssueLifecycleTaskController,
    private val fileCleaner: IssuePurgeFileCleaner,
    private val databaseCleaner: IssuePurgeDatabaseCleaner,
    private val scheduler: IssuePurgeScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun request(
        command: RequestIssuePurgeOperationCommand,
    ): IssuePurgeRequestResult {
        val preview = when (val result = impactCalculator.inspect(command.issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return IssuePurgeRequestResult.Failure("purge_impact_unavailable")
        }
        if (IssuePurgeImpactHasher.hash(preview) != command.impactHash) {
            return IssuePurgeRequestResult.Failure("purge_impact_changed")
        }
        val operation = when (val result = repository.requestIssuePurgeOperation(command)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return IssuePurgeRequestResult.Failure(
                result.error.stableLifecycleCode("purge_request_failed"),
            )
        }
        if (!scheduler.schedule(operation.id)) {
            fail(
                operation = operation,
                expected = setOf(IssuePurgeState.REQUESTED),
                code = "purge_schedule_failed",
                phase = IssuePurgeFailurePhase.STORAGE,
            )
            return IssuePurgeRequestResult.Failure("purge_schedule_failed")
        }
        return IssuePurgeRequestResult.Scheduled(operation)
    }

    suspend fun execute(operationId: String): IssuePurgeExecutionResult {
        var operation = when (val result = repository.getIssuePurgeOperation(operationId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return IssuePurgeExecutionResult.Rejected("purge_operation_missing")
        }

        if (operation.state == IssuePurgeState.FAILED_RETRYABLE &&
            operation.failurePhase == IssuePurgeFailurePhase.IMPACT
        ) {
            return IssuePurgeExecutionResult.Rejected("purge_impact_changed")
        }

        if (operation.state == IssuePurgeState.REQUESTED ||
            operation.state == IssuePurgeState.WAITING_FOR_TASKS
        ) {
            val preview = when (val result = impactCalculator.inspect(operation.issueId)) {
                is RepositoryResult.Success -> result.value
                is RepositoryResult.Failure -> {
                    return failResult(
                        operation,
                        setOf(operation.state),
                        "purge_impact_unavailable",
                        IssuePurgeFailurePhase.IMPACT,
                    )
                }
            }
            if (IssuePurgeImpactHasher.hash(preview) != operation.impactHash) {
                return failResult(
                    operation,
                    setOf(operation.state),
                    "purge_impact_changed",
                    IssuePurgeFailurePhase.IMPACT,
                )
            }
        }

        if (operation.state in setOf(
                IssuePurgeState.REQUESTED,
                IssuePurgeState.WAITING_FOR_TASKS,
            ) || (
                operation.state == IssuePurgeState.FAILED_RETRYABLE &&
                    operation.failurePhase in setOf(
                        IssuePurgeFailurePhase.TASK_CANCEL,
                        IssuePurgeFailurePhase.STORAGE,
                    )
                )
        ) {
            operation = transition(
                operation,
                IssuePurgeState.CANCELING_TASKS,
                expected = setOf(operation.state),
            ) ?: return IssuePurgeExecutionResult.RetryableFailure("purge_state_changed")
        }

        if (operation.state == IssuePurgeState.CANCELING_TASKS) {
            val tasks = try {
                taskController.inspect(operation.issueId)
            } catch (_: Exception) {
                return failResult(
                    operation,
                    setOf(IssuePurgeState.CANCELING_TASKS),
                    "purge_task_inspection_failed",
                    IssuePurgeFailurePhase.TASK_CANCEL,
                )
            }
            if (tasks.hasActiveWork) {
                when (val stop = taskController.stopAll(tasks)) {
                    is IssueLifecycleTaskStopResult.Stopped -> Unit
                    is IssueLifecycleTaskStopResult.Failure -> {
                        return failResult(
                            operation,
                            setOf(IssuePurgeState.CANCELING_TASKS),
                            stop.code,
                            IssuePurgeFailurePhase.TASK_CANCEL,
                        )
                    }
                }
            }
            operation = transition(
                operation,
                IssuePurgeState.DELETING_FILES,
                expected = setOf(IssuePurgeState.CANCELING_TASKS),
            ) ?: return IssuePurgeExecutionResult.RetryableFailure("purge_state_changed")
        } else if (
            operation.state == IssuePurgeState.FAILED_RETRYABLE &&
            operation.failurePhase in setOf(
                IssuePurgeFailurePhase.AUDIO_DELETE_REQUEST,
                IssuePurgeFailurePhase.FILE_DELETE,
            )
        ) {
            operation = transition(
                operation,
                IssuePurgeState.DELETING_FILES,
                expected = setOf(IssuePurgeState.FAILED_RETRYABLE),
            ) ?: return IssuePurgeExecutionResult.RetryableFailure("purge_state_changed")
        }

        if (operation.state == IssuePurgeState.DELETING_FILES) {
            when (val cleanup = fileCleaner.clean(operation.issueId, operation.requestedAt)) {
                is IssuePurgeFileCleanupResult.Success -> Unit
                is IssuePurgeFileCleanupResult.Failure -> {
                    val phase = if (
                        cleanup.code == IssuePurgeFileFailureCode.AUDIO_DELETE_REQUEST_FAILED
                    ) {
                        IssuePurgeFailurePhase.AUDIO_DELETE_REQUEST
                    } else {
                        IssuePurgeFailurePhase.FILE_DELETE
                    }
                    return failResult(
                        operation,
                        setOf(IssuePurgeState.DELETING_FILES),
                        cleanup.code.storageValue,
                        phase,
                    )
                }
            }
            operation = transition(
                operation,
                IssuePurgeState.READY_FOR_DATABASE_PURGE,
                expected = setOf(IssuePurgeState.DELETING_FILES),
            ) ?: return IssuePurgeExecutionResult.RetryableFailure("purge_state_changed")
        }

        if (operation.state == IssuePurgeState.FAILED_RETRYABLE &&
            operation.failurePhase == IssuePurgeFailurePhase.DATABASE_PURGE
        ) {
            operation = transition(
                operation,
                IssuePurgeState.DATABASE_PURGING,
                expected = setOf(IssuePurgeState.FAILED_RETRYABLE),
            ) ?: return IssuePurgeExecutionResult.RetryableFailure("purge_state_changed")
        } else if (operation.state == IssuePurgeState.READY_FOR_DATABASE_PURGE) {
            operation = transition(
                operation,
                IssuePurgeState.DATABASE_PURGING,
                expected = setOf(IssuePurgeState.READY_FOR_DATABASE_PURGE),
            ) ?: return IssuePurgeExecutionResult.RetryableFailure("purge_state_changed")
        }

        if (operation.state != IssuePurgeState.DATABASE_PURGING) {
            return IssuePurgeExecutionResult.Rejected("purge_state_not_executable")
        }
        return when (databaseCleaner.purge(operation.id, clock())) {
            is RepositoryResult.Success -> IssuePurgeExecutionResult.Completed
            is RepositoryResult.Failure -> failResult(
                operation,
                setOf(IssuePurgeState.DATABASE_PURGING),
                "purge_database_failed",
                IssuePurgeFailurePhase.DATABASE_PURGE,
            )
        }
    }

    suspend fun retry(operationId: String): IssuePurgeExecutionResult {
        val operation = when (val result = repository.getIssuePurgeOperation(operationId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return IssuePurgeExecutionResult.Rejected("purge_operation_missing")
        }
        if (operation.state != IssuePurgeState.FAILED_RETRYABLE ||
            operation.failurePhase == IssuePurgeFailurePhase.IMPACT
        ) {
            return IssuePurgeExecutionResult.Rejected("purge_retry_not_allowed")
        }
        return if (scheduler.schedule(operation.id)) {
            IssuePurgeExecutionResult.RetryableFailure("purge_retry_scheduled")
        } else {
            IssuePurgeExecutionResult.RetryableFailure("purge_schedule_failed")
        }
    }

    suspend fun recoverPendingOperations(): Int {
        val operations = when (val result = repository.listRecoverableIssuePurgeOperations()) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return 0
        }
        var scheduled = 0
        for (operation in operations) {
            if (operation.state == IssuePurgeState.FAILED_RETRYABLE) continue
            if (!scheduler.isActive(operation.id) && scheduler.schedule(operation.id)) {
                scheduled += 1
            }
        }
        return scheduled
    }

    private suspend fun transition(
        current: IssuePurgeOperationEntity,
        target: IssuePurgeState,
        expected: Set<IssuePurgeState>,
    ): IssuePurgeOperationEntity? {
        return when (
            val result = repository.transitionIssuePurgeOperation(
                TransitionIssuePurgeOperationCommand(
                    operationId = current.id,
                    expectedStates = expected,
                    targetState = target,
                    updatedAt = clock(),
                ),
            )
        ) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> null
        }
    }

    private suspend fun failResult(
        operation: IssuePurgeOperationEntity,
        expected: Set<IssuePurgeState>,
        code: String,
        phase: IssuePurgeFailurePhase,
    ): IssuePurgeExecutionResult.RetryableFailure {
        fail(operation, expected, code, phase)
        return IssuePurgeExecutionResult.RetryableFailure(code)
    }

    private suspend fun fail(
        operation: IssuePurgeOperationEntity,
        expected: Set<IssuePurgeState>,
        code: String,
        phase: IssuePurgeFailurePhase,
    ) {
        repository.transitionIssuePurgeOperation(
            TransitionIssuePurgeOperationCommand(
                operationId = operation.id,
                expectedStates = expected,
                targetState = IssuePurgeState.FAILED_RETRYABLE,
                updatedAt = clock(),
                failureCode = code,
                failurePhase = phase,
            ),
        )
    }
}

private fun com.elio.jianyu.data.RepositoryError.stableLifecycleCode(fallback: String): String = when (this) {
    is com.elio.jianyu.data.RepositoryError.InvalidState -> reason
    is com.elio.jianyu.data.RepositoryError.IdempotencyConflict -> "purge_idempotency_conflict"
    is com.elio.jianyu.data.RepositoryError.StorageFailure -> "purge_storage_unavailable"
    else -> fallback
}
