package com.elio.jianyu.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.home.HomeWorkflow
import com.elio.jianyu.home.HomeWorkflowIds
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuShellTestTags
import com.elio.jianyu.ui.navigation.ResourceTab
import com.elio.jianyu.ui.screens.home.HomeScreen
import com.elio.jianyu.ui.screens.home.HomeTestTags
import com.elio.jianyu.ui.screens.home.HomeUiState
import com.elio.jianyu.ui.screens.issues.IssueNavigationUiItem
import com.elio.jianyu.ui.screens.issues.IssuesScreen
import com.elio.jianyu.ui.screens.issues.IssuesTestTags
import com.elio.jianyu.ui.screens.issues.IssuesUiState
import com.elio.jianyu.ui.screens.resources.ResourcesScreen
import com.elio.jianyu.ui.screens.resources.ResourcesTestTags
import com.elio.jianyu.ui.screens.settings.SettingsScreen
import com.elio.jianyu.ui.screens.settings.SettingsShellTestTags
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JianyuNavigationShellScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_exposesGlobalSettingsAndRealQuestionInput() {
        var settingsOpened = false
        val ids = HomeWorkflowIds(
            workflowId = "navigation-home-workflow",
            issueId = "navigation-home-issue",
            stageId = "navigation-home-stage",
            runId = "navigation-home-run",
            saveIssueIdempotencyKey = "navigation-home-save",
            executionIdempotencyKey = "navigation-home-execute",
        )
        composeRule.setContent {
            SkillRoundtableTheme {
                HomeScreen(
                    uiState = HomeUiState(HomeWorkflow.initial(ids)),
                    onQuestionChanged = {},
                    onClearQuestion = {},
                    onToggleDirection = {},
                    onUseExample = {},
                    onRequestRecommendation = {},
                    onSaveIssueOnly = {},
                    onToggleSkill = {},
                    onResponsibilityChanged = { _, _ -> },
                    onMoveSkill = { _, _ -> },
                    onModeChanged = {},
                    onConfirmRecommendation = {},
                    onOpenContextConfirmation = {},
                    onNetworkAuthorized = {},
                    onHighStakesConfirmed = {},
                    onPersonDisclaimerConfirmed = {},
                    onBrowseSkills = {},
                    onStartIssue = {},
                    onOpenSettings = { settingsOpened = true },
                )
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.SCREEN).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.QUESTION_INPUT).assertExists()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Home.RECOMMENDATION_REQUEST_BUTTON,
        ).assertExists()
        composeRule.onNodeWithTag(JianyuShellTestTags.GLOBAL_SETTINGS_BUTTON).performClick()
        composeRule.runOnIdle { assertTrue(settingsOpened) }
    }

    @Test
    fun issues_contentShowsLifecycleSectionsAndUsesStableIds() {
        var openedIssue: Pair<String, String?>? = null
        val item = IssueNavigationUiItem(
            issueId = "issue-42",
            title = "验证导航",
            lifecycleState = IssueLifecycleState.ARCHIVED,
            currentStageId = "stage-3",
            currentStageTitle = "阶段三",
            activeOrRecoverableRunCount = 2,
            updatedAt = 100L,
        )
        composeRule.setContent {
            SkillRoundtableTheme {
                IssuesScreen(
                    state = IssuesUiState.Content(
                        active = emptyList(),
                        archived = listOf(item),
                        trashed = emptyList(),
                    ),
                    onRetry = {},
                    onOpenIssue = { issueId, stageId ->
                        openedIssue = issueId to stageId
                    },
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(IssuesTestTags.ACTIVE_SECTION).assertExists()
        composeRule.onNodeWithText("已归档").performClick()
        composeRule.onNodeWithTag(IssuesTestTags.ARCHIVED_SECTION).assertExists()
        composeRule.onNodeWithText("回收站").performClick()
        composeRule.onNodeWithTag(IssuesTestTags.TRASHED_SECTION).assertExists()
        composeRule.onNodeWithText("已归档").performClick()
        composeRule.onNodeWithTag(IssuesTestTags.issue("issue-42")).performClick()
        composeRule.runOnIdle {
            assertEquals("issue-42" to "stage-3", openedIssue)
        }
    }

    @Test
    fun resources_switchesBetweenMaterialsAndArtifacts() {
        val selectedTab = mutableStateOf(ResourceTab.MATERIALS)
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = selectedTab.value,
                    onSelectTab = { selectedTab.value = it },
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesTestTags.MATERIALS_TAB).assertIsSelected()
        composeRule.onNodeWithTag(ResourcesTestTags.ARTIFACTS_TAB).performClick()
        composeRule.onNodeWithTag(ResourcesTestTags.ARTIFACTS_TAB).assertIsSelected()
        composeRule.runOnIdle {
            assertEquals(ResourceTab.ARTIFACTS, selectedTab.value)
        }
    }

    @Test
    fun settings_preservesAiManagementTelemetryAndBackCallbacks() {
        var backCount = 0
        var apiKeyCount = 0
        var telemetryCount = 0
        composeRule.setContent {
            SkillRoundtableTheme {
                SettingsScreen(
                    onBack = { backCount += 1 },
                    onOpenAiManagement = { apiKeyCount += 1 },
                    onOpenTelemetry = { telemetryCount += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(SettingsShellTestTags.SCREEN).assertExists()
        composeRule.onNodeWithTag(SettingsShellTestTags.AI_MANAGEMENT_ACTION).performClick()
        composeRule.onNodeWithTag(SettingsShellTestTags.TELEMETRY_ACTION).performClick()
        composeRule.onNodeWithTag(JianyuShellTestTags.PAGE_BACK_BUTTON).performClick()
        composeRule.runOnIdle {
            assertEquals(1, apiKeyCount)
            assertEquals(1, telemetryCount)
            assertEquals(1, backCount)
        }
    }
}
