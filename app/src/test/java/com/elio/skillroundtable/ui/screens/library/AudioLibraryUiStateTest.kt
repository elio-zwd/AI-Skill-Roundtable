package com.elio.skillroundtable.ui.screens.library

import com.elio.skillroundtable.audio.AudioSynthesisErrorCode
import com.elio.skillroundtable.audio.AudioSynthesisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLibraryUiStateTest {
    @Test
    fun synthesisPresentation_mapsEveryStateToStableTitleAndDescription() {
        assertEquals("正在连接语音服务", audioSynthesisPresentation(AudioSynthesisState.Connecting).title)
        assertEquals(
            "正在建立安全连接…",
            audioSynthesisPresentation(AudioSynthesisState.Connecting).description,
        )
        assertEquals(
            "已连接，等待服务端确认配置…",
            audioSynthesisPresentation(AudioSynthesisState.Configuring).description,
        )
        assertEquals(
            "已开始生成，等待首段音频…",
            audioSynthesisPresentation(AudioSynthesisState.Generating(receivedBytes = 0L)).description,
        )
        assertEquals(
            "已生成 1.5 秒音频",
            audioSynthesisPresentation(
                AudioSynthesisState.Generating(
                    receivedBytes = 0L,
                    generatedDurationMs = 1_500L,
                ),
            ).description,
        )
        assertEquals(
            "正在校验并写入 WAV 文件…",
            audioSynthesisPresentation(AudioSynthesisState.Finalizing).description,
        )
        assertEquals(
            "已生成 2.0 秒音频",
            audioSynthesisPresentation(
                AudioSynthesisState.Ready(audioSizeBytes = 2_048L, generatedDurationMs = 2_000L),
            ).description,
        )
    }

    @Test
    fun failedSynthesis_exposesRetryHintOnlyWhenRetryable() {
        val retryable = audioSynthesisPresentation(
            AudioSynthesisState.Failed(
                code = AudioSynthesisErrorCode.NETWORK_ERROR,
                displayMessage = "网络连接失败",
                retryable = true,
            ),
        )
        val terminal = audioSynthesisPresentation(
            AudioSynthesisState.Failed(
                code = AudioSynthesisErrorCode.AUTH_FAILED,
                displayMessage = "Key 无权限",
                retryable = false,
            ),
        )

        assertEquals("语音合成失败", retryable.title)
        assertEquals("网络连接失败", retryable.description)
        assertTrue(retryable.isFailure)
        assertTrue(retryable.showRetryHint)
        assertFalse(terminal.showRetryHint)
    }

    @Test
    fun visibleSynthesisTasks_keepsOnlyProgressAndFailureSortedByMessageId() {
        val tasks = buildVisibleSynthesisTasks(
            mapOf(
                8L to AudioSynthesisState.Ready(100L, 10L),
                5L to AudioSynthesisState.Finalizing,
                2L to AudioSynthesisState.Failed(
                    AudioSynthesisErrorCode.UNKNOWN,
                    "失败",
                    true,
                ),
                1L to AudioSynthesisState.Idle,
            ),
        )

        assertEquals(listOf(2L, 5L), tasks.map { it.messageId })
    }

    @Test
    fun audioMetadataPresentation_keepsFormatSizeAndExpansionRules() {
        assertEquals("512 B", formatAudioSize(512L))
        assertEquals("1.5 KB", formatAudioSize(1_536L))
        assertEquals("2.00 MB", formatAudioSize(2L * 1024 * 1024))
        assertEquals("WAV", audioFormatLabel(null))
        assertEquals("AAC", audioFormatLabel("aac"))
        assertTrue(canTranscodeAudio("wav", "/tmp/audio.wav"))
        assertFalse(canTranscodeAudio("wav", ""))
        assertFalse(canTranscodeAudio("aac", "/tmp/audio.aac"))
        assertFalse(shouldShowAudioBodyToggle("x".repeat(70)))
        assertTrue(shouldShowAudioBodyToggle("x".repeat(71)))
        assertEquals("展开全文", audioBodyToggleLabel(false))
        assertEquals("收起全文", audioBodyToggleLabel(true))
    }

    @Test
    fun audioTestTags_preserveExistingSynthesisContracts() {
        assertEquals("audio_synthesis_progress_42", AudioLibraryTestTags.synthesis(42L, false))
        assertEquals("audio_synthesis_error_42", AudioLibraryTestTags.synthesis(42L, true))
        assertEquals("audio_item_42", AudioLibraryTestTags.item(42L))
    }
}
