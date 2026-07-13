package com.example.skillroundtable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillroundtable.data.Character
import com.example.skillroundtable.data.Message
import com.example.skillroundtable.viewmodel.RoundtableViewModel

private val SlateBg = Color(0xFF121824)
private val CardBg = Color(0xFF1E293B)
private val PrimaryAccent = Color(0xFF3B82F6)
private val GoldAccent = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    viewModel: RoundtableViewModel,
    allCharacters: List<Character>
) {
    val audioMessages by viewModel.allAudioMessages.collectAsState()
    val currentPlayingId by viewModel.currentPlayingMessageId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
    ) {
        Text(
            text = "🎵 离线语音音频库",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "查阅、管理和离线播放所有已生成的智囊团音频，转为高压缩比的 AAC 后可大幅度瘦身。",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (audioMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎵", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("无任何已合成语音音频", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(audioMessages) { message ->
                    AudioItemCard(
                        message = message,
                        currentPlayingId = currentPlayingId,
                        allCharacters = allCharacters,
                        onPlay = {
                            val voice = allCharacters.find { it.id == message.senderId }?.voiceConfig ?: "Aoede"
                            viewModel.playOrSynthesizeTts(message, voice)
                        },
                        onDelete = {
                            viewModel.deleteAudio(message)
                        },
                        onTranscode = {
                            if (message.audioFormat == "wav" && !message.audioFilePath.isNullOrBlank()) {
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
        message.audioSizeBytes >= 1024 * 1024 -> String.format("%.2f MB", message.audioSizeBytes.toDouble() / (1024 * 1024))
        message.audioSizeBytes >= 1024 -> String.format("%.1f KB", message.audioSizeBytes.toDouble() / 1024)
        else -> "${message.audioSizeBytes} B"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isPlaying) GoldAccent.copy(alpha = 0.4f) else PrimaryAccent.copy(alpha = 0.1f))
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
                                    .background(if (isAac) Color.Green.copy(alpha = 0.15f) else Color.Yellow.copy(alpha = 0.15f))
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
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent.copy(alpha = 0.1f), contentColor = PrimaryAccent),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp).padding(end = 6.dp)
                        ) {
                            Text("⚡ 转码", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (isPlaying) GoldAccent.copy(alpha = 0.2f) else PrimaryAccent.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播音",
                            tint = if (isPlaying) GoldAccent else TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
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
                            .clickable { expanded = !expanded }
                    )
                }
            }
        }
    }
}
