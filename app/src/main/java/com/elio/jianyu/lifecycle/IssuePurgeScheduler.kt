package com.elio.jianyu.lifecycle

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IssuePurgeWorkPolicy {
    const val OPERATION_ID_KEY = "purge_operation_id"
    private const val UNIQUE_WORK_PREFIX = "jianyu-issue-purge-"

    fun uniqueWorkName(operationId: String): String =
        UNIQUE_WORK_PREFIX + operationId.requireStableWorkId()
}

interface IssuePurgeScheduler {
    suspend fun isActive(operationId: String): Boolean
    suspend fun schedule(operationId: String): Boolean
    suspend fun cancel(operationId: String): Boolean
}

class WorkManagerIssuePurgeScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) : IssuePurgeScheduler {
    override suspend fun isActive(operationId: String): Boolean = withContext(Dispatchers.IO) {
        val workName = runCatching { IssuePurgeWorkPolicy.uniqueWorkName(operationId) }
            .getOrElse { return@withContext false }
        runCatching {
            workManager.getWorkInfosForUniqueWork(workName).get().any { info ->
                info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.BLOCKED
            }
        }.getOrDefault(false)
    }

    override suspend fun schedule(operationId: String): Boolean = withContext(Dispatchers.IO) {
        val workName = runCatching { IssuePurgeWorkPolicy.uniqueWorkName(operationId) }
            .getOrElse { return@withContext false }
        val data = Data.Builder()
            .putString(IssuePurgeWorkPolicy.OPERATION_ID_KEY, operationId)
            .build()
        if (data.keyValueMap.keys != setOf(IssuePurgeWorkPolicy.OPERATION_ID_KEY)) {
            return@withContext false
        }
        val request = OneTimeWorkRequestBuilder<IssuePurgeWorker>()
            .setInputData(data)
            .addTag(ISSUE_PURGE_WORK_TAG)
            .build()
        runCatching {
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request).result.get()
            true
        }.getOrDefault(false)
    }

    override suspend fun cancel(operationId: String): Boolean = withContext(Dispatchers.IO) {
        val workName = runCatching { IssuePurgeWorkPolicy.uniqueWorkName(operationId) }
            .getOrElse { return@withContext false }
        runCatching {
            workManager.cancelUniqueWork(workName).result.get()
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val ISSUE_PURGE_WORK_TAG = "jianyu-issue-purge"
    }
}

private fun String.requireStableWorkId(): String {
    require(isNotBlank()) { "清理操作 ID 不能为空" }
    require(length <= 128) { "清理操作 ID 过长" }
    require(Regex("[A-Za-z0-9][A-Za-z0-9._-]*").matches(this)) {
        "清理操作 ID 包含不安全字符"
    }
    return this
}
