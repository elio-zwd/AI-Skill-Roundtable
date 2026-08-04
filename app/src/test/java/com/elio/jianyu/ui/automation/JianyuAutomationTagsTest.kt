package com.elio.jianyu.ui.automation

import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.ui.AppTestTags
import com.elio.jianyu.ui.components.JianyuShellTestTags
import com.elio.jianyu.ui.navigation.AppDestination
import com.elio.jianyu.ui.screens.context.ContextConfirmationTestTags
import com.elio.jianyu.ui.screens.execution.IssueExecutionTestTags
import com.elio.jianyu.ui.screens.home.HomeTestTags
import com.elio.jianyu.ui.screens.issues.IssuesTestTags
import com.elio.jianyu.ui.screens.resources.ResourcesTestTags
import com.elio.jianyu.ui.screens.settings.SettingsShellTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuAutomationTagsTest {
    private val staticTagPattern = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")

    @Test
    fun frozenStaticTags_areUniqueAsciiAndFollowNamingConvention() {
        val tags = JianyuAutomationTags.frozenStaticTags

        assertTrue(tags.isNotEmpty())
        assertEquals("冻结标签存在重复项", tags.size, tags.distinct().size)
        tags.forEach { tag ->
            assertTrue("标签不能为空：$tag", tag.isNotBlank())
            assertTrue("标签必须是可打印 ASCII：$tag", tag.all { it.code in 0x21..0x7e })
            assertTrue("标签必须使用 lower_snake_case：$tag", staticTagPattern.matches(tag))
        }
    }

    @Test
    fun legacyTags_remainCompatibleWithCentralContract() {
        assertEquals(JianyuAutomationTags.App.BOTTOM_NAVIGATION, AppTestTags.BOTTOM_NAVIGATION)
        assertEquals(
            JianyuAutomationTags.Navigation.HOME,
            AppTestTags.destination(AppDestination.HOME),
        )
        assertEquals(JianyuAutomationTags.Screen.HOME, HomeTestTags.SCREEN)
        assertEquals(
            JianyuAutomationTags.Shell.GLOBAL_SETTINGS_BUTTON,
            JianyuShellTestTags.GLOBAL_SETTINGS_BUTTON,
        )
        assertEquals(JianyuAutomationTags.Screen.ISSUES, IssuesTestTags.SCREEN)
        assertEquals(
            JianyuAutomationTags.Issues.issue("issue-42"),
            IssuesTestTags.issue("issue-42"),
        )
        assertEquals(JianyuAutomationTags.Screen.RESOURCES, ResourcesTestTags.SCREEN)
        assertEquals(
            JianyuAutomationTags.Resources.material("material-42"),
            ResourcesTestTags.material("material-42"),
        )
        assertEquals(
            JianyuAutomationTags.Screen.ISSUE_EXECUTION,
            IssueExecutionTestTags.SCREEN,
        )
        assertEquals(
            JianyuAutomationTags.Execution.participant("snapshot-42"),
            IssueExecutionTestTags.participant("snapshot-42"),
        )
        assertEquals(
            JianyuAutomationTags.Context.candidate("material", "material-42"),
            ContextConfirmationTestTags.candidate(ContextSourceType.MATERIAL, "material-42"),
        )
        assertEquals(JianyuAutomationTags.Screen.SETTINGS, SettingsShellTestTags.SCREEN)
    }

    @Test
    fun temporaryHomePlaceholder_isNotFrozen() {
        assertFalse(
            JianyuAutomationTags.frozenStaticTags.contains(HomeTestTags.QUESTION_PLACEHOLDER),
        )
    }

    @Test
    fun dynamicTags_useExplicitStableIdsAndRejectUserContent() {
        assertEquals(
            "issue_navigation_issue-42",
            JianyuAutomationTags.Issues.issue("issue-42"),
        )
        assertNotEquals(
            JianyuAutomationTags.Resources.material("same-id"),
            JianyuAutomationTags.Resources.personalContext("same-id"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            JianyuAutomationTags.normalizedStableId("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JianyuAutomationTags.normalizedStableId("用户输入")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JianyuAutomationTags.normalizedStableId("private title")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JianyuAutomationTags.normalizedStableId("a".repeat(129))
        }
    }
}
