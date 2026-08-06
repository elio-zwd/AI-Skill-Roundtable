package com.elio.jianyu.lifecycle

import android.content.Context
import android.os.StatFs
import java.io.File

data class IssueTrashStorageStatus(
    val audioBytes: Long,
    val appOwnedBytes: Long,
    val availableBytes: Long,
    val lowStorage: Boolean,
    val inspectionAvailable: Boolean,
)

fun interface IssueTrashStorageProvider {
    fun inspect(): IssueTrashStorageStatus
}

/**
 * 仅统计 App 自有 filesDir 与正式音频根，不删除、不移动、不修改任何文件。
 *
 * 低空间只作为用户警告；不得触发自动 Purge、Orphan 删除或回收站过期。
 */
class IssueTrashStorageMonitor(
    context: Context,
    private val lowStorageThresholdBytes: Long = DEFAULT_LOW_STORAGE_THRESHOLD_BYTES,
) : IssueTrashStorageProvider {
    private val filesRoot = context.applicationContext.filesDir
    private val audioRoot = File(filesRoot, "jianyu-audio")

    override fun inspect(): IssueTrashStorageStatus {
        return try {
            val audioBytes = ownedFileBytes(audioRoot)
            val appOwnedBytes = ownedFileBytes(filesRoot)
            val availableBytes = StatFs(filesRoot.absolutePath).availableBytes.coerceAtLeast(0L)
            IssueTrashStorageStatus(
                audioBytes = audioBytes,
                appOwnedBytes = appOwnedBytes,
                availableBytes = availableBytes,
                lowStorage = availableBytes < lowStorageThresholdBytes,
                inspectionAvailable = true,
            )
        } catch (_: Exception) {
            IssueTrashStorageStatus(
                audioBytes = 0L,
                appOwnedBytes = 0L,
                availableBytes = 0L,
                lowStorage = false,
                inspectionAvailable = false,
            )
        }
    }

    private fun ownedFileBytes(root: File): Long {
        if (!root.exists()) return 0L
        val canonicalRoot = root.canonicalFile
        return canonicalRoot.walkTopDown()
            .filter(File::isFile)
            .filter { file ->
                runCatching {
                    val canonical = file.canonicalFile
                    canonical.path == canonicalRoot.path ||
                        canonical.path.startsWith(canonicalRoot.path + File.separator)
                }.getOrDefault(false)
            }
            .sumOf { file -> file.length().coerceAtLeast(0L) }
    }

    private companion object {
        const val DEFAULT_LOW_STORAGE_THRESHOLD_BYTES = 256L * 1024L * 1024L
    }
}
