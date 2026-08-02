package com.elio.jianyu.ui.screens.roundtable

import com.elio.jianyu.data.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundtableMessageGroupingTest {

    @Test
    fun groupMessages_userMessageSeparatesCharacterRounds() {
        val firstQuestion = message(id = 1, senderId = "user", text = "问题一")
        val firstAnswer = message(id = 2, senderId = "a", roundIndex = 1, text = "回答一")
        val secondQuestion = message(id = 3, senderId = "user", text = "问题二")
        val secondAnswer = message(id = 4, senderId = "b", roundIndex = 1, text = "回答二")

        val result = groupMessages(listOf(firstQuestion, firstAnswer, secondQuestion, secondAnswer))

        assertEquals(4, result.size)
        assertEquals(firstQuestion, (result[0] as ChatItem.UserMessage).message)
        assertEquals(listOf(firstAnswer), (result[1] as ChatItem.RoundtableRound).messages)
        assertEquals(secondQuestion, (result[2] as ChatItem.UserMessage).message)
        assertEquals(listOf(secondAnswer), (result[3] as ChatItem.RoundtableRound).messages)
    }

    @Test
    fun groupMessages_pendingPlaceholderIsFiltered() {
        val placeholder = message(
            id = 2,
            senderId = "a",
            text = THINKING_PLACEHOLDER_TEXT,
            isPending = true,
            roundIndex = 1,
        )

        val result = groupMessages(listOf(message(id = 1, senderId = "user"), placeholder))

        assertEquals(1, result.size)
        assertTrue(result.single() is ChatItem.UserMessage)
    }

    @Test
    fun groupMessages_streamingPendingBodyIsRetained() {
        val streaming = message(
            id = 2,
            senderId = "a",
            text = "正在流式输出的正文",
            isPending = true,
            roundIndex = 1,
        )

        val result = groupMessages(listOf(message(id = 1, senderId = "user"), streaming))

        val round = result[1] as ChatItem.RoundtableRound
        assertEquals(listOf(streaming), round.messages)
    }

    @Test
    fun groupMessages_multipleRoundsAreSortedAndStableWithinRound() {
        val roundTwo = message(id = 2, senderId = "b", roundIndex = 2, timestamp = 20)
        val roundOneA = message(id = 3, senderId = "a", roundIndex = 1, timestamp = 30)
        val roundOneB = message(id = 4, senderId = "c", roundIndex = 1, timestamp = 40)

        val result = groupMessages(
            listOf(message(id = 1, senderId = "user"), roundTwo, roundOneA, roundOneB),
        )

        val rounds = result.filterIsInstance<ChatItem.RoundtableRound>()
        assertEquals(listOf(1, 2), rounds.map { it.roundIndex })
        assertEquals(listOf(roundOneA, roundOneB), rounds[0].messages)
        assertEquals(listOf(roundTwo), rounds[1].messages)
    }

    @Test
    fun currentRoundMessageGroups_onlyUsesMessagesAfterLatestQuestion() {
        val oldAnswer = message(id = 2, senderId = "a", roundIndex = 1)
        val currentRoundOne = message(id = 4, senderId = "b", roundIndex = 1)
        val currentRoundTwo = message(id = 5, senderId = "c", roundIndex = 2)

        val result = currentRoundMessageGroups(
            listOf(
                message(id = 1, senderId = "user"),
                oldAnswer,
                message(id = 3, senderId = "user"),
                currentRoundTwo,
                currentRoundOne,
            ),
        )

        assertEquals(listOf(1, 2), result.map { it.roundIndex })
        assertEquals(listOf(currentRoundOne), result[0].messages)
        assertEquals(listOf(currentRoundTwo), result[1].messages)
    }

    private fun message(
        id: Long,
        senderId: String,
        text: String = "文本$id",
        isPending: Boolean = false,
        roundIndex: Int = 0,
        timestamp: Long = id,
    ): Message {
        return Message(
            id = id,
            chatId = 1,
            senderId = senderId,
            senderName = senderId,
            avatar = "A",
            text = text,
            timestamp = timestamp,
            isPending = isPending,
            roundIndex = roundIndex,
        )
    }
}
