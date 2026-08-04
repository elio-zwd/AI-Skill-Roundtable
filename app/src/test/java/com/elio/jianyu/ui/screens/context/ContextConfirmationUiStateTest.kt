package com.elio.jianyu.ui.screens.context

import com.elio.jianyu.data.ContextSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextConfirmationUiStateTest {
    @Test
    fun personalBackgroundCandidatesRemainUnselectedAndUnauthorizedByDefault() {
        val candidate = candidate(ContextSourceType.PERSONAL_CONTEXT)
        val state = state(candidate)

        assertFalse(candidate.selected)
        assertFalse(candidate.networkAllowed)
        assertTrue(state.selectedItems.isEmpty())
        assertFalse(state.networkPermissionMissing)
    }

    @Test
    fun selectedContextCountsExactExcerptAndRequiresNetworkPermission() {
        val candidate = candidate(ContextSourceType.MATERIAL).copy(
            selected = true,
            selectedContent = "确认摘录",
        )
        val state = state(candidate)

        assertEquals(4, state.selectedCharacters)
        assertEquals(104, state.totalCharacters)
        assertTrue(state.networkPermissionMissing)
    }

    @Test
    fun sensitiveSelectionRequiresIndependentConfirmation() {
        val candidate = candidate(ContextSourceType.PERSONAL_CONTEXT).copy(
            selected = true,
            networkAllowed = true,
            sensitive = true,
            sensitiveConfirmed = false,
        )

        assertTrue(state(candidate).sensitiveConfirmationMissing)
    }

    private fun state(candidate: ContextCandidateUi) = ContextConfirmationUiState(
        visible = true,
        retryMode = false,
        runId = "run-1",
        issueId = "issue-1",
        stageId = "stage-1",
        currentUserInput = "问题",
        baseContextCharacters = 100,
        candidates = listOf(candidate),
    )

    private fun candidate(type: ContextSourceType) = ContextCandidateUi(
        sourceType = type,
        sourceId = "source-1",
        title = "来源",
        sourceKind = type.storageValue,
        sourceLocator = null,
        sourcePublishedAt = null,
        sourceCapturedAt = null,
        originalContent = "原始正文",
        selectedContent = "原始正文",
        sourceHash = "hash",
        sourceUpdatedAt = 1L,
        sensitive = false,
    )
}
