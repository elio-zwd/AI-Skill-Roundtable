package com.elio.jianyu.ui.screens.execution

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.elio.jianyu.collaboration.CollaborationContextSelection
import com.elio.jianyu.collaboration.CollaborationRepositoryException
import com.elio.jianyu.collaboration.CollaborationRetryRequest
import com.elio.jianyu.collaboration.CollaborationStateException
import com.elio.jianyu.collaboration.CrossDiscussionRequest
import com.elio.jianyu.collaboration.CrossDiscussionSynthesisRequest
import com.elio.jianyu.collaboration.DirectedResponseRequest
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.collaboration.IssueCollaborationSnapshot
import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.PreparedExecutionContext
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 工作区协作草稿与恢复状态。已确认事实全部由 Room 和 [IssueCollaborationCoordinator] 管理。
 */
class IssueCollaborationViewModel internal constructor(
    private val coordinator: IssueCollaborationCoordinator?,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow<IssueCollaborationUiState>(IssueCollaborationUiState.Loading)
    val state: StateFlow<IssueCollaborationUiState> = _state.asStateFlow()

    private var currentIssueId: String? = null
    private var currentStageId: String? = null
    private var latestSnapshot: IssueCollaborationSnapshot? = null

    fun load(issueId: String?, stageId: String?) {
        currentIssueId = issueId
        currentStageId = stageId
        if (issueId.isNullOrBlank() || stageId.isNullOrBlank()) {
            _state.value = IssueCollaborationUiState.Failure("缺少稳定的议题或阶段标识。")
            return
        }
        val collaborationCoordinator = coordinator
        if (collaborationCoordinator == null) {
            _state.value = IssueCollaborationUiState.Failure(
                message = "官方 Skill 目录不可用，协作入口保持只读。",
                catalogUnavailable = true,
            )
            return
        }
        viewModelScope.launch {
            _state.value = IssueCollaborationUiState.Loading
            refresh(collaborationCoordinator, issueId, stageId)
        }
    }

    fun updateInput(value: String) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        savedStateHandle[KEY_INPUT] = value
        _state.value = content.copy(input = value, errorMessage = null)
    }

    fun openDirected() {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        if (!content.canOpenDirected) return
        val selected = content.roster.singleOrNull()?.skillId?.let(::setOf).orEmpty()
        persistSelectedSkills(selected)
        savedStateHandle[KEY_DIALOG] = CollaborationDialogMode.DIRECTED.name
        savedStateHandle[KEY_OPERATION_ID] = newOperationId()
        _state.value = content.copy(
            dialogMode = CollaborationDialogMode.DIRECTED,
            roster = content.roster.map { it.copy(selected = it.skillId in selected) },
            errorMessage = null,
        )
    }

    fun openCross() {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        if (!content.canOpenCross) return
        val selected = content.roster.take(2).mapTo(linkedSetOf()) { it.skillId }
        persistSelectedSkills(selected)
        savedStateHandle[KEY_DIALOG] = CollaborationDialogMode.CROSS.name
        savedStateHandle[KEY_OPERATION_ID] = newOperationId()
        _state.value = content.copy(
            dialogMode = CollaborationDialogMode.CROSS,
            roster = content.roster.map { it.copy(selected = it.skillId in selected) },
            errorMessage = null,
        )
    }

    fun dismissDialog() {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        savedStateHandle.remove<String>(KEY_DIALOG)
        savedStateHandle.remove<String>(KEY_OPERATION_ID)
        persistSelectedSkills(emptySet())
        _state.value = content.copy(
            dialogMode = null,
            roster = content.roster.map { it.copy(selected = false) },
            errorMessage = null,
        )
    }

    fun toggleParticipant(skillId: String) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        val mode = content.dialogMode ?: return
        if (content.operationInProgress || content.roster.none { it.skillId == skillId }) return
        val selected = content.selectedParticipants.mapTo(linkedSetOf()) { it.skillId }
        when (mode) {
            CollaborationDialogMode.DIRECTED -> {
                selected.clear()
                selected += skillId
            }
            CollaborationDialogMode.CROSS -> {
                if (!selected.add(skillId)) selected.remove(skillId)
            }
        }
        persistSelectedSkills(selected)
        _state.value = content.copy(
            roster = content.roster.map { it.copy(selected = it.skillId in selected) },
            errorMessage = null,
        )
    }

    fun toggleMessage(messageId: Long) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        if (content.operationInProgress || content.messages.none { it.messageId == messageId }) return
        val selected = content.selectedMessageIds.toMutableSet()
        if (!selected.add(messageId)) selected.remove(messageId)
        savedStateHandle[KEY_MESSAGE_IDS] = ArrayList(selected.sorted())
        _state.value = content.copy(
            messages = content.messages.map { it.copy(selected = it.messageId in selected) },
            errorMessage = null,
        )
    }

    fun confirmDirected(preparedContext: PreparedExecutionContext?) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        if (!content.canConfirmDirected) return
        val participant = content.selectedParticipants.single()
        runOperation(clearDraftOnSuccess = true) { collaborationCoordinator ->
            collaborationCoordinator.startDirected(
                DirectedResponseRequest(
                    operationId = requireOperationId(),
                    issueId = content.issueId,
                    stageId = content.stageId,
                    selectedSkillId = participant.skillId,
                    question = content.input,
                    roundIndex = nextRoundIndex(),
                    userConfirmedAt = System.currentTimeMillis(),
                    context = contextSelection(content, preparedContext),
                    budget = ExecutionRuntimeBudgetConfig(maxApiCalls = 1),
                ),
            )
        }
    }

    fun confirmCross(preparedContext: PreparedExecutionContext?) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        if (!content.canConfirmCross) return
        val selected = content.selectedParticipants.map { it.skillId }
        runOperation(clearDraftOnSuccess = true) { collaborationCoordinator ->
            collaborationCoordinator.startCrossDiscussion(
                CrossDiscussionRequest(
                    operationId = requireOperationId(),
                    issueId = content.issueId,
                    stageId = content.stageId,
                    selectedSkillIds = selected,
                    focus = content.input,
                    roundIndex = nextRoundIndex(),
                    userConfirmedAt = System.currentTimeMillis(),
                    context = contextSelection(content, preparedContext),
                    budget = ExecutionRuntimeBudgetConfig(maxApiCalls = selected.size + 1),
                    autoStartSynthesisOnFullSuccess = true,
                ),
            )
        }
    }

    fun synthesize(sessionId: String, preparedContext: PreparedExecutionContext?) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        val session = content.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        if (!session.canSynthesize || content.operationInProgress) return
        runOperation(clearDraftOnSuccess = false) { collaborationCoordinator ->
            collaborationCoordinator.startSynthesis(
                CrossDiscussionSynthesisRequest(
                    operationId = newOperationId(),
                    issueId = content.issueId,
                    stageId = content.stageId,
                    sessionId = session.sessionId,
                    focus = session.focus,
                    roundIndex = nextRoundIndex(),
                    userConfirmedAt = System.currentTimeMillis(),
                    userAcceptedPartial = session.status == CrossDiscussionStatus.PARTIAL_SUCCESS,
                    context = contextSelection(content, preparedContext),
                ),
            )
        }
    }

    fun retryFailed(sessionId: String) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        val session = content.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        if (!session.canRetryFailed || content.operationInProgress) return
        retryRun(session.responseRunId, session.focus)
    }

    fun retrySynthesis(sessionId: String) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        val session = content.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        val synthesisRunId = session.synthesisRunId ?: return
        if (!session.canRetrySynthesis || content.operationInProgress) return
        retryRun(synthesisRunId, session.focus)
    }

    fun stop(sessionId: String) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        val session = content.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        val runId = if (session.status == CrossDiscussionStatus.SYNTHESIZING) {
            session.synthesisRunId
        } else {
            session.responseRunId
        } ?: return
        runOperation(clearDraftOnSuccess = false) { collaborationCoordinator ->
            collaborationCoordinator.stop(runId)
        }
    }

    private fun retryRun(previousRunId: String, input: String) {
        runOperation(clearDraftOnSuccess = false) { collaborationCoordinator ->
            collaborationCoordinator.retry(
                CollaborationRetryRequest(
                    operationId = newOperationId(),
                    previousRunId = previousRunId,
                    currentUserInput = input,
                    roundIndex = nextRoundIndex(),
                    userConfirmedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun runOperation(
        clearDraftOnSuccess: Boolean,
        operation: suspend (IssueCollaborationCoordinator) -> Unit,
    ) {
        val content = _state.value as? IssueCollaborationUiState.Content ?: return
        val collaborationCoordinator = coordinator ?: return
        if (content.operationInProgress) return
        viewModelScope.launch {
            _state.value = content.copy(operationInProgress = true, errorMessage = null)
            try {
                operation(collaborationCoordinator)
                if (clearDraftOnSuccess) clearDraft()
                refresh(collaborationCoordinator, content.issueId, content.stageId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: CollaborationRepositoryException) {
                _state.value = content.copy(
                    operationInProgress = false,
                    errorMessage = repositoryMessage(error),
                )
            } catch (error: CollaborationStateException) {
                _state.value = content.copy(
                    operationInProgress = false,
                    errorMessage = stateMessage(error.code),
                )
            } catch (error: IllegalArgumentException) {
                _state.value = content.copy(
                    operationInProgress = false,
                    errorMessage = "当前协作配置无效，请重新选择。",
                )
            } catch (error: IllegalStateException) {
                _state.value = content.copy(
                    operationInProgress = false,
                    errorMessage = "协作状态已变化，请刷新后重试。",
                )
            }
        }
    }

    private suspend fun refresh(
        collaborationCoordinator: IssueCollaborationCoordinator,
        issueId: String,
        stageId: String,
    ) {
        try {
            val snapshot = collaborationCoordinator.recover(issueId, stageId)
            latestSnapshot = snapshot
            _state.value = mapContent(snapshot)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CollaborationRepositoryException) {
            _state.value = IssueCollaborationUiState.Failure(
                message = repositoryMessage(error),
                storageFailure = true,
            )
        } catch (error: IllegalStateException) {
            _state.value = IssueCollaborationUiState.Failure(
                message = "协作状态无法恢复，请重新打开工作区。",
            )
        }
    }

    private fun mapContent(snapshot: IssueCollaborationSnapshot): IssueCollaborationUiState.Content {
        val input = savedStateHandle.get<String>(KEY_INPUT).orEmpty()
        val dialog = savedStateHandle.get<String>(KEY_DIALOG)
            ?.let { stored -> CollaborationDialogMode.entries.firstOrNull { it.name == stored } }
        val selectedSkills = savedStateHandle.get<ArrayList<String>>(KEY_SKILL_IDS)
            .orEmpty().toSet()
        val selectedMessages = savedStateHandle.get<ArrayList<Long>>(KEY_MESSAGE_IDS)
            .orEmpty().toSet()
        val stageId = currentStageId ?: error("stage_missing")
        val messagesById = snapshot.issue.core.messages.associateBy { it.id }
        val latestRunsByDiscussion = snapshot.issue.core.runs
            .filter { it.discussionId != null }
            .groupBy { it.discussionId }
        val sessions = snapshot.collaboration.discussions.map { session ->
            val runs = latestRunsByDiscussion[session.id].orEmpty()
            val latestResponse = runs
                .filter { it.runKind == ExecutionRunKind.CROSS_DISCUSSION_RESPONSE }
                .maxWithOrNull(compareBy({ it.createdAt }, { it.id }))
            val latestSynthesis = runs
                .filter { it.runKind == ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS }
                .maxWithOrNull(compareBy({ it.createdAt }, { it.id }))
            CrossDiscussionSessionUi(
                sessionId = session.id,
                status = session.status,
                focus = messagesById[session.triggerMessageId]?.text.orEmpty(),
                responseRunId = latestResponse?.id ?: session.responseRunId,
                synthesisRunId = latestSynthesis?.id ?: session.synthesisRunId,
                integratorSkillId = session.integratorSkillId,
                successfulSkillIds = decodeIds(session.successfulParticipantIdsJson),
                failedSkillIds = decodeIds(session.failedParticipantIdsJson),
            )
        }.sortedByDescending { session ->
            snapshot.collaboration.discussions.first { it.id == session.sessionId }.updatedAt
        }
        return IssueCollaborationUiState.Content(
            issueId = snapshot.issue.core.issue.id,
            stageId = stageId,
            input = input,
            roster = snapshot.roster?.participants.orEmpty().map { participant ->
                CollaborationParticipantUi(
                    skillId = participant.sourceId,
                    displayName = participant.displayName,
                    avatar = participant.avatar,
                    responsibility = participant.defaultResponsibility,
                    position = participant.position,
                    selected = participant.sourceId in selectedSkills,
                )
            },
            messages = snapshot.issue.core.messages
                .asSequence()
                .filter { it.stageId == stageId && !it.isPending && it.text.isNotBlank() }
                .sortedWith(compareByDescending<com.elio.jianyu.data.Message> { it.timestamp }.thenByDescending { it.id })
                .map { message ->
                    CollaborationMessageUi(
                        messageId = message.id,
                        senderName = message.senderName,
                        preview = message.text.replace('\n', ' ').take(MESSAGE_PREVIEW_LENGTH),
                        selected = message.id in selectedMessages,
                    )
                }
                .toList(),
            dialogMode = dialog,
            sessions = sessions,
        )
    }

    private fun contextSelection(
        content: IssueCollaborationUiState.Content,
        preparedContext: PreparedExecutionContext?,
    ): CollaborationContextSelection = CollaborationContextSelection(
        selectedMessageIds = content.selectedMessageIds,
        contributions = preparedContext?.preparation?.contributions.orEmpty(),
        usage = preparedContext?.usage ?: com.elio.jianyu.data.ContextUsageWriteSet(),
    )

    private fun nextRoundIndex(): Int = latestSnapshot?.issue?.core?.messages
        ?.maxOfOrNull { it.roundIndex }
        ?.plus(1)
        ?: 0

    private fun requireOperationId(): String = savedStateHandle.get<String>(KEY_OPERATION_ID)
        ?: newOperationId().also { savedStateHandle[KEY_OPERATION_ID] = it }

    private fun newOperationId(): String = UUID.randomUUID().toString()

    private fun persistSelectedSkills(ids: Set<String>) {
        savedStateHandle[KEY_SKILL_IDS] = ArrayList(ids.sorted())
    }

    private fun clearDraft() {
        savedStateHandle[KEY_INPUT] = ""
        savedStateHandle.remove<String>(KEY_DIALOG)
        savedStateHandle.remove<String>(KEY_OPERATION_ID)
        savedStateHandle[KEY_SKILL_IDS] = arrayListOf<String>()
        savedStateHandle[KEY_MESSAGE_IDS] = arrayListOf<Long>()
    }

    private fun repositoryMessage(error: CollaborationRepositoryException): String = when (
        error.error,
    ) {
        is com.elio.jianyu.data.RepositoryError.StorageFailure ->
            "本地存储暂时不可用，请刷新后重试。"
        is com.elio.jianyu.data.RepositoryError.IdempotencyConflict ->
            "相同确认操作已用于不同内容，请返回后重新确认。"
        is com.elio.jianyu.data.RepositoryError.NotFound ->
            "议题、阶段、消息或协作运行不存在。"
        is com.elio.jianyu.data.RepositoryError.InvalidState ->
            "协作状态已变化，请刷新后重试。"
        else -> "协作操作未通过数据约束，请检查选择。"
    }

    private fun stateMessage(code: String): String = when (code) {
        "no_roster" -> "当前阶段尚无正式 Skill 阵容。"
        "skill_not_executable" -> "所选 Skill 当前不可执行。"
        "integrator_not_executable",
        "integrator_not_eligible_for_synthesis" -> "会议行动助手当前不具备透明整合资格。"
        "context_too_large" -> "本次上下文超过 24,000 字符，请缩短后重试。"
        "partial_synthesis_confirmation_required" -> "需要明确确认仅整合当前成功内容。"
        else -> "协作配置或状态已变化，请重新确认。"
    }

    private fun decodeIds(value: String): List<String> = runCatching {
        Json.decodeFromString<List<String>>(value)
    }.getOrDefault(emptyList())

    companion object {
        private const val KEY_INPUT = "issue_collaboration_input"
        private const val KEY_DIALOG = "issue_collaboration_dialog"
        private const val KEY_OPERATION_ID = "issue_collaboration_operation_id"
        private const val KEY_SKILL_IDS = "issue_collaboration_skill_ids"
        private const val KEY_MESSAGE_IDS = "issue_collaboration_message_ids"
        private const val MESSAGE_PREVIEW_LENGTH = 120

        fun factory(
            coordinator: IssueCollaborationCoordinator?,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <VM : ViewModel> create(
                modelClass: Class<VM>,
                extras: CreationExtras,
            ): VM {
                require(modelClass.isAssignableFrom(IssueCollaborationViewModel::class.java))
                return IssueCollaborationViewModel(
                    coordinator = coordinator,
                    savedStateHandle = extras.createSavedStateHandle(),
                ) as VM
            }
        }
    }
}
