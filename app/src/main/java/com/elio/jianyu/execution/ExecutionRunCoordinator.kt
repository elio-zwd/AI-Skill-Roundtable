package com.elio.jianyu.execution

import com.elio.jianyu.data.AppendDomainMessageCommand
import com.elio.jianyu.data.CreateExecutionRuntimeCommand
import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionParticipantStateEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.RecoverInterruptedExecutionCommand
import com.elio.jianyu.data.RecordExecutionApiCallCommand
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
    private val modelIdResolver: (String) -> String = { requestedModel -> requestedModel },
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val locallyStoppedRuns = ConcurrentHashMap.newKeySet<String>()

    suspend fun start(command: ExecutionStartCommand): ExecutionRunResult {
        ExecutionContextGate.validate(command.contributions, command.contextUsage)?.let { failure ->
            throw ExecutionStartException(failure)
        }
        return withRunRegistration(command.runId) {
            val modelId = modelIdResolver(command.model)
            val issueRecovery = persistence.recoverIssue(command.issueId)
            val stage = requireStage(issueRecovery, command.stageId)
            val thinking = ExecutionThinkingPolicyResolver.resolve(
                issueDefault = issueRecovery.core.issue.defaultThinkingPolicy,
                roundOverride = command.thinkingOverride,
                runKind = ExecutionRunKind.STANDARD,
            )
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
                        actualModelId = modelId,
                        actualThinkingLevel = thinking.level,
                        thinkingLevelSource = thinking.source,
                    ),
                    participants = participants,
                    budgetRootRunId = command.runId,
                    budget = command.budget,
                    contextUsage = command.contextUsage,
                ),
            )
            if (runtime.run.status != ExecutionRunStatus.NOT_STARTED) {
                return@withRunRegistration ExecutionRunResult(runtime)
            }
            transitionRunToRunning(runtime.run.id)
            executeRuntime(
                runtime = persistence.getRuntime(runtime.run.id),
                issueRecovery = issueRecovery,
                stage = stage,
                currentUserInput = command.currentUserInput,
                roundIndex = command.roundIndex,
                model = modelId,
                searchMode = command.searchMode,
                contributions = command.contributions,
            )
        }
    }

    /**
     * 执行已经由协作 Repository 原子创建的 Runtime；不再创建任何 Message、Usage 或预算事实。
     */
    suspend fun startPrepared(command: ExecutionPreparedRunCommand): ExecutionRunResult {
        return withRunRegistration(command.runId) {
            val issueRecovery = persistence.recoverIssue(command.issueId)
            val stage = requireStage(issueRecovery, command.stageId)
            val runtime = persistence.getRuntime(command.runId)
            require(runtime.run.issueId == command.issueId && runtime.run.stageId == command.stageId)
            if (runtime.run.status != ExecutionRunStatus.NOT_STARTED) {
                return@withRunRegistration ExecutionRunResult(runtime)
            }
            transitionRunToRunning(runtime.run.id)
            executeRuntime(
                runtime = persistence.getRuntime(runtime.run.id),
                issueRecovery = issueRecovery,
                stage = stage,
                currentUserInput = command.currentUserInput,
                roundIndex = command.roundIndex,
                model = runtime.run.actualModelId ?: modelIdResolver(command.model),
                searchMode = command.searchMode,
                contributions = command.contributions,
                keepBudgetOpenOnSuccess = command.keepBudgetOpenOnSuccess,
            )
        }
    }

    suspend fun retry(command: ExecutionRetryCommand): ExecutionRunResult {
        ExecutionContextGate.validate(command.contributions, command.contextUsage)?.let { failure ->
            throw ExecutionStartException(failure)
        }
        return withRunRegistration(command.newRunId) {
            val modelId = modelIdResolver(command.model)
            val previous = persistence.getRuntime(command.previousRunId)
            require(previous.run.runKind == ExecutionRunKind.STANDARD) {
                "协作 Run 必须通过协作协调器重试，以保留消息快照和 Discussion 关系"
            }
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
            val thinking = ExecutionThinkingPolicyResolver.resolve(
                issueDefault = issueRecovery.core.issue.defaultThinkingPolicy,
                roundOverride = command.thinkingOverride,
                runKind = previous.run.runKind,
            )
            val budgetConfig = ExecutionRuntimeBudgetConfig(
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
                        runKind = previous.run.runKind,
                        parentRunId = previous.run.parentRunId,
                        discussionId = previous.run.discussionId,
                        historyScope = previous.run.historyScope,
                        actualModelId = modelId,
                        actualThinkingLevel = thinking.level,
                        thinkingLevelSource = thinking.source,
                    ),
                    participants = retryParticipants,
                    budgetRootRunId = previous.budget.rootRunId,
                    budget = budgetConfig,
                    contextUsage = command.contextUsage,
                ),
            )
            if (child.run.status != ExecutionRunStatus.NOT_STARTED) {
                return@withRunRegistration ExecutionRunResult(child)
            }
            transitionRunToRunning(child.run.id)
            executeRuntime(
                runtime = persistence.getRuntime(child.run.id),
                issueRecovery = issueRecovery,
                stage = stage,
                currentUserInput = command.currentUserInput,
                roundIndex = command.roundIndex,
                model = modelId,
                searchMode = command.searchMode,
                contributions = command.contributions,
            )
        }
    }

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

    suspend fun recoverInterrupted(runId: String): ExecutionRuntimeSnapshot =
        persistence.recoverInterrupted(
            RecoverInterruptedExecutionCommand(
                runId = runId,
                updatedAt = clock.nowMillis(),
            ),
        )

    private suspend fun transitionRunToRunning(runId: String) {
        val now = clock.nowMillis()
        persistence.transitionRun(
            TransitionRunCommand(
                runId = runId,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = now,
                startedAt = now,
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
        searchMode: SearchMode,
        contributions: List<ExecutionContextContribution>,
        keepBudgetOpenOnSuccess: Boolean = false,
    ): ExecutionRunResult {
        val failures = linkedMapOf<String, ExecutionFailure>()
        val history = historyFor(runtime, issueRecovery, stage)
        runtime.participants.sortedBy { it.position }.forEach { participant ->
            val current = persistence.getRuntime(runtime.run.id)
            if (current.run.status == ExecutionRunStatus.STOPPED) return@forEach
            val currentState = current.participantStates.first { state ->
                state.participantSnapshotId == participant.id
            }
            if (currentState.status == ExecutionParticipantStatus.SUCCEEDED) {
                return@forEach
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
                searchMode = searchMode,
                contributions = contributions,
            )
            if (failure != null) failures[participant.id] = failure
            aggregateRun(runtime.run.id, failures.values.firstOrNull())
        }

        val finalRuntime = aggregateRun(runtime.run.id, failures.values.firstOrNull())
        val shouldClose = when (finalRuntime.run.status) {
            ExecutionRunStatus.SUCCEEDED -> !keepBudgetOpenOnSuccess
            ExecutionRunStatus.FAILED -> true
            else -> false
        }
        if (shouldClose) {
            persistence.closeBudget(finalRuntime.budget.rootRunId, clock.nowMillis())
        }
        return ExecutionRunResult(
            runtime = persistence.getRuntime(runtime.run.id),
            participantFailures = failures,
        )
    }

    private suspend fun historyFor(
        runtime: ExecutionRuntimeSnapshot,
        issueRecovery: IssueRecoverySnapshot,
        stage: StageEntity,
    ): List<ExecutionHistoryEntry> = when (runtime.run.historyScope) {
        ExecutionHistoryScope.FULL_STAGE -> issueRecovery.core.messages
            .asSequence()
            .filter { message ->
                message.stageId == stage.id &&
                    message.id != runtime.run.triggerMessageId &&
                    !message.isPending &&
                    message.executionRunId != runtime.run.id
            }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
            .mapIndexed { index, message ->
                ExecutionHistoryEntry(
                    sourceMessageId = message.id,
                    senderName = message.senderName,
                    content = message.text,
                    usageOrder = index,
                    sourceExecutionRunId = message.executionRunId,
                    sourceParticipantSnapshotId = message.participantSnapshotId,
                )
            }
            .toList()
        ExecutionHistoryScope.EXPLICIT_MESSAGES -> persistence.listMessageUsage(runtime.run.id)
            .sortedWith(compareBy({ it.usageOrder }, { it.sourceMessageId }))
            .map { usage ->
                ExecutionHistoryEntry(
                    sourceMessageId = usage.sourceMessageId,
                    senderName = usage.senderNameSnapshot,
                    content = usage.contentSnapshot,
                    usageOrder = usage.usageOrder,
                    sourceExecutionRunId = usage.sourceExecutionRunId,
                    sourceParticipantSnapshotId = usage.sourceParticipantSnapshotId,
                )
            }
        ExecutionHistoryScope.NO_HISTORY -> {
            check(persistence.listMessageUsage(runtime.run.id).isEmpty()) {
                "NO_HISTORY Run 不得存在消息使用快照"
            }
            emptyList()
        }
    }

    private suspend fun executeParticipant(
        runtime: ExecutionRuntimeSnapshot,
        participant: ExecutionParticipantSnapshotEntity,
        issueRecovery: IssueRecoverySnapshot,
        stage: StageEntity,
        history: List<ExecutionHistoryEntry>,
        currentUserInput: String,
        roundIndex: Int,
        model: String,
        searchMode: SearchMode,
        contributions: List<ExecutionContextContribution>,
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
                    historyScope = run.historyScope,
                    contributions = contributions,
                    promptMode = if (run.runKind == ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS) {
                        ExecutionPromptMode.CROSS_DISCUSSION_SYNTHESIS
                    } else {
                        ExecutionPromptMode.INDEPENDENT_RESPONSE
                    },
                ),
            )
            val preparedCall = networkGateway.prepare(
                ExecutionNetworkRequest(
                    sessionId = StableExecutionIds.sessionId(run.issueId),
                    participant = participant,
                    modelRequest = modelRequest,
                    model = run.actualModelId ?: model,
                    thinkingLevel = requireNotNull(run.actualThinkingLevel) {
                        "新执行 Run 必须在发起请求前保存思考强度快照"
                    }.storageValue,
                    interactionChainKey = "${run.stageId}:${participant.sourceId}",
                    maxOutputTokens = runtime.budget.maxOutputTokensPerAnswer,
                    searchMode = searchMode,
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
                    persistence.recordApiCall(
                        RecordExecutionApiCallCommand(
                            rootRunId = runtime.budget.rootRunId,
                            count = 1,
                            updatedAt = clock.nowMillis(),
                        ),
                    )
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
            if (networkResult.providerModel != null && networkResult.providerModel != run.actualModelId) {
                throw ExecutionModelMismatchException()
            }
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
        ) clock.nowMillis() else null
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
