package com.elio.jianyu.data

import com.elio.jianyu.execution.ExecutionErrorCode
import com.elio.jianyu.execution.ExecutionStateMachine

/**
 * 为 STANDARD、retry 子 Run 和协作 parent 子 Run 提供统一的 Runtime 读取与中断恢复。
 * 本组件不创建参与者状态机，也不调用网络；所有状态转换仍使用既有 DAO/CAS 语义。
 */
internal class CollaborationRuntimeRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
) {
    suspend fun getExecutionRuntime(
        runId: String,
    ): RepositoryResult<ExecutionRuntimeSnapshot> = transactions.collaborationTransaction(
        "get_execution_runtime",
    ) {
        val run = core.getExecutionRun(runId)
            ?: return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.NotFound("execution_run", runId),
            )
        loadRuntime(run)
    }

    suspend fun recoverInterruptedExecution(
        command: RecoverInterruptedExecutionCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> = transactions.collaborationTransaction(
        "recover_interrupted_execution",
    ) {
        require(command.runId.isNotBlank() && command.updatedAt > 0L)
        val run = core.getExecutionRun(command.runId)
            ?: return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.NotFound("execution_run", command.runId),
            )
        val states = core.getParticipantStates(command.runId)
        states.filter { state ->
            state.status == ExecutionParticipantStatus.RUNNING ||
                state.status == ExecutionParticipantStatus.STREAMING
        }.forEach { state ->
            core.compareAndSetParticipantState(
                participantSnapshotId = state.participantSnapshotId,
                runId = state.runId,
                expectedStatuses = listOf(
                    ExecutionParticipantStatus.RUNNING.storageValue,
                    ExecutionParticipantStatus.STREAMING.storageValue,
                ),
                newStatus = ExecutionParticipantStatus.RETRYABLE.storageValue,
                attemptIncrement = 0,
                outputMessageId = state.outputMessageId,
                startedAt = state.startedAt,
                finishedAt = command.updatedAt,
                lastErrorCode = ExecutionErrorCode.PROCESS_INTERRUPTED.storageValue,
                lastErrorMessage = "运行被系统中断，可由用户显式重试。",
                hasIncompleteOutput = state.outputMessageId != null,
                updatedAt = command.updatedAt,
            )
            state.outputMessageId?.let { messageId ->
                val message = core.getMessage(messageId)
                if (message?.isPending == true) {
                    core.compareAndSetPendingDomainMessage(
                        messageId = message.id,
                        issueId = requireNotNull(message.issueId),
                        stageId = requireNotNull(message.stageId),
                        executionRunId = message.executionRunId,
                        participantSnapshotId = message.participantSnapshotId,
                        text = message.text,
                        keepPending = false,
                    )
                }
            }
        }

        val latestStates = core.getParticipantStates(command.runId)
        val targetStatus = ExecutionStateMachine.aggregate(
            participantStatuses = latestStates.map { it.status },
            retryableParticipantIds = latestStates.indices
                .filterTo(mutableSetOf()) { index ->
                    latestStates[index].status != ExecutionParticipantStatus.SUCCEEDED
                },
        )
        if (
            run.status == ExecutionRunStatus.RUNNING ||
            run.status == ExecutionRunStatus.PARTIAL_SUCCESS
        ) {
            core.compareAndSetRunStatus(
                runId = run.id,
                expectedStatuses = listOf(
                    ExecutionRunStatus.RUNNING.storageValue,
                    ExecutionRunStatus.PARTIAL_SUCCESS.storageValue,
                ),
                newStatus = targetStatus.storageValue,
                updatedAt = command.updatedAt,
                startedAt = run.startedAt,
                finishedAt = command.updatedAt,
                stoppedAt = run.stoppedAt,
                failureCode = ExecutionErrorCode.PROCESS_INTERRUPTED.storageValue,
                failureMessage = "运行被系统中断，可由用户显式重试。",
            )
        }
        val recovered = core.getExecutionRun(command.runId)
            ?: throw IllegalStateException("Recovered run disappeared")
        loadRuntime(recovered)
    }

    private suspend fun CollaborationTransactionScope.loadRuntime(
        run: ExecutionRunEntity,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        val rootRunId = resolveBudgetRoot(run)
        val budget = core.getRunBudget(rootRunId)
            ?: return RepositoryResult.Failure(
                RepositoryError.NotFound("execution_run_budget", rootRunId),
            )
        return RepositoryResult.Success(
            ExecutionRuntimeSnapshot(
                run = run,
                participants = core.getParticipantSnapshots(run.id),
                participantStates = core.getParticipantStates(run.id),
                budget = budget,
            ),
        )
    }

    private suspend fun CollaborationTransactionScope.resolveBudgetRoot(
        run: ExecutionRunEntity,
    ): String {
        var current = run
        val visited = mutableSetOf<String>()
        while (true) {
            check(visited.add(current.id)) { "Execution parent chain contains a cycle" }
            val parentId = current.retryOfRunId ?: current.parentRunId ?: return current.id
            val parent = core.getExecutionRun(parentId)
                ?: throw RepositoryCompatibilityAbort("missing_execution_parent")
            if (parent.issueId != current.issueId || parent.stageId != current.stageId) {
                throw RepositoryCompatibilityAbort("execution_parent_scope_mismatch")
            }
            current = parent
        }
    }
}
