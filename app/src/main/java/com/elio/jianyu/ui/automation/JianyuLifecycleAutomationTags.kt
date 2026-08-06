package com.elio.jianyu.ui.automation

/** PR09-12 生命周期自动化标签，通过 [JianyuAutomationTags] 中央命名空间公开。 */
object JianyuLifecycleAutomationTags {
    object IssueLifecycle {
        const val ARCHIVE_BUTTON = "issue_archive_button"
        const val MOVE_TO_TRASH = "issue_move_to_trash"
        const val TRASH_CONFIRM = "issue_trash_confirm"
        const val RESTORE_FROM_TRASH = "issue_restore_from_trash"
    }

    object Archive {
        const val DIALOG = "issue_archive_dialog"
        const val WAIT = "issue_archive_wait"
        const val STOP = "issue_archive_stop"
        const val SUMMARY = "issue_archive_summary"
        const val CONFIRM = "issue_archive_confirm"
        const val CANCEL = "issue_archive_cancel"
    }

    object Resume {
        const val BUTTON = "issue_resume_button"
        const val CHANGE_NOTE = "issue_resume_change_note"
        const val NO_CHANGE = "issue_resume_no_change"
        const val CONFIRM = "issue_resume_confirm"
    }

    object RelatedIssue {
        const val BUTTON = "issue_related_new_button"
        const val TITLE = "issue_related_new_title"
        const val OBJECTIVE = "issue_related_new_objective"
        const val CONFIRM = "issue_related_new_confirm"
    }

    object Trash {
        const val IMPACT = "issue_trash_impact"
    }

    object Purge {
        const val BUTTON = "issue_purge_button"
        const val IMPACT = "issue_purge_impact"
        const val FIRST_CONFIRM = "issue_purge_first_confirm"
        const val FINAL_CONFIRM = "issue_purge_final_confirm"
        const val PROGRESS = "issue_purge_progress"
        const val RETRY = "issue_purge_retry"
        const val FAILURE = "issue_purge_failure"
    }

    val frozenStaticTags: List<String> = listOf(
        IssueLifecycle.ARCHIVE_BUTTON,
        IssueLifecycle.MOVE_TO_TRASH,
        IssueLifecycle.TRASH_CONFIRM,
        IssueLifecycle.RESTORE_FROM_TRASH,
        Archive.DIALOG,
        Archive.WAIT,
        Archive.STOP,
        Archive.SUMMARY,
        Archive.CONFIRM,
        Archive.CANCEL,
        Resume.BUTTON,
        Resume.CHANGE_NOTE,
        Resume.NO_CHANGE,
        Resume.CONFIRM,
        RelatedIssue.BUTTON,
        RelatedIssue.TITLE,
        RelatedIssue.OBJECTIVE,
        RelatedIssue.CONFIRM,
        Trash.IMPACT,
        Purge.BUTTON,
        Purge.IMPACT,
        Purge.FIRST_CONFIRM,
        Purge.FINAL_CONFIRM,
        Purge.PROGRESS,
        Purge.RETRY,
        Purge.FAILURE,
    )

    fun archiveEvent(stableId: String): String =
        "archive_event_${JianyuAutomationTags.normalizedStableId(stableId)}"

    fun resumeEvent(stableId: String): String =
        "resume_event_${JianyuAutomationTags.normalizedStableId(stableId)}"

    fun issueRelation(stableId: String): String =
        "issue_relation_${JianyuAutomationTags.normalizedStableId(stableId)}"

    fun purgeOperation(stableId: String): String =
        "purge_operation_${JianyuAutomationTags.normalizedStableId(stableId)}"

    fun purgeAudioAsset(stableAudioAssetId: String): String =
        "purge_audio_asset_${JianyuAutomationTags.normalizedStableId(stableAudioAssetId)}"
}

/** 保持调用方使用中央 `JianyuAutomationTags.Lifecycle` 命名空间。 */
val JianyuAutomationTags.Lifecycle: JianyuLifecycleAutomationTags
    get() = JianyuLifecycleAutomationTags
