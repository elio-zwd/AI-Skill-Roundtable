package com.elio.skillroundtable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.skillroundtable.audio.AudioSynthesisState
import com.elio.skillroundtable.audio.AudioSynthesisStatusStore
import com.elio.skillroundtable.audio.isInProgress
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.viewmodel.RoundtableViewModel

// Consistent High-End Slate Palette
private val SlateBg = Color(0xFF121824)
private val CardBg = Color(0xFF1E2638)
private val PrimaryAccent = Color(0xFF6366F1)
private val SecondaryAccent = Color(0xFF10B981)
private val GoldAccent = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFF3F4F6)
private val TextSecondary = Color(0xFF9CA3AF)

@Composable
fun MinimalistAudioEmptyIndicator(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(80.dp)) {
        val width = size.width
        val height = size.height
        val barCount = 5
        val spacing = 8.dp.toPx()
        val barWidth = 4.dp.toPx()
        val startX = (width - (barCount * barWidth + (barCount - 1) * spacing)) / 2
        val heights = floatArrayOf(0.3f, 0.6f, 0.8f, 0.5f, 0.2f)

        for (index in 0 until barCount) {
            val x = startX + index * (barWidth + spacing)
            val barHeight = height * heights[index]
            val y = (height - barHeight) / 2
            drawRoundRect(
                color = PrimaryAccent.copy(alpha = 0.4f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    viewModel: RoundtableViewModel,
    allCharacters: List<Character>
) {
    val audioMessages by viewModel.allAudioMessages.collectAsState()
    val currentPlayingId by viewModel.currentPlayingMessageId.collectAsState()
    val synthesisStates by AudioSynthesisStatusStore.states.collectAsState()

    val visibleSynthesisTasks = synthesisStates.entries
        .filter { (_, state) ->
            state.isInProgress() || state is AudioSynthesisState.Failed
        }
        .sortedBy { (messageId, _) -> messageId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
    ) {
        Text(
            text = "离线语音音频库",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "生成时显示真实的已接收音频时长；完成后可离线播放并转为 AAC 节省空间。",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (audioMessages.isEmpty() && visibleSynthesisTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MinimalistAudioEmptyIndicator(modifier = Modifier.padding(bottom = 12.dp))
                    Text("无任何已合成语音音频", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = visibleSynthesisTasks,
                    key = { (messageId, _) -> "synthesis_$messageId" }
                ) { (messageId, state) ->
                    AudioSynthesisTaskCard(
                        messageId = messageId,
                        state = state,
                        onDismissFailure = {
                            AudioSynthesisStatusStore.clear(messageId)
                        }
                    )
                }

                items(
                    items = audioMessages,
                    key = { it.id }
                ) { message ->
                    AudioItemCard(
                        message = message,
                        currentPlayingId = currentPlayingId,
                        allCharacters = allCharacters,
                        onPlay = {
                            val voice = allCharacters
                                .find { it.id == message.senderId }
                                ?.voiceConfig
                                ?: "Aoede"
                            viewModel.playOrSynthesizeTts(message, voice)
                        },
                        onDelete = {
                            viewModel.deleteAudio(message)
                        },
                        onTranscode = {
                            if (message.audioFormat == "wav" &&
                                !message.audioFilePath.isNullOrBlank()
                            ) {
                                viewModel.triggerTranscode(message.id, message.audioFilePath)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioSynthesisTaskCard(
    messageId: Long,
    state: AudioSynthesisState,
    onDismissFailure: () -> Unit
) {
    val isFailure = state is AudioSynthesisState.Failed
    val borderColor = if (isFailure) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    } else {
        PrimaryAccent.copy(alpha = 0.35f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (isFailure) {
                    "audio_synthesis_error_$messageId"
                } else {
                    "audio_synthesis_progress_$messageId"
                }
            ),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isFailure) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryAccent,
                        strokeWidth = 2.dp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = synthesisTitle(state),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = synthesisDescription(state),
                        color = if (isFailure) {
                            MaterialTheme.colorScheme.error
                        } else {
                            TextSecondary
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (isFailure) {
                    IconButton(
                        onClick = onDismissFailure,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭合成错误",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (state.isInProgress()) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PrimaryAccent,
                    trackColor = PrimaryAccent.copy(alpha = 0.15f)
                )
            }

            if (state is AudioSynthesisState.Failed && state.retryable) {
                Text(
                    text = "返回原对话后再次点击“合成语音”即可重试。",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun synthesisTitle(state: AudioSynthesisState): String {
    return when (state) {
        AudioSynthesisState.Idle -> "等待生成语音"
        AudioSynthesisState.Connecting -> "正在连接语音服务"
        AudioSynthesisState.Configuring -> "正在初始化语音模型"
        is AudioSynthesisState.Generating -> "正在生成语音"
        AudioSynthesisState.Finalizing -> "正在保存音频"
        is AudioSynthesisState.Ready -> "语音已生成"
        is AudioSynthesisState.Failed -> "语音合成失败"
    }
}

private fun synthesisDescription(state: AudioSynthesisState): String {
    return when (state) {
        AudioSynthesisState.Idle -> "尚未开始"
        AudioSynthesisState.Connecting -> "正在建立安全连接…"
        AudioSynthesisState.Configuring -> "已连接，等待服务端确认配置…"
        is AudioSynthesisState.Generating -> {
            if (state.generatedDurationMs <= 0L) {
                "已开始生成，等待首段音频…"
            } else {
                val seconds = state.generatedDurationMs / 1_000.0
                "已生成 ${String.format("%.1f", seconds)} 秒音频"
            }
        }
        AudioSynthesisState.Finalizing -> "正在校验并写入 WAV 文件…"
        is AudioSynthesisState.Ready -> {
            val seconds = state.generatedDurationMs / 1_000.0
            "已生成 ${String.format("%.1f", seconds)} 秒音频"
        }
        is AudioSynthesisState.Failed -> state.displayMessage
    }
}

@Composable
fun AudioItemCard(
    message: Message,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onTranscode: () -> Unit
) {
    val isPlaying = currentPlayingId == message.id
    var expanded by remember { mutableStateOf(false) }

    val sizeText = when {
        message.audioSizeBytes >= 1024 * 1024 -> {
            String.format(
                "%.2f MB",
                message.audioSizeBytes.toDouble() / (1024 * 1024)
            )
        }
        message.audioSizeBytes >= 1024 -> {
            String.format("%.1f KB", message.audioSizeBytes.toDouble() / 1024)
        }
        else -> "${message.audioSizeBytes} B"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isPlaying) {
                GoldAccent.copy(alpha = 0.4f)
            } else {
                PrimaryAccent.copy(alpha = 0.1f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterAvatar(
                        avatar = message.avatar,
                        name = message.senderName,
                        size = 40.dp,
                        textSize = 20.sp
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = message.senderName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isAac = message.audioFormat == "aac"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isAac) {
                                            Color.Green.copy(alpha = 0.15f)
                                        } else {
                                            Color.Yellow.copy(alpha = 0.15f)
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = message.audioFormat?.uppercase() ?: "WAV",
                                    color = if (isAac) Color.Green else GoldAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = sizeText,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.audioFormat == "wav") {
                        Button(
                            onClick = onTranscode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryAccent.copy(alpha = 0.1f),
                                contentColor = PrimaryAccent
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .padding(end = 6.dp)
                                .bounceClick()
                        ) {
                            Text("转码", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .size(32.dp)
                            .bounceClick()
                            .background(
                                if (isPlaying) {
                                    GoldAccent.copy(alpha = 0.2f)
                                } else {
                                    PrimaryAccent.copy(alpha = 0.1f)
                                },
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停音频" else "播放音频",
                            tint = if (isPlaying) GoldAccent else TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .bounceClick()
                            .background(Color.Red.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除音频",
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )

                if (message.text.length > 70) {
                    Text(
                        text = if (expanded) "收起全文" else "展开全文",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .bounceClick()
                            .clickable { expanded = !expanded }
                    )
                }
            }
        }
    }
}
