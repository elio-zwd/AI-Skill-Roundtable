package com.elio.skillroundtable.roundtable

import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.viewmodel.RoundActionState
import org.junit.Assert.assertEquals
import org.junit.Test

class RoundActionStateResolverTest {

    @Test
    fun testPartialRoundReturnsContinue() {
        val selected = listOf("char_a", "char_b", "char_c")
        // 最新已存在轮次是 1，其中仅 a 和 b 答过，c 没答
        val messages = listOf(
            Message(id = 2, chatId = 1, senderId = "char_a", senderName = "A", avatar = "A", text = "答", roundIndex = 1),
            Message(id = 3, chatId = 1, senderId = "char_b", senderName = "B", avatar = "B", text = "答", roundIndex = 1)
        )
        val state = RoundActionStateResolver.resolve(
            selectedParticipantIds = selected,
            messagesSinceRun = messages,
            isBudgetExceeded = false
        )
        assertEquals("有人未答，返回 CONTINUE_ROUND", RoundActionState.CONTINUE_ROUND, state)
    }

    @Test
    fun testCompletedRoundReturnsStartNext() {
        val selected = listOf("char_a", "char_b")
        // 最新已存在轮次是 1，且 a 和 b 都答了
        val messages = listOf(
            Message(id = 2, chatId = 1, senderId = "char_a", senderName = "A", avatar = "A", text = "答", roundIndex = 1),
            Message(id = 3, chatId = 1, senderId = "char_b", senderName = "B", avatar = "B", text = "答", roundIndex = 1)
        )
        val state = RoundActionStateResolver.resolve(
            selectedParticipantIds = selected,
            messagesSinceRun = messages,
            isBudgetExceeded = false
        )
        assertEquals("当轮全部作答完毕，返回 START_NEXT_ROUND", RoundActionState.START_NEXT_ROUND, state)
    }

    @Test
    fun testSixParticipantsCompletedStillReturnsStartNext() {
        val selected = listOf("c1", "c2", "c3", "c4", "c5", "c6")
        // 这 6 人都完成了第一轮
        val messages = selected.mapIndexed { index, id ->
            Message(id = index + 2L, chatId = 1, senderId = id, senderName = id, avatar = id, text = "答", roundIndex = 1)
        }
        val state = RoundActionStateResolver.resolve(
            selectedParticipantIds = selected,
            messagesSinceRun = messages,
            isBudgetExceeded = false
        )
        assertEquals("6人限制不限制其开启下一轮，完成仍应返回 START_NEXT_ROUND", RoundActionState.START_NEXT_ROUND, state)
    }

    @Test
    fun testExhaustedRequestBudgetReturnsBudgetExceeded() {
        val selected = listOf("char_a")
        val messages = emptyList<Message>()
        val state = RoundActionStateResolver.resolve(
            selectedParticipantIds = selected,
            messagesSinceRun = messages,
            isBudgetExceeded = true
        )
        assertEquals("API预算耗尽直接返回 BUDGET_EXCEEDED", RoundActionState.BUDGET_EXCEEDED, state)
    }
}
