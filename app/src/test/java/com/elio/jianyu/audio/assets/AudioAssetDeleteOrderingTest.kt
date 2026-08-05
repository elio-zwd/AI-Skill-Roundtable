package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioAssetDeleteOrderingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rejectedDeleteRequestDoesNotCancelPendingWork() = runBlocking {
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
            id = "asset-delete-ordering",
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
        val repository = RejectingDeleteRepository(asset)
        val scheduler = RecordingScheduler()
        val service = AudioAssetLifecycleService(
            repository = repository,
            scheduler = scheduler,
            fileStore = AudioFileStore(temporaryFolder.newFolder("delete-ordering")),
        )

        val result = service.requestDelete(
            RequestAudioAssetDeleteCommand(
                audioAssetId = asset.id,
                requestedAt = 100L,
                userConfirmed = true,
            ),
        )

        assertEquals(
            AudioAssetDeleteRequestResult.Failure(
                AudioGenerationErrorCode.REPOSITORY_REJECTED,
            ),
            result,
        )
        assertEquals(1, repository.requestDeleteCount)
        assertEquals(0, scheduler.cancelCount)
    }

    private class RejectingDeleteRepository(
        private val asset: AudioAssetRecord,
    ) : AudioAssetLifecycleRepositoryPort {
        var requestDeleteCount: Int = 0

        override suspend fun getAudioAsset(audioAssetId: String): AudioAssetRecord? {
            return asset.takeIf { it.id == audioAssetId }
        }

        override suspend fun listAudioAssetsForIssue(issueId: String): List<AudioAssetRecord> {
            error("删除请求不应查询议题列表")
        }

        override suspend fun listAudioAssetsForStage(
            issueId: String,
            stageId: String,
        ): List<AudioAssetRecord> {
            error("删除请求不应查询阶段列表")
        }

        override suspend fun markMissing(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean {
            error("删除请求不应标记缺失")
        }

        override suspend fun requestDelete(
            command: PersistAudioDeleteRequestCommand,
        ): AudioDeleteWriteResult {
            requestDeleteCount += 1
            return AudioDeleteWriteResult.Rejected
        }
    }

    private class RecordingScheduler : AudioGenerationSchedulerPort {
        var cancelCount: Int = 0

        override suspend fun isActive(uniqueWorkName: String): Boolean = true

        override suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean {
            error("删除请求不应排队")
        }

        override suspend fun cancel(uniqueWorkName: String): Boolean {
            cancelCount += 1
            return true
        }
    }
}
