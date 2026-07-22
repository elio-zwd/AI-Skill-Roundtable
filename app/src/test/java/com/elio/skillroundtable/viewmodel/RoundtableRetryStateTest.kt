package com.elio.skillroundtable.viewmodel

import org.junit.Assert.assertEquals
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
}
