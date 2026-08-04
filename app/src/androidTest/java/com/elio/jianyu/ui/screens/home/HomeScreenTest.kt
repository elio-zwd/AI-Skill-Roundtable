package com.elio.jianyu.ui.screens.home

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.home.HomeContextSelectionSnapshot
import com.elio.jianyu.home.HomeRecommendation
import com.elio.jianyu.home.HomeWorkflow
import com.elio.jianyu.home.HomeWorkflowIds
import com.elio.jianyu.home.HomeWorkflowStep
import com.elio.jianyu.home.RecommendationMode
import com.elio.jianyu.home.RecommendationRisk
import com.elio.jianyu.home.RecommendationSource
import com.elio.jianyu.home.RecommendedSkill
import com.elio.jianyu.home.ValueDirection
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ids = HomeWorkflowIds(
        workflowId = "workflow-ui",
        issueId = "issue-ui",
        stageId = "stage-ui",
        runId = "run-ui",
        saveIssueIdempotencyKey = "save-ui",
        executionIdempotencyKey = "execute-ui",
    )

    @Test
    fun emptyHome_exposesRealInputDirectionsAndStableDisabledActions() {
        setHomeContent(HomeUiState(HomeWorkflow.initial(ids)))

        composeRule.onNodeWithTag(HomeTestTags.SCREEN).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.QUESTION_INPUT).assertExists()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Home.DIRECTION_REALITY_SUPPORT,
        ).assertExists()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Home.DIRECTION_THINKING_EXPANSION,
        ).assertExists()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Home.SAVE_ISSUE_ONLY_BUTTON,
        ).assertIsNotEnabled()
        composeRule.onNodeWithTag(HomeTestTags.RECOMMENDATION_REQUEST_BUTTON)
            .assertIsNotEnabled()
    }

    @Test
    fun exampleQuestion_emitsStableExampleAndKeepsInputTagUnchanged() {
        val examples = listOf(HomeExampleQuestion("example-one", "这是示例问题"))
        var selected: HomeExampleQuestion? = null
        setHomeContent(
            uiState = HomeUiState(HomeWorkflow.initial(ids)),
            examples = examples,
            onUseExample = { selected = it },
        )

        composeRule.onNodeWithTag(
            JianyuAutomationTags.Home.exampleQuestion("example-one"),
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(examples.single(), selected)
        }
        composeRule.onNodeWithTag(HomeTestTags.QUESTION_INPUT).assertExists()
    }

    @Test
    fun recommendationReady_showsReasonSkillBoundaryAndConfirmAction() {
        val recommendation = recommendation()
        val state = HomeWorkflow.initial(ids).copy(
            draft = com.elio.jianyu.home.HomeQuestionDraft(
                question = recommendation.questionSummary,
                directions = setOf(ValueDirection.REALITY_SUPPORT),
            ),
            step = HomeWorkflowStep.RECOMMENDATION_READY,
            recommendation = recommendation,
        )
        setHomeContent(HomeUiState(state))

        composeRule.onNodeWithTag(HomeTestTags.RECOMMENDATION_RESULT).assertExists()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Home.recommendationSkill("career-advisor"),
        ).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.RECOMMENDATION_CONFIRM_BUTTON)
            .assertIsEnabled()
    }

    @Test
    fun finalReview_requiresExplicitContextConfirmationAndKeepsStartTagStable() {
        val recommendation = recommendation()
        val state = HomeWorkflow.initial(ids).copy(
            draft = com.elio.jianyu.home.HomeQuestionDraft(
                question = recommendation.questionSummary,
                directions = setOf(
                    ValueDirection.REALITY_SUPPORT,
                    ValueDirection.THINKING_EXPANSION,
                ),
            ),
            step = HomeWorkflowStep.FINAL_REVIEW,
            recommendation = recommendation,
            recommendationConfirmed = true,
            contextSelection = HomeContextSelectionSnapshot(confirmed = true),
            finalConfirmationReady = true,
        )
        setHomeContent(HomeUiState(state))

        composeRule.onNodeWithTag(HomeTestTags.CONTEXT_CONFIRMED_SUMMARY).assertExists()
        composeRule.onNodeWithTag(JianyuAutomationTags.Home.FINAL_REVIEW).assertExists()
        composeRule.onNodeWithTag(JianyuAutomationTags.Home.START_ISSUE_BUTTON)
            .assertIsEnabled()
    }

    private fun setHomeContent(
        uiState: HomeUiState,
        examples: List<HomeExampleQuestion> = defaultHomeExampleQuestions,
        onUseExample: (HomeExampleQuestion) -> Unit = {},
    ) {
        composeRule.setContent {
            SkillRoundtableTheme {
                HomeScreen(
                    uiState = uiState,
                    onQuestionChanged = {},
                    onClearQuestion = {},
                    onToggleDirection = {},
                    onUseExample = onUseExample,
                    onRequestRecommendation = {},
                    onSaveIssueOnly = {},
                    onToggleSkill = {},
                    onResponsibilityChanged = { _, _ -> },
                    onMoveSkill = { _, _ -> },
                    onModeChanged = {},
                    onConfirmRecommendation = {},
                    onOpenContextConfirmation = {},
                    onBrowseSkills = {},
                    onStartIssue = {},
                    onOpenSettings = {},
                    examples = examples,
                )
            }
        }
    }

    private fun recommendation(): HomeRecommendation = HomeRecommendation(
        questionSummary = "如何规划未来半年的职业转型？",
        directions = setOf(ValueDirection.REALITY_SUPPORT),
        mode = RecommendationMode.SINGLE,
        modeReason = "一个专业能力即可先覆盖当前目标。",
        skills = listOf(
            RecommendedSkill(
                skillId = "career-advisor",
                displayName = "职业规划顾问",
                responsibility = "拆解目标并形成阶段计划",
                reason = "问题聚焦职业路径和行动安排",
                risk = RecommendationRisk.GENERAL,
                riskDisclosure = "一般决策风险，需要结合现实条件复核",
                freshnessDisclosure = "岗位信息需要按当前地区核验",
                networkRequirement = "联网可选",
                materialRequirement = "资料可选",
                expectedOutput = "半年行动计划",
                executable = true,
                selected = true,
                position = 0,
            ),
        ),
        expectedOutput = "半年行动计划",
        source = RecommendationSource.LOCAL_CATALOG,
    )
}
