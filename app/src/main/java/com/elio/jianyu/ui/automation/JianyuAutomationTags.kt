package com.elio.jianyu.ui.automation

/**
 * 见域 UI 自动化的稳定语义契约。
 *
 * 静态标签只描述节点职责，不包含可见文案；动态标签只允许调用方传入稳定内部 ID，
 * 禁止传入标题、正文、姓名或其他用户内容。
 */
object JianyuAutomationTags {
    private const val MAX_STABLE_ID_LENGTH = 128
    private val stableIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

    object App {
        const val CONTENT_ROOT = "jianyu_app_content_root"
        const val BOTTOM_NAVIGATION = "app_bottom_navigation"
        const val RUNTIME_MAINTENANCE = "app_runtime_maintenance"
        const val RUNTIME_UNAVAILABLE = "app_runtime_unavailable"
        const val RUNTIME_RETRY = "app_runtime_retry"
    }

    object Navigation {
        const val HOME = "app_destination_home"
        const val ISSUES = "app_destination_issues"
        const val SKILLS = "app_destination_skills"
        const val RESOURCES = "app_destination_resources"

        fun destination(destinationSuffix: String): String =
            "app_destination_${normalizedStableId(destinationSuffix)}"
    }

    object Shell {
        const val GLOBAL_SETTINGS_BUTTON = "global_settings_button"
        const val PAGE_BACK_BUTTON = "page_back_button"
    }

    object Screen {
        const val HOME = "home_screen"
        const val ISSUES = "issues_screen"
        const val ISSUE_EXECUTION = "issue_execution_screen"
        const val SKILLS = "official_skill_catalog"
        const val RESOURCES = "resources_screen"
        const val SETTINGS = "settings_screen"
    }

    object Home {
        const val QUESTION_INPUT = "home_question_input"
        const val QUESTION_CLEAR_BUTTON = "home_question_clear_button"
        const val DIRECTION_REALITY_SUPPORT = "home_direction_reality_support"
        const val DIRECTION_THINKING_EXPANSION = "home_direction_thinking_expansion"
        const val SAVE_ISSUE_ONLY_BUTTON = "home_save_issue_only_button"
        const val RECOMMENDATION_REQUEST_BUTTON = "home_recommendation_request_button"
        const val RECOMMENDATION_RESULT = "home_recommendation_result"
        const val RECOMMENDATION_LOADING = "home_recommendation_loading"
        const val RECOMMENDATION_FAILURE = "home_recommendation_failure"
        const val RECOMMENDATION_CONFIRM_BUTTON = "home_recommendation_confirm_button"
        const val CONTEXT_CONFIRMATION_BUTTON = "home_context_confirmation_button"
        const val CONTEXT_CONFIRMED_SUMMARY = "home_context_confirmed_summary"
        const val FINAL_REVIEW = "home_final_review"
        const val START_ISSUE_BUTTON = "home_start_issue_button"
        const val DRAFT_RECOVERY = "home_draft_recovery"

        fun recommendationSkill(skillId: String): String =
            "home_recommendation_skill_${normalizedStableId(skillId)}"

        fun exampleQuestion(exampleId: String): String =
            "home_example_question_${normalizedStableId(exampleId)}"
    }

    object Issues {
        const val LOADING = "issues_loading"
        const val EMPTY = "issues_empty"
        const val FAILURE = "issues_failure"
        const val ACTIVE_SECTION = "issues_active_section"
        const val ARCHIVED_SECTION = "issues_archived_section"
        const val TRASHED_SECTION = "issues_trashed_section"
        const val RECOVERY_SCREEN = "issue_recovery_screen"
        const val RECOVERY_FAILURE = "issue_recovery_failure"

        fun issue(issueId: String): String =
            "issue_navigation_${normalizedStableId(issueId)}"
    }

    object Resources {
        const val MATERIALS_TAB = "resources_tab_materials"
        const val ARTIFACTS_TAB = "resources_tab_artifacts"
        const val MATERIAL_LIBRARY = "resources_material_library"
        const val PERSONAL_CONTEXT_LIBRARY = "resources_personal_context_library"
        const val MATERIALS_CONTENT = "resources_materials_content"
        const val PERSONAL_CONTEXT_CONTENT = "resources_personal_context_content"
        const val SEARCH = "resources_search"
        const val ADD = "resources_add"
        const val EMPTY_STATE = "resources_empty_state"
        const val EDITOR = "resources_editor"
        const val PURGE_CONFIRMATION = "resources_purge_confirmation"

        fun material(materialId: String): String =
            "resources_material_${normalizedStableId(materialId)}"

        fun personalContext(contextId: String): String =
            "resources_personal_context_${normalizedStableId(contextId)}"
    }

    object Artifacts {
        const val LIBRARY = "artifact_library"
        const val EMPTY = "artifact_library_empty"
        const val FAILURE = "artifact_library_failure"
        const val SEARCH = "artifact_search"
        const val TYPE_FILTER = "artifact_type_filter"
        const val HISTORY_FILTER = "artifact_revision_history"
        const val DETAIL = "artifact_detail"
        const val SOURCES = "artifact_sources"
        const val OPEN_ISSUE = "artifact_open_issue"

        fun item(artifactId: String): String =
            "artifact_item_${normalizedStableId(artifactId)}"
    }

    object Execution {
        const val LOADING = "issue_execution_loading"
        const val FAILURE = "issue_execution_failure"
        const val STATUS = "issue_execution_status"
        const val PARTICIPANTS = "issue_execution_participants"
        const val STOP = "issue_execution_stop"
        const val RETRY = "issue_execution_retry"
        const val RECOVER = "issue_execution_recover"
        const val CONTEXT = "issue_execution_context"

        fun participant(snapshotId: String): String =
            "issue_execution_participant_${normalizedStableId(snapshotId)}"
    }

    object Collaboration {
        const val INPUT = "issue_collaboration_input"
        const val DIRECTED_RESPONSE_BUTTON = "issue_directed_response_button"
        const val CROSS_DISCUSSION_BUTTON = "issue_cross_discussion_button"
        const val ROSTER = "issue_collaboration_roster"
        const val DIRECTED_DIALOG = "directed_response_dialog"
        const val DIRECTED_CONFIRM = "directed_response_confirm"
        const val DIRECTED_FAILURE = "directed_response_failure"
        const val DIRECTED_RETRY = "directed_response_retry"
        const val CROSS_DIALOG = "cross_discussion_dialog"
        const val CROSS_FOCUS_INPUT = "cross_discussion_focus_input"
        const val CROSS_INTEGRATOR = "cross_discussion_integrator"
        const val CROSS_CONFIRM = "cross_discussion_confirm"
        const val CROSS_STATUS = "cross_discussion_status"
        const val CROSS_RETRY_FAILED = "cross_discussion_retry_failed"
        const val CROSS_SYNTHESIZE_AVAILABLE = "cross_discussion_synthesize_available"
        const val CROSS_RESUME_SYNTHESIS = "cross_discussion_resume_synthesis"
        const val CROSS_FAILURE = "cross_discussion_failure"

        fun directedParticipant(skillId: String): String =
            "directed_participant_${normalizedStableId(skillId)}"

        fun crossParticipant(skillId: String): String =
            "cross_discussion_participant_${normalizedStableId(skillId)}"

        fun message(messageId: Long): String =
            "cross_discussion_message_${normalizedStableId(messageId.toString())}"

        fun session(discussionId: String): String =
            "cross_discussion_session_${normalizedStableId(discussionId)}"
    }

    object StageResult {
        const val PANEL = "stage_result_panel"
        const val DRAFT_EMPTY = "stage_draft_empty"
        const val DRAFT_CREATE = "stage_draft_create_button"
        const val DRAFT_CREATE_FROM_MESSAGES = "stage_draft_create_from_messages"
        const val DRAFT_EDITOR = "stage_draft_editor"
        const val DRAFT_SAVE = "stage_draft_save_button"
        const val DRAFT_SAVING = "stage_draft_saving"
        const val DRAFT_SAVED = "stage_draft_saved"
        const val DRAFT_SAVE_FAILURE = "stage_draft_save_failure"
        const val DRAFT_CONFLICT = "stage_draft_conflict"
        const val DRAFT_ABANDON = "stage_draft_abandon_button"
        const val DRAFT_ABANDON_CONFIRMATION = "stage_draft_abandon_confirmation"
        const val ARTIFACT_CONFIRM = "stage_artifact_confirm_button"
        const val ARTIFACT_CONFIRMATION_DIALOG = "artifact_confirmation_dialog"
        const val ARTIFACT_CONFIRMATION_CONFIRM = "artifact_confirmation_confirm"
        const val ARTIFACT_CONFIRMATION_CANCEL = "artifact_confirmation_cancel"

        fun message(messageId: Long): String =
            "stage_source_message_${normalizedStableId(messageId.toString())}"

        fun artifact(artifactId: String): String =
            "stage_artifact_${normalizedStableId(artifactId)}"
    }

    object Context {
        const val DIALOG = "context_confirmation_dialog"
        const val TOTAL = "context_confirmation_total"
        const val VALIDATION_ERRORS = "context_confirmation_validation_errors"
        const val CONFIRM = "context_confirmation_confirm"
        const val CANCEL = "context_confirmation_cancel"

        fun candidate(sourceType: String, sourceId: String): String =
            "context_candidate_${normalizedStableId(sourceType)}_${normalizedStableId(sourceId)}"
    }

    object AdvanceIssue {
        const val BUTTON = "issue_advance_button"
        const val DIALOG = "advance_issue_dialog"
        const val DIRECTION_STEP = "advance_direction_step"
        const val DIRECTION_REALITY_SUPPORT = "advance_direction_reality_support"
        const val DIRECTION_THINKING_EXPANSION = "advance_direction_thinking_expansion"
        const val MEASURE_STEP = "advance_measure_step"
        const val CUSTOM_OBJECTIVE = "advance_custom_objective"
        const val SUMMARY_STEP = "advance_summary_step"
        const val INHERITED_MATERIALS = "advance_inherited_materials"
        const val INHERITED_ARTIFACTS = "advance_inherited_artifacts"
        const val ROSTER = "advance_roster"
        const val EXPECTED_OUTPUT = "advance_expected_output"
        const val CONFIRM = "advance_confirm"
        const val CANCEL = "advance_cancel"
        const val WAIT_FOR_RUN = "advance_wait_for_run"
        const val STOP_CURRENT_RUN = "advance_stop_current_run"
        const val FAILURE = "advance_failure"

        fun rosterMember(skillId: String): String =
            "advance_roster_member_${normalizedStableId(skillId)}"

        fun material(materialId: String): String =
            "advance_material_${normalizedStableId(materialId)}"

        fun artifact(artifactId: String): String =
            "advance_artifact_${normalizedStableId(artifactId)}"
    }

    object StageTimeline {
        const val TIMELINE = "stage_timeline"
        const val CURRENT = "stage_timeline_current"
        const val UNDO_BUTTON = "stage_undo_button"
        const val UNDO_CONFIRMATION = "stage_undo_confirmation"

        fun item(stageId: String): String =
            "stage_timeline_item_${normalizedStableId(stageId)}"
    }

    object Settings {
        const val API_KEYS_ENTRY = "settings_api_keys_entry"
        const val API_KEYS_ACTION = "settings_api_keys_action"
        const val TELEMETRY_ENTRY = "settings_telemetry_entry"
        const val TELEMETRY_ACTION = "settings_telemetry_action"
    }

    /** 已冻结的静态标签清单。使用 List 而非 Set，测试才能发现重复项。 */
    val frozenStaticTags: List<String> = listOf(
        App.CONTENT_ROOT,
        App.BOTTOM_NAVIGATION,
        App.RUNTIME_MAINTENANCE,
        App.RUNTIME_UNAVAILABLE,
        App.RUNTIME_RETRY,
        Navigation.HOME,
        Navigation.ISSUES,
        Navigation.SKILLS,
        Navigation.RESOURCES,
        Shell.GLOBAL_SETTINGS_BUTTON,
        Shell.PAGE_BACK_BUTTON,
        Screen.HOME,
        Screen.ISSUES,
        Screen.ISSUE_EXECUTION,
        Screen.SKILLS,
        Screen.RESOURCES,
        Screen.SETTINGS,
        Home.QUESTION_INPUT,
        Home.QUESTION_CLEAR_BUTTON,
        Home.DIRECTION_REALITY_SUPPORT,
        Home.DIRECTION_THINKING_EXPANSION,
        Home.SAVE_ISSUE_ONLY_BUTTON,
        Home.RECOMMENDATION_REQUEST_BUTTON,
        Home.RECOMMENDATION_RESULT,
        Home.RECOMMENDATION_LOADING,
        Home.RECOMMENDATION_FAILURE,
        Home.RECOMMENDATION_CONFIRM_BUTTON,
        Home.CONTEXT_CONFIRMATION_BUTTON,
        Home.CONTEXT_CONFIRMED_SUMMARY,
        Home.FINAL_REVIEW,
        Home.START_ISSUE_BUTTON,
        Home.DRAFT_RECOVERY,
        Issues.LOADING,
        Issues.EMPTY,
        Issues.FAILURE,
        Issues.ACTIVE_SECTION,
        Issues.ARCHIVED_SECTION,
        Issues.TRASHED_SECTION,
        Issues.RECOVERY_SCREEN,
        Issues.RECOVERY_FAILURE,
        Resources.MATERIALS_TAB,
        Resources.ARTIFACTS_TAB,
        Resources.MATERIAL_LIBRARY,
        Resources.PERSONAL_CONTEXT_LIBRARY,
        Resources.MATERIALS_CONTENT,
        Resources.PERSONAL_CONTEXT_CONTENT,
        Resources.SEARCH,
        Resources.ADD,
        Resources.EMPTY_STATE,
        Resources.EDITOR,
        Resources.PURGE_CONFIRMATION,
        Artifacts.LIBRARY,
        Artifacts.EMPTY,
        Artifacts.FAILURE,
        Artifacts.SEARCH,
        Artifacts.TYPE_FILTER,
        Artifacts.HISTORY_FILTER,
        Artifacts.DETAIL,
        Artifacts.SOURCES,
        Artifacts.OPEN_ISSUE,
        Execution.LOADING,
        Execution.FAILURE,
        Execution.STATUS,
        Execution.PARTICIPANTS,
        Execution.STOP,
        Execution.RETRY,
        Execution.RECOVER,
        Execution.CONTEXT,
        Collaboration.INPUT,
        Collaboration.DIRECTED_RESPONSE_BUTTON,
        Collaboration.CROSS_DISCUSSION_BUTTON,
        Collaboration.ROSTER,
        Collaboration.DIRECTED_DIALOG,
        Collaboration.DIRECTED_CONFIRM,
        Collaboration.DIRECTED_FAILURE,
        Collaboration.DIRECTED_RETRY,
        Collaboration.CROSS_DIALOG,
        Collaboration.CROSS_FOCUS_INPUT,
        Collaboration.CROSS_INTEGRATOR,
        Collaboration.CROSS_CONFIRM,
        Collaboration.CROSS_STATUS,
        Collaboration.CROSS_RETRY_FAILED,
        Collaboration.CROSS_SYNTHESIZE_AVAILABLE,
        Collaboration.CROSS_RESUME_SYNTHESIS,
        Collaboration.CROSS_FAILURE,
        StageResult.PANEL,
        StageResult.DRAFT_EMPTY,
        StageResult.DRAFT_CREATE,
        StageResult.DRAFT_CREATE_FROM_MESSAGES,
        StageResult.DRAFT_EDITOR,
        StageResult.DRAFT_SAVE,
        StageResult.DRAFT_SAVING,
        StageResult.DRAFT_SAVED,
        StageResult.DRAFT_SAVE_FAILURE,
        StageResult.DRAFT_CONFLICT,
        StageResult.DRAFT_ABANDON,
        StageResult.DRAFT_ABANDON_CONFIRMATION,
        StageResult.ARTIFACT_CONFIRM,
        StageResult.ARTIFACT_CONFIRMATION_DIALOG,
        StageResult.ARTIFACT_CONFIRMATION_CONFIRM,
        StageResult.ARTIFACT_CONFIRMATION_CANCEL,
        Context.DIALOG,
        Context.TOTAL,
        Context.VALIDATION_ERRORS,
        Context.CONFIRM,
        Context.CANCEL,
        AdvanceIssue.BUTTON,
        AdvanceIssue.DIALOG,
        AdvanceIssue.DIRECTION_STEP,
        AdvanceIssue.DIRECTION_REALITY_SUPPORT,
        AdvanceIssue.DIRECTION_THINKING_EXPANSION,
        AdvanceIssue.MEASURE_STEP,
        AdvanceIssue.CUSTOM_OBJECTIVE,
        AdvanceIssue.SUMMARY_STEP,
        AdvanceIssue.INHERITED_MATERIALS,
        AdvanceIssue.INHERITED_ARTIFACTS,
        AdvanceIssue.ROSTER,
        AdvanceIssue.EXPECTED_OUTPUT,
        AdvanceIssue.CONFIRM,
        AdvanceIssue.CANCEL,
        AdvanceIssue.WAIT_FOR_RUN,
        AdvanceIssue.STOP_CURRENT_RUN,
        AdvanceIssue.FAILURE,
        StageTimeline.TIMELINE,
        StageTimeline.CURRENT,
        StageTimeline.UNDO_BUTTON,
        StageTimeline.UNDO_CONFIRMATION,
        Settings.API_KEYS_ENTRY,
        Settings.API_KEYS_ACTION,
        Settings.TELEMETRY_ENTRY,
        Settings.TELEMETRY_ACTION,
    )

    fun normalizedStableId(rawValue: String): String {
        require(rawValue.isNotBlank()) { "稳定 ID 不能为空" }
        require(rawValue == rawValue.trim()) { "稳定 ID 不得包含首尾空白" }
        require(rawValue.length <= MAX_STABLE_ID_LENGTH) { "稳定 ID 长度超过限制" }
        require(stableIdPattern.matches(rawValue)) {
            "稳定 ID 只能包含 ASCII 字母、数字、点、下划线或连字符"
        }
        return rawValue
    }
}
