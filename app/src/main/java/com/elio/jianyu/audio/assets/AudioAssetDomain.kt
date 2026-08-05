package com.elio.jianyu.audio.assets

/** 正式音频资产允许的最终文件格式。 */
enum class AudioTargetFormat(
    val storageValue: String,
    val extension: String,
    val mimeType: String,
) {
    WAV(
        storageValue = "wav",
        extension = "wav",
        mimeType = "audio/wav",
    ),
    AAC_ADTS(
        storageValue = "aac_adts",
        extension = "aac",
        mimeType = "audio/aac",
    ),
}

/**
 * 正式音频资产来源。
 *
 * 只保存稳定关系和内容摘要，不在 Generation Key 模型中携带正文。
 */
sealed interface AudioAssetSource {
    val issueId: String
    val stageId: String
    val contentHash: String

    internal val sourceType: String
    internal val stableSourceId: String

    data class CompletedMessage(
        override val issueId: String,
        override val stageId: String,
        override val contentHash: String,
        val messageId: Long,
    ) : AudioAssetSource {
        override val sourceType: String = "message"
        override val stableSourceId: String = messageId.toString()
    }

    data class ConfirmedArtifact(
        override val issueId: String,
        override val stageId: String,
        override val contentHash: String,
        val artifactId: String,
    ) : AudioAssetSource {
        override val sourceType: String = "confirmed_artifact"
        override val stableSourceId: String = artifactId
    }
}

/** 影响音频内容或文件表示的稳定生成配置。 */
data class AudioGenerationConfig(
    val voiceProfileId: String,
    val targetFormat: AudioTargetFormat,
    val parameterVersion: Int,
)
