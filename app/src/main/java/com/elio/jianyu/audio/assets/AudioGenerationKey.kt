package com.elio.jianyu.audio.assets

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 为相同来源内容与生成配置建立稳定、不可逆的幂等键。 */
object AudioGenerationKeyFactory {
    private const val KEY_PREFIX = "audio:v1:"

    fun create(
        source: AudioAssetSource,
        config: AudioGenerationConfig,
    ): String {
        require(source.issueId.isNotBlank()) { "议题 ID 不能为空" }
        require(source.stageId.isNotBlank()) { "阶段 ID 不能为空" }
        require(source.contentHash.isNotBlank()) { "来源内容 Hash 不能为空" }
        require(source.stableSourceId.isNotBlank()) { "来源稳定 ID 不能为空" }
        require(config.voiceProfileId.isNotBlank()) { "声音配置 ID 不能为空" }
        require(config.parameterVersion > 0) { "生成参数版本必须为正数" }

        val canonicalPayload = listOf(
            "source_type=${source.sourceType}",
            "source_id=${source.stableSourceId}",
            "issue_id=${source.issueId}",
            "stage_id=${source.stageId}",
            "content_hash=${source.contentHash}",
            "voice_profile=${config.voiceProfileId}",
            "target_format=${config.targetFormat.storageValue}",
            "parameter_version=${config.parameterVersion}",
        ).joinToString(separator = "\n")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalPayload.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return KEY_PREFIX + digest
    }
}
