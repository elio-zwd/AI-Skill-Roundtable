package com.elio.skillroundtable.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.ui.SlateBg
import com.elio.skillroundtable.ui.TextPrimary
import com.elio.skillroundtable.ui.TextSecondary

@Composable
fun AudioLibraryScreen(
    uiState: AudioLibraryUiState,
    onDismissSynthesisFailure: (Long) -> Unit,
    onPlay: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onTranscode: (Message) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
            .testTag(AudioLibraryTestTags.ROOT),
    ) {
        Text(
            text = "离线语音音频库",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )
        Text(
            text = "生成时显示真实的已接收音频时长；完成后可离线播放并转为 AAC 节省空间。",
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (uiState.isEmpty) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(AudioLibraryTestTags.EMPTY_STATE),
                contentAlignment = Alignment.Center,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.synthesisTasks,
                    key = { task -> "synthesis_${task.messageId}" },
                ) { task ->
                    AudioSynthesisTaskCard(
                        task = task,
                        onDismissFailure = {
                            onDismissSynthesisFailure(task.messageId)
                        },
                    )
                }

                items(
                    items = uiState.audioMessages,
                    key = { message -> message.id },
                ) { message ->
                    AudioItemCard(
                        message = message,
                        currentPlayingId = uiState.currentPlayingId,
                        allCharacters = uiState.allCharacters,
                        onPlay = { onPlay(message) },
                        onDelete = { onDelete(message) },
                        onTranscode = { onTranscode(message) },
                    )
                }
            }
        }
    }
}
