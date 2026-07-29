package com.elio.skillroundtable.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.ui.CardBg
import com.elio.skillroundtable.ui.GoldAccent
import com.elio.skillroundtable.ui.PrimaryAccent
import com.elio.skillroundtable.ui.TextPrimary
import com.elio.skillroundtable.ui.TextSecondary
import com.elio.skillroundtable.ui.components.CharacterAvatar
import com.elio.skillroundtable.ui.components.bounceClick

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
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun AudioSynthesisTaskCard(
    task: AudioSynthesisTaskUiState,
    onDismissFailure: () -> Unit,
) {
    val presentation = audioSynthesisPresentation(task.state)
    val borderColor = if (presentation.isFailure) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
    } else {
        PrimaryAccent.copy(alpha = 0.35f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                AudioLibraryTestTags.synthesis(
                    messageId = task.messageId,
                    isFailure = presentation.isFailure,
                ),
            ),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (presentation.isFailure) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryAccent,
                        strokeWidth = 2.dp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = presentation.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = presentation.description,
                        color = if (presentation.isFailure) {
                            MaterialTheme.colorScheme.error
                        } else {
                            TextSecondary
                        },
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (presentation.isFailure) {
                    IconButton(
                        onClick = onDismissFailure,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭合成错误",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (presentation.showProgress) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = PrimaryAccent,
                    trackColor = PrimaryAccent.copy(alpha = 0.15f),
                )
            }

            if (presentation.showRetryHint) {
                Text(
                    text = "返回原对话后再次点击“合成语音”即可重试。",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun AudioItemCard(
    message: Message,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onTranscode: () -> Unit,
) {
    val isPlaying = currentPlayingId == message.id
    var expanded by remember { mutableStateOf(false) }
    val sizeText = formatAudioSize(message.audioSizeBytes)
    val isAac = isAacAudio(message.audioFormat)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AudioLibraryTestTags.item(message.id))
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
            },
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CharacterAvatar(
                        avatar = message.avatar,
                        name = message.senderName,
                        size = 40.dp,
                        textSize = 20.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = message.senderName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isAac) {
                                            Color.Green.copy(alpha = 0.15f)
                                        } else {
                                            Color.Yellow.copy(alpha = 0.15f)
                                        },
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = audioFormatLabel(message.audioFormat),
                                    color = if (isAac) Color.Green else GoldAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = sizeText,
                                fontSize = 11.sp,
                                color = TextSecondary,
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
                                contentColor = PrimaryAccent,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .padding(end = 6.dp)
                                .bounceClick(),
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
                                CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停音频" else "播放音频",
                            tint = if (isPlaying) GoldAccent else TextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .bounceClick()
                            .background(Color.Red.copy(alpha = 0.1f), CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除音频",
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(10.dp),
            ) {
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (shouldShowAudioBodyToggle(message.text)) {
                    Text(
                        text = audioBodyToggleLabel(expanded),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .bounceClick()
                            .clickable { expanded = !expanded },
                    )
                }
            }
        }
    }
}
