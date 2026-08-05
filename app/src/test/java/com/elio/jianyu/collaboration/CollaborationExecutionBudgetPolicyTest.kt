package com.elio.jianyu.collaboration

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CollaborationExecutionBudgetPolicyTest {
    @Test
    fun directedBudgetAllowsInitialCallAndExplicitRetries() {
        assertEquals(
            CollaborationExecutionBudgetPolicy.DIRECTED_MIN_API_CALLS,
            CollaborationExecutionBudgetPolicy.directed().maxApiCalls,
        )
    }

    @Test
    fun crossBudgetCoversResponsesFailedMemberRetriesAndSynthesisRetry() {
        assertEquals(8, CollaborationExecutionBudgetPolicy.cross(participantCount = 2).maxApiCalls)
        assertEquals(10, CollaborationExecutionBudgetPolicy.cross(participantCount = 4).maxApiCalls)
        assertEquals(14, CollaborationExecutionBudgetPolicy.cross(participantCount = 6).maxApiCalls)
    }

    @Test
    fun requestDefaultsUseRetryCapableBudgetPolicy() {
        val directed = DirectedResponseRequest(
            operationId = "directed-default-1",
            issueId = "issue-1",
            stageId = "stage-1",
            selectedSkillId = "study-planner",
            question = "问题",
            roundIndex = 0,
            userConfirmedAt = 1L,
        )
        val cross = CrossDiscussionRequest(
            operationId = "cross-default-1",
            issueId = "issue-1",
            stageId = "stage-1",
            selectedSkillIds = listOf("study-planner", "research-fact-checker"),
            focus = "焦点",
            roundIndex = 0,
            userConfirmedAt = 1L,
        )

        assertEquals(3, directed.budget.maxApiCalls)
        assertEquals(8, cross.budget.maxApiCalls)
    }

    @Test
    fun crossBudgetRejectsAnInvalidParticipantCount() {
        try {
            CollaborationExecutionBudgetPolicy.cross(participantCount = 1)
            fail("Expected participant count validation to fail")
        } catch (_: IllegalArgumentException) {
            // 预期：交叉讨论至少需要两位成员。
        }
    }
}
