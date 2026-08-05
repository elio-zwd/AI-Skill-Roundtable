package com.elio.jianyu.result

import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.MaterialUsageSnapshotEntity
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.StageEntity
import com.elio.jianyu.data.StageSummaryDraftEntity
import com.elio.jianyu.data.StageSummaryDraftRevisionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StageResultDomainTest {
    @Test
    fun artifactTypesUseStableBusinessValuesAndGeneralSummaryIsDefault() {
        assertEquals(ArtifactType.GENERAL_SUMMARY, ArtifactType.DEFAULT)
        assertEquals("general_summary", ArtifactType.GENERAL_SUMMARY.storageValue)
        assertEquals("行动方案", ArtifactType.ACTION_PLAN.displayName)
        assertEquals(ArtifactType.DELIVERABLE, ArtifactType.fromStorageValue("deliverable"))
        assertNull(ArtifactType.fromStorageValue("pdf"))
        assertEquals("markdown", ARTIFACT_CONTENT_FORMAT_MARKDOWN)
    }

    @Test
    fun genericDraftUsesDeterministicLocalStructureWithoutSelectingMessages() {
        val seed = GenericStageDraftBuilder.build()

        assertEquals(ArtifactType.GENERAL_SUMMARY, seed.defaultArtifactType)
        assertEquals(
            """## 阶段概述

## 已形成的判断

## 主要分歧

## 行动项

## 待确认事项""",
            seed.content,
        )
        assertTrue(seed.sourceMessages.isEmpty())
    }

    @Test
    fun messageSelectionDefaultsToEmptyAndSortsExplicitCompletedMessages() {
        val messages = listOf(
            message(id = 3, timestamp = 20, text = "第三条"),
            message(id = 2, timestamp = 10, text = "第二条"),
            message(id = 1, timestamp = 10, text = "第一条"),
        )

        assertEquals(
            StageMessageSelectionResult.Selected(emptyList()),
            StageMessageSelectionPolicy.select("issue-1", "stage-1", messages, emptyList()),
        )
        val selected = StageMessageSelectionPolicy.select(
            issueId = "issue-1",
            stageId = "stage-1",
            messages = messages,
            selectedMessageIds = listOf(3, 1, 2),
        ) as StageMessageSelectionResult.Selected

        assertEquals(listOf(1L, 2L, 3L), selected.messages.map { it.messageId })
        assertTrue(selected.messages.all { !it.pending })
    }

    @Test
    fun messageSelectionRejectsPendingCrossStageAndDuplicateIds() {
        val pending = message(id = 1, pending = true)
        val otherStage = message(id = 2, stageId = "stage-2")

        assertEquals(
            StageMessageSelectionError.MESSAGE_PENDING,
            (StageMessageSelectionPolicy.select(
                "issue-1",
                "stage-1",
                listOf(pending),
                listOf(1),
            ) as StageMessageSelectionResult.Rejected).error,
        )
        assertEquals(
            StageMessageSelectionError.MESSAGE_SCOPE_MISMATCH,
            (StageMessageSelectionPolicy.select(
                "issue-1",
                "stage-1",
                listOf(otherStage),
                listOf(2),
            ) as StageMessageSelectionResult.Rejected).error,
        )
        assertEquals(
            StageMessageSelectionError.DUPLICATE_MESSAGE_ID,
            (StageMessageSelectionPolicy.select(
                "issue-1",
                "stage-1",
                listOf(message(id = 4)),
                listOf(4, 4),
            ) as StageMessageSelectionResult.Rejected).error,
        )
    }

    @Test
    fun importedMessagesRemainAnEditableDraft() {
        val selected = StageMessageSelectionResult.Selected(
            listOf(
                StageMessageCandidate(1, "run-1", "成员甲", "判断 A", 1, pending = false),
                StageMessageCandidate(2, "run-2", "成员乙", "判断 B", 2, pending = false),
            ),
        )

        val seed = GenericStageDraftBuilder.build(selected.messages)

        assertTrue(seed.content.contains("## 选定来源消息"))
        assertTrue(seed.content.contains("判断 A"))
        assertTrue(seed.content.contains("判断 B"))
        assertEquals(listOf(1L, 2L), seed.sourceMessages.map { it.messageId })
        assertFalse(seed.confirmed)
    }

    @Test
    fun draftPolicyStartsAtOneSkipsIdenticalContentAndMapsRevisionConflict() {
        assertEquals(
            StageDraftSavePlan.Persist(revisionNumber = 1),
            StageDraftEditorPolicy.plan(current = null, editedContent = "第一版"),
        )
        val current = draft(content = "第一版", revision = 1)
        assertEquals(
            StageDraftSavePlan.Unchanged,
            StageDraftEditorPolicy.plan(current, "第一版"),
        )
        assertEquals(
            StageDraftSavePlan.Persist(revisionNumber = 2),
            StageDraftEditorPolicy.plan(current, "第二版"),
        )
        assertEquals(
            StageDraftSaveFailure.REVISION_CONFLICT,
            StageDraftEditorPolicy.mapFailure(
                RepositoryError.InvalidState("save_stage_draft", "revision_not_contiguous"),
            ),
        )
    }

    @Test
    fun sourceBuilderAlwaysIncludesExactDraftAndOnlyActualSelectedRunMaterials() {
        val draftRevision = revision(id = "draft-revision-2", revision = 2)
        val messages = listOf(
            message(id = 2, runId = "run-b", timestamp = 20),
            message(id = 1, runId = "run-a", timestamp = 10),
        )
        val usages = listOf(
            materialUsage(id = "usage-b", runId = "run-b"),
            materialUsage(id = "usage-unused", runId = "run-c"),
            materialUsage(id = "usage-a", runId = "run-a"),
        )

        val result = ArtifactSourceBuilder.build(
            issueId = "issue-1",
            stageId = "stage-1",
            draftRevision = draftRevision,
            selectedMessages = messages,
            materialUsages = usages,
        ) as ArtifactSourceBuildResult.Ready

        assertEquals(listOf("draft-revision-2"), result.plan.draftRevisionIds)
        assertEquals(listOf(1L, 2L), result.plan.messageIds)
        assertEquals(listOf("run-a", "run-b"), result.plan.runIds)
        assertEquals(listOf("usage-a", "usage-b"), result.plan.materialUsageSnapshotIds)
    }

    @Test
    fun sourceBuilderRejectsPendingDuplicateAndCrossScopeSources() {
        val revision = revision()

        assertEquals(
            ArtifactSourceError.MESSAGE_PENDING,
            (ArtifactSourceBuilder.build(
                "issue-1",
                "stage-1",
                revision,
                listOf(message(id = 1, pending = true)),
                emptyList(),
            ) as ArtifactSourceBuildResult.Rejected).error,
        )
        assertEquals(
            ArtifactSourceError.DUPLICATE_MESSAGE_ID,
            (ArtifactSourceBuilder.build(
                "issue-1",
                "stage-1",
                revision,
                listOf(message(id = 1), message(id = 1)),
                emptyList(),
            ) as ArtifactSourceBuildResult.Rejected).error,
        )
        assertEquals(
            ArtifactSourceError.DRAFT_SCOPE_MISMATCH,
            (ArtifactSourceBuilder.build(
                "issue-1",
                "stage-1",
                revision(issueId = "issue-2"),
                emptyList(),
                emptyList(),
            ) as ArtifactSourceBuildResult.Rejected).error,
        )
    }

    @Test
    fun revisionResolverFindsLatestVersionWithoutOverwritingHistory() {
        val first = artifact(id = "artifact-1", confirmedAt = 10)
        val second = artifact(id = "artifact-2", revisionOf = first.id, confirmedAt = 20)
        val resolution = ArtifactRevisionResolver.resolve(listOf(second, first))

        assertTrue(resolution.problems.isEmpty())
        assertEquals(listOf("artifact-1", "artifact-2"), resolution.chains.single().versions.map { it.id })
        assertEquals(setOf("artifact-2"), resolution.latestArtifactIds)
        assertTrue(resolution.allArtifacts.map { it.id }.contains("artifact-1"))
    }

    @Test
    fun revisionResolverDetectsSelfCycleMultiNodeCycleCrossStageAndFork() {
        val self = artifact(id = "self", revisionOf = "self")
        val cycleA = artifact(id = "cycle-a", revisionOf = "cycle-b")
        val cycleB = artifact(id = "cycle-b", revisionOf = "cycle-a")
        val parent = artifact(id = "parent")
        val crossStage = artifact(id = "cross", stageId = "stage-2", revisionOf = parent.id)
        val childA = artifact(id = "child-a", revisionOf = parent.id)
        val childB = artifact(id = "child-b", revisionOf = parent.id)

        val problems = ArtifactRevisionResolver.resolve(
            listOf(self, cycleA, cycleB, parent, crossStage, childA, childB),
        ).problems.map { it.code }.toSet()

        assertTrue(ArtifactRevisionProblemCode.SELF_CYCLE in problems)
        assertTrue(ArtifactRevisionProblemCode.CYCLE in problems)
        assertTrue(ArtifactRevisionProblemCode.CROSS_STAGE in problems)
        assertTrue(ArtifactRevisionProblemCode.FORK in problems)
    }

    @Test
    fun artifactLibraryShowsOnlyConfirmedArtifactsAndFiltersLatestSearchAndType() {
        val first = artifact(id = "a1", title = "旧行动计划", type = "action_plan", confirmedAt = 10)
        val latest = artifact(
            id = "a2",
            title = "新行动计划",
            content = "第一步：验证",
            type = "action_plan",
            confirmedAt = 20,
            revisionOf = first.id,
        )
        val note = artifact(
            id = "n1",
            title = "知识笔记",
            content = "传感器结论",
            type = "knowledge_note",
            confirmedAt = 15,
        )
        val snapshot = recoverySnapshot(
            artifacts = listOf(first, latest, note),
            drafts = listOf(draft()),
        )

        val library = ArtifactLibraryAggregator.aggregate(listOf(snapshot))
        val visible = ArtifactLibraryAggregator.visibleItems(
            snapshot = library,
            query = "验证",
            types = setOf(ArtifactType.ACTION_PLAN),
            includeHistory = false,
        )

        assertEquals(listOf("a2"), visible.map { it.artifactId })
        assertFalse(library.items.any { it.artifactId == "draft-1" })
        assertEquals(3, library.items.size)
        assertEquals(setOf("a2", "n1"), library.items.filter { it.latest }.map { it.artifactId }.toSet())
    }

    private fun message(
        id: Long,
        issueId: String = "issue-1",
        stageId: String = "stage-1",
        runId: String? = "run-1",
        timestamp: Long = 1,
        text: String = "正文",
        pending: Boolean = false,
    ) = Message(
        id = id,
        chatId = 1,
        senderId = "skill-1",
        senderName = "成员",
        avatar = "A",
        text = text,
        timestamp = timestamp,
        isPending = pending,
        issueId = issueId,
        stageId = stageId,
        executionRunId = runId,
    )

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
        id: String = "draft-revision-1",
        issueId: String = "issue-1",
        stageId: String = "stage-1",
        revision: Int = 1,
    ) = StageSummaryDraftRevisionEntity(
        id = id,
        issueId = issueId,
        stageId = stageId,
        draftIdSnapshot = "draft-1",
        revisionNumber = revision,
        contentSnapshot = "草稿正文",
        createdAt = revision.toLong(),
    )

    private fun artifact(
        id: String,
        issueId: String = "issue-1",
        stageId: String = "stage-1",
        title: String = id,
        content: String = "成果正文",
        type: String = "general_summary",
        confirmedAt: Long = 1,
        revisionOf: String? = null,
    ) = ConfirmedArtifactEntity(
        id = id,
        issueId = issueId,
        stageId = stageId,
        title = title,
        content = content,
        artifactType = type,
        contentFormat = "markdown",
        confirmedAt = confirmedAt,
        revisionOfArtifactId = revisionOf,
        createdAt = confirmedAt,
        updatedAt = confirmedAt,
    )

    private fun materialUsage(
        id: String,
        runId: String,
        issueId: String = "issue-1",
        stageId: String = "stage-1",
    ) = MaterialUsageSnapshotEntity(
        id = id,
        issueId = issueId,
        stageId = stageId,
        runId = runId,
        titleSnapshot = "资料",
        sourceTypeSnapshot = "note",
        contentSnapshot = "资料正文",
        contentHash = "hash",
        userConfirmedAt = 1,
        createdAt = 1,
    )

    private fun recoverySnapshot(
        artifacts: List<ConfirmedArtifactEntity>,
        drafts: List<StageSummaryDraftEntity>,
    ): IssueRecoverySnapshot {
        val issue = IssueEntity("issue-1", "测试议题", 1, 1)
        val stage = StageEntity("stage-1", issue.id, 0, "阶段一", "目标", 1, 1)
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
                messages = emptyList(),
                pendingMessages = emptyList(),
            ),
            resources = IssueRecoveryResources(
                drafts = drafts,
                draftRevisions = emptyList(),
                artifacts = artifacts,
                materialUsages = emptyList(),
                personalContextUsages = emptyList(),
                audioAssets = emptyList(),
            ),
        )
    }
}
