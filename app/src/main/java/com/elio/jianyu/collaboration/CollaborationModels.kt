package com.elio.jianyu.collaboration

import com.elio.jianyu.data.ContextUsageWriteSet
import com.elio.jianyu.data.CrossDiscussionSessionEntity
import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.execution.DEFAULT_EXECUTION_MODEL
import com.elio.jianyu.execution.ExecutionContextContribution
import com.elio.jianyu.execution.StableExecutionIds

private val STABLE_OPERATION_ID = Regex("^[A-Za-z0-9._-]{8,160}$")

data class CollaborationContextSelection(
    val selectedMessageIds: List<Long> = emptyList(),
    val contributions: List<ExecutionContextContribution> = emptyList(),
    val usage: ContextUsageWriteSet = ContextUsageWriteSet(),
) {
    init {
        require(selectedMessageIds.all { it > 0L })
        require(selectedMessageIds.distinct().size == selectedMessageIds.size)
    }
}

data class DirectedResponseRequest(
    val operationId: String,
    val issueId: String,
    val stageId: String,
    val selectedSkillId: String,
    val question: String,
    val roundIndex: Int,
    val userConfirmedAt: Long,
    val context: CollaborationContextSelection = CollaborationContextSelection(),
    val model: String = DEFAULT_EXECUTION_MODEL,
    val budget: ExecutionRuntimeBudgetConfig = CollaborationExecutionBudgetPolicy.directed(),
) {
    init {
        require(STABLE_OPERATION_ID.matches(operationId))
        require(issueId.isNotBlank())
        require(stageId.isNotBlank())
        require(selectedSkillId.isNotBlank())
        require(question.isNotBlank())
        require(roundIndex >= 0)
        require(userConfirmedAt > 0L)
        require(model.isNotBlank())
        require(budget.maxApiCalls >= 1)
    }
}

data class CrossDiscussionRequest(
    val operationId: String,
    val issueId: String,
    val stageId: String,
    val selectedSkillIds: List<String>,
    val focus: String,
    val roundIndex: Int,
    val userConfirmedAt: Long,
    val context: CollaborationContextSelection = CollaborationContextSelection(),
    val model: String = DEFAULT_EXECUTION_MODEL,
    val budget: ExecutionRuntimeBudgetConfig = CollaborationExecutionBudgetPolicy.cross(
        selectedSkillIds.size,
    ),
    val autoStartSynthesisOnFullSuccess: Boolean = true,
) {
    init {
        require(STABLE_OPERATION_ID.matches(operationId))
        require(issueId.isNotBlank())
        require(stageId.isNotBlank())
        require(selectedSkillIds.size >= 2)
        require(selectedSkillIds.all(String::isNotBlank))
        require(selectedSkillIds.distinct().size == selectedSkillIds.size)
        require(focus.isNotBlank())
        require(roundIndex >= 0)
        require(userConfirmedAt > 0L)
        require(model.isNotBlank())
        require(budget.maxApiCalls >= selectedSkillIds.size + 1)
    }
}

data class CrossDiscussionSynthesisRequest(
    val operationId: String,
    val issueId: String,
    val stageId: String,
    val sessionId: String,
    val focus: String,
    val roundIndex: Int,
    val userConfirmedAt: Long,
    val userAcceptedPartial: Boolean,
    val context: CollaborationContextSelection = CollaborationContextSelection(),
    val model: String = DEFAULT_EXECUTION_MODEL,
) {
    init {
        require(STABLE_OPERATION_ID.matches(operationId))
        require(issueId.isNotBlank())
        require(stageId.isNotBlank())
        require(sessionId.isNotBlank())
        require(focus.isNotBlank())
        require(roundIndex >= 0)
        require(userConfirmedAt > 0L)
        require(model.isNotBlank())
    }
}

data class CollaborationRetryRequest(
    val operationId: String,
    val previousRunId: String,
    val currentUserInput: String,
    val roundIndex: Int,
    val userConfirmedAt: Long,
    val model: String = DEFAULT_EXECUTION_MODEL,
) {
    init {
        require(STABLE_OPERATION_ID.matches(operationId))
        require(previousRunId.isNotBlank())
        require(currentUserInput.isNotBlank())
        require(roundIndex >= 0)
        require(userConfirmedAt > 0L)
        require(model.isNotBlank())
    }
}

data class CollaborationExecutionResult(
    val runtime: ExecutionRuntimeSnapshot,
    val discussion: CrossDiscussionSessionEntity? = null,
)

data class DirectedOperationIds(
    val runId: String,
    val userMessageId: Long,
    val idempotencyKey: String,
)

data class CrossResponseOperationIds(
    val discussionId: String,
    val runId: String,
    val userMessageId: Long,
    val runIdempotencyKey: String,
    val discussionIdempotencyKey: String,
)

data class CrossSynthesisOperationIds(
    val runId: String,
    val idempotencyKey: String,
)

data class CollaborationRetryOperationIds(
    val runId: String,
    val idempotencyKey: String,
)

object CollaborationOperationIds {
    fun directed(operationId: String): DirectedOperationIds {
        validate(operationId)
        return DirectedOperationIds(
            runId = "directed-$operationId",
            userMessageId = StableExecutionIds.userMessageId("directed-$operationId"),
            idempotencyKey = "directed-$operationId",
        )
    }

    fun crossResponse(operationId: String): CrossResponseOperationIds {
        validate(operationId)
        return CrossResponseOperationIds(
            discussionId = "discussion-$operationId",
            runId = "cross-response-$operationId",
            userMessageId = StableExecutionIds.userMessageId("cross-response-$operationId"),
            runIdempotencyKey = "cross-response-$operationId",
            discussionIdempotencyKey = "cross-discussion-$operationId",
        )
    }

    fun crossSynthesis(operationId: String): CrossSynthesisOperationIds {
        validate(operationId)
        return CrossSynthesisOperationIds(
            runId = "cross-synthesis-$operationId",
            idempotencyKey = "cross-synthesis-$operationId",
        )
    }

    fun retry(operationId: String): CollaborationRetryOperationIds {
        validate(operationId)
        return CollaborationRetryOperationIds(
            runId = "collaboration-retry-$operationId",
            idempotencyKey = "collaboration-retry-$operationId",
        )
    }

    private fun validate(operationId: String) {
        require(STABLE_OPERATION_ID.matches(operationId))
    }
}
