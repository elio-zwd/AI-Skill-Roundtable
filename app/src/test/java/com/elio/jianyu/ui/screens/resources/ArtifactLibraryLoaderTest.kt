package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueNavigationItem
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.StageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ArtifactLibraryLoaderTest {
    @Test
    fun navigationFailureReturnsArtifactFailureWithoutReadingIssues() = runBlocking {
        val repository = mock<JianyuRepository>()
        whenever(
            repository.listIssueNavigation(
                setOf(IssueLifecycleState.ACTIVE, IssueLifecycleState.ARCHIVED),
            ),
        ).thenReturn(
            RepositoryResult.Failure(
                RepositoryError.StorageFailure("list_issue_navigation", retryable = true),
            ),
        )

        val state = ArtifactLibraryLoader(repository).load()

        assertEquals(
            ArtifactLibraryUiState.Failure("artifact_load_failed"),
            state,
        )
    }

    @Test
    fun oneRecoveryFailureKeepsOtherIssueArtifactsAsPartialContent() = runBlocking {
        val repository = mock<JianyuRepository>()
        val first = navigation("issue-1", "议题一")
        val second = navigation("issue-2", "议题二")
        whenever(
            repository.listIssueNavigation(
                setOf(IssueLifecycleState.ACTIVE, IssueLifecycleState.ARCHIVED),
            ),
        ).thenReturn(RepositoryResult.Success(listOf(first, second)))
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(recovery("issue-1", "议题一", "artifact-1")),
        )
        whenever(repository.recoverIssue("issue-2")).thenReturn(
            RepositoryResult.Failure(
                RepositoryError.StorageFailure("recover_issue", retryable = true),
            ),
        )

        val state = ArtifactLibraryLoader(repository).load()

        assertTrue(state is ArtifactLibraryUiState.PartialFailure)
        state as ArtifactLibraryUiState.PartialFailure
        assertEquals("artifact_partial_load_failed", state.errorCode)
        assertEquals(listOf("artifact-1"), state.content.visibleItems.map { it.artifactId })
    }

    @Test
    fun successfulRecoveryWithoutArtifactsReturnsEmpty() = runBlocking {
        val repository = mock<JianyuRepository>()
        val navigation = navigation("issue-1", "议题一")
        whenever(
            repository.listIssueNavigation(
                setOf(IssueLifecycleState.ACTIVE, IssueLifecycleState.ARCHIVED),
            ),
        ).thenReturn(RepositoryResult.Success(listOf(navigation)))
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(recovery("issue-1", "议题一", artifactId = null)),
        )

        assertEquals(ArtifactLibraryUiState.Empty, ArtifactLibraryLoader(repository).load())
    }

    private fun navigation(issueId: String, title: String): IssueNavigationItem {
        val issue = IssueEntity(issueId, title, 1, 1)
        val stage = StageEntity("stage-$issueId", issueId, 0, "阶段", "目标", 1, 1)
        return IssueNavigationItem(
            issue = issue,
            lifecycle = IssueLifecycleEntity(
                issueId = issueId,
                state = IssueLifecycleState.ACTIVE,
                stateChangedAt = 1,
                updatedAt = 1,
            ),
            currentStage = stage,
            activeRunCount = 0,
        )
    }

    private fun recovery(
        issueId: String,
        title: String,
        artifactId: String?,
    ): IssueRecoverySnapshot {
        val issue = IssueEntity(issueId, title, 1, 1)
        val stage = StageEntity("stage-$issueId", issueId, 0, "阶段", "目标", 1, 1)
        val artifacts = artifactId?.let {
            listOf(
                ConfirmedArtifactEntity(
                    id = it,
                    issueId = issueId,
                    stageId = stage.id,
                    title = "阶段总结",
                    content = "摘要",
                    artifactType = "general_summary",
                    contentFormat = "markdown",
                    confirmedAt = 1,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            )
        }.orEmpty()
        return IssueRecoverySnapshot(
            core = IssueRecoveryCore(
                issue = issue,
                lifecycle = IssueLifecycleEntity(
                    issueId = issueId,
                    state = IssueLifecycleState.ACTIVE,
                    stateChangedAt = 1,
                    updatedAt = 1,
                ),
                stages = listOf(stage),
                currentStage = stage,
                runs = emptyList(),
                activeOrRecoverableRuns = emptyList(),
                participants = emptyList(),
                messages = emptyList(),
                pendingMessages = emptyList(),
            ),
            resources = IssueRecoveryResources(
                drafts = emptyList(),
                draftRevisions = emptyList(),
                artifacts = artifacts,
                materialUsages = emptyList(),
                personalContextUsages = emptyList(),
                audioAssets = emptyList(),
            ),
        )
    }
}
