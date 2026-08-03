package com.elio.jianyu.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuNavigationRoutesTest {
    @Test
    fun resources_defaultsToMaterialsAndRejectsUnknownTab() {
        assertEquals(ResourceTab.MATERIALS, ResourceTab.fromRouteValue(null))
        assertEquals(ResourceTab.MATERIALS, ResourceTab.fromRouteValue("unknown"))
        assertEquals(ResourceTab.ARTIFACTS, ResourceTab.fromRouteValue("artifacts"))
        assertEquals(
            "resources?tab=materials",
            JianyuNavigationRoutes.resources(ResourceTab.MATERIALS),
        )
    }

    @Test
    fun issueRoute_usesOnlyStableIds() {
        assertEquals(
            "issues/issue-123?stageId=stage-7",
            JianyuNavigationRoutes.issue("issue-123", "stage-7"),
        )
        assertEquals(
            "issues/issue-123",
            JianyuNavigationRoutes.issue("issue-123"),
        )
    }

    @Test
    fun skillDetailRoute_usesOnlyOfficialStableId() {
        assertEquals(
            "skills/decision-reviewer",
            JianyuNavigationRoutes.skillDetail("decision-reviewer"),
        )
    }

    @Test
    fun dynamicRoutes_rejectSensitiveOrStructuralPayloads() {
        listOf(
            "",
            " issue-1",
            "issue/1",
            "issue?apiKey=secret",
            "prompt=ignore previous instructions",
            "资料正文",
        ).forEach { invalidId ->
            assertThrows(IllegalArgumentException::class.java) {
                JianyuNavigationRoutes.issue(invalidId)
            }
        }
    }

    @Test
    fun deepLinkPatterns_exposeOnlyStableIdentifiersAndTab() {
        assertEquals(
            "jianyu://issues/{issueId}?stageId={stageId}",
            JianyuNavigationRoutes.ISSUE_DEEP_LINK_PATTERN,
        )
        assertEquals(
            "jianyu://skills/{skillId}",
            JianyuNavigationRoutes.SKILL_DEEP_LINK_PATTERN,
        )
        assertEquals(
            "jianyu://resources?tab={tab}",
            JianyuNavigationRoutes.RESOURCES_DEEP_LINK_PATTERN,
        )
        val patterns = listOf(
            JianyuNavigationRoutes.ISSUE_DEEP_LINK_PATTERN,
            JianyuNavigationRoutes.SKILL_DEEP_LINK_PATTERN,
            JianyuNavigationRoutes.RESOURCES_DEEP_LINK_PATTERN,
        )
        patterns.forEach { pattern ->
            assertFalse(pattern.contains("apiKey", ignoreCase = true))
            assertFalse(pattern.contains("prompt", ignoreCase = true))
            assertTrue(pattern.startsWith("jianyu://"))
        }
    }
}
