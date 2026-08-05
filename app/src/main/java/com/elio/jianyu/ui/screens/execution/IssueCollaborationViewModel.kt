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
import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.PreparedExecutionContext
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** 协作草稿只保存在 SavedStateHandle；已确认事实始终以 Room 为准。 */
class IssueCollaborationViewModel internal constructor(
    private val coordinator: IssueCollaborationCoordinator?,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow<IssueCollaborationUiState>(IssueCollaborationUiState.Loading)
    val state: StateFlow<IssueCollaborationUiState> = _state.asStateFlow()

    private var issueId: String? = null
    private var stageId: String? = null
    private var snapshot: IssueCollaborationSnapshot? = null

    fun load(issueId: String?, stageId: String?) {
        this.issueId = issueId
        this.stageId = stageId
        if (issueId.isNullOrBlank() || stageId.isNullOrBlank()) {
            _state.value = IssueCollaborationUiState.Failure("缺少稳定的议题或阶段标识。")
            return
        }
        val target = coordinator
        if (target == null) {
            _state.value = IssueCollaborationUiState.Failure(
                message = "官方 Skill 目录不可用，协作入口保持只读。",
                catalogUnavailable = true,
            )
            return
        }
        viewModelScope.launch {
            _state.value = IssueCollaborationUiState.Loading
            refresh(target, issueId, stageId)
        }
    }

    fun updateInput(value: String) = updateContent { current ->
        savedStateHandle[KEY_INPUT] = value
        current.copy(input = value, errorMessage = null)
    }

    fun openDirected() = updateContent { current ->
        if (!current.canOpenDirected) return@updateContent current
        val selected = current.roster.singleOrNull()?.skillId?.let(::setOf).orEmpty()
        persistSelection(CollaborationDialogMode.DIRECTED, selected)
        current.copy(
            dialogMode = CollaborationDialogMode.DIRECTED,
            roster = current.roster.markSelected(selected),
            errorMessage = null,
        )
    }

    fun openCross() = updateContent { current ->
        if (!current.canOpenCross) return@updateContent current
        val selected = current.roster.take(2).mapTo(linkedSetOf()) { it.skillId }
        persistSelection(CollaborationDialogMode.CROSS, selected)
        current.copy(
            dialogMode = CollaborationDialogMode.CROSS,
            roster = current.roster.markSelected(selected),
            errorMessage = null,
        )
    }

    fun dismissDialog() = updateContent { current ->
        savedStateHandle.remove<String>(KEY_DIALOG)
        savedStateHandle.remove<String>(KEY_OPERATION_ID)
        saveSkillIds(emptySet())
        current.copy(
            dialogMode = null,
            roster = current.roster.markSelected(emptySet()),
            errorMessage = null,
        )
    }

    fun toggleParticipant(skillId: String) = updateContent { current ->
        val mode = current.dialogMode ?: return@updateContent current
        if (current.operationInProgress || current.roster.none { it.skillId == skillId }) {
            return@updateContent current
        }
        val selected = current.selectedParticipants.mapTo(linkedSetOf()) { it.skillId }
        if (mode == CollaborationDialogMode.DIRECTED) {
            selected.clear()
            selected += skillId
        } else if (!selected.add(skillId)) {
            selected.remove(skillId)
        }
        saveSkillIds(selected)
        current.copy(roster = current.roster.markSelected(selected), errorMessage = null)
    }

    fun toggleMessage(messageId: Long) = updateContent { current ->
        if (current.operationInProgress || current.messages.none { it.messageId == messageId }) {
            return@updateContent current
        }
        val selected = current.selectedMessageIds.toMutableSet()
        if (!selected.add(messageId)) selected.remove(messageId)
        savedStateHandle[KEY_MESSAGE_IDS] = ArrayList(selected.sorted())
        current.copy(
            messages = current.messages.map { it.copy(selected = it.messageId in selected) },
            errorMessage = null,
        )
    }

    fun confirmDirected(preparedContext: PreparedExecutionContext?) {
        val current = contentOrNull() ?: return
        val participant = current.selectedParticipants.singleOrNull() ?: return
        if (!current.canConfirmDirected) return
        runOperation(clearDraft = true) { target ->
            target.startDirected(
                DirectedResponseRequest(
                    operationId = operationId(),
                    issueId = current.issueId,
                    stageId = current.stageId,
                    selectedSkillId = participant.skillId,
                    question = current.input,
                    roundIndex = nextRoundIndex(),
                    userConfirmedAt = System.currentTimeMillis(),
                    context = contextSelection(current, preparedContext),
                    budget = ExecutionRuntimeBudgetConfig(maxApiCalls = 1),
                ),
            )
        }
    }

    fun confirmCross(preparedContext: PreparedExecutionContext?) {
        val current = contentOrNull() ?: return
        val selected = current.selectedParticipants.map { it.skillId }
        if (!current.canConfirmCross) return
        runOperation(clearDraft = true) { target ->
            target.startCrossDiscussion(
                CrossDiscussionRequest(
                    operationId = operationId(),
                    issueId = current.issueId,
                    stageId = current.stageId,
                    selectedSkillIds = selected,
                    focus = current.input,
                    roundIndex = nextRoundIndex(),
                    userConfirmedAt = System.currentTimeMillis(),
                    context = contextSelection(current, preparedContext),
                    budget = ExecutionRuntimeBudgetConfig(maxApiCalls = selected.size + 1),
                    autoStartSynthesisOnFullSuccess = true,
                ),
            )
        }
    }

    fun synthesize(sessionId: String, preparedContext: PreparedExecutionContext?) {
        val current = contentOrNull() ?: return
        val session = current.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        if (!session.canSynthesize || current.operationInProgress) return
        runOperation(clearDraft = false) { target ->
            target.startSynthesis(
                CrossDiscussionSynthesisRequest(
                    operationId = newOperationId(),
                    issueId = current.issueId,
                    stageId = current.stageId,
                    sessionId = session.sessionId,
                    focus = session.focus,
                    roundIndex = nextRoundIndex(),
                    userConfirmedAt = System.currentTimeMillis(),
                    userAcceptedPartial = session.status == CrossDiscussionStatus.PARTIAL_SUCCESS,
                    context = contextSelection(current, preparedContext),
                ),
            )
        }
    }

    fun retryFailed(sessionId: String) {
        val current = contentOrNull() ?: return
        val session = current.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        if (session.canRetryFailed) retryRun(session.responseRunId, session.focus)
    }

    fun retrySynthesis(sessionId: String) {
        val current = contentOrNull() ?: return
        val session = current.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        val runId = session.synthesisRunId ?: return
        if (session.canRetrySynthesis) retryRun(runId, session.focus)
    }

    fun stop(sessionId: String) {
        val current = contentOrNull() ?: return
        val session = current.sessions.singleOrNull { it.sessionId == sessionId } ?: return
        val runId = if (session.status == CrossDiscussionStatus.SYNTHESIZING) {
            session.synthesisRunId
        } else {
            session.responseRunId
        } ?: return
        runOperation(clearDraft = false) { it.stop(runId) }
    }

    private fun retryRun(previousRunId: String, input: String) {
        runOperation(clearDraft = false) { target ->
            target.retry(
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
        clearDraft: Boolean,
        action: suspend (IssueCollaborationCoordinator) -> Unit,
    ) {
        val current = contentOrNull() ?: return
        val target = coordinator ?: return
        if (current.operationInProgress) return
        viewModelScope.launch {
            _state.value = current.copy(operationInProgress = true, errorMessage = null)
            try {
                action(target)
                if (clearDraft) clearDraft()
                refresh(target, current.issueId, current.stageId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: CollaborationRepositoryException) {
                _state.value = current.copy(
                    operationInProgress = false,
                    errorMessage = repositoryMessage(error),
                )
            } catch (error: CollaborationStateException) {
                _state.value = current.copy(
                    operationInProgress = false,
                    errorMessage = stateMessage(error.code),
                )
            } catch (_: IllegalArgumentException) {
                _state.value = current.copy(
                    operationInProgress = false,
                    errorMessage = "当前协作配置无效，请重新选择。",
                )
            } catch (_: IllegalStateException) {
                _state.value = current.copy(
                    operationInProgress = false,
                    errorMessage = "协作状态已变化，请刷新后重试。",
                )
            }
        }
    }

    private suspend fun refresh(
        target: IssueCollaborationCoordinator,
        issueId: String,
        stageId: String,
    ) {
        try {
            val recovered = target.recover(issueId, stageId)
            snapshot = recovered
            _state.value = mapContent(recovered)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CollaborationRepositoryException) {
            _state.value = IssueCollaborationUiState.Failure(
                message = repositoryMessage(error),
                storageFailure = true,
            )
        } catch (_: IllegalStateException) {
            _state.value = IssueCollaborationUiState.Failure(
                "协作状态无法恢复，请重新打开工作区。",
            )
        }
    }

    private fun mapContent(recovered: IssueCollaborationSnapshot): IssueCollaborationUiState.Content {
        val stableStageId = stageId ?: error("stage_missing")
        val selectedSkills = savedStateHandle.get<ArrayList<String>>(KEY_SKILL_IDS).orEmpty().toSet()
        val selectedMessages = savedStateHandle.get<ArrayList<Long>>(KEY_MESSAGE_IDS).orEmpty().toSet()
        val messagesById = recovered.issue.core.messages.associateBy(Message::id)
        val runsByDiscussion = recovered.issue.core.runs
            .filter { it.discussionId != null }
            .groupBy { it.discussionId }
        val sessions = recovered.collaboration.discussions.map { session ->
            val runs = runsByDiscussion[session.id].orEmpty()
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
        }.sortedByDescending { mapped ->
            recovered.collaboration.discussions.first { it.id == mapped.sessionId }.updatedAt
        }
        return IssueCollaborationUiState.Content(
            issueId = recovered.issue.core.issue.id,
            stageId = stableStageId,
            input = savedStateHandle.get<String>(KEY_INPUT).orEmpty(),
            roster = recovered.roster?.participants.orEmpty().map { participant ->
                CollaborationParticipantUi(
                    skillId = participant.sourceId,
                    displayName = participant.displayName,
                    avatar = participant.avatar,
                    responsibility = participant.defaultResponsibility,
                    position = participant.position,
                    selected = participant.sourceId in selectedSkills,
                )
            },
            messages = recovered.issue.core.messages
                .asSequence()
                .filter { it.stageId == stableStageId && !it.isPending && it.text.isNotBlank() }
                .sortedWith(compareByDescending<Message> { it.timestamp }.thenByDescending { it.id })
                .map { message ->
                    CollaborationMessageUi(
                        messageId = message.id,
                        senderName = message.senderName,
                        preview = message.text.replace('\n', ' ').take(MESSAGE_PREVIEW_LENGTH),
                        selected = message.id in selectedMessages,
                    )
                }
                .toList(),
            dialogMode = savedStateHandle.get<String>(KEY_DIALOG)?.let { stored ->
                CollaborationDialogMode.entries.firstOrNull { it.name == stored }
            },
            sessions = sessions,
        )
    }

    private fun contextSelection(
        current: IssueCollaborationUiState.Content,
        prepared: PreparedExecutionContext?,
    ) = CollaborationContextSelection(
        selectedMessageIds = current.selectedMessageIds,
        contributions = prepared?.preparation?.contributions.orEmpty(),
        usage = prepared?.usage ?: com.elio.jianyu.data.ContextUsageWriteSet(),
    )

    private fun nextRoundIndex(): Int = snapshot?.issue?.core?.messages
        ?.maxOfOrNull(Message::roundIndex)
        ?.plus(1)
        ?: 0

    private fun operationId(): String = savedStateHandle.get<String>(KEY_OPERATION_ID)
        ?: newOperationId().also { savedStateHandle[KEY_OPERATION_ID] = it }

    private fun persistSelection(mode: CollaborationDialogMode, selected: Set<String>) {
        savedStateHandle[KEY_DIALOG] = mode.name
        savedStateHandle[KEY_OPERATION_ID] = newOperationId()
        saveSkillIds(selected)
    }

    private fun saveSkillIds(selected: Set<String>) {
        savedStateHandle[KEY_SKILL_IDS] = ArrayList(selected.sorted())
    }

    private fun clearDraft() {
        savedStateHandle[KEY_INPUT] = ""
        savedStateHandle.remove<String>(KEY_DIALOG)
        savedStateHandle.remove<String>(KEY_OPERATION_ID)
        savedStateHandle[KEY_SKILL_IDS] = arrayListOf<String>()
        savedStateHandle[KEY_MESSAGE_IDS] = arrayListOf<Long>()
    }

    private fun contentOrNull() = _state.value as? IssueCollaborationUiState.Content

    private fun updateContent(
        transform: (IssueCollaborationUiState.Content) -> IssueCollaborationUiState.Content,
    ) {
        contentOrNull()?.let { _state.value = transform(it) }
    }

    private fun List<CollaborationParticipantUi>.markSelected(selected: Set<String>) =
        map { it.copy(selected = it.skillId in selected) }

    private fun newOperationId(): String = UUID.randomUUID().toString()

    private fun repositoryMessage(error: CollaborationRepositoryException): String =
        when (error.error) {
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

        fun factory(coordinator: IssueCollaborationCoordinator?): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
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
