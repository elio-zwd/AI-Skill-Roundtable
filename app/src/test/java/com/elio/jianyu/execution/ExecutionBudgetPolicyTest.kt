package com.elio.jianyu.execution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionBudgetPolicyTest {
    private val initial = ExecutionBudgetSnapshot(
        rootRunId = "root-run",
        maxApiCalls = 5,
        usedApiCalls = 0,
        reservedRequiredCalls = 2,
        closed = false,
    )

    @Test
    fun requiredCallConsumesBeforeNetworkAndUpdatesReserve() {
        val consumed = ExecutionBudgetPolicy.consume(
            snapshot = initial,
            kind = ExecutionBudgetCallKind.REQUIRED,
            count = 1,
            reserveAfter = 1,
        )

        assertEquals(1, consumed?.usedApiCalls)
        assertEquals(1, consumed?.reservedRequiredCalls)
    }

    @Test
    fun optionalCallCannotUseCapacityReservedForRequiredCalls() {
        val snapshot = initial.copy(usedApiCalls = 3, reservedRequiredCalls = 2)

        assertFalse(
            ExecutionBudgetPolicy.canConsume(
                snapshot = snapshot,
                kind = ExecutionBudgetCallKind.OPTIONAL,
                count = 1,
                reserveAfter = 2,
            ),
        )
        assertNull(
            ExecutionBudgetPolicy.consume(
                snapshot = snapshot,
                kind = ExecutionBudgetCallKind.OPTIONAL,
                count = 1,
                reserveAfter = 2,
            ),
        )
    }

    @Test
    fun requiredCallMayUseItsOwnReservation() {
        val snapshot = initial.copy(usedApiCalls = 3, reservedRequiredCalls = 2)

        assertTrue(
            ExecutionBudgetPolicy.canConsume(
                snapshot = snapshot,
                kind = ExecutionBudgetCallKind.REQUIRED,
                count = 1,
                reserveAfter = 1,
            ),
        )
    }

    @Test
    fun closedBudgetNeverAllowsConsumption() {
        val snapshot = initial.copy(closed = true)

        assertFalse(
            ExecutionBudgetPolicy.canConsume(
                snapshot = snapshot,
                kind = ExecutionBudgetCallKind.REQUIRED,
                count = 1,
                reserveAfter = 1,
            ),
        )
    }

    @Test
    fun retryChainUsesExistingUsedCountInsteadOfResetting() {
        val restored = initial.copy(usedApiCalls = 4, reservedRequiredCalls = 1)

        val exhausted = ExecutionBudgetPolicy.consume(
            snapshot = restored,
            kind = ExecutionBudgetCallKind.REQUIRED,
            count = 2,
            reserveAfter = 0,
        )

        assertNull(exhausted)
    }
}
