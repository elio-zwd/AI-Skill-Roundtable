package com.elio.skillroundtable.roundtable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundtableBudgetTest {

    @Test
    fun testDefaultBudgets() {
        val budget = RoundtableBudget()
        assertEquals("角色上限应为 6", 6, budget.maxCharactersPerQuestion)
        assertEquals("搜索限制应为 3", 3, budget.maxSearchQueriesPerCharacter)
        assertEquals("输出 Token 限制应为 4096", 4096, budget.maxOutputTokensPerAnswer)
        assertEquals("总 API 调用预算应为 30", 30, budget.maxApiCallsPerQuestion)
    }

    @Test
    fun testRequestBudgetTracker() {
        val budget = RoundtableBudget()
        val tracker = RequestBudgetTracker(budget.maxApiCallsPerQuestion)

        // 初始状态
        assertFalse("初始时预算不应超限", tracker.isExceeded())
        assertEquals("初始已用调用应为 0", 0, tracker.getUsed())

        // 消耗一次
        val success = tracker.tryConsume(1)
        assertTrue("预算充足时应消耗成功", success)
        assertFalse("消耗 1 次不应超限", tracker.isExceeded())
        assertEquals("已用调用应为 1", 1, tracker.getUsed())

        // 消耗至临界值
        for (i in 2..29) {
            val s = tracker.tryConsume(1)
            assertTrue("预算充足时应消耗成功", s)
        }
        assertFalse("消耗 29 次仍不应超限", tracker.isExceeded())

        // 消耗第 30 次，应当刚好达到上限，且 isExceeded 返回 true
        val s30 = tracker.tryConsume(1)
        assertTrue("第 30 次应消耗成功", s30)
        assertTrue("消耗 30 次应触发超限", tracker.isExceeded())
        
        // 再消耗一次，理应失败，因为超过了 30
        val s31 = tracker.tryConsume(1)
        assertFalse("超限后再次消耗应失败", s31)
        assertEquals("计数器应依然为 30", 30, tracker.getUsed())
    }
}
