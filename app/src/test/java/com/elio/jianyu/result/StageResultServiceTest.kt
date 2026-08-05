package com.elio.jianyu.result

import com.elio.jianyu.data.ArtifactDraftSourceEntity
import com.elio.jianyu.data.ArtifactMaterialSourceEntity
import com.elio.jianyu.data.ArtifactMessageSourceEntity
import com.elio.jianyu.data.ArtifactRunSourceEntity
import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.MaterialUsageSnapshotEntity
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.StageEntity
import com.elio.jianyu.data.StageSummaryDraftEntity
import com.elio.jianyu.data.StageSummaryDraftRevisionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class StageResultServiceTest {
    @Test
    fun firstDraftSaveCreatesRevisionOneAndPersistsExactContent() = runBlocking {
        val repository = mock<JianyuRepository>()
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(recovery()),
        )
        whenever(repository.saveStageDraft(any())).thenAnswer { invocation ->
            RepositoryResult.Success(
                invocation.getArgument<com.elio.jianyu.data.SaveStageDraftCommand>(0).draft,
            )
        }

        val result = StageResultService(repository).saveDraft(
            SaveStageResultDraftCommand(
                issueId = "issue-1",
                stageId = "stage-1",
                draftId = "draft-1",
                revisionId = "revision-1",
                expectedCurrentRevision = 0,
                content = "第一版草稿",
                savedAt = 100,
            ),
        )

        assertTrue(result is StageDraftWriteResult.Saved)
        val command = argumentCaptor<com.elio.jianyu.data.SaveStageDraftCommand>()
        verify(repository).saveStageDraft(command.capture())
        assertEquals(1, command.firstValue.draft.revisionNumber)
        assertEquals("第一版草稿", command.firstValue.revision.contentSnapshot)
        assertEquals("revision-1", command.firstValue.revision.id)
    }

    @Test
    fun identicalDraftReturnsUnchangedWithoutCreatingRevision() = runBlocking {
        val repository = mock<JianyuRepository>()
        val current = draft(content = "相同内容", revision = 2)
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(recovery(drafts = listOf(current))),
        )

        val result = StageResultService(repository).saveDraft(
            SaveStageResultDraftCommand(
                issueId = "issue-1",
                stageId = "stage-1",
                draftId = current.id,
                revisionId = "revision-3",
                expectedCurrentRevision = 2,
                content = "相同内容",
                savedAt = 100,
            ),
        )

        assertEquals(StageDraftWriteResult.Unchanged(current), result)
        verify(repository, never()).saveStageDraft(any())
        Unit
    }

    @Test
    fun staleEditorReturnsConflictWithoutOverwritingNewerRevision() = runBlocking {
        val repository = mock<JianyuRepository>()
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(recovery(drafts = listOf(draft(revision = 3)))),
        )

        val result = StageResultService(repository).saveDraft(
            SaveStageResultDraftCommand(
                issueId = "issue-1",
                stageId = "stage-1",
                draftId = "draft-1",
                revisionId = "revision-3",
                expectedCurrentRevision = 2,
                content = "过期页面内容",
                savedAt = 100,
            ),
        )

        assertEquals(StageDraftWriteResult.Conflict, result)
        verify(repository, never()).saveStageDraft(any())
        Unit
    }

    @Test
    fun confirmationUsesExactSavedDraftAndActualSelectedSources() = runBlocking {
        val repository = mock<JianyuRepository>()
        val draft = draft(content = "正式成果正文", revision = 2)
        val revision = revision(id = "revision-2", revision = 2, content = draft.content)
        val message = message(id = 11, runId = "run-1")
        val usage = materialUsage(id = "usage-1", runId = "run-1")
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(
                recovery(
                    drafts = listOf(draft),
                    revisions = listOf(revision),
                    messages = listOf(message),
                    materialUsages = listOf(usage),
                ),
            ),
        )
        whenever(repository.confirmArtifact(any())).thenAnswer { invocation ->
            RepositoryResult.Success(
                invocation.getArgument<com.elio.jianyu.data.ConfirmArtifactCommand>(0).artifact,
            )
        }

        val result = StageResultService(repository).confirmArtifact(
            ConfirmStageArtifactCommand(
                artifactId = "artifact-1",
                issueId = "issue-1",
                stageId = "stage-1",
                draftRevisionId = revision.id,
                title = "阶段总结",
                artifactType = ArtifactType.GENERAL_SUMMARY,
                selectedMessageIds = listOf(message.id),
                confirmedAt = 200,
            ),
        )

        assertTrue(result is StageArtifactConfirmationResult.Confirmed)
        val command = argumentCaptor<com.elio.jianyu.data.ConfirmArtifactCommand>()
        verify(repository).confirmArtifact(command.capture())
        assertEquals("正式成果正文", command.firstValue.artifact.content)
        assertEquals(
            listOf(ArtifactDraftSourceEntity("artifact-1", "issue-1", "revision-2", 200)),
            command.firstValue.sources.draftRevisions,
        )
        assertEquals(
            listOf(ArtifactMessageSourceEntity("artifact-1", "issue-1", 11, 200)),
            command.firstValue.sources.messages,
        )
        assertEquals(
            listOf(ArtifactRunSourceEntity("artifact-1", "issue-1", "run-1", 200)),
            command.firstValue.sources.runs,
        )
        assertEquals(
            listOf(ArtifactMaterialSourceEntity("artifact-1", "issue-1", "usage-1", 200)),
            command.firstValue.sources.materials,
        )
        assertFalse((result as StageArtifactConfirmationResult.Confirmed).artifact.content.isBlank())
    }

    @Test
    fun confirmationBeforeExactDraftRevisionExistsCreatesNoArtifact() = runBlocking {
        val repository = mock<JianyuRepository>()
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(recovery(drafts = listOf(draft()))),
        )

        val result = StageResultService(repository).confirmArtifact(
            ConfirmStageArtifactCommand(
                artifactId = "artifact-1",
                issueId = "issue-1",
                stageId = "stage-1",
                draftRevisionId = "missing-revision",
                title = "阶段总结",
                artifactType = ArtifactType.GENERAL_SUMMARY,
                selectedMessageIds = emptyList(),
                confirmedAt = 200,
            ),
        )

        assertEquals(
            StageArtifactConfirmationResult.Failure("draft_revision_not_saved"),
            result,
        )
        verify(repository, never()).confirmArtifact(any())
        Unit
    }

    @Test
    fun revisionForkIsRejectedBeforeRepositoryWrite() = runBlocking {
        val repository = mock<JianyuRepository>()
        val draft = draft()
        val revision = revision()
        val parent = artifact("parent")
        val existingChild = artifact("existing-child", revisionOf = parent.id)
        whenever(repository.recoverIssue("issue-1")).thenReturn(
            RepositoryResult.Success(
                recovery(
                    drafts = listOf(draft),
                    revisions = listOf(revision),
                    artifacts = listOf(parent, existingChild),
                ),
            ),
        )

        val result = StageResultService(repository).confirmArtifact(
            ConfirmStageArtifactCommand(
                artifactId = "second-child",
                issueId = "issue-1",
                stageId = "stage-1",
                draftRevisionId = revision.id,
                title = "修订版",
                artifactType = ArtifactType.GENERAL_SUMMARY,
                selectedMessageIds = emptyList(),
                revisionOfArtifactId = parent.id,
                confirmedAt = 200,
            ),
        )

        assertEquals(
            StageArtifactConfirmationResult.Failure("artifact_revision_fork"),
            result,
        )
        verify(repository, never()).confirmArtifact(any())
        Unit
    }

    private fun recovery(
        drafts: List<StageSummaryDraftEntity> = emptyList(),
        revisions: List<StageSummaryDraftRevisionEntity> = emptyList(),
        artifacts: List<ConfirmedArtifactEntity> = emptyList(),
        messages: List<Message> = emptyList(),
        materialUsages: List<MaterialUsageSnapshotEntity> = emptyList(),
    ): IssueRecoverySnapshot {
        val issue = IssueEntity("issue-1", "议题", 1, 1)
        val stage = StageEntity("stage-1", issue.id, 0, "阶段", "目标", 1, 1)
        return IssueRecoverySnapshot(
            core = IssueRecoveryCore(
                issue = issue,
                lifecycle = IssueLifecycleEntity(
                    issueId = issue.id,
                    state = IssueLifecycleState.ACTIVE,
                    stateChangedAt = 1,
                    updatedAt = 1,
                ),
                stages = listOf(stage),
                currentStage = stage,
                runs = emptyList(),
                activeOrRecoverableRuns = emptyList(),
                participants = emptyList(),
                messages = messages,
                pendingMessages = messages.filter { it.isPending },
            ),
            resources = IssueRecoveryResources(
                drafts = drafts,
                draftRevisions = revisions,
                artifacts = artifacts,
                materialUsages = materialUsages,
                personalContextUsages = emptyList(),
                audioAssets = emptyList(),
            ),
        )
    }

    private fun draft(
        content: String = "草稿正文",
        revision: Int = 1,
    ) = StageSummaryDraftEntity(
        id = "draft-1",
        issueId = "issue-1",
        stageId = "stage-1",
        content = content,
        revisionNumber = revision,
        createdAt = 1,
        updatedAt = revision.toLong(),
    )

    private fun revision(
        id: String = "revision-1",
        revision: Int = 1,
        content: String = "草稿正文",
    ) = StageSummaryDraftRevisionEntity(
        id = id,
        issueId = "issue-1",
        stageId = "stage-1",
        draftIdSnapshot = "draft-1",
        revisionNumber = revision,
        contentSnapshot = content,
        createdAt = revision.toLong(),
    )

    private fun message(id: Long, runId: String) = Message(
        id = id,
        chatId = 1,
        senderId = "skill",
        senderName = "成员",
        avatar = "A",
        text = "消息正文",
        timestamp = 10,
        isPending = false,
        issueId = "issue-1",
        stageId = "stage-1",
        executionRunId = runId,
    )

    private fun materialUsage(id: String, runId: String) = MaterialUsageSnapshotEntity(
        id = id,
        issueId = "issue-1",
        stageId = "stage-1",
        runId = runId,
        titleSnapshot = "资料",
        sourceTypeSnapshot = "note",
        contentSnapshot = "资料正文",
        contentHash = "hash",
        userConfirmedAt = 1,
        createdAt = 1,
    )

    private fun artifact(
        id: String,
        revisionOf: String? = null,
    ) = ConfirmedArtifactEntity(
        id = id,
        issueId = "issue-1",
        stageId = "stage-1",
        title = id,
        content = "成果",
        artifactType = "general_summary",
        contentFormat = "markdown",
        confirmedAt = 10,
        revisionOfArtifactId = revisionOf,
        createdAt = 10,
        updatedAt = 10,
    )
}
