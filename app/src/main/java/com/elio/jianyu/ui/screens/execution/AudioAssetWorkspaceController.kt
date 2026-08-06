package com.elio.jianyu.ui.screens.execution

import com.elio.jianyu.audio.assets.AudioAssetDeleteRequestResult
import com.elio.jianyu.audio.assets.AudioAssetPlaybackResult
import com.elio.jianyu.audio.assets.AudioAssetPlaybackState
import com.elio.jianyu.audio.assets.AudioAssetRecord
import com.elio.jianyu.audio.assets.AudioFileReconciliationResult
import com.elio.jianyu.audio.assets.AudioGenerationCancelResult
import com.elio.jianyu.audio.assets.AudioGenerationRequestResult
import com.elio.jianyu.audio.assets.AudioGenerationRetryResult
import com.elio.jianyu.audio.assets.AudioSourceReference
import com.elio.jianyu.audio.assets.CreateAudioGenerationCommand
import com.elio.jianyu.audio.assets.RequestAudioAssetDeleteCommand
import com.elio.jianyu.audio.assets.RetryAudioGenerationCommand
import com.elio.jianyu.audio.runtime.JianyuAudioRuntime
import com.elio.jianyu.data.AudioFileState
import com.elio.jianyu.data.FormalAudioV1Policy

sealed interface AudioAssetPendingAction {
    data class Generate(val reference: AudioSourceReference) : AudioAssetPendingAction
    data class Retry(val audioAssetId: String) : AudioAssetPendingAction
    data class Delete(val audioAssetId: String) : AudioAssetPendingAction
}

data class AudioAssetWorkspaceState(
    val issueId: String? = null,
    val stageId: String? = null,
    val assets: List<AudioAssetRecord> = emptyList(),
    val loading: Boolean = false,
    val operationInProgress: Boolean = false,
    val pendingAction: AudioAssetPendingAction? = null,
    val statusMessage: String? = null,
    val errorCode: String? = null,
    val reconciliation: AudioFileReconciliationResult? = null,
    val playbackState: AudioAssetPlaybackState = AudioAssetPlaybackState.Idle,
)

sealed interface AudioAssetWorkspaceOperationResult {
    data class Success(val message: String) : AudioAssetWorkspaceOperationResult
    data class Failure(val errorCode: String) : AudioAssetWorkspaceOperationResult
}

interface AudioAssetWorkspaceOperations {
    suspend fun listStage(issueId: String, stageId: String): List<AudioAssetRecord>
    suspend fun reconcile(issueId: String): AudioFileReconciliationResult
    suspend fun generate(reference: AudioSourceReference): AudioAssetWorkspaceOperationResult
    suspend fun retry(audioAssetId: String): AudioAssetWorkspaceOperationResult
    suspend fun cancel(audioAssetId: String): AudioAssetWorkspaceOperationResult
    suspend fun requestDelete(audioAssetId: String): AudioAssetWorkspaceOperationResult
    fun play(asset: AudioAssetRecord): AudioAssetWorkspaceOperationResult
    fun pause(): AudioAssetWorkspaceOperationResult
    fun resume(): AudioAssetWorkspaceOperationResult
    fun stop(): AudioAssetWorkspaceOperationResult
    fun playbackState(): AudioAssetPlaybackState = AudioAssetPlaybackState.Idle
    fun release() = Unit
}

/**
 * 议题共享工作区中的音频状态控制器。
 *
 * 构造与 load 只读取 Room 资产；生成、重试和删除必须先保存 pendingAction，
 * 再由用户触发 confirmPendingAction。缺失与孤儿对账也只能由显式扫描触发，
 * 避免恢复、导航或重组自动改变状态、消耗网络或删除文件。
 */
class AudioAssetWorkspaceController(
    private val operations: AudioAssetWorkspaceOperations,
) {
    var state: AudioAssetWorkspaceState = AudioAssetWorkspaceState()
        private set

    suspend fun load(issueId: String, stageId: String) {
        if (issueId.isBlank() || stageId.isBlank()) {
            state = AudioAssetWorkspaceState(errorCode = "INVALID_SCOPE")
            return
        }
        state = state.copy(
            issueId = issueId,
            stageId = stageId,
            loading = true,
            operationInProgress = false,
            pendingAction = null,
            statusMessage = null,
            errorCode = null,
            reconciliation = null,
        )
        refresh()
    }

    fun requestGeneration(reference: AudioSourceReference) {
        if (!matchesCurrentScope(reference.issueId, reference.stageId)) {
            state = state.copy(errorCode = "CROSS_STAGE", statusMessage = null)
            return
        }
        state = state.copy(
            pendingAction = AudioAssetPendingAction.Generate(reference),
            errorCode = null,
            statusMessage = null,
        )
    }

    fun requestRetry(audioAssetId: String) {
        if (audioAssetId.isBlank()) return
        state = state.copy(
            pendingAction = AudioAssetPendingAction.Retry(audioAssetId),
            errorCode = null,
            statusMessage = null,
        )
    }

    fun requestDelete(audioAssetId: String) {
        if (audioAssetId.isBlank()) return
        state = state.copy(
            pendingAction = AudioAssetPendingAction.Delete(audioAssetId),
            errorCode = null,
            statusMessage = null,
        )
    }

    fun dismissPendingAction() {
        state = state.copy(pendingAction = null)
    }

    suspend fun confirmPendingAction() {
        val action = state.pendingAction ?: return
        if (state.operationInProgress) return
        state = state.copy(
            operationInProgress = true,
            pendingAction = null,
            statusMessage = null,
            errorCode = null,
        )
        val result = when (action) {
            is AudioAssetPendingAction.Generate -> operations.generate(action.reference)
            is AudioAssetPendingAction.Retry -> operations.retry(action.audioAssetId)
            is AudioAssetPendingAction.Delete -> operations.requestDelete(action.audioAssetId)
        }
        applyOperationResult(result)
        refresh()
    }

    suspend fun reconcileFiles() {
        val issueId = state.issueId ?: return
        if (state.operationInProgress) return
        state = state.copy(
            operationInProgress = true,
            statusMessage = null,
            errorCode = null,
        )
        state = try {
            val result = operations.reconcile(issueId)
            state.copy(
                operationInProgress = false,
                reconciliation = result,
                statusMessage = "缺失与孤儿检查已完成，不会自动删除文件",
                errorCode = null,
            )
        } catch (_: Throwable) {
            state.copy(
                operationInProgress = false,
                errorCode = "RECONCILIATION_FAILURE",
            )
        }
        refresh()
    }

    suspend fun cancelGeneration(audioAssetId: String) {
        if (audioAssetId.isBlank() || state.operationInProgress) return
        state = state.copy(operationInProgress = true, errorCode = null, statusMessage = null)
        applyOperationResult(operations.cancel(audioAssetId))
        refresh()
    }

    suspend fun refresh() {
        val issueId = state.issueId
        val stageId = state.stageId
        if (issueId.isNullOrBlank() || stageId.isNullOrBlank()) {
            state = state.copy(loading = false, operationInProgress = false)
            return
        }
        state = try {
            state.copy(
                assets = operations.listStage(issueId, stageId),
                loading = false,
                operationInProgress = false,
                playbackState = operations.playbackState(),
            )
        } catch (_: Throwable) {
            state.copy(
                loading = false,
                operationInProgress = false,
                errorCode = "STORAGE_FAILURE",
            )
        }
    }

    fun play(asset: AudioAssetRecord) = applyPlaybackResult(operations.play(asset))
    fun pause() = applyPlaybackResult(operations.pause())
    fun resume() = applyPlaybackResult(operations.resume())
    fun stop() = applyPlaybackResult(operations.stop())

    fun release() {
        operations.release()
    }

    private fun matchesCurrentScope(issueId: String, stageId: String): Boolean {
        return state.issueId == issueId && state.stageId == stageId
    }

    private fun applyPlaybackResult(result: AudioAssetWorkspaceOperationResult) {
        applyOperationResult(result)
        state = state.copy(playbackState = operations.playbackState())
    }

    private fun applyOperationResult(result: AudioAssetWorkspaceOperationResult) {
        state = when (result) {
            is AudioAssetWorkspaceOperationResult.Success -> state.copy(
                operationInProgress = false,
                statusMessage = result.message,
                errorCode = null,
            )
            is AudioAssetWorkspaceOperationResult.Failure -> state.copy(
                operationInProgress = false,
                statusMessage = null,
                errorCode = result.errorCode,
            )
        }
    }
}

class RuntimeAudioAssetWorkspaceOperations(
    private val runtime: JianyuAudioRuntime,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : AudioAssetWorkspaceOperations {
    override suspend fun listStage(issueId: String, stageId: String): List<AudioAssetRecord> =
        runtime.lifecycleService.listAudioAssetsForStage(issueId, stageId)

    override suspend fun reconcile(issueId: String): AudioFileReconciliationResult =
        runtime.lifecycleService.reconcileFilesForIssue(issueId)

    override suspend fun generate(reference: AudioSourceReference): AudioAssetWorkspaceOperationResult {
        val result = runtime.generationCoordinator.createGenerationRequest(
            CreateAudioGenerationCommand(
                sourceReference = reference,
                config = FormalAudioV1Policy.defaultConfig,
                userConfirmed = true,
                estimatedOutputBytes = ESTIMATED_OUTPUT_BYTES,
                minimumReservationBytes = MINIMUM_RESERVATION_BYTES,
                safetyMarginBytes = SAFETY_MARGIN_BYTES,
            ),
        )
        return when (result) {
            is AudioGenerationRequestResult.Queued -> success("音频已加入后台生成队列")
            is AudioGenerationRequestResult.ReusedAvailable -> success("已复用可播放音频")
            is AudioGenerationRequestResult.ReusedPending -> success("相同音频正在生成")
            is AudioGenerationRequestResult.ExplicitRetryRequired -> failure("EXPLICIT_RETRY_REQUIRED")
            is AudioGenerationRequestResult.Failure -> failure(result.errorCode.name)
            AudioGenerationRequestResult.ConfirmationRequired -> failure("CONFIRMATION_REQUIRED")
        }
    }

    override suspend fun retry(audioAssetId: String): AudioAssetWorkspaceOperationResult {
        val result = runtime.generationCoordinator.retryGeneration(
            RetryAudioGenerationCommand(
                audioAssetId = audioAssetId,
                userConfirmed = true,
                estimatedOutputBytes = ESTIMATED_OUTPUT_BYTES,
                minimumReservationBytes = MINIMUM_RESERVATION_BYTES,
                safetyMarginBytes = SAFETY_MARGIN_BYTES,
            ),
        )
        return when (result) {
            is AudioGenerationRetryResult.Queued -> success("音频已重新加入后台队列")
            is AudioGenerationRetryResult.ReusedAvailable -> success("音频已可播放")
            is AudioGenerationRetryResult.ReusedPending -> success("音频仍在生成")
            is AudioGenerationRetryResult.Failure -> failure(result.errorCode.name)
            AudioGenerationRetryResult.ConfirmationRequired -> failure("CONFIRMATION_REQUIRED")
        }
    }

    override suspend fun cancel(audioAssetId: String): AudioAssetWorkspaceOperationResult {
        return when (val result = runtime.generationCoordinator.cancelGeneration(audioAssetId)) {
            is AudioGenerationCancelResult.Canceled -> success("已取消音频生成")
            is AudioGenerationCancelResult.Failure -> failure(result.errorCode.name)
        }
    }

    override suspend fun requestDelete(audioAssetId: String): AudioAssetWorkspaceOperationResult {
        return when (val result = runtime.lifecycleService.requestDelete(
            RequestAudioAssetDeleteCommand(
                audioAssetId = audioAssetId,
                requestedAt = nowProvider(),
                userConfirmed = true,
            ),
        )) {
            is AudioAssetDeleteRequestResult.Requested -> success("已记录受控删除请求")
            is AudioAssetDeleteRequestResult.AlreadyRequested -> success("删除请求已存在")
            is AudioAssetDeleteRequestResult.Failure -> failure(result.errorCode.name)
            AudioAssetDeleteRequestResult.ConfirmationRequired -> failure("CONFIRMATION_REQUIRED")
        }
    }

    override fun play(asset: AudioAssetRecord): AudioAssetWorkspaceOperationResult {
        if (asset.fileState != AudioFileState.AVAILABLE || asset.purgeRequestedAt != null) {
            return failure("AUDIO_NOT_AVAILABLE")
        }
        val path = asset.storagePath ?: return failure("FILE_MISSING")
        return runtime.playbackManager.play(asset.id, path).toWorkspaceResult("正在播放")
    }

    override fun pause(): AudioAssetWorkspaceOperationResult =
        runtime.playbackManager.pause().toWorkspaceResult("已暂停")

    override fun resume(): AudioAssetWorkspaceOperationResult =
        runtime.playbackManager.resume().toWorkspaceResult("继续播放")

    override fun stop(): AudioAssetWorkspaceOperationResult =
        runtime.playbackManager.stop().toWorkspaceResult("已停止")

    override fun playbackState(): AudioAssetPlaybackState = runtime.playbackManager.state

    override fun release() {
        runtime.playbackManager.release()
    }

    private fun AudioAssetPlaybackResult.toWorkspaceResult(
        successMessage: String,
    ): AudioAssetWorkspaceOperationResult = when (this) {
        AudioAssetPlaybackResult.STARTED,
        AudioAssetPlaybackResult.PAUSED,
        AudioAssetPlaybackResult.RESUMED,
        AudioAssetPlaybackResult.STOPPED,
        -> success(successMessage)
        is AudioAssetPlaybackResult.Failure -> failure(errorCode.name)
    }

    private fun success(message: String) = AudioAssetWorkspaceOperationResult.Success(message)
    private fun failure(code: String) = AudioAssetWorkspaceOperationResult.Failure(code)

    private companion object {
        const val ESTIMATED_OUTPUT_BYTES = 16L * 1024L * 1024L
        const val MINIMUM_RESERVATION_BYTES = 1L * 1024L * 1024L
        const val SAFETY_MARGIN_BYTES = 8L * 1024L * 1024L
    }
}
