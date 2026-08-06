package com.elio.jianyu.lifecycle

import com.elio.jianyu.data.ArchiveIssueWithEventCommand
import com.elio.jianyu.data.ArchivedIssueResult
import com.elio.jianyu.data.CreateRelatedIssueCommand
import com.elio.jianyu.data.IssueArchiveEventEntity
import com.elio.jianyu.data.IssueLifecycleV12Repository
import com.elio.jianyu.data.IssuePurgeDatabaseCleanup
import com.elio.jianyu.data.IssuePurgeFailurePhase
import com.elio.jianyu.data.IssuePurgeOperationEntity
import com.elio.jianyu.data.IssuePurgeState
import com.elio.jianyu.data.IssueRelationEntity
import com.elio.jianyu.data.IssueResumeEventEntity
import com.elio.jianyu.data.RelatedIssueResult
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RequestIssuePurgeOperationCommand
import com.elio.jianyu.data.ResumeArchivedIssueCommand
import com.elio.jianyu.data.ResumedIssueResult
import com.elio.jianyu.data.TransitionIssuePurgeOperationCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssuePurgeCoordinatorTest {
    @Test
    fun changedImpactRequiresNewConfirmationAndCreatesNoOperation() = runBlocking {
        val repository = FakeLifecycleRepository()
        val scheduler = FakeScheduler()
        val currentImpact = impact(databaseCount = 2L)
        val coordinator = coordinator(
            repository = repository,
            impactProvider = IssuePurgeImpactProvider {
                RepositoryResult.Success(currentImpact.copy(databaseCounts = mapOf("messages" to 3L)))
            },
            scheduler = scheduler,
        )

        val result = coordinator.request(requestCommand(currentImpact))

        assertEquals(IssuePurgeRequestResult.Failure("purge_impact_changed"), result)
        assertEquals(0, repository.requestCount)
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun fileFailurePersistsRetryablePhaseAndSkipsDatabaseCleanup() = runBlocking {
        val impact = impact()
        val repository = FakeLifecycleRepository(operation(impact))
        val fileCleaner = CountingFileCleaner(
            IssuePurgeFileCleanupResult.Failure(
                IssuePurgeFileFailureCode.FORMAL_FILE_DELETE_FAILED,
                stableAudioAssetId = "audio-1",
            ),
        )
        val databaseCleaner = CountingDatabaseCleaner(RepositoryResult.Success(Unit))
        val coordinator = coordinator(
            repository = repository,
            impactProvider = IssuePurgeImpactProvider { RepositoryResult.Success(impact) },
            fileCleaner = fileCleaner,
            databaseCleaner = databaseCleaner,
        )

        val result = coordinator.execute(OPERATION_ROW_ID)

        assertEquals(
            IssuePurgeExecutionResult.RetryableFailure("purge_file_delete_failed"),
            result,
        )
        assertEquals(1, fileCleaner.calls)
        assertEquals(0, databaseCleaner.calls)
        assertEquals(IssuePurgeState.FAILED_RETRYABLE, repository.current().state)
        assertEquals(IssuePurgeFailurePhase.FILE_DELETE, repository.current().failurePhase)
        assertEquals("purge_file_delete_failed", repository.current().failureCode)
    }

    @Test
    fun databaseRetryDoesNotRepeatFileCleanup() = runBlocking {
        val impact = impact()
        val repository = FakeLifecycleRepository(operation(impact))
        val fileCleaner = CountingFileCleaner(
            IssuePurgeFileCleanupResult.Success(
                assetCount = 1,
                formalFileCount = 1,
                temporaryFileCount = 1,
            ),
        )
        val databaseCleaner = SequencedDatabaseCleaner(
            ArrayDeque(
                listOf(
                    RepositoryResult.Failure(
                        RepositoryError.StorageFailure("purge_issue_database", retryable = true),
                    ),
                    RepositoryResult.Success(Unit),
                ),
            ),
        )
        val coordinator = coordinator(
            repository = repository,
            impactProvider = IssuePurgeImpactProvider { RepositoryResult.Success(impact) },
            fileCleaner = fileCleaner,
            databaseCleaner = databaseCleaner,
        )

        val first = coordinator.execute(OPERATION_ROW_ID)
        val second = coordinator.execute(OPERATION_ROW_ID)

        assertEquals(IssuePurgeExecutionResult.RetryableFailure("purge_database_failed"), first)
        assertEquals(IssuePurgeExecutionResult.Completed, second)
        assertEquals(1, fileCleaner.calls)
        assertEquals(2, databaseCleaner.calls)
        assertEquals(IssuePurgeState.DATABASE_PURGING, repository.current().state)
    }

    @Test
    fun taskStopFailurePreventsFileAndDatabasePhases() = runBlocking {
        val impact = impact()
        val repository = FakeLifecycleRepository(operation(impact))
        val tasks = FakeTaskController(
            inspection = activeTasks(),
            stopResult = IssueLifecycleTaskStopResult.Failure("purge_audio_cancel_failed"),
        )
        val fileCleaner = CountingFileCleaner(successfulFileCleanup())
        val databaseCleaner = CountingDatabaseCleaner(RepositoryResult.Success(Unit))
        val coordinator = coordinator(
            repository = repository,
            impactProvider = IssuePurgeImpactProvider { RepositoryResult.Success(impact) },
            taskController = tasks,
            fileCleaner = fileCleaner,
            databaseCleaner = databaseCleaner,
        )

        val result = coordinator.execute(OPERATION_ROW_ID)

        assertEquals(
            IssuePurgeExecutionResult.RetryableFailure("purge_audio_cancel_failed"),
            result,
        )
        assertEquals(1, tasks.stopCalls)
        assertEquals(0, fileCleaner.calls)
        assertEquals(0, databaseCleaner.calls)
        assertEquals(IssuePurgeFailurePhase.TASK_CANCEL, repository.current().failurePhase)
    }

    @Test
    fun processRecoveryReusesExistingWorkAndNeverReschedulesFailedOperation() = runBlocking {
        val impact = impact()
        val requested = operation(impact)
        val active = operation(impact).copy(
            id = "operation-row-active",
            operationId = "operation-key-active",
            state = IssuePurgeState.DELETING_FILES,
        )
        val failed = operation(impact).copy(
            id = "operation-row-failed",
            operationId = "operation-key-failed",
            state = IssuePurgeState.FAILED_RETRYABLE,
            failureCode = "purge_file_delete_failed",
            failurePhase = IssuePurgeFailurePhase.FILE_DELETE,
        )
        val repository = FakeLifecycleRepository(requested, active, failed)
        val scheduler = FakeScheduler(active = mutableSetOf(active.id))
        val coordinator = coordinator(repository = repository, scheduler = scheduler)

        val scheduledCount = coordinator.recoverPendingOperations()

        assertEquals(1, scheduledCount)
        assertEquals(listOf(requested.id), scheduler.scheduled)
        assertFalse(failed.id in scheduler.scheduled)
        assertFalse(active.id in scheduler.scheduled)
    }

    @Test
    fun purgeRequestSchedulesExactlyOnePersistedOperation() = runBlocking {
        val impact = impact()
        val repository = FakeLifecycleRepository()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(
            repository = repository,
            impactProvider = IssuePurgeImpactProvider { RepositoryResult.Success(impact) },
            scheduler = scheduler,
        )

        val result = coordinator.request(requestCommand(impact))

        assertTrue(result is IssuePurgeRequestResult.Scheduled)
        assertEquals(1, repository.requestCount)
        assertEquals(listOf(OPERATION_ROW_ID), scheduler.scheduled)
        assertEquals(IssuePurgeState.REQUESTED, repository.current().state)
    }

    private fun coordinator(
        repository: FakeLifecycleRepository,
        impactProvider: IssuePurgeImpactProvider = IssuePurgeImpactProvider {
            RepositoryResult.Success(impact())
        },
        taskController: IssueLifecycleTaskController = FakeTaskController(emptyTasks()),
        fileCleaner: IssuePurgeFileCleanup = CountingFileCleaner(successfulFileCleanup()),
        databaseCleaner: IssuePurgeDatabaseCleanup = CountingDatabaseCleaner(
            RepositoryResult.Success(Unit),
        ),
        scheduler: FakeScheduler = FakeScheduler(),
    ): IssuePurgeCoordinator {
        var now = 1_000L
        return IssuePurgeCoordinator(
            repository = repository,
            impactCalculator = impactProvider,
            taskController = taskController,
            fileCleaner = fileCleaner,
            databaseCleaner = databaseCleaner,
            scheduler = scheduler,
            clock = { ++now },
        )
    }

    private fun impact(databaseCount: Long = 2L): IssuePurgeImpactSnapshot =
        IssuePurgeImpactSnapshot(
            issueId = ISSUE_ID,
            databaseCounts = mapOf("messages" to databaseCount),
            formalFiles = listOf(PurgeFileImpact("committed/audio-1.mp3", 8L)),
            pendingWorkNames = listOf("audio-work-1"),
            missingAssetIds = emptyList(),
            orphanRelativePaths = listOf("temporary/orphan.part"),
            relatedIssueCount = 1,
            externalObjectCount = 1,
        )

    private fun operation(impact: IssuePurgeImpactSnapshot): IssuePurgeOperationEntity =
        IssuePurgeOperationEntity(
            id = OPERATION_ROW_ID,
            issueId = ISSUE_ID,
            operationId = OPERATION_KEY,
            payloadHash = "payload-hash",
            impactHash = IssuePurgeImpactHasher.hash(impact),
            state = IssuePurgeState.REQUESTED,
            requestedAt = 500L,
            updatedAt = 500L,
        )

    private fun requestCommand(impact: IssuePurgeImpactSnapshot) =
        RequestIssuePurgeOperationCommand(
            id = OPERATION_ROW_ID,
            issueId = ISSUE_ID,
            operationId = OPERATION_KEY,
            impactHash = IssuePurgeImpactHasher.hash(impact),
            firstConfirmation = true,
            finalConfirmation = true,
            requestedAt = 500L,
        )

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
        pendingAudioAssetIds = listOf("audio-1"),
        revision = "active",
    )

    private fun successfulFileCleanup() = IssuePurgeFileCleanupResult.Success(
        assetCount = 0,
        formalFileCount = 0,
        temporaryFileCount = 0,
    )

    private class FakeLifecycleRepository(
        vararg initial: IssuePurgeOperationEntity,
    ) : IssueLifecycleV12Repository {
        private val operations = initial.associateByTo(linkedMapOf(), IssuePurgeOperationEntity::id)
        var requestCount: Int = 0

        fun current(): IssuePurgeOperationEntity = operations.values.first()

        override suspend fun requestIssuePurgeOperation(
            command: RequestIssuePurgeOperationCommand,
        ): RepositoryResult<IssuePurgeOperationEntity> {
            requestCount += 1
            val entity = IssuePurgeOperationEntity(
                id = command.id,
                issueId = command.issueId,
                operationId = command.operationId,
                payloadHash = "payload-hash",
                impactHash = command.impactHash,
                state = IssuePurgeState.REQUESTED,
                requestedAt = command.requestedAt,
                updatedAt = command.requestedAt,
            )
            operations[entity.id] = entity
            return RepositoryResult.Success(entity)
        }

        override suspend fun transitionIssuePurgeOperation(
            command: TransitionIssuePurgeOperationCommand,
        ): RepositoryResult<IssuePurgeOperationEntity> {
            val current = operations[command.operationId]
                ?: return RepositoryResult.Failure(
                    RepositoryError.NotFound("purge_operation", command.operationId),
                )
            if (current.state !in command.expectedStates) {
                return RepositoryResult.Failure(
                    RepositoryError.InvalidState("transition_issue_purge", "purge_state_changed"),
                )
            }
            val updated = current.copy(
                state = command.targetState,
                startedAt = current.startedAt ?: command.updatedAt,
                updatedAt = command.updatedAt,
                failedAt = command.updatedAt.takeIf {
                    command.targetState == IssuePurgeState.FAILED_RETRYABLE
                },
                failureCode = command.failureCode,
                failurePhase = command.failurePhase,
                retryCount = current.retryCount + if (
                    command.targetState == IssuePurgeState.FAILED_RETRYABLE
                ) 1 else 0,
            )
            operations[updated.id] = updated
            return RepositoryResult.Success(updated)
        }

        override suspend fun getIssuePurgeOperation(
            operationId: String,
        ): RepositoryResult<IssuePurgeOperationEntity> = operations[operationId]
            ?.let { RepositoryResult.Success(it) }
            ?: RepositoryResult.Failure(RepositoryError.NotFound("purge_operation", operationId))

        override suspend fun listRecoverableIssuePurgeOperations(): RepositoryResult<List<IssuePurgeOperationEntity>> =
            RepositoryResult.Success(operations.values.toList())

        override suspend fun archiveIssueWithEvent(
            command: ArchiveIssueWithEventCommand,
        ): RepositoryResult<ArchivedIssueResult> = unsupported()

        override suspend fun resumeArchivedIssue(
            command: ResumeArchivedIssueCommand,
        ): RepositoryResult<ResumedIssueResult> = unsupported()

        override suspend fun createRelatedIssue(
            command: CreateRelatedIssueCommand,
        ): RepositoryResult<RelatedIssueResult> = unsupported()

        override suspend fun listArchiveEvents(
            issueId: String,
        ): RepositoryResult<List<IssueArchiveEventEntity>> = RepositoryResult.Success(emptyList())

        override suspend fun listResumeEvents(
            issueId: String,
        ): RepositoryResult<List<IssueResumeEventEntity>> = RepositoryResult.Success(emptyList())

        override suspend fun listIssueRelations(
            issueId: String,
        ): RepositoryResult<List<IssueRelationEntity>> = RepositoryResult.Success(emptyList())

        private fun <T> unsupported(): RepositoryResult<T> = RepositoryResult.Failure(
            RepositoryError.InvalidState("test", "unsupported"),
        )
    }

    private class FakeTaskController(
        private val inspection: IssueLifecycleActiveTasks,
        private val stopResult: IssueLifecycleTaskStopResult =
            IssueLifecycleTaskStopResult.Stopped(inspection),
    ) : IssueLifecycleTaskController {
        var stopCalls: Int = 0

        override suspend fun inspect(issueId: String): IssueLifecycleActiveTasks = inspection

        override suspend fun stopAll(
            snapshot: IssueLifecycleActiveTasks,
        ): IssueLifecycleTaskStopResult {
            stopCalls += 1
            return stopResult
        }
    }

    private class CountingFileCleaner(
        private val result: IssuePurgeFileCleanupResult,
    ) : IssuePurgeFileCleanup {
        var calls: Int = 0

        override suspend fun clean(
            issueId: String,
            requestedAt: Long,
        ): IssuePurgeFileCleanupResult {
            calls += 1
            return result
        }
    }

    private class CountingDatabaseCleaner(
        private val result: RepositoryResult<Unit>,
    ) : IssuePurgeDatabaseCleanup {
        var calls: Int = 0

        override suspend fun purge(operationId: String, purgedAt: Long): RepositoryResult<Unit> {
            calls += 1
            return result
        }
    }

    private class SequencedDatabaseCleaner(
        private val results: ArrayDeque<RepositoryResult<Unit>>,
    ) : IssuePurgeDatabaseCleanup {
        var calls: Int = 0

        override suspend fun purge(operationId: String, purgedAt: Long): RepositoryResult<Unit> {
            calls += 1
            return results.removeFirst()
        }
    }

    private class FakeScheduler(
        private val active: MutableSet<String> = mutableSetOf(),
    ) : IssuePurgeScheduler {
        val scheduled = mutableListOf<String>()

        override suspend fun isActive(operationId: String): Boolean = operationId in active

        override suspend fun schedule(operationId: String): Boolean {
            scheduled += operationId
            active += operationId
            return true
        }

        override suspend fun cancel(operationId: String): Boolean {
            active -= operationId
            return true
        }
    }

    private companion object {
        const val ISSUE_ID = "issue-1"
        const val OPERATION_ROW_ID = "operation-row-1"
        const val OPERATION_KEY = "operation-key-1"
    }
}
