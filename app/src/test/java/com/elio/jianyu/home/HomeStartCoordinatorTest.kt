package com.elio.jianyu.home

import com.elio.jianyu.data.ContextPreparationResult
import com.elio.jianyu.data.ContextSelectionDraft
import com.elio.jianyu.data.ContextUsageWriteSet
import com.elio.jianyu.data.PrepareExecutionContextCommand
import com.elio.jianyu.data.PreparedExecutionContext
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SaveIssueCommand
import com.elio.jianyu.execution.ExecutionStartCommand
import com.elio.jianyu.execution.SearchMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartCoordinatorTest {
    private val ids = HomeWorkflowIds(
        workflowId = "workflow-1",
        issueId = "issue-1",
        stageId = "stage-1",
        runId = "run-1",
        saveIssueIdempotencyKey = "save-1",
        executionIdempotencyKey = "execute-1",
    )

    @Test
    fun saveOnly_callsOnlySaveIssueAndNeverPreparesOrStartsExecution() = runBlocking {
        val repository = FakeHomeRepositoryGateway()
        val starter = FakeHomeExecutionStarter()
        val coordinator = HomeStartCoordinator(repository, starter)

        val result = coordinator.saveOnly(
            HomeSaveOnlyCommand(
                ids = ids,
                question = "先保存这个议题",
                createdAt = 100L,
            ),
        )

        assertEquals(HomeStartResult.SavedOnly("issue-1", "stage-1"), result)
        assertEquals(1, repository.saveCommands.size)
        assertEquals(0, repository.prepareCommands.size)
        assertEquals(0, starter.commands.size)
    }

    @Test
    fun repeatedSaveOnly_reusesStableIssueAndStageIds() = runBlocking {
        val repository = FakeHomeRepositoryGateway()
        val coordinator = HomeStartCoordinator(repository, FakeHomeExecutionStarter())
        val command = HomeSaveOnlyCommand(ids, "先保存这个议题", 100L)

        coordinator.saveOnly(command)
        coordinator.saveOnly(command)

        assertEquals(listOf("issue-1", "issue-1"), repository.saveCommands.map { it.issueId })
        assertEquals(listOf("stage-1", "stage-1"), repository.saveCommands.map { it.initialStageId })
    }

    @Test
    fun contextPreparationFailure_keepsSavedIssueAndDoesNotStartExecution() = runBlocking {
        val repository = FakeHomeRepositoryGateway(
            prepareResult = RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(
                    operation = "prepare_execution_context",
                    constraintCode = "source_stale",
                ),
            ),
        )
        val starter = FakeHomeExecutionStarter()
        val coordinator = HomeStartCoordinator(repository, starter)

        val result = coordinator.start(finalConfirmation())

        assertTrue(result is HomeStartResult.SavedNotStarted)
        assertEquals("source_stale", (result as HomeStartResult.SavedNotStarted).errorCode)
        assertEquals(1, repository.saveCommands.size)
        assertEquals(1, repository.prepareCommands.size)
        assertTrue(starter.commands.isEmpty())
    }

    @Test
    fun start_passesPreparedContributionsAndUsageToSingleExecutionStarter() = runBlocking {
        val prepared = PreparedExecutionContext(
            preparation = ContextPreparationResult.Ready(
                items = emptyList(),
                contributions = emptyList(),
                totalCharacters = 120,
                remainingCharacters = 23_880,
            ),
            usage = ContextUsageWriteSet(),
        )
        val repository = FakeHomeRepositoryGateway(
            prepareResult = RepositoryResult.Success(prepared),
        )
        val starter = FakeHomeExecutionStarter()
        val coordinator = HomeStartCoordinator(repository, starter)

        val result = coordinator.start(finalConfirmation())

        assertEquals(HomeStartResult.Started("issue-1", "stage-1", "run-1"), result)
        assertEquals(1, starter.commands.size)
        val command = starter.commands.single()
        assertEquals(prepared.preparation.contributions, command.contributions)
        assertEquals(prepared.usage, command.contextUsage)
        assertEquals("execute-1", command.idempotencyKey)
        assertEquals("issue-1", command.issueId)
        assertEquals("stage-1", command.stageId)
        assertEquals("run-1", command.runId)
        assertEquals(SearchMode.ON, command.searchMode)
    }

    @Test
    fun executionFailure_returnsSavedNotStartedWithoutChangingStableIds() = runBlocking {
        val repository = FakeHomeRepositoryGateway()
        val starter = FakeHomeExecutionStarter(
            result = HomeExecutionStartResult.Failure("no_api_key"),
        )
        val coordinator = HomeStartCoordinator(repository, starter)

        val result = coordinator.start(finalConfirmation())

        assertEquals(
            HomeStartResult.SavedNotStarted("issue-1", "stage-1", "no_api_key"),
            result,
        )
        assertEquals("run-1", starter.commands.single().runId)
    }

    @Test
    fun finalConfirmation_requiresExplicitContextConfirmation() = runBlocking {
        val repository = FakeHomeRepositoryGateway()
        val starter = FakeHomeExecutionStarter()
        val coordinator = HomeStartCoordinator(repository, starter)

        val result = coordinator.start(
            finalConfirmation().copy(
                contextSelection = HomeContextSelectionSnapshot(confirmed = false),
            ),
        )

        assertEquals(HomeStartResult.Failure("context_confirmation_required"), result)
        assertTrue(repository.saveCommands.isEmpty())
        assertTrue(repository.prepareCommands.isEmpty())
        assertTrue(starter.commands.isEmpty())
    }

    private fun finalConfirmation(): HomeFinalConfirmation = HomeFinalConfirmation(
        ids = ids,
        question = "如何规划未来半年的职业转型？",
        directions = setOf(ValueDirection.REALITY_SUPPORT),
        recommendation = HomeRecommendation(
            questionSummary = "规划未来半年的职业转型",
            directions = setOf(ValueDirection.REALITY_SUPPORT),
            mode = RecommendationMode.SINGLE,
            modeReason = "单一专业顾问即可覆盖当前目标。",
            skills = listOf(
                RecommendedSkill(
                    skillId = "career_advisor",
                    displayName = "职业规划顾问",
                    responsibility = "拆解目标并形成阶段计划",
                    reason = "问题聚焦职业路径和行动安排",
                    risk = RecommendationRisk.GENERAL,
                    riskDisclosure = "一般决策风险",
                    freshnessDisclosure = "岗位信息需要按当前地区核验",
                    networkRequirement = "可选",
                    materialRequirement = "可选",
                    expectedOutput = "半年行动计划",
                    executable = true,
                    selected = true,
                    position = 0,
                ),
            ),
            expectedOutput = "半年行动计划",
            source = RecommendationSource.LOCAL_CATALOG,
        ),
        contextSelection = HomeContextSelectionSnapshot(confirmed = true),
        searchMode = SearchMode.ON,
        confirmedAt = 200L,
    )
}

private class FakeHomeRepositoryGateway(
    private val saveResult: RepositoryResult<Unit> = RepositoryResult.Success(Unit),
    private val prepareResult: RepositoryResult<PreparedExecutionContext> = RepositoryResult.Success(
        PreparedExecutionContext(
            preparation = ContextPreparationResult.Ready(
                items = emptyList(),
                contributions = emptyList(),
                totalCharacters = 100,
                remainingCharacters = 23_900,
            ),
            usage = ContextUsageWriteSet(),
        ),
    ),
) : HomeRepositoryGateway {
    val saveCommands = mutableListOf<SaveIssueCommand>()
    val prepareCommands = mutableListOf<PrepareExecutionContextCommand>()

    override suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<Unit> {
        saveCommands += command
        return saveResult
    }

    override suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand,
    ): RepositoryResult<PreparedExecutionContext> {
        prepareCommands += command
        return prepareResult
    }
}

private class FakeHomeExecutionStarter(
    private val result: HomeExecutionStartResult = HomeExecutionStartResult.Started("run-1"),
) : HomeExecutionStarter {
    val commands = mutableListOf<ExecutionStartCommand>()

    override suspend fun start(command: ExecutionStartCommand): HomeExecutionStartResult {
        commands += command
        return result
    }
}
