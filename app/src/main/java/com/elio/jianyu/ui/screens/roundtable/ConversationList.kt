package com.elio.jianyu.ui.screens.roundtable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.Character

@Composable
internal fun ConversationList(
    chatItems: List<ChatItem>,
    waitingCharacters: List<Character>,
    isRoundtableRunning: Boolean,
    currentPlayingMessageId: Long?,
    allCharacters: List<Character>,
    messageCount: Int,
    onEvent: (RoundtableEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messageCount, waitingCharacters.size) {
        val totalItems = chatItems.size + if (isRoundtableRunning) waitingCharacters.size else 0
        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        items(chatItems) { item ->
            when (item) {
                is ChatItem.UserMessage -> MessageBubble(
                    message = item.message,
                    currentPlayingId = currentPlayingMessageId,
                    allCharacters = allCharacters,
                    onEvent = onEvent,
                )

                is ChatItem.RoundtableRound -> RoundtableRoundBubble(
                    roundItem = item,
                    currentPlayingId = currentPlayingMessageId,
                    allCharacters = allCharacters,
                    onEvent = onEvent,
                )
            }
        }

        if (isRoundtableRunning) {
            waitingCharacters.forEach { character ->
                item(key = "typing_${character.id}") {
                    TypingIndicatorBubble(character = character)
                }
            }
        }
    }
}
