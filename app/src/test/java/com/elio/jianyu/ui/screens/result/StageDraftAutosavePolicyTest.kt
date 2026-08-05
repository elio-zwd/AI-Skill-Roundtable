package com.elio.jianyu.ui.screens.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StageDraftAutosavePolicyTest {
    @Test
    fun debounceUsesEightHundredMilliseconds() {
        assertEquals(800L, StageDraftAutosavePolicy.DEBOUNCE_MILLIS)
    }

    @Test
    fun onlyChangedNonSavingDraftSchedulesPersistence() {
        assertTrue(
            StageDraftAutosavePolicy.shouldSchedule(
                draftId = "draft-1",
                editorContent = "新正文",
                persistedContent = "旧正文",
                saveStatus = StageDraftSaveStatus.Dirty,
            ),
        )
        assertFalse(
            StageDraftAutosavePolicy.shouldSchedule(
                draftId = "draft-1",
                editorContent = "相同正文",
                persistedContent = "相同正文",
                saveStatus = StageDraftSaveStatus.Dirty,
            ),
        )
        assertFalse(
            StageDraftAutosavePolicy.shouldSchedule(
                draftId = null,
                editorContent = "正文",
                persistedContent = "",
                saveStatus = StageDraftSaveStatus.Dirty,
            ),
        )
        assertFalse(
            StageDraftAutosavePolicy.shouldSchedule(
                draftId = "draft-1",
                editorContent = "新正文",
                persistedContent = "旧正文",
                saveStatus = StageDraftSaveStatus.Saving,
            ),
        )
    }
}
