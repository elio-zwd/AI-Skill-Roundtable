package com.elio.jianyu.ui.screens.execution

import com.elio.jianyu.audio.assets.AudioAssetRecord
import com.elio.jianyu.audio.assets.AudioAssetSource
import com.elio.jianyu.audio.assets.AudioGenerationConfig
import com.elio.jianyu.audio.assets.AudioSourceReference
import com.elio.jianyu.audio.assets.AudioTargetFormat
import com.elio.jianyu.data.AudioFileState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAssetWorkspaceControllerTest {
    @Test
    fun loadOnlyReadsAssetsAndDoesNotGenerateOrSchedule() = runBlocking {
        val operations = FakeOperations(listOf(asset()))
        val controller = AudioAssetWorkspaceController(operations)
        controller.load("issue-1", "stage-1")
        assertEquals(1, operations.listCalls)
        assertEquals(0, operations.generateCalls)
        assertEquals(0, operations.retryCalls)
        assertEquals(0, operations.deleteCalls)
        assertEquals(listOf(asset()), controller.state.assets)
    }

    @Test
    fun generationRequiresExplicitConfirmation() = runBlocking {
        val operations = FakeOperations(emptyList())
        val controller = AudioAssetWorkspaceController(operations)
        val reference = AudioSourceReference.Message("issue-1", "stage-1", 10L)
        controller.load("issue-1", "stage-1")
        controller.requestGeneration(reference)
        assertEquals(0, operations.generateCalls)
        assertTrue(controller.state.pendingAction is AudioAssetPendingAction.Generate)
        controller.confirmPendingAction()
        assertEquals(1, operations.generateCalls)
        assertEquals(reference, operations.lastReference)
        assertNull(controller.state.pendingAction)
    }

    @Test
    fun retryAndDeleteRequireConfirmationWhileDismissDoesNothing() = runBlocking {
        val operations = FakeOperations(listOf(asset()))
        val controller = AudioAssetWorkspaceController(operations)
        controller.load("issue-1", "stage-1")
        controller.requestRetry("asset-1")
        controller.dismissPendingAction()
        assertEquals(0, operations.retryCalls)
        controller.requestRetry("asset-1")
        controller.confirmPendingAction()
        assertEquals(1, operations.retryCalls)
        controller.requestDelete("asset-1")
        assertEquals(0, operations.deleteCalls)
        controller.confirmPendingAction()
        assertEquals(1, operations.deleteCalls)
    }

    private class FakeOperations(
        private val listedAssets: List<AudioAssetRecord>,
    ) : AudioAssetWorkspaceOperations {
        var listCalls = 0
        var generateCalls = 0
        var retryCalls = 0
        var deleteCalls = 0
        var lastReference: AudioSourceReference? = null

        override suspend fun listStage(issueId: String, stageId: String): List<AudioAssetRecord> {
            listCalls += 1
            return listedAssets
        }

        override suspend fun generate(reference: AudioSourceReference): AudioAssetWorkspaceOperationResult {
            generateCalls += 1
            lastReference = reference
            return AudioAssetWorkspaceOperationResult.Success("已加入生成队列")
        }

        override suspend fun retry(audioAssetId: String): AudioAssetWorkspaceOperationResult {
            retryCalls += 1
            return AudioAssetWorkspaceOperationResult.Success("已重新排队")
        }

        override suspend fun cancel(audioAssetId: String): AudioAssetWorkspaceOperationResult =
            AudioAssetWorkspaceOperationResult.Success("已取消")

        override suspend fun requestDelete(audioAssetId: String): AudioAssetWorkspaceOperationResult {
            deleteCalls += 1
            return AudioAssetWorkspaceOperationResult.Success("已记录删除请求")
        }

        override fun play(asset: AudioAssetRecord): AudioAssetWorkspaceOperationResult =
            AudioAssetWorkspaceOperationResult.Success("正在播放")

        override fun pause(): AudioAssetWorkspaceOperationResult =
            AudioAssetWorkspaceOperationResult.Success("已暂停")

        override fun resume(): AudioAssetWorkspaceOperationResult =
            AudioAssetWorkspaceOperationResult.Success("继续播放")

        override fun stop(): AudioAssetWorkspaceOperationResult =
            AudioAssetWorkspaceOperationResult.Success("已停止")
    }

    private fun asset() = AudioAssetRecord(
        id = "asset-1",
        source = AudioAssetSource.CompletedMessage("issue-1", "stage-1", "hash", 10L),
        config = AudioGenerationConfig("jianyu-default", AudioTargetFormat.WAV, 1),
        generationKey = "generation-key",
        fileState = AudioFileState.AVAILABLE,
        storagePath = "asset.wav",
        mimeType = "audio/wav",
        sizeBytes = 128L,
        deletedAt = null,
        purgeRequestedAt = null,
    )
}
