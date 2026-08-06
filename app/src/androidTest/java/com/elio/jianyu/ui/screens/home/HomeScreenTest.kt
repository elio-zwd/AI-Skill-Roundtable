package com.elio.jianyu.ui.screens.home

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.home.HomeContextSelectionSnapshot
import com.elio.jianyu.home.HomeExecutionConsentSnapshot
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
    fun finalReview_generalSkillKeepsStableStartTagEnabled() {
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

    @Test
    fun personHighStakesNetworkSkillShowsThreeExplicitConfirmationsAndDisablesStart() {
        val recommendation = recommendation(
            skill = RecommendedSkill(
                skillId = "person-risk-network",
                displayName = "人物高后果 Skill",
                responsibility = "分析公开思考框架",
                reason = "测试人物与风险披露",
                risk = RecommendationRisk.HIGH_STAKES,
                riskDisclosure = "AI 模拟，不代表本人；重要决定需专业复核",
                freshnessDisclosure = "当前事实需要联网核验",
                networkRequirement = "需要联网核验",
                materialRequirement = "资料可选",
                expectedOutput = "风险分析",
                executable = true,
                selected = true,
                position = 0,
                isPersonPerspective = true,
                requiresHighStakesConfirmation = true,
                requiresNetworkAuthorization = true,
            ),
        )
        val state = HomeWorkflow.initial(ids).copy(
            draft = com.elio.jianyu.home.HomeQuestionDraft(recommendation.questionSummary),
            step = HomeWorkflowStep.FINAL_REVIEW,
            recommendation = recommendation,
            recommendationConfirmed = true,
            contextSelection = HomeContextSelectionSnapshot(confirmed = true),
            executionConsent = HomeExecutionConsentSnapshot(),
            finalConfirmationReady = false,
        )
        setHomeContent(HomeUiState(state))

        composeRule.onNodeWithTag(HomeTestTags.NETWORK_AUTHORIZATION).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.HIGH_STAKES_CONFIRMATION).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.PERSON_DISCLAIMER_CONFIRMATION).assertExists()
        composeRule.onNodeWithTag(JianyuAutomationTags.Home.START_ISSUE_BUTTON)
            .assertIsNotEnabled()
    }

    @Test
    fun restrictedPatentMaterialShowsBlockAndDisablesStart() {
        val recommendation = recommendation(
            skill = RecommendedSkill(
                skillId = "patent-disclosure-organizer",
                displayName = "专利交底材料整理",
                responsibility = "整理脱敏摘要",
                reason = "测试禁止外传",
                risk = RecommendationRisk.HIGH_STAKES,
                riskDisclosure = "禁止外传材料不得发送至外部模型",
                freshnessDisclosure = "按法域核验",
                networkRequirement = "资料正文不得联网发送",
                materialRequirement = "需要资料",
                expectedOutput = "材料清单",
                executable = true,
                selected = true,
                position = 0,
                requiresHighStakesConfirmation = true,
                prohibitsExternalMaterial = true,
            ),
        )
        val state = HomeWorkflow.initial(ids).copy(
            draft = com.elio.jianyu.home.HomeQuestionDraft(recommendation.questionSummary),
            step = HomeWorkflowStep.FINAL_REVIEW,
            recommendation = recommendation,
            recommendationConfirmed = true,
            contextSelection = HomeContextSelectionSnapshot(confirmed = true),
            executionConsent = HomeExecutionConsentSnapshot(
                highStakesConfirmed = true,
                restrictedMaterialPresent = true,
                materialMayLeaveDevice = true,
            ),
            finalConfirmationReady = false,
        )
        setHomeContent(HomeUiState(state))

        composeRule.onNodeWithTag(HomeTestTags.RESTRICTED_MATERIAL_BLOCK).assertExists()
        composeRule.onNodeWithTag(JianyuAutomationTags.Home.START_ISSUE_BUTTON)
            .assertIsNotEnabled()
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
                    onNetworkAuthorized = {},
                    onHighStakesConfirmed = {},
                    onPersonDisclaimerConfirmed = {},
                    onBrowseSkills = {},
                    onStartIssue = {},
                    onOpenSettings = {},
                    examples = examples,
                )
            }
        }
    }

    private fun recommendation(
        skill: RecommendedSkill = RecommendedSkill(
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
    ): HomeRecommendation = HomeRecommendation(
        questionSummary = "如何规划未来半年的职业转型？",
        directions = setOf(ValueDirection.REALITY_SUPPORT),
        mode = RecommendationMode.SINGLE,
        modeReason = "一个专业能力即可先覆盖当前目标。",
        skills = listOf(skill),
        expectedOutput = skill.expectedOutput,
        source = RecommendationSource.LOCAL_CATALOG,
    )
}
