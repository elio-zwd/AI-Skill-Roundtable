package com.elio.jianyu.data

import com.elio.jianyu.audio.assets.AudioAssetCreateResult
import com.elio.jianyu.audio.assets.AudioAssetRecord
import com.elio.jianyu.audio.assets.AudioAssetRepositoryPort
import com.elio.jianyu.audio.assets.AudioAssetRetryResetResult
import com.elio.jianyu.audio.assets.AudioGenerationErrorCode
import com.elio.jianyu.audio.assets.AudioSourceLoadResult
import com.elio.jianyu.audio.assets.AudioSourceReference
import com.elio.jianyu.audio.assets.CreatePendingAudioCommand
import com.elio.jianyu.audio.assets.MarkAudioAvailableCommand
import com.elio.jianyu.audio.assets.ResetAudioForRetryCommand
import com.elio.jianyu.lifecycle.IssueWriteAction

/**
 * 正式音频生成链的最终生命周期门禁。
 *
 * 生成、重试和迟到成功都必须重新读取 Issue 生命周期；FAILED/CANCELED 终态写入仍允许，
 * 以便归档、回收站和 Purge 的停止流程可以安全收敛。
 */
class LifecycleAwareAudioAssetRepository(
    private val delegate: AudioAssetRepositoryPort,
    database: RoundtableDatabase,
) : AudioAssetRepositoryPort {
    private val writeGate = IssueLifecycleWriteGate(database)

    override suspend fun loadSource(reference: AudioSourceReference): AudioSourceLoadResult {
        return if (allows(reference.issueId, "load_audio_source")) {
            delegate.loadSource(reference)
        } else {
            AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.INVALID_STATE)
        }
    }

    override suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord? =
        delegate.findByGenerationKey(generationKey)

    override suspend fun createPending(command: CreatePendingAudioCommand): AudioAssetCreateResult {
        return if (allows(command.source.issueId, "create_audio_asset")) {
            delegate.createPending(command)
        } else {
            AudioAssetCreateResult.Conflict
        }
    }

    override suspend fun loadAsset(audioAssetId: String): AudioAssetRecord? =
        delegate.loadAsset(audioAssetId)

    override suspend fun markAvailable(command: MarkAudioAvailableCommand): Boolean {
        val asset = delegate.loadAsset(command.audioAssetId) ?: return false
        if (!allows(asset.source.issueId, "mark_audio_available")) return false
        return delegate.markAvailable(command)
    }

    override suspend fun markFailed(
        audioAssetId: String,
        expectedState: AudioFileState,
    ): Boolean = delegate.markFailed(audioAssetId, expectedState)

    override suspend fun markCanceled(
        audioAssetId: String,
        expectedState: AudioFileState,
    ): Boolean = delegate.markCanceled(audioAssetId, expectedState)

    override suspend fun resetForRetry(
        command: ResetAudioForRetryCommand,
    ): AudioAssetRetryResetResult {
        return if (allows(command.source.issueId, "retry_audio_asset")) {
            delegate.resetForRetry(command)
        } else {
            AudioAssetRetryResetResult.Rejected
        }
    }

    private suspend fun allows(issueId: String, operation: String): Boolean =
        writeGate.requireAllowed(
            issueId = issueId,
            action = IssueWriteAction.GENERATE_AUDIO,
            operation = operation,
        ) is RepositoryResult.Success
}
