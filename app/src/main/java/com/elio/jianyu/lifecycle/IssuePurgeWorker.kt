package com.elio.jianyu.lifecycle

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elio.jianyu.JianyuAppRuntimeProvider
import kotlinx.coroutines.CancellationException

/** 输入只接受持久化的 purge operation ID；用户意图、路径和正文均从 Room/服务重新读取。 */
class IssuePurgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val operationId = inputData.getString(IssuePurgeWorkPolicy.OPERATION_ID_KEY)
            ?.takeIf(String::isNotBlank)
            ?: return failure("missing_purge_operation_id")
        if (inputData.keyValueMap.keys != setOf(IssuePurgeWorkPolicy.OPERATION_ID_KEY)) {
            return failure("invalid_purge_worker_data")
        }
        return try {
            when (
                val result = JianyuAppRuntimeProvider.get(applicationContext)
                    .lifecycleRuntime
                    .purgeCoordinator
                    .execute(operationId)
            ) {
                IssuePurgeExecutionResult.Completed -> ListenableWorker.Result.success()
                is IssuePurgeExecutionResult.RetryableFailure -> failure(result.code)
                is IssuePurgeExecutionResult.Rejected -> failure(result.code)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            failure("purge_worker_unknown")
        }
    }

    private fun failure(code: String): ListenableWorker.Result =
        ListenableWorker.Result.failure(workDataOf(ERROR_CODE_KEY to code))

    private companion object {
        const val ERROR_CODE_KEY = "purge_error_code"
    }
}
