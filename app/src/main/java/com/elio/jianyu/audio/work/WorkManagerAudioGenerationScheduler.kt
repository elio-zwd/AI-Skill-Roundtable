package com.elio.jianyu.audio.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.elio.jianyu.audio.assets.AudioExistingWorkPolicy
import com.elio.jianyu.audio.assets.AudioGenerationSchedulerPort
import com.elio.jianyu.audio.assets.AudioGenerationWorkPlan
import com.elio.jianyu.audio.assets.AudioGenerationWorkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** WorkManager 对正式音频唯一任务语义的生产适配。 */
class WorkManagerAudioGenerationScheduler(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
) : AudioGenerationSchedulerPort {
    override suspend fun isActive(uniqueWorkName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            workManager.getWorkInfosForUniqueWork(uniqueWorkName).get().any { info ->
                info.state == WorkInfo.State.ENQUEUED ||
                    info.state == WorkInfo.State.RUNNING ||
                    info.state == WorkInfo.State.BLOCKED
            }
        }.getOrDefault(false)
    }

    override suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean =
        withContext(Dispatchers.IO) {
            val audioAssetId = plan.inputData[AudioGenerationWorkPolicy.AUDIO_ASSET_ID_KEY]
                ?.takeIf { it.isNotBlank() }
                ?: return@withContext false
            if (plan.inputData.keys != setOf(AudioGenerationWorkPolicy.AUDIO_ASSET_ID_KEY)) {
                return@withContext false
            }
            val input = Data.Builder()
                .putString(AudioGenerationWorkPolicy.AUDIO_ASSET_ID_KEY, audioAssetId)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<AudioAssetGenerationWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .addTag(FORMAL_AUDIO_WORK_TAG)
                .build()
            val policy = when (plan.existingWorkPolicy) {
                AudioExistingWorkPolicy.KEEP -> ExistingWorkPolicy.KEEP
                AudioExistingWorkPolicy.REPLACE -> ExistingWorkPolicy.REPLACE
            }
            runCatching {
                workManager.enqueueUniqueWork(plan.uniqueWorkName, policy, request).result.get()
                true
            }.getOrDefault(false)
        }

    override suspend fun cancel(uniqueWorkName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            workManager.cancelUniqueWork(uniqueWorkName).result.get()
            true
        }.getOrDefault(false)
    }

    private companion object {
        const val FORMAL_AUDIO_WORK_TAG = "formal-audio-generation"
    }
}
