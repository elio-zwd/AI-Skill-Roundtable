package com.elio.skillroundtable.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.elio.skillroundtable.audio.AudioSynthesisStatusStore
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.viewmodel.RoundtableViewModel

/**
 * 保留 PR07-B 冻结的页面入口签名，由 Route 负责收集状态和连接业务事件。
 */
@Composable
fun AudioLibraryScreen(
    viewModel: RoundtableViewModel,
    allCharacters: List<Character>,
) {
    AudioLibraryRoute(
        viewModel = viewModel,
        allCharacters = allCharacters,
    )
}

@Composable
fun AudioLibraryRoute(
    viewModel: RoundtableViewModel,
    allCharacters: List<Character>,
) {
    val audioMessages by viewModel.allAudioMessages.collectAsState()
    val currentPlayingId by viewModel.currentPlayingMessageId.collectAsState()
    val synthesisStates by AudioSynthesisStatusStore.states.collectAsState()

    AudioLibraryScreen(
        uiState = AudioLibraryUiState(
            audioMessages = audioMessages,
            currentPlayingId = currentPlayingId,
            synthesisTasks = buildVisibleSynthesisTasks(synthesisStates),
            allCharacters = allCharacters,
        ),
        onDismissSynthesisFailure = { messageId ->
            AudioSynthesisStatusStore.clear(messageId)
        },
        onPlay = { message -> playAudioMessage(viewModel, allCharacters, message) },
        onDelete = viewModel::deleteAudio,
        onTranscode = { message ->
            if (canTranscodeAudio(message.audioFormat, message.audioFilePath)) {
                viewModel.triggerTranscode(message.id, message.audioFilePath.orEmpty())
            }
        },
    )
}

private fun playAudioMessage(
    viewModel: RoundtableViewModel,
    allCharacters: List<Character>,
    message: Message,
) {
    val voice = allCharacters
        .find { it.id == message.senderId }
        ?.voiceConfig
        ?: "Aoede"
    viewModel.playOrSynthesizeTts(message, voice)
}
