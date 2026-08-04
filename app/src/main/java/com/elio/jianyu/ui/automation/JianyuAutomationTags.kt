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

    object Context {
        const val DIALOG = "context_confirmation_dialog"
        const val TOTAL = "context_confirmation_total"
        const val VALIDATION_ERRORS = "context_confirmation_validation_errors"
        const val CONFIRM = "context_confirmation_confirm"
        const val CANCEL = "context_confirmation_cancel"

        fun candidate(sourceType: String, sourceId: String): String =
            "context_candidate_${normalizedStableId(sourceType)}_${normalizedStableId(sourceId)}"
    }

    object Settings {
        const val API_KEYS_ENTRY = "settings_api_keys_entry"
        const val API_KEYS_ACTION = "settings_api_keys_action"
        const val TELEMETRY_ENTRY = "settings_telemetry_entry"
        const val TELEMETRY_ACTION = "settings_telemetry_action"
    }

    /**
     * PR-B 冻结的静态标签清单。使用 List 而非 Set，测试才能发现重复项。
     */
    val frozenStaticTags: List<String> = listOf(
        App.CONTENT_ROOT,
        App.BOTTOM_NAVIGATION,
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
        Execution.LOADING,
        Execution.FAILURE,
        Execution.STATUS,
        Execution.PARTICIPANTS,
        Execution.STOP,
        Execution.RETRY,
        Execution.RECOVER,
        Execution.CONTEXT,
        Context.DIALOG,
        Context.TOTAL,
        Context.VALIDATION_ERRORS,
        Context.CONFIRM,
        Context.CANCEL,
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
