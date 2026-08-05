package com.elio.jianyu.collaboration

import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig

/** 为首次执行、失败成员重试与整合重试预留共享调用预算。 */
object CollaborationExecutionBudgetPolicy {
    const val DIRECTED_MIN_API_CALLS: Int = 3
    const val CROSS_MIN_API_CALLS: Int = 8

    fun directed(): ExecutionRuntimeBudgetConfig = ExecutionRuntimeBudgetConfig(
        maxApiCalls = DIRECTED_MIN_API_CALLS,
    )

    fun cross(participantCount: Int): ExecutionRuntimeBudgetConfig {
        require(participantCount >= 2)
        val initialResponsesAndSynthesis = participantCount + 1
        val failedResponseRetriesAndSynthesisRetry = participantCount + 1
        return ExecutionRuntimeBudgetConfig(
  maxApiCalls = maxOf(
      CROSS_MIN_API_CALLS,
      initialResponsesAndSynthesis + failedResponseRetriesAndSynthesisRetry,
  ),
        )
    }
}
