package com.elio.jianyu.ui.screens.execution

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.data.CreateExecutionRunCommand
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.OfficialSkillIdValidator
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RoomJianyuRepository
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.data.SaveIssueCommand
import com.elio.jianyu.data.TransitionRunCommand
import com.elio.jianyu.data.listStageAdvancements
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdvanceIssueViewModelDatabaseTest {
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
    fun openingAndCancellingEveryStepNeverCreatesStage() = runBlocking {
        prepareCompletedStandardRun()
        val viewModel = viewModel(SavedStateHandle())

        viewModel.load(ISSUE_ID, SOURCE_STAGE_ID)
        awaitWorkspace(viewModel)
        viewModel.open()
        awaitState<AdvanceIssueUiState.DirectionStep>(viewModel)
        viewModel.close()

        assertTrue(repository.listStageAdvancements(ISSUE_ID).successValue().isEmpty())
        assertEquals(
            listOf(SOURCE_STAGE_ID),
            repository.recoverIssue(ISSUE_ID).successValue().core.stages.map { it.id },
        )
    }

    @Test
    fun savedStateRestoresUnconfirmedChoicesAndRosterWithoutCreatingStage() = runBlocking {
        prepareCompletedStandardRun()
        val savedState = SavedStateHandle()
        val first = viewModel(savedState)

        first.load(ISSUE_ID, SOURCE_STAGE_ID)
        awaitWorkspace(first)
        first.open()
        awaitState<AdvanceIssueUiState.DirectionStep>(first)
        first.toggleDirection(AdvanceIssueDirection.REALITY_SUPPORT)
        first.continueFromDirection()
        awaitState<AdvanceIssueUiState.MeasureStep>(first)
        first.updateObjective("恢复后仍需再次确认")
        first.toggleRosterMember("skill-b")
        first.close()

        val restored = viewModel(savedState)
        restored.load(ISSUE_ID, SOURCE_STAGE_ID)
        awaitWorkspace(restored)
        restored.open()
        val state = awaitState<AdvanceIssueUiState.DirectionStep>(restored)

        assertTrue(state.restored)
        assertEquals("恢复后仍需再次确认", state.draft.objective)
        assertEquals(listOf("skill-a"), state.draft.roster.map { it.officialSkillId })
        assertFalse(state.draft.summaryIsCurrent)
        assertTrue(repository.listStageAdvancements(ISSUE_ID).successValue().isEmpty())
    }

    @Test
    fun doubleConfirmCreatesExactlyOneStage() = runBlocking {
        prepareCompletedStandardRun()
        val viewModel = viewModel(SavedStateHandle())

        viewModel.load(ISSUE_ID, SOURCE_STAGE_ID)
        awaitWorkspace(viewModel)
        viewModel.open()
        awaitState<AdvanceIssueUiState.DirectionStep>(viewModel)
        viewModel.toggleDirection(AdvanceIssueDirection.REALITY_SUPPORT)
        viewModel.continueFromDirection()
        awaitState<AdvanceIssueUiState.MeasureStep>(viewModel)
        viewModel.updateObjective("只创建一个新阶段")
        viewModel.continueToSummary()
        awaitState<AdvanceIssueUiState.SummaryStep>(viewModel)

        viewModel.confirm()
        viewModel.confirm()
        awaitState<AdvanceIssueUiState.Created>(viewModel)

        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        assertEquals(listOf(SOURCE_STAGE_ID, NEW_STAGE_ID), recovery.core.stages.map { it.id })
        assertEquals(1, repository.listStageAdvancements(ISSUE_ID).successValue().size)
    }

    @Test
    fun activeRunRequiresExplicitStopEventAndNeverCreatesStageByItself() = runBlocking {
        prepareActiveStandardRun()
        val viewModel = viewModel(SavedStateHandle())

        viewModel.load(ISSUE_ID, SOURCE_STAGE_ID)
        awaitWorkspace(viewModel)
        viewModel.open()
        awaitState<AdvanceIssueUiState.DirectionStep>(viewModel)
        viewModel.toggleDirection(AdvanceIssueDirection.REALITY_SUPPORT)
        viewModel.continueFromDirection()
        awaitState<AdvanceIssueUiState.MeasureStep>(viewModel)
        viewModel.updateObjective("运行结束后再推进")
        viewModel.continueToSummary()
        awaitState<AdvanceIssueUiState.SummaryStep>(viewModel)
        viewModel.confirm()
        awaitState<AdvanceIssueUiState.WaitingForRun>(viewModel)

        val event = async { viewModel.events.first() }
        viewModel.requestStopCurrentRun()

        assertEquals(AdvanceIssueEvent.RequestStopCurrentRun, event.await())
        assertTrue(viewModel.state.value is AdvanceIssueUiState.StoppingCurrentRun)
        assertTrue(repository.listStageAdvancements(ISSUE_ID).successValue().isEmpty())
        assertEquals(
            ExecutionRunStatus.NOT_STARTED,
            repository.recoverIssue(ISSUE_ID).successValue().core.runs.single().status,
        )
    }

    private fun viewModel(savedStateHandle: SavedStateHandle): AdvanceIssueViewModel {
        val ids = ArrayDeque(listOf(OPERATION_ID, NEW_STAGE_ID))
        return AdvanceIssueViewModel(
            savedStateHandle = savedStateHandle,
            repository = repository,
            idProvider = { ids.removeFirst() },
            clock = { 40L },
        )
    }

    private suspend fun prepareCompletedStandardRun() {
        prepareActiveStandardRun()
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

    private suspend fun prepareActiveStandardRun() {
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "议题",
                initialStageId = SOURCE_STAGE_ID,
                initialStageTitle = "初始阶段",
                initialObjective = "理解问题",
                createdAt = 10L,
            ),
        ).successValue()
        repository.createExecutionRun(
            CreateExecutionRunCommand(
                run = ExecutionRunEntity(
                    id = SOURCE_RUN_ID,
                    issueId = ISSUE_ID,
                    stageId = SOURCE_STAGE_ID,
                    idempotencyKey = "source-run-key",
                    createdAt = 15L,
                    updatedAt = 15L,
                ),
                participants = listOf(
                    participant("participant-a", "skill-a", 0, "形成执行步骤"),
                    participant("participant-b", "skill-b", 1, "检查关键假设"),
                ),
            ),
        ).successValue()
    }

    private fun participant(
        id: String,
        skillId: String,
        position: Int,
        responsibility: String,
    ) = ExecutionParticipantSnapshotEntity(
        id = id,
        runId = SOURCE_RUN_ID,
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

    private suspend inline fun <reified T : AdvanceIssueUiState> awaitState(
        viewModel: AdvanceIssueViewModel,
    ): T {
        repeat(200) {
            val current = viewModel.state.value
            if (current is T) return current
            delay(10)
        }
        error("等待 ${T::class.simpleName} 超时，当前状态=${viewModel.state.value}")
    }

    private suspend fun awaitWorkspace(viewModel: AdvanceIssueViewModel) {
        repeat(200) {
            if (viewModel.workspace.value != null) return
            delay(10)
        }
        error("等待推进候选超时，当前状态=${viewModel.state.value}")
    }

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    companion object {
        private const val ISSUE_ID = "issue-view-model"
        private const val SOURCE_STAGE_ID = "stage-source"
        private const val NEW_STAGE_ID = "stage-new"
        private const val SOURCE_RUN_ID = "run-source"
        private const val OPERATION_ID = "advance-operation"
    }
}
