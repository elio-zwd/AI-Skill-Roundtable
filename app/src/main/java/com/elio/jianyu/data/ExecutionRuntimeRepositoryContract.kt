package com.elio.jianyu.data

import com.elio.jianyu.execution.ExecutionBudgetCallKind

data class ExecutionRuntimeBudgetConfig(
    val maxApiCalls: Int = 30,
    val maxCharacters: Int = 6,
    val maxSearchQueriesPerCharacter: Int = 3,
    val maxOutputTokensPerAnswer: Int = 4096,
) {
    init {
        require(maxApiCalls > 0)
        require(maxCharacters > 0)
        require(maxSearchQueriesPerCharacter >= 0)
        require(maxOutputTokensPerAnswer > 0)
    }
}

data class CreateExecutionRuntimeCommand(
    val run: ExecutionRunEntity,
    val participants: List<ExecutionParticipantSnapshotEntity>,
    val budgetRootRunId: String,
    val budget: ExecutionRuntimeBudgetConfig,
)

data class ExecutionRuntimeSnapshot(
    val run: ExecutionRunEntity,
    val participants: List<ExecutionParticipantSnapshotEntity>,
    val participantStates: List<ExecutionParticipantStateEntity>,
    val budget: ExecutionRunBudgetEntity,
)

data class TransitionExecutionParticipantCommand(
    val participantSnapshotId: String,
    val runId: String,
    val expectedStatuses: Set<ExecutionParticipantStatus>,
    val newStatus: ExecutionParticipantStatus,
    val attemptIncrement: Int = 0,
    val outputMessageId: Long? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    val hasIncompleteOutput: Boolean = false,
    val updatedAt: Long,
)

data class ConsumeExecutionBudgetCommand(
    val rootRunId: String,
    val kind: ExecutionBudgetCallKind,
    val count: Int = 1,
    val reserveForRequired: Int = 0,
    val updatedAt: Long,
)

data class SetExecutionBudgetReserveCommand(
    val rootRunId: String,
    val reservedRequiredCalls: Int,
    val updatedAt: Long,
)

data class RecoverInterruptedExecutionCommand(
    val runId: String,
    val updatedAt: Long,
)

internal interface JianyuExecutionRuntimeRepository {
    suspend fun createExecutionRuntime(
        command: CreateExecutionRuntimeCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot>

    suspend fun getExecutionRuntime(
        runId: String,
    ): RepositoryResult<ExecutionRuntimeSnapshot>

    suspend fun transitionExecutionParticipant(
        command: TransitionExecutionParticipantCommand,
    ): RepositoryResult<ExecutionParticipantStateEntity>

    suspend fun consumeExecutionBudget(
        command: ConsumeExecutionBudgetCommand,
    ): RepositoryResult<ExecutionRunBudgetEntity>

    suspend fun setExecutionBudgetReserve(
        command: SetExecutionBudgetReserveCommand,
    ): RepositoryResult<ExecutionRunBudgetEntity>

    suspend fun closeExecutionBudget(
        rootRunId: String,
        updatedAt: Long,
    ): RepositoryResult<ExecutionRunBudgetEntity>

    suspend fun recoverInterruptedExecution(
        command: RecoverInterruptedExecutionCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot>
}

suspend fun JianyuRepository.createExecutionRuntime(
    command: CreateExecutionRuntimeCommand,
): RepositoryResult<ExecutionRuntimeSnapshot> = executionRuntimeCapability(
    "create_execution_runtime",
)?.createExecutionRuntime(command) ?: missingExecutionRuntimeCapability("create_execution_runtime")

suspend fun JianyuRepository.getExecutionRuntime(
    runId: String,
): RepositoryResult<ExecutionRuntimeSnapshot> = executionRuntimeCapability(
    "get_execution_runtime",
)?.getExecutionRuntime(runId) ?: missingExecutionRuntimeCapability("get_execution_runtime")

suspend fun JianyuRepository.transitionExecutionParticipant(
    command: TransitionExecutionParticipantCommand,
): RepositoryResult<ExecutionParticipantStateEntity> = executionRuntimeCapability(
    "transition_execution_participant",
)?.transitionExecutionParticipant(command)
    ?: missingExecutionRuntimeCapability("transition_execution_participant")

suspend fun JianyuRepository.consumeExecutionBudget(
    command: ConsumeExecutionBudgetCommand,
): RepositoryResult<ExecutionRunBudgetEntity> = executionRuntimeCapability(
    "consume_execution_budget",
)?.consumeExecutionBudget(command) ?: missingExecutionRuntimeCapability("consume_execution_budget")

suspend fun JianyuRepository.setExecutionBudgetReserve(
    command: SetExecutionBudgetReserveCommand,
): RepositoryResult<ExecutionRunBudgetEntity> = executionRuntimeCapability(
    "set_execution_budget_reserve",
)?.setExecutionBudgetReserve(command)
    ?: missingExecutionRuntimeCapability("set_execution_budget_reserve")

suspend fun JianyuRepository.closeExecutionBudget(
    rootRunId: String,
    updatedAt: Long,
): RepositoryResult<ExecutionRunBudgetEntity> = executionRuntimeCapability(
    "close_execution_budget",
)?.closeExecutionBudget(rootRunId, updatedAt)
    ?: missingExecutionRuntimeCapability("close_execution_budget")

suspend fun JianyuRepository.recoverInterruptedExecution(
    command: RecoverInterruptedExecutionCommand,
): RepositoryResult<ExecutionRuntimeSnapshot> = executionRuntimeCapability(
    "recover_interrupted_execution",
)?.recoverInterruptedExecution(command)
    ?: missingExecutionRuntimeCapability("recover_interrupted_execution")

private fun JianyuRepository.executionRuntimeCapability(
    operation: String,
): JianyuExecutionRuntimeRepository? {
    @Suppress("UNUSED_VARIABLE")
    val operationName = operation
    return this as? JianyuExecutionRuntimeRepository
}

private fun <T> missingExecutionRuntimeCapability(operation: String): RepositoryResult<T> =
    RepositoryResult.Failure(
        RepositoryError.CompatibilityFailure(operation, "execution_runtime_not_supported"),
    )
