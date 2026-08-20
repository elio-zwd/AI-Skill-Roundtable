package com.elio.jianyu.ui.screens.roundtable

import com.elio.jianyu.data.Character
import com.elio.jianyu.data.ChatSession
import com.elio.jianyu.data.Message
import com.elio.jianyu.viewmodel.RoundActionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoundtableUiStateTest {

    @Test
    fun mapCharacterTurnStatuses_mapsWaitingStreamingCompletedAndIdle() {
        val characters = listOf(
            character("waiting"),
            character("streaming"),
            character("completed"),
            character("idle"),
        )
        val messages = listOf(
            message(1, "user", "问题"),
            message(2, "streaming", "流式正文", isPending = true, roundIndex = 1),
            message(3, "completed", "完整正文", roundIndex = 1),
        )

        val states = mapCharacterTurnStatuses(
            characters = characters,
            messages = messages,
            typingCharacterIds = setOf("waiting"),
        ).associate { it.character.id to it.status }

        assertEquals(CharacterTurnStatus.WAITING, states["waiting"])
        assertEquals(CharacterTurnStatus.STREAMING, states["streaming"])
        assertEquals(CharacterTurnStatus.COMPLETED, states["completed"])
        assertEquals(CharacterTurnStatus.IDLE, states["idle"])
    }

    @Test
    fun resolveRoundActionUiState_requiresSessionMessagesActiveCharacterAndIdleRun() {
        val session = ChatSession(id = 1, title = "会议")
        val messages = listOf(message(1, "user", "问题"))
        val activeCharacters = listOf(character("a"))

        assertEquals(
            RoundActionUiState.CONTINUE_ROUND,
            resolveRoundActionUiState(
                session,
                messages,
                activeCharacters,
                isRoundtableRunning = false,
                roundActionState = RoundActionState.CONTINUE_ROUND,
            ),
        )
        assertEquals(
            RoundActionUiState.START_NEXT_ROUND,
            resolveRoundActionUiState(
                session,
                messages,
                activeCharacters,
                isRoundtableRunning = false,
                roundActionState = RoundActionState.START_NEXT_ROUND,
            ),
        )
        assertEquals(
            RoundActionUiState.HIDDEN,
            resolveRoundActionUiState(
                session,
                messages,
                activeCharacters,
                isRoundtableRunning = true,
                roundActionState = RoundActionState.CONTINUE_ROUND,
            ),
        )
    }

    @Test
    fun resolveRoundActionUiState_hidesForEmptySessionOrCharacters() {
        val session = ChatSession(id = 1, title = "会议")
        val messages = listOf(message(1, "user", "问题"))

        assertEquals(
            RoundActionUiState.HIDDEN,
            resolveRoundActionUiState(
                currentSession = null,
                messages = messages,
                characters = listOf(character("a")),
                isRoundtableRunning = false,
                roundActionState = RoundActionState.CONTINUE_ROUND,
            ),
        )
        assertEquals(
            RoundActionUiState.HIDDEN,
            resolveRoundActionUiState(
                currentSession = session,
                messages = messages,
                characters = emptyList(),
                isRoundtableRunning = false,
                roundActionState = RoundActionState.CONTINUE_ROUND,
            ),
        )
        assertEquals(
            RoundActionUiState.HIDDEN,
            resolveRoundActionUiState(
                currentSession = session,
                messages = messages,
                characters = listOf(character("disabled", isActive = false)),
                isRoundtableRunning = false,
                roundActionState = RoundActionState.CONTINUE_ROUND,
            ),
        )
    }

    @Test
    fun resolveRetryBarUiState_onlyShowsForCurrentIdleSession() {
        val session = ChatSession(id = 7, title = "会议")

        assertEquals(
            RetryBarUiState(failedCount = 2),
            resolveRetryBarUiState(
                currentSession = session,
                retryableSessionId = 7,
                retryableCharacterIds = listOf("a", "b"),
                isRoundtableRunning = false,
            ),
        )
        assertNull(
            resolveRetryBarUiState(
                currentSession = session,
                retryableSessionId = 8,
                retryableCharacterIds = listOf("a"),
                isRoundtableRunning = false,
            ),
        )
        assertNull(
            resolveRetryBarUiState(
                currentSession = session,
                retryableSessionId = 7,
                retryableCharacterIds = emptyList(),
                isRoundtableRunning = false,
            ),
        )
        assertNull(
            resolveRetryBarUiState(
                currentSession = session,
                retryableSessionId = 7,
                retryableCharacterIds = listOf("a"),
                isRoundtableRunning = true,
            ),
        )
    }

    private fun character(id: String, isActive: Boolean = true): Character {
        return Character(
            id = id,
            name = id,
            avatar = "A",
            tagline = "tagline",
            systemPrompt = "prompt",
            order = 1,
            isActive = isActive,
        )
    }

    private fun message(
        id: Long,
        senderId: String,
        text: String,
        isPending: Boolean = false,
        roundIndex: Int = 0,
    ): Message {
        return Message(
            id = id,
            chatId = 1,
            senderId = senderId,
            senderName = senderId,
            avatar = "A",
            text = text,
            timestamp = id,
            isPending = isPending,
            roundIndex = roundIndex,
        )
    }
}
