package com.elio.jianyu.ui.screens.execution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.getExecutionRuntime
import com.elio.jianyu.execution.ExecutionErrorCode
import com.elio.jianyu.execution.ExecutionRepositoryException
import com.elio.jianyu.execution.ExecutionRetryCommand
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.execution.ExecutionStartCommand
import com.elio.jianyu.execution.ExecutionStartException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IssueExecutionViewModel internal constructor(
    private val repository: JianyuRepository,
    private val coordinator: ExecutionRunCoordinator?,
) : ViewModel() {
    private val _state = MutableStateFlow<IssueExecutionUiState>(IssueExecutionUiState.Loading)
    val state: StateFlow<IssueExecutionUiState> = _state.asStateFlow()

    private var currentIssueId: String? = null
    private var requestedStageId: String? = null
    private var latestRecovery: IssueRecoverySnapshot? = null
    private var latestRuntime: ExecutionRuntimeSnapshot? = null

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
        val recovery = latestRecovery ?: return
        val runtime = latestRuntime ?: return
        val input = runtime.run.triggerMessageId
            ?.let { triggerId -> recovery.core.messages.firstOrNull { it.id == triggerId } }
            ?.text
            ?.takeIf(String::isNotBlank)
            ?: "请继续完成此前已确认的问题。"
        val retryIdentity = "${runtime.run.id}-${runtime.run.updatedAt}"
        val nextRound = recovery.core.messages.maxOfOrNull { it.roundIndex }?.plus(1) ?: 0
        val now = System.currentTimeMillis()

        runOperation {
            requireNotNull(coordinator).retry(
                ExecutionRetryCommand(
                    previousRunId = runtime.run.id,
                    newRunId = "$retryIdentity-retry",
                    idempotencyKey = "retry:$retryIdentity",
                    currentUserInput = input,
                    roundIndex = nextRound,
                    userConfirmedAt = now,
                ),
            )
        }
    }

    private fun runOperation(operation: suspend () -> Unit) {
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
            try {
                operation()
                refreshInternal()
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
                _state.value = buildContent(recovery, runtime, selectedStage?.id)
                    .copy(operationInProgress = operationInProgress)
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
        val messagesByParticipant = recovery.core.messages
            .filter { message -> message.stageId == stage?.id }
            .groupBy { it.participantSnapshotId }
        val participants = runtime?.participants
            .orEmpty()
            .sortedBy { it.position }
            .map { snapshot ->
                val runtimeState = runtime?.participantStates?.firstOrNull {
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
            budget = runtime?.budget?.let { budget ->
                IssueExecutionBudgetUi(
                    maxApiCalls = budget.maxApiCalls,
                    usedApiCalls = budget.usedApiCalls,
                    reservedRequiredCalls = budget.reservedRequiredCalls,
                    closed = budget.closed,
                )
            },
            failureCode = run?.failureCode,
            failureMessage = run?.failureMessage,
            executionAvailable = coordinator != null,
            canStop = coordinator != null && run?.status in ACTIVE_RUN_STATES,
            canRetry = coordinator != null && run?.status in RETRYABLE_RUN_STATES,
            canRecoverInterrupted = coordinator != null && run?.status in ACTIVE_RUN_STATES,
        )
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
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                require(modelClass.isAssignableFrom(IssueExecutionViewModel::class.java)) {
                    "不支持的 ViewModel 类型：${modelClass.name}"
                }
                return IssueExecutionViewModel(repository, coordinator) as VM
            }
        }
    }
}
