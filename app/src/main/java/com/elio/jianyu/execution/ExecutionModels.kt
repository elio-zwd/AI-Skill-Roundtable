package com.elio.jianyu.execution

import com.elio.jianyu.data.ContextUsageWriteSet
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionThinkingLevel
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import java.nio.ByteBuffer
import java.security.MessageDigest

typealias ExecutionParticipantStatus = com.elio.jianyu.data.ExecutionParticipantStatus

enum class ExecutionErrorCode(val storageValue: String, val retryable: Boolean) {
    NO_API_KEY("no_api_key", true),
    OFFLINE("offline", true),
    RATE_LIMITED("rate_limited", true),
    AUTHENTICATION_FAILED("authentication_failed", true),
    TIMEOUT("timeout", true),
    EMPTY_RESPONSE("empty_response", true),
    PROVIDER_ERROR("provider_error", true),
    SAFETY_BLOCKED("safety_blocked", false),
    STORAGE_FAILURE("storage_failure", true),
    INVALID_SKILL("invalid_skill", false),
    INVALID_STATE("invalid_state", false),
    USER_STOPPED("user_stopped", true),
    PROCESS_INTERRUPTED("process_interrupted", true),
    CONTEXT_NETWORK_NOT_ALLOWED("context_network_not_allowed", true),
    CONTEXT_USAGE_CONFLICT("context_usage_conflict", true),
    CONTEXT_TOO_LARGE("context_too_large", true),
}

data class ExecutionFailure(
    val code: ExecutionErrorCode,
    val safeMessage: String,
) {
    val retryable: Boolean
        get() = code.retryable
}

typealias ExecutionError = ExecutionFailure

class NoExecutionApiKeyException : IllegalStateException("No imported API key is available")
class ExecutionSafetyBlockedException : IllegalStateException("Provider blocked the request")
class ExecutionEmptyResponseException : IllegalStateException("Provider returned no model text")
class ExecutionModelMismatchException : IllegalStateException("Provider returned an unexpected model")

data class ResolvedExecutionThinking(
    val level: ExecutionThinkingLevel,
    val source: ExecutionThinkingSource,
)

object ExecutionThinkingPolicyResolver {
    fun resolve(
        issueDefault: IssueThinkingPolicy,
        roundOverride: IssueThinkingPolicy?,
        runKind: ExecutionRunKind,
    ): ResolvedExecutionThinking {
        val selected = roundOverride ?: issueDefault
        if (roundOverride != null && roundOverride != IssueThinkingPolicy.AUTO) {
            return ResolvedExecutionThinking(
                level = selected.toExecutionThinkingLevel(),
                source = ExecutionThinkingSource.ROUND_USER_OVERRIDE,
            )
        }
        if (roundOverride == null && selected != IssueThinkingPolicy.AUTO) {
            return ResolvedExecutionThinking(
                level = selected.toExecutionThinkingLevel(),
                source = ExecutionThinkingSource.ISSUE_USER_DEFAULT,
            )
        }
        return ResolvedExecutionThinking(
            level = when (runKind) {
                ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
                ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS -> ExecutionThinkingLevel.HIGH
                else -> ExecutionThinkingLevel.MEDIUM
            },
            source = ExecutionThinkingSource.AUTO_ROUTED,
        )
    }

    private fun IssueThinkingPolicy.toExecutionThinkingLevel(): ExecutionThinkingLevel = when (this) {
        IssueThinkingPolicy.MINIMAL -> ExecutionThinkingLevel.MINIMAL
        IssueThinkingPolicy.LOW -> ExecutionThinkingLevel.LOW
        IssueThinkingPolicy.MEDIUM -> ExecutionThinkingLevel.MEDIUM
        IssueThinkingPolicy.HIGH -> ExecutionThinkingLevel.HIGH
        IssueThinkingPolicy.AUTO -> error("AUTO 必须先经过自动路由")
    }
}

data class ExecutionBudgetSnapshot(
    val rootRunId: String,
    val usedApiCalls: Int,
    val closed: Boolean,
) {
    init {
        require(rootRunId.isNotBlank())
        require(usedApiCalls >= 0)
    }
}

data class ExecutionParticipantResult(
    val participantSnapshotId: String,
    val status: ExecutionParticipantStatus,
    val attemptCount: Int,
    val outputMessageId: Long?,
    val hasIncompleteOutput: Boolean,
    val error: ExecutionError?,
)

data class ExecutionRecoverySnapshot(
    val runId: String,
    val runStatus: ExecutionRunStatus,
    val participants: List<ExecutionParticipantResult>,
    val budget: ExecutionBudgetSnapshot,
    val requiresExplicitRetry: Boolean,
)

fun ExecutionRuntimeSnapshot.toExecutionRecoverySnapshot(): ExecutionRecoverySnapshot =
    ExecutionRecoverySnapshot(
        runId = run.id,
        runStatus = run.status,
        participants = participantStates.map { state ->
            ExecutionParticipantResult(
                participantSnapshotId = state.participantSnapshotId,
                status = state.status,
                attemptCount = state.attemptCount,
                outputMessageId = state.outputMessageId,
                hasIncompleteOutput = state.hasIncompleteOutput,
                error = state.lastErrorCode?.let { storedCode ->
                    val code = ExecutionErrorCode.entries.firstOrNull {
                        it.storageValue == storedCode
                    } ?: ExecutionErrorCode.PROVIDER_ERROR
                    ExecutionFailure(
                        code = code,
                        safeMessage = state.lastErrorMessage.orEmpty(),
                    )
                },
            )
        },
        budget = ExecutionBudgetSnapshot(
            rootRunId = budget.rootRunId,
            usedApiCalls = budget.usedApiCalls,
            closed = budget.closed,
        ),
        requiresExplicitRetry = run.status in setOf(
            ExecutionRunStatus.RETRYABLE,
            ExecutionRunStatus.STOPPED,
        ),
    )

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
    val thinkingOverride: IssueThinkingPolicy? = null,
    val searchMode: SearchMode = SearchMode.AUTO,
    val budget: ExecutionRuntimeBudgetConfig = ExecutionRuntimeBudgetConfig(),
    val contributions: List<ExecutionContextContribution> = emptyList(),
    val contextUsage: ContextUsageWriteSet = ContextUsageWriteSet(),
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
    val thinkingOverride: IssueThinkingPolicy? = null,
    val searchMode: SearchMode = SearchMode.AUTO,
    val contributions: List<ExecutionContextContribution> = emptyList(),
    val contextUsage: ContextUsageWriteSet = ContextUsageWriteSet(),
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

/**
 * 执行已经由 Repository 原子创建的 Runtime。
 * 该命令不再写用户 Message、Participant、Usage 或预算，只复用唯一执行状态机。
 */
data class ExecutionPreparedRunCommand(
    val runId: String,
    val issueId: String,
    val stageId: String,
    val currentUserInput: String,
    val roundIndex: Int,
    val userConfirmedAt: Long,
    val model: String = DEFAULT_EXECUTION_MODEL,
    val searchMode: SearchMode = SearchMode.AUTO,
    val contributions: List<ExecutionContextContribution> = emptyList(),
    val keepBudgetOpenOnSuccess: Boolean = false,
) {
    init {
        require(runId.isNotBlank())
        require(issueId.isNotBlank())
        require(stageId.isNotBlank())
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

    fun userMessageId(idempotencyKey: String): Long = positiveLong("user-message:$idempotencyKey")

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

const val DEFAULT_EXECUTION_MODEL = "gemini-3.6-flash"
