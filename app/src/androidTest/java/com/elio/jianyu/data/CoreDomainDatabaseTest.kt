package com.elio.jianyu.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreDomainDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var coreDomainDao: CoreDomainDao

    @Before
    fun setUp() {
        context.deleteDatabase(REOPEN_DATABASE)
        database = Room.inMemoryDatabaseBuilder(
            context,
            RoundtableDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        coreDomainDao = database.coreDomainDao()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(REOPEN_DATABASE)
    }

    @Test
    fun issueOwnsMultipleOrderedStages() = runBlocking {
        coreDomainDao.insertIssue(issue())
        coreDomainDao.insertStage(stage(id = "stage-2", sequenceIndex = 2))
        coreDomainDao.insertStage(stage(id = "stage-0", sequenceIndex = 0))
        coreDomainDao.insertStage(stage(id = "stage-1", sequenceIndex = 1))

        val stages = coreDomainDao.getStagesForIssue(ISSUE_ID)

        assertEquals(listOf(0, 1, 2), stages.map { it.sequenceIndex })
        assertTrue(stages.all { it.issueId == ISSUE_ID })
    }

    @Test
    fun orphanStageIsRejected() = runBlocking {
        assertConstraint {
            coreDomainDao.insertStage(stage(issueId = "missing-issue"))
        }
    }

    @Test
    fun duplicateRunIdempotencyKeyIsRejected() = runBlocking {
        insertIssueAndStage()
        coreDomainDao.insertExecutionRun(run(id = "run-1"))

        assertConstraint {
            coreDomainDao.insertExecutionRun(run(id = "run-2"))
        }
    }

    @Test
    fun participantSnapshotIsNotChangedByLiveCharacterUpdate() = runBlocking {
        insertIssueStageAndRun()
        val snapshot = participantSnapshot()
        coreDomainDao.insertParticipantSnapshots(listOf(snapshot))

        database.characterDao().insertCharacter(
            Character(
                id = snapshot.sourceId,
                name = "实时名称",
                avatar = "N",
                tagline = "new",
                systemPrompt = "实时 Prompt",
                order = 0
            )
        )

        val stored = coreDomainDao.getParticipantSnapshots(RUN_ID).single()
        assertEquals("历史名称", stored.displayName)
        assertEquals("历史 Prompt", stored.systemPrompt)
        assertEquals(0, stored.position)
    }

    @Test
    fun issueAndInitialStageCreationRollsBackWhenStageInsertFails() = runBlocking {
        val candidateIssue = issue(id = "rollback-issue")
        val invalidStage = stage(
            id = "rollback-stage",
            issueId = "missing-parent",
            sequenceIndex = 0
        )

        assertConstraint {
            coreDomainDao.createIssueWithInitialStage(candidateIssue, invalidStage)
        }

        assertNull(coreDomainDao.getIssue(candidateIssue.id))
    }

    @Test
    fun runAndParticipantsCreationRollsBackWhenSnapshotInsertFails() = runBlocking {
        insertIssueAndStage()
        val candidateRun = run()
        val first = participantSnapshot(id = "snapshot-1", position = 0)
        val duplicatePosition = participantSnapshot(id = "snapshot-2", position = 0)

        assertConstraint {
            coreDomainDao.createRunWithParticipants(
                candidateRun,
                listOf(first, duplicatePosition)
            )
        }

        assertNull(coreDomainDao.getExecutionRun(candidateRun.id))
        assertTrue(coreDomainDao.getParticipantSnapshots(candidateRun.id).isEmpty())
    }

    @Test
    fun messageBindingPreservesRoundIndexAndDomainRelations() = runBlocking {
        insertIssueStageAndRun()
        val snapshot = participantSnapshot()
        coreDomainDao.insertParticipantSnapshots(listOf(snapshot))
        val chatId = database.chatDao().insertSession(
            ChatSession(title = "兼容会话", createdAt = 10L)
        )
        val messageId = database.chatDao().insertMessage(
            Message(
                chatId = chatId,
                senderId = snapshot.sourceId,
                senderName = snapshot.displayName,
                avatar = snapshot.avatar,
                text = "历史消息",
                timestamp = 20L,
                roundIndex = 7
            )
        )

        val updatedRows = coreDomainDao.bindMessageToDomain(
            messageId = messageId,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = snapshot.id
        )
        val stored = coreDomainDao.getMessagesForStage(STAGE_ID).single()

        assertEquals(1, updatedRows)
        assertEquals(7, stored.roundIndex)
        assertEquals(ISSUE_ID, stored.issueId)
        assertEquals(STAGE_ID, stored.stageId)
        assertEquals(RUN_ID, stored.executionRunId)
        assertEquals(snapshot.id, stored.participantSnapshotId)
    }

    @Test
    fun deletingIssueWithHistoricalStageIsRestricted() = runBlocking {
        insertIssueAndStage()

        assertConstraint {
            coreDomainDao.deleteIssueById(ISSUE_ID)
        }

        assertTrue(coreDomainDao.getIssue(ISSUE_ID) != null)
        assertEquals(1, coreDomainDao.getStagesForIssue(ISSUE_ID).size)
    }

    @Test
    fun runningStageAndRunRecoverAfterDatabaseReopen() = runBlocking {
        database.close()
        val firstOpen = openPersistentDatabase()
        val firstDao = firstOpen.coreDomainDao()
        firstDao.createIssueWithInitialStage(issue(), stage())
        firstDao.insertExecutionRun(run(status = ExecutionRunStatus.RUNNING))
        firstOpen.close()

        val reopened = openPersistentDatabase()
        val reopenedDao = reopened.coreDomainDao()
        val restoredIssue = reopenedDao.getIssue(ISSUE_ID)
        val restoredStage = reopenedDao.getStagesForIssue(ISSUE_ID).single()
        val restoredRun = reopenedDao.getExecutionRun(RUN_ID)

        assertEquals(ISSUE_ID, restoredIssue?.id)
        assertEquals(STAGE_ID, restoredStage.id)
        assertEquals(ExecutionRunStatus.RUNNING, restoredRun?.status)
        assertEquals(STAGE_ID, restoredRun?.stageId)
        reopened.close()

        database = Room.inMemoryDatabaseBuilder(
            context,
            RoundtableDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        coreDomainDao = database.coreDomainDao()
    }

    private suspend fun insertIssueAndStage() {
        coreDomainDao.createIssueWithInitialStage(issue(), stage())
    }

    private suspend fun insertIssueStageAndRun() {
        insertIssueAndStage()
        coreDomainDao.insertExecutionRun(run())
    }

    private fun openPersistentDatabase(): RoundtableDatabase {
        return Room.databaseBuilder(
            context,
            RoundtableDatabase::class.java,
            REOPEN_DATABASE
        )
            .addMigrations(*RoundtableDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private fun issue(
        id: String = ISSUE_ID
    ) = IssueEntity(
        id = id,
        title = "测试议题",
        createdAt = 100L,
        updatedAt = 100L
    )

    private fun stage(
        id: String = STAGE_ID,
        issueId: String = ISSUE_ID,
        sequenceIndex: Int = 0
    ) = StageEntity(
        id = id,
        issueId = issueId,
        sequenceIndex = sequenceIndex,
        title = "阶段 $sequenceIndex",
        objective = "推进目标 $sequenceIndex",
        createdAt = 110L + sequenceIndex,
        updatedAt = 110L + sequenceIndex
    )

    private fun run(
        id: String = RUN_ID,
        status: ExecutionRunStatus = ExecutionRunStatus.NOT_STARTED
    ) = ExecutionRunEntity(
        id = id,
        issueId = ISSUE_ID,
        stageId = STAGE_ID,
        triggerMessageId = null,
        idempotencyKey = IDEMPOTENCY_KEY,
        status = status,
        retryOfRunId = null,
        createdAt = 120L,
        updatedAt = 120L,
        startedAt = if (status == ExecutionRunStatus.RUNNING) 121L else null,
        finishedAt = null,
        stoppedAt = null,
        failureCode = null,
        failureMessage = null
    )

    private fun participantSnapshot(
        id: String = SNAPSHOT_ID,
        position: Int = 0
    ) = ExecutionParticipantSnapshotEntity(
        id = id,
        runId = RUN_ID,
        sourceType = "character",
        sourceId = "character-1",
        displayName = "历史名称",
        avatar = "H",
        skillAssetPath = "skills/history/SKILL.md",
        systemPrompt = "历史 Prompt",
        configurationJson = "{\"temperature\":0.4}",
        defaultResponsibility = "反方审查",
        position = position,
        createdAt = 130L
    )

    private suspend fun assertConstraint(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a SQLite constraint failure")
        } catch (error: Throwable) {
            assertTrue(
                "Expected SQLiteConstraintException but was ${error::class.java.name}",
                error.hasConstraintCause()
            )
        }
    }

    private fun Throwable.hasConstraintCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SQLiteConstraintException) return true
            current = current.cause
        }
        return false
    }

    companion object {
        private const val ISSUE_ID = "issue-1"
        private const val STAGE_ID = "stage-1"
        private const val RUN_ID = "run-1"
        private const val SNAPSHOT_ID = "snapshot-1"
        private const val IDEMPOTENCY_KEY = "issue-1:stage-1:request-1"
        private const val REOPEN_DATABASE = "core-domain-reopen-test"
    }
}
