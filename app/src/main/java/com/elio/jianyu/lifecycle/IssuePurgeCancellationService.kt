package com.elio.jianyu.lifecycle

import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssuePurgeFailurePhase
import com.elio.jianyu.data.IssuePurgeState
import com.elio.jianyu.data.JianyuRepositoryTransactions
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RoundtableDatabase

/**
 * 只允许在任何正式文件删除开始前取消 Purge。
 *
 * WorkManager 取消发生在 Room 事务外；随后事务重新读取 Operation 状态。若 Worker 已推进到
 * `DELETING_FILES` 或更晚阶段，事务拒绝取消，避免把部分文件已删除的议题伪装成可完整恢复。
 */
class IssuePurgeCancellationService(
    database: RoundtableDatabase,
    private val scheduler: IssuePurgeScheduler,
) {
    private val transactions = JianyuRepositoryTransactions(database)

    suspend fun cancel(
        operationId: String,
        canceledAt: Long,
    ): RepositoryResult<IssueLifecycleEntity> {
        if (operationId.isBlank() || canceledAt <= 0L) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation("cancel_issue_purge", "invalid_argument"),
            )
        }
        if (!scheduler.cancel(operationId)) {
            return RepositoryResult.Failure(
                RepositoryError.StorageFailure("cancel_issue_purge", retryable = true),
            )
        }
        return transactions.databaseTransaction("cancel_issue_purge") {
            val operation = issueLifecycleV12Dao().getPurgeOperation(operationId)
                ?: return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("purge_operation", operationId),
                )
            val safelyCancelable = operation.state in setOf(
                IssuePurgeState.REQUESTED,
                IssuePurgeState.WAITING_FOR_TASKS,
                IssuePurgeState.CANCELING_TASKS,
            ) || (
                operation.state == IssuePurgeState.FAILED_RETRYABLE &&
                    operation.failurePhase in setOf(
                        IssuePurgeFailurePhase.IMPACT,
                        IssuePurgeFailurePhase.TASK_CANCEL,
                        IssuePurgeFailurePhase.STORAGE,
                    )
                )
            if (!safelyCancelable) {
                return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "cancel_issue_purge",
                        "purge_cancel_after_file_delete_forbidden",
                    ),
                )
            }
            val lifecycle = jianyuRepositoryDao().getIssueLifecycle(operation.issueId)
                ?: return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.CompatibilityFailure(
                        "cancel_issue_purge",
                        "missing_issue_lifecycle",
                    ),
                )
            if (lifecycle.state != IssueLifecycleState.TRASHED || lifecycle.purgeRequestedAt == null) {
                return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "cancel_issue_purge",
                        "purge_lifecycle_changed",
                    ),
                )
            }
            val restored = lifecycle.copy(
                purgeRequestedAt = null,
                updatedAt = maxOf(canceledAt, lifecycle.updatedAt + 1L),
            )
            if (jianyuRepositoryDao().updateIssueLifecycle(restored) != 1) {
                throw IllegalStateException("Purge cancellation lifecycle update failed")
            }
            if (issueLifecycleV12Dao().deletePurgeOperationForIssue(operation.issueId) != 1) {
                throw IllegalStateException("Purge cancellation operation delete failed")
            }
            RepositoryResult.Success(restored)
        }
    }
}
