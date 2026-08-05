package com.elio.jianyu.audio.assets

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class AudioWorkRequestKind {
    INITIAL,
    EXPLICIT_RETRY,
}

enum class AudioExistingWorkPolicy {
    KEEP,
    REPLACE,
}

data class AudioGenerationWorkPlan(
    val uniqueWorkName: String,
    val existingWorkPolicy: AudioExistingWorkPolicy,
    val inputData: Map<String, String>,
)

/**
 * 冻结正式音频后台任务的唯一标识与输入白名单。
 *
 * 实际 WorkManager 调度在 PR09-11 合并后的阶段 B 接线。
 */
object AudioGenerationWorkPolicy {
    const val AUDIO_ASSET_ID_KEY: String = "audio_asset_id"

    private val generationKeyPattern = Regex("audio:v1:[0-9a-f]{64}")

    fun plan(
        audioAssetId: String,
        generationKey: String,
        requestKind: AudioWorkRequestKind,
    ): AudioGenerationWorkPlan {
        require(audioAssetId.isNotBlank()) { "音频资产 ID 不能为空" }
        require(generationKeyPattern.matches(generationKey)) { "Generation Key 格式无效" }

        val workDigest = MessageDigest.getInstance("SHA-256")
            .digest(generationKey.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

        return AudioGenerationWorkPlan(
            uniqueWorkName = "audio-generation:$workDigest",
            existingWorkPolicy = when (requestKind) {
                AudioWorkRequestKind.INITIAL -> AudioExistingWorkPolicy.KEEP
                AudioWorkRequestKind.EXPLICIT_RETRY -> AudioExistingWorkPolicy.REPLACE
            },
            inputData = mapOf(AUDIO_ASSET_ID_KEY to audioAssetId),
        )
    }
}
