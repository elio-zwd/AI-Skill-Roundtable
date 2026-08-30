package com.elio.jianyu.ui.screens.dialog

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import com.elio.jianyu.execution.SearchMode
import com.elio.jianyu.viewmodel.RoundtableViewModel
import kotlinx.coroutines.launch

/**
 * 见域「对话」页面 Route 桥接层。
 *
 * 页面临时交互留在 Compose；会话、消息、Skill 角色阵容和生成状态全部来自真实 ViewModel。
 */
@Composable
fun DialogRoute(
    viewModel: RoundtableViewModel,
    onNavigateBottomTab: (Int) -> Unit = {},
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

    var localState by remember { mutableStateOf(initialUiState) }
    var showArchivedSessions by remember { mutableStateOf(false) }
    var renameTitle by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

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
                    localState = uiState.copy(activeOverlay = DialogOverlayType.NONE)
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
                    localState = uiState.copy(activeOverlay = DialogOverlayType.NONE)
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
                    val content = uiState.messages.firstOrNull { it.id == event.messageId }
                        ?.let { item ->
                            when (item) {
                                is DialogMessageItem.UserMessage -> item.text
                                is DialogMessageItem.SkillMessage -> item.text
                            }
                        }
                    if (content != null) {
                        clipboard.setText(AnnotatedString(content))
                        Toast.makeText(context, "已复制内容，可到资料页整理为成果。", Toast.LENGTH_SHORT).show()
                    }
                }
                is DialogEvent.ClickMessageMore -> Toast.makeText(
                    context,
                    "更多消息操作即将开放。",
                    Toast.LENGTH_SHORT,
                ).show()
                is DialogEvent.NavigateBottomTab -> onNavigateBottomTab(event.tabIndex)
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
                    exportSession(sessionId, "会话已整理为 Markdown 并复制，可到资料页保存为成果。")
                }
                DialogEvent.AddFileAttachment,
                DialogEvent.SelectMaterials,
                DialogEvent.ViewReferenceContent,
                -> Toast.makeText(context, "该能力尚未接入当前对话。", Toast.LENGTH_SHORT).show()
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
    state.copy(
        inputText = "",
        targetRole = null,
        isMultiRoleAnswer = false,
    )
