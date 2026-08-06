package com.elio.jianyu.audio.playback

import android.media.MediaPlayer
import com.elio.jianyu.audio.assets.AudioAssetPlayer
import com.elio.jianyu.audio.assets.AudioAssetPlayerFactory
import java.io.File

/** 使用 Android MediaPlayer 播放 App 私有受控文件。 */
class AndroidAudioAssetPlayerFactory : AudioAssetPlayerFactory {
    override fun create(
        file: File,
        onCompletion: () -> Unit,
        onError: (Throwable) -> Unit,
    ): AudioAssetPlayer {
        val mediaPlayer = MediaPlayer()
        try {
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnCompletionListener { onCompletion() }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                onError(IllegalStateException("MediaPlayer failure ($what/$extra)"))
                true
            }
            mediaPlayer.prepare()
        } catch (error: Throwable) {
            runCatching { mediaPlayer.release() }
            throw error
        }
        return AndroidAudioAssetPlayer(mediaPlayer)
    }
}

private class AndroidAudioAssetPlayer(
    private val player: MediaPlayer,
) : AudioAssetPlayer {
    override fun start() = player.start()
    override fun pause() = player.pause()
    override fun stop() = player.stop()
    override fun release() = player.release()
}
