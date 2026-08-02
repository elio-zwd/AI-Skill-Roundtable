package com.elio.jianyu.ui.screens.roundtable

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.elio.jianyu.telemetry.PrivacySafeLogger
import com.elio.jianyu.viewmodel.RoundtableViewModel
import kotlinx.coroutines.launch

@Composable
fun RoundtableRoute(
    viewModel: RoundtableViewModel,
    onOpenApiKeyConfig: () -> Unit,
    onOpenTelemetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions by viewModel.allSessions.collectAsState()
    val characters by viewModel.allCharacters.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isRoundtableRunning by viewModel.isRoundtableRunning.collectAsState()
    val typingCharacterIds by viewModel.typingCharacterIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val apiKeySummaries by viewModel.apiKeySummaries.collectAsState()
    val isAutoNextEnabled by viewModel.isAutoNextEnabled.collectAsState()
    val isSemanticRoutingEnabled by viewModel.isSemanticRoutingEnabled.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val roundActionState by viewModel.roundActionState.collectAsState()
    val retryableState by viewModel.retryableRoundtableState.collectAsState()
    val currentPlayingMessageId by viewModel.currentPlayingMessageId.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isDrawerVisible by remember { mutableStateOf(false) }
    var renameSession by remember { mutableStateOf<RenameSessionUiState?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val uiState = RoundtableUiState(
        sessions = sessions,
        currentSessionId = currentSessionId,
        currentSession = currentSession,
        messages = messages,
        characters = characters,
        isRoundtableRunning = isRoundtableRunning,
        typingCharacterIds = typingCharacterIds,
        hasApiKeys = apiKeySummaries.any { it.enabled },
        isAutoNextEnabled = isAutoNextEnabled,
        isSemanticRoutingEnabled = isSemanticRoutingEnabled,
        searchMode = searchMode,
        roundActionState = roundActionState,
        retryableSessionId = retryableState?.sessionId,
        retryableCharacterIds = retryableState?.characterIds.orEmpty(),
        currentPlayingMessageId = currentPlayingMessageId,
        errorMessage = errorMessage,
        inputText = inputText,
        isDrawerVisible = isDrawerVisible,
        renameSession = renameSession,
    )

    fun copyText(text: String, successMessage: String, errorLog: String) {
        try {
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            PrivacySafeLogger.e("RoundtableRoute", errorLog, error)
            Toast.makeText(context, "复制失败：剪贴板不可用", Toast.LENGTH_SHORT).show()
        }
    }

    val onEvent: (RoundtableEvent) -> Unit = eventHandler@{ event ->
        when (event) {
            RoundtableEvent.ToggleDrawer -> isDrawerVisible = !isDrawerVisible
            RoundtableEvent.DismissDrawer -> isDrawerVisible = false
            RoundtableEvent.CreateSession -> {
                viewModel.createNewSession("关于新概念的圆桌会议 #${sessions.size + 1}")
                isDrawerVisible = false
            }
            RoundtableEvent.CreateFirstSession -> {
                viewModel.createNewSession("关于新方向的圆桌脑暴")
            }
            is RoundtableEvent.SelectSession -> {
                viewModel.selectSession(event.sessionId)
                isDrawerVisible = false
            }
            is RoundtableEvent.DeleteSession -> viewModel.deleteSession(event.sessionId)
            is RoundtableEvent.RequestRename -> {
                renameSession = RenameSessionUiState(event.sessionId, event.title)
            }
            is RoundtableEvent.RenameTitleChanged -> {
                renameSession = renameSession?.copy(title = event.title)
            }
            RoundtableEvent.ConfirmRename -> {
                val pendingRename = renameSession
                if (pendingRename != null && pendingRename.title.isNotBlank()) {
                    viewModel.renameSession(pendingRename.sessionId, pendingRename.title)
                    renameSession = null
                }
            }
            RoundtableEvent.DismissRename -> renameSession = null
            is RoundtableEvent.AutoNextChanged -> viewModel.setAutoNextEnabled(event.enabled)
            is RoundtableEvent.SemanticRoutingChanged -> {
                viewModel.setSemanticRoutingEnabled(event.enabled)
            }
            is RoundtableEvent.SearchModeChanged -> viewModel.setSearchMode(event.mode)
            is RoundtableEvent.InputChanged -> inputText = event.text
            RoundtableEvent.SubmitOrStop -> {
                if (isRoundtableRunning) {
                    viewModel.cancelRoundtable()
                } else if (inputText.isNotBlank()) {
                    viewModel.askQuestion(inputText)
                    inputText = ""
                }
            }
            RoundtableEvent.ContinueRound -> viewModel.triggerNextCharacterManual()
            RoundtableEvent.RetryFailedCharacters -> viewModel.retryFailedCharacters()
            RoundtableEvent.DismissRetryableState -> viewModel.dismissRetryableState()
            RoundtableEvent.OpenApiKeyConfig -> onOpenApiKeyConfig()
            RoundtableEvent.OpenTelemetry -> {
                isDrawerVisible = false
                onOpenTelemetry()
            }
            RoundtableEvent.CopyConversationMarkdown -> {
                val sessionId = currentSession?.id ?: return@eventHandler
                coroutineScope.launch {
                    copyText(
                        text = viewModel.exportConversation(sessionId),
                        successMessage = "已复制至剪贴板",
                        errorLog = "复制对话 Markdown 失败",
                    )
                }
            }
            RoundtableEvent.SaveConversationMarkdown -> {
                val session = currentSession ?: return@eventHandler
                coroutineScope.launch {
                    val markdown = viewModel.exportConversation(session.id)
                    val saved = saveMarkdownToLocal(context, session.title, markdown)
                    if (saved != null) {
                        Toast.makeText(
                            context,
                            "已保存到 Documents/AI智囊圆桌/",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            is RoundtableEvent.CopyMessageText -> {
                copyText(event.text, "已复制至剪贴板", "复制消息失败")
            }
            is RoundtableEvent.PlayAudio -> {
                viewModel.playOrSynthesizeTts(event.message, event.voiceName)
            }
            RoundtableEvent.ClearError -> viewModel.clearError()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        RoundtableScreen(
            uiState = uiState,
            onEvent = onEvent,
            modifier = Modifier.fillMaxSize(),
        )

        SessionDrawer(
            visible = uiState.isDrawerVisible,
            sessions = uiState.sessions,
            currentSessionId = uiState.currentSessionId,
            isAutoNextEnabled = uiState.isAutoNextEnabled,
            isSemanticRoutingEnabled = uiState.isSemanticRoutingEnabled,
            onEvent = onEvent,
        )

        uiState.renameSession?.let { renameState ->
            RenameSessionDialog(
                state = renameState,
                onEvent = onEvent,
            )
        }

        if (uiState.errorMessage != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = { onEvent(RoundtableEvent.ClearError) }) {
                        Text("确定", color = Color.Yellow)
                    }
                },
            ) {
                Text(uiState.errorMessage.orEmpty())
            }
        }
    }
}
