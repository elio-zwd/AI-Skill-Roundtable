package com.elio.jianyu.audio.assets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioAssetPlaybackSwitchFailureTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun switchingToMissingAssetStopsPreviousPlaybackBeforeReportingFailure() {
        val root = temporaryFolder.newFolder("switch-missing-root")
        val fileStore = AudioFileStore(root)
        val availablePath = commitWav(fileStore, "asset-playing")
        val players = mutableListOf<RecordingPlayer>()
        var attemptedNetworkFallback = false
        val manager = AudioAssetPlaybackManager(
            fileStore = fileStore,
            playerFactory = AudioAssetPlayerFactory { _, _, _ ->
                RecordingPlayer().also(players::add)
            },
        )

        assertEquals(
            AudioAssetPlaybackResult.STARTED,
            manager.play("asset-playing", availablePath),
        )
        assertEquals(1, players.size)

        val result = manager.play("asset-missing", "missing.wav")

        assertEquals(
            AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.FILE_MISSING),
            result,
        )
        assertEquals(1, players.size)
        assertEquals(1, players.single().stopCount)
        assertEquals(1, players.single().releaseCount)
        assertEquals(
            AudioAssetPlaybackState.Failed(
                audioAssetId = "asset-missing",
                errorCode = AudioAssetPlaybackErrorCode.FILE_MISSING,
            ),
            manager.state,
        )
        assertFalse(attemptedNetworkFallback)
    }

    private fun commitWav(
        fileStore: AudioFileStore,
        audioAssetId: String,
    ): String {
        val target = fileStore.createPendingTarget(audioAssetId, AudioTargetFormat.WAV)
        fileStore.openPendingWriter(target).use { writer ->
            writer.write(validWavBytes())
        }
        return (fileStore.commit(target) as AudioFileCommitResult.Success).file.relativePath
    }

    private fun validWavBytes(): ByteArray {
        val bytes = ByteArray(48)
        "RIFF".toByteArray().copyInto(bytes, destinationOffset = 0)
        "WAVE".toByteArray().copyInto(bytes, destinationOffset = 8)
        "fmt ".toByteArray().copyInto(bytes, destinationOffset = 12)
        "data".toByteArray().copyInto(bytes, destinationOffset = 36)
        bytes[44] = 1
        bytes[45] = 2
        bytes[46] = 3
        bytes[47] = 4
        return bytes
    }

    private class RecordingPlayer : AudioAssetPlayer {
        var stopCount: Int = 0
        var releaseCount: Int = 0

        override fun start() = Unit

        override fun pause() = Unit

        override fun stop() {
            stopCount += 1
        }

        override fun release() {
            releaseCount += 1
        }
    }
}
