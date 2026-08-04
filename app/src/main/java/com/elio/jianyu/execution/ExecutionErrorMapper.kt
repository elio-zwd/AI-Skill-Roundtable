package com.elio.jianyu.execution

import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.network.retry.ApiCallFailure
import com.elio.jianyu.network.retry.ApiExecutionException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

object ExecutionErrorMapper {
    fun fromThrowable(error: Throwable): ExecutionFailure {
        if (error is CancellationException) throw error
        return when (error) {
            is NoExecutionApiKeyException -> failure(
                ExecutionErrorCode.NO_API_KEY,
                "尚未配置可用的 API Key，请先前往设置。",
            )
            is ExecutionBudgetExhaustedException -> failure(
                ExecutionErrorCode.BUDGET_EXHAUSTED,
                "本次执行的调用预算已用完。",
            )
            is ExecutionSafetyBlockedException -> failure(
                ExecutionErrorCode.SAFETY_BLOCKED,
                "服务商因安全规则拒绝了本次请求。",
            )
            is ExecutionEmptyResponseException -> failure(
                ExecutionErrorCode.EMPTY_RESPONSE,
                "服务商未返回可用文本。",
            )
            is SocketTimeoutException -> failure(
                ExecutionErrorCode.TIMEOUT,
                "请求超时，可稍后重试。",
            )
            is ApiExecutionException -> fromApiFailure(error.failure)
            is SerializationException -> failure(
                ExecutionErrorCode.EMPTY_RESPONSE,
                "服务商响应无法解析或没有可用文本。",
            )
            is IOException -> failure(
                ExecutionErrorCode.OFFLINE,
                "网络不可用，请检查连接后重试。",
            )
            else -> failure(
                ExecutionErrorCode.PROVIDER_ERROR,
                "服务暂时不可用，可稍后重试。",
            )
        }
    }

    fun fromRepositoryError(error: RepositoryError): ExecutionFailure = when (error) {
        is RepositoryError.StorageFailure -> failure(
            ExecutionErrorCode.STORAGE_FAILURE,
            "本地存储暂时不可用。",
        )
        is RepositoryError.NotFound,
        is RepositoryError.InvalidState,
        is RepositoryError.IdempotencyConflict,
        is RepositoryError.AlreadyExists,
        is RepositoryError.ConstraintViolation,
        is RepositoryError.CompatibilityFailure -> failure(
            ExecutionErrorCode.INVALID_STATE,
            "当前执行状态已经变化，请刷新后重试。",
        )
    }

    private fun fromApiFailure(failure: ApiCallFailure): ExecutionFailure = when (failure) {
        is ApiCallFailure.Http -> when (failure.code) {
            401, 403 -> failure(
                ExecutionErrorCode.AUTHENTICATION_FAILED,
                "API Key 无法通过认证，请检查配置。",
            )
            408 -> failure(ExecutionErrorCode.TIMEOUT, "请求超时，可稍后重试。")
            429 -> failure(
                ExecutionErrorCode.RATE_LIMITED,
                "请求过于频繁，请稍后重试。",
            )
            in 500..599 -> failure(
                ExecutionErrorCode.PROVIDER_ERROR,
                "服务商暂时不可用，可稍后重试。",
            )
            else -> failure(
                ExecutionErrorCode.PROVIDER_ERROR,
                "服务商拒绝了本次请求。",
            )
        }
        is ApiCallFailure.Network -> failure(
            ExecutionErrorCode.OFFLINE,
            "网络不可用，请检查连接后重试。",
        )
        is ApiCallFailure.Serialization -> failure(
            ExecutionErrorCode.EMPTY_RESPONSE,
            "服务商响应无法解析或没有可用文本。",
        )
        is ApiCallFailure.Unknown -> failure(
            ExecutionErrorCode.PROVIDER_ERROR,
            "服务暂时不可用，可稍后重试。",
        )
    }

    private fun failure(
        code: ExecutionErrorCode,
        message: String,
    ): ExecutionFailure = ExecutionFailure(code, message)
}
