package com.elio.skillroundtable.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PCM_SAMPLE_RATE_HZ = 24_000
private const val PCM_CHANNEL_COUNT = 1
private const val PCM_BITS_PER_SAMPLE = 16

enum class AudioSynthesisErrorCode {
    AUTH_FAILED,
    RATE_LIMITED,
    MODEL_UNAVAILABLE,
    PROTOCOL_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    EMPTY_AUDIO,
    FILE_IO_ERROR,
    ALREADY_RUNNING,
    CANCELLED,
    UNKNOWN
}

sealed interface AudioSynthesisState {
    data object Idle : AudioSynthesisState
    data object Connecting : AudioSynthesisState
    data object Configuring : AudioSynthesisState

    data class Generating(
        val receivedBytes: Long,
        val generatedDurationMs: Long = pcmBytesToDurationMs(receivedBytes)
    ) : AudioSynthesisState

    data object Finalizing : AudioSynthesisState

    data class Ready(
        val audioSizeBytes: Long,
        val generatedDurationMs: Long
    ) : AudioSynthesisState

    data class Failed(
        val code: AudioSynthesisErrorCode,
        val displayMessage: String,
        val retryable: Boolean
    ) : AudioSynthesisState
}

fun AudioSynthesisState.isInProgress(): Boolean {
    return this is AudioSynthesisState.Connecting ||
        this is AudioSynthesisState.Configuring ||
        this is AudioSynthesisState.Generating ||
        this is AudioSynthesisState.Finalizing
}

internal fun pcmBytesToDurationMs(
    byteCount: Long,
    sampleRateHz: Int = PCM_SAMPLE_RATE_HZ,
    channelCount: Int = PCM_CHANNEL_COUNT,
    bitsPerSample: Int = PCM_BITS_PER_SAMPLE
): Long {
    if (byteCount <= 0L || sampleRateHz <= 0 || channelCount <= 0 || bitsPerSample <= 0) {
        return 0L
    }
    val bytesPerSecond = sampleRateHz.toLong() * channelCount * bitsPerSample / 8L
    if (bytesPerSecond <= 0L) return 0L
    return byteCount * 1_000L / bytesPerSecond
}

object AudioSynthesisStatusStore {
    private val _states = MutableStateFlow<Map<Long, AudioSynthesisState>>(emptyMap())
    val states: StateFlow<Map<Long, AudioSynthesisState>> = _states.asStateFlow()

    fun update(messageId: Long?, state: AudioSynthesisState) {
        if (messageId == null || messageId < 0L) return
        _states.update { current -> current + (messageId to state) }
    }

    fun clear(messageId: Long?) {
        if (messageId == null || messageId < 0L) return
        _states.update { current -> current - messageId }
    }

    fun stateFor(messageId: Long): AudioSynthesisState {
        return _states.value[messageId] ?: AudioSynthesisState.Idle
    }

    internal fun resetForTests() {
        _states.value = emptyMap()
    }
}

class TtsSynthesisException(
    val code: AudioSynthesisErrorCode,
    val safeDisplayMessage: String,
    val retryable: Boolean,
    cause: Throwable? = null
) : Exception(safeDisplayMessage, cause)
