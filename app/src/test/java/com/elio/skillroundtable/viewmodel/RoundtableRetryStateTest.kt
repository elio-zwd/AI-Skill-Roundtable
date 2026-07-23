package com.elio.skillroundtable.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundtableRetryStateTest {

    @Test
    fun testBuildRetryableCharacterIds_combinesFailedAndTimedOutInOrderWithoutDuplicates() {
        val failed = listOf("b", "a", "b")
        val timedOut = listOf("c", "a")

        val result = buildRetryableCharacterIds(failed, timedOut)

        assertEquals(listOf("b", "a", "c"), result)
    }

    @Test
    fun testBuildRetryableCharacterIds_emptyListsReturnEmpty() {
        val result = buildRetryableCharacterIds(emptyList(), emptyList())
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun testBuildRetryableCharacterIds_onlyFailed() {
        val failed = listOf("char_1", "char_2", "char_1")
        val result = buildRetryableCharacterIds(failed, emptyList())
        assertEquals(listOf("char_1", "char_2"), result)
    }

    @Test
    fun testBuildRetryableCharacterIds_onlyTimedOut() {
        val timedOut = listOf("char_x", "char_y", "char_x")
        val result = buildRetryableCharacterIds(emptyList(), timedOut)
        assertEquals(listOf("char_x", "char_y"), result)
    }

    @Test
    fun testRemainingRetryableCharacterIds_removesCompletedPreservesOthersInOrder() {
        val initial = listOf("a", "b", "c", "d")
        val completed = setOf("a", "c")

        val remaining = remainingRetryableCharacterIds(initial, completed)

        assertEquals(listOf("b", "d"), remaining)
    }

    @Test
    fun testRemainingRetryableCharacterIds_noneCompletedPreservesAll() {
        val initial = listOf("a", "b", "c")
        val completed = emptySet<String>()

        val remaining = remainingRetryableCharacterIds(initial, completed)

        assertEquals(listOf("a", "b", "c"), remaining)
    }

    @Test
    fun testRemainingRetryableCharacterIds_allCompletedReturnsEmpty() {
        val initial = listOf("a", "b")
        val completed = setOf("a", "b")

        val remaining = remainingRetryableCharacterIds(initial, completed)

        assertTrue(remaining.isEmpty())
    }

    @Test
    fun testRemainingRetryableCharacterIds_preservesSkippedAndDisabledCharacters() {
        val initial = listOf("a", "disabled_char", "b", "missing_char")
        val completed = setOf("a")

        val remaining = remainingRetryableCharacterIds(initial, completed)

        assertEquals(listOf("disabled_char", "b", "missing_char"), remaining)
    }

    @Test
    fun testRetryableRoundtableState_equalityAndSessionBinding() {
        val state1 = RetryableRoundtableState(sessionId = 1L, questionRunId = 100L, characterIds = listOf("a", "b"))
        val state2 = RetryableRoundtableState(sessionId = 1L, questionRunId = 100L, characterIds = listOf("a", "b"))
        val state3 = RetryableRoundtableState(sessionId = 2L, questionRunId = 100L, characterIds = listOf("a", "b"))

        assertEquals(state1, state2)
        assertNotEquals(state1, state3)
        assertEquals(1L, state1.sessionId)
        assertEquals(100L, state1.questionRunId)
        assertEquals(listOf("a", "b"), state1.characterIds)
    }

    @Test
    fun testRemainingRetryableCharacterIds_partialSuccessPreservesOriginalOrder() {
        val initial = listOf("char_a", "char_b", "char_c")
        val completed = setOf("char_a")

        val remaining = remainingRetryableCharacterIds(initial, completed)

        assertEquals(listOf("char_b", "char_c"), remaining)
    }

    @Test
    fun testRemainingRetryableCharacterIds_timedOutCharactersRemain() {
        val initial = listOf("char_a", "char_b", "char_c")
        val completed = setOf("char_a") // b failed, c timed out -> neither in completed

        val remaining = remainingRetryableCharacterIds(initial, completed)

        assertEquals(listOf("char_b", "char_c"), remaining)
    }

    @Test
    fun testRemainingRetryableCharacterIds_cancellationPreservesUnfinishedInOrder() {
        val initial = listOf("a", "b", "c")
        // a completed before cancellation, b was streaming when cancelled, c not started
        val answeredBeforeCancel = setOf("a")

        val remaining = remainingRetryableCharacterIds(initial, answeredBeforeCancel)

        assertEquals(listOf("b", "c"), remaining)
    }
}
