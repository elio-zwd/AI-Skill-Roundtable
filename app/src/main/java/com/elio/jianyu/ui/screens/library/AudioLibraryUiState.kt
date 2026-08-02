package com.elio.jianyu.ui.screens.library

import com.elio.jianyu.audio.AudioSynthesisState
import com.elio.jianyu.audio.isInProgress
import com.elio.jianyu.data.Character
import com.elio.jianyu.data.Message

data class AudioSynthesisTaskUiState(
    val messageId: Long,
    val state: AudioSynthesisState,
)

data class AudioLibraryUiState(
    val audioMessages: List<Message>,
    val currentPlayingId: Long?,
    val synthesisTasks: List<AudioSynthesisTaskUiState>,
    val allCharacters: List<Character>,
) {
    val isEmpty: Boolean
        get() = audioMessages.isEmpty() && synthesisTasks.isEmpty()
}

data class AudioSynthesisPresentation(
    val title: String,
    val description: String,
    val isFailure: Boolean,
    val showProgress: Boolean,
    val showRetryHint: Boolean,
)

internal object AudioLibraryTestTags {
    const val ROOT = "audio_library"
    const val EMPTY_STATE = "audio_library_empty"
    const val ITEM_PREFIX = "audio_item_"
    const val SYNTHESIS_PROGRESS_PREFIX = "audio_synthesis_progress_"
    const val SYNTHESIS_ERROR_PREFIX = "audio_synthesis_error_"

    fun item(messageId: Long): String = "$ITEM_PREFIX$messageId"

    fun synthesis(messageId: Long, isFailure: Boolean): String {
        return if (isFailure) {
            "$SYNTHESIS_ERROR_PREFIX$messageId"
        } else {
            "$SYNTHESIS_PROGRESS_PREFIX$messageId"
        }
    }
}

internal fun buildVisibleSynthesisTasks(
    states: Map<Long, AudioSynthesisState>,
): List<AudioSynthesisTaskUiState> {
    return states.entries
        .filter { (_, state) ->
            state.isInProgress() || state is AudioSynthesisState.Failed
        }
        .sortedBy { (messageId, _) -> messageId }
        .map { (messageId, state) -> AudioSynthesisTaskUiState(messageId, state) }
}

internal fun audioSynthesisPresentation(
    state: AudioSynthesisState,
): AudioSynthesisPresentation {
    val title = when (state) {
        AudioSynthesisState.Idle -> "等待生成语音"
        AudioSynthesisState.Connecting -> "正在连接语音服务"
        AudioSynthesisState.Configuring -> "正在初始化语音模型"
        is AudioSynthesisState.Generating -> "正在生成语音"
        AudioSynthesisState.Finalizing -> "正在保存音频"
        is AudioSynthesisState.Ready -> "语音已生成"
        is AudioSynthesisState.Failed -> "语音合成失败"
    }
    val description = when (state) {
        AudioSynthesisState.Idle -> "尚未开始"
        AudioSynthesisState.Connecting -> "正在建立安全连接…"
        AudioSynthesisState.Configuring -> "已连接，等待服务端确认配置…"
        is AudioSynthesisState.Generating -> {
            if (state.generatedDurationMs <= 0L) {
                "已开始生成，等待首段音频…"
            } else {
                "已生成 ${formatAudioDuration(state.generatedDurationMs)} 秒音频"
            }
        }
        AudioSynthesisState.Finalizing -> "正在校验并写入 WAV 文件…"
        is AudioSynthesisState.Ready -> {
            "已生成 ${formatAudioDuration(state.generatedDurationMs)} 秒音频"
        }
        is AudioSynthesisState.Failed -> state.displayMessage
    }
    return AudioSynthesisPresentation(
        title = title,
        description = description,
        isFailure = state is AudioSynthesisState.Failed,
        showProgress = state.isInProgress(),
        showRetryHint = state is AudioSynthesisState.Failed && state.retryable,
    )
}

internal fun formatAudioDuration(durationMs: Long): String {
    return String.format("%.1f", durationMs / 1_000.0)
}

internal fun formatAudioSize(audioSizeBytes: Long): String {
    return when {
        audioSizeBytes >= 1024 * 1024 -> {
            String.format("%.2f MB", audioSizeBytes.toDouble() / (1024 * 1024))
        }
        audioSizeBytes >= 1024 -> {
            String.format("%.1f KB", audioSizeBytes.toDouble() / 1024)
        }
        else -> "$audioSizeBytes B"
    }
}

internal fun audioFormatLabel(format: String?): String = format?.uppercase() ?: "WAV"

internal fun isAacAudio(format: String?): Boolean = format == "aac"

internal fun canTranscodeAudio(format: String?, audioFilePath: String?): Boolean {
    return format == "wav" && !audioFilePath.isNullOrBlank()
}

internal fun shouldShowAudioBodyToggle(text: String): Boolean = text.length > 70

internal fun audioBodyToggleLabel(expanded: Boolean): String {
    return if (expanded) "收起全文" else "展开全文"
}
