package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StageAdvancementRepositoryDatabaseTest {
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
            officialSkillIdValidator = OfficialSkillIdValidator {
                it in setOf("skill-a", "skill-b")
            },
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun advanceIssueAtomicallyCreatesOneStageAndReturnsIdempotentSnapshot() = runBlocking {
        prepareCompletedStandardRun()
        val command = advanceCommand()

        val first = repository.advanceIssue(command).successValue()
        val repeated = repository.advanceIssue(command.copy(confirmedAt = 50L))
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()

        assertEquals(NEW_STAGE_ID, first.snapshot.stage.id)
        assertEquals(1, first.snapshot.stage.sequenceIndex)
        assertEquals(SOURCE_STAGE_ID, first.snapshot.advancement.sourceStageId)
        assertEquals(listOf("skill-a", "skill-b"), first.snapshot.roster.map { it.officialSkillId })
        assertTrue(repeated.isIdempotentSuccess())
        assertEquals(listOf(SOURCE_STAGE_ID, NEW_STAGE_ID), recovery.core.stages.map { it.id })
        assertTrue(recovery.core.runs.none { it.stageId == NEW_STAGE_ID })
        assertTrue(recovery.core.messages.none { it.stageId == NEW_STAGE_ID })
        assertTrue(recovery.resources.drafts.none { it.stageId == NEW_STAGE_ID })
        assertTrue(recovery.resources.artifacts.none { it.stageId == NEW_STAGE_ID })
        assertEquals(
            0,
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { it.count },
        )
    }

    @Test
    fun plannedRosterCanAdvanceAgainBeforeTheNewStageCreatesAnyRun() = runBlocking {
        prepareCompletedStandardRun()
        repository.advanceIssue(advanceCommand()).successValue()

        val second = repository.advanceIssue(
            advanceCommand(
                operationId = SECOND_OPERATION_ID,
                sourceStageId = NEW_STAGE_ID,
                newStageId = SECOND_STAGE_ID,
                objective = "继续验证计划",
                confirmedAt = 50L,
            ),
        ).successValue()

        assertEquals(SECOND_STAGE_ID, second.snapshot.stage.id)
        assertEquals(NEW_STAGE_ID, second.snapshot.advancement.sourceStageId)
        assertEquals(SOURCE_RUN_ID, second.snapshot.roster.first().sourceRunId)
        assertEquals(
            listOf(SOURCE_STAGE_ID, NEW_STAGE_ID, SECOND_STAGE_ID),
            repository.recoverIssue(ISSUE_ID).successValue().core.stages.map { it.id },
        )
    }

    @Test
    fun sameOperationWithDifferentPayloadConflictsWithoutCreatingAnotherStage() = runBlocking {
        prepareCompletedStandardRun()
        repository.advanceIssue(advanceCommand()).successValue()

        val conflict = repository.advanceIssue(
            advanceCommand(newStageId = "stage-conflict", objective = "不同目标"),
        )
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()

        assertTrue(conflict.failureError() is RepositoryError.IdempotencyConflict)
        assertEquals(listOf(SOURCE_STAGE_ID, NEW_STAGE_ID), recovery.core.stages.map { it.id })
        assertTrue(
            repository.getStageAdvancement("stage-conflict").failureError()
                is RepositoryError.NotFound,
        )
    }

    @Test
    fun invalidInheritedRelationRollsBackAllAdvancementWrites() = runBlocking {
        prepareCompletedStandardRun()

        val result = repository.advanceIssue(
            advanceCommand(inheritedMaterialIds = listOf("missing-material")),
        )
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()

        assertTrue(result.failureError() is RepositoryError.ConstraintViolation)
        assertEquals(listOf(SOURCE_STAGE_ID), recovery.core.stages.map { it.id })
        assertTrue(repository.listStageAdvancements(ISSUE_ID).successValue().isEmpty())
    }

    @Test
    fun latestNeverRunStageCanBeUndoneTogetherWithAdvancementRelations() = runBlocking {
        prepareCompletedStandardRun()
        repository.advanceIssue(advanceCommand()).successValue()

        val result = repository.undoLatestUnrunStage(ISSUE_ID, NEW_STAGE_ID)
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()

        assertFalse(result.isIdempotentSuccess())
        assertEquals(listOf(SOURCE_STAGE_ID), recovery.core.stages.map { it.id })
        assertTrue(repository.listStageAdvancements(ISSUE_ID).successValue().isEmpty())
        assertTrue(repository.undoLatestUnrunStage(ISSUE_ID, NEW_STAGE_ID).isIdempotentSuccess())
    }

    @Test
    fun anyRunPermanentlyBlocksNeverRunUndoEvenAfterRunStops() = runBlocking {
        prepareCompletedStandardRun()
        repository.advanceIssue(advanceCommand()).successValue()
        repository.createExecutionRun(
            runCommand(
                runId = NEW_STAGE_RUN_ID,
                stageId = NEW_STAGE_ID,
                idempotencyKey = "new-stage-run-key",
            ),
        ).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = NEW_STAGE_RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 60L,
                startedAt = 60L,
            ),
        ).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = NEW_STAGE_RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.RUNNING),
                newStatus = ExecutionRunStatus.STOPPED,
                updatedAt = 70L,
                stoppedAt = 70L,
                failureCode = "user_stopped",
            ),
        ).successValue()

        val result = repository.undoLatestUnrunStage(ISSUE_ID, NEW_STAGE_ID)

        assertTrue(result.failureError() is RepositoryError.InvalidState)
        assertEquals(
            NEW_STAGE_ID,
            repository.recoverIssue(ISSUE_ID).successValue().core.currentStage?.id,
        )
    }

    @Test
    fun activeSourceRunBlocksAdvanceWithoutStoppingOrWriting() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()

        val result = repository.advanceIssue(advanceCommand())
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()

        assertTrue(result.failureError() is RepositoryError.InvalidState)
        assertEquals(ExecutionRunStatus.NOT_STARTED, recovery.core.runs.single().status)
        assertEquals(listOf(SOURCE_STAGE_ID), recovery.core.stages.map { it.id })
        assertTrue(repository.listStageAdvancements(ISSUE_ID).successValue().isEmpty())
    }

    private suspend fun prepareCompletedStandardRun() {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = SOURCE_RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 20L,
                startedAt = 20L,
            ),
        ).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = SOURCE_RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.RUNNING),
                newStatus = ExecutionRunStatus.SUCCEEDED,
                updatedAt = 30L,
                finishedAt = 30L,
            ),
        ).successValue()
    }

    private fun issueCommand() = SaveIssueCommand(
        issueId = ISSUE_ID,
        title = "议题",
        initialStageId = SOURCE_STAGE_ID,
        initialStageTitle = "初始阶段",
        initialObjective = "理解问题",
        createdAt = 10L,
    )

    private fun runCommand(
        runId: String = SOURCE_RUN_ID,
        stageId: String = SOURCE_STAGE_ID,
        idempotencyKey: String = "source-run-key",
    ): CreateExecutionRunCommand = CreateExecutionRunCommand(
        run = ExecutionRunEntity(
            id = runId,
            issueId = ISSUE_ID,
            stageId = stageId,
            idempotencyKey = idempotencyKey,
            createdAt = 15L,
            updatedAt = 15L,
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
        ),
        participants = listOf(
            participant("$runId-participant-a", runId, "skill-a", 0, "形成执行步骤"),
            participant("$runId-participant-b", runId, "skill-b", 1, "检查关键假设"),
        ),
    )

    private fun participant(
        id: String,
        runId: String,
        skillId: String,
        position: Int,
        responsibility: String,
    ) = ExecutionParticipantSnapshotEntity(
        id = id,
        runId = runId,
        sourceType = "official_skill",
        sourceId = skillId,
        displayName = skillId,
        avatar = "A",
        skillAssetPath = "skills/$skillId/SKILL.md",
        systemPrompt = "system-$skillId",
        configurationJson = "{}",
        defaultResponsibility = responsibility,
        position = position,
        createdAt = 15L,
    )

    private fun advanceCommand(
        operationId: String = OPERATION_ID,
        sourceStageId: String = SOURCE_STAGE_ID,
        newStageId: String = NEW_STAGE_ID,
        objective: String = "形成下一阶段计划",
        inheritedMaterialIds: List<String> = emptyList(),
        confirmedAt: Long = 40L,
    ) = AdvanceIssueCommand(
        operationId = operationId,
        issueId = ISSUE_ID,
        sourceStageId = sourceStageId,
        newStageId = newStageId,
        newStageTitle = "阶段推进",
        objective = objective,
        realitySupport = true,
        thinkingExpansion = true,
        measures = listOf(
            StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS,
            StageAdvancementMeasure.CLARIFY_NEXT_STEP,
        ),
        expectedOutput = "行动计划",
        roster = listOf(
            StageAdvancementSkillPlan(
                officialSkillId = "skill-a",
                position = 0,
                responsibility = "形成执行步骤",
                sourceRunId = SOURCE_RUN_ID,
                sourceParticipantSnapshotId = "$SOURCE_RUN_ID-participant-a",
            ),
            StageAdvancementSkillPlan(
                officialSkillId = "skill-b",
                position = 1,
                responsibility = "检查关键假设",
                sourceRunId = SOURCE_RUN_ID,
                sourceParticipantSnapshotId = "$SOURCE_RUN_ID-participant-b",
            ),
        ),
        inheritedMaterialIds = inheritedMaterialIds,
        inheritedArtifactIds = emptyList(),
        confirmedAt = confirmedAt,
    )

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private fun RepositoryResult<*>.failureError(): RepositoryError =
        (this as RepositoryResult.Failure).error

    private fun RepositoryResult<*>.isIdempotentSuccess(): Boolean =
        (this as? RepositoryResult.Success<*>)?.idempotent == true

    companion object {
        private const val ISSUE_ID = "issue-advance"
        private const val SOURCE_STAGE_ID = "stage-source"
        private const val NEW_STAGE_ID = "stage-new"
        private const val SECOND_STAGE_ID = "stage-second"
        private const val SOURCE_RUN_ID = "run-source"
        private const val NEW_STAGE_RUN_ID = "run-new-stage"
        private const val OPERATION_ID = "advance-operation"
        private const val SECOND_OPERATION_ID = "advance-operation-second"
    }
}
