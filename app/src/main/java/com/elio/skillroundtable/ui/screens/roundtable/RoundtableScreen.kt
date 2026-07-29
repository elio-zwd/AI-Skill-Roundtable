package com.elio.skillroundtable.ui.screens.roundtable

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.ChatSession
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.telemetry.PrivacySafeLogger
import com.elio.skillroundtable.ui.CardBg
import com.elio.skillroundtable.ui.GoldAccent
import com.elio.skillroundtable.ui.PrimaryAccent
import com.elio.skillroundtable.ui.SecondaryAccent
import com.elio.skillroundtable.ui.SlateBg
import com.elio.skillroundtable.ui.TextPrimary
import com.elio.skillroundtable.ui.TextSecondary
import com.elio.skillroundtable.ui.components.CharacterAvatar
import com.elio.skillroundtable.ui.components.MinimalistPulseIndicator
import com.elio.skillroundtable.ui.components.bounceClick
import com.elio.skillroundtable.viewmodel.RoundActionState
import com.elio.skillroundtable.viewmodel.RoundtableViewModel
import com.elio.skillroundtable.viewmodel.SearchMode
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

sealed class ChatItem {
    data class UserMessage(val message: Message) : ChatItem()
    data class RoundtableRound(val roundIndex: Int, val messages: List<Message>) : ChatItem()
}

fun groupMessages(messages: List<Message>): List<ChatItem> {
    val result = mutableListOf<ChatItem>()
    var currentGroup = mutableListOf<Message>()

    for (msg in messages) {
        if (msg.senderId == "user") {
            if (currentGroup.isNotEmpty()) {
                val groupedByRound = currentGroup.groupBy { it.roundIndex }.entries.sortedBy { it.key }
                groupedByRound.forEach { (round, msgs) ->
                    result.add(ChatItem.RoundtableRound(round, msgs))
                }
                currentGroup.clear()
            }
            result.add(ChatItem.UserMessage(msg))
        } else {
            if (!msg.isPending || msg.text != "正在思考中...") {
                currentGroup.add(msg)
            }
        }
    }
    if (currentGroup.isNotEmpty()) {
        val groupedByRound = currentGroup.groupBy { it.roundIndex }.entries.sortedBy { it.key }
        groupedByRound.forEach { (round, msgs) ->
            result.add(ChatItem.RoundtableRound(round, msgs))
        }
    }
    return result
}

@Composable
fun RoundtableRoundBubble(
    roundItem: ChatItem.RoundtableRound,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onPlayAudio: (Message, String) -> Unit
) {
    val msgs = roundItem.messages
    if (msgs.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { msgs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) {
            pagerState.animateScrollToPage(msgs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(CardBg.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .border(1.dp, PrimaryAccent.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${roundItem.roundIndex} 轮脑暴交锋",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccent
            )
            Text(
                text = "${pagerState.currentPage + 1}/${msgs.size}",
                fontSize = 11.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val msg = msgs[page]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                MessageBubble(
                    message = msg,
                    currentPlayingId = currentPlayingId,
                    allCharacters = allCharacters,
                    onPlayAudio = onPlayAudio
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            msgs.forEachIndexed { index, msg ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(if (isSelected) 36.dp else 28.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) GoldAccent else TextSecondary.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CharacterAvatar(
                        avatar = msg.avatar,
                        name = msg.senderName,
                        size = if (isSelected) 36.dp else 28.dp,
                        textSize = if (isSelected) 18.sp else 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RoundtableBrainstormScreen(
    viewModel: RoundtableViewModel,
    allSessions: List<ChatSession>,
    currentSession: ChatSession?,
    currentMessages: List<Message>,
    allCharacters: List<Character>,
    isRoundtableRunning: Boolean,
    typingCharacterIds: Set<String>,
    hasApiKeys: Boolean,
    isAutoNextEnabled: Boolean,
    isSemanticRoutingEnabled: Boolean,
    searchMode: SearchMode,
    roundActionState: RoundActionState,
    onSearchModeChange: (SearchMode) -> Unit,
    onOpenApiKeyConfig: () -> Unit,
    onToggleDrawer: () -> Unit,
    onRenameSession: (Long, String) -> Unit
) {
    val listState = rememberLazyListState()
    var userQuestionText by remember { mutableStateOf("") }
    var isInputFocused by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val currentPlayingId by viewModel.currentPlayingMessageId.collectAsState()

    LaunchedEffect(currentMessages.size) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBg)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "历史会议", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                @OptIn(ExperimentalFoundationApi::class)
                Column(
                    modifier = Modifier.combinedClickable(
                        enabled = currentSession != null,
                        onLongClick = {
                            currentSession?.let {
                                onRenameSession(it.id, it.title)
                            }
                        },
                        onClick = {}
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currentSession?.title ?: "AI 智囊圆桌",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isRoundtableRunning) {
                            Spacer(Modifier.width(6.dp))
                            CircularProgressIndicator(
                                color = SecondaryAccent,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentSession != null && currentMessages.isNotEmpty()) {
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "导出对话",
                                tint = TextPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("复制为 Markdown") },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val md = viewModel.exportConversation(currentSession.id)
                                        try {
                                            clipboardManager.setText(AnnotatedString(md))
                                            Toast.makeText(context, "已复制至剪贴板", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            PrivacySafeLogger.e(
                                                "MainActivity",
                                                "复制剪贴板失败",
                                                e
                                            )
                                            Toast.makeText(context, "复制失败：剪贴板不可用", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("保存到本地文档") },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val md = viewModel.exportConversation(currentSession.id)
                                        val saved = saveMarkdownToLocal(context, currentSession.title, md)
                                        if (saved != null) {
                                            Toast.makeText(context, "已保存到 Documents/AI智囊圆桌/", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = onOpenApiKeyConfig, modifier = Modifier.bounceClick()) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "密钥设置",
                        tint = if (hasApiKeys) SecondaryAccent else GoldAccent
                    )
                }
            }
        }

        RoundtableSeatingDiagram(
            characters = allCharacters.filter { it.isActive },
            typingCharacterIds = typingCharacterIds,
            currentMessages = currentMessages,
            searchMode = searchMode,
            onSearchModeChange = onSearchModeChange
        )

        if (currentSession == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MinimalistPulseIndicator(
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "欢迎来到 AI 智囊圆桌",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "本软件支持多角色“轮流式”群聊讨论。当你输入问题，激活的智囊会顺次作答，自动携带上下文展开思想辩论！",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            viewModel.createNewSession("关于新方向的圆桌脑暴")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                        modifier = Modifier.bounceClick()
                    ) {
                        Text("开启首个圆桌会议")
                    }
                }
            }
        } else {
            val streamingCharacterIds = remember(currentMessages) {
                currentMessages.asSequence()
                    .filter { it.isPending && it.text != "正在思考中..." }
                    .map { it.senderId }
                    .toSet()
            }
            val chatItems = remember(currentMessages) { groupMessages(currentMessages) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                items(chatItems) { item ->
                    when (item) {
                        is ChatItem.UserMessage -> MessageBubble(
                            message = item.message,
                            currentPlayingId = currentPlayingId,
                            allCharacters = allCharacters,
                            onPlayAudio = { msg, voice ->
                                viewModel.playOrSynthesizeTts(msg, voice)
                            }
                        )
                        is ChatItem.RoundtableRound -> RoundtableRoundBubble(
                            roundItem = item,
                            currentPlayingId = currentPlayingId,
                            allCharacters = allCharacters,
                            onPlayAudio = { msg, voice ->
                                viewModel.playOrSynthesizeTts(msg, voice)
                            }
                        )
                    }
                }

                val waitingCharacterIds = typingCharacterIds - streamingCharacterIds
                if (isRoundtableRunning && waitingCharacterIds.isNotEmpty()) {
                    waitingCharacterIds.forEach { charId ->
                        val typingChar = allCharacters.find { it.id == charId }
                        if (typingChar != null) {
                            item(key = "typing_$charId") {
                                TypingIndicatorBubble(character = typingChar)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = currentSession != null && !isRoundtableRunning && currentMessages.isNotEmpty()) {
            val hasActiveChars = allCharacters.any { it.isActive }
            if (hasActiveChars) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    when (roundActionState) {
                        RoundActionState.CONTINUE_ROUND -> {
                            Surface(
                                onClick = { viewModel.triggerNextCharacterManual() },
                                color = SecondaryAccent.copy(alpha = 0.08f),
                                contentColor = SecondaryAccent,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, SecondaryAccent.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .bounceClick()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = SecondaryAccent
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "继续本轮",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SecondaryAccent
                                    )
                                }
                            }
                        }
                        RoundActionState.START_NEXT_ROUND -> {
                            Surface(
                                onClick = { viewModel.triggerNextCharacterManual() },
                                color = GoldAccent.copy(alpha = 0.08f),
                                contentColor = GoldAccent,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .bounceClick()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = GoldAccent
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "开启下一轮",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GoldAccent
                                    )
                                }
                            }
                        }
                        RoundActionState.BUDGET_EXCEEDED -> {
                            Surface(
                                color = Color(0xFF2D3748),
                                contentColor = TextSecondary,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.wrapContentWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = TextSecondary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "本问题已达安全预算上限",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val retryableState by viewModel.retryableRoundtableState.collectAsState()
        val showRetryBar = currentSession != null &&
            retryableState != null &&
            retryableState?.sessionId == currentSession.id &&
            !retryableState?.characterIds.isNullOrEmpty() &&
            !isRoundtableRunning

        if (showRetryBar) {
            val failedCount = retryableState?.characterIds?.size ?: 0
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                contentColor = TextPrimary,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$failedCount 位智囊未完成",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { viewModel.retryFailedCharacters() },
                            color = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .bounceClick()
                                .testTag("retry_failed_characters_button")
                        ) {
                            Text(
                                text = "重试失败角色",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.dismissRetryableState() },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("dismiss_failed_characters_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "忽略",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (currentSession != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(
                            color = if (isInputFocused) SlateBg else Color(0xFF151B27),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isInputFocused) PrimaryAccent.copy(alpha = 0.8f) else Color(0xFF232D42),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = userQuestionText,
                        onValueChange = { userQuestionText = it },
                        placeholder = { Text("向诸位智囊提问...", color = TextSecondary.copy(alpha = 0.8f), fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .testTag("chat_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        maxLines = 4,
                        enabled = !isRoundtableRunning
                    )
                    Spacer(Modifier.width(4.dp))
                    val isSendEnabled = !isRoundtableRunning && userQuestionText.isNotBlank()
                    val isActionEnabled = isRoundtableRunning || isSendEnabled
                    IconButton(
                        onClick = {
                            if (isRoundtableRunning) {
                                viewModel.cancelRoundtable()
                            } else if (userQuestionText.isNotBlank()) {
                                viewModel.askQuestion(userQuestionText)
                                userQuestionText = ""
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = when {
                                    isRoundtableRunning -> MaterialTheme.colorScheme.error
                                    isSendEnabled -> PrimaryAccent
                                    else -> Color.Transparent
                                },
                                shape = CircleShape
                            )
                            .bounceClick()
                            .testTag(if (isRoundtableRunning) "stop_button" else "send_button"),
                        enabled = isActionEnabled
                    ) {
                        Icon(
                            imageVector = if (isRoundtableRunning) Icons.Default.Close else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (isRoundtableRunning) "停止生成" else "发送",
                            tint = if (isActionEnabled) Color.White else TextSecondary.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoundtableSeatingDiagram(
    characters: List<Character>,
    typingCharacterIds: Set<String>,
    currentMessages: List<Message>,
    searchMode: SearchMode,
    onSearchModeChange: (SearchMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateBg)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(characters) { char ->
                val isTyping = typingCharacterIds.contains(char.id)
                val lastQuestionIndex = currentMessages.indexOfLast { it.senderId == "user" }
                val messagesSinceQuestion = if (lastQuestionIndex != -1) {
                    currentMessages.subList(lastQuestionIndex + 1, currentMessages.size)
                } else {
                    emptyList()
                }
                val hasReplied = messagesSinceQuestion.any { it.senderId == char.id }

                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .scale(if (isTyping) pulseScale else 1.0f)
                        .clip(CircleShape)
                        .background(
                            if (isTyping) PrimaryAccent.copy(alpha = 0.3f)
                            else if (hasReplied) SecondaryAccent.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (isTyping) 1.5.dp else 1.dp,
                            color = if (isTyping) PrimaryAccent
                            else if (hasReplied) SecondaryAccent
                            else TextSecondary.copy(alpha = 0.4f),
                            shape = CircleShape
                        )
                ) {
                    CharacterAvatar(
                        avatar = char.avatar,
                        name = char.name,
                        size = 32.dp,
                        textSize = 16.sp
                    )

                    if (hasReplied) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(SecondaryAccent)
                                .align(Alignment.BottomEnd)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(8.dp).align(Alignment.Center)
                            )
                        }
                    } else if (isTyping) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(PrimaryAccent)
                                .align(Alignment.BottomEnd)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 1.5.dp,
                                modifier = Modifier.fillMaxSize().padding(1.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CardBg)
                .border(0.5.dp, PrimaryAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(1.5.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            SearchMode.values().forEach { mode ->
                val isSelected = searchMode == mode
                val text = when (mode) {
                    SearchMode.SMART -> "智能"
                    SearchMode.FORCE -> "强制"
                    SearchMode.OFF -> "关闭"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) PrimaryAccent else Color.Transparent)
                        .bounceClick()
                        .clickable { onSearchModeChange(mode) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        fontSize = 9.sp,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onPlayAudio: (Message, String) -> Unit
) {
    val isUser = message.senderId == "user"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            CharacterAvatar(
                avatar = message.avatar,
                name = message.senderName,
                size = 42.dp,
                textSize = 20.sp
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (!isUser) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isUser) PrimaryAccent
                        else if (message.senderId == "zhang_xuefeng") GoldAccent.copy(alpha = 0.15f)
                        else CardBg
                    )
                    .border(
                        width = 1.dp,
                        color = if (isUser) PrimaryAccent
                        else if (message.senderId == "zhang_xuefeng") GoldAccent.copy(alpha = 0.5f)
                        else PrimaryAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .bounceClick()
                    .clickable {
                        try {
                            clipboardManager.setText(AnnotatedString(message.text))
                            Toast.makeText(context, "已复制至剪贴板", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            PrivacySafeLogger.e(
                                "MainActivity",
                                "复制消息剪贴板失败",
                                e
                            )
                            Toast.makeText(context, "复制失败：剪贴板不可用", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(14.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.text,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                } else {
                    MarkdownText(
                        markdown = message.text,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!isUser && !message.isPending) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .bounceClick()
                        .clickable {
                            val voice = allCharacters.find { it.id == message.senderId }?.voiceConfig ?: "Aoede"
                            onPlayAudio(message, voice)
                        }
                ) {
                    val isPlaying = currentPlayingId == message.id
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放TTS",
                        tint = if (isPlaying) GoldAccent else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "播音中..." else "合成语音",
                        fontSize = 11.sp,
                        color = if (isPlaying) GoldAccent else TextSecondary
                    )
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            CharacterAvatar(
                avatar = message.avatar,
                name = message.senderName,
                size = 42.dp,
                textSize = 20.sp
            )
        }
    }
}

@Composable
fun TypingIndicatorBubble(character: Character) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        CharacterAvatar(
            avatar = character.avatar,
            name = character.name,
            size = 42.dp,
            textSize = 20.sp
        )
        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = character.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryAccent,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                    .background(CardBg)
                    .border(
                        1.dp,
                        PrimaryAccent.copy(alpha = 0.15f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryAccent
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "正在思考如何交锋论证...",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
