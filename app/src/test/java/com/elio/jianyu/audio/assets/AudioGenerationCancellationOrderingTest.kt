package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioGenerationCancellationOrderingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rejectedCanceledTransitionDoesNotCancelWorkOrRemoveTemporaryFile() = runBlocking {
        val root = temporaryFolder.newFolder("cancel-ordering")
        val source = AudioAssetSource.CompletedMessage(
            issueId = "issue-1",
            stageId = "stage-1",
            contentHash = "content-hash",
            messageId = 42L,
        )
        val config = AudioGenerationConfig(
            voiceProfileId = AudioGenerationCoordinator.V1_VOICE_PROFILE_ID,
            targetFormat = AudioTargetFormat.WAV,
            parameterVersion = AudioGenerationCoordinator.V1_PARAMETER_VERSION,
        )
        val asset = AudioAssetRecord(
            id = "asset-cancel-ordering",
            source = source,
            config = config,
            generationKey = AudioGenerationKeyFactory.create(source, config),
            fileState = AudioFileState.PENDING,
            storagePath = null,
            mimeType = null,
            sizeBytes = 0L,
            deletedAt = null,
            purgeRequestedAt = null,
        )
        val repository = RejectingCancellationRepository(asset)
        val scheduler = RecordingScheduler()
        val fileStore = AudioFileStore(root)
        val pendingTarget = fileStore.createPendingTarget(asset.id, AudioTargetFormat.WAV)
        fileStore.openPendingWriter(pendingTarget).use { writer ->
            writer.write(byteArrayOf(1, 2, 3, 4))
        }
        val coordinator = AudioGenerationCoordinator(
            repository = repository,
            scheduler = scheduler,
            gateway = AudioGenerationGateway { _, _ ->
                error("取消流程不应调用 Gateway")
            },
            fileStore = fileStore,
            audioAssetIdFactory = { "unused" },
        )

        val result = coordinator.cancelGeneration(asset.id)

        assertEquals(
            AudioGenerationCancelResult.Failure(AudioGenerationErrorCode.REPOSITORY_REJECTED),
            result,
        )
        assertEquals(1, repository.markCanceledCount)
        assertEquals(0, scheduler.cancelCount)
        assertEquals(AudioFileState.PENDING, repository.current.fileState)
        assertTrue(pendingTarget.temporaryFile.isFile)
    }

    private class RejectingCancellationRepository(
        initial: AudioAssetRecord,
    ) : AudioAssetRepositoryPort {
        var current: AudioAssetRecord = initial
        var markCanceledCount: Int = 0

        override suspend fun loadSource(reference: AudioSourceReference): AudioSourceLoadResult {
            error("取消流程不应加载来源")
        }

        override suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord? {
            error("取消流程不应按 Generation Key 查询")
        }

        override suspend fun createPending(
            command: CreatePendingAudioCommand,
        ): AudioAssetCreateResult {
            error("取消流程不应创建资产")
        }

        override suspend fun loadAsset(audioAssetId: String): AudioAssetRecord? {
            return current.takeIf { it.id == audioAssetId }
        }

        override suspend fun markAvailable(command: MarkAudioAvailableCommand): Boolean {
            error("取消流程不应标记可用")
        }

        override suspend fun markFailed(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean {
            error("取消流程不应标记失败")
        }

        override suspend fun markCanceled(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean {
            markCanceledCount += 1
            return false
        }
    }

    private class RecordingScheduler : AudioGenerationSchedulerPort {
        var cancelCount: Int = 0

        override suspend fun isActive(uniqueWorkName: String): Boolean = true

        override suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean {
            error("取消流程不应排队")
        }

        override suspend fun cancel(uniqueWorkName: String): Boolean {
            cancelCount += 1
            return true
        }
    }
}
