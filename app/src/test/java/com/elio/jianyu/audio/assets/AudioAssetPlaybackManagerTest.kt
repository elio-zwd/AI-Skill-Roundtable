package com.elio.jianyu.audio.assets

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioAssetPlaybackManagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun playPauseResumeAndStopUseAudioAssetIdentity() {
        val fixture = fixtureWithAudio("asset-1")

        assertEquals(
            AudioAssetPlaybackResult.STARTED,
            fixture.manager.play("asset-1", fixture.relativePath),
        )
        assertEquals(AudioAssetPlaybackState.Playing("asset-1"), fixture.manager.state)
        assertEquals(1, fixture.player.startCount)

        assertEquals(AudioAssetPlaybackResult.PAUSED, fixture.manager.pause())
        assertEquals(AudioAssetPlaybackState.Paused("asset-1"), fixture.manager.state)
        assertEquals(1, fixture.player.pauseCount)

        assertEquals(AudioAssetPlaybackResult.RESUMED, fixture.manager.resume())
        assertEquals(AudioAssetPlaybackState.Playing("asset-1"), fixture.manager.state)
        assertEquals(2, fixture.player.startCount)

        assertEquals(AudioAssetPlaybackResult.STOPPED, fixture.manager.stop())
        assertEquals(AudioAssetPlaybackState.Idle, fixture.manager.state)
        assertEquals(1, fixture.player.stopCount)
        assertEquals(1, fixture.player.releaseCount)
    }

    @Test
    fun switchingAssetsStopsAndReleasesPreviousPlayer() {
        val root = temporaryFolder.newFolder("switch-root")
        val store = AudioFileStore(root)
        val firstPath = committedWav(store, "asset-1")
        val secondPath = committedWav(store, "asset-2")
        val players = mutableListOf<FakeAudioAssetPlayer>()
        val manager = AudioAssetPlaybackManager(
            fileStore = store,
            playerFactory = AudioAssetPlayerFactory { _, onCompletion, onError ->
                FakeAudioAssetPlayer(onCompletion, onError).also(players::add)
            },
        )

        manager.play("asset-1", firstPath)
        manager.play("asset-2", secondPath)

        assertEquals(2, players.size)
        assertEquals(1, players[0].stopCount)
        assertEquals(1, players[0].releaseCount)
        assertEquals(AudioAssetPlaybackState.Playing("asset-2"), manager.state)
        assertEquals(1, players[1].startCount)
    }

    @Test
    fun missingOrRejectedFileFailsWithoutCreatingPlayerOrNetworking() {
        val root = temporaryFolder.newFolder("missing-root")
        val store = AudioFileStore(root)
        var playerCreated = false
        val manager = AudioAssetPlaybackManager(
            fileStore = store,
            playerFactory = AudioAssetPlayerFactory { _, _, _ ->
                playerCreated = true
                error("不应创建播放器")
            },
        )

        assertEquals(
            AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.FILE_MISSING),
            manager.play("asset-missing", "missing.wav"),
        )
        assertEquals(
            AudioAssetPlaybackState.Failed(
                audioAssetId = "asset-missing",
                errorCode = AudioAssetPlaybackErrorCode.FILE_MISSING,
            ),
            manager.state,
        )
        assertFalse(playerCreated)

        assertEquals(
            AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.PATH_REJECTED),
            manager.play("asset-rejected", "../escape.wav"),
        )
        assertFalse(playerCreated)
    }

    @Test
    fun completionAndPlayerErrorOnlyAffectCurrentAudioAsset() {
        val root = temporaryFolder.newFolder("callback-root")
        val store = AudioFileStore(root)
        val firstPath = committedWav(store, "asset-1")
        val secondPath = committedWav(store, "asset-2")
        val players = mutableListOf<FakeAudioAssetPlayer>()
        val manager = AudioAssetPlaybackManager(
            fileStore = store,
            playerFactory = AudioAssetPlayerFactory { _, onCompletion, onError ->
                FakeAudioAssetPlayer(onCompletion, onError).also(players::add)
            },
        )

        manager.play("asset-1", firstPath)
        manager.play("asset-2", secondPath)
        players[0].complete()
        assertEquals(AudioAssetPlaybackState.Playing("asset-2"), manager.state)

        players[1].fail()
        assertEquals(
            AudioAssetPlaybackState.Failed(
                audioAssetId = "asset-2",
                errorCode = AudioAssetPlaybackErrorCode.PLAYER_FAILURE,
            ),
            manager.state,
        )
        assertEquals(1, players[1].releaseCount)
    }

    @Test
    fun releaseIsIdempotentAndPreventsNewPlayback() {
        val fixture = fixtureWithAudio("asset-release")
        fixture.manager.play("asset-release", fixture.relativePath)

        fixture.manager.release()
        fixture.manager.release()

        assertEquals(AudioAssetPlaybackState.Released, fixture.manager.state)
        assertEquals(1, fixture.player.stopCount)
        assertEquals(1, fixture.player.releaseCount)
        assertEquals(
            AudioAssetPlaybackResult.Failure(AudioAssetPlaybackErrorCode.RELEASED),
            fixture.manager.play("asset-release", fixture.relativePath),
        )
    }

    private fun fixtureWithAudio(audioAssetId: String): PlaybackFixture {
        val root = temporaryFolder.newFolder("playback-$audioAssetId")
        val store = AudioFileStore(root)
        val path = committedWav(store, audioAssetId)
        lateinit var player: FakeAudioAssetPlayer
        val manager = AudioAssetPlaybackManager(
            fileStore = store,
            playerFactory = AudioAssetPlayerFactory { _, onCompletion, onError ->
                FakeAudioAssetPlayer(onCompletion, onError).also { player = it }
            },
        )
        manager.play(audioAssetId, path)
        manager.stop()
        return PlaybackFixture(
            manager = AudioAssetPlaybackManager(
                fileStore = store,
                playerFactory = AudioAssetPlayerFactory { _, onCompletion, onError ->
                    FakeAudioAssetPlayer(onCompletion, onError).also { player = it }
                },
            ),
            playerProvider = { player },
            relativePath = path,
        )
    }

    private fun committedWav(
        store: AudioFileStore,
        audioAssetId: String,
    ): String {
        val target = store.createPendingTarget(audioAssetId, AudioTargetFormat.WAV)
        store.openPendingWriter(target).use { it.write(validWavBytes()) }
        return (store.commit(target) as AudioFileCommitResult.Success).file.relativePath
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

    private data class PlaybackFixture(
        val manager: AudioAssetPlaybackManager,
        val playerProvider: () -> FakeAudioAssetPlayer,
        val relativePath: String,
    ) {
        val player: FakeAudioAssetPlayer
            get() = playerProvider()
    }

    private class FakeAudioAssetPlayer(
        private val onCompletion: () -> Unit,
        private val onError: (Throwable) -> Unit,
    ) : AudioAssetPlayer {
        var startCount: Int = 0
        var pauseCount: Int = 0
        var stopCount: Int = 0
        var releaseCount: Int = 0

        override fun start() {
            startCount += 1
        }

        override fun pause() {
            pauseCount += 1
        }

        override fun stop() {
            stopCount += 1
        }

        override fun release() {
            releaseCount += 1
        }

        fun complete() {
            onCompletion()
        }

        fun fail() {
            onError(IllegalStateException("simulated-player-failure"))
        }
    }
}
