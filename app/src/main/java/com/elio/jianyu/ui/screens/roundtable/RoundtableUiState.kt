package com.elio.jianyu.ui.screens.roundtable

import com.elio.jianyu.data.Character
import com.elio.jianyu.data.ChatSession
import com.elio.jianyu.data.Message
import com.elio.jianyu.viewmodel.RoundActionState
import com.elio.jianyu.viewmodel.SearchMode

internal const val THINKING_PLACEHOLDER_TEXT = "正在思考中..."

object RoundtableTestTags {
    const val NEW_SESSION_BUTTON = "new_session_button"
    const val RETRY_FAILED_CHARACTERS_BUTTON = "retry_failed_characters_button"
    const val DISMISS_FAILED_CHARACTERS_BUTTON = "dismiss_failed_characters_button"
    const val CHAT_INPUT = "chat_input"
    const val SEND_BUTTON = "send_button"
    const val STOP_BUTTON = "stop_button"

    val required: Set<String> = setOf(
        NEW_SESSION_BUTTON,
        RETRY_FAILED_CHARACTERS_BUTTON,
        DISMISS_FAILED_CHARACTERS_BUTTON,
        CHAT_INPUT,
        SEND_BUTTON,
        STOP_BUTTON,
    )
}

sealed interface ChatItem {
    data class UserMessage(val message: Message) : ChatItem

    data class RoundtableRound(
        val roundIndex: Int,
        val messages: List<Message>,
    ) : ChatItem
}

enum class CharacterTurnStatus {
    IDLE,
    WAITING,
    STREAMING,
    COMPLETED,
}

data class CharacterSeatUiState(
    val character: Character,
    val status: CharacterTurnStatus,
)

enum class RoundActionUiState {
    HIDDEN,
    CONTINUE_ROUND,
    START_NEXT_ROUND,
    BUDGET_EXCEEDED,
}

data class RetryBarUiState(
    val failedCount: Int,
)

data class RenameSessionUiState(
    val sessionId: Long,
    val title: String,
)

data class RoundtableUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val currentSession: ChatSession? = null,
    val messages: List<Message> = emptyList(),
    val characters: List<Character> = emptyList(),
    val isRoundtableRunning: Boolean = false,
    val typingCharacterIds: Set<String> = emptySet(),
    val hasApiKeys: Boolean = false,
    val isAutoNextEnabled: Boolean = true,
    val isSemanticRoutingEnabled: Boolean = false,
    val searchMode: SearchMode = SearchMode.SMART,
    val roundActionState: RoundActionState = RoundActionState.CONTINUE_ROUND,
    val retryableSessionId: Long? = null,
    val retryableCharacterIds: List<String> = emptyList(),
    val currentPlayingMessageId: Long? = null,
    val errorMessage: String? = null,
    val inputText: String = "",
    val isDrawerVisible: Boolean = false,
    val renameSession: RenameSessionUiState? = null,
) {
    val activeCharacters: List<Character>
        get() = characters.filter { it.isActive }

    val chatItems: List<ChatItem>
        get() = groupMessages(messages)

    val characterSeats: List<CharacterSeatUiState>
        get() = mapCharacterTurnStatuses(activeCharacters, messages, typingCharacterIds)

    val waitingCharacters: List<Character>
        get() = characterSeats
            .filter { it.status == CharacterTurnStatus.WAITING }
            .map { it.character }

    val action: RoundActionUiState
        get() = resolveRoundActionUiState(
            currentSession = currentSession,
            messages = messages,
            characters = characters,
            isRoundtableRunning = isRoundtableRunning,
            roundActionState = roundActionState,
        )

    val retryBar: RetryBarUiState?
        get() = resolveRetryBarUiState(
            currentSession = currentSession,
            retryableSessionId = retryableSessionId,
            retryableCharacterIds = retryableCharacterIds,
            isRoundtableRunning = isRoundtableRunning,
        )

    val canExport: Boolean
        get() = currentSession != null && messages.isNotEmpty()
}

sealed interface RoundtableEvent {
    data object ToggleDrawer : RoundtableEvent
    data object DismissDrawer : RoundtableEvent
    data object CreateSession : RoundtableEvent
    data object CreateFirstSession : RoundtableEvent
    data class SelectSession(val sessionId: Long) : RoundtableEvent
    data class DeleteSession(val sessionId: Long) : RoundtableEvent
    data class RequestRename(val sessionId: Long, val title: String) : RoundtableEvent
    data class RenameTitleChanged(val title: String) : RoundtableEvent
    data object ConfirmRename : RoundtableEvent
    data object DismissRename : RoundtableEvent
    data class AutoNextChanged(val enabled: Boolean) : RoundtableEvent
    data class SemanticRoutingChanged(val enabled: Boolean) : RoundtableEvent
    data class SearchModeChanged(val mode: SearchMode) : RoundtableEvent
    data class InputChanged(val text: String) : RoundtableEvent
    data object SubmitOrStop : RoundtableEvent
    data object ContinueRound : RoundtableEvent
    data object RetryFailedCharacters : RoundtableEvent
    data object DismissRetryableState : RoundtableEvent
    data object OpenApiKeyConfig : RoundtableEvent
    data object OpenTelemetry : RoundtableEvent
    data object CopyConversationMarkdown : RoundtableEvent
    data object SaveConversationMarkdown : RoundtableEvent
    data class CopyMessageText(val text: String) : RoundtableEvent
    data class PlayAudio(val message: Message, val voiceName: String) : RoundtableEvent
    data object ClearError : RoundtableEvent
}

fun groupMessages(messages: List<Message>): List<ChatItem> {
    val result = mutableListOf<ChatItem>()
    val currentCharacterMessages = mutableListOf<Message>()

    fun flushCharacterMessages() {
        currentCharacterMessages
            .groupBy { it.roundIndex }
            .entries
            .sortedBy { it.key }
            .forEach { (roundIndex, roundMessages) ->
                result += ChatItem.RoundtableRound(roundIndex, roundMessages)
            }
        currentCharacterMessages.clear()
    }

    messages.forEach { message ->
        if (message.senderId == "user") {
            if (currentCharacterMessages.isNotEmpty()) flushCharacterMessages()
            result += ChatItem.UserMessage(message)
        } else if (isVisibleCharacterMessage(message)) {
            currentCharacterMessages += message
        }
    }

    if (currentCharacterMessages.isNotEmpty()) flushCharacterMessages()
    return result
}

fun currentQuestionMessages(messages: List<Message>): List<Message> {
    val lastUserIndex = messages.indexOfLast { it.senderId == "user" }
    if (lastUserIndex == -1 || lastUserIndex == messages.lastIndex) return emptyList()
    return messages.subList(lastUserIndex + 1, messages.size)
}

fun currentRoundMessageGroups(messages: List<Message>): List<ChatItem.RoundtableRound> {
    return currentQuestionMessages(messages)
        .filter(::isVisibleCharacterMessage)
        .groupBy { it.roundIndex }
        .entries
        .sortedBy { it.key }
        .map { (roundIndex, roundMessages) ->
            ChatItem.RoundtableRound(roundIndex, roundMessages)
        }
}

fun mapCharacterTurnStatuses(
    characters: List<Character>,
    messages: List<Message>,
    typingCharacterIds: Set<String>,
): List<CharacterSeatUiState> {
    val currentMessages = currentQuestionMessages(messages)
    val streamingCharacterIds = currentMessages
        .asSequence()
        .filter { it.isPending && it.text != THINKING_PLACEHOLDER_TEXT }
        .map { it.senderId }
        .toSet()
    val completedCharacterIds = currentMessages
        .asSequence()
        .filterNot { it.isPending }
        .map { it.senderId }
        .toSet()

    return characters.map { character ->
        val status = when {
            character.id in streamingCharacterIds -> CharacterTurnStatus.STREAMING
            character.id in typingCharacterIds -> CharacterTurnStatus.WAITING
            character.id in completedCharacterIds -> CharacterTurnStatus.COMPLETED
            else -> CharacterTurnStatus.IDLE
        }
        CharacterSeatUiState(character, status)
    }
}

fun resolveRoundActionUiState(
    currentSession: ChatSession?,
    messages: List<Message>,
    characters: List<Character>,
    isRoundtableRunning: Boolean,
    roundActionState: RoundActionState,
): RoundActionUiState {
    val shouldShow = currentSession != null &&
        !isRoundtableRunning &&
        messages.isNotEmpty() &&
        characters.any { it.isActive }
    if (!shouldShow) return RoundActionUiState.HIDDEN

    return when (roundActionState) {
        RoundActionState.CONTINUE_ROUND -> RoundActionUiState.CONTINUE_ROUND
        RoundActionState.START_NEXT_ROUND -> RoundActionUiState.START_NEXT_ROUND
        RoundActionState.BUDGET_EXCEEDED -> RoundActionUiState.BUDGET_EXCEEDED
    }
}

fun resolveRetryBarUiState(
    currentSession: ChatSession?,
    retryableSessionId: Long?,
    retryableCharacterIds: List<String>,
    isRoundtableRunning: Boolean,
): RetryBarUiState? {
    val shouldShow = currentSession != null &&
        retryableSessionId == currentSession.id &&
        retryableCharacterIds.isNotEmpty() &&
        !isRoundtableRunning
    return if (shouldShow) RetryBarUiState(retryableCharacterIds.size) else null
}

private fun isVisibleCharacterMessage(message: Message): Boolean {
    return !message.isPending || message.text != THINKING_PLACEHOLDER_TEXT
}
