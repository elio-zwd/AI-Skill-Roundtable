package com.elio.jianyu.audio.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.elio.jianyu.JianyuAppRuntimeProvider
import com.elio.jianyu.audio.assets.AudioGenerationErrorCode
import com.elio.jianyu.audio.assets.AudioGenerationExecutionResult
import com.elio.jianyu.audio.assets.AudioGenerationWorkPolicy
import com.elio.jianyu.audio.assets.AudioWorkerCompletion
import com.elio.jianyu.audio.assets.AudioWorkerCompletionPolicy
import kotlinx.coroutines.CancellationException

/**
 * 正式音频生成的唯一 Worker。
 *
 * 输入只接受 audio_asset_id；来源正文、API Key、文件路径均在运行时重新读取或租用。
 */
class AudioAssetGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): ListenableWorker.Result {
        val audioAssetId = inputData.getString(AudioGenerationWorkPolicy.AUDIO_ASSET_ID_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return failure("missing_audio_asset_id")

        return try {
            JianyuAppRuntimeProvider.withRuntime(applicationContext) { runtime ->
                val result = runtime.audioRuntime.generationCoordinator.execute(audioAssetId)
                when (AudioWorkerCompletionPolicy.resolve(result)) {
                    AudioWorkerCompletion.SUCCESS -> ListenableWorker.Result.success()
                    AudioWorkerCompletion.FAILURE -> {
                        val code = (result as? AudioGenerationExecutionResult.Failure)
                            ?.errorCode ?: AudioGenerationErrorCode.UNKNOWN
                        failure(code.name.lowercase())
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            failure(AudioGenerationErrorCode.UNKNOWN.name.lowercase())
        }
    }

    private fun failure(errorCode: String): ListenableWorker.Result {
        return ListenableWorker.Result.failure(workDataOf(ERROR_CODE_KEY to errorCode))
    }

    private companion object {
        const val ERROR_CODE_KEY = "audio_error_code"
    }
}
