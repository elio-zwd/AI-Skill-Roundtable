package com.elio.jianyu.execution

typealias ExecutionParticipantStatus = com.elio.jianyu.data.ExecutionParticipantStatus

enum class ExecutionBudgetCallKind {
    REQUIRED,
    OPTIONAL,
}

enum class ExecutionErrorCode(val storageValue: String, val retryable: Boolean) {
    NO_API_KEY("no_api_key", true),
    OFFLINE("offline", true),
    RATE_LIMITED("rate_limited", true),
    AUTHENTICATION_FAILED("authentication_failed", true),
    TIMEOUT("timeout", true),
    EMPTY_RESPONSE("empty_response", true),
    PROVIDER_ERROR("provider_error", true),
    SAFETY_BLOCKED("safety_blocked", false),
    BUDGET_EXHAUSTED("budget_exhausted", true),
    STORAGE_FAILURE("storage_failure", true),
    INVALID_SKILL("invalid_skill", false),
    INVALID_STATE("invalid_state", false),
    USER_STOPPED("user_stopped", true),
    PROCESS_INTERRUPTED("process_interrupted", true),
}

data class ExecutionBudgetSnapshot(
    val rootRunId: String,
    val maxApiCalls: Int,
    val usedApiCalls: Int,
    val reservedRequiredCalls: Int,
    val closed: Boolean,
) {
    init {
        require(rootRunId.isNotBlank())
        require(maxApiCalls > 0)
        require(usedApiCalls in 0..maxApiCalls)
        require(reservedRequiredCalls >= 0)
    }

    val remainingApiCalls: Int
        get() = maxApiCalls - usedApiCalls
}

data class ExecutionContextContribution(
    val sourceId: String,
    val sourceType: String,
    val content: String,
    val contentHash: String,
    val userConfirmedAt: Long,
    val networkAllowed: Boolean,
    val sensitive: Boolean,
) {
    init {
        require(sourceId.isNotBlank())
        require(sourceType.isNotBlank())
        require(contentHash.isNotBlank())
        require(userConfirmedAt > 0L)
    }
}
