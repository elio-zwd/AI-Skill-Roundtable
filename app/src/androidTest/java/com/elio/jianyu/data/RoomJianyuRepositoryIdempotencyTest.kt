package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomJianyuRepositoryIdempotencyTest {
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
            officialSkillIdValidator = OfficialSkillIdValidator { id -> id == SKILL_ID }
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun saveIssueRetryRemainsIdempotentAfterMessageAndLifecycleChanges() = runBlocking {
        val command = issueCommand()
        repository.saveIssue(command).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        repository.appendDomainMessage(messageCommand()).successValue()
        repository.archiveIssue(ISSUE_ID, 40L).successValue()

        val repeated = repository.saveIssue(command)
        val saved = repeated.successValue()

        assertTrue(repeated.isIdempotentSuccess())
        assertEquals(IssueLifecycleState.ARCHIVED, saved.lifecycle.state)
        assertNotNull(saved.issue.legacyChatSessionId)
        assertEquals(1, database.coreDomainDao().getStagesForIssue(ISSUE_ID).size)
    }

    @Test
    fun runCreationRetryRemainsIdempotentAfterRunStateChanges() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        val command = runCommand()
        repository.createExecutionRun(command).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 25L,
                startedAt = 25L
            )
        ).successValue()

        val repeated = repository.createExecutionRun(command)
        val snapshot = repeated.successValue()

        assertTrue(repeated.isIdempotentSuccess())
        assertEquals(ExecutionRunStatus.RUNNING, snapshot.run.status)
        assertEquals(2, snapshot.participants.size)
        assertEquals(1, database.coreDomainDao().getActiveRunsForStage(STAGE_ID).size)
    }

    @Test
    fun usageRequiresExplicitConfirmationAndRecoversIdempotently() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        val unconfirmed = materialUsage(userConfirmedAt = 0L)

        val rejected = repository.recordMaterialUsage(unconfirmed)
        assertTrue(rejected.failureError() is RepositoryError.ConstraintViolation)

        val confirmed = unconfirmed.copy(userConfirmedAt = 30L)
        repository.recordMaterialUsage(confirmed).successValue()
        val repeated = repository.recordMaterialUsage(confirmed)
        repository.recordPersonalContextUsage(personalContextUsage()).successValue()

        assertTrue(repeated.isIdempotentSuccess())
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        assertEquals(listOf(MATERIAL_USAGE_ID), recovery.resources.materialUsages.map { it.id })
        assertEquals(listOf(CONTEXT_USAGE_ID), recovery.resources.personalContextUsages.map { it.id })
    }

    @Test
    fun illegalLifecycleTransitionDoesNotChangeTrashedState() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.moveIssueToTrash(ISSUE_ID, 20L).successValue()

        val rejected = repository.archiveIssue(ISSUE_ID, 30L)
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()

        assertTrue(rejected.failureError() is RepositoryError.InvalidState)
        assertEquals(IssueLifecycleState.TRASHED, recovery.core.lifecycle.state)
        assertEquals(IssueLifecycleState.ACTIVE, recovery.core.lifecycle.previousState)
        assertEquals(20L, recovery.core.lifecycle.stateChangedAt)
    }

    private fun issueCommand(): SaveIssueCommand {
        return SaveIssueCommand(
            issueId = ISSUE_ID,
            title = "议题",
            initialStageId = STAGE_ID,
            initialStageTitle = "初始阶段",
            initialObjective = "理解问题",
            createdAt = 10L
        )
    }

    private fun runCommand(): CreateExecutionRunCommand {
        return CreateExecutionRunCommand(
            run = ExecutionRunEntity(
                id = RUN_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                idempotencyKey = "run-key",
                createdAt = 15L,
                updatedAt = 15L
            ),
            participants = listOf(
                participant("participant-a", SKILL_ID, 0),
                participant("participant-b", "skill-b", 1)
            )
        )
    }

    private fun participant(
        id: String,
        sourceId: String,
        position: Int
    ): ExecutionParticipantSnapshotEntity {
        return ExecutionParticipantSnapshotEntity(
            id = id,
            runId = RUN_ID,
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

    private fun messageCommand(): AppendDomainMessageCommand {
        return AppendDomainMessageCommand(
            messageId = 1001L,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = "participant-a",
            senderId = SKILL_ID,
            senderName = "Skill A",
            avatar = "A",
            text = "成员回答",
            timestamp = 30L,
            isPending = false,
            roundIndex = 1,
            compatibilitySessionTitle = "议题"
        )
    }

    private fun materialUsage(userConfirmedAt: Long): MaterialUsageSnapshotEntity {
        return MaterialUsageSnapshotEntity(
            id = MATERIAL_USAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            titleSnapshot = "资料",
            sourceTypeSnapshot = "text",
            contentSnapshot = "资料正文",
            contentHash = "material-hash",
            userConfirmedAt = userConfirmedAt,
            createdAt = 30L
        )
    }

    private fun personalContextUsage(): PersonalContextUsageSnapshotEntity {
        return PersonalContextUsageSnapshotEntity(
            id = CONTEXT_USAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            titleSnapshot = "个人背景",
            contentSnapshot = "背景正文",
            contentHash = "context-hash",
            userConfirmedAt = 30L,
            createdAt = 30L
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
        private const val ISSUE_ID = "issue-idempotency"
        private const val STAGE_ID = "stage-idempotency"
        private const val RUN_ID = "run-idempotency"
        private const val SKILL_ID = "skill-a"
        private const val MATERIAL_USAGE_ID = "material-usage-1"
        private const val CONTEXT_USAGE_ID = "context-usage-1"
    }
}
