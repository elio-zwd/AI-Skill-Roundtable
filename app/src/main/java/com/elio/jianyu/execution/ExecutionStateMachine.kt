package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionRunStatus

/**
 * 纯函数形式的运行状态机。Repository 仍通过 compare-and-set 执行真实持久化转换。
 */
object ExecutionStateMachine {
    private val allowedTransitions = mapOf(
        ExecutionRunStatus.NOT_STARTED to setOf(
            ExecutionRunStatus.RUNNING,
            ExecutionRunStatus.STOPPED,
            ExecutionRunStatus.FAILED,
        ),
        ExecutionRunStatus.RUNNING to setOf(
            ExecutionRunStatus.PARTIAL_SUCCESS,
            ExecutionRunStatus.SUCCEEDED,
            ExecutionRunStatus.RETRYABLE,
            ExecutionRunStatus.FAILED,
            ExecutionRunStatus.STOPPED,
        ),
        ExecutionRunStatus.PARTIAL_SUCCESS to setOf(
            ExecutionRunStatus.SUCCEEDED,
            ExecutionRunStatus.RETRYABLE,
            ExecutionRunStatus.STOPPED,
        ),
        ExecutionRunStatus.RETRYABLE to emptySet(),
        ExecutionRunStatus.SUCCEEDED to emptySet(),
        ExecutionRunStatus.STOPPED to emptySet(),
        ExecutionRunStatus.FAILED to emptySet(),
        ExecutionRunStatus.COMPLETED to emptySet(),
    )

    fun canTransition(
        current: ExecutionRunStatus,
        target: ExecutionRunStatus,
    ): Boolean {
        if (current == target) return true
        if (target == ExecutionRunStatus.COMPLETED) return false
        return target in allowedTransitions.getValue(current)
    }

    fun aggregate(
        participantStatuses: List<ExecutionParticipantStatus>,
        retryableParticipantIds: Set<Int> = participantStatuses.indices
            .filterTo(mutableSetOf()) { index ->
                participantStatuses[index] in setOf(
                    ExecutionParticipantStatus.QUEUED,
                    ExecutionParticipantStatus.RUNNING,
                    ExecutionParticipantStatus.STREAMING,
                    ExecutionParticipantStatus.FAILED,
                    ExecutionParticipantStatus.TIMED_OUT,
                    ExecutionParticipantStatus.STOPPED,
                    ExecutionParticipantStatus.RETRYABLE,
                )
            },
    ): ExecutionRunStatus {
        require(participantStatuses.isNotEmpty()) { "执行运行必须至少包含一位参与者" }

        if (participantStatuses.all { it == ExecutionParticipantStatus.SUCCEEDED }) {
            return ExecutionRunStatus.SUCCEEDED
        }

        val hasSuccess = participantStatuses.any { it == ExecutionParticipantStatus.SUCCEEDED }
        val hasActive = participantStatuses.any {
            it == ExecutionParticipantStatus.QUEUED ||
                it == ExecutionParticipantStatus.RUNNING ||
                it == ExecutionParticipantStatus.STREAMING
        }
        if (hasSuccess && hasActive) return ExecutionRunStatus.PARTIAL_SUCCESS
        if (hasActive) return ExecutionRunStatus.RUNNING
        if (retryableParticipantIds.isNotEmpty()) return ExecutionRunStatus.RETRYABLE
        return ExecutionRunStatus.FAILED
    }
}
