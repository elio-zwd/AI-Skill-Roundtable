package com.elio.jianyu.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.runtime.DatabaseMaintenanceStage
import com.elio.jianyu.runtime.JianyuRuntimeState
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeMaintenanceHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun maintenanceStateShowsBlockingStatusWithoutNormalAppContent() {
        composeRule.setContent {
            SkillRoundtableTheme {
                JianyuRuntimeStatusContent(
                    state = JianyuRuntimeState.Maintenance(generation = 3L),
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(JianyuAutomationTags.App.RUNTIME_MAINTENANCE)
            .assertExists()
        composeRule
            .onNodeWithTag(JianyuAutomationTags.App.CONTENT_ROOT)
            .assertDoesNotExist()
        composeRule
            .onNodeWithTag(JianyuAutomationTags.App.RUNTIME_RETRY)
            .assertDoesNotExist()
    }

    @Test
    fun unavailableStateShowsStableRetryAction() {
        var retryRequested = false
        composeRule.setContent {
            SkillRoundtableTheme {
                JianyuRuntimeStatusContent(
                    state = JianyuRuntimeState.Unavailable(
                        generation = 4L,
                        stage = DatabaseMaintenanceStage.REOPEN,
                    ),
                    onRetry = { retryRequested = true },
                )
            }
        }

        composeRule
            .onNodeWithTag(JianyuAutomationTags.App.RUNTIME_UNAVAILABLE)
            .assertExists()
        composeRule
            .onNodeWithTag(JianyuAutomationTags.App.RUNTIME_RETRY)
            .assertExists()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(retryRequested)
        }
    }
}
