package com.elio.jianyu.home

import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.execution.SearchMode
import kotlinx.serialization.Serializable

@Serializable
enum class ValueDirection {
    REALITY_SUPPORT,
    THINKING_EXPANSION,
}

@Serializable
enum class RecommendationMode {
    SINGLE,
    MULTI,
}

@Serializable
enum class RecommendationSource {
    LOCAL_CATALOG,
}

@Serializable
enum class RecommendationRisk {
    GENERAL,
    SENSITIVE,
    HIGH_STAKES,
    URGENT,
}

@Serializable
enum class HomeWorkflowStep {
    EDITING_QUESTION,
    RECOMMENDATION_LOADING,
    RECOMMENDATION_FAILURE,
    RECOMMENDATION_READY,
    NO_SUITABLE_SKILL,
    NO_EXECUTABLE_SKILL,
    EDITING_RECOMMENDATION,
    CONTEXT_CONFIRMING,
    CONTEXT_NEEDS_CORRECTION,
    FINAL_REVIEW,
    SAVING_ISSUE,
    SAVED_NOT_STARTED,
    STARTING_EXECUTION,
    START_FAILURE,
    NAVIGATING_TO_ISSUE,
    RESTORED_DRAFT,
}

@Serializable
enum class HomeWorkflowError(val code: String) {
    QUESTION_REQUIRED("question_required"),
    RECOMMENDATION_FAILED("recommendation_failed"),
    CATALOG_UNAVAILABLE("catalog_unavailable"),
    NO_SUITABLE_SKILL("no_suitable_skill"),
    NO_EXECUTABLE_SKILL("no_executable_skill"),
    RECOMMENDATION_CONFIRMATION_REQUIRED("recommendation_confirmation_required"),
    CONTEXT_CONFIRMATION_REQUIRED("context_confirmation_required"),
    CONTEXT_NEEDS_CORRECTION("context_needs_correction"),
    STORAGE_FAILURE("storage_failure"),
    EXECUTION_FAILURE("execution_failure"),
}

@Serializable
data class HomeWorkflowIds(
    val workflowId: String,
    val issueId: String,
    val stageId: String,
    val runId: String,
    val saveIssueIdempotencyKey: String,
    val executionIdempotencyKey: String,
) {
    init {
        require(workflowId.isNotBlank())
        require(issueId.isNotBlank())
        require(stageId.isNotBlank())
        require(runId.isNotBlank())
        require(saveIssueIdempotencyKey.isNotBlank())
        require(executionIdempotencyKey.isNotBlank())
    }
}

@Serializable
data class HomeQuestionDraft(
    val question: String = "",
    val directions: Set<ValueDirection> = emptySet(),
    val preferredSkillId: String? = null,
)

@Serializable
data class HomeRecommendationRequest(
    val question: String,
    val directions: Set<ValueDirection>,
    val preferredSkillId: String? = null,
) {
    init {
        require(question.isNotBlank())
    }
}

@Serializable
data class RecommendedSkill(
    val skillId: String,
    val displayName: String,
    val responsibility: String,
    val reason: String,
    val risk: RecommendationRisk,
    val riskDisclosure: String,
    val freshnessDisclosure: String,
    val networkRequirement: String,
    val materialRequirement: String,
    val expectedOutput: String,
    val executable: Boolean,
    val selected: Boolean,
    val position: Int,
    val isPersonPerspective: Boolean = false,
    val requiresHighStakesConfirmation: Boolean = false,
    val requiresNetworkAuthorization: Boolean = false,
    val requiresMaterial: Boolean = false,
    val requiresMaterialAuthorization: Boolean = false,
    val requiresSensitiveMaterialConfirmation: Boolean = false,
    val prohibitsExternalMaterial: Boolean = false,
) {
    init {
        require(skillId.isNotBlank())
        require(displayName.isNotBlank())
        require(responsibility.isNotBlank())
        require(reason.isNotBlank())
        require(position >= 0)
    }
}

@Serializable
data class HomeRecommendation(
    val questionSummary: String,
    val directions: Set<ValueDirection>,
    val mode: RecommendationMode,
    val modeReason: String,
    val skills: List<RecommendedSkill>,
    val expectedOutput: String,
    val source: RecommendationSource,
) {
    init {
        require(questionSummary.isNotBlank())
        require(modeReason.isNotBlank())
        require(expectedOutput.isNotBlank())
    }

    val selectedSkills: List<RecommendedSkill>
        get() = skills.filter(RecommendedSkill::selected).sortedBy(RecommendedSkill::position)
}

sealed interface HomeRecommendationOutcome {
    data class Ready(val recommendation: HomeRecommendation) : HomeRecommendationOutcome
    data object NoSuitableSkill : HomeRecommendationOutcome
    data class NoExecutableSkill(
        val candidates: List<RecommendedSkill>,
    ) : HomeRecommendationOutcome
}

enum class HomeRecommendationValidationError {
    EMPTY_SELECTION,
    DUPLICATE_SKILL,
    UNKNOWN_SKILL,
    NON_EXECUTABLE_SKILL,
    BLANK_REASON,
    BLANK_RESPONSIBILITY,
    INVALID_POSITION,
}

@Serializable
data class HomeContextItemSnapshot(
    val sourceType: String,
    val sourceId: String,
    val title: String,
    val sourceKind: String = "",
    val sourceLocator: String? = null,
    val sourcePublishedAt: Long? = null,
    val sourceCapturedAt: Long? = null,
    val originalContent: String,
    val selectedContent: String,
    val sourceHash: String,
    val sourceUpdatedAt: Long,
    val sensitive: Boolean,
    val selected: Boolean = false,
    val networkAllowed: Boolean = false,
    val sensitiveConfirmed: Boolean = false,
    val confirmationOrder: Int = 0,
    val userConfirmedAt: Long = 0L,
)

@Serializable
data class HomeContextSelectionSnapshot(
    val baseContextCharacters: Int = 0,
    val items: List<HomeContextItemSnapshot> = emptyList(),
    val confirmed: Boolean = false,
)

/**
 * 用户针对本次阵容、联网和风险边界作出的显式同意。
 *
 * 该快照不保存用户正文；阵容、职责、顺序或问题变化后必须整体失效。
 */
@Serializable
data class HomeExecutionConsentSnapshot(
    val networkAuthorized: Boolean = false,
    val highStakesConfirmed: Boolean = false,
    val personDisclaimerConfirmed: Boolean = false,
    val restrictedMaterialPresent: Boolean = false,
    val materialMayLeaveDevice: Boolean = false,
)

@Serializable
data class HomeWorkflowState(
    val ids: HomeWorkflowIds,
    val draft: HomeQuestionDraft = HomeQuestionDraft(),
    val step: HomeWorkflowStep = HomeWorkflowStep.EDITING_QUESTION,
    val recommendation: HomeRecommendation? = null,
    val recommendationConfirmed: Boolean = false,
    val contextSelection: HomeContextSelectionSnapshot = HomeContextSelectionSnapshot(),
    val executionConsent: HomeExecutionConsentSnapshot = HomeExecutionConsentSnapshot(),
    val finalConfirmationReady: Boolean = false,
    val activeRecommendationToken: Long? = null,
    val nextRecommendationToken: Long = 1L,
    val errorCode: String? = null,
    val operationInFlight: Boolean = false,
    val restored: Boolean = false,
)

@Serializable
data class HomeFinalConfirmation(
    val ids: HomeWorkflowIds,
    val question: String,
    val directions: Set<ValueDirection>,
    val recommendation: HomeRecommendation,
    val contextSelection: HomeContextSelectionSnapshot,
    val executionConsent: HomeExecutionConsentSnapshot = HomeExecutionConsentSnapshot(),
    val thinkingOverride: IssueThinkingPolicy? = null,
    val searchMode: SearchMode = SearchMode.AUTO,
    val confirmedAt: Long,
) {
    init {
        require(question.isNotBlank())
        require(confirmedAt > 0L)
    }
}

data class HomeSaveOnlyCommand(
    val ids: HomeWorkflowIds,
    val question: String,
    val createdAt: Long,
) {
    init {
        require(question.isNotBlank())
        require(createdAt > 0L)
    }
}

sealed interface HomeStartResult {
    data class SavedOnly(
        val issueId: String,
        val stageId: String,
    ) : HomeStartResult

    data class Started(
        val issueId: String,
        val stageId: String,
        val runId: String,
    ) : HomeStartResult

    data class SavedNotStarted(
        val issueId: String,
        val stageId: String,
        val errorCode: String,
    ) : HomeStartResult

    data class Failure(
        val errorCode: String,
    ) : HomeStartResult
}

data class HomeRecommendationRequestTransition(
    val state: HomeWorkflowState,
    val requestToken: Long?,
    val error: HomeWorkflowError? = null,
)
