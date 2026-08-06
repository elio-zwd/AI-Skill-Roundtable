package com.elio.jianyu.audio.assets

/** WorkManager 对一次正式生成执行的终态处理；失败必须等待用户显式重试。 */
enum class AudioWorkerCompletion {
    SUCCESS,
    FAILURE,
}

object AudioWorkerCompletionPolicy {
    fun resolve(result: AudioGenerationExecutionResult): AudioWorkerCompletion {
        return when (result) {
            is AudioGenerationExecutionResult.Available,
            is AudioGenerationExecutionResult.Suppressed,
            -> AudioWorkerCompletion.SUCCESS
            is AudioGenerationExecutionResult.Failure -> AudioWorkerCompletion.FAILURE
        }
    }
}
