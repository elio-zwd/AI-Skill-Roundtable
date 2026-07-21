package com.example.skillroundtable.network.retry

/**
 * 封装 API 调用失败的底层异常分类。
 */
sealed interface ApiCallFailure {
    data class Http(val code: Int, val retryAfterMs: Long? = null) : ApiCallFailure
    data class Network(val cause: Throwable) : ApiCallFailure
    data class Serialization(val cause: Throwable) : ApiCallFailure
    data class Unknown(val cause: Throwable) : ApiCallFailure
}

/**
 * 结构化 API 执行异常，暴露底层的错误类型、操作名称和所使用的 KeyId。
 */
class ApiExecutionException(
    val failure: ApiCallFailure,
    val operationName: String,
    val keyId: String?,
    cause: Throwable
) : Exception("API operation $operationName failed using key $keyId: $failure", cause)

/**
 * 重试决策结果。
 */
enum class ApiRetryDecision {
    STOP_REQUEST,              // 立即终止，不进行重试
    RETRY_SAME_KEY,            // 在相同的 Key 上进行有限重试
    TRY_NEXT_KEY,              // 放弃当前 Key，切换到尝试下一个 Key Lease
    COOLDOWN_AND_TRY_NEXT_KEY  // 将当前 Key 标记为临时冷却冷却，并切换到下一个 Key
}
