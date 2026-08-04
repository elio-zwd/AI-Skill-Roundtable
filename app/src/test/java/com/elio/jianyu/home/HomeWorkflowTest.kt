package com.elio.jianyu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWorkflowTest {
    private val ids = HomeWorkflowIds(
        workflowId = "workflow-1",
        issueId = "issue-1",
        stageId = "stage-1",
        runId = "run-1",
        saveIssueIdempotencyKey = "save-1",
        executionIdempotencyKey = "execute-1",
    )

    @Test
    fun initialState_isProblemFirstAndEmpty() {
        val state = HomeWorkflow.initial(ids)

        assertEquals(HomeWorkflowStep.EDITING_QUESTION, state.step)
        assertEquals("", state.draft.question)
        assertTrue(state.draft.directions.isEmpty())
        assertNull(state.recommendation)
        assertFalse(state.recommendationConfirmed)
        assertFalse(state.contextSelection.confirmed)
    }

    @Test
    fun directions_supportSkipSingleAndCombinationWithoutChangingMainlineIds() {
        val initial = HomeWorkflow.initial(ids)
        val reality = HomeWorkflow.toggleDirection(initial, ValueDirection.REALITY_SUPPORT)
        val both = HomeWorkflow.toggleDirection(reality, ValueDirection.THINKING_EXPANSION)
        val thinkingOnly = HomeWorkflow.toggleDirection(both, ValueDirection.REALITY_SUPPORT)

        assertEquals(setOf(ValueDirection.REALITY_SUPPORT), reality.draft.directions)
        assertEquals(
            setOf(ValueDirection.REALITY_SUPPORT, ValueDirection.THINKING_EXPANSION),
            both.draft.directions,
        )
        assertEquals(setOf(ValueDirection.THINKING_EXPANSION), thinkingOnly.draft.directions)
        assertEquals(ids, reality.ids)
        assertEquals(ids, both.ids)
        assertEquals(ids, thinkingOnly.ids)
    }

    @Test
    fun blankQuestion_cannotBeginRecommendationOrSave() {
        val state = HomeWorkflow.initial(ids)

        val result = HomeWorkflow.beginRecommendation(state)

        assertEquals(HomeWorkflowStep.EDITING_QUESTION, result.state.step)
        assertEquals(HomeWorkflowError.QUESTION_REQUIRED, result.error)
        assertNull(result.requestToken)
        assertFalse(HomeWorkflow.canSaveIssueOnly(state))
    }

    @Test
    fun lateRecommendation_doesNotOverwriteNewQuestion() {
        val initial = HomeWorkflow.onQuestionChanged(HomeWorkflow.initial(ids), "第一个问题")
        val firstRequest = HomeWorkflow.beginRecommendation(initial)
        val changed = HomeWorkflow.onQuestionChanged(firstRequest.state, "第二个问题")

        val late = HomeWorkflow.applyRecommendation(
            state = changed,
            requestToken = requireNotNull(firstRequest.requestToken),
            recommendation = sampleRecommendation("第一个问题"),
        )

        assertSame(changed, late)
        assertEquals("第二个问题", late.draft.question)
        assertNull(late.recommendation)
    }

    @Test
    fun recommendationFailure_preservesQuestionAndDirections() {
        val editing = HomeWorkflow.toggleDirection(
            HomeWorkflow.onQuestionChanged(HomeWorkflow.initial(ids), "如何规划转型？"),
            ValueDirection.REALITY_SUPPORT,
        )
        val request = HomeWorkflow.beginRecommendation(editing)

        val failed = HomeWorkflow.failRecommendation(
            state = request.state,
            requestToken = requireNotNull(request.requestToken),
            error = HomeWorkflowError.RECOMMENDATION_FAILED,
        )

        assertEquals(HomeWorkflowStep.RECOMMENDATION_FAILURE, failed.step)
        assertEquals("如何规划转型？", failed.draft.question)
        assertEquals(setOf(ValueDirection.REALITY_SUPPORT), failed.draft.directions)
    }

    @Test
    fun recommendationAdjustment_invalidatesRecommendationContextAndFinalConfirmation() {
        val ready = readyState()
        val confirmed = HomeWorkflow.confirmRecommendation(ready)
        val contextConfirmed = HomeWorkflow.confirmContext(
            confirmed,
            HomeContextSelectionSnapshot(confirmed = true),
        )
        val finalReview = HomeWorkflow.enterFinalReview(contextConfirmed)

        val changed = HomeWorkflow.updateSkillResponsibility(
            finalReview,
            skillId = "skill-a",
            responsibility = "重新核对执行风险",
        )

        assertEquals(HomeWorkflowStep.EDITING_RECOMMENDATION, changed.step)
        assertFalse(changed.recommendationConfirmed)
        assertFalse(changed.contextSelection.confirmed)
        assertFalse(changed.finalConfirmationReady)
        assertEquals(
            "重新核对执行风险",
            changed.recommendation?.skills?.single()?.responsibility,
        )
    }

    @Test
    fun restoredPendingState_neverBecomesConfirmedAutomatically() {
        val pending = readyState().copy(restored = true)

        val restored = HomeWorkflow.restore(pending)

        assertEquals(HomeWorkflowStep.RESTORED_DRAFT, restored.step)
        assertFalse(restored.recommendationConfirmed)
        assertFalse(restored.contextSelection.confirmed)
        assertFalse(restored.finalConfirmationReady)
        assertEquals(ids, restored.ids)
    }

    private fun readyState(): HomeWorkflowState {
        val editing = HomeWorkflow.onQuestionChanged(HomeWorkflow.initial(ids), "如何规划转型？")
        val request = HomeWorkflow.beginRecommendation(editing)
        return HomeWorkflow.applyRecommendation(
            state = request.state,
            requestToken = requireNotNull(request.requestToken),
            recommendation = sampleRecommendation("如何规划转型？"),
        )
    }

    private fun sampleRecommendation(question: String): HomeRecommendation = HomeRecommendation(
        questionSummary = question,
        directions = emptySet(),
        mode = RecommendationMode.SINGLE,
        modeReason = "一个明确能力即可覆盖当前问题。",
        skills = listOf(
            RecommendedSkill(
                skillId = "skill-a",
                displayName = "Skill A",
                responsibility = "拆解问题并给出行动步骤",
                reason = "与问题目标直接匹配",
                risk = RecommendationRisk.GENERAL,
                riskDisclosure = "一般风险",
                freshnessDisclosure = "不依赖实时信息",
                networkRequirement = "可选",
                materialRequirement = "可选",
                expectedOutput = "行动计划",
                executable = true,
                selected = true,
                position = 0,
            ),
        ),
        expectedOutput = "行动计划",
        source = RecommendationSource.LOCAL_CATALOG,
    )
}
