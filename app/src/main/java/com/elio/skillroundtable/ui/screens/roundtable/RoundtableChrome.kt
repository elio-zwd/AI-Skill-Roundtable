package com.elio.skillroundtable.ui.screens.roundtable

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.elio.skillroundtable.viewmodel.SearchMode

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RoundtableTopBar(
    uiState: RoundtableUiState,
    onEvent: (RoundtableEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onEvent(RoundtableEvent.ToggleDrawer) }) {
                Icon(Icons.Default.Menu, contentDescription = "历史会议", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.combinedClickable(
                    enabled = uiState.currentSession != null,
                    onLongClick = {
                        uiState.currentSession?.let { session ->
                            onEvent(RoundtableEvent.RequestRename(session.id, session.title))
                        }
                    },
                    onClick = {},
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = uiState.currentSession?.title ?: "AI 智囊圆桌",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (uiState.isRoundtableRunning) {
                        Spacer(Modifier.width(6.dp))
                        CircularProgressIndicator(
                            color = SecondaryAccent,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.canExport) {
                var showExportMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "导出对话",
                            tint = TextPrimary,
                        )
                    }
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("复制为 Markdown") },
                            onClick = {
                                showExportMenu = false
                                onEvent(RoundtableEvent.CopyConversationMarkdown)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("保存到本地文档") },
                            onClick = {
                                showExportMenu = false
                                onEvent(RoundtableEvent.SaveConversationMarkdown)
                            },
                        )
                    }
                }
            }

            IconButton(
                onClick = { onEvent(RoundtableEvent.OpenApiKeyConfig) },
                modifier = Modifier.bounceClick(),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "密钥设置",
                    tint = if (uiState.hasApiKeys) SecondaryAccent else GoldAccent,
                )
            }
        }
    }
}

@Composable
internal fun RoundtableSeatingBar(
    seats: List<CharacterSeatUiState>,
    searchMode: SearchMode,
    onEvent: (RoundtableEvent) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SlateBg)
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(seats, key = { it.character.id }) { seat ->
                CharacterSeat(state = seat)
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CardBg)
                .border(0.5.dp, PrimaryAccent.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(1.5.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
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
                        .clickable { onEvent(RoundtableEvent.SearchModeChanged(mode)) }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = text,
                        fontSize = 9.sp,
                        color = if (isSelected) Color.White else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterSeat(state: CharacterSeatUiState) {
    val isWaiting = state.status == CharacterTurnStatus.WAITING
    val hasVisibleReply = state.status == CharacterTurnStatus.STREAMING ||
        state.status == CharacterTurnStatus.COMPLETED
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .scale(if (isWaiting) pulseScale else 1.0f)
            .clip(CircleShape)
            .background(
                when {
                    isWaiting -> PrimaryAccent.copy(alpha = 0.3f)
                    hasVisibleReply -> SecondaryAccent.copy(alpha = 0.15f)
                    else -> Color.Transparent
                },
            )
            .border(
                width = if (isWaiting) 1.5.dp else 1.dp,
                color = when {
                    isWaiting -> PrimaryAccent
                    hasVisibleReply -> SecondaryAccent
                    else -> TextSecondary.copy(alpha = 0.4f)
                },
                shape = CircleShape,
            ),
    ) {
        CharacterAvatar(
            avatar = state.character.avatar,
            name = state.character.name,
            size = 32.dp,
            textSize = 16.sp,
        )

        if (hasVisibleReply) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(SecondaryAccent)
                    .align(Alignment.BottomEnd),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.Center),
                )
            }
        } else if (isWaiting) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(PrimaryAccent)
                    .align(Alignment.BottomEnd),
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.dp),
                )
            }
        }
    }
}

@Composable
internal fun RoundtableEmptyState(
    onCreateSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MinimalistPulseIndicator(modifier = Modifier.padding(bottom = 16.dp))
            Text(
                text = "欢迎来到 AI 智囊圆桌",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "本软件支持多角色“轮流式”群聊讨论。当你输入问题，激活的智囊会顺次作答，自动携带上下文展开思想辩论！",
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onCreateSession,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                modifier = Modifier.bounceClick(),
            ) {
                Text("开启首个圆桌会议")
            }
        }
    }
}

@Composable
internal fun RoundActionBar(
    action: RoundActionUiState,
    onEvent: (RoundtableEvent) -> Unit,
) {
    AnimatedVisibility(visible = action != RoundActionUiState.HIDDEN) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            when (action) {
                RoundActionUiState.HIDDEN -> Unit
                RoundActionUiState.CONTINUE_ROUND -> {
                    Surface(
                        onClick = { onEvent(RoundtableEvent.ContinueRound) },
                        color = SecondaryAccent.copy(alpha = 0.08f),
                        contentColor = SecondaryAccent,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, SecondaryAccent.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .wrapContentWidth()
                            .bounceClick(),
                    ) {
                        RoundActionContent(
                            icon = Icons.Default.PlayArrow,
                            text = "继续本轮",
                            color = SecondaryAccent,
                        )
                    }
                }
                RoundActionUiState.START_NEXT_ROUND -> {
                    Surface(
                        onClick = { onEvent(RoundtableEvent.ContinueRound) },
                        color = GoldAccent.copy(alpha = 0.08f),
                        contentColor = GoldAccent,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .wrapContentWidth()
                            .bounceClick(),
                    ) {
                        RoundActionContent(
                            icon = Icons.Default.Refresh,
                            text = "开启下一轮",
                            color = GoldAccent,
                        )
                    }
                }
                RoundActionUiState.BUDGET_EXCEEDED -> {
                    Surface(
                        color = Color(0xFF2D3748),
                        contentColor = TextSecondary,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        RoundActionContent(
                            icon = Icons.Default.Info,
                            text = "本问题已达安全预算上限",
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundActionContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

@Composable
internal fun FailedCharactersRetryBar(
    state: RetryBarUiState,
    onEvent: (RoundtableEvent) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
        contentColor = TextPrimary,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${state.failedCount} 位智囊未完成",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { onEvent(RoundtableEvent.RetryFailedCharacters) },
                    color = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .bounceClick()
                        .testTag(RoundtableTestTags.RETRY_FAILED_CHARACTERS_BUTTON),
                ) {
                    Text(
                        text = "重试失败角色",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = { onEvent(RoundtableEvent.DismissRetryableState) },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag(RoundtableTestTags.DISMISS_FAILED_CHARACTERS_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "忽略",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoundtableInputBar(
    inputText: String,
    isRoundtableRunning: Boolean,
    onEvent: (RoundtableEvent) -> Unit,
) {
    var isInputFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    color = if (isInputFocused) SlateBg else Color(0xFF151B27),
                    shape = RoundedCornerShape(24.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (isInputFocused) {
                        PrimaryAccent.copy(alpha = 0.8f)
                    } else {
                        Color(0xFF232D42)
                    },
                    shape = RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = inputText,
                onValueChange = { onEvent(RoundtableEvent.InputChanged(it)) },
                placeholder = {
                    Text(
                        "向诸位智囊提问...",
                        color = TextSecondary.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isInputFocused = it.isFocused }
                    .testTag(RoundtableTestTags.CHAT_INPUT),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                maxLines = 4,
                enabled = !isRoundtableRunning,
            )
            Spacer(Modifier.width(4.dp))
            val isSendEnabled = !isRoundtableRunning && inputText.isNotBlank()
            val isActionEnabled = isRoundtableRunning || isSendEnabled
            IconButton(
                onClick = { onEvent(RoundtableEvent.SubmitOrStop) },
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = when {
                            isRoundtableRunning -> MaterialTheme.colorScheme.error
                            isSendEnabled -> PrimaryAccent
                            else -> Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .bounceClick()
                    .testTag(
                        if (isRoundtableRunning) {
                            RoundtableTestTags.STOP_BUTTON
                        } else {
                            RoundtableTestTags.SEND_BUTTON
                        },
                    ),
                enabled = isActionEnabled,
            ) {
                Icon(
                    imageVector = if (isRoundtableRunning) {
                        Icons.Default.Close
                    } else {
                        Icons.AutoMirrored.Filled.Send
                    },
                    contentDescription = if (isRoundtableRunning) "停止生成" else "发送",
                    tint = if (isActionEnabled) Color.White else TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
