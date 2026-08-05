package com.elio.jianyu.audio.assets

import java.io.File

/** 正式音频资产播放的稳定错误码。 */
enum class AudioAssetPlaybackErrorCode {
    FILE_MISSING,
    PATH_REJECTED,
    PLAYER_FAILURE,
    INVALID_STATE,
    RELEASED,
}

sealed interface AudioAssetPlaybackState {
    data object Idle : AudioAssetPlaybackState

    data class Playing(val audioAssetId: String) : AudioAssetPlaybackState

    data class Paused(val audioAssetId: String) : AudioAssetPlaybackState

    data class Failed(
        val audioAssetId: String,
        val errorCode: AudioAssetPlaybackErrorCode,
    ) : AudioAssetPlaybackState

    data object Released : AudioAssetPlaybackState
}

sealed interface AudioAssetPlaybackResult {
    data object STARTED : AudioAssetPlaybackResult

    data object PAUSED : AudioAssetPlaybackResult

    data object RESUMED : AudioAssetPlaybackResult

    data object STOPPED : AudioAssetPlaybackResult

    data class Failure(
        val errorCode: AudioAssetPlaybackErrorCode,
    ) : AudioAssetPlaybackResult
}

/** Android MediaPlayer 或测试播放器需要实现的最小能力。 */
interface AudioAssetPlayer {
    fun start()

    fun pause()

    fun stop()

    fun release()
}

fun interface AudioAssetPlayerFactory {
    fun create(
        file: File,
        onCompletion: () -> Unit,
        onError: (Throwable) -> Unit,
    ): AudioAssetPlayer
}

/**
 * 以 audioAssetId 为身份的单活动音频播放状态机。
 *
 * 该组件只读取本地受控文件，不包含网络、缓存回填或来源对象写入能力。
 */
class AudioAssetPlaybackManager(
    private val fileStore: AudioFileStore,
    private val playerFactory: AudioAssetPlayerFactory,
) {
    var state: AudioAssetPlaybackState = AudioAssetPlaybackState.Idle
        private set

    private var activeAudioAssetId: String? = null
    private var activePlayer: AudioAssetPlayer? = null
    private var activeToken: Any? = null
    private var released: Boolean = false

    fun play(
        audioAssetId: String,
        relativePath: String,
    ): AudioAssetPlaybackResult {
        if (released) {
            return AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.RELEASED)
        }
        if (audioAssetId.isBlank()) {
            return fail(audioAssetId, AudioAssetPlaybackErrorCode.PATH_REJECTED)
        }

        val availableFile = when (val resolution = fileStore.resolve(relativePath)) {
            is AudioFileResolution.Available -> resolution.file
            is AudioFileResolution.Missing -> {
                return fail(audioAssetId, AudioAssetPlaybackErrorCode.FILE_MISSING)
            }
            is AudioFileResolution.Rejected -> {
                return fail(audioAssetId, AudioAssetPlaybackErrorCode.PATH_REJECTED)
            }
        }

        clearActivePlayer(stopFirst = true)
        val token = Any()
        activeToken = token
        activeAudioAssetId = audioAssetId

        val player = try {
            playerFactory.create(
                file = availableFile,
                onCompletion = { handleCompletion(token) },
                onError = { handlePlayerError(token) },
            )
        } catch (_: Throwable) {
            clearActiveIdentity(token)
            return fail(audioAssetId, AudioAssetPlaybackErrorCode.PLAYER_FAILURE)
        }
        activePlayer = player

        return try {
            player.start()
            state = AudioAssetPlaybackState.Playing(audioAssetId)
            AudioAssetPlaybackResult.STARTED
        } catch (_: Throwable) {
            clearActivePlayer(stopFirst = false)
            fail(audioAssetId, AudioAssetPlaybackErrorCode.PLAYER_FAILURE)
        }
    }

    fun pause(): AudioAssetPlaybackResult {
        if (released) {
            return AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.RELEASED)
        }
        val currentState = state
        val player = activePlayer
        if (currentState !is AudioAssetPlaybackState.Playing || player == null) {
            return AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.INVALID_STATE)
        }

        return try {
            player.pause()
            state = AudioAssetPlaybackState.Paused(currentState.audioAssetId)
            AudioAssetPlaybackResult.PAUSED
        } catch (_: Throwable) {
            clearActivePlayer(stopFirst = false)
            fail(currentState.audioAssetId, AudioAssetPlaybackErrorCode.PLAYER_FAILURE)
        }
    }

    fun resume(): AudioAssetPlaybackResult {
        if (released) {
            return AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.RELEASED)
        }
        val currentState = state
        val player = activePlayer
        if (currentState !is AudioAssetPlaybackState.Paused || player == null) {
            return AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.INVALID_STATE)
        }

        return try {
            player.start()
            state = AudioAssetPlaybackState.Playing(currentState.audioAssetId)
            AudioAssetPlaybackResult.RESUMED
        } catch (_: Throwable) {
            clearActivePlayer(stopFirst = false)
            fail(currentState.audioAssetId, AudioAssetPlaybackErrorCode.PLAYER_FAILURE)
        }
    }

    fun stop(): AudioAssetPlaybackResult {
        if (released) {
            return AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.RELEASED)
        }
        val audioAssetId = activeAudioAssetId
        if (activePlayer == null || audioAssetId == null) {
            state = AudioAssetPlaybackState.Idle
            return AudioAssetPlaybackResult.STOPPED
        }

        val clean = clearActivePlayer(stopFirst = true)
        return if (clean) {
            state = AudioAssetPlaybackState.Idle
            AudioAssetPlaybackResult.STOPPED
        } else {
            fail(audioAssetId, AudioAssetPlaybackErrorCode.PLAYER_FAILURE)
        }
    }

    fun release() {
        if (released) return
        released = true
        clearActivePlayer(stopFirst = true)
        state = AudioAssetPlaybackState.Released
    }

    private fun handleCompletion(token: Any) {
        if (activeToken !== token || released) return
        clearActivePlayer(stopFirst = false)
        state = AudioAssetPlaybackState.Idle
    }

    private fun handlePlayerError(token: Any) {
        if (activeToken !== token || released) return
        val audioAssetId = activeAudioAssetId ?: return
        clearActivePlayer(stopFirst = false)
        state = AudioAssetPlaybackState.Failed(
            audioAssetId = audioAssetId,
            errorCode = AudioAssetPlaybackErrorCode.PLAYER_FAILURE,
        )
    }

    private fun clearActivePlayer(stopFirst: Boolean): Boolean {
        val player = activePlayer
        activePlayer = null
        activeAudioAssetId = null
        activeToken = null
        if (player == null) return true

        var clean = true
        if (stopFirst) {
            try {
                player.stop()
            } catch (_: Throwable) {
                clean = false
            }
        }
        try {
            player.release()
        } catch (_: Throwable) {
            clean = false
        }
        return clean
    }

    private fun clearActiveIdentity(token: Any) {
        if (activeToken !== token) return
        activePlayer = null
        activeAudioAssetId = null
        activeToken = null
    }

    private fun fail(
        audioAssetId: String,
        errorCode: AudioAssetPlaybackErrorCode,
    ): AudioAssetPlaybackResult.Failure {
        state = AudioAssetPlaybackState.Failed(
            audioAssetId = audioAssetId,
            errorCode = errorCode,
        )
        return AudioAssetPlaybackResult.Failure(errorCode)
    }
}
