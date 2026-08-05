package com.elio.jianyu.ui.screens.result

object StageDraftAutosavePolicy {
    const val DEBOUNCE_MILLIS = 800L

    fun shouldSchedule(
        draftId: String?,
        editorContent: String,
        persistedContent: String,
        saveStatus: StageDraftSaveStatus,
    ): Boolean = draftId != null &&
        editorContent != persistedContent &&
        saveStatus !is StageDraftSaveStatus.Saving &&
        saveStatus !is StageDraftSaveStatus.Conflict
}
