package com.elio.jianyu.data

import com.elio.jianyu.audio.assets.AudioGenerationConfig
import com.elio.jianyu.audio.assets.AudioTargetFormat

/** Room v11 不新增配置列时，正式 V1 音频使用的可恢复稳定配置。 */
object FormalAudioV1Policy {
    const val VOICE_PROFILE_ID: String = "jianyu-default"
    const val PARAMETER_VERSION: Int = 1

    val defaultConfig: AudioGenerationConfig = AudioGenerationConfig(
        voiceProfileId = VOICE_PROFILE_ID,
        targetFormat = AudioTargetFormat.WAV,
        parameterVersion = PARAMETER_VERSION,
    )

    fun supports(config: AudioGenerationConfig): Boolean {
        return config.voiceProfileId == VOICE_PROFILE_ID &&
            config.parameterVersion == PARAMETER_VERSION &&
            config.targetFormat in AudioTargetFormat.entries
    }

    fun restore(formatValue: String): AudioGenerationConfig? {
        val format = AudioTargetFormat.entries.firstOrNull { it.storageValue == formatValue }
            ?: return null
        return defaultConfig.copy(targetFormat = format)
    }
}
