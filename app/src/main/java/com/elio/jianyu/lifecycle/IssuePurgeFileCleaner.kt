package com.elio.jianyu.lifecycle

import com.elio.jianyu.audio.assets.AudioAssetDeleteRequestResult
import com.elio.jianyu.audio.assets.AudioAssetLifecycleService
import com.elio.jianyu.audio.assets.AudioFileStore
import com.elio.jianyu.audio.assets.RequestAudioAssetDeleteCommand

enum class IssuePurgeFileFailureCode(val storageValue: String) {
    AUDIO_DELETE_REQUEST_FAILED("purge_audio_delete_request_failed"),
    FORMAL_FILE_DELETE_FAILED("purge_file_delete_failed"),
    TEMPORARY_FILE_DELETE_FAILED("purge_temporary_file_delete_failed"),
    STORAGE_UNAVAILABLE("purge_storage_unavailable"),
}

sealed interface IssuePurgeFileCleanupResult {
    data class Success(
        val assetCount: Int,
        val formalFileCount: Int,
        val temporaryFileCount: Int,
    ) : IssuePurgeFileCleanupResult

    data class Failure(
        val code: IssuePurgeFileFailureCode,
        val stableAudioAssetId: String?,
    ) : IssuePurgeFileCleanupResult
}

fun interface IssuePurgeFileCleanup {
    suspend fun clean(issueId: String, requestedAt: Long): IssuePurgeFileCleanupResult
}

/**
 * 只清理正式 AudioAsset 指向的受控文件及该资产的 `.part` 文件。
 *
 * 孤儿文件不在本流程中删除；绝对路径、路径穿越和其他 Issue 文件由 [AudioFileStore] 拒绝。
 */
class IssuePurgeFileCleaner(
    private val lifecycleService: AudioAssetLifecycleService,
    private val fileStore: AudioFileStore,
) : IssuePurgeFileCleanup {
    override suspend fun clean(
        issueId: String,
        requestedAt: Long,
    ): IssuePurgeFileCleanupResult {
        if (issueId.isBlank() || requestedAt <= 0L) {
            return IssuePurgeFileCleanupResult.Failure(
                IssuePurgeFileFailureCode.STORAGE_UNAVAILABLE,
                stableAudioAssetId = null,
            )
        }
        return try {
            // 用户已完成双确认后，先用正式服务对账缺失文件，但不删除 Orphan。
            lifecycleService.reconcileFilesForIssue(issueId)
            val assets = lifecycleService.listAudioAssetsForIssue(issueId).sortedBy { it.id }
            for (asset in assets) {
                when (
                    lifecycleService.requestDelete(
                        RequestAudioAssetDeleteCommand(
                            audioAssetId = asset.id,
                            requestedAt = requestedAt,
                            userConfirmed = true,
                        ),
                    )
                ) {
                    is AudioAssetDeleteRequestResult.Requested,
                    is AudioAssetDeleteRequestResult.AlreadyRequested,
                    -> Unit

                    AudioAssetDeleteRequestResult.ConfirmationRequired,
                    is AudioAssetDeleteRequestResult.Failure,
                    -> return IssuePurgeFileCleanupResult.Failure(
                        IssuePurgeFileFailureCode.AUDIO_DELETE_REQUEST_FAILED,
                        stableAudioAssetId = asset.id,
                    )
                }
            }

            var formalFileCount = 0
            var temporaryFileCount = 0
            val deleteReadyAssets = lifecycleService.listAudioAssetsForIssue(issueId).sortedBy { it.id }
            for (asset in deleteReadyAssets) {
                val relativePath = asset.storagePath
                if (!relativePath.isNullOrBlank()) {
                    if (!fileStore.removeCommitted(relativePath)) {
                        return IssuePurgeFileCleanupResult.Failure(
                            IssuePurgeFileFailureCode.FORMAL_FILE_DELETE_FAILED,
                            stableAudioAssetId = asset.id,
                        )
                    }
                    formalFileCount += 1
                }
                val temporary = fileStore.removeTemporaryFilesForAsset(
                    audioAssetId = asset.id,
                    format = asset.config.targetFormat,
                )
                temporaryFileCount += temporary.removedCount
                if (temporary.failedRelativePaths.isNotEmpty()) {
                    return IssuePurgeFileCleanupResult.Failure(
                        IssuePurgeFileFailureCode.TEMPORARY_FILE_DELETE_FAILED,
                        stableAudioAssetId = asset.id,
                    )
                }
            }
            IssuePurgeFileCleanupResult.Success(
                assetCount = deleteReadyAssets.size,
                formalFileCount = formalFileCount,
                temporaryFileCount = temporaryFileCount,
            )
        } catch (_: Exception) {
            IssuePurgeFileCleanupResult.Failure(
                IssuePurgeFileFailureCode.STORAGE_UNAVAILABLE,
                stableAudioAssetId = null,
            )
        }
    }
}
