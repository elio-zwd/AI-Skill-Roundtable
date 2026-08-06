package com.elio.jianyu.ui.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuLifecycleAutomationTagsTest {
    private val staticTagPattern = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")

    @Test
    fun lifecycleStaticTagsAreUniqueAsciiAndCentralized() {
        val tags = JianyuAutomationTags.Lifecycle.frozenStaticTags

        assertTrue(tags.isNotEmpty())
        assertEquals(tags.size, tags.distinct().size)
        tags.forEach { tag ->
            assertTrue(tag.all { it.code in 0x21..0x7e })
            assertTrue("标签必须使用 lower_snake_case：$tag", staticTagPattern.matches(tag))
        }
    }

    @Test
    fun dynamicLifecycleTagsOnlyAcceptStableIds() {
        assertEquals(
            "archive_event_archive-1",
            JianyuAutomationTags.Lifecycle.archiveEvent("archive-1"),
        )
        assertEquals(
            "resume_event_resume-1",
            JianyuAutomationTags.Lifecycle.resumeEvent("resume-1"),
        )
        assertEquals(
            "issue_relation_relation-1",
            JianyuAutomationTags.Lifecycle.issueRelation("relation-1"),
        )
        assertEquals(
            "purge_operation_purge-1",
            JianyuAutomationTags.Lifecycle.purgeOperation("purge-1"),
        )
        assertEquals(
            "purge_audio_asset_audio-1",
            JianyuAutomationTags.Lifecycle.purgeAudioAsset("audio-1"),
        )

        listOf(
            "用户标题",
            "archive summary",
            "/data/user/0/com.elio.jianyu/files/audio.wav",
            "a".repeat(129),
        ).forEach { content ->
            assertThrows(IllegalArgumentException::class.java) {
                JianyuAutomationTags.Lifecycle.archiveEvent(content)
            }
        }
    }
}
