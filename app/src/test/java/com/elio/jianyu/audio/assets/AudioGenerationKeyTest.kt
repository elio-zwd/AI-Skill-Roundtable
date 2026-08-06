package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState
import com.elio.jianyu.data.ResourceLifecycleConverters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGenerationKeyTest {
    @Test
    fun sameStableInputsProduceSameKeyWithoutLeakingSourceContent() {
        val source = AudioAssetSource.CompletedMessage(
            issueId = "issue-1",
            stageId = "stage-1",
            contentHash = "sha256-content",
            messageId = 42L,
        )
        val config = AudioGenerationConfig(
            voiceProfileId = "jianyu-default",
            targetFormat = AudioTargetFormat.WAV,
            parameterVersion = 1,
        )

        val first = AudioGenerationKeyFactory.create(source, config)
        val second = AudioGenerationKeyFactory.create(source, config)

        assertEquals(first, second)
        assertTrue(first.matches(Regex("audio:v1:[0-9a-f]{64}")))
        assertFalse(first.contains("sha256-content"))
        assertFalse(first.contains("issue-1"))
        assertFalse(first.contains("stage-1"))
    }

    @Test
    fun sourceTypeStableIdAndConfigAllParticipateInKey() {
        val message = AudioAssetSource.CompletedMessage(
            issueId = "issue-1",
            stageId = "stage-1",
            contentHash = "same-content",
            messageId = 7L,
        )
        val artifact = AudioAssetSource.ConfirmedArtifact(
            issueId = "issue-1",
            stageId = "stage-1",
            contentHash = "same-content",
            artifactId = "7",
        )
        val wav = AudioGenerationConfig(
            voiceProfileId = "jianyu-default",
            targetFormat = AudioTargetFormat.WAV,
            parameterVersion = 1,
        )
        val aac = wav.copy(targetFormat = AudioTargetFormat.AAC_ADTS)
        val newParameters = wav.copy(parameterVersion = 2)

        assertNotEquals(
            AudioGenerationKeyFactory.create(message, wav),
            AudioGenerationKeyFactory.create(artifact, wav),
        )
        assertNotEquals(
            AudioGenerationKeyFactory.create(message, wav),
            AudioGenerationKeyFactory.create(message.copy(messageId = 8L), wav),
        )
        assertNotEquals(
            AudioGenerationKeyFactory.create(message, wav),
            AudioGenerationKeyFactory.create(message, aac),
        )
        assertNotEquals(
            AudioGenerationKeyFactory.create(message, wav),
            AudioGenerationKeyFactory.create(message, newParameters),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankVoiceProfileIsRejectedBeforeKeyCreation() {
        AudioGenerationKeyFactory.create(
            source = AudioAssetSource.ConfirmedArtifact(
                issueId = "issue-1",
                stageId = "stage-1",
                contentHash = "hash",
                artifactId = "artifact-1",
            ),
            config = AudioGenerationConfig(
                voiceProfileId = " ",
                targetFormat = AudioTargetFormat.WAV,
                parameterVersion = 1,
            ),
        )
    }
}

class AudioFileStateConverterTest {
    private val converters = ResourceLifecycleConverters()

    @Test
    fun canceledStateRoundTripsWithoutChangingExistingValues() {
        val existing = listOf(
            AudioFileState.PENDING,
            AudioFileState.AVAILABLE,
            AudioFileState.MISSING,
            AudioFileState.FAILED,
        )

        existing.forEach { state ->
            assertEquals(
                state,
                converters.storageToAudioFileState(
                    converters.audioFileStateToStorage(state),
                ),
            )
        }
        assertEquals("canceled", converters.audioFileStateToStorage(AudioFileState.CANCELED))
        assertEquals(AudioFileState.CANCELED, converters.storageToAudioFileState("canceled"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownAudioStateRemainsRejected() {
        converters.storageToAudioFileState("unknown-future-state")
    }
}
