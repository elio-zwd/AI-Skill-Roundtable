package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioAssetGlobalOrphanSafetyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun fileReferencedByAnotherIssueIsNeverReportedAsOrphan() = runBlocking {
        val fileStore = AudioFileStore(temporaryFolder.newFolder("global-orphan-safety"))
        val issueOnePath = commitWav(fileStore, "issue-one")
        val issueTwoPath = commitWav(fileStore, "issue-two")
        val repository = GlobalFakeRepository(
            mutableListOf(
                record("asset-one", "issue-1", issueOnePath),
                record("asset-two", "issue-2", issueTwoPath),
            ),
        )
        val service = AudioAssetLifecycleService(
            repository = repository,
            scheduler = NoOpScheduler,
            fileStore = fileStore,
        )

        val result = service.reconcileFilesForIssue("issue-1")

        assertTrue(
            "其他议题仍引用的文件不能被误报为孤儿：${result.orphanReport.files}",
            result.orphanReport.files.isEmpty(),
        )
    }

    private class GlobalFakeRepository(
        private val assets: MutableList<AudioAssetRecord>,
    ) : AudioAssetLifecycleRepositoryPort, AudioAssetGlobalReferenceRepositoryPort {
        override suspend fun getAudioAsset(audioAssetId: String): AudioAssetRecord? =
            assets.firstOrNull { it.id == audioAssetId }

        override suspend fun listAudioAssetsForIssue(issueId: String): List<AudioAssetRecord> =
            assets.filter { it.source.issueId == issueId }

        override suspend fun listAudioAssetsForStage(
            issueId: String,
            stageId: String,
        ): List<AudioAssetRecord> = assets.filter {
            it.source.issueId == issueId && it.source.stageId == stageId
        }

        override suspend fun listAllAudioAssets(): List<AudioAssetRecord> = assets.toList()

        override suspend fun markMissing(
            audioAssetId: String,
            expectedState: AudioFileState,
        ): Boolean = false

        override suspend fun requestDelete(
            command: PersistAudioDeleteRequestCommand,
        ): AudioDeleteWriteResult = AudioDeleteWriteResult.Rejected
    }

    private object NoOpScheduler : AudioGenerationSchedulerPort {
        override suspend fun isActive(uniqueWorkName: String): Boolean = false
        override suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean = true
        override suspend fun cancel(uniqueWorkName: String): Boolean = true
    }

    private fun record(
        id: String,
        issueId: String,
        storagePath: String,
    ): AudioAssetRecord {
        val source = AudioAssetSource.CompletedMessage(
            issueId = issueId,
            stageId = "stage-1",
            contentHash = "hash-$id",
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
            fileState = AudioFileState.AVAILABLE,
            storagePath = storagePath,
            mimeType = "audio/wav",
            sizeBytes = 48L,
            deletedAt = null,
            purgeRequestedAt = null,
        )
    }

    private fun commitWav(fileStore: AudioFileStore, audioAssetId: String): String {
        val target = fileStore.createPendingTarget(audioAssetId, AudioTargetFormat.WAV)
        fileStore.openPendingWriter(target).use { writer -> writer.write(validWavBytes()) }
        return (fileStore.commit(target) as AudioFileCommitResult.Success).file.relativePath
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
