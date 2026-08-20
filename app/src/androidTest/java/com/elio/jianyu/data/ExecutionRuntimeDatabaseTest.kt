package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.execution.ExecutionErrorCode
import com.elio.jianyu.execution.JianyuExecutionPersistenceGateway
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionRuntimeDatabaseTest {
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
            officialSkillIdValidator = OfficialSkillIdValidator { true },
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun runtimeCreationPersistsQueuedParticipantsAndRootBudgetIdempotently() = runBlocking {
        saveIssue()
        val command = runtimeCommand()

        val first = repository.createExecutionRuntime(command).successValue()
        val repeated = repository.createExecutionRuntime(command)
        val reloaded = repository.getExecutionRuntime(RUN_ID).successValue()

        assertEquals(listOf(0, 1), first.participants.map { it.position })
        assertTrue(first.participantStates.all { it.status == ExecutionParticipantStatus.QUEUED })
        assertTrue((repeated as RepositoryResult.Success).idempotent)
        assertEquals(first, reloaded)
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun failedChildCreationRollsBackRunParticipantsAndStates() = runBlocking {
        saveIssue()
        repository.createExecutionRuntime(runtimeCommand()).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 190L,
                startedAt = 190L,
            ),
        ).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.RUNNING),
                newStatus = ExecutionRunStatus.RETRYABLE,
                updatedAt = 200L,
                startedAt = 190L,
                finishedAt = 200L,
            ),
        ).successValue()
        val child = runtimeCommand(
            runId = CHILD_RUN_ID,
            idempotencyKey = "retry-command",
            retryOfRunId = RUN_ID,
            budgetRootRunId = "missing-root",
        )

        val failure = repository.createExecutionRuntime(child)

        assertTrue((failure as RepositoryResult.Failure).error is RepositoryError.InvalidState)
        assertNull(database.jianyuRepositoryDao().getExecutionRun(CHILD_RUN_ID))
        assertTrue(database.jianyuRepositoryDao().getParticipantSnapshots(CHILD_RUN_ID).isEmpty())
        assertTrue(database.jianyuRepositoryDao().getParticipantStates(CHILD_RUN_ID).isEmpty())
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun concurrentApiCallRecordsAccumulateWithoutApplicationCeiling() = runBlocking {
        saveIssue()
        repository.createExecutionRuntime(
            runtimeCommand(),
        ).successValue()

        val results = coroutineScope {
            (1..8).map { attempt ->
                async {
                    repository.recordExecutionApiCall(
                        RecordExecutionApiCallCommand(
                            rootRunId = RUN_ID,
                            count = 1,
                            updatedAt = 300L + attempt,
                        ),
                    )
                }
            }.awaitAll()
        }
        val budget = repository.getExecutionRuntime(RUN_ID).successValue().budget

        assertEquals(8, results.count { it is RepositoryResult.Success })
        assertEquals(8, budget.usedApiCalls)
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun explicitRecoveryClosesPendingAndPreservesConsumedBudgetWithoutNetwork() = runBlocking {
        saveIssue()
        repository.createExecutionRuntime(runtimeCommand()).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 200L,
                startedAt = 200L,
            ),
        ).successValue()
        repository.recordExecutionApiCall(
            RecordExecutionApiCallCommand(
                rootRunId = RUN_ID,
                count = 1,
                updatedAt = 201L,
            ),
        ).successValue()
        repository.appendDomainMessage(
            AppendDomainMessageCommand(
                messageId = MESSAGE_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                executionRunId = RUN_ID,
                participantSnapshotId = "$RUN_ID-participant-0",
                senderId = "skill-a",
                senderName = "Skill A",
                avatar = "A",
                text = "部分文本",
                timestamp = 202L,
                isPending = true,
                roundIndex = 1,
                compatibilitySessionTitle = "Issue",
            ),
        ).successValue()
        repository.transitionExecutionParticipant(
            TransitionExecutionParticipantCommand(
                participantSnapshotId = "$RUN_ID-participant-0",
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionParticipantStatus.QUEUED),
                newStatus = ExecutionParticipantStatus.RUNNING,
                attemptIncrement = 1,
                startedAt = 200L,
                updatedAt = 201L,
            ),
        ).successValue()
        repository.transitionExecutionParticipant(
            TransitionExecutionParticipantCommand(
                participantSnapshotId = "$RUN_ID-participant-0",
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionParticipantStatus.RUNNING),
                newStatus = ExecutionParticipantStatus.STREAMING,
                outputMessageId = MESSAGE_ID,
                startedAt = 200L,
                updatedAt = 202L,
            ),
        ).successValue()

        val recovered = JianyuExecutionPersistenceGateway(repository).recoverInterrupted(
            RecoverInterruptedExecutionCommand(RUN_ID, 400L),
        )
        val issue = repository.recoverIssue(ISSUE_ID).successValue()

        assertEquals(ExecutionRunStatus.RETRYABLE, recovered.run.status)
        assertEquals(
            ExecutionParticipantStatus.RETRYABLE,
            recovered.participantStates.first().status,
        )
        assertEquals(
            ExecutionErrorCode.PROCESS_INTERRUPTED.storageValue,
            recovered.participantStates.first().lastErrorCode,
        )
        assertEquals(1, recovered.budget.usedApiCalls)
        assertEquals("部分文本", issue.core.messages.single().text)
        assertFalse(issue.core.messages.single().isPending)
        assertEquals(0, foreignKeyViolations())
    }

    private suspend fun saveIssue() {
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "Issue",
                initialStageId = STAGE_ID,
                initialStageTitle = "Stage",
                initialObjective = "Objective",
                createdAt = 100L,
            ),
        ).successValue()
    }

    private fun runtimeCommand(
        runId: String = RUN_ID,
        idempotencyKey: String = "command-1",
        retryOfRunId: String? = null,
        budgetRootRunId: String = runId,
        budget: ExecutionRuntimeBudgetConfig = ExecutionRuntimeBudgetConfig(),
    ): CreateExecutionRuntimeCommand {
        val run = ExecutionRunEntity(
            id = runId,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            triggerMessageId = null,
            idempotencyKey = idempotencyKey,
            retryOfRunId = retryOfRunId,
            createdAt = 150L,
            updatedAt = 150L,
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
        )
        val participants = listOf("skill-a", "skill-b").mapIndexed { index, skillId ->
            ExecutionParticipantSnapshotEntity(
                id = "$runId-participant-$index",
                runId = runId,
                sourceType = "official_skill",
                sourceId = skillId,
                displayName = "Skill ${index + 1}",
                avatar = "S",
                skillAssetPath = "skills/$skillId/SKILL.md",
                systemPrompt = "Prompt $index",
                configurationJson = "{}",
                defaultResponsibility = "",
                position = index,
                createdAt = 150L,
            )
        }
        return CreateExecutionRuntimeCommand(
            run = run,
            participants = participants,
            budgetRootRunId = budgetRootRunId,
            budget = budget,
        )
    }

    private fun foreignKeyViolations(): Int = database.openHelper.writableDatabase
        .query("PRAGMA foreign_key_check")
        .use { it.count }

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private companion object {
        const val ISSUE_ID = "execution-runtime-issue"
        const val STAGE_ID = "execution-runtime-stage"
        const val RUN_ID = "execution-runtime-run"
        const val CHILD_RUN_ID = "execution-runtime-retry"
        const val MESSAGE_ID = 9_001L
    }
}
