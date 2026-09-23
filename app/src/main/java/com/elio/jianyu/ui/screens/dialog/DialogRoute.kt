package com.elio.jianyu.ui.screens.dialog

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.execution.SearchMode
import com.elio.jianyu.ui.settings.AppPreferences
import com.elio.jianyu.viewmodel.RoundtableViewModel
import com.elio.jianyu.viewmodel.ConversationContextSelection
import kotlinx.coroutines.launch

/**
 * 见域「对话」页面 Route 桥接层。
 *
 * 页面临时交互留在 Compose；会话、消息、Skill 角色阵容和生成状态全部来自真实 ViewModel。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogRoute(
    viewModel: RoundtableViewModel,
    modifier: Modifier = Modifier,
    initialUiState: DialogUiState = DialogUiState(),
) {
    val sessions by viewModel.allSessions.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val characters by viewModel.allCharacters.collectAsState()
    val participantIds by viewModel.currentParticipantIds.collectAsState()
    val archivedSessionIds by viewModel.archivedSessionIds.collectAsState()
    val isGenerating by viewModel.isRoundtableRunning.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val thinkingIntensity by viewModel.thinkingIntensity.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val appPreferences by AppPreferences.state.collectAsState()

    var localState by remember { mutableStateOf(initialUiState) }
    var showArchivedSessions by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf<String?>(null) }
    var contextConfirmation by remember { mutableStateOf<DialogContextState?>(null) }
    var showReferenceDialog by remember { mutableStateOf(false) }
    var messageActionId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val attachmentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = viewModel.attachTextMaterial(uri)
                Toast.makeText(context, materialAttachMessage(result), Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.ensureConversationReady()
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val uiState = mapDialogUiState(
        localState = localState,
        sessions = sessions,
        currentSession = currentSession,
        messages = messages,
        characters = characters,
        participantIds = participantIds,
        archivedSessionIds = archivedSessionIds,
        showArchivedSessions = showArchivedSessions,
        isGenerating = isGenerating,
        searchEnabled = searchMode != SearchMode.OFF,
        thinkingIntensity = thinkingIntensity,
        showMessageTimestamps = appPreferences.showMessageTimestamps,
    )

    fun resolveSessionId(rawId: String): Long? =
        rawId.toLongOrNull() ?: currentSession?.id

    fun exportSession(sessionId: Long, successMessage: String) {
        scope.launch {
            val markdown = viewModel.exportConversation(sessionId)
            if (markdown.isBlank()) {
                Toast.makeText(context, "当前会话还没有可导出的内容。", Toast.LENGTH_SHORT).show()
            } else {
                clipboard.setText(AnnotatedString(markdown))
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    DialogScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                DialogEvent.SendMessage -> {
                    if (isGenerating) {
                        viewModel.cancelRoundtable()
                    } else {
                        val text = uiState.composerState.inputText.trim()
                        if (text.isNotEmpty()) {
                            val accepted = viewModel.askQuestion(
                                text,
                                uiState.composerState.targetRole?.id,
                            )
                            if (accepted) {
                                localState = uiState.copy(
                                    composerState = clearComposerAfterSubmission(uiState.composerState),
                                )
                            }
                        }
                    }
                }
                DialogEvent.CreateNewSession -> {
                    viewModel.createNewSession("新建对话")
                    showArchivedSessions = false
                    localState = uiState.copy(
                        activeOverlay = DialogOverlayType.NONE,
                        composerState = clearComposerReplySelection(uiState.composerState),
                    )
                }
                is DialogEvent.SelectSession -> {
                    event.sessionId.toLongOrNull()?.let { sessionId ->
                        if (sessionId in archivedSessionIds) {
                            viewModel.restoreSession(sessionId)
                            showArchivedSessions = false
                        } else {
                            viewModel.selectSession(sessionId)
                        }
                    }
                    localState = uiState.copy(
                        activeOverlay = DialogOverlayType.NONE,
                        composerState = clearComposerReplySelection(uiState.composerState),
                    )
                }
                is DialogEvent.AddSkillToSession -> {
                    viewModel.addSkillRoleToCurrentSession(event.skillId)
                    localState = uiState.copy(activeOverlay = DialogOverlayType.NONE)
                }
                is DialogEvent.RemoveSkillFromSession -> {
                    viewModel.removeSkillRoleFromCurrentSession(event.skillId)
                    localState = uiState.copy(
                        activeOverlay = DialogOverlayType.NONE,
                        selectedSkillDetail = null,
                        composerState = if (uiState.composerState.targetRole?.id == event.skillId) {
                            clearComposerReplySelection(uiState.composerState)
                        } else {
                            uiState.composerState
                        },
                    )
                }
                is DialogEvent.LetSkillAnswerCurrent -> {
                    viewModel.letSkillRoleAnswerCurrent(event.skillId)
                    localState = uiState.copy(activeOverlay = DialogOverlayType.NONE)
                }
                DialogEvent.ToggleSearchMode -> viewModel.setSearchMode(
                    if (searchMode == SearchMode.OFF) SearchMode.AUTO else SearchMode.OFF,
                )
                is DialogEvent.SelectThinkingIntensity -> viewModel.setThinkingIntensity(event.intensity)
                DialogEvent.TriggerCrossDiscussion -> viewModel.triggerCrossDiscussion()
                DialogEvent.ContinueDeeper -> viewModel.askQuestion(
                    "请基于当前对话继续深入，补充尚未展开的关键判断、适用条件和下一步。",
                )
                is DialogEvent.CopyMessage -> {
                    clipboard.setText(AnnotatedString(event.content))
                    Toast.makeText(context, "已复制消息。", Toast.LENGTH_SHORT).show()
                }
                is DialogEvent.SaveMessageAsArtifact -> {
                    scope.launch {
                        val result = viewModel.saveMessageAsArtifact(event.messageId.toLongOrNull() ?: 0L)
                        Toast.makeText(context, artifactSaveMessage(result), Toast.LENGTH_SHORT).show()
                    }
                }
                is DialogEvent.ClickMessageMore -> messageActionId = event.messageId
                is DialogEvent.RenameSession -> {
                    if (resolveSessionId(event.sessionId) != null) {
                        renameTitle = currentSession?.title.orEmpty()
                    }
                }
                is DialogEvent.ExportSession -> resolveSessionId(event.sessionId)?.let { sessionId ->
                    exportSession(sessionId, "会话已整理为 Markdown 并复制。")
                }
                is DialogEvent.ArchiveSession -> {
                    resolveSessionId(event.sessionId)?.let(viewModel::archiveSession)
                    localState = uiState.copy(isMoreMenuOpen = false)
                }
                is DialogEvent.DeleteSession -> {
                    resolveSessionId(event.sessionId)?.let(viewModel::deleteSession)
                    localState = uiState.copy(isMoreMenuOpen = false)
                }
                DialogEvent.OpenArchivedSessions -> {
                    showArchivedSessions = !showArchivedSessions
                    localState = uiState.copy(activeOverlay = DialogOverlayType.DRAWER_SESSIONS)
                }
                DialogEvent.SaveOrOrganizeArtifacts -> currentSession?.id?.let { sessionId ->
                    scope.launch {
                        val result = viewModel.saveConversationAsArtifact(sessionId)
                        Toast.makeText(context, artifactSaveMessage(result), Toast.LENGTH_SHORT).show()
                    }
                }
                DialogEvent.AddFileAttachment -> attachmentLauncher.launch(
                    arrayOf(
                        "text/*",
                        "application/json",
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ),
                )
                DialogEvent.SelectMaterials -> {
                    scope.launch {
                        val (materials, personalContexts) = viewModel.loadAvailableConversationContext()
                        val selected = viewModel.currentActiveConversationContextSelections()
            .ifEmpty { viewModel.currentConversationContextSelections() }
                            .associateBy { it.sourceType to it.sourceId }
                        val candidates = materials.map { material ->
                            val key = ContextSourceType.MATERIAL to material.id
                            val previous = selected[key]?.takeIf {
                                it.expectedSourceHash == material.contentHash &&
                                    it.expectedSourceUpdatedAt == material.updatedAt
                            }
                            DialogContextCandidate(
                                sourceType = ContextSourceType.MATERIAL,
                                sourceId = material.id,
                                title = material.title,
                                content = previous?.content ?: material.content,
                                expectedSourceHash = material.contentHash,
                                expectedSourceUpdatedAt = material.updatedAt,
                                sensitive = material.sensitive,
                                selected = previous != null,
                                networkAllowed = previous?.networkAllowed == true,
                                sensitiveConfirmed = previous?.sensitiveConfirmed == true,
                            )
                        } + personalContexts.map { personal ->
                            val key = ContextSourceType.PERSONAL_CONTEXT to personal.id
                            val previous = selected[key]?.takeIf {
                                it.expectedSourceHash == personal.contentHash &&
                                    it.expectedSourceUpdatedAt == personal.updatedAt
                            }
                            DialogContextCandidate(
                                sourceType = ContextSourceType.PERSONAL_CONTEXT,
                                sourceId = personal.id,
                                title = personal.title,
                                content = previous?.content ?: personal.content,
                                expectedSourceHash = personal.contentHash,
                                expectedSourceUpdatedAt = personal.updatedAt,
                                sensitive = personal.sensitive,
                                selected = previous != null,
                                networkAllowed = previous?.networkAllowed == true,
                                sensitiveConfirmed = previous?.sensitiveConfirmed == true,
                            )
                        }
                        contextConfirmation = DialogContextState(candidates)
                    }
                }
                DialogEvent.ViewReferenceContent -> showReferenceDialog = true
                else -> localState = reduceDialogLocalState(uiState, event)
            }
        },
        modifier = modifier,
    )

    renameTitle?.let { currentTitle ->
        AlertDialog(
            onDismissRequest = { renameTitle = null },
            title = { Text("重命名会话") },
            text = {
                TextField(
                    value = currentTitle,
                    onValueChange = { renameTitle = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = currentTitle.isNotBlank(),
                    onClick = {
                        currentSession?.id?.let { sessionId ->
                            viewModel.renameSession(sessionId, currentTitle.trim())
                        }
                        renameTitle = null
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTitle = null }) {
                    Text("取消")
                }
            },
        )
    }

    contextConfirmation?.let { state ->
        DialogContextSelectionDialog(
            state = state,
            showSensitiveReminder = appPreferences.confirmSensitiveContext,
            onDismiss = { contextConfirmation = null },
            onChange = { candidate ->
                contextConfirmation = state.copy(candidates = state.candidates.map {
                    if (it.sourceType == candidate.sourceType && it.sourceId == candidate.sourceId) candidate else it
                })
            },
            onConfirm = {
                val selections = state.selectedItems.map { candidate ->
                    ConversationContextSelection(
                        sourceType = candidate.sourceType,
                        sourceId = candidate.sourceId,
                        title = candidate.title,
                        content = candidate.content,
                        expectedSourceHash = candidate.expectedSourceHash,
                        expectedSourceUpdatedAt = candidate.expectedSourceUpdatedAt,
                        networkAllowed = candidate.networkAllowed,
                        sensitive = candidate.sensitive,
                        sensitiveConfirmed = candidate.sensitiveConfirmed,
                    )
                }
                if (viewModel.confirmConversationContext(selections)) {
                    contextConfirmation = null
                    Toast.makeText(context, "已保存下一次请求的参考内容。", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    if (showReferenceDialog) {
        val selected = viewModel.currentConversationContextSelections()
        AlertDialog(
            onDismissRequest = { showReferenceDialog = false },
            title = { Text("本次参考内容") },
            text = {
                Text(
                    if (selected.isEmpty()) "当前没有选择资料或个人背景。"
                    else selected.joinToString("\n\n") { "${it.title}\n${it.content.take(300)}" },
                )
            },
            confirmButton = { TextButton(onClick = { showReferenceDialog = false }) { Text("关闭") } },
        )
    }

    messageActionId?.let { messageId ->
        val item = uiState.messages.firstOrNull { it.id == messageId }
        val content = when (item) {
            is DialogMessageItem.UserMessage -> item.text
            is DialogMessageItem.SkillMessage -> item.text
            null -> null
        }
        AlertDialog(
            onDismissRequest = { messageActionId = null },
            title = { Text("消息操作") },
            text = { Text("可以复制消息，或确认保存为正式成果。") },
            confirmButton = {
                TextButton(onClick = {
                    if (content != null) clipboard.setText(AnnotatedString(content))
                    messageActionId = null
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = viewModel.saveMessageAsArtifact(messageId.toLongOrNull() ?: 0L)
                        Toast.makeText(context, artifactSaveMessage(result), Toast.LENGTH_SHORT).show()
                    }
                    messageActionId = null
                }) { Text("保存为成果") }
            },
        )
    }
}

private data class DialogContextCandidate(
    val sourceType: ContextSourceType,
    val sourceId: String,
    val title: String,
    val content: String,
    val expectedSourceHash: String,
    val expectedSourceUpdatedAt: Long,
    val sensitive: Boolean,
    val selected: Boolean = false,
    val networkAllowed: Boolean = false,
    val sensitiveConfirmed: Boolean = false,
)

private data class DialogContextState(
    val candidates: List<DialogContextCandidate>,
) {
    val selectedItems: List<DialogContextCandidate>
        get() = candidates.filter { it.selected }
}

@Composable
private fun DialogContextSelectionDialog(
    state: DialogContextState,
    showSensitiveReminder: Boolean,
    onDismiss: () -> Unit,
    onChange: (DialogContextCandidate) -> Unit,
    onConfirm: () -> Unit,
) {
    val hasMissingPermission = state.selectedItems.any {
        !it.networkAllowed ||
            (it.sensitive && !it.sensitiveConfirmed) ||
            it.content.isBlank()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择本次参考内容") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "资料和个人背景默认不发送。只有勾选、允许本次发送并确认后，才会进入模型请求。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.candidates.isEmpty()) {
                    Text("当前没有可选的活跃资料或个人背景。")
                }
                state.candidates.forEach { candidate ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row {
                            Checkbox(
                                checked = candidate.selected,
                                onCheckedChange = {
                                    onChange(
                                        candidate.copy(
                                            selected = !candidate.selected,
                                            networkAllowed = if (candidate.selected) false else candidate.networkAllowed,
                                            sensitiveConfirmed = if (candidate.selected) {
                                                false
                                            } else {
                                                candidate.sensitiveConfirmed
                                            },
                                        )
                                    )
                                },
                            )
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(candidate.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (candidate.sourceType == ContextSourceType.MATERIAL) "资料" else "个人背景",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        if (candidate.selected) {
                            OutlinedTextField(
                                value = candidate.content,
                                onValueChange = { onChange(candidate.copy(content = it)) },
                                label = { Text("本次发送的正文或摘录") },
                                minLines = 3,
                            )
                            Row {
                                Checkbox(
                                    checked = candidate.networkAllowed,
                                    onCheckedChange = { onChange(candidate.copy(networkAllowed = it)) },
                                )
                                Text("允许本次发送给模型服务", modifier = Modifier.padding(top = 12.dp))
                            }
                            if (candidate.sensitive) {
                                if (showSensitiveReminder) {
                                    Text(
                                        "这项内容标记为敏感；请确认本次确实需要发送。",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                Row {
                                    Checkbox(
                                        checked = candidate.sensitiveConfirmed,
                                        onCheckedChange = { onChange(candidate.copy(sensitiveConfirmed = it)) },
                                    )
                                    Text(
                                        "我已查看并确认发送敏感内容",
                                        modifier = Modifier.padding(top = 12.dp),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        } else {
                            Text(
                                if (candidate.sensitive) {
                                    "敏感内容已隐藏；选中后查看并确认。"
                                } else {
                                    candidate.content.lineSequence().firstOrNull().orEmpty().take(120)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !hasMissingPermission) { Text("确认选择") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun artifactSaveMessage(result: com.elio.jianyu.data.RepositoryResult<*>) = when (result) {
    is com.elio.jianyu.data.RepositoryResult.Success -> if (result.idempotent) "该内容已保存为成果。" else "已保存为正式成果。"
    is com.elio.jianyu.data.RepositoryResult.Failure -> "成果保存失败，请稍后重试。"
}

private fun materialAttachMessage(result: com.elio.jianyu.data.RepositoryResult<*>) = when (result) {
    is com.elio.jianyu.data.RepositoryResult.Success -> "资料已加入当前对话。"
    is com.elio.jianyu.data.RepositoryResult.Failure ->
        "资料读取或保存失败；当前支持文本、Markdown、CSV、JSON、HTML 和 DOCX。"
}

/** 仅处理页面局部交互，不伪造或修改真实业务数据。 */
internal fun reduceDialogLocalState(
    currentState: DialogUiState,
    event: DialogEvent,
): DialogUiState = when (event) {
    is DialogEvent.SetDrawerOpen -> currentState.copy(
        activeOverlay = if (event.open) DialogOverlayType.DRAWER_SESSIONS else DialogOverlayType.NONE,
    )
    is DialogEvent.SetOverlay -> currentState.copy(activeOverlay = event.overlay)
    DialogEvent.DismissOverlay -> currentState.copy(activeOverlay = DialogOverlayType.NONE)
    DialogEvent.ToggleMoreMenu -> currentState.copy(isMoreMenuOpen = !currentState.isMoreMenuOpen)
    DialogEvent.DismissMoreMenu -> currentState.copy(isMoreMenuOpen = false)
    is DialogEvent.InputTextChanged -> currentState.copy(
        composerState = currentState.composerState.copy(inputText = event.text),
    )
    DialogEvent.ClickPlusButton -> currentState.copy(
        activeOverlay = DialogOverlayType.SHEET_COMPOSER_PLUS_MENU,
    )
    DialogEvent.ClickAtButton -> currentState.copy(
        activeOverlay = DialogOverlayType.SHEET_TARGET_ROLE_SELECT,
    )
    is DialogEvent.SelectReplyTargetRole -> currentState.copy(
        composerState = currentState.composerState.copy(
            targetRole = event.role,
            isMultiRoleAnswer = false,
        ),
        activeOverlay = DialogOverlayType.NONE,
    )
    DialogEvent.SelectMultiRoleAnswer -> currentState.copy(
        composerState = currentState.composerState.copy(
            targetRole = null,
            isMultiRoleAnswer = true,
        ),
        activeOverlay = DialogOverlayType.NONE,
    )
    DialogEvent.ClearReplyTargetRole -> currentState.copy(
        composerState = currentState.composerState.copy(
            targetRole = null,
            isMultiRoleAnswer = false,
        ),
    )
    is DialogEvent.ClickSkillCard -> {
        val role = currentState.activeRoles.firstOrNull { it.id == event.skillId }
        if (role == null) {
            currentState
        } else {
            currentState.copy(
                activeOverlay = DialogOverlayType.SHEET_SKILL_DETAIL,
                selectedSkillDetail = SkillRoleDetailUiModel(
                    role = role,
                    isInCurrentSession = true,
                    fullDescription = role.shortDescription,
                    capabilities = emptyList(),
                ),
            )
        }
    }
    DialogEvent.ClickAddSkillCard -> currentState.copy(
        activeOverlay = DialogOverlayType.SHEET_ADD_SKILL,
    )
    is DialogEvent.SearchSessions -> currentState.copy(
        drawerData = currentState.drawerData.copy(searchQuery = event.query),
    )
    is DialogEvent.SearchSkillsToAdd -> currentState.copy(
        addSkillCatalog = currentState.addSkillCatalog.copy(searchQuery = event.query),
    )
    else -> currentState
}

/** @ 点名与多角色选择只作用于当前一次请求，发送后必须复位。 */
internal fun clearComposerAfterSubmission(state: DialogComposerState): DialogComposerState =
    clearComposerReplySelection(state).copy(inputText = "")

/** 切换会话或移除被点名角色时保留草稿，只清除本次回复范围。 */
internal fun clearComposerReplySelection(state: DialogComposerState): DialogComposerState =
    state.copy(
        targetRole = null,
        isMultiRoleAnswer = false,
    )
