package com.elio.jianyu.ui.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvanceIssueAutomationTagsTest {
    @Test
    fun advancementAndTimelineTagsAreFrozenAndUnique() {
        val required = listOf(
            JianyuAutomationTags.AdvanceIssue.BUTTON,
            JianyuAutomationTags.StageTimeline.TIMELINE,
            JianyuAutomationTags.StageTimeline.CURRENT,
            JianyuAutomationTags.AdvanceIssue.DIALOG,
            JianyuAutomationTags.AdvanceIssue.DIRECTION_STEP,
            JianyuAutomationTags.AdvanceIssue.DIRECTION_REALITY_SUPPORT,
            JianyuAutomationTags.AdvanceIssue.DIRECTION_THINKING_EXPANSION,
            JianyuAutomationTags.AdvanceIssue.MEASURE_STEP,
            JianyuAutomationTags.AdvanceIssue.CUSTOM_OBJECTIVE,
            JianyuAutomationTags.AdvanceIssue.SUMMARY_STEP,
            JianyuAutomationTags.AdvanceIssue.INHERITED_MATERIALS,
            JianyuAutomationTags.AdvanceIssue.INHERITED_ARTIFACTS,
            JianyuAutomationTags.AdvanceIssue.ROSTER,
            JianyuAutomationTags.AdvanceIssue.EXPECTED_OUTPUT,
            JianyuAutomationTags.AdvanceIssue.CONFIRM,
            JianyuAutomationTags.AdvanceIssue.CANCEL,
            JianyuAutomationTags.AdvanceIssue.WAIT_FOR_RUN,
            JianyuAutomationTags.AdvanceIssue.STOP_CURRENT_RUN,
            JianyuAutomationTags.AdvanceIssue.FAILURE,
            JianyuAutomationTags.StageTimeline.UNDO_BUTTON,
            JianyuAutomationTags.StageTimeline.UNDO_CONFIRMATION,
        )

        assertEquals(required.size, required.distinct().size)
        assertTrue(JianyuAutomationTags.frozenStaticTags.containsAll(required))
    }

    @Test
    fun dynamicTagsUseOnlyNormalizedStableIds() {
        assertEquals(
            "stage_timeline_item_stage-2",
            JianyuAutomationTags.StageTimeline.item("stage-2"),
        )
        assertEquals(
            "advance_roster_member_skill.a",
            JianyuAutomationTags.AdvanceIssue.rosterMember("skill.a"),
        )
        assertEquals(
            "advance_material_material_1",
            JianyuAutomationTags.AdvanceIssue.material("material_1"),
        )
        assertEquals(
            "advance_artifact_artifact-1",
            JianyuAutomationTags.AdvanceIssue.artifact("artifact-1"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            JianyuAutomationTags.StageTimeline.item("用户输入标题")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JianyuAutomationTags.AdvanceIssue.material("material id")
        }
    }
}
