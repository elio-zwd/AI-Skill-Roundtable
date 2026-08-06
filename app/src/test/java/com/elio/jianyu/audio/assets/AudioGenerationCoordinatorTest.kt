package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioGenerationCoordinatorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun confirmationIsRequiredBeforeRepositoryFileWorkOrGatewaySideEffects() = runBlocking {
        val fixture = fixture()

        val result = fixture.coordinator.createGenerationRequest(
            command = fixture.createCommand(userConfirmed = false),
        )

        assertEquals(AudioGenerationRequestResult.ConfirmationRequired, result)
        assertEquals(0, fixture.repository.loadSourceCount)
        assertEquals(0, fixture.repository.createPendingCount)
        assertEquals(0, fixture.scheduler.scheduleCount)
        assertEquals(0, fixture.gateway.callCount)
        assertTrue(fixture.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun availableAndActivePendingAreReusedWhileTerminalStateRequiresExplicitRetry() = runBlocking {
        val availableFixture = fixture()
        val available = availableFixture.record(state = AudioFileState.AVAILABLE)
        availableFixture.repository.assetsByKey[available.generationKey] = available

        assertEquals(
            AudioGenerationRequestResult.ReusedAvailable(available),
            availableFixture.coordinator.createGenerationRequest(
                availableFixture.createCommand(),
            ),
        )
        assertEquals(0, availableFixture.scheduler.scheduleCount)

        val pendingFixture = fixture()
        val pending = pendingFixture.record(state = AudioFileState.PENDING)
        pendingFixture.repository.assetsByKey[pending.generationKey] = pending
        pendingFixture.repository.assetsById[pending.id] = pending
        pendingFixture.scheduler.activeWorkNames += AudioGenerationWorkPolicy.plan(
            audioAssetId = pending.id,
            generationKey = pending.generationKey,
            requestKind = AudioWorkRequestKind.INITIAL,
        ).uniqueWorkName

        assertEquals(
            AudioGenerationRequestResult.ReusedPending(pending),
            pendingFixture.coordinator.createGenerationRequest(
                pendingFixture.createCommand(),
            ),
        )
        assertEquals(0, pendingFixture.scheduler.scheduleCount)

        AudioFileState.entries
            .filter { it == AudioFileState.FAILED || it == AudioFileState.MISSING || it == AudioFileState.CANCELED }
            .forEach { terminalState ->
                val terminalFixture = fixture()
                val terminal = terminalFixture.record(state = terminalState)
                terminalFixture.repository.assetsByKey[terminal.generationKey] = terminal

                assertEquals(
                    AudioGenerationRequestResult.ExplicitRetryRequired(terminal),
                    terminalFixture.coordinator.createGenerationRequest(
                        terminalFixture.createCommand(),
                    ),
                )
                assertEquals(0, terminalFixture.scheduler.scheduleCount)
            }
    }

    @Test
    fun insufficientStorageDoesNotCreateAssetScheduleWorkOrCallGateway() = runBlocking {
        val fixture = fixture(usableBytes = 256L)

        val result = fixture.coordinator.createGenerationRequest(
            fixture.createCommand(
                estimatedOutputBytes = 1_024L,
                minimumReservationBytes = 512L,
                safetyMarginBytes = 128L,
            ),
        )

        assertEquals(
            AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.INSUFFICIENT_STORAGE),
            result,
        )
        assertEquals(1, fixture.repository.loadSourceCount)
        assertEquals(0, fixture.repository.createPendingCount)
        assertEquals(0, fixture.scheduler.scheduleCount)
        assertEquals(0, fixture.gateway.callCount)
        assertTrue(fixture.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun successfulExecutionWritesValidPartCommitsRelativeFileAndMarksAvailable() = runBlocking {
        val fixture = fixture()
        val queued = fixture.coordinator.createGenerationRequest(fixture.createCommand())
            as AudioGenerationRequestResult.Queued
        fixture.gateway.bytes = validWavBytes()

        val result = fixture.coordinator.execute(queued.asset.id)

        assertTrue(result is AudioGenerationExecutionResult.Available)
        val available = result as AudioGenerationExecutionResult.Available
        assertEquals(queued.asset.id, available.audioAssetId)
        assertTrue(available.relativePath.endsWith(".wav"))
        assertFalse(available.relativePath.startsWith(fixture.root.absolutePath))
        assertEquals(1, fixture.gateway.callCount)
        assertEquals(1, fixture.repository.markAvailableCount)
        assertEquals(AudioFileState.AVAILABLE, fixture.repository.assetsById[queued.asset.id]?.fileState)
        assertTrue(File(fixture.root, available.relativePath).isFile)
        assertFalse(fixture.root.listFiles().orEmpty().any { it.name.endsWith(".part") })
    }

    @Test
    fun lateGatewaySuccessCannotOverrideCanceledAssetAndLeavesNoFinalFile() = runBlocking {
        val fixture = fixture()
        val queued = fixture.coordinator.createGenerationRequest(fixture.createCommand())
            as AudioGenerationRequestResult.Queued
        fixture.gateway.bytes = validWavBytes()
        fixture.gateway.beforeSuccess = {
            fixture.repository.forceState(queued.asset.id, AudioFileState.CANCELED)
        }

        val result = fixture.coordinator.execute(queued.asset.id)

        assertEquals(
            AudioGenerationExecutionResult.Suppressed(AudioGenerationErrorCode.CANCELED),
            result,
        )
        assertEquals(0, fixture.repository.markAvailableCount)
        assertEquals(AudioFileState.CANCELED, fixture.repository.assetsById[queued.asset.id]?.fileState)
        assertTrue(fixture.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun repositoryRejectingAvailableTransitionRollsBackCommittedFile() = runBlocking {
        val fixture = fixture()
        val queued = fixture.coordinator.createGenerationRequest(fixture.createCommand())
            as AudioGenerationRequestResult.Queued
        fixture.gateway.bytes = validWavBytes()
        fixture.repository.allowMarkAvailable = false

        val result = fixture.coordinator.execute(queued.asset.id)

        assertEquals(
            AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.REPOSITORY_REJECTED),
            result,
        )
        assertEquals(1, fixture.repository.markAvailableCount)
        assertNull(fixture.repository.assetsById[queued.asset.id]?.storagePath)
        assertTrue(fixture.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun gatewayFailureUsesStableErrorAndRemovesTemporaryFile() = runBlocking {
        val fixture = fixture()
        val queued = fixture.coordinator.createGenerationRequest(fixture.createCommand())
            as AudioGenerationRequestResult.Queued
        fixture.gateway.failure = AudioGenerationGatewayErrorCode.RATE_LIMITED

        val result = fixture.coordinator.execute(queued.asset.id)

        assertEquals(
            AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.RATE_LIMITED),
            result,
        )
        assertEquals(1, fixture.repository.markFailedCount)
        assertEquals(AudioFileState.FAILED, fixture.repository.assetsById[queued.asset.id]?.fileState)
        assertTrue(fixture.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancelPersistsCanceledStateCancelsUniqueWorkAndRemovesPartFile() = runBlocking {
        val fixture = fixture()
        val queued = fixture.coordinator.createGenerationRequest(fixture.createCommand())
            as AudioGenerationRequestResult.Queued
        val target = fixture.fileStore.createPendingTarget(queued.asset.id, AudioTargetFormat.WAV)
        fixture.fileStore.openPendingWriter(target).use { it.write(validWavBytes()) }

        val result = fixture.coordinator.cancelGeneration(queued.asset.id)

        assertEquals(AudioGenerationCancelResult.Canceled(queued.asset.id), result)
        assertEquals(1, fixture.scheduler.cancelCount)
        assertEquals(AudioFileState.CANCELED, fixture.repository.assetsById[queued.asset.id]?.fileState)
        assertFalse(target.temporaryFile.exists())
        assertFalse(target.finalFile.exists())
    }

    private fun fixture(
        usableBytes: Long = Long.MAX_VALUE,
    ): Fixture {
        val root = temporaryFolder.newFolder("audio-${System.nanoTime()}")
        val sourceReference = AudioSourceReference.Message(
            issueId = "issue-1",
            stageId = "stage-1",
            messageId = 42L,
        )
        val sourceSnapshot = AudioSourceSnapshot(
            source = AudioAssetSource.CompletedMessage(
                issueId = sourceReference.issueId,
                stageId = sourceReference.stageId,
                contentHash = "content-hash-1",
                messageId = sourceReference.messageId,
            ),
            content = "仅在 Gateway 进程内消费的来源正文",
        )
        val repository = FakeAudioAssetRepository(sourceReference, sourceSnapshot)
        val scheduler = FakeAudioGenerationScheduler()
        val gateway = FakeAudioGenerationGateway()
        val fileStore = AudioFileStore(
            rootDirectory = root,
            usableSpaceProvider = { usableBytes },
        )
        val coordinator = AudioGenerationCoordinator(
            repository = repository,
            scheduler = scheduler,
            gateway = gateway,
            fileStore = fileStore,
            audioAssetIdFactory = { "asset-1" },
        )
        return Fixture(
            root = root,
            sourceReference = sourceReference,
            sourceSnapshot = sourceSnapshot,
            repository = repository,
            scheduler = scheduler,
            gateway = gateway,
            fileStore = fileStore,
            coordinator = coordinator,
        )
    }

    private data class Fixture(
        val root: File,
        val sourceReference: AudioSourceReference.Message,
        val sourceSnapshot: AudioSourceSnapshot,
        val repository: FakeAudioAssetRepository,
        val scheduler: FakeAudioGenerationScheduler,
        val gateway: FakeAudioGenerationGateway,
        val fileStore: AudioFileStore,
        val coordinator: AudioGenerationCoordinator,
    ) {
        fun createCommand(
            userConfirmed: Boolean = true,
            estimatedOutputBytes: Long = 64L,
            minimumReservationBytes: Long = 64L,
            safetyMarginBytes: Long = 16L,
        ): CreateAudioGenerationCommand {
            return CreateAudioGenerationCommand(
                sourceReference = sourceReference,
                config = defaultConfig(),
                userConfirmed = userConfirmed,
                estimatedOutputBytes = estimatedOutputBytes,
                minimumReservationBytes = minimumReservationBytes,
                safetyMarginBytes = safetyMarginBytes,
            )
        }

        fun record(state: AudioFileState): AudioAssetRecord {
            val config = defaultConfig()
            return AudioAssetRecord(
                id = "existing-${state.storageValue}",
                source = sourceSnapshot.source,
                config = config,
                generationKey = AudioGenerationKeyFactory.create(sourceSnapshot.source, config),
                fileState = state,
                storagePath = if (state == AudioFileState.AVAILABLE) "existing.wav" else null,
                mimeType = if (state == AudioFileState.AVAILABLE) "audio/wav" else null,
                sizeBytes = if (state == AudioFileState.AVAILABLE) 48L else 0L,
                deletedAt = null,
                purgeRequestedAt = null,
            )
        }

        private fun defaultConfig(): AudioGenerationConfig {
            return AudioGenerationConfig(
                voiceProfileId = AudioGenerationCoordinator.V1_VOICE_PROFILE_ID,
                targetFormat = AudioTargetFormat.WAV,
                parameterVersion = AudioGenerationCoordinator.V1_PARAMETER_VERSION,
            )
        }
    }

    private class FakeAudioAssetRepository(
        private val expectedReference: AudioSourceReference,
        private val sourceSnapshot: AudioSourceSnapshot,
    ) : AudioAssetRepositoryPort {
        val assetsById = linkedMapOf<String, AudioAssetRecord>()
        val assetsByKey = linkedMapOf<String, AudioAssetRecord>()
        var loadSourceCount = 0
        var createPendingCount = 0
        var markAvailableCount = 0
        var markFailedCount = 0
        var allowMarkAvailable = true

        override suspend fun loadSource(reference: AudioSourceReference): AudioSourceLoadResult {
            loadSourceCount += 1
            return if (reference == expectedReference) {
                AudioSourceLoadResult.Ready(sourceSnapshot)
            } else {
                AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.SOURCE_NOT_FOUND)
            }
        }

        override suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord? {
            return assetsByKey[generationKey]
        }

        override suspend fun createPending(command: CreatePendingAudioCommand): AudioAssetCreateResult {
            createPendingCount += 1
            val existing = assetsByKey[command.generationKey]
            if (existing != null) return AudioAssetCreateResult.Existing(existing)
            val record = AudioAssetRecord(
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
            assetsById[record.id] = record
            assetsByKey[record.generationKey] = record
            return AudioAssetCreateResult.Created(record)
        }

        override suspend fun loadAsset(audioAssetId: String): AudioAssetRecord? {
            return assetsById[audioAssetId]
        }

        override suspend fun markAvailable(command: MarkAudioAvailableCommand): Boolean {
            markAvailableCount += 1
            val current = assetsById[command.audioAssetId] ?: return false
            if (!allowMarkAvailable || current.fileState != AudioFileState.PENDING) return false
            val updated = current.copy(
                fileState = AudioFileState.AVAILABLE,
                storagePath = command.relativePath,
                mimeType = command.mimeType,
                sizeBytes = command.sizeBytes,
            )
            assetsById[updated.id] = updated
            assetsByKey[updated.generationKey] = updated
            return true
        }

        override suspend fun markFailed(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean {
            markFailedCount += 1
            val current = assetsById[audioAssetId] ?: return false
            if (current.fileState != expectedState) return false
            forceState(audioAssetId, AudioFileState.FAILED)
            return true
        }

        override suspend fun markCanceled(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean {
            val current = assetsById[audioAssetId] ?: return false
            if (current.fileState != expectedState) return false
            forceState(audioAssetId, AudioFileState.CANCELED)
            return true
        }

        fun forceState(
            audioAssetId: String,
            state: AudioFileState,
        ) {
            val current = assetsById.getValue(audioAssetId)
            val updated = current.copy(fileState = state)
            assetsById[audioAssetId] = updated
            assetsByKey[updated.generationKey] = updated
        }
    }

    private class FakeAudioGenerationScheduler : AudioGenerationSchedulerPort {
        val activeWorkNames = linkedSetOf<String>()
        var scheduleCount = 0
        var cancelCount = 0

        override suspend fun isActive(uniqueWorkName: String): Boolean {
            return uniqueWorkName in activeWorkNames
        }

        override suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean {
            scheduleCount += 1
            activeWorkNames += plan.uniqueWorkName
            return true
        }

        override suspend fun cancel(uniqueWorkName: String): Boolean {
            cancelCount += 1
            activeWorkNames -= uniqueWorkName
            return true
        }
    }

    private class FakeAudioGenerationGateway : AudioGenerationGateway {
        var callCount = 0
        var bytes: ByteArray = ByteArray(0)
        var failure: AudioGenerationGatewayErrorCode? = null
        var beforeSuccess: (() -> Unit)? = null

        override suspend fun generate(
            request: AudioGenerationRequest,
            output: AudioGenerationOutput,
        ): AudioGenerationGatewayResult {
            callCount += 1
            failure?.let { return AudioGenerationGatewayResult.Failure(it) }
            output.write(bytes)
            beforeSuccess?.invoke()
            return AudioGenerationGatewayResult.Success
        }
    }

    private fun validWavBytes(): ByteArray {
        val bytes = ByteArray(48)
        "RIFF".toByteArray().copyInto(bytes, destinationOffset = 0)
        "WAVE".toByteArray().copyInto(bytes, destinationOffset = 8)
        "fmt ".toByteArray().copyInto(bytes, destinationOffset = 12)
        "data".toByteArray().copyInto(bytes, destinationOffset = 36)
        bytes[44] = 1
        bytes[45] = 2
        bytes[46] = 3
        bytes[47] = 4
        return bytes
    }
}
