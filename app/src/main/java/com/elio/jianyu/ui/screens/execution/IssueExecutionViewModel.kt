package com.elio.jianyu.ui.screens.execution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.collaboration.StandardFollowUpRequest
import com.elio.jianyu.data.ConfirmedContextItem
import com.elio.jianyu.data.ContextContentHasher
import com.elio.jianyu.data.ContextSelectionDraft
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.MaterialFilter
import com.elio.jianyu.data.PersonalContextFilter
import com.elio.jianyu.data.PrepareExecutionContextCommand
import com.elio.jianyu.data.PreparedExecutionContext
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.UpdateIssueThinkingPolicyCommand
import com.elio.jianyu.data.getExecutionRuntime
import com.elio.jianyu.execution.ExecutionErrorCode
import com.elio.jianyu.execution.ExecutionRepositoryException
import com.elio.jianyu.execution.ExecutionRetryCommand
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.execution.ExecutionStartCommand
import com.elio.jianyu.execution.ExecutionStartException
import com.elio.jianyu.execution.SearchMode
import com.elio.jianyu.ui.screens.context.ContextCandidateUi
import com.elio.jianyu.ui.screens.context.ContextConfirmationUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IssueExecutionViewModel internal constructor(
    private val repository: JianyuRepository,
    private val coordinator: ExecutionRunCoordinator?,
    private val collaborationCoordinator: IssueCollaborationCoordinator? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<IssueExecutionUiState>(IssueExecutionUiState.Loading)
    val state: StateFlow<IssueExecutionUiState> = _state.asStateFlow()

    private val _events = Channel<IssueExecutionEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentIssueId: String? = null
    private var requestedStageId: String? = null
    private var latestRecovery: IssueRecoverySnapshot? = null
    private var latestRuntime: ExecutionRuntimeSnapshot? = null
    private var preparedContextForStart: PreparedExecutionContext? = null
    private var selectedSearchMode: SearchMode = SearchMode.AUTO
    private var selectedThinkingOverride: IssueThinkingPolicy? = null

    fun setSearchMode(mode: SearchMode) {
        selectedSearchMode = mode
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        _state.value = content.copy(searchMode = mode)
    }

    fun setThinkingOverride(policy: IssueThinkingPolicy?) {
        selectedThinkingOverride = policy
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        _state.value = content.copy(thinkingOverride = policy)
    }

    fun setIssueDefaultThinkingPolicy(policy: IssueThinkingPolicy) {
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        if (!content.canChangeIssueDefaultThinkingPolicy || content.operationInProgress) return
        viewModelScope.launch {
            _state.value = content.copy(operationInProgress = true)
            when (
                val result = repository.updateIssueThinkingPolicy(
                    UpdateIssueThinkingPolicyCommand(
                        issueId = content.issueId,
                        policy = policy,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            ) {
                is RepositoryResult.Success -> refreshInternal()
                is RepositoryResult.Failure -> _state.value = operationFailure(
                    "默认思考策略未保存，请刷新后重试。",
                    result.error is RepositoryError.StorageFailure,
                )
            }
        }
    }

    fun load(issueId: String?, stageId: String?) {
        viewModelScope.launch {
            currentIssueId = issueId
            requestedStageId = stageId
            _state.value = IssueExecutionUiState.Loading
            refreshInternal()
        }
    }

    /** PR09-06 在用户确认成员后可直接复用此入口，不需要改写执行状态机。 */
    fun start(command: ExecutionStartCommand) {
        runOperation {
            require(command.issueId == currentIssueId) { "启动命令与当前议题不一致" }
            requireNotNull(coordinator) { "官方 Skill 目录不可用，无法开始执行" }
                .start(command)
        }
    }

    fun startStandardFollowUp(request: StandardFollowUpRequest): Boolean {
        if ((_state.value as? IssueExecutionUiState.Content)?.operationInProgress == true) {
            return false
        }
        val target = collaborationCoordinator ?: return false
        runOperation(
            onFinished = { succeeded ->
                _events.trySend(IssueExecutionEvent.StandardFollowUpFinished(succeeded))
            },
        ) {
            target.startStandardFollowUp(request)
        }
        return true
    }

    /** 停止必须绕过普通操作的忙碌门禁，才能取消正在执行的 Run。 */
    fun stop() {
        val runId = latestRuntime?.run?.id ?: return
        viewModelScope.launch {
            try {
                requireNotNull(coordinator).stop(runId)
                refreshInternal()
            } catch (error: CancellationException) {
                throw error
            } catch (error: ExecutionRepositoryException) {
                _state.value = repositoryFailure(error.repositoryError)
            } catch (error: IllegalArgumentException) {
                _state.value = operationFailure(
                    error.message ?: "停止执行参数无效，请刷新后重试。",
                    false,
                )
            } catch (error: IllegalStateException) {
                _state.value = operationFailure(
                    error.message ?: "停止执行失败，请刷新后重试。",
                    false,
                )
            }
        }
    }

    fun recoverInterrupted() {
        val runId = latestRuntime?.run?.id ?: return
        runOperation { requireNotNull(coordinator).recoverInterrupted(runId) }
    }

    fun retryFailedParticipants() {
        openContextSelection(retryMode = true)
    }

    /** 详情只读取既有 Run；不会恢复、重试或改写历史快照。 */
    fun openRunHistoryDetail(runId: String) {
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        if (content.runHistory.none { it.runId == runId }) return
        val recovery = latestRecovery ?: return
        viewModelScope.launch {
            _state.value = content.copy(
                runDetail = IssueExecutionRunDetailUiState.Loading(runId),
            )
            when (val result = repository.getExecutionRuntime(runId)) {
                is RepositoryResult.Success -> {
                    val runtime = result.value
                    replaceRunDetailIfLoading(
                        runId = runId,
                        detail = IssueExecutionRunDetailUiState.Content(
                            run = runtime.run.toRunHistoryUi(
                                isCurrent = runtime.run.id == latestRuntime?.run?.id,
                            ),
                            participants = mapParticipants(runtime, recovery),
                            budget = runtime.budget.toUi(),
                        ),
                    )
                }
                is RepositoryResult.Failure -> replaceRunDetailIfLoading(
                    runId = runId,
                    detail = IssueExecutionRunDetailUiState.Failure(
                        runId = runId,
                        message = "无法读取该 Run 的持久化详情，请刷新后重试。",
                    ),
                )
            }
        }
    }

    fun dismissRunHistoryDetail() {
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        _state.value = content.copy(runDetail = null)
    }

    fun openContextSelection(retryMode: Boolean = false) {
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        val recovery = latestRecovery ?: return
        val stageId = content.stageId ?: return
        val existing = content.contextConfirmation
        if (existing != null && existing.retryMode == retryMode) {
            _state.value = content.copy(
                contextConfirmation = existing.copy(visible = true, errorMessage = null),
            )
            return
        }
        viewModelScope.launch {
            _state.value = content.copy(operationInProgress = true)
            val runtime = latestRuntime
            val input = runtime?.run?.triggerMessageId
                ?.let { triggerId -> recovery.core.messages.firstOrNull { it.id == triggerId } }
                ?.text
                ?.takeIf(String::isNotBlank)
                ?: "请继续完成此前已确认的问题。"
            val retryIdentity = runtime?.let { "${it.run.id}-${it.run.updatedAt}" }
            val runId = if (retryMode && retryIdentity != null) {
                "$retryIdentity-retry"
            } else {
                "context-${content.issueId}-$stageId-${System.currentTimeMillis()}"
            }
            val materialResult = repository.listMaterials(
                MaterialFilter(
                    issueId = content.issueId,
                    lifecycles = setOf(ContextSourceLifecycle.ACTIVE),
                ),
            )
            val personalResult = repository.listPersonalContexts(
                PersonalContextFilter(lifecycles = setOf(ContextSourceLifecycle.ACTIVE)),
            )
            val previousUsage = runtime?.run?.id?.let { previousRunId ->
                when (val usage = repository.listRunContextUsage(previousRunId)) {
                    is RepositoryResult.Success -> usage.value
                    is RepositoryResult.Failure -> emptyList()
                }
            }.orEmpty()
            val materialCandidates = (materialResult as? RepositoryResult.Success)
                ?.value.orEmpty()
                .filter { it.stageId == null || it.stageId == stageId }
                .map { material ->
                    ContextCandidateUi(
                        sourceType = ContextSourceType.MATERIAL,
                        sourceId = material.id,
                        title = material.title,
                        sourceKind = material.sourceType,
                        sourceLocator = material.sourceLocator,
                        sourcePublishedAt = material.sourcePublishedAt,
                        sourceCapturedAt = material.sourceCapturedAt,
                        originalContent = material.content,
                        selectedContent = material.content,
                        sourceHash = material.contentHash,
                        sourceUpdatedAt = material.updatedAt,
                        sensitive = material.sensitive,
                    )
                }
            val personalCandidates = (personalResult as? RepositoryResult.Success)
                ?.value.orEmpty()
                .map { personal ->
                    ContextCandidateUi(
                        sourceType = ContextSourceType.PERSONAL_CONTEXT,
                        sourceId = personal.id,
                        title = personal.title,
                        sourceKind = "personal_context",
                        sourceLocator = null,
                        sourcePublishedAt = null,
                        sourceCapturedAt = null,
                        originalContent = personal.content,
                        selectedContent = personal.content,
                        sourceHash = personal.contentHash,
                        sourceUpdatedAt = personal.updatedAt,
                        sensitive = personal.sensitive,
                    )
                }
            val errors = buildList {
                if (materialResult is RepositoryResult.Failure) add("资料读取失败")
                if (personalResult is RepositoryResult.Failure) add("个人背景读取失败")
            }
            val baseCharacters = estimateBaseContextCharacters(recovery, content.stageId, input)
            _state.value = content.copy(
                contextConfirmation = ContextConfirmationUiState(
                    visible = true,
                    retryMode = retryMode,
                    runId = runId,
                    issueId = content.issueId,
                    stageId = stageId,
                    currentUserInput = input,
                    baseContextCharacters = baseCharacters,
                    candidates = materialCandidates + personalCandidates,
                    previousUsage = previousUsage,
                    errorMessage = errors.takeIf { it.isNotEmpty() }?.joinToString("；"),
                ),
                operationInProgress = false,
            )
        }
    }

    fun dismissContextSelection() = updateContextConfirmation { copy(visible = false) }

    fun toggleContextCandidate(sourceType: ContextSourceType, sourceId: String) {
        updateCandidate(sourceType, sourceId) { candidate ->
            candidate.copy(selected = !candidate.selected)
        }
    }

    fun setContextNetworkAllowed(
        sourceType: ContextSourceType,
        sourceId: String,
        allowed: Boolean,
    ) {
        updateCandidate(sourceType, sourceId) { it.copy(networkAllowed = allowed) }
    }

    fun setSensitiveContextConfirmed(
        sourceType: ContextSourceType,
        sourceId: String,
        confirmed: Boolean,
    ) {
        updateCandidate(sourceType, sourceId) { it.copy(sensitiveConfirmed = confirmed) }
    }

    fun updateContextExcerpt(sourceType: ContextSourceType, sourceId: String, content: String) {
        updateCandidate(sourceType, sourceId) { it.copy(selectedContent = content) }
    }

    fun confirmContextSelection() {
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        val confirmation = content.contextConfirmation ?: return
        if (!confirmation.visible || confirmation.tooLarge) return
        viewModelScope.launch {
            _state.value = content.copy(operationInProgress = true)
            val now = System.currentTimeMillis()
            val items = confirmation.selectedItems.mapIndexed { index, candidate ->
                ConfirmedContextItem(
                    sourceType = candidate.sourceType,
                    sourceId = candidate.sourceId,
                    title = candidate.title,
                    sourceKind = candidate.sourceKind,
                    sourceLocator = candidate.sourceLocator,
                    sourcePublishedAt = candidate.sourcePublishedAt,
                    sourceCapturedAt = candidate.sourceCapturedAt,
                    content = candidate.selectedContent,
                    contentHash = ContextContentHasher.hash(candidate.selectedContent),
                    expectedSourceHash = candidate.sourceHash,
                    expectedSourceUpdatedAt = candidate.sourceUpdatedAt,
                    confirmationOrder = index,
                    userConfirmedAt = now + index,
                    networkAllowed = candidate.networkAllowed,
                    sensitive = candidate.sensitive,
                    sensitiveConfirmed = candidate.sensitiveConfirmed,
                )
            }
            val prepared = repository.prepareExecutionContext(
                PrepareExecutionContextCommand(
                    draft = ContextSelectionDraft(
                        issueId = confirmation.issueId,
                        stageId = confirmation.stageId,
                        runId = confirmation.runId,
                        baseContextCharacters = confirmation.baseContextCharacters,
                        items = items,
                        confirmed = true,
                    ),
                    preparedAt = now,
                ),
            )
            when (prepared) {
                is RepositoryResult.Failure -> {
                    _state.value = content.copy(
                        contextConfirmation = confirmation.copy(
                            errorMessage = repositoryErrorToContextMessage(prepared.error),
                        ),
                        operationInProgress = false,
                    )
                }
                is RepositoryResult.Success -> {
                    if (confirmation.retryMode) {
                        retryWithPreparedContext(prepared.value, confirmation)
                    } else {
                        preparedContextForStart = prepared.value
                        _state.value = content.copy(
                            contextConfirmation = confirmation.copy(
                                visible = false,
                                confirmedForStart = true,
                                errorMessage = null,
                            ),
                            operationInProgress = false,
                        )
                    }
                }
            }
        }
    }

    /** PR09-06 可在同一 ViewModel 生命周期内读取用户已确认、尚未创建 Run 的上下文。 */
    fun peekPreparedContextForStart(): PreparedExecutionContext? = preparedContextForStart

    private suspend fun retryWithPreparedContext(
        prepared: PreparedExecutionContext,
        confirmation: ContextConfirmationUiState,
    ) {
        val recovery = latestRecovery ?: return
        val runtime = latestRuntime ?: return
        val retryIdentity = "${runtime.run.id}-${runtime.run.updatedAt}"
        val nextRound = recovery.core.messages.maxOfOrNull { it.roundIndex }?.plus(1) ?: 0
        try {
            requireNotNull(coordinator).retry(
                ExecutionRetryCommand(
                    previousRunId = runtime.run.id,
                    newRunId = confirmation.runId,
                    idempotencyKey = "retry:$retryIdentity",
                    currentUserInput = confirmation.currentUserInput,
                    roundIndex = nextRound,
                    userConfirmedAt = System.currentTimeMillis(),
                    thinkingOverride = selectedThinkingOverride,
                    searchMode = selectedSearchMode,
                    contributions = prepared.preparation.contributions,
                    contextUsage = prepared.usage,
                ),
            )
            refreshInternal()
        } catch (error: ExecutionStartException) {
            val current = _state.value as? IssueExecutionUiState.Content ?: return
            _state.value = current.copy(
                contextConfirmation = confirmation.copy(
                    visible = true,
                    errorMessage = error.failure.safeMessage,
                ),
                operationInProgress = false,
            )
        } catch (error: ExecutionRepositoryException) {
            val current = _state.value as? IssueExecutionUiState.Content ?: return
            _state.value = current.copy(
                contextConfirmation = confirmation.copy(
                    visible = true,
                    errorMessage = repositoryErrorToContextMessage(error.repositoryError),
                ),
                operationInProgress = false,
            )
        }
    }

    private fun updateContextConfirmation(
        transform: ContextConfirmationUiState.() -> ContextConfirmationUiState,
    ) {
        val current = _state.value as? IssueExecutionUiState.Content ?: return
        val confirmation = current.contextConfirmation ?: return
        _state.value = current.copy(contextConfirmation = confirmation.transform())
    }

    private fun updateCandidate(
        sourceType: ContextSourceType,
        sourceId: String,
        transform: (ContextCandidateUi) -> ContextCandidateUi,
    ) {
        updateContextConfirmation {
            copy(
                candidates = candidates.map { candidate ->
                    if (candidate.sourceType == sourceType && candidate.sourceId == sourceId) {
                        transform(candidate)
                    } else {
                        candidate
                    }
                },
                errorMessage = null,
                confirmedForStart = false,
            )
        }
        preparedContextForStart = null
    }

    private fun estimateBaseContextCharacters(
        recovery: IssueRecoverySnapshot,
        stageId: String?,
        currentUserInput: String,
    ): Int = recovery.core.issue.title.length +
        recovery.core.stages.firstOrNull { it.id == stageId }?.let { it.title.length + it.objective.length }.orZero() +
        recovery.core.messages.filter { it.stageId == stageId }.sumOf { it.text.length } +
        currentUserInput.length +
        CONTEXT_TEMPLATE_RESERVE_CHARACTERS

    private fun Int?.orZero(): Int = this ?: 0

    private fun repositoryErrorToContextMessage(error: RepositoryError): String = when (error) {
        is RepositoryError.ConstraintViolation -> when (error.constraintCode) {
            "network_not_allowed" -> "至少一个来源未允许本次发送给模型服务。"
            "sensitive_confirmation_required" -> "敏感来源需要额外确认。"
            "context_too_large" -> "上下文超过 24,000 字符，请缩短摘录或移除来源。"
            "source_stale" -> "来源在确认后发生变化，请重新查看并确认。"
            "source_deleted", "source_purged" -> "来源已删除或清除，不能用于本次执行。"
            else -> "上下文确认未通过，请检查选中项。"
        }
        is RepositoryError.InvalidState -> "来源状态已变化，请重新查看并确认。"
        is RepositoryError.NotFound -> "来源或阶段不存在。"
        is RepositoryError.StorageFailure -> "本地存储暂时不可用。"
        else -> "上下文暂时无法确认。"
    }

    private fun runOperation(
        onFinished: (Boolean) -> Unit = {},
        operation: suspend () -> Unit,
    ) {
        if ((_state.value as? IssueExecutionUiState.Content)?.operationInProgress == true) return
        viewModelScope.launch {
            val content = _state.value as? IssueExecutionUiState.Content
            if (content != null) {
                _state.value = content.copy(operationInProgress = true)
            }
            val refreshJob = launch {
                while (isActive) {
                    delay(STATE_REFRESH_INTERVAL_MILLIS)
                    refreshInternal(operationInProgress = true)
                }
            }
            var succeeded = false
            try {
                operation()
                refreshInternal()
                succeeded = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: ExecutionStartException) {
                _state.value = operationFailure(error.failure.safeMessage, false)
            } catch (error: ExecutionRepositoryException) {
                _state.value = repositoryFailure(error.repositoryError)
            } catch (error: IllegalArgumentException) {
                _state.value = operationFailure(
                    error.message ?: "当前执行参数无效，请刷新后重试。",
                    false,
                )
            } catch (error: IllegalStateException) {
                _state.value = operationFailure(
                    error.message ?: "当前执行状态已经变化，请刷新后重试。",
                    false,
                )
            } finally {
                refreshJob.cancel()
                onFinished(succeeded)
            }
        }
    }

    private suspend fun refreshInternal(operationInProgress: Boolean = false) {
        val issueId = currentIssueId
        if (issueId.isNullOrBlank()) {
            _state.value = operationFailure("缺少稳定的 issueId，无法恢复工作区。", false)
            return
        }
        when (val recovered = repository.recoverIssue(issueId)) {
            is RepositoryResult.Failure -> {
                _state.value = repositoryFailure(recovered.error)
            }
            is RepositoryResult.Success -> {
                val recovery = recovered.value
                val selectedStage = requestedStageId
                    ?.let { id -> recovery.core.stages.firstOrNull { it.id == id } }
                    ?: recovery.core.currentStage
                if (requestedStageId != null && selectedStage == null) {
                    _state.value = operationFailure("指定阶段不存在或已被修改。", false)
                    return
                }
                val selectedRun = selectedStage?.let { stage ->
                    recovery.core.runs
                        .filter { it.stageId == stage.id }
                        .maxWithOrNull(compareBy({ it.createdAt }, { it.id }))
                }
                val runtime = when (selectedRun) {
                    null -> null
                    else -> when (val result = repository.getExecutionRuntime(selectedRun.id)) {
                        is RepositoryResult.Success -> result.value
                        is RepositoryResult.Failure -> {
                            _state.value = repositoryFailure(result.error)
                            return
                        }
                    }
                }
                latestRecovery = recovery
                latestRuntime = runtime
                val existingConfirmation =
                    (_state.value as? IssueExecutionUiState.Content)?.contextConfirmation
                _state.value = buildContent(recovery, runtime, selectedStage?.id)
                    .copy(
                        contextConfirmation = existingConfirmation,
                        operationInProgress = operationInProgress,
                    )
            }
        }
    }

    private fun buildContent(
        recovery: IssueRecoverySnapshot,
        runtime: ExecutionRuntimeSnapshot?,
        selectedStageId: String?,
    ): IssueExecutionUiState.Content {
        val stage = selectedStageId
            ?.let { id -> recovery.core.stages.firstOrNull { it.id == id } }
            ?: recovery.core.currentStage
        val participants = runtime?.let { current -> mapParticipants(current, recovery) }.orEmpty()
        val run = runtime?.run
        return IssueExecutionUiState.Content(
            issueId = recovery.core.issue.id,
            issueTitle = recovery.core.issue.title,
            stageId = stage?.id,
            stageTitle = stage?.title,
            phase = phaseFor(runtime),
            runId = run?.id,
            runStatus = run?.status,
            participants = participants,
            budget = runtime?.budget?.toUi(),
            failureCode = run?.failureCode,
            failureMessage = run?.failureMessage,
            executionAvailable = coordinator != null,
            canStop = coordinator != null && run?.status in ACTIVE_RUN_STATES,
            canRetry = coordinator != null && run?.status in RETRYABLE_RUN_STATES,
            canRecoverInterrupted = coordinator != null && run?.status in ACTIVE_RUN_STATES,
            issueDefaultThinkingPolicy = recovery.core.issue.defaultThinkingPolicy,
            thinkingOverride = selectedThinkingOverride,
            canChangeIssueDefaultThinkingPolicy = recovery.core.runs.none { existing ->
                existing.status in ACTIVE_RUN_STATES
            },
            actualModelId = run?.actualModelId,
            actualThinkingLevel = run?.actualThinkingLevel,
            thinkingLevelSource = run?.thinkingLevelSource,
            searchMode = selectedSearchMode,
            runHistory = stage?.let { selected ->
                recovery.core.runs
                    .filter { candidate -> candidate.stageId == selected.id }
                    .sortedWith(
                        compareByDescending<ExecutionRunEntity> { candidate -> candidate.createdAt }
                            .thenByDescending { candidate -> candidate.id },
                    )
                    .map { candidate ->
                        candidate.toRunHistoryUi(isCurrent = candidate.id == run?.id)
                    }
            }.orEmpty(),
        )
    }

    private fun mapParticipants(
        runtime: ExecutionRuntimeSnapshot,
        recovery: IssueRecoverySnapshot,
    ): List<IssueExecutionParticipantUi> {
        val messagesByParticipant = recovery.core.messages
            .asSequence()
            .filter { message -> message.executionRunId == runtime.run.id }
            .groupBy { message -> message.participantSnapshotId }
        return runtime.participants
            .sortedBy { it.position }
            .map { snapshot ->
                val runtimeState = runtime.participantStates.firstOrNull {
                    it.participantSnapshotId == snapshot.id
                }
                val message = messagesByParticipant[snapshot.id]
                    ?.maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
                IssueExecutionParticipantUi(
                    snapshotId = snapshot.id,
                    displayName = snapshot.displayName,
                    position = snapshot.position,
                    status = runtimeState?.status
                        ?: com.elio.jianyu.data.ExecutionParticipantStatus.QUEUED,
                    attemptCount = runtimeState?.attemptCount ?: 0,
                    text = message?.text,
                    isPending = message?.isPending == true,
                    hasIncompleteOutput = runtimeState?.hasIncompleteOutput == true,
                    errorCode = runtimeState?.lastErrorCode,
                    errorMessage = runtimeState?.lastErrorMessage,
                )
            }
    }

    private fun com.elio.jianyu.data.ExecutionRunBudgetEntity.toUi() =
        IssueExecutionBudgetUi(
            maxApiCalls = maxApiCalls,
            usedApiCalls = usedApiCalls,
            reservedRequiredCalls = reservedRequiredCalls,
            closed = closed,
        )

    private fun ExecutionRunEntity.toRunHistoryUi(
        isCurrent: Boolean,
    ) = IssueExecutionRunHistoryUi(
        runId = id,
        runKind = runKind,
        status = status,
        historyScope = historyScope,
        retryOfRunId = retryOfRunId,
        parentRunId = parentRunId,
        failureMessage = failureMessage,
        actualModelId = actualModelId,
        actualThinkingLevel = actualThinkingLevel,
        thinkingLevelSource = thinkingLevelSource,
        isCurrent = isCurrent,
    )

    private fun replaceRunDetailIfLoading(
        runId: String,
        detail: IssueExecutionRunDetailUiState,
    ) {
        val content = _state.value as? IssueExecutionUiState.Content ?: return
        if ((content.runDetail as? IssueExecutionRunDetailUiState.Loading)?.runId != runId) return
        _state.value = content.copy(runDetail = detail)
    }

    private fun phaseFor(runtime: ExecutionRuntimeSnapshot?): IssueExecutionPhase {
        val run = runtime?.run ?: return IssueExecutionPhase.READY
        return when (run.failureCode) {
            ExecutionErrorCode.NO_API_KEY.storageValue -> IssueExecutionPhase.NO_API_KEY
            ExecutionErrorCode.OFFLINE.storageValue -> IssueExecutionPhase.OFFLINE
            ExecutionErrorCode.RATE_LIMITED.storageValue -> IssueExecutionPhase.RATE_LIMITED
            ExecutionErrorCode.BUDGET_EXHAUSTED.storageValue -> IssueExecutionPhase.BUDGET_EXHAUSTED
            else -> when (run.status) {
                ExecutionRunStatus.NOT_STARTED -> IssueExecutionPhase.READY
                ExecutionRunStatus.RUNNING -> IssueExecutionPhase.RUNNING
                ExecutionRunStatus.PARTIAL_SUCCESS -> IssueExecutionPhase.PARTIAL_SUCCESS
                ExecutionRunStatus.SUCCEEDED,
                ExecutionRunStatus.COMPLETED -> IssueExecutionPhase.SUCCEEDED
                ExecutionRunStatus.RETRYABLE -> IssueExecutionPhase.RETRYABLE
                ExecutionRunStatus.STOPPED -> IssueExecutionPhase.STOPPED
                ExecutionRunStatus.FAILED -> IssueExecutionPhase.FAILED
            }
        }
    }

    private fun repositoryFailure(error: RepositoryError): IssueExecutionUiState.Failure =
        when (error) {
            is RepositoryError.StorageFailure -> operationFailure(
                message = "本地存储暂时不可用，请重新打开工作区后重试。",
                storageFailure = true,
            )
            is RepositoryError.NotFound -> operationFailure("议题、阶段或运行不存在。", false)
            is RepositoryError.IdempotencyConflict -> operationFailure(
                "相同命令标识已用于不同执行，请刷新后重试。",
                false,
            )
            is RepositoryError.InvalidState -> operationFailure(
                "当前执行状态已经变化，请刷新后重试。",
                false,
            )
            is RepositoryError.AlreadyExists,
            is RepositoryError.ConstraintViolation,
            is RepositoryError.CompatibilityFailure -> operationFailure(
                "当前数据无法完成该操作，请刷新后重试。",
                false,
            )
        }

    private fun operationFailure(
        message: String,
        storageFailure: Boolean,
    ): IssueExecutionUiState.Failure = IssueExecutionUiState.Failure(
        title = if (storageFailure) "存储不可用" else "工作区操作失败",
        message = message,
        storageFailure = storageFailure,
    )

    companion object {
        private const val STATE_REFRESH_INTERVAL_MILLIS = 120L
        private const val CONTEXT_TEMPLATE_RESERVE_CHARACTERS = 512

        private val ACTIVE_RUN_STATES = setOf(
            ExecutionRunStatus.NOT_STARTED,
            ExecutionRunStatus.RUNNING,
            ExecutionRunStatus.PARTIAL_SUCCESS,
        )
        private val RETRYABLE_RUN_STATES = setOf(
            ExecutionRunStatus.RETRYABLE,
            ExecutionRunStatus.STOPPED,
        )

        fun factory(
            repository: JianyuRepository,
            coordinator: ExecutionRunCoordinator?,
            collaborationCoordinator: IssueCollaborationCoordinator? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                require(modelClass.isAssignableFrom(IssueExecutionViewModel::class.java)) {
                    "不支持的 ViewModel 类型：${modelClass.name}"
                }
                return IssueExecutionViewModel(
                    repository,
                    coordinator,
                    collaborationCoordinator,
                ) as VM
            }
        }
    }
}

sealed interface IssueExecutionEvent {
    data class StandardFollowUpFinished(val succeeded: Boolean) : IssueExecutionEvent
}
