package com.elio.skillroundtable.ui.screens.roundtable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RoundtableScreen(
    uiState: RoundtableUiState,
    onEvent: (RoundtableEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        RoundtableTopBar(
            uiState = uiState,
            onEvent = onEvent,
        )

        RoundtableSeatingBar(
            seats = uiState.characterSeats,
            searchMode = uiState.searchMode,
            onEvent = onEvent,
        )

        if (uiState.currentSession == null) {
            RoundtableEmptyState(
                onCreateSession = { onEvent(RoundtableEvent.CreateFirstSession) },
                modifier = Modifier.weight(1f),
            )
        } else {
            ConversationList(
                chatItems = uiState.chatItems,
                waitingCharacters = uiState.waitingCharacters,
                isRoundtableRunning = uiState.isRoundtableRunning,
                currentPlayingMessageId = uiState.currentPlayingMessageId,
                allCharacters = uiState.characters,
                messageCount = uiState.messages.size,
                onEvent = onEvent,
                modifier = Modifier.weight(1f),
            )
        }

        RoundActionBar(
            action = uiState.action,
            onEvent = onEvent,
        )

        uiState.retryBar?.let { retryBar ->
            FailedCharactersRetryBar(
                state = retryBar,
                onEvent = onEvent,
            )
        }

        if (uiState.currentSession != null) {
            RoundtableInputBar(
                inputText = uiState.inputText,
                isRoundtableRunning = uiState.isRoundtableRunning,
                onEvent = onEvent,
            )
        }
    }
}
