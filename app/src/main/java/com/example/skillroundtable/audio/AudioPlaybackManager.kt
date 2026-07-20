package com.example.skillroundtable.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
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
            Log.e(TAG, "音频文件不存在: $filePath")
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
                prepare()
                start()
                setOnCompletionListener {
                    stopAudio()
                }
            }
            _currentPlayingMessageId.value = messageId
            _isPlaying.value = true
            Log.d(TAG, "开始播放音频 messageId: $messageId, file: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "播放音频出错", e)
            stopAudio()
        }
    }

    fun pauseAudio() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                _isPlaying.value = false
                Log.d(TAG, "音频已暂停")
            }
        } catch (e: Exception) {
            Log.e(TAG, "暂停音频失败", e)
        }
    }

    fun stopAudio() {
        val player = mediaPlayer ?: return
        mediaPlayer = null  // 立即将引用置空，切断重入路径
        try {
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        } catch (e: Exception) {
            Log.e(TAG, "释放 MediaPlayer 失败", e)
        } finally {
            _currentPlayingMessageId.value = null
            _isPlaying.value = false
            Log.d(TAG, "音频已停止并释放")
        }
    }
}
