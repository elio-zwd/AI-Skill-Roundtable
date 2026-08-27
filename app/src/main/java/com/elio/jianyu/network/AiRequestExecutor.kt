package com.elio.jianyu.network

import com.elio.jianyu.network.keys.ApiKeyLease
import com.elio.jianyu.network.retry.ApiCallFailure
import com.elio.jianyu.network.retry.ApiExecutionException
import com.elio.jianyu.network.retry.ApiRetryDecision
import com.elio.jianyu.network.retry.ApiRetryPolicy
import com.elio.jianyu.network.retry.safeCategory
import com.elio.jianyu.roundtable.DefaultDelayProvider
import com.elio.jianyu.roundtable.DelayProvider
import com.elio.jianyu.telemetry.PrivacySafeLogger
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

internal interface ProviderKeyAttemptReporter {
    fun secretFor(keyId: String): String?
    fun recordSuccess(sessionId: Long, keyId: String)
    fun recordFailure(keyId: String, failure: ApiCallFailure)
}

/**
 * 所有文本提供商共用的请求执行器：负责 Key 尝试顺序、重试、冷却回写和安全错误包装。
 * transport 只提供单次协议请求，不得自行轮换或修改 Key 状态。
 */
internal class AiRequestExecutor(
    private val provider: AiProvider,
    private val keyRepository: ProviderKeyAttemptReporter,
) {
    suspend fun <T> execute(
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        operationName: String,
        delayProvider: DelayProvider = DefaultDelayProvider,
        onAttemptStarted: suspend () -> Unit = {},
        failureClassifier: (Exception) -> ApiCallFailure = { error -> classifyFailure(error) },
        block: suspend (String) -> T,
    ): T {
        val safeOperationName = sanitizeOperationName(operationName)
        if (attemptPlan.isEmpty()) {
            throw executionException(
                failure = ApiCallFailure.Unknown(Exception("No available ${provider.displayName} keys")),
                operationName = safeOperationName,
                keyId = null,
            )
        }

        var lastFailure: ApiCallFailure? = null
        for (lease in attemptPlan) {
            require(lease.provider == provider) { "密钥计划与提供商不一致" }
            var sameKeyAttemptCount = 0
            while (true) {
                try {
                    onAttemptStarted()
                    val secret = keyRepository.secretFor(lease.keyId)
                        ?: throw IllegalStateException("${provider.displayName} key is no longer available")
                    val result = block(secret)
                    keyRepository.recordSuccess(sessionId, lease.keyId)
                    return result
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val failure = failureClassifier(error)
                    lastFailure = failure
                    keyRepository.recordFailure(lease.keyId, failure)
                    PrivacySafeLogger.e(
                        "AiRequestExecutor",
                        "${provider.displayName} 请求失败 (operation=$safeOperationName, category=${failure.safeCategory()})",
                    )
                    when (ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount)) {
                        ApiRetryDecision.STOP_REQUEST -> throw executionException(
                            failure,
                            safeOperationName,
                            lease.keyId,
                        )
                        ApiRetryDecision.RETRY_SAME_KEY -> {
                            sameKeyAttemptCount++
                            delayProvider.delay(retryDelayMs(failure, sameKeyAttemptCount))
                        }
                        ApiRetryDecision.TRY_NEXT_KEY,
                        ApiRetryDecision.COOLDOWN_AND_TRY_NEXT_KEY -> break
                    }
                }
            }
        }

        throw executionException(
            failure = lastFailure ?: ApiCallFailure.Unknown(Exception("All ${provider.displayName} keys failed")),
            operationName = safeOperationName,
            keyId = null,
        )
    }

    private fun classifyFailure(error: Exception): ApiCallFailure = when (error) {
        is HttpException -> ApiCallFailure.Http(
            code = error.code(),
            retryAfterMs = ApiRetryPolicy.parseRetryAfterMs(error.response()?.headers()?.get("Retry-After")),
        )
        is IOException -> ApiCallFailure.Network(error)
        is SerializationException -> ApiCallFailure.Serialization(error)
        else -> ApiCallFailure.Unknown(error)
    }

    private fun retryDelayMs(failure: ApiCallFailure, retryCount: Int): Long =
        if (failure is ApiCallFailure.Http && failure.code in 500..599) retryCount * 1000L else 1000L

    private fun executionException(
        failure: ApiCallFailure,
        operationName: String,
        keyId: String?,
    ): ApiExecutionException = ApiExecutionException(
        failure = failure,
        operationName = operationName,
        keyId = keyId,
        cause = Exception("操作 [$operationName] 失败（${failure.safeCategory()}）。"),
    )

    private fun sanitizeOperationName(operationName: String): String = operationName
        .substringBefore('-')
        .filter { it.isLetterOrDigit() || it == '_' }
        .take(80)
        .ifBlank { "AiOperation" }
}
