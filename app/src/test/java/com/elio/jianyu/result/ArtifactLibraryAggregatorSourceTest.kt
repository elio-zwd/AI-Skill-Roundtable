package com.elio.jianyu.result

import com.elio.jianyu.data.ArtifactDraftSourceEntity
import com.elio.jianyu.data.ArtifactMaterialSourceEntity
import com.elio.jianyu.data.ArtifactMessageSourceEntity
import com.elio.jianyu.data.ArtifactRunSourceEntity
import com.elio.jianyu.data.ArtifactSourceRecoverySnapshot
import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.StageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactLibraryAggregatorSourceTest {
    @Test
    fun aggregateMapsPersistedSourceRelationsWithoutInferringContent() {
        val snapshot = recovery()
        val sources = ArtifactSourceRecoverySnapshot(
            artifactId = "artifact-1",
            messages = listOf(ArtifactMessageSourceEntity("artifact-1", "issue-1", 11, 10)),
            runs = listOf(ArtifactRunSourceEntity("artifact-1", "issue-1", "run-1", 10)),
            draftRevisions = listOf(
                ArtifactDraftSourceEntity("artifact-1", "issue-1", "revision-1", 10),
            ),
            materials = listOf(
                ArtifactMaterialSourceEntity("artifact-1", "issue-1", "usage-1", 10),
            ),
        )

        val item = ArtifactLibraryAggregator.aggregate(
            snapshots = listOf(snapshot),
            sourcesByIssue = mapOf("issue-1" to listOf(sources)),
        ).items.single()

        assertTrue(item.sourcesAvailable)
        assertEquals(listOf(11L), item.sourceMessageIds)
        assertEquals(listOf("run-1"), item.sourceRunIds)
        assertEquals(listOf("revision-1"), item.sourceDraftRevisionIds)
        assertEquals(listOf("usage-1"), item.sourceMaterialUsageSnapshotIds)
    }

    @Test
    fun aggregateMarksSourcesUnavailableInsteadOfGuessingFromArtifactBody() {
        val item = ArtifactLibraryAggregator.aggregate(listOf(recovery())).items.single()

        assertTrue(!item.sourcesAvailable)
        assertTrue(item.sourceMessageIds.isEmpty())
        assertTrue(item.sourceRunIds.isEmpty())
        assertTrue(item.sourceDraftRevisionIds.isEmpty())
        assertTrue(item.sourceMaterialUsageSnapshotIds.isEmpty())
    }

    private fun recovery(): IssueRecoverySnapshot {
        val issue = IssueEntity("issue-1", "议题", 1, 1)
        val stage = StageEntity("stage-1", issue.id, 0, "阶段", "目标", 1, 1)
        val artifact = ConfirmedArtifactEntity(
            id = "artifact-1",
            issueId = issue.id,
            stageId = stage.id,
            title = "成果",
            content = "正文中即使出现 run-1 也不得反推来源",
            artifactType = ArtifactType.GENERAL_SUMMARY.storageValue,
            contentFormat = "markdown",
            confirmedAt = 10,
            createdAt = 10,
            updatedAt = 10,
        )
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
                drafts = emptyList(),
                draftRevisions = emptyList(),
                artifacts = listOf(artifact),
                materialUsages = emptyList(),
                personalContextUsages = emptyList(),
                audioAssets = emptyList(),
            ),
        )
    }
}
