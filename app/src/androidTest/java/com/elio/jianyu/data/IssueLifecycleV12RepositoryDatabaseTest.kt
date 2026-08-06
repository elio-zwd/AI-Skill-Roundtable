package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
class IssueLifecycleV12RepositoryDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: JianyuRepository
    private lateinit var lifecycleRepository: RoomIssueLifecycleV12Repository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(database)
        lifecycleRepository = RoomIssueLifecycleV12Repository(database)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun archiveEventAndLifecycleAreAtomicAndDoubleSubmitIsIdempotent() = runBlocking {
        saveIssue()
        val command = archiveCommand()

        val first = lifecycleRepository.archiveIssueWithEvent(command)
        val second = lifecycleRepository.archiveIssueWithEvent(command)

        assertTrue(first is RepositoryResult.Success)
        assertTrue((second as RepositoryResult.Success).idempotent)
        assertEquals(
            IssueLifecycleState.ARCHIVED,
            database.jianyuRepositoryDao().getIssueLifecycle(ISSUE_ID)?.state,
        )
        val events = database.issueLifecycleV12Dao().listArchiveEvents(ISSUE_ID)
        assertEquals(1, events.size)
        assertEquals("归档简报", events.single().summaryMarkdown)
        assertEquals(1, database.jianyuRepositoryDao().getStagesForIssue(ISSUE_ID).size)
    }

    @Test
    fun archivePayloadConflictDoesNotOverwriteImmutableEvent() = runBlocking {
        saveIssue()
        lifecycleRepository.archiveIssueWithEvent(archiveCommand()).successValue()

        val conflict = lifecycleRepository.archiveIssueWithEvent(
            archiveCommand(summary = "不同简报"),
        )

        assertTrue(conflict.failureError() is RepositoryError.IdempotencyConflict)
        assertEquals(
            "归档简报",
            database.issueLifecycleV12Dao().listArchiveEvents(ISSUE_ID).single().summaryMarkdown,
        )
    }

    @Test
    fun resumeCreatesChangeEventWithoutCreatingStageOrRun() = runBlocking {
        saveIssue()
        val archived = lifecycleRepository.archiveIssueWithEvent(archiveCommand()).successValue()
        val beforeStages = database.jianyuRepositoryDao().getStagesForIssue(ISSUE_ID).map { it.id }

        val result = lifecycleRepository.resumeArchivedIssue(
            ResumeArchivedIssueCommand(
                eventId = "resume-event-1",
                issueId = ISSUE_ID,
                archiveEventId = archived.archiveEvent.id,
                operationId = "resume-operation-1",
                changeNote = "目标范围已缩小",
                noChangeConfirmed = false,
                resumedAt = 300L,
            ),
        ).successValue()

        assertEquals(IssueLifecycleState.ACTIVE, result.lifecycle.state)
        assertEquals("目标范围已缩小", result.resumeEvent.changeNote)
        assertEquals(beforeStages, database.jianyuRepositoryDao().getStagesForIssue(ISSUE_ID).map { it.id })
        assertTrue(database.jianyuRepositoryDao().getExecutionRunsForIssue(ISSUE_ID).isEmpty())
        assertEquals(
            "归档简报",
            database.issueLifecycleV12Dao().getArchiveEvent(archived.archiveEvent.id)?.summaryMarkdown,
        )
    }

    @Test
    fun resumeRequiresNoteOrExplicitNoChangeAndWritesNothingOnFailure() = runBlocking {
        saveIssue()
        val archived = lifecycleRepository.archiveIssueWithEvent(archiveCommand()).successValue()

        val result = lifecycleRepository.resumeArchivedIssue(
            ResumeArchivedIssueCommand(
                eventId = "resume-event-1",
                issueId = ISSUE_ID,
                archiveEventId = archived.archiveEvent.id,
                operationId = "resume-operation-1",
                changeNote = "",
                noChangeConfirmed = false,
                resumedAt = 300L,
            ),
        )

        assertTrue(result.failureError() is RepositoryError.InvalidState)
        assertEquals(
            IssueLifecycleState.ARCHIVED,
            database.jianyuRepositoryDao().getIssueLifecycle(ISSUE_ID)?.state,
        )
        assertTrue(database.issueLifecycleV12Dao().listResumeEvents(ISSUE_ID).isEmpty())
    }

    @Test
    fun relatedIssueIsIndependentAndDoesNotCopyHistory() = runBlocking {
        saveIssue()
        val archived = lifecycleRepository.archiveIssueWithEvent(archiveCommand()).successValue()

        val result = lifecycleRepository.createRelatedIssue(
            CreateRelatedIssueCommand(
                relationId = "relation-1",
                operationId = "related-operation-1",
                sourceIssueId = ISSUE_ID,
                sourceArchiveEventId = archived.archiveEvent.id,
                targetIssueId = RELATED_ISSUE_ID,
                targetIssueTitle = "关联新议题",
                targetStageId = RELATED_STAGE_ID,
                targetStageTitle = "初始阶段",
                targetObjective = "独立验证新目标",
                createdAt = 400L,
            ),
        ).successValue()

        assertEquals(RELATED_ISSUE_ID, result.issue.id)
        assertEquals(0, result.initialStage.sequenceIndex)
        assertEquals(IssueLifecycleState.ACTIVE, result.lifecycle.state)
        assertEquals(ISSUE_ID, result.relation.sourceIssueId)
        assertTrue(database.jianyuRepositoryDao().getMessagesForIssue(RELATED_ISSUE_ID).isEmpty())
        assertTrue(database.jianyuRepositoryDao().getExecutionRunsForIssue(RELATED_ISSUE_ID).isEmpty())
        assertTrue(database.jianyuRepositoryDao().getDraftsForIssue(RELATED_ISSUE_ID).isEmpty())
        assertTrue(database.jianyuRepositoryDao().getArtifactsForIssue(RELATED_ISSUE_ID).isEmpty())
        assertEquals(1, database.jianyuRepositoryDao().getStagesForIssue(RELATED_ISSUE_ID).size)
        assertEquals(1, database.issueLifecycleV12Dao().listRelationsFromIssue(ISSUE_ID).size)
    }

    @Test
    fun oldLifecycleShortcutsCannotBypassV12Facts() = runBlocking {
        saveIssue()

        assertEquals(
            "archive_event_required",
            (repository.archiveIssue(ISSUE_ID, 200L).failureError() as RepositoryError.InvalidState).stateCode,
        )
        assertEquals(
            "resume_event_required",
            (repository.restoreIssue(ISSUE_ID, 200L).failureError() as RepositoryError.InvalidState).stateCode,
        )
        assertEquals(
            "purge_operation_required",
            (repository.requestIssuePurge(ISSUE_ID, 200L).failureError() as RepositoryError.InvalidState).stateCode,
        )
        assertEquals(IssueLifecycleState.ACTIVE, database.jianyuRepositoryDao().getIssueLifecycle(ISSUE_ID)?.state)
        assertTrue(database.issueLifecycleV12Dao().listArchiveEvents(ISSUE_ID).isEmpty())
    }

    @Test
    fun trashRestoresPreviousActiveOrArchivedStateAndHasNoExpiration() = runBlocking {
        saveIssue()
        val activeTrash = repository.moveIssueToTrash(ISSUE_ID, 200L).successValue()
        assertEquals(IssueLifecycleState.ACTIVE, activeTrash.previousState)
        assertEquals(IssueLifecycleState.TRASHED, activeTrash.state)
        assertNull(activeTrash.purgeRequestedAt)
        assertEquals(IssueLifecycleState.ACTIVE, repository.restoreIssueFromTrash(ISSUE_ID, 210L).successValue().state)

        lifecycleRepository.archiveIssueWithEvent(archiveCommand(archivedAt = 300L)).successValue()
        val archivedTrash = repository.moveIssueToTrash(ISSUE_ID, 310L).successValue()
        assertEquals(IssueLifecycleState.ARCHIVED, archivedTrash.previousState)
        assertEquals(IssueLifecycleState.ARCHIVED, repository.restoreIssueFromTrash(ISSUE_ID, 320L).successValue().state)
    }

    @Test
    fun purgeRequestRequiresTrashedAndBothConfirmations() = runBlocking {
        saveIssue()
        val activeRequest = lifecycleRepository.requestIssuePurgeOperation(purgeCommand())
        assertTrue(activeRequest.failureError() is RepositoryError.InvalidState)

        repository.moveIssueToTrash(ISSUE_ID, 200L).successValue()
        val missingConfirmation = lifecycleRepository.requestIssuePurgeOperation(
            purgeCommand(finalConfirmation = false),
        )
        assertTrue(missingConfirmation.failureError() is RepositoryError.InvalidState)

        val requested = lifecycleRepository.requestIssuePurgeOperation(purgeCommand()).successValue()
        assertEquals(IssuePurgeState.REQUESTED, requested.state)
        assertEquals(
            250L,
            database.jianyuRepositoryDao().getIssueLifecycle(ISSUE_ID)?.purgeRequestedAt,
        )
        assertFalse(requested.payloadHash.isBlank())
    }

    private suspend fun saveIssue() {
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "议题",
                initialStageId = STAGE_ID,
                initialStageTitle = "初始阶段",
                initialObjective = "验证目标",
                createdAt = 100L,
            ),
        ).successValue()
    }

    private fun archiveCommand(
        summary: String = "归档简报",
        archivedAt: Long = 200L,
    ): ArchiveIssueWithEventCommand = ArchiveIssueWithEventCommand(
        eventId = "archive-event-$archivedAt",
        issueId = ISSUE_ID,
        operationId = "archive-operation-$archivedAt",
        summaryMarkdown = summary,
        currentStageIdSnapshot = STAGE_ID,
        stageCountSnapshot = 1,
        runCountSnapshot = 0,
        draftCountSnapshot = 0,
        artifactCountSnapshot = 0,
        audioAssetCountSnapshot = 0,
        archivedAt = archivedAt,
    )

    private fun purgeCommand(
        finalConfirmation: Boolean = true,
    ): RequestIssuePurgeOperationCommand = RequestIssuePurgeOperationCommand(
        id = "purge-operation-row-1",
        issueId = ISSUE_ID,
        operationId = "purge-operation-key-1",
        impactHash = "impact-hash-1",
        firstConfirmation = true,
        finalConfirmation = finalConfirmation,
        requestedAt = 250L,
    )

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private fun RepositoryResult<*>.failureError(): RepositoryError =
        (this as RepositoryResult.Failure).error

    private companion object {
        const val ISSUE_ID = "issue-1"
        const val STAGE_ID = "stage-1"
        const val RELATED_ISSUE_ID = "issue-related-1"
        const val RELATED_STAGE_ID = "stage-related-1"
    }
}
