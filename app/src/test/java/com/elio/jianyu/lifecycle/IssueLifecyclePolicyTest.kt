package com.elio.jianyu.lifecycle

import com.elio.jianyu.data.IssueLifecycleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueLifecyclePolicyTest {
    @Test
    fun archivedIssueBlocksBusinessWritesButKeepsExplicitLifecycleActions() {
        val decision = IssueWriteAccessPolicy.evaluate(
            state = IssueLifecycleState.ARCHIVED,
            purgeRequested = false,
        )

        assertFalse(decision.allows(IssueWriteAction.CREATE_RUN))
        assertFalse(decision.allows(IssueWriteAction.DIRECTED_RESPONSE))
        assertFalse(decision.allows(IssueWriteAction.CROSS_DISCUSSION))
        assertFalse(decision.allows(IssueWriteAction.ADVANCE_STAGE))
        assertFalse(decision.allows(IssueWriteAction.SAVE_DRAFT))
        assertFalse(decision.allows(IssueWriteAction.CONFIRM_ARTIFACT))
        assertFalse(decision.allows(IssueWriteAction.GENERATE_AUDIO))
        assertFalse(decision.allows(IssueWriteAction.RECORD_CONTEXT_USAGE))
        assertTrue(decision.allows(IssueWriteAction.READ_HISTORY))
        assertTrue(decision.allows(IssueWriteAction.PLAY_AVAILABLE_AUDIO))
        assertTrue(decision.allows(IssueWriteAction.RESUME_ISSUE))
        assertTrue(decision.allows(IssueWriteAction.CREATE_RELATED_ISSUE))
        assertTrue(decision.allows(IssueWriteAction.MOVE_TO_TRASH))
    }

    @Test
    fun trashedIssueOnlyAllowsImpactRestoreAndPurgeEntry() {
        val decision = IssueWriteAccessPolicy.evaluate(
            state = IssueLifecycleState.TRASHED,
            purgeRequested = false,
        )

        assertEquals(
            setOf(
                IssueWriteAction.READ_PURGE_IMPACT,
                IssueWriteAction.RESTORE_FROM_TRASH,
                IssueWriteAction.REQUEST_PURGE,
            ),
            decision.allowedActions,
        )
    }

    @Test
    fun purgeRequestFreezesIssueAndOnlyAllowsStatusRetryOrSafeCancel() {
        val decision = IssueWriteAccessPolicy.evaluate(
            state = IssueLifecycleState.TRASHED,
            purgeRequested = true,
        )

        assertEquals(
            setOf(
                IssueWriteAction.READ_PURGE_STATUS,
                IssueWriteAction.RETRY_PURGE,
                IssueWriteAction.CANCEL_PURGE_BEFORE_FILE_DELETE,
            ),
            decision.allowedActions,
        )
    }

    @Test
    fun resumeRequiresChangeNoteOrExplicitNoChangeChoice() {
        assertFalse(ResumeChangeNotePolicy.isConfirmed("", noChangeConfirmed = false))
        assertFalse(ResumeChangeNotePolicy.isConfirmed("   ", noChangeConfirmed = false))
        assertTrue(ResumeChangeNotePolicy.isConfirmed("工作目标已调整", noChangeConfirmed = false))
        assertTrue(ResumeChangeNotePolicy.isConfirmed("", noChangeConfirmed = true))
        assertEquals("暂无变化", ResumeChangeNotePolicy.normalized("", noChangeConfirmed = true))
    }

    @Test
    fun purgeImpactHashIsStableAcrossMapAndListOrdering() {
        val first = IssuePurgeImpactSnapshot(
            issueId = "issue-1",
            databaseCounts = linkedMapOf("messages" to 4L, "stages" to 2L),
            formalFiles = listOf(
                PurgeFileImpact("b.wav", 20L),
                PurgeFileImpact("a.wav", 10L),
            ),
            pendingWorkNames = listOf("work-b", "work-a"),
            missingAssetIds = listOf("audio-b", "audio-a"),
            orphanRelativePaths = listOf("orphan-b.part", "orphan-a.wav"),
            relatedIssueCount = 1,
            externalObjectCount = 2,
        )
        val reordered = first.copy(
            databaseCounts = linkedMapOf("stages" to 2L, "messages" to 4L),
            formalFiles = first.formalFiles.reversed(),
            pendingWorkNames = first.pendingWorkNames.reversed(),
            missingAssetIds = first.missingAssetIds.reversed(),
            orphanRelativePaths = first.orphanRelativePaths.reversed(),
        )

        assertEquals(IssuePurgeImpactHasher.hash(first), IssuePurgeImpactHasher.hash(reordered))
        assertNotEquals(
            IssuePurgeImpactHasher.hash(first),
            IssuePurgeImpactHasher.hash(first.copy(relatedIssueCount = 2)),
        )
    }

    @Test
    fun archiveSummaryUsesCountsWithoutCopyingMessageBodies() {
        val impact = IssueArchiveImpact(
            issueId = "issue-1",
            currentStageTitle = "验证阶段",
            stageCount = 2,
            runCount = 3,
            activeWorkCount = 0,
            pendingMessageCount = 0,
            draftCount = 1,
            artifactCount = 2,
            audioAssetCount = 1,
            audioPendingCount = 0,
        )

        val summary = IssueArchiveSummaryFactory.create(impact, userNote = "后续等待评审")

        assertTrue(summary.contains("当前阶段：验证阶段"))
        assertTrue(summary.contains("Stage 数量：2"))
        assertTrue(summary.contains("正式成果数量：2"))
        assertTrue(summary.contains("用户备注：后续等待评审"))
        assertFalse(summary.contains("message正文"))
    }
}
