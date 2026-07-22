package com.elio.skillroundtable.viewmodel

import org.junit.Assert.assertEquals
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
}
