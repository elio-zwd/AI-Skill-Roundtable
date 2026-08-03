package com.elio.jianyu.execution

import com.elio.jianyu.data.AppendDomainMessageCommand
import com.elio.jianyu.data.ConsumeExecutionBudgetCommand
import com.elio.jianyu.data.CreateExecutionRuntimeCommand
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionParticipantStateEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.RecoverInterruptedExecutionCommand
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.SetExecutionBudgetReserveCommand
import com.elio.jianyu.data.StageEntity
import com.elio.jianyu.data.TransitionExecutionParticipantCommand
import com.elio.jianyu.data.TransitionRunCommand
import com.elio.jianyu.data.UpdatePendingDomainMessageCommand
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/**
 * 新见域 Issue 的唯一生产执行协调器。
 *
 * Coordinator 不依赖 Compose、DAO 或 Retrofit 单例；网络和持久化边界均可替换为 Fake。
 * 数据库是运行状态和预算的最终事实源，进程内注册表只防止同进程重复 Job。
 */
class ExecutionRunCoordinator(
    private val persistence: ExecutionPersistenceGateway,
    private val skillResolver: ExecutionSkillResolver,
    private val networkGateway: ExecutionNetworkGateway,
    private val contextBuilder: ExecutionContextBuilder = ExecutionContextBuilder(),
    private val clock: ExecutionClock = SystemExecutionClock,
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val locallyStoppedRuns = ConcurrentHashMap.newKeySet<String>()

    suspend fun start(command: ExecutionStartCommand): ExecutionRunResult {
        return withRunRegistration(command.runId) {
            val issueRecovery = persistence.recoverIssue(command.issueId)
            val stage = requireStage(issueRecovery, command.stageId)
            val participants = try {
                skillResolver.resolve(
                    runId = command.runId,
                    selections = command.selections,
                    createdAt = command.userConfirmedAt,
                )
            } catch (error: InvalidExecutionSkillException) {
                throw ExecutionStartException(
                    ExecutionFailure(
                        ExecutionErrorCode.INVALID_SKILL,
                        "所选 Skill 当前不可执行，请重新选择。",
                    ),
                    error,
                )
            }
            require(command.budget.maxApiCalls >= participants.size) {
                "调用预算不足以覆盖每位参与者的一次必需调用"
            }

            val runtime = persistence.createRuntime(
                CreateExecutionRuntimeCommand(
                    run = ExecutionRunEntity(
                        id = command.runId,
                        issueId = command.issueId,
                        stageId = command.stageId,
                        triggerMessageId = command.triggerMessageId,
                        idempotencyKey = command.idempotencyKey,
                        createdAt = command.userConfirmedAt,
                        updatedAt = command.userConfirmedAt,
                    ),
                    participants = participants,
                    budgetRootRunId = command.runId,
                    budget = command.budget,
                ),
            )

            if (runtime.run.status != ExecutionRunStatus.NOT_STARTED) {
                // 同一稳定命令已启动或已结束时只返回数据库事实，绝不重复调用网络。
                return@withRunRegistration ExecutionRunResult(runtime)
            }

            persistence.transitionRun(
                TransitionRunCommand(
                    runId = runtime.run.id,
                    expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                    newStatus = ExecutionRunStatus.RUNNING,
                    updatedAt = clock.nowMillis(),
                    startedAt = clock.nowMillis(),
                ),
            )
            executeRuntime(
                runtime = persistence.getRuntime(runtime.run.id),
                issueRecovery = issueRecovery,
                stage = stage,
                currentUserInput = command.currentUserInput,
                roundIndex = command.roundIndex,
                model = command.model,
                contributions = command.contributions,
            )
        }
    }

    suspend fun retry(command: ExecutionRetryCommand): ExecutionRunResult {
        return withRunRegistration(command.newRunId) {
            val previous = persistence.getRuntime(command.previousRunId)
            require(
                previous.run.status == ExecutionRunStatus.RETRYABLE ||
                    previous.run.status == ExecutionRunStatus.STOPPED
            ) { "只有可重试或用户停止的 Run 可以创建子 Run" }

            val statesByParticipant = previous.participantStates.associateBy {
                it.participantSnapshotId
            }
            val retryParticipants = previous.participants
                .sortedBy { it.position }
                .filter { participant ->
                    statesByParticipant[participant.id]?.status in RETRYABLE_PARTICIPANT_STATES
                }
                .mapIndexed { index, participant ->
                    participant.copy(
                        id = "${command.newRunId}-participant-$index",
                        runId = command.newRunId,
                        position = index,
                        createdAt = command.userConfirmedAt,
                    )
                }
            require(retryParticipants.isNotEmpty()) { "没有需要重试的参与者" }

            val issueRecovery = persistence.recoverIssue(previous.run.issueId)
            val stage = requireStage(issueRecovery, previous.run.stageId)
            val budgetConfig = ExecutionRuntimeBudgetConfig(
                maxApiCalls = previous.budget.maxApiCalls,
                maxCharacters = previous.budget.maxCharacters,
                maxSearchQueriesPerCharacter = previous.budget.maxSearchQueriesPerCharacter,
                maxOutputTokensPerAnswer = previous.budget.maxOutputTokensPerAnswer,
            )
            val child = persistence.createRuntime(
                CreateExecutionRuntimeCommand(
                    run = ExecutionRunEntity(
                        id = command.newRunId,
                        issueId = previous.run.issueId,
                        stageId = previous.run.stageId,
                        triggerMessageId = previous.run.triggerMessageId,
                        idempotencyKey = command.idempotencyKey,
                        retryOfRunId = previous.run.id,
                        createdAt = command.userConfirmedAt,
                        updatedAt = command.userConfirmedAt,
                    ),
                    participants = retryParticipants,
                    budgetRootRunId = previous.budget.rootRunId,
                    budget = budgetConfig,
                ),
            )
            if (child.run.status != ExecutionRunStatus.NOT_STARTED) {
                return@withRunRegistration ExecutionRunResult(child)
            }

            val remaining = child.budget.maxApiCalls - child.budget.usedApiCalls
            if (remaining < retryParticipants.size) {
                return@withRunRegistration markBudgetExhausted(child)
            }
            persistence.setBudgetReserve(
                SetExecutionBudgetReserveCommand(
                    rootRunId = child.budget.rootRunId,
                    reservedRequiredCalls = retryParticipants.size,
                    updatedAt = clock.nowMillis(),
                ),
            )
            persistence.transitionRun(
                TransitionRunCommand(
                    runId = child.run.id,
                    expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                    newStatus = ExecutionRunStatus.RUNNING,
                    updatedAt = clock.nowMillis(),
                    startedAt = clock.nowMillis(),
                ),
            )
            executeRuntime(
                runtime = persistence.getRuntime(child.run.id),
                issueRecovery = issueRecovery,
                stage = stage,
                currentUserInput = command.currentUserInput,
                roundIndex = command.roundIndex,
                model = command.model,
                contributions = command.contributions,
            )
        }
    }

    /**
     * 用户停止先持久化 Run，再收敛成员和 Pending，最后取消进程内 Job。
     */
    suspend fun stop(runId: String): ExecutionRuntimeSnapshot {
        val before = persistence.getRuntime(runId)
        if (before.run.status in TERMINAL_RUN_STATES) return before

        persistence.transitionRun(
            TransitionRunCommand(
                runId = runId,
                expectedStatuses = setOf(
                    ExecutionRunStatus.NOT_STARTED,
                    ExecutionRunStatus.RUNNING,
                    ExecutionRunStatus.PARTIAL_SUCCESS,
                ),
                newStatus = ExecutionRunStatus.STOPPED,
                updatedAt = clock.nowMillis(),
                finishedAt = clock.nowMillis(),
                stoppedAt = clock.nowMillis(),
                failureCode = ExecutionErrorCode.USER_STOPPED.storageValue,
                failureMessage = "用户已停止本次执行。",
            ),
        )

        val issueRecovery = persistence.recoverIssue(before.run.issueId)
        before.participantStates
            .filter { it.status in ACTIVE_PARTICIPANT_STATES }
            .forEach { state ->
                state.outputMessageId?.let { messageId ->
                    issueRecovery.core.messages.firstOrNull { it.id == messageId }
                        ?.takeIf { it.isPending }
                        ?.let { message ->
                            persistence.updatePendingMessage(
                                UpdatePendingDomainMessageCommand(
                                    messageId = message.id,
                                    issueId = requireNotNull(message.issueId),
                                    stageId = requireNotNull(message.stageId),
                                    executionRunId = message.executionRunId,
                                    participantSnapshotId = message.participantSnapshotId,
                                    text = message.text.ifBlank { "已停止，未生成完整回答。" },
                                    keepPending = false,
                                ),
                            )
                        }
                }
                persistence.transitionParticipant(
                    TransitionExecutionParticipantCommand(
                        participantSnapshotId = state.participantSnapshotId,
                        runId = state.runId,
                        expectedStatuses = ACTIVE_PARTICIPANT_STATES,
                        newStatus = ExecutionParticipantStatus.STOPPED,
                        outputMessageId = state.outputMessageId,
                        startedAt = state.startedAt,
                        finishedAt = clock.nowMillis(),
                        lastErrorCode = ExecutionErrorCode.USER_STOPPED.storageValue,
                        lastErrorMessage = "用户已停止本次执行。",
                        hasIncompleteOutput = state.outputMessageId != null,
                        updatedAt = clock.nowMillis(),
                    ),
                )
            }

        locallyStoppedRuns += runId
        activeJobs.remove(runId)?.cancel(CancellationException("execution_stopped_by_user"))
        return persistence.getRuntime(runId)
    }

    /** 只收敛数据库中的中断状态，不自动创建 Run、Pending 或网络请求。 */
    suspend fun recoverInterrupted(runId: String): ExecutionRuntimeSnapshot {
        return persistence.recoverInterrupted(
            RecoverInterruptedExecutionCommand(
                runId = runId,
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    private suspend fun executeRuntime(
        runtime: ExecutionRuntimeSnapshot,
        issueRecovery: IssueRecoverySnapshot,
        stage: StageEntity,
        currentUserInput: String,
        roundIndex: Int,
        model: String,
        contributions: List<ExecutionContextContribution>,
    ): ExecutionRunResult {
        val failures = linkedMapOf<String, ExecutionFailure>()
        val issue = issueRecovery.core.issue
        val history = issueRecovery.core.messages.filter { message ->
            message.stageId == stage.id &&
                message.id != runtime.run.triggerMessageId &&
                !message.isPending
        }

        runtime.participants.sortedBy { it.position }.forEachIndexed { index, participant ->
            val current = persistence.getRuntime(runtime.run.id)
            if (current.run.status == ExecutionRunStatus.STOPPED) return@forEachIndexed
            val currentState = current.participantStates.first { state ->
                state.participantSnapshotId == participant.id
            }
            if (currentState.status == ExecutionParticipantStatus.SUCCEEDED) {
                return@forEachIndexed
            }

            val failure = executeParticipant(
                runtime = current,
                participant = participant,
                issueRecovery = issueRecovery,
                stage = stage,
                history = history,
                currentUserInput = currentUserInput,
                roundIndex = roundIndex,
                model = model,
                contributions = contributions,
                remainingRequiredCalls = current.participants.size - index - 1,
            )
            if (failure != null) failures[participant.id] = failure
            aggregateRun(runtime.run.id, failures.values.firstOrNull())
        }

        val finalRuntime = aggregateRun(runtime.run.id, failures.values.firstOrNull())
        if (finalRuntime.run.status in setOf(
                ExecutionRunStatus.SUCCEEDED,
                ExecutionRunStatus.FAILED,
            )
        ) {
            persistence.closeBudget(finalRuntime.budget.rootRunId, clock.nowMillis())
        }
        return ExecutionRunResult(
            runtime = persistence.getRuntime(runtime.run.id),
            participantFailures = failures,
        )
    }

    private suspend fun executeParticipant(
        runtime: ExecutionRuntimeSnapshot,
        participant: ExecutionParticipantSnapshotEntity,
        issueRecovery: IssueRecoverySnapshot,
        stage: StageEntity,
        history: List<com.elio.jianyu.data.Message>,
        currentUserInput: String,
        roundIndex: Int,
        model: String,
        contributions: List<ExecutionContextContribution>,
        remainingRequiredCalls: Int,
    ): ExecutionFailure? {
        val run = runtime.run
        val now = clock.nowMillis()
        persistence.transitionParticipant(
            TransitionExecutionParticipantCommand(
                participantSnapshotId = participant.id,
                runId = run.id,
                expectedStatuses = setOf(ExecutionParticipantStatus.QUEUED),
                newStatus = ExecutionParticipantStatus.RUNNING,
                startedAt = now,
                updatedAt = now,
            ),
        )

        var pendingMessageId: Long? = null
        var latestText = ""
        return try {
            val modelRequest = contextBuilder.build(
                ExecutionContextInput(
                    issue = issueRecovery.core.issue,
                    stage = stage,
                    participant = participant,
                    currentRunId = run.id,
                    currentUserInput = currentUserInput,
                    roundIndex = roundIndex,
                    history = history,
                    contributions = contributions,
                ),
            )
            val preparedCall = networkGateway.prepare(
                ExecutionNetworkRequest(
                    sessionId = StableExecutionIds.sessionId(run.issueId),
                    participant = participant,
                    modelRequest = modelRequest,
                    model = model,
                    maxOutputTokens = runtime.budget.maxOutputTokensPerAnswer,
                ),
            )

            pendingMessageId = StableExecutionIds.messageId(run.id, participant.id)
            persistence.appendMessage(
                AppendDomainMessageCommand(
                    messageId = requireNotNull(pendingMessageId),
                    issueId = run.issueId,
                    stageId = run.stageId,
                    executionRunId = run.id,
                    participantSnapshotId = participant.id,
                    senderId = participant.sourceId,
                    senderName = participant.displayName,
                    avatar = participant.avatar,
                    text = "",
                    timestamp = clock.nowMillis(),
                    isPending = true,
                    roundIndex = roundIndex,
                    compatibilitySessionTitle = issueRecovery.core.issue.title,
                ),
            )
            persistence.transitionParticipant(
                TransitionExecutionParticipantCommand(
                    participantSnapshotId = participant.id,
                    runId = run.id,
                    expectedStatuses = setOf(ExecutionParticipantStatus.RUNNING),
                    newStatus = ExecutionParticipantStatus.STREAMING,
                    outputMessageId = pendingMessageId,
                    startedAt = now,
                    updatedAt = clock.nowMillis(),
                ),
            )

            val networkResult = preparedCall.execute(
                onAttemptStarted = {
                    ensureAcceptsWrites(run.id, participant.id)
                    try {
                        persistence.consumeBudget(
                            ConsumeExecutionBudgetCommand(
                                rootRunId = runtime.budget.rootRunId,
                                kind = ExecutionBudgetCallKind.REQUIRED,
                                count = 1,
                                reserveForRequired = remainingRequiredCalls,
                                updatedAt = clock.nowMillis(),
                            ),
                        )
                    } catch (error: ExecutionRepositoryException) {
                        val repositoryError = error.repositoryError
                        if (
                            repositoryError is RepositoryError.InvalidState &&
                            repositoryError.stateCode == "budget_exhausted"
                        ) {
                            throw ExecutionBudgetExhaustedException()
                        }
                        throw error
                    }
                    val currentState = persistence.getRuntime(run.id).participantStates
                        .first { it.participantSnapshotId == participant.id }
                    persistence.transitionParticipant(
                        TransitionExecutionParticipantCommand(
                            participantSnapshotId = participant.id,
                            runId = run.id,
                            expectedStatuses = setOf(
                                ExecutionParticipantStatus.RUNNING,
                                ExecutionParticipantStatus.STREAMING,
                            ),
                            newStatus = currentState.status,
                            attemptIncrement = 1,
                            outputMessageId = pendingMessageId,
                            startedAt = currentState.startedAt,
                            updatedAt = clock.nowMillis(),
                        ),
                    )
                },
                onTextUpdate = { accumulatedText ->
                    ensureAcceptsWrites(run.id, participant.id)
                    latestText = accumulatedText
                    persistence.updatePendingMessage(
                        UpdatePendingDomainMessageCommand(
                            messageId = requireNotNull(pendingMessageId),
                            issueId = run.issueId,
                            stageId = run.stageId,
                            executionRunId = run.id,
                            participantSnapshotId = participant.id,
                            text = accumulatedText,
                            keepPending = true,
                        ),
                    )
                },
            )
            val finalText = networkResult.outputText.trim()
            if (finalText.isBlank()) throw ExecutionEmptyResponseException()
            ensureAcceptsWrites(run.id, participant.id)
            persistence.updatePendingMessage(
                UpdatePendingDomainMessageCommand(
                    messageId = requireNotNull(pendingMessageId),
                    issueId = run.issueId,
                    stageId = run.stageId,
                    executionRunId = run.id,
                    participantSnapshotId = participant.id,
                    text = finalText,
                    keepPending = false,
                ),
            )
            persistence.transitionParticipant(
                TransitionExecutionParticipantCommand(
                    participantSnapshotId = participant.id,
                    runId = run.id,
                    expectedStatuses = setOf(
                        ExecutionParticipantStatus.RUNNING,
                        ExecutionParticipantStatus.STREAMING,
                    ),
                    newStatus = ExecutionParticipantStatus.SUCCEEDED,
                    outputMessageId = pendingMessageId,
                    startedAt = now,
                    finishedAt = clock.nowMillis(),
                    updatedAt = clock.nowMillis(),
                ),
            )
            null
        } catch (error: CancellationException) {
            if (locallyStoppedRuns.contains(run.id) || isPersistedStopped(run.id)) {
                null
            } else {
                throw error
            }
        } catch (error: Throwable) {
            val failure = when (error) {
                is ExecutionRepositoryException ->
                    ExecutionErrorMapper.fromRepositoryError(error.repositoryError)
                is InvalidExecutionSkillException -> ExecutionFailure(
                    ExecutionErrorCode.INVALID_SKILL,
                    "所选 Skill 当前不可执行，请重新选择。",
                )
                else -> ExecutionErrorMapper.fromThrowable(error)
            }
            pendingMessageId?.let { messageId ->
                persistence.updatePendingMessage(
                    UpdatePendingDomainMessageCommand(
                        messageId = messageId,
                        issueId = run.issueId,
                        stageId = run.stageId,
                        executionRunId = run.id,
                        participantSnapshotId = participant.id,
                        text = latestText.ifBlank { failure.safeMessage },
                        keepPending = false,
                    ),
                )
            }
            val targetStatus = when {
                failure.code == ExecutionErrorCode.TIMEOUT -> ExecutionParticipantStatus.TIMED_OUT
                failure.code == ExecutionErrorCode.USER_STOPPED -> ExecutionParticipantStatus.STOPPED
                failure.retryable -> ExecutionParticipantStatus.RETRYABLE
                else -> ExecutionParticipantStatus.FAILED
            }
            persistence.transitionParticipant(
                TransitionExecutionParticipantCommand(
                    participantSnapshotId = participant.id,
                    runId = run.id,
                    expectedStatuses = ACTIVE_PARTICIPANT_STATES,
                    newStatus = targetStatus,
                    outputMessageId = pendingMessageId,
                    startedAt = now,
                    finishedAt = clock.nowMillis(),
                    lastErrorCode = failure.code.storageValue,
                    lastErrorMessage = failure.safeMessage,
                    hasIncompleteOutput = latestText.isNotBlank(),
                    updatedAt = clock.nowMillis(),
                ),
            )
            failure
        }
    }

    private suspend fun aggregateRun(
        runId: String,
        representativeFailure: ExecutionFailure?,
    ): ExecutionRuntimeSnapshot {
        val current = persistence.getRuntime(runId)
        if (current.run.status == ExecutionRunStatus.STOPPED) return current
        val retryableIndexes = current.participantStates.indices
            .filterTo(mutableSetOf()) { index ->
                current.participantStates[index].status in RETRYABLE_PARTICIPANT_STATES
            }
        val target = ExecutionStateMachine.aggregate(
            participantStatuses = current.participantStates.map { it.status },
            retryableParticipantIds = retryableIndexes,
        )
        if (target == current.run.status) return current
        if (!ExecutionStateMachine.canTransition(current.run.status, target)) return current

        val terminalAt = if (target in setOf(
                ExecutionRunStatus.SUCCEEDED,
                ExecutionRunStatus.RETRYABLE,
                ExecutionRunStatus.FAILED,
            )
        ) {
            clock.nowMillis()
        } else {
            null
        }
        persistence.transitionRun(
            TransitionRunCommand(
                runId = runId,
                expectedStatuses = setOf(current.run.status),
                newStatus = target,
                updatedAt = clock.nowMillis(),
                startedAt = current.run.startedAt,
                finishedAt = terminalAt,
                failureCode = representativeFailure?.code?.storageValue,
                failureMessage = representativeFailure?.safeMessage,
            ),
        )
        return persistence.getRuntime(runId)
    }

    private suspend fun markBudgetExhausted(
        runtime: ExecutionRuntimeSnapshot,
    ): ExecutionRunResult {
        val failure = ExecutionFailure(
            ExecutionErrorCode.BUDGET_EXHAUSTED,
            "本次执行的调用预算已用完。",
        )
        runtime.participantStates.forEach { state ->
            persistence.transitionParticipant(
                TransitionExecutionParticipantCommand(
                    participantSnapshotId = state.participantSnapshotId,
                    runId = state.runId,
                    expectedStatuses = setOf(ExecutionParticipantStatus.QUEUED),
                    newStatus = ExecutionParticipantStatus.RETRYABLE,
                    finishedAt = clock.nowMillis(),
                    lastErrorCode = failure.code.storageValue,
                    lastErrorMessage = failure.safeMessage,
                    updatedAt = clock.nowMillis(),
                ),
            )
        }
        persistence.transitionRun(
            TransitionRunCommand(
                runId = runtime.run.id,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.FAILED,
                updatedAt = clock.nowMillis(),
                finishedAt = clock.nowMillis(),
                failureCode = failure.code.storageValue,
                failureMessage = failure.safeMessage,
            ),
        )
        return ExecutionRunResult(
            runtime = persistence.getRuntime(runtime.run.id),
            participantFailures = runtime.participants.associate { it.id to failure },
        )
    }

    private suspend fun ensureAcceptsWrites(runId: String, participantId: String) {
        val runtime = persistence.getRuntime(runId)
        val participantState = runtime.participantStates.firstOrNull {
            it.participantSnapshotId == participantId
        }
        if (
            runtime.run.status !in ACTIVE_RUN_STATES ||
            participantState?.status !in setOf(
                ExecutionParticipantStatus.RUNNING,
                ExecutionParticipantStatus.STREAMING,
            )
        ) {
            throw CancellationException("execution_no_longer_accepts_writes")
        }
    }

    private suspend fun isPersistedStopped(runId: String): Boolean =
        persistence.getRuntime(runId).run.status == ExecutionRunStatus.STOPPED

    private fun requireStage(
        recovery: IssueRecoverySnapshot,
        stageId: String,
    ): StageEntity = recovery.core.stages.firstOrNull { it.id == stageId }
        ?: throw ExecutionStartException(
            ExecutionFailure(
                ExecutionErrorCode.INVALID_STATE,
                "当前阶段不存在或已被修改。",
            ),
        )

    private suspend fun withRunRegistration(
        runId: String,
        block: suspend () -> ExecutionRunResult,
    ): ExecutionRunResult {
        val currentJob = currentCoroutineContext()[Job]
            ?: throw IllegalStateException("Execution requires a coroutine Job")
        val existing = activeJobs.putIfAbsent(runId, currentJob)
        if (existing != null && existing !== currentJob) {
            existing.join()
            return ExecutionRunResult(persistence.getRuntime(runId))
        }
        return try {
            block()
        } finally {
            activeJobs.remove(runId, currentJob)
            locallyStoppedRuns.remove(runId)
        }
    }

    private companion object {
        val ACTIVE_RUN_STATES = setOf(
            ExecutionRunStatus.NOT_STARTED,
            ExecutionRunStatus.RUNNING,
            ExecutionRunStatus.PARTIAL_SUCCESS,
        )
        val TERMINAL_RUN_STATES = setOf(
            ExecutionRunStatus.SUCCEEDED,
            ExecutionRunStatus.STOPPED,
            ExecutionRunStatus.FAILED,
            ExecutionRunStatus.COMPLETED,
        )
        val ACTIVE_PARTICIPANT_STATES = setOf(
            ExecutionParticipantStatus.QUEUED,
            ExecutionParticipantStatus.RUNNING,
            ExecutionParticipantStatus.STREAMING,
        )
        val RETRYABLE_PARTICIPANT_STATES = setOf(
            ExecutionParticipantStatus.QUEUED,
            ExecutionParticipantStatus.RETRYABLE,
            ExecutionParticipantStatus.TIMED_OUT,
            ExecutionParticipantStatus.STOPPED,
        )
    }
}

class ExecutionStartException(
    val failure: ExecutionFailure,
    cause: Throwable? = null,
) : IllegalStateException(failure.safeMessage, cause)
