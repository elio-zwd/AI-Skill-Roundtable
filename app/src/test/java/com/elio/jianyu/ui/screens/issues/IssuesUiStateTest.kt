package com.elio.jianyu.ui.screens.issues

import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueNavigationItem
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.StageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuesUiStateTest {
    @Test
    fun mapIssueNavigation_sortsEachSectionByMostRecentUpdate() {
        val state = mapIssueNavigation(
            listOf(
                item("older", IssueLifecycleState.ACTIVE, updatedAt = 10L),
                item("newer", IssueLifecycleState.ACTIVE, updatedAt = 30L),
                item("archived", IssueLifecycleState.ARCHIVED, updatedAt = 20L),
            ),
        )

        assertTrue(state is IssuesUiState.Content)
        state as IssuesUiState.Content
        assertEquals(listOf("newer", "older"), state.active.map { it.issueId })
        assertEquals(listOf("archived"), state.archived.map { it.issueId })
        assertTrue(state.trashed.isEmpty())
    }

    @Test
    fun mapIssueNavigation_returnsEmptyForNoItems() {
        assertEquals(IssuesUiState.Empty, mapIssueNavigation(emptyList()))
    }

    @Test
    fun mapIssueRecovery_defaultsToCurrentStageAndKeepsLifecycle() {
        val snapshot = snapshot(
            state = IssueLifecycleState.ARCHIVED,
            currentStageId = "stage-2",
        )

        val result = mapIssueRecovery(snapshot, requestedStageId = null)

        assertTrue(result is IssueRecoveryUiState.Content)
        result as IssueRecoveryUiState.Content
        assertEquals(IssueLifecycleState.ARCHIVED, result.lifecycleState)
        assertEquals("stage-2", result.selectedStageId)
        assertEquals("阶段 2", result.selectedStageTitle)
        assertEquals(2, result.stageCount)
    }

    @Test
    fun mapIssueRecovery_supportsIssueWithoutCurrentStage() {
        val snapshot = snapshot(
            state = IssueLifecycleState.TRASHED,
            currentStageId = null,
        )

        val result = mapIssueRecovery(snapshot, requestedStageId = null)

        assertTrue(result is IssueRecoveryUiState.Content)
        result as IssueRecoveryUiState.Content
        assertEquals(IssueLifecycleState.TRASHED, result.lifecycleState)
        assertNull(result.selectedStageId)
        assertNull(result.selectedStageTitle)
    }

    private fun item(
        id: String,
        state: IssueLifecycleState,
        updatedAt: Long,
    ): IssueNavigationItem = IssueNavigationItem(
        issue = IssueEntity(
            id = id,
            title = "议题 $id",
            createdAt = 1L,
            updatedAt = updatedAt,
        ),
        lifecycle = IssueLifecycleEntity(
            issueId = id,
            state = state,
            stateChangedAt = updatedAt,
            updatedAt = updatedAt,
        ),
        currentStage = null,
        activeRunCount = 0,
    )

    private fun snapshot(
        state: IssueLifecycleState,
        currentStageId: String?,
    ): IssueRecoverySnapshot {
        val stages = listOf(
            StageEntity(
                id = "stage-1",
                issueId = "issue-1",
                sequenceIndex = 0,
                title = "阶段 1",
                objective = "目标 1",
                createdAt = 1L,
                updatedAt = 1L,
            ),
            StageEntity(
                id = "stage-2",
                issueId = "issue-1",
                sequenceIndex = 1,
                title = "阶段 2",
                objective = "目标 2",
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        return IssueRecoverySnapshot(
            core = IssueRecoveryCore(
                issue = IssueEntity(
                    id = "issue-1",
                    title = "议题",
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
                lifecycle = IssueLifecycleEntity(
                    issueId = "issue-1",
                    state = state,
                    stateChangedAt = 2L,
                    updatedAt = 2L,
                ),
                stages = stages,
                currentStage = stages.firstOrNull { it.id == currentStageId },
                runs = emptyList(),
                activeOrRecoverableRuns = emptyList(),
                participants = emptyList(),
                messages = emptyList(),
                pendingMessages = emptyList(),
            ),
            resources = IssueRecoveryResources(
                drafts = emptyList(),
                draftRevisions = emptyList(),
                artifacts = emptyList(),
                materialUsages = emptyList(),
                personalContextUsages = emptyList(),
                audioAssets = emptyList(),
            ),
        )
    }
}
