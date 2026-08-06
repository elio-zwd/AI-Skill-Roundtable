package com.elio.jianyu.ui.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuAudioAutomationTagsTest {
    @Test
    fun staticAudioTagsAreUniqueAndStable() {
        assertEquals(
            JianyuAudioAutomationTags.frozenStaticTags.size,
            JianyuAudioAutomationTags.frozenStaticTags.toSet().size,
        )
        JianyuAudioAutomationTags.frozenStaticTags.forEach { tag ->
            assertTrue(tag.startsWith("audio_"))
            assertFalse(tag.any(Char::isWhitespace))
        }
    }

    @Test
    fun dynamicAudioTagsOnlyEmbedStableInternalIds() {
        assertEquals(
            "audio_message_generate_42",
            JianyuAudioAutomationTags.messageGenerate(42L),
        )
        assertEquals(
            "audio_artifact_generate_artifact-1",
            JianyuAudioAutomationTags.artifactGenerate("artifact-1"),
        )
        assertEquals(
            "audio_asset_play_asset-1",
            JianyuAudioAutomationTags.assetPlay("asset-1"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun dynamicAudioTagRejectsUserContent() {
        JianyuAudioAutomationTags.artifactGenerate("包含 用户正文")
    }
}
