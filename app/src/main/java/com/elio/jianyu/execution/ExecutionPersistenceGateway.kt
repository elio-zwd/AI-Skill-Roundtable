package com.elio.jianyu.execution

import com.elio.jianyu.data.AppendDomainMessageCommand
import com.elio.jianyu.data.ConsumeExecutionBudgetCommand
import com.elio.jianyu.data.CreateExecutionRuntimeCommand
import com.elio.jianyu.data.ExecutionMessageUsageSnapshotEntity
import com.elio.jianyu.data.ExecutionParticipantStateEntity
import com.elio.jianyu.data.ExecutionRunBudgetEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.RecoverInterruptedExecutionCommand
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SetExecutionBudgetReserveCommand
import com.elio.jianyu.data.TransitionExecutionParticipantCommand
import com.elio.jianyu.data.TransitionRunCommand
import com.elio.jianyu.data.UpdatePendingDomainMessageCommand
import com.elio.jianyu.data.closeExecutionBudget
import com.elio.jianyu.data.consumeExecutionBudget
import com.elio.jianyu.data.createExecutionRuntime
import com.elio.jianyu.data.getExecutionRuntime
import com.elio.jianyu.data.listExecutionMessageUsage
import com.elio.jianyu.data.recoverInterruptedExecution
import com.elio.jianyu.data.setExecutionBudgetReserve
import com.elio.jianyu.data.transitionExecutionParticipant

interface ExecutionPersistenceGateway {
    suspend fun recoverIssue(issueId: String): IssueRecoverySnapshot

    suspend fun createRuntime(command: CreateExecutionRuntimeCommand): ExecutionRuntimeSnapshot

    suspend fun getRuntime(runId: String): ExecutionRuntimeSnapshot

    suspend fun listMessageUsage(
        runId: String,
    ): List<ExecutionMessageUsageSnapshotEntity> = emptyList()

    suspend fun transitionParticipant(
        command: TransitionExecutionParticipantCommand,
    ): ExecutionParticipantStateEntity

    suspend fun consumeBudget(
        command: ConsumeExecutionBudgetCommand,
    ): ExecutionRunBudgetEntity

    suspend fun setBudgetReserve(
        command: SetExecutionBudgetReserveCommand,
    ): ExecutionRunBudgetEntity

    suspend fun closeBudget(rootRunId: String, updatedAt: Long): ExecutionRunBudgetEntity

    suspend fun appendMessage(command: AppendDomainMessageCommand): Message

    suspend fun updatePendingMessage(command: UpdatePendingDomainMessageCommand): Message

    suspend fun transitionRun(command: TransitionRunCommand): ExecutionRunEntity

    suspend fun recoverInterrupted(
        command: RecoverInterruptedExecutionCommand,
    ): ExecutionRuntimeSnapshot
}

class JianyuExecutionPersistenceGateway(
    private val repository: JianyuRepository,
) : ExecutionPersistenceGateway {
    override suspend fun recoverIssue(issueId: String): IssueRecoverySnapshot =
        repository.recoverIssue(issueId).valueOrThrow()

    override suspend fun createRuntime(
        command: CreateExecutionRuntimeCommand,
    ): ExecutionRuntimeSnapshot = repository.createExecutionRuntime(command).valueOrThrow()

    override suspend fun getRuntime(runId: String): ExecutionRuntimeSnapshot =
        repository.getExecutionRuntime(runId).valueOrThrow()

    override suspend fun listMessageUsage(
        runId: String,
    ): List<ExecutionMessageUsageSnapshotEntity> =
        repository.listExecutionMessageUsage(runId).valueOrThrow()

    override suspend fun transitionParticipant(
        command: TransitionExecutionParticipantCommand,
    ): ExecutionParticipantStateEntity =
        repository.transitionExecutionParticipant(command).valueOrThrow()

    override suspend fun consumeBudget(
        command: ConsumeExecutionBudgetCommand,
    ): ExecutionRunBudgetEntity = repository.consumeExecutionBudget(command).valueOrThrow()

    override suspend fun setBudgetReserve(
        command: SetExecutionBudgetReserveCommand,
    ): ExecutionRunBudgetEntity = repository.setExecutionBudgetReserve(command).valueOrThrow()

    override suspend fun closeBudget(
        rootRunId: String,
        updatedAt: Long,
    ): ExecutionRunBudgetEntity = repository.closeExecutionBudget(rootRunId, updatedAt).valueOrThrow()

    override suspend fun appendMessage(command: AppendDomainMessageCommand): Message =
        repository.appendDomainMessage(command).valueOrThrow()

    override suspend fun updatePendingMessage(
        command: UpdatePendingDomainMessageCommand,
    ): Message = repository.updatePendingDomainMessage(command).valueOrThrow()

    override suspend fun transitionRun(command: TransitionRunCommand): ExecutionRunEntity =
        repository.transitionRun(command).valueOrThrow()

    override suspend fun recoverInterrupted(
        command: RecoverInterruptedExecutionCommand,
    ): ExecutionRuntimeSnapshot {
        val recovered = repository.recoverInterruptedExecution(command).valueOrThrow()
        if (
            recovered.run.status == ExecutionRunStatus.RUNNING ||
            recovered.run.status == ExecutionRunStatus.PARTIAL_SUCCESS
        ) {
            repository.transitionRun(
                TransitionRunCommand(
                    runId = command.runId,
                    expectedStatuses = setOf(
                        ExecutionRunStatus.RUNNING,
                        ExecutionRunStatus.PARTIAL_SUCCESS,
                    ),
                    newStatus = ExecutionRunStatus.RETRYABLE,
                    updatedAt = command.updatedAt,
                    startedAt = recovered.run.startedAt,
                    finishedAt = command.updatedAt,
                    failureCode = ExecutionErrorCode.PROCESS_INTERRUPTED.storageValue,
                    failureMessage = "运行被系统中断，可由用户显式重试。",
                ),
            ).valueOrThrow()
            return repository.getExecutionRuntime(command.runId).valueOrThrow()
        }
        return recovered
    }
}

class ExecutionRepositoryException(
    val repositoryError: RepositoryError,
) : IllegalStateException("Execution repository operation failed")

private fun <T> RepositoryResult<T>.valueOrThrow(): T = when (this) {
    is RepositoryResult.Success -> value
    is RepositoryResult.Failure -> throw ExecutionRepositoryException(error)
}
