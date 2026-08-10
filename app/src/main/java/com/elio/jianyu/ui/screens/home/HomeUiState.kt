package com.elio.jianyu.ui.screens.home

import com.elio.jianyu.home.HomeWorkflowState
import com.elio.jianyu.home.HomeWorkflowStep
import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.ui.screens.context.ContextConfirmationUiState

data class HomeUiState(
    val workflow: HomeWorkflowState,
    val contextConfirmation: ContextConfirmationUiState? = null,
    val message: String? = null,
    val preferredSkillDisplayName: String? = null,
    val thinkingOverride: IssueThinkingPolicy? = null,
) {
    val question: String
        get() = workflow.draft.question

    val canRequestRecommendation: Boolean
        get() = question.isNotBlank() && !workflow.operationInFlight

    val canSaveIssueOnly: Boolean
        get() = question.isNotBlank() && !workflow.operationInFlight

    val recommendationVisible: Boolean
        get() = workflow.step in setOf(
            HomeWorkflowStep.RECOMMENDATION_LOADING,
            HomeWorkflowStep.RECOMMENDATION_FAILURE,
            HomeWorkflowStep.RECOMMENDATION_READY,
            HomeWorkflowStep.NO_SUITABLE_SKILL,
            HomeWorkflowStep.NO_EXECUTABLE_SKILL,
            HomeWorkflowStep.EDITING_RECOMMENDATION,
            HomeWorkflowStep.CONTEXT_CONFIRMING,
            HomeWorkflowStep.CONTEXT_NEEDS_CORRECTION,
            HomeWorkflowStep.FINAL_REVIEW,
            HomeWorkflowStep.SAVING_ISSUE,
            HomeWorkflowStep.SAVED_NOT_STARTED,
            HomeWorkflowStep.STARTING_EXECUTION,
            HomeWorkflowStep.START_FAILURE,
            HomeWorkflowStep.NAVIGATING_TO_ISSUE,
        )

    val finalReviewVisible: Boolean
        get() = workflow.step == HomeWorkflowStep.FINAL_REVIEW ||
            workflow.finalConfirmationReady
}

sealed interface HomeNavigationEvent {
    data class NavigateToIssue(
        val issueId: String,
        val stageId: String,
    ) : HomeNavigationEvent

    data object OpenSkillCatalog : HomeNavigationEvent
}

data class HomeExampleQuestion(
    val stableId: String,
    val text: String,
)

val defaultHomeExampleQuestions: List<HomeExampleQuestion> = listOf(
    HomeExampleQuestion(
        stableId = "career-transition",
        text = "我应该如何规划未来半年的职业转型？",
    ),
    HomeExampleQuestion(
        stableId = "project-decision",
        text = "这个项目下一步应该优先解决什么，并检查哪些盲区？",
    ),
)
