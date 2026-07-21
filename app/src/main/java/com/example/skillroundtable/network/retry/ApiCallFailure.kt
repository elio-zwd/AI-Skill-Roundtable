package com.example.skillroundtable.network.retry

/**
 * 封装 API 调用失败的底层异常分类。
 * cause 只用于程序内重试决策，不得直接拼接到日志、UI 或持久化遥测。
 */
sealed interface ApiCallFailure {
    data class Http(val code: Int, val retryAfterMs: Long? = null) : ApiCallFailure
    data class Network(val cause: Throwable) : ApiCallFailure
    data class Serialization(val cause: Throwable) : ApiCallFailure
    data class Unknown(val cause: Throwable) : ApiCallFailure
}

fun ApiCallFailure.safeCategory(): String = when (this) {
    is ApiCallFailure.Http -> "HTTP_$code"
    is ApiCallFailure.Network -> "NETWORK"
    is ApiCallFailure.Serialization -> "SERIALIZATION"
    is ApiCallFailure.Unknown -> "UNKNOWN"
}

/**
 * 结构化 API 执行异常，只在消息中暴露稳定分类；原始 cause 不进入异常描述。
 */
class ApiExecutionException(
    val failure: ApiCallFailure,
    val operationName: String,
    val keyId: String?,
    cause: Throwable
) : Exception(
    "API operation $operationName failed using key ${keyId ?: "none"}: ${failure.safeCategory()}",
    cause
)

enum class ApiRetryDecision {
    STOP_REQUEST,
    RETRY_SAME_KEY,
    TRY_NEXT_KEY,
    COOLDOWN_AND_TRY_NEXT_KEY
}
