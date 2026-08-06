package com.elio.jianyu.audio.assets

/** 正式音频生成 Gateway 对外暴露的稳定错误码。 */
enum class AudioGenerationGatewayErrorCode {
    AUTH_UNAVAILABLE,
    RATE_LIMITED,
    OFFLINE,
    TIMEOUT,
    EMPTY_RESPONSE,
    CANCELED,
    INVALID_RESPONSE,
    UNKNOWN,
}

/**
 * 仅在受控生成进程内存在的请求。
 *
 * API Key 由阶段 B 的生产 Gateway 通过现有 BYOK 体系获取，不进入请求、Work Data、数据库或日志。
 */
data class AudioGenerationRequest(
    val content: String,
    val config: AudioGenerationConfig,
)

/** Gateway 只能向 Coordinator 管理的受控临时文件写入。 */
interface AudioGenerationOutput {
    fun write(bytes: ByteArray)

    fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    )
}

sealed interface AudioGenerationGatewayResult {
    data object Success : AudioGenerationGatewayResult

    data class Failure(
        val errorCode: AudioGenerationGatewayErrorCode,
    ) : AudioGenerationGatewayResult
}

fun interface AudioGenerationGateway {
    suspend fun generate(
        request: AudioGenerationRequest,
        output: AudioGenerationOutput,
    ): AudioGenerationGatewayResult
}
