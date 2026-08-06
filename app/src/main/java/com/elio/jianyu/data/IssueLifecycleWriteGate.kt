package com.elio.jianyu.data

import com.elio.jianyu.lifecycle.IssueWriteAccessPolicy
import com.elio.jianyu.lifecycle.IssueWriteAction

/**
 * Repository/Service 最终业务边界的生命周期门禁。
 *
 * UI 与协调器的禁用仅用于体验；本门禁每次都从 Room 重新读取状态，防止深链、迟到回调或旧调用方绕过。
 */
internal class IssueLifecycleWriteGate(
    private val database: RoundtableDatabase,
) {
    suspend fun requireAllowed(
        issueId: String,
        action: IssueWriteAction,
        operation: String,
    ): RepositoryResult<Unit> {
        if (issueId.isBlank()) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(operation, "issue_id_required"),
            )
        }
        return try {
            val lifecycle = database.jianyuRepositoryDao().getIssueLifecycle(issueId)
                ?: return RepositoryResult.Failure(
                    RepositoryError.CompatibilityFailure(operation, "missing_issue_lifecycle"),
                )
            val purgeOperation = database.issueLifecycleV12Dao().getPurgeOperationForIssue(issueId)
            val decision = IssueWriteAccessPolicy.evaluate(
                state = lifecycle.state,
                purgeRequested = lifecycle.purgeRequestedAt != null || purgeOperation != null,
            )
            if (decision.allows(action)) {
                RepositoryResult.Success(Unit)
            } else {
                RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        operation = operation,
                        stateCode = when {
                            lifecycle.purgeRequestedAt != null || purgeOperation != null ->
                                "issue_purge_write_blocked"
                            lifecycle.state == IssueLifecycleState.ARCHIVED ->
                                "issue_archived_write_blocked"
                            lifecycle.state == IssueLifecycleState.TRASHED ->
                                "issue_trashed_write_blocked"
                            else -> "issue_write_blocked"
                        },
                    ),
                )
            }
        } catch (_: Exception) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true),
            )
        }
    }

    suspend fun requireRunAllowed(
        runId: String,
        action: IssueWriteAction,
        operation: String,
    ): RepositoryResult<Unit> {
        if (runId.isBlank()) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(operation, "run_id_required"),
            )
        }
        val run = try {
            database.jianyuRepositoryDao().getExecutionRun(runId)
        } catch (_: Exception) {
            return RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true),
            )
        } ?: return RepositoryResult.Failure(RepositoryError.NotFound("execution_run", runId))
        return requireAllowed(run.issueId, action, operation)
    }

    suspend fun requireNoActiveWork(
        issueId: String,
        operation: String,
    ): RepositoryResult<Unit> {
        return try {
            val core = database.jianyuRepositoryDao()
            val activeRun = core.getExecutionRunsForIssue(issueId).any {
                it.status == ExecutionRunStatus.NOT_STARTED || it.status == ExecutionRunStatus.RUNNING
            }
            val pendingMessage = core.getMessagesForIssue(issueId).any(Message::isPending)
            val pendingAudio = core.getAudioAssetsForIssue(issueId).any {
                it.fileState == AudioFileState.PENDING && it.purgeRequestedAt == null && it.deletedAt == null
            }
            var activeDiscussion = false
            for (stage in core.getStagesForIssue(issueId)) {
                if (
                    database.collaborationDao().getCrossDiscussionSessionsForStage(stage.id).any {
                        it.status == CrossDiscussionStatus.RESPONDING ||
                            it.status == CrossDiscussionStatus.SYNTHESIZING
                    }
                ) {
                    activeDiscussion = true
                    break
                }
            }
            if (activeRun || pendingMessage || pendingAudio || activeDiscussion) {
                RepositoryResult.Failure(
                    RepositoryError.InvalidState(operation, "trash_active_work"),
                )
            } else {
                RepositoryResult.Success(Unit)
            }
        } catch (_: Exception) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true),
            )
        }
    }
}

internal suspend inline fun <T> RepositoryResult<Unit>.then(
    crossinline block: suspend () -> RepositoryResult<T>,
): RepositoryResult<T> = when (this) {
    is RepositoryResult.Success -> block()
    is RepositoryResult.Failure -> this
}
