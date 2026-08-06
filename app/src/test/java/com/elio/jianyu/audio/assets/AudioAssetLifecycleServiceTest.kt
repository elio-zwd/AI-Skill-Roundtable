package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioAssetLifecycleServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun reconcileMarksUnavailableAvailableRecordsMissingAndReportsOrphansWithoutDeleting() = runBlocking {
        val fixture = fixture()
        val presentPath = fixture.commitWav("present")
        val orphanPath = fixture.commitWav("orphan")
        val present = fixture.record(
            id = "asset-present",
            state = AudioFileState.AVAILABLE,
            storagePath = presentPath,
            sizeBytes = 48L,
        )
        val missing = fixture.record(
            id = "asset-missing",
            state = AudioFileState.AVAILABLE,
            storagePath = "missing.wav",
            sizeBytes = 48L,
        )
        val pending = fixture.record(
            id = "asset-pending",
            state = AudioFileState.PENDING,
        )
        fixture.repository.issueAssets += listOf(present, missing, pending)

        val result = fixture.service.reconcileFilesForIssue("issue-1")

        assertEquals(listOf("asset-missing"), result.markedMissingAssetIds)
        assertTrue(result.staleAssetIds.isEmpty())
        assertEquals(listOf(orphanPath), result.orphanReport.files.map { it.relativePath })
        assertEquals(AudioFileState.MISSING, fixture.repository.asset("asset-missing")?.fileState)
        assertEquals(AudioFileState.AVAILABLE, fixture.repository.asset("asset-present")?.fileState)
        assertTrue(fixture.fileStore.resolve(presentPath) is AudioFileResolution.Available)
        assertTrue(fixture.fileStore.resolve(orphanPath) is AudioFileResolution.Available)
        assertEquals(0, fixture.repository.deleteRequestCount)
    }

    @Test
    fun reconcileReportsCasRejectionAsStaleInsteadOfClaimingMissingTransition() = runBlocking {
        val fixture = fixture()
        fixture.repository.issueAssets += fixture.record(
            id = "asset-stale",
            state = AudioFileState.AVAILABLE,
            storagePath = "missing.wav",
            sizeBytes = 48L,
        )
        fixture.repository.allowMarkMissing = false

        val result = fixture.service.reconcileFilesForIssue("issue-1")

        assertTrue(result.markedMissingAssetIds.isEmpty())
        assertEquals(listOf("asset-stale"), result.staleAssetIds)
        assertEquals(AudioFileState.AVAILABLE, fixture.repository.asset("asset-stale")?.fileState)
    }

    @Test
    fun purgeImpactListsPendingWorkFilesMissingAndOrphansWithoutPhysicalCleanup() = runBlocking {
        val fixture = fixture()
        val availablePath = fixture.commitWav("available")
        val orphanPath = fixture.commitWav("orphan-impact")
        val available = fixture.record(
            id = "asset-available",
            state = AudioFileState.AVAILABLE,
            storagePath = availablePath,
            sizeBytes = 48L,
        )
        val missing = fixture.record(
            id = "asset-missing",
            state = AudioFileState.MISSING,
            storagePath = "missing.wav",
            sizeBytes = 48L,
        )
        val pending = fixture.record(
            id = "asset-pending",
            state = AudioFileState.PENDING,
        )
        fixture.repository.issueAssets += listOf(available, missing, pending)

        val impact = fixture.service.inspectPurgeImpact("issue-1")

        assertEquals(3, impact.assetCount)
        assertEquals(1, impact.pendingAssetCount)
        assertEquals(1, impact.referencedFileCount)
        assertEquals(48L, impact.referencedFileBytes)
        assertEquals(listOf("asset-missing"), impact.missingAssetIds)
        assertEquals(1, impact.uniqueWorkNames.size)
        assertTrue(impact.uniqueWorkNames.single().matches(Regex("audio-generation:[0-9a-f]{64}")))
        assertEquals(listOf(orphanPath), impact.orphanReport.files.map { it.relativePath })
        assertTrue(fixture.fileStore.resolve(availablePath) is AudioFileResolution.Available)
        assertTrue(fixture.fileStore.resolve(orphanPath) is AudioFileResolution.Available)
        assertEquals(0, fixture.scheduler.cancelCount)
        assertEquals(0, fixture.repository.deleteRequestCount)
    }

    @Test
    fun deleteRequestRequiresConfirmationBeforeRepositoryOrSchedulerSideEffects() = runBlocking {
        val fixture = fixture()

        val result = fixture.service.requestDelete(
            RequestAudioAssetDeleteCommand(
                audioAssetId = "asset-1",
                requestedAt = 1_000L,
                userConfirmed = false,
            ),
        )

        assertEquals(AudioAssetDeleteRequestResult.ConfirmationRequired, result)
        assertEquals(0, fixture.repository.loadAssetCount)
        assertEquals(0, fixture.repository.deleteRequestCount)
        assertEquals(0, fixture.scheduler.cancelCount)
    }

    @Test
    fun pendingDeleteRequestCancelsUniqueWorkPersistsRequestAndKeepsFilesAndSourceMetadata() = runBlocking {
        val fixture = fixture()
        val path = fixture.commitWav("pending-race")
        val pending = fixture.record(
            id = "asset-pending-delete",
            state = AudioFileState.PENDING,
            storagePath = path,
            sizeBytes = 48L,
        )
        fixture.repository.issueAssets += pending

        val result = fixture.service.requestDelete(
            RequestAudioAssetDeleteCommand(
                audioAssetId = pending.id,
                requestedAt = 2_000L,
                userConfirmed = true,
            ),
        )

        assertEquals(AudioAssetDeleteRequestResult.Requested(pending.id), result)
        assertEquals(1, fixture.scheduler.cancelCount)
        assertEquals(1, fixture.repository.deleteRequestCount)
        val updated = fixture.repository.asset(pending.id)!!
        assertEquals(pending.source, updated.source)
        assertEquals(2_000L, updated.purgeRequestedAt)
        assertTrue(fixture.fileStore.resolve(path) is AudioFileResolution.Available)
    }

    @Test
    fun availableDeleteRequestDoesNotCancelWorkOrDeleteFile() = runBlocking {
        val fixture = fixture()
        val path = fixture.commitWav("available-delete")
        val available = fixture.record(
            id = "asset-available-delete",
            state = AudioFileState.AVAILABLE,
            storagePath = path,
            sizeBytes = 48L,
        )
        fixture.repository.issueAssets += available

        val result = fixture.service.requestDelete(
            RequestAudioAssetDeleteCommand(
                audioAssetId = available.id,
                requestedAt = 3_000L,
                userConfirmed = true,
            ),
        )

        assertEquals(AudioAssetDeleteRequestResult.Requested(available.id), result)
        assertEquals(0, fixture.scheduler.cancelCount)
        assertTrue(fixture.fileStore.resolve(path) is AudioFileResolution.Available)
        assertEquals(3_000L, fixture.repository.asset(available.id)?.purgeRequestedAt)
    }

    @Test
    fun stageAndIssueQueriesRemainRepositoryCapabilitiesWithoutDaoExposure() = runBlocking {
        val fixture = fixture()
        val stageOne = fixture.record(
            id = "stage-1-asset",
            state = AudioFileState.FAILED,
            stageId = "stage-1",
        )
        val stageTwo = fixture.record(
            id = "stage-2-asset",
            state = AudioFileState.CANCELED,
            stageId = "stage-2",
        )
        fixture.repository.issueAssets += listOf(stageOne, stageTwo)

        assertEquals(
            listOf(stageOne, stageTwo),
            fixture.service.listAudioAssetsForIssue("issue-1"),
        )
        assertEquals(
            listOf(stageOne),
            fixture.service.listAudioAssetsForStage("issue-1", "stage-1"),
        )
    }

    private fun fixture(): Fixture {
        val root = temporaryFolder.newFolder("lifecycle-${System.nanoTime()}")
        val fileStore = AudioFileStore(root)
        val repository = FakeLifecycleRepository()
        val scheduler = FakeLifecycleScheduler()
        return Fixture(
            fileStore = fileStore,
            repository = repository,
            scheduler = scheduler,
            service = AudioAssetLifecycleService(
                repository = repository,
                scheduler = scheduler,
                fileStore = fileStore,
            ),
        )
    }

    private data class Fixture(
        val fileStore: AudioFileStore,
        val repository: FakeLifecycleRepository,
        val scheduler: FakeLifecycleScheduler,
        val service: AudioAssetLifecycleService,
    ) {
        fun record(
            id: String,
            state: AudioFileState,
            issueId: String = "issue-1",
            stageId: String = "stage-1",
            storagePath: String? = null,
            sizeBytes: Long = 0L,
        ): AudioAssetRecord {
            val source = AudioAssetSource.CompletedMessage(
                issueId = issueId,
                stageId = stageId,
                contentHash = "content-$id",
                messageId = id.hashCode().toLong(),
            )
            val config = AudioGenerationConfig(
                voiceProfileId = AudioGenerationCoordinator.V1_VOICE_PROFILE_ID,
                targetFormat = AudioTargetFormat.WAV,
                parameterVersion = AudioGenerationCoordinator.V1_PARAMETER_VERSION,
            )
            return AudioAssetRecord(
                id = id,
                source = source,
                config = config,
                generationKey = AudioGenerationKeyFactory.create(source, config),
                fileState = state,
                storagePath = storagePath,
                mimeType = storagePath?.let { "audio/wav" },
                sizeBytes = sizeBytes,
                deletedAt = null,
                purgeRequestedAt = null,
            )
        }

        fun commitWav(audioAssetId: String): String {
            val target = fileStore.createPendingTarget(audioAssetId, AudioTargetFormat.WAV)
            fileStore.openPendingWriter(target).use { it.write(validWavBytes()) }
            return (fileStore.commit(target) as AudioFileCommitResult.Success).file.relativePath
        }
    }

    private class FakeLifecycleRepository : AudioAssetLifecycleRepositoryPort {
        val issueAssets = mutableListOf<AudioAssetRecord>()
        var allowMarkMissing = true
        var loadAssetCount = 0
        var deleteRequestCount = 0

        override suspend fun getAudioAsset(audioAssetId: String): AudioAssetRecord? {
            loadAssetCount += 1
            return asset(audioAssetId)
        }

        override suspend fun listAudioAssetsForIssue(issueId: String): List<AudioAssetRecord> {
            return issueAssets.filter { it.source.issueId == issueId }
        }

        override suspend fun listAudioAssetsForStage(
            issueId: String,
            stageId: String,
        ): List<AudioAssetRecord> {
            return issueAssets.filter {
                it.source.issueId == issueId && it.source.stageId == stageId
            }
        }

        override suspend fun markMissing(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean {
            if (!allowMarkMissing) return false
            val index = issueAssets.indexOfFirst { it.id == audioAssetId }
            if (index < 0 || issueAssets[index].fileState != expectedState) return false
            issueAssets[index] = issueAssets[index].copy(fileState = AudioFileState.MISSING)
            return true
        }

        override suspend fun requestDelete(
            command: PersistAudioDeleteRequestCommand,
        ): AudioDeleteWriteResult {
            deleteRequestCount += 1
            val index = issueAssets.indexOfFirst { it.id == command.audioAssetId }
            if (index < 0) return AudioDeleteWriteResult.Rejected
            val current = issueAssets[index]
            if (current.fileState != command.expectedState || current.purgeRequestedAt != null) {
                return AudioDeleteWriteResult.Rejected
            }
            issueAssets[index] = current.copy(purgeRequestedAt = command.requestedAt)
            return AudioDeleteWriteResult.Requested(issueAssets[index])
        }

        fun asset(audioAssetId: String): AudioAssetRecord? {
            return issueAssets.firstOrNull { it.id == audioAssetId }
        }
    }

    private class FakeLifecycleScheduler : AudioGenerationSchedulerPort {
        var cancelCount = 0

        override suspend fun isActive(uniqueWorkName: String): Boolean = false

        override suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean = true

        override suspend fun cancel(uniqueWorkName: String): Boolean {
            cancelCount += 1
            return true
        }
    }

    private companion object {
        fun validWavBytes(): ByteArray {
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
}
