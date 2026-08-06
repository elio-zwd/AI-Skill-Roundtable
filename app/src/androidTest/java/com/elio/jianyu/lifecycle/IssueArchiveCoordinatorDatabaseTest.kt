package com.elio.jianyu.lifecycle

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RoomIssueLifecycleV12Repository
import com.elio.jianyu.data.RoomJianyuRepository
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.data.SaveIssueCommand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssueArchiveCoordinatorDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: RoomJianyuRepository
    private lateinit var lifecycleRepository: RoomIssueLifecycleV12Repository
    private lateinit var tasks: MutableTaskController
    private lateinit var coordinator: IssueArchiveCoordinator

    @Before
    fun setUp() {
        runBlocking {
            database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repository = RoomJianyuRepository(database)
            lifecycleRepository = RoomIssueLifecycleV12Repository(database)
            tasks = MutableTaskController(emptyTasks())
            coordinator = IssueArchiveCoordinator(
                repository = repository,
                lifecycleRepository = lifecycleRepository,
                taskController = tasks,
            )
            repository.saveIssue(
                SaveIssueCommand(
                    issueId = ISSUE_ID,
                    title = "归档测试议题",
                    initialStageId = STAGE_ID,
                    initialStageTitle = "初始阶段",
                    initialObjective = "验证用户主动归档",
                    createdAt = 100L,
                ),
            ).successValue()
        }
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun openingCancelingAndWaitingArchiveWriteNothing() = runBlocking {
        tasks.inspection = activeTasks()

        val opened = coordinator.prepare(ISSUE_ID).successValue()
        val waited = coordinator.waitUntilReady(ISSUE_ID).successValue()

        assertTrue(opened.activeTasks.hasActiveWork)
        assertTrue(waited.activeTasks.hasActiveWork)
        assertEquals(0, tasks.stopCalls)
        assertEquals(IssueLifecycleState.ACTIVE, lifecycleState())
        assertTrue(database.issueLifecycleV12Dao().listArchiveEvents(ISSUE_ID).isEmpty())
    }

    @Test
    fun stopFailureDoesNotArchiveOrCreateEvent() = runBlocking {
        tasks.inspection = activeTasks()
        tasks.stopResult = IssueLifecycleTaskStopResult.Failure("archive_stop_failed")
        val preparation = coordinator.prepare(ISSUE_ID).successValue()

        val result = coordinator.stopActiveWork(preparation)

        assertEquals(IssueArchiveStopResult.Failure("archive_stop_failed"), result)
        assertEquals(1, tasks.stopCalls)
        assertEquals(IssueLifecycleState.ACTIVE, lifecycleState())
        assertTrue(database.issueLifecycleV12Dao().listArchiveEvents(ISSUE_ID).isEmpty())
    }

    @Test
    fun stopMakesOldConfirmationInvalidAndFreshConfirmationArchivesAtomically() = runBlocking {
        tasks.inspection = activeTasks()
        val oldPreparation = coordinator.prepare(ISSUE_ID).successValue()
        tasks.stopResult = IssueLifecycleTaskStopResult.Stopped(emptyTasks())
        tasks.afterStopInspection = emptyTasks()

        val stopResult = coordinator.stopActiveWork(oldPreparation)
        assertTrue(stopResult is IssueArchiveStopResult.Ready)
        val freshPreparation = (stopResult as IssueArchiveStopResult.Ready).preparation

        val staleConfirm = coordinator.confirmArchive(
            preparation = oldPreparation,
            eventId = "archive-event-stale",
            operationId = "archive-operation-stale",
            editedSummaryMarkdown = "旧确认不应生效",
            archivedAt = 200L,
        )
        assertEquals(
            "archive_state_changed",
            (staleConfirm.failureError() as RepositoryError.InvalidState).stateCode,
        )
        assertEquals(IssueLifecycleState.ACTIVE, lifecycleState())
        assertTrue(database.issueLifecycleV12Dao().listArchiveEvents(ISSUE_ID).isEmpty())

        val confirmed = coordinator.confirmArchive(
            preparation = freshPreparation,
            eventId = "archive-event-1",
            operationId = "archive-operation-1",
            editedSummaryMarkdown = "用户确认后的归档简报",
            archivedAt = 300L,
        ).successValue()

        assertEquals(IssueLifecycleState.ARCHIVED, confirmed.lifecycle.state)
        assertEquals("用户确认后的归档简报", confirmed.archiveEvent.summaryMarkdown)
        assertEquals(1, database.issueLifecycleV12Dao().listArchiveEvents(ISSUE_ID).size)
    }

    private suspend fun lifecycleState(): IssueLifecycleState =
        database.jianyuRepositoryDao().getIssueLifecycle(ISSUE_ID)!!.state

    private fun emptyTasks() = IssueLifecycleActiveTasks(
        issueId = ISSUE_ID,
        activeStandardRunIds = emptyList(),
        activeCollaborationRunIds = emptyList(),
        activeDiscussionIds = emptyList(),
        pendingMessageIds = emptyList(),
        pendingAudioAssetIds = emptyList(),
        revision = "empty",
    )

    private fun activeTasks() = emptyTasks().copy(
        activeStandardRunIds = listOf("run-1"),
        pendingMessageIds = listOf(1L),
        pendingAudioAssetIds = listOf("audio-1"),
        revision = "active",
    )

    private class MutableTaskController(
        var inspection: IssueLifecycleActiveTasks,
    ) : IssueLifecycleTaskController {
        var stopCalls: Int = 0
        var stopResult: IssueLifecycleTaskStopResult =
            IssueLifecycleTaskStopResult.Stopped(inspection)
        var afterStopInspection: IssueLifecycleActiveTasks? = null

        override suspend fun inspect(issueId: String): IssueLifecycleActiveTasks = inspection

        override suspend fun stopAll(
            snapshot: IssueLifecycleActiveTasks,
        ): IssueLifecycleTaskStopResult {
            stopCalls += 1
            val result = stopResult
            if (result is IssueLifecycleTaskStopResult.Stopped) {
                inspection = afterStopInspection ?: result.latest
            }
            return result
        }
    }

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private fun RepositoryResult<*>.failureError(): RepositoryError =
        (this as RepositoryResult.Failure).error

    private companion object {
        const val ISSUE_ID = "archive-issue"
        const val STAGE_ID = "archive-stage"
    }
}
