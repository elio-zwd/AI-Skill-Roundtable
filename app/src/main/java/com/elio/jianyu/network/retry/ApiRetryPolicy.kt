package com.elio.jianyu.network.retry

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

    fun parseRetryAfterMs(headerValue: String?): Long? {
        if (headerValue == null) return null
        val seconds = headerValue.toLongOrNull()
        return if (seconds != null) seconds * 1000L else null
    }
}
