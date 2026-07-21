package com.example.skillroundtable.network.retry

import android.content.Context
import com.example.skillroundtable.network.ApiKeyPool
import com.example.skillroundtable.network.ApiKeyValidationState

object ApiRetryPolicy {
    /**
     * 根据当前失败类型和当前 Key 的已尝试次数（重试计数），决定下一步的操作。
     */
    fun getDecision(
        failure: ApiCallFailure,
        sameKeyAttemptCount: Int
    ): ApiRetryDecision {
        return when (failure) {
            is ApiCallFailure.Http -> {
                when (failure.code) {
                    400, 404 -> ApiRetryDecision.STOP_REQUEST
                    401, 403 -> ApiRetryDecision.TRY_NEXT_KEY
                    429 -> ApiRetryDecision.COOLDOWN_AND_TRY_NEXT_KEY
                    408 -> {
                        if (sameKeyAttemptCount < 1) {
                            ApiRetryDecision.RETRY_SAME_KEY
                        } else {
                            ApiRetryDecision.TRY_NEXT_KEY
                        }
                    }
                    in 500..599 -> {
                        if (sameKeyAttemptCount < 2) {
                            ApiRetryDecision.RETRY_SAME_KEY
                        } else {
                            ApiRetryDecision.TRY_NEXT_KEY
                        }
                    }
                    else -> ApiRetryDecision.STOP_REQUEST
                }
            }
            is ApiCallFailure.Network -> {
                if (sameKeyAttemptCount < 1) {
                    ApiRetryDecision.RETRY_SAME_KEY
                } else {
                    ApiRetryDecision.TRY_NEXT_KEY
                }
            }
            is ApiCallFailure.Serialization -> ApiRetryDecision.STOP_REQUEST
            is ApiCallFailure.Unknown -> ApiRetryDecision.STOP_REQUEST
        }
    }

    /**
     * 当遇到需要更新 Key 校验状态或冷却时间的错误时，同步到 ApiKeyPool 中。
     */
    fun handleKeyStatusUpdate(
        context: Context,
        keyId: String,
        failure: ApiCallFailure
    ) {
        when (failure) {
            is ApiCallFailure.Http -> {
                when (failure.code) {
                    401, 403 -> {
                        ApiKeyPool.markKeyInvalid(context, keyId, "鉴权失败或权限不足 (HTTP ${failure.code})")
                    }
                    429 -> {
                        val retryAfterMs = failure.retryAfterMs ?: getCooldownDurationMs(context, keyId)
                        ApiKeyPool.banKeyWithDuration(context, keyId, retryAfterMs)
                        incrementRateLimitCount(context, keyId)
                    }
                }
            }
            else -> { /* 网络 IO、5xx、400、序列化等非永久鉴权或非频控限制，不修改 Key 状态 */ }
        }
    }

    private fun getCooldownDurationMs(context: Context, keyId: String): Long {
        val prefs = context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("rate_limit_count_$keyId", 0)
        return when (count) {
            0 -> 60 * 1000L               // 1st: 60s
            1 -> 5 * 60 * 1000L            // 2nd: 5m
            2 -> 30 * 60 * 1000L           // 3rd: 30m
            else -> 24 * 60 * 60 * 1000L   // 4th+: 24h
        }
    }

    private fun incrementRateLimitCount(context: Context, keyId: String) {
        val prefs = context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("rate_limit_count_$keyId", 0)
        prefs.edit().putInt("rate_limit_count_$keyId", count + 1).apply()
    }

    fun resetRateLimitCount(context: Context, keyId: String) {
        val prefs = context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("rate_limit_count_$keyId").apply()
    }
}
