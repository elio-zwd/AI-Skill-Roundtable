package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioGenerationRetryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun retryRequiresExplicitUserConfirmationBeforeAnyReadOrWrite() = runBlocking {
        val fixture = fixture(AudioFileState.FAILED)

        val result = fixture.coordinator.retryGeneration(
            RetryAudioGenerationCommand(
                audioAssetId = fixture.original.id,
                userConfirmed = false,
                estimatedOutputBytes = 64L,
                minimumReservationBytes = 64L,
                safetyMarginBytes = 16L,
            ),
        )

        assertEquals(AudioGenerationRetryResult.ConfirmationRequired, result)
        assertEquals(0, fixture.repository.loadAssetCount)
        assertEquals(0, fixture.repository.loadSourceCount)
        assertEquals(0, fixture.repository.resetCount)
        assertEquals(0, fixture.scheduler.scheduleCount)
    }

    @Test
    fun failedAssetWithSameSourceContentResetsSameRecordAndUsesReplaceWork() = runBlocking {
        val fixture = fixture(AudioFileState.FAILED)

        val result = fixture.coordinator.retryGeneration(fixture.retryCommand())

        assertTrue(result is AudioGenerationRetryResult.Queued)
        val queued = result as AudioGenerationRetryResult.Queued
        assertEquals(fixture.original.id, queued.asset.id)
        assertEquals(AudioFileState.PENDING, queued.asset.fileState)
        assertEquals(fixture.original.generationKey, queued.asset.generationKey)
        assertEquals(AudioExistingWorkPolicy.REPLACE, queued.workPlan.existingWorkPolicy)
        assertEquals(1, fixture.repository.resetCount)
        assertEquals(0, fixture.repository.createPendingCount)
        assertEquals(1, fixture.scheduler.scheduleCount)
    }

    @Test
    fun changedSourceContentCreatesNewAssetAndKeepsTerminalHistory() = runBlocking {
        val fixture = fixture(AudioFileState.MISSING)
        fixture.repository.sourceSnapshot = fixture.repository.sourceSnapshot.copy(
            source = (fixture.repository.sourceSnapshot.source as AudioAssetSource.CompletedMessage).copy(
                contentHash = "content-hash-2",
            ),
            content = "更新后的正式来源正文",
        )

        val result = fixture.coordinator.retryGeneration(fixture.retryCommand())

        assertTrue(result is AudioGenerationRetryResult.Queued)
        val queued = result as AudioGenerationRetryResult.Queued
        assertNotEquals(fixture.original.id, queued.asset.id)
        assertNotEquals(fixture.original.generationKey, queued.asset.generationKey)
        assertEquals(AudioFileState.MISSING, fixture.repository.assetsById[fixture.original.id]?.fileState)
        assertEquals(AudioFileState.PENDING, fixture.repository.assetsById[queued.asset.id]?.fileState)
        assertEquals(0, fixture.repository.resetCount)
        assertEquals(1, fixture.repository.createPendingCount)
        assertEquals(AudioExistingWorkPolicy.REPLACE, queued.workPlan.existingWorkPolicy)
    }

    @Test
    fun retryNeverOverwritesAvailableOrAutomaticallyRevivesPendingWithoutWork() = runBlocking {
        val availableFixture = fixture(AudioFileState.AVAILABLE)
        assertEquals(
            AudioGenerationRetryResult.ReusedAvailable(availableFixture.original),
            availableFixture.coordinator.retryGeneration(availableFixture.retryCommand()),
        )
        assertEquals(0, availableFixture.scheduler.scheduleCount)
        assertEquals(0, availableFixture.repository.resetCount)

        val pendingFixture = fixture(AudioFileState.PENDING)
        assertEquals(
            AudioGenerationRetryResult.Failure(
                AudioGenerationErrorCode.PENDING_WITHOUT_ACTIVE_WORK,
            ),
            pendingFixture.coordinator.retryGeneration(pendingFixture.retryCommand()),
        )
        assertEquals(0, pendingFixture.scheduler.scheduleCount)
        assertEquals(0, pendingFixture.repository.resetCount)
    }

    @Test
    fun unavailableSourceAndInsufficientStorageDoNotResetOrSchedule() = runBlocking {
        val missingSourceFixture = fixture(AudioFileState.CANCELED)
        missingSourceFixture.repository.rejectSource = true

        assertEquals(
            AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.SOURCE_NOT_FOUND),
            missingSourceFixture.coordinator.retryGeneration(missingSourceFixture.retryCommand()),
        )
        assertEquals(0, missingSourceFixture.repository.resetCount)
        assertEquals(0, missingSourceFixture.scheduler.scheduleCount)

        val lowSpaceFixture = fixture(
            state = AudioFileState.FAILED,
            usableBytes = 32L,
        )
        assertEquals(
            AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.INSUFFICIENT_STORAGE),
            lowSpaceFixture.coordinator.retryGeneration(
                lowSpaceFixture.retryCommand(
                    estimatedOutputBytes = 128L,
                    minimumReservationBytes = 64L,
                    safetyMarginBytes = 16L,
                ),
            ),
        )
        assertEquals(0, lowSpaceFixture.repository.resetCount)
        assertEquals(0, lowSpaceFixture.scheduler.scheduleCount)
    }

    private fun fixture(
        state: AudioFileState,
        usableBytes: Long = Long.MAX_VALUE,
    ): Fixture {
        val source = AudioAssetSource.CompletedMessage(
            issueId = "issue-1",
            stageId = "stage-1",
            contentHash = "content-hash-1",
            messageId = 42L,
        )
        val config = AudioGenerationConfig(
            voiceProfileId = AudioGenerationCoordinator.V1_VOICE_PROFILE_ID,
            targetFormat = AudioTargetFormat.WAV,
            parameterVersion = AudioGenerationCoordinator.V1_PARAMETER_VERSION,
        )
        val original = AudioAssetRecord(
            id = "original-${state.storageValue}",
            source = source,
            config = config,
            generationKey = AudioGenerationKeyFactory.create(source, config),
            fileState = state,
            storagePath = if (state == AudioFileState.AVAILABLE) "available.wav" else null,
            mimeType = if (state == AudioFileState.AVAILABLE) "audio/wav" else null,
            sizeBytes = if (state == AudioFileState.AVAILABLE) 48L else 0L,
            deletedAt = null,
            purgeRequestedAt = null,
        )
        val repository = RetryRepository(
            original = original,
            sourceSnapshot = AudioSourceSnapshot(
                source = source,
                content = "正式来源正文",
            ),
        )
        val scheduler = RetryScheduler()
        val root = temporaryFolder.newFolder("retry-${System.nanoTime()}")
        val coordinator = AudioGenerationCoordinator(
            repository = repository,
            scheduler = scheduler,
            gateway = AudioGenerationGateway { _, _ -> AudioGenerationGatewayResult.Success },
            fileStore = AudioFileStore(
                rootDirectory = root,
                usableSpaceProvider = { usableBytes },
            ),
            audioAssetIdFactory = { "retry-new-asset" },
        )
        return Fixture(
            original = original,
            repository = repository,
            scheduler = scheduler,
            coordinator = coordinator,
        )
    }

    private data class Fixture(
        val original: AudioAssetRecord,
        val repository: RetryRepository,
        val scheduler: RetryScheduler,
        val coordinator: AudioGenerationCoordinator,
    ) {
        fun retryCommand(
            estimatedOutputBytes: Long = 64L,
            minimumReservationBytes: Long = 64L,
            safetyMarginBytes: Long = 16L,
        ): RetryAudioGenerationCommand {
            return RetryAudioGenerationCommand(
                audioAssetId = original.id,
                userConfirmed = true,
                estimatedOutputBytes = estimatedOutputBytes,
                minimumReservationBytes = minimumReservationBytes,
                safetyMarginBytes = safetyMarginBytes,
            )
        }
    }

    private class RetryRepository(
        original: AudioAssetRecord,
        var sourceSnapshot: AudioSourceSnapshot,
    ) : AudioAssetRepositoryPort {
        val assetsById = linkedMapOf(original.id to original)
        val assetsByKey = linkedMapOf(original.generationKey to original)
        var loadAssetCount = 0
        var loadSourceCount = 0
        var resetCount = 0
        var createPendingCount = 0
        var rejectSource = false

        override suspend fun loadSource(reference: AudioSourceReference): AudioSourceLoadResult {
            loadSourceCount += 1
            return if (rejectSource) {
                AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.SOURCE_NOT_FOUND)
            } else {
                AudioSourceLoadResult.Ready(sourceSnapshot)
            }
        }

        override suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord? {
            return assetsByKey[generationKey]
        }

        override suspend fun createPending(command: CreatePendingAudioCommand): AudioAssetCreateResult {
            createPendingCount += 1
            assetsByKey[command.generationKey]?.let { return AudioAssetCreateResult.Existing(it) }
            val created = AudioAssetRecord(
                id = command.audioAssetId,
                source = command.source,
                config = command.config,
                generationKey = command.generationKey,
                fileState = AudioFileState.PENDING,
                storagePath = null,
                mimeType = null,
                sizeBytes = 0L,
                deletedAt = null,
                purgeRequestedAt = null,
            )
            assetsById[created.id] = created
            assetsByKey[created.generationKey] = created
            return AudioAssetCreateResult.Created(created)
        }

        override suspend fun loadAsset(audioAssetId: String): AudioAssetRecord? {
            loadAssetCount += 1
            return assetsById[audioAssetId]
        }

        override suspend fun markAvailable(command: MarkAudioAvailableCommand): Boolean = false

        override suspend fun markFailed(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean = false

        override suspend fun markCanceled(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean = false

        override suspend fun resetForRetry(
            command: ResetAudioForRetryCommand,
        ): AudioAssetRetryResetResult {
            resetCount += 1
            val current = assetsById[command.audioAssetId]
                ?: return AudioAssetRetryResetResult.Rejected
            if (current.fileState != command.expectedState) {
                return AudioAssetRetryResetResult.Rejected
            }
            val reset = current.copy(
                source = command.source,
                config = command.config,
                generationKey = command.generationKey,
                fileState = AudioFileState.PENDING,
                storagePath = null,
                mimeType = null,
                sizeBytes = 0L,
            )
            assetsByKey.remove(current.generationKey)
            assetsById[reset.id] = reset
            assetsByKey[reset.generationKey] = reset
            return AudioAssetRetryResetResult.Reset(reset)
        }
    }

    private class RetryScheduler : AudioGenerationSchedulerPort {
        val activeWorkNames = linkedSetOf<String>()
        var scheduleCount = 0

        override suspend fun isActive(uniqueWorkName: String): Boolean {
            return uniqueWorkName in activeWorkNames
        }

        override suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean {
            scheduleCount += 1
            activeWorkNames += plan.uniqueWorkName
            return true
        }

        override suspend fun cancel(uniqueWorkName: String): Boolean = true
    }
}
