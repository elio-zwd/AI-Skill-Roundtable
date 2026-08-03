package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomJianyuRepositoryDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: JianyuRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(
            database = database,
            officialSkillIdValidator = OfficialSkillIdValidator { id -> id in setOf("skill-a", "skill-b") }
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun saveIssueCreatesOnlyIssueStageAndLifecycleAtomically() = runBlocking {
        val result = repository.saveIssue(issueCommand())

        val saved = result.successValue()
        assertEquals(ISSUE_ID, saved.issue.id)
        assertEquals(0, saved.initialStage.sequenceIndex)
        assertEquals(IssueLifecycleState.ACTIVE, saved.lifecycle.state)
        assertNull(saved.issue.legacyChatSessionId)
        assertTrue(database.coreDomainDao().getActiveRunsForStage(STAGE_ID).isEmpty())
        assertTrue(database.coreDomainDao().getParticipantSnapshots(RUN_ID).isEmpty())
        assertTrue(database.chatDao().getMessagesForChat(0L).isEmpty())
    }

    @Test
    fun saveIssueIsIdempotentAndConflictingPayloadIsRejected() = runBlocking {
        assertFalse(repository.saveIssue(issueCommand()).isIdempotentSuccess())
        assertTrue(repository.saveIssue(issueCommand()).isIdempotentSuccess())

        val conflict = repository.saveIssue(issueCommand(title = "不同标题"))
        assertTrue(conflict.failureError() is RepositoryError.IdempotencyConflict)
        assertEquals(1, database.coreDomainDao().getStagesForIssue(ISSUE_ID).size)
    }

    @Test
    fun stageCreationUsesDomainOrderAndOnlyLatestEmptyStageCanBeUndone() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        val stage1 = repository.createStage(
            CreateStageCommand(
                stageId = "stage-1",
                issueId = ISSUE_ID,
                title = "第二阶段",
                objective = "验证方案",
                createdAt = 20L
            )
        ).successValue()
        val stage2 = repository.createStage(
            CreateStageCommand(
                stageId = "stage-2",
                issueId = ISSUE_ID,
                title = "第三阶段",
                objective = "形成行动",
                createdAt = 30L
            )
        ).successValue()

        assertEquals(1, stage1.sequenceIndex)
        assertEquals(2, stage2.sequenceIndex)
        assertTrue(
            repository.undoLatestUnrunStage(ISSUE_ID, stage1.id).failureError()
                is RepositoryError.InvalidState
        )
        repository.undoLatestUnrunStage(ISSUE_ID, stage2.id).successValue()
        assertEquals(listOf(0, 1), database.coreDomainDao().getStagesForIssue(ISSUE_ID).map { it.sequenceIndex })
    }

    @Test
    fun runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        val command = runCommand()
        val conflictingCommand = runCommand(runId = CONFLICTING_RUN_ID)

        val first = repository.createExecutionRun(command).successValue()
        val repeated = repository.createExecutionRun(command)
        val conflict = repository.createExecutionRun(conflictingCommand)

        assertEquals(listOf(0, 1), first.participants.map { it.position })
        assertTrue(repeated.isIdempotentSuccess())
        assertTrue(conflict.failureError() is RepositoryError.IdempotencyConflict)
        assertEquals(command.run, database.coreDomainDao().getExecutionRun(RUN_ID))
        assertEquals(
            command.participants.sortedBy { it.position },
            database.coreDomainDao().getParticipantSnapshots(RUN_ID)
        )
        assertNull(database.coreDomainDao().getExecutionRun(CONFLICTING_RUN_ID))
        assertTrue(
            database.coreDomainDao().getParticipantSnapshots(CONFLICTING_RUN_ID).isEmpty()
        )
    }

    @Test
    fun runParticipantRelationMismatchReturnsConstraintViolationWithoutWrites() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        val originalCommand = runCommand()
        repository.createExecutionRun(originalCommand).successValue()
        val validDifferentRun = runCommand(runId = RELATION_MISMATCH_RUN_ID)
        val mismatchedCommand = validDifferentRun.copy(
            participants = validDifferentRun.participants.map { participant ->
                participant.copy(runId = RUN_ID)
            }
        )

        val result = repository.createExecutionRun(mismatchedCommand)

        assertTrue(result.failureError() is RepositoryError.ConstraintViolation)
        assertNull(database.coreDomainDao().getExecutionRun(RELATION_MISMATCH_RUN_ID))
        assertTrue(
            database.coreDomainDao().getParticipantSnapshots(RELATION_MISMATCH_RUN_ID).isEmpty()
        )
        assertEquals(originalCommand.run, database.coreDomainDao().getExecutionRun(RUN_ID))
        assertEquals(
            originalCommand.participants.sortedBy { it.position },
            database.coreDomainDao().getParticipantSnapshots(RUN_ID)
        )
        assertEquals(1, database.coreDomainDao().getStagesForIssue(ISSUE_ID).size)
    }

    @Test
    fun domainMessageUsesAbortAndCreatesCompatibilitySessionOnlyOnFirstMessage() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        val message = participantMessageCommand()

        val first = repository.appendDomainMessage(message).successValue()
        val recoveredIssue = database.coreDomainDao().getIssue(ISSUE_ID)
        val sessionId = requireNotNull(recoveredIssue?.legacyChatSessionId)
        val repeated = repository.appendDomainMessage(message)
        val conflict = repository.appendDomainMessage(message.copy(text = "不能覆盖成功消息"))

        assertEquals(MESSAGE_ID, first.id)
        assertEquals(sessionId, first.chatId)
        assertTrue(repeated.isIdempotentSuccess())
        assertTrue(conflict.failureError() is RepositoryError.IdempotencyConflict)
        assertEquals("成员回答", database.chatDao().getMessagesForChat(sessionId).single().text)
    }

    @Test
    fun runStatusUsesCompareAndSetAndSurvivesRecoveryReads() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()

        val running = repository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 20L,
                startedAt = 20L
            )
        ).successValue()
        val rejected = repository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.FAILED,
                updatedAt = 30L,
                failureCode = "late_failure"
            )
        )

        val before = running.updatedAt
        val recovery1 = repository.recoverIssue(ISSUE_ID).successValue()
        val recovery2 = repository.recoverIssue(ISSUE_ID).successValue()
        assertTrue(rejected.failureError() is RepositoryError.InvalidState)
        assertEquals(ExecutionRunStatus.RUNNING, recovery1.core.runs.single().status)
        assertEquals(before, recovery2.core.runs.single().updatedAt)
    }

    @Test
    fun draftRevisionAndArtifactSourcesRecoverWithoutDeletingDraft() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        repository.appendDomainMessage(participantMessageCommand()).successValue()
        val draft = StageSummaryDraftEntity(
            id = "draft-1",
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            content = "阶段摘要",
            revisionNumber = 1,
            createdAt = 40L,
            updatedAt = 40L
        )
        val revision = StageSummaryDraftRevisionEntity(
            id = "revision-1",
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            draftIdSnapshot = draft.id,
            revisionNumber = 1,
            contentSnapshot = draft.content,
            createdAt = 40L
        )
        repository.saveStageDraft(SaveStageDraftCommand(draft, revision)).successValue()
        repository.confirmArtifact(
            ConfirmArtifactCommand(
                artifact = ConfirmedArtifactEntity(
                    id = "artifact-1",
                    issueId = ISSUE_ID,
                    stageId = STAGE_ID,
                    title = "结论",
                    content = "确认内容",
                    artifactType = "decision",
                    contentFormat = "markdown",
                    confirmedAt = 50L,
                    createdAt = 50L,
                    updatedAt = 50L
                ),
                sources = ArtifactSources(
                    messages = listOf(
                        ArtifactMessageSourceEntity("artifact-1", ISSUE_ID, MESSAGE_ID, 50L)
                    ),
                    runs = listOf(ArtifactRunSourceEntity("artifact-1", ISSUE_ID, RUN_ID, 50L)),
                    draftRevisions = listOf(
                        ArtifactDraftSourceEntity("artifact-1", ISSUE_ID, "revision-1", 50L)
                    )
                )
            )
        ).successValue()

        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        assertEquals("阶段摘要", recovery.resources.drafts.single().content)
        assertEquals("确认内容", recovery.resources.artifacts.single().content)
        repository.abandonStageDraft(ISSUE_ID, STAGE_ID).successValue()
        val afterAbandon = repository.recoverIssue(ISSUE_ID).successValue()
        assertTrue(afterAbandon.resources.drafts.isEmpty())
        assertEquals(1, afterAbandon.resources.draftRevisions.size)
        assertEquals(1, afterAbandon.resources.artifacts.size)
    }

    @Test
    fun officialCombinationRequiresValidatorAndSoftDeleteKeepsMembers() = runBlocking {
        val combination = OfficialSkillCombinationEntity(
            id = "combo-1",
            name = "组合",
            createdAt = 10L,
            updatedAt = 10L
        )
        val invalid = repository.saveOfficialSkillCombination(
            SaveOfficialSkillCombinationCommand(
                combination,
                listOf(OfficialSkillCombinationMemberEntity("combo-1", "unknown", 0, null, 10L))
            )
        )
        assertTrue(invalid.failureError() is RepositoryError.ConstraintViolation)

        repository.saveOfficialSkillCombination(
            SaveOfficialSkillCombinationCommand(
                combination,
                listOf(
                    OfficialSkillCombinationMemberEntity("combo-1", "skill-a", 0, "主持", 10L),
                    OfficialSkillCombinationMemberEntity("combo-1", "skill-b", 1, null, 10L)
                )
            )
        ).successValue()
        repository.deleteOfficialSkillCombination(
            DeleteOfficialSkillCombinationCommand("combo-1", expectedUpdatedAt = 10L, deletedAt = 20L)
        ).successValue()

        val stored = repository.getOfficialSkillCombination("combo-1").successValue()
        assertEquals(20L, stored.combination.deletedAt)
        assertEquals(2, stored.members.size)
    }

    @Test
    fun lifecycleAndPurgeRequestNeverDeleteIssueOrStopRun() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                RUN_ID,
                setOf(ExecutionRunStatus.NOT_STARTED),
                ExecutionRunStatus.RUNNING,
                updatedAt = 20L,
                startedAt = 20L
            )
        ).successValue()

        repository.archiveIssue(ISSUE_ID, 30L).successValue()
        repository.moveIssueToTrash(ISSUE_ID, 40L).successValue()
        repository.requestIssuePurge(ISSUE_ID, 50L).successValue()

        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        assertEquals(IssueLifecycleState.TRASHED, recovery.core.lifecycle.state)
        assertEquals(50L, recovery.core.lifecycle.purgeRequestedAt)
        assertEquals(ExecutionRunStatus.RUNNING, recovery.core.runs.single().status)
        assertNotNull(database.coreDomainDao().getIssue(ISSUE_ID))
    }

    @Test
    fun closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue() = runBlocking {
        database.close()

        val result = repository.recoverIssue(ISSUE_ID)

        assertTrue(result.failureError() is RepositoryError.StorageFailure)
    }

    @Test
    fun foreignKeyCheckRemainsClean() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        repository.appendDomainMessage(participantMessageCommand()).successValue()

        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private fun issueCommand(title: String = "议题"): SaveIssueCommand {
        return SaveIssueCommand(
            issueId = ISSUE_ID,
            title = title,
            initialStageId = STAGE_ID,
            initialStageTitle = "初始阶段",
            initialObjective = "理解问题",
            createdAt = 10L
        )
    }

    private fun runCommand(
        runId: String = RUN_ID,
        idempotencyKey: String = "run-key-1"
    ): CreateExecutionRunCommand {
        val run = ExecutionRunEntity(
            id = runId,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            idempotencyKey = idempotencyKey,
            createdAt = 15L,
            updatedAt = 15L
        )
        val participantIdPrefix = if (runId == RUN_ID) "participant" else "$runId-participant"
        return CreateExecutionRunCommand(
            run = run,
            participants = listOf(
                participant("$participantIdPrefix-1", "skill-a", 0, runId),
                participant("$participantIdPrefix-2", "skill-b", 1, runId)
            )
        )
    }

    private fun participant(
        id: String,
        sourceId: String,
        position: Int,
        runId: String = RUN_ID
    ): ExecutionParticipantSnapshotEntity {
        return ExecutionParticipantSnapshotEntity(
            id = id,
            runId = runId,
            sourceType = "official_skill",
            sourceId = sourceId,
            displayName = sourceId,
            avatar = "A",
            skillAssetPath = "skills/$sourceId/SKILL.md",
            systemPrompt = "system",
            configurationJson = "{}",
            defaultResponsibility = "",
            position = position,
            createdAt = 15L
        )
    }

    private fun participantMessageCommand(): AppendDomainMessageCommand {
        return AppendDomainMessageCommand(
            messageId = MESSAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = "participant-1",
            senderId = "skill-a",
            senderName = "Skill A",
            avatar = "A",
            text = "成员回答",
            timestamp = 25L,
            isPending = false,
            roundIndex = 1,
            compatibilitySessionTitle = "议题"
        )
    }

    private fun <T> RepositoryResult<T>.successValue(): T {
        return (this as RepositoryResult.Success<T>).value
    }

    private fun RepositoryResult<*>.failureError(): RepositoryError {
        return (this as RepositoryResult.Failure).error
    }

    private fun RepositoryResult<*>.isIdempotentSuccess(): Boolean {
        return (this as? RepositoryResult.Success<*>)?.idempotent == true
    }

    companion object {
        private const val ISSUE_ID = "issue-1"
        private const val STAGE_ID = "stage-0"
        private const val RUN_ID = "run-1"
        private const val CONFLICTING_RUN_ID = "run-other"
        private const val RELATION_MISMATCH_RUN_ID = "run-mismatch"
        private const val MESSAGE_ID = 1001L
    }
}