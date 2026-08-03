package com.elio.jianyu.ui.screens.issues

import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueNavigationItem
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.StageEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuesNavigationLoaderTest {
    @Test
    fun load_readsEveryLifecycleStateAndMapsSections() = runBlocking {
        val reader = FakeIssueNavigationReader(
            listResult = RepositoryResult.Success(
                listOf(
                    navigationItem("active", IssueLifecycleState.ACTIVE, updatedAt = 30L),
                    navigationItem("archived", IssueLifecycleState.ARCHIVED, updatedAt = 20L),
                    navigationItem("trashed", IssueLifecycleState.TRASHED, updatedAt = 10L),
                ),
            ),
        )

        val state = IssuesNavigationLoader(reader).load()

        assertEquals(1, reader.listCalls.size)
        assertEquals(IssueLifecycleState.entries.toSet(), reader.listCalls.single())
        assertTrue(state is IssuesUiState.Content)
        state as IssuesUiState.Content
        assertEquals(listOf("active"), state.active.map { it.issueId })
        assertEquals(listOf("archived"), state.archived.map { it.issueId })
        assertEquals(listOf("trashed"), state.trashed.map { it.issueId })
    }

    @Test
    fun load_mapsRepositoryFailureWithoutRetryingOrWriting() = runBlocking {
        val reader = FakeIssueNavigationReader(
            listResult = RepositoryResult.Failure(
                RepositoryError.StorageFailure(
                    operation = "list_issue_navigation",
                    retryable = true,
                ),
            ),
        )

        val state = IssuesNavigationLoader(reader).load()

        assertEquals(IssuesUiState.Failure("议题读取失败，请重试"), state)
        assertEquals(1, reader.listCalls.size)
        assertTrue(reader.recoverCalls.isEmpty())
    }

    @Test
    fun recover_rejectsInvalidIdsBeforeCallingRepository() = runBlocking {
        val reader = FakeIssueNavigationReader()
        val loader = IssuesNavigationLoader(reader)

        val invalidIssue = loader.recover("issue/unsafe", null)
        val invalidStage = loader.recover("issue-1", "stage?unsafe")

        assertEquals(IssueRecoveryUiState.Failure("无效的议题 ID"), invalidIssue)
        assertEquals(IssueRecoveryUiState.Failure("无效的阶段 ID"), invalidStage)
        assertTrue(reader.recoverCalls.isEmpty())
    }

    @Test
    fun recover_callsOnlyRecoverIssueAndSelectsRequestedStage() = runBlocking {
        val snapshot = recoverySnapshot(
            issueId = "issue-1",
            stageIds = listOf("stage-1", "stage-2"),
            currentStageId = "stage-2",
        )
        val reader = FakeIssueNavigationReader(
            recoveryResult = RepositoryResult.Success(snapshot),
        )

        val state = IssuesNavigationLoader(reader).recover("issue-1", "stage-1")

        assertEquals(listOf("issue-1"), reader.recoverCalls)
        assertTrue(reader.listCalls.isEmpty())
        assertTrue(state is IssueRecoveryUiState.Content)
        state as IssueRecoveryUiState.Content
        assertEquals("stage-1", state.selectedStageId)
        assertEquals("阶段 stage-1", state.selectedStageTitle)
    }

    @Test
    fun recover_rejectsStageThatDoesNotBelongToIssue() = runBlocking {
        val reader = FakeIssueNavigationReader(
            recoveryResult = RepositoryResult.Success(
                recoverySnapshot(
                    issueId = "issue-1",
                    stageIds = listOf("stage-1"),
                    currentStageId = "stage-1",
                ),
            ),
        )

        val state = IssuesNavigationLoader(reader).recover("issue-1", "stage-other")

        assertEquals(
            IssueRecoveryUiState.Failure("指定阶段不属于该议题"),
            state,
        )
        assertEquals(listOf("issue-1"), reader.recoverCalls)
    }

    private class FakeIssueNavigationReader(
        private val listResult: RepositoryResult<List<IssueNavigationItem>> =
            RepositoryResult.Success(emptyList()),
        private val recoveryResult: RepositoryResult<IssueRecoverySnapshot> =
            RepositoryResult.Failure(
                RepositoryError.NotFound(
                    resource = "issue",
                    stableId = "missing",
                ),
            ),
    ) : IssueNavigationReader {
        val listCalls = mutableListOf<Set<IssueLifecycleState>>()
        val recoverCalls = mutableListOf<String>()

        override suspend fun listIssueNavigation(
            states: Set<IssueLifecycleState>,
        ): RepositoryResult<List<IssueNavigationItem>> {
            listCalls += states
            return listResult
        }

        override suspend fun recoverIssue(
            issueId: String,
        ): RepositoryResult<IssueRecoverySnapshot> {
            recoverCalls += issueId
            return recoveryResult
        }
    }

    private fun navigationItem(
        issueId: String,
        state: IssueLifecycleState,
        updatedAt: Long,
    ): IssueNavigationItem {
        val stage = StageEntity(
            id = "$issueId-stage",
            issueId = issueId,
            sequenceIndex = 0,
            title = "当前阶段",
            objective = "验证导航",
            createdAt = 1L,
            updatedAt = updatedAt,
        )
        return IssueNavigationItem(
            issue = IssueEntity(
                id = issueId,
                title = "议题 $issueId",
                createdAt = 1L,
                updatedAt = updatedAt,
            ),
            lifecycle = IssueLifecycleEntity(
                issueId = issueId,
                state = state,
                stateChangedAt = updatedAt,
                updatedAt = updatedAt,
            ),
            currentStage = stage,
            activeRunCount = 0,
        )
    }

    private fun recoverySnapshot(
        issueId: String,
        stageIds: List<String>,
        currentStageId: String,
    ): IssueRecoverySnapshot {
        val stages = stageIds.mapIndexed { index, stageId ->
            StageEntity(
                id = stageId,
                issueId = issueId,
                sequenceIndex = index,
                title = "阶段 $stageId",
                objective = "验证恢复",
                createdAt = index + 1L,
                updatedAt = index + 1L,
            )
        }
        return IssueRecoverySnapshot(
            core = IssueRecoveryCore(
                issue = IssueEntity(
                    id = issueId,
                    title = "恢复议题",
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
                lifecycle = IssueLifecycleEntity(
                    issueId = issueId,
                    state = IssueLifecycleState.ACTIVE,
                    stateChangedAt = 1L,
                    updatedAt = 2L,
                ),
                stages = stages,
                currentStage = stages.first { it.id == currentStageId },
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
