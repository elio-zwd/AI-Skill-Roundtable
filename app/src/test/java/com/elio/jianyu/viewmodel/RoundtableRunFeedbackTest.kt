package com.elio.jianyu.viewmodel

import com.elio.jianyu.roundtable.OrchestrationResult
import com.elio.jianyu.roundtable.RoundtableBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoundtableRunFeedbackTest {
    private val budget = RoundtableBudget()

    @Test
    fun noFailuresProducesNoFeedback() {
        val result = OrchestrationResult(listOf("a"), emptyList(), 1, false)
        assertNull(buildRoundtableFeedback(result, budget))
    }

    @Test
    fun allTimeoutsProduceActionableFeedback() {
        val result = OrchestrationResult(
            completedCharacters = emptyList(),
            failedCharacters = listOf("a", "b"),
            apiCallsUsed = 2,
            isLimitExceeded = false,
            timedOutCharacters = listOf("a", "b")
        )
        assertEquals(
            "本轮所有智囊均未能在规定时间内完成回答，请稍后重试。",
            buildRoundtableFeedback(result, budget)
        )
    }

    @Test
    fun partialFailurePreservesCompletedCount() {
        val result = OrchestrationResult(
            completedCharacters = listOf("a"),
            failedCharacters = listOf("b", "c"),
            apiCallsUsed = 3,
            isLimitExceeded = false,
            timedOutCharacters = listOf("b")
        )
        assertEquals(
            "已保留 1 位智囊的回复；另有 2 位未完成，其中 1 位超时。",
            buildRoundtableFeedback(result, budget)
        )
    }
}
