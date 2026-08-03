package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import java.nio.ByteBuffer
import java.security.MessageDigest

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

data class ExecutionFailure(
    val code: ExecutionErrorCode,
    val safeMessage: String,
) {
    val retryable: Boolean
        get() = code.retryable
}

class NoExecutionApiKeyException : IllegalStateException("No imported API key is available")
class ExecutionBudgetExhaustedException : IllegalStateException("Execution budget is exhausted")
class ExecutionSafetyBlockedException : IllegalStateException("Provider blocked the request")
class ExecutionEmptyResponseException : IllegalStateException("Provider returned no model text")

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

data class ExecutionStartCommand(
    val runId: String,
    val issueId: String,
    val stageId: String,
    val triggerMessageId: Long?,
    val idempotencyKey: String,
    val selections: List<ExecutionSkillSelection>,
    val officialCombinationId: String? = null,
    val currentUserInput: String,
    val roundIndex: Int,
    val userConfirmedAt: Long,
    val model: String = DEFAULT_EXECUTION_MODEL,
    val budget: ExecutionRuntimeBudgetConfig = ExecutionRuntimeBudgetConfig(),
    val contributions: List<ExecutionContextContribution> = emptyList(),
) {
    init {
        require(runId.isNotBlank())
        require(issueId.isNotBlank())
        require(stageId.isNotBlank())
        require(idempotencyKey.isNotBlank())
        require(selections.isNotEmpty())
        require(currentUserInput.isNotBlank())
        require(roundIndex >= 0)
        require(userConfirmedAt > 0L)
        require(model.isNotBlank())
    }
}

data class ExecutionRetryCommand(
    val previousRunId: String,
    val newRunId: String,
    val idempotencyKey: String,
    val currentUserInput: String,
    val roundIndex: Int,
    val userConfirmedAt: Long,
    val model: String = DEFAULT_EXECUTION_MODEL,
    val contributions: List<ExecutionContextContribution> = emptyList(),
) {
    init {
        require(previousRunId.isNotBlank())
        require(newRunId.isNotBlank())
        require(previousRunId != newRunId)
        require(idempotencyKey.isNotBlank())
        require(currentUserInput.isNotBlank())
        require(roundIndex >= 0)
        require(userConfirmedAt > 0L)
        require(model.isNotBlank())
    }
}

data class ExecutionRunResult(
    val runtime: ExecutionRuntimeSnapshot,
    val participantFailures: Map<String, ExecutionFailure> = emptyMap(),
)

fun interface ExecutionClock {
    fun nowMillis(): Long
}

object SystemExecutionClock : ExecutionClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

object StableExecutionIds {
    fun messageId(runId: String, participantSnapshotId: String): Long =
        positiveLong("message:$runId:$participantSnapshotId")

    fun sessionId(issueId: String): Long = positiveLong("session:$issueId")

    private fun positiveLong(value: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        val raw = ByteBuffer.wrap(digest.copyOfRange(0, Long.SIZE_BYTES)).long
        return when (raw) {
            Long.MIN_VALUE -> Long.MAX_VALUE
            0L -> 1L
            else -> kotlin.math.abs(raw)
        }
    }
}

const val DEFAULT_EXECUTION_MODEL = "gemini-3.1-flash-lite"
