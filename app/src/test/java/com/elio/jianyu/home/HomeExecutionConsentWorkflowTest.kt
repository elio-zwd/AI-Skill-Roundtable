package com.elio.jianyu.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeExecutionConsentWorkflowTest {
    private val ids = HomeWorkflowIds(
        workflowId = "workflow-consent",
        issueId = "issue-consent",
        stageId = "stage-consent",
        runId = "run-consent",
        saveIssueIdempotencyKey = "save-consent",
        executionIdempotencyKey = "execute-consent",
    )

    @Test
    fun finalReview_requiresEverySelectedSkillSpecificConsent() {
        val recommendation = recommendation(
            person = true,
            highStakes = true,
            networkRequired = true,
        )
        val state = HomeWorkflowState(
            ids = ids,
            draft = HomeQuestionDraft("测试问题"),
            step = HomeWorkflowStep.FINAL_REVIEW,
            recommendation = recommendation,
            recommendationConfirmed = true,
            contextSelection = HomeContextSelectionSnapshot(confirmed = true),
        )

        val notReady = HomeWorkflow.enterFinalReview(state)
        assertFalse(notReady.finalConfirmationReady)

        val network = HomeWorkflow.updateExecutionConsent(
            notReady,
            notReady.executionConsent.copy(networkAuthorized = true),
        )
        val high = HomeWorkflow.updateExecutionConsent(
            network,
            network.executionConsent.copy(highStakesConfirmed = true),
        )
        val person = HomeWorkflow.updateExecutionConsent(
            high,
            high.executionConsent.copy(personDisclaimerConfirmed = true),
        )
        val ready = HomeWorkflow.enterFinalReview(person)

        assertTrue(ready.finalConfirmationReady)
    }

    @Test
    fun changingRoster_invalidatesAllExecutionConsents() {
        val ready = HomeWorkflowState(
            ids = ids,
            draft = HomeQuestionDraft("测试问题"),
            step = HomeWorkflowStep.FINAL_REVIEW,
            recommendation = recommendation(person = true),
            recommendationConfirmed = true,
            contextSelection = HomeContextSelectionSnapshot(confirmed = true),
            executionConsent = HomeExecutionConsentSnapshot(
                networkAuthorized = true,
                highStakesConfirmed = true,
                personDisclaimerConfirmed = true,
            ),
            finalConfirmationReady = true,
        )

        val changed = HomeWorkflow.updateSkillResponsibility(
            ready,
            skillId = "person-skill",
            responsibility = "修改后的职责",
        )

        assertFalse(changed.executionConsent.networkAuthorized)
        assertFalse(changed.executionConsent.highStakesConfirmed)
        assertFalse(changed.executionConsent.personDisclaimerConfirmed)
        assertFalse(changed.finalConfirmationReady)
    }

    @Test
    fun restrictedPatentMaterial_neverBecomesReadyForExternalExecution() {
        val state = HomeWorkflowState(
            ids = ids,
            draft = HomeQuestionDraft("整理专利摘要"),
            step = HomeWorkflowStep.FINAL_REVIEW,
            recommendation = recommendation(prohibitsExternalMaterial = true),
            recommendationConfirmed = true,
            contextSelection = HomeContextSelectionSnapshot(
                items = listOf(
                    HomeContextItemSnapshot(
                        sourceType = "material",
                        sourceId = "secret",
                        title = "未公开技术",
                        originalContent = "敏感正文",
                        selectedContent = "敏感正文",
                        sourceHash = "hash",
                        sourceUpdatedAt = 1L,
                        sensitive = true,
                        selected = true,
                        networkAllowed = true,
                        sensitiveConfirmed = true,
                        userConfirmedAt = 1L,
                    ),
                ),
                confirmed = true,
            ),
            executionConsent = HomeExecutionConsentSnapshot(
                restrictedMaterialPresent = true,
                materialMayLeaveDevice = true,
            ),
        )

        val result = HomeWorkflow.enterFinalReview(state)

        assertFalse(result.finalConfirmationReady)
    }

    private fun recommendation(
        person: Boolean = false,
        highStakes: Boolean = false,
        networkRequired: Boolean = false,
        prohibitsExternalMaterial: Boolean = false,
    ) = HomeRecommendation(
        questionSummary = "测试问题",
        directions = emptySet(),
        mode = RecommendationMode.SINGLE,
        modeReason = "测试",
        skills = listOf(
            RecommendedSkill(
                skillId = if (person) "person-skill" else "task-skill",
                displayName = "测试 Skill",
                responsibility = "分析问题",
                reason = "测试匹配",
                risk = if (highStakes) RecommendationRisk.HIGH_STAKES else RecommendationRisk.GENERAL,
                riskDisclosure = "测试边界",
                freshnessDisclosure = "测试时效",
                networkRequirement = if (networkRequired) "需要联网核验" else "不需要联网",
                materialRequirement = "资料可选",
                expectedOutput = "测试输出",
                executable = true,
                selected = true,
                position = 0,
                isPersonPerspective = person,
                requiresHighStakesConfirmation = highStakes,
                requiresNetworkAuthorization = networkRequired,
                prohibitsExternalMaterial = prohibitsExternalMaterial,
            ),
        ),
        expectedOutput = "测试输出",
        source = RecommendationSource.LOCAL_CATALOG,
    )
}
