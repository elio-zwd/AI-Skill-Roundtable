package com.elio.jianyu.audio.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGenerationWorkPolicyTest {
    @Test
    fun sameGenerationKeyProducesSameOpaqueUniqueWorkName() {
        val generationKey = "audio:v1:${"a".repeat(64)}"

        val first = AudioGenerationWorkPolicy.plan(
            audioAssetId = "asset-1",
            generationKey = generationKey,
            requestKind = AudioWorkRequestKind.INITIAL,
        )
        val second = AudioGenerationWorkPolicy.plan(
            audioAssetId = "asset-1",
            generationKey = generationKey,
            requestKind = AudioWorkRequestKind.INITIAL,
        )
        val different = AudioGenerationWorkPolicy.plan(
            audioAssetId = "asset-1",
            generationKey = "audio:v1:${"b".repeat(64)}",
            requestKind = AudioWorkRequestKind.INITIAL,
        )

        assertEquals(first.uniqueWorkName, second.uniqueWorkName)
        assertNotEquals(first.uniqueWorkName, different.uniqueWorkName)
        assertTrue(first.uniqueWorkName.matches(Regex("audio-generation:[0-9a-f]{64}")))
        assertFalse(first.uniqueWorkName.contains(generationKey))
        assertFalse(first.uniqueWorkName.contains("asset-1"))
    }

    @Test
    fun initialAndExplicitRetryUseDifferentExistingWorkPolicies() {
        val initial = AudioGenerationWorkPolicy.plan(
            audioAssetId = "asset-1",
            generationKey = "audio:v1:${"a".repeat(64)}",
            requestKind = AudioWorkRequestKind.INITIAL,
        )
        val retry = AudioGenerationWorkPolicy.plan(
            audioAssetId = "asset-1",
            generationKey = "audio:v1:${"a".repeat(64)}",
            requestKind = AudioWorkRequestKind.EXPLICIT_RETRY,
        )

        assertEquals(AudioExistingWorkPolicy.KEEP, initial.existingWorkPolicy)
        assertEquals(AudioExistingWorkPolicy.REPLACE, retry.existingWorkPolicy)
    }

    @Test
    fun workerInputContainsOnlyStableInternalAudioAssetId() {
        val plan = AudioGenerationWorkPolicy.plan(
            audioAssetId = "asset-42",
            generationKey = "audio:v1:${"c".repeat(64)}",
            requestKind = AudioWorkRequestKind.INITIAL,
        )

        assertEquals(
            mapOf(AudioGenerationWorkPolicy.AUDIO_ASSET_ID_KEY to "asset-42"),
            plan.inputData,
        )
        assertEquals(setOf("audio_asset_id"), plan.inputData.keys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedGenerationKeyIsRejected() {
        AudioGenerationWorkPolicy.plan(
            audioAssetId = "asset-1",
            generationKey = "not-a-generation-key",
            requestKind = AudioWorkRequestKind.INITIAL,
        )
    }
}
