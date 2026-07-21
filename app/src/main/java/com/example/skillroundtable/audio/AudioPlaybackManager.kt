package com.example.skillroundtable.audio

import android.content.Context
import android.media.MediaPlayer
import com.example.skillroundtable.telemetry.PrivacySafeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

object AudioPlaybackManager {
    private const val TAG = "AudioPlaybackManager"
    private var mediaPlayer: MediaPlayer? = null

    private val _currentPlayingMessageId = MutableStateFlow<Long?>(null)
    val currentPlayingMessageId: StateFlow<Long?> = _currentPlayingMessageId

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    fun playAudio(context: Context, messageId: Long, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            PrivacySafeLogger.e(TAG, "Audio file does not exist")
            return
        }

        try {
            if (_currentPlayingMessageId.value == messageId && _isPlaying.value) {
                pauseAudio()
                return
            }

            if (_currentPlayingMessageId.value == messageId && mediaPlayer != null) {
                mediaPlayer?.start()
                _isPlaying.value = true
                return
            }

            stopAudio()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnPreparedListener { player ->
                    if (mediaPlayer == player) {
                        player.start()
                        _isPlaying.value = true
                    }
                }
                setOnCompletionListener { stopAudio() }
                prepareAsync()
            }
            _currentPlayingMessageId.value = messageId
            PrivacySafeLogger.d(TAG, "Audio playback started")
        } catch (error: Exception) {
            PrivacySafeLogger.e(TAG, "Audio playback failed", error)
            stopAudio()
        }
    }

    fun pauseAudio() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                PrivacySafeLogger.d(TAG, "Audio playback paused")
            }
        } catch (error: Exception) {
            PrivacySafeLogger.e(TAG, "Audio pause failed", error)
        }
    }

    fun stopAudio() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        try {
            if (player.isPlaying) player.stop()
            player.release()
        } catch (error: Exception) {
            PrivacySafeLogger.e(TAG, "MediaPlayer release failed", error)
        } finally {
            _currentPlayingMessageId.value = null
            _isPlaying.value = false
            PrivacySafeLogger.d(TAG, "Audio playback stopped")
        }
    }
}
