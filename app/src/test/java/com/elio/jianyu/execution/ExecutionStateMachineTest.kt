package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionStateMachineTest {
    @Test
    fun notStartedCanOnlyEnterRunningStoppedOrFailed() {
        assertTrue(
            ExecutionStateMachine.canTransition(
                ExecutionRunStatus.NOT_STARTED,
                ExecutionRunStatus.RUNNING,
            ),
        )
        assertTrue(
            ExecutionStateMachine.canTransition(
                ExecutionRunStatus.NOT_STARTED,
                ExecutionRunStatus.STOPPED,
            ),
        )
        assertTrue(
            ExecutionStateMachine.canTransition(
                ExecutionRunStatus.NOT_STARTED,
                ExecutionRunStatus.FAILED,
            ),
        )
        assertFalse(
            ExecutionStateMachine.canTransition(
                ExecutionRunStatus.NOT_STARTED,
                ExecutionRunStatus.SUCCEEDED,
            ),
        )
    }

    @Test
    fun terminalRunCannotReturnToRunning() {
        val terminalStates = listOf(
            ExecutionRunStatus.SUCCEEDED,
            ExecutionRunStatus.FAILED,
            ExecutionRunStatus.STOPPED,
            ExecutionRunStatus.COMPLETED,
        )

        terminalStates.forEach { state ->
            assertFalse(
                "$state 不得被迟到回调重新写回 RUNNING",
                ExecutionStateMachine.canTransition(state, ExecutionRunStatus.RUNNING),
            )
        }
    }

    @Test
    fun completedIsCompatibilityOnlyAndCannotBeProduced() {
        ExecutionRunStatus.entries
            .filterNot { it == ExecutionRunStatus.COMPLETED }
            .forEach { current ->
                assertFalse(
                    "新执行链不得从 $current 产生 COMPLETED",
                    ExecutionStateMachine.canTransition(current, ExecutionRunStatus.COMPLETED),
                )
            }
    }

    @Test
    fun participantAggregationReturnsPartialSuccessWhileWorkRemains() {
        assertEquals(
            ExecutionRunStatus.PARTIAL_SUCCESS,
            ExecutionStateMachine.aggregate(
                listOf(
                    ExecutionParticipantStatus.SUCCEEDED,
                    ExecutionParticipantStatus.STREAMING,
                    ExecutionParticipantStatus.QUEUED,
                ),
            ),
        )
    }

    @Test
    fun participantAggregationReturnsRetryableWhenActiveWorkStopsWithRecoverableFailure() {
        assertEquals(
            ExecutionRunStatus.RETRYABLE,
            ExecutionStateMachine.aggregate(
                listOf(
                    ExecutionParticipantStatus.SUCCEEDED,
                    ExecutionParticipantStatus.FAILED,
                    ExecutionParticipantStatus.TIMED_OUT,
                ),
            ),
        )
    }

    @Test
    fun participantAggregationReturnsFailedWhenNoSuccessAndNothingIsRetryable() {
        assertEquals(
            ExecutionRunStatus.FAILED,
            ExecutionStateMachine.aggregate(
                listOf(
                    ExecutionParticipantStatus.FAILED,
                    ExecutionParticipantStatus.FAILED,
                ),
                retryableParticipantIds = emptySet(),
            ),
        )
    }

    @Test
    fun participantAggregationReturnsSucceededOnlyWhenEveryParticipantSucceeded() {
        assertEquals(
            ExecutionRunStatus.SUCCEEDED,
            ExecutionStateMachine.aggregate(
                listOf(
                    ExecutionParticipantStatus.SUCCEEDED,
                    ExecutionParticipantStatus.SUCCEEDED,
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun participantAggregationRejectsEmptyParticipantList() {
        ExecutionStateMachine.aggregate(emptyList())
    }
}
