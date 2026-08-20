package com.elio.jianyu.roundtable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundtableBudgetTest {

    @Test
    fun testDefaultBudgets() {
        val budget = RoundtableBudget()
        assertEquals("角色上限应为 15", 15, budget.maxCharactersPerQuestion)
        assertEquals("搜索限制应为 3", 3, budget.maxSearchQueriesPerCharacter)
        assertEquals("输出 Token 限制应为 4096", 4096, budget.maxOutputTokensPerAnswer)
    }

    @Test
    fun defaultBudgetSelectsAndLocksFifteenParticipants() {
        val participantIds = (1..16).map { "skill_$it" }
        val manager = RoundtableBudgetManager()

        assertEquals(
            participantIds.take(15),
            manager.getOrSetSelectedParticipants(questionRunId = 1L, activeCharIds = participantIds)
        )
        assertEquals(
            participantIds.take(15),
            manager.getOrSetSelectedParticipants(
                questionRunId = 1L,
                activeCharIds = participantIds.reversed()
            )
        )
    }

    @Test
    fun testRequestBudgetTracker() {
        val tracker = RequestBudgetTracker()
        assertEquals("初始已用调用应为 0", 0, tracker.getUsed())

        repeat(100) {
            assertTrue("累计调用次数不应阻断请求", tracker.tryConsume())
        }
        assertEquals("调用次数应完整记录", 100, tracker.getUsed())
    }
}
