package com.elio.jianyu.ui.screens.roundtable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.data.Character
import com.elio.jianyu.data.Message
import com.elio.jianyu.ui.CardBg
import com.elio.jianyu.ui.GoldAccent
import com.elio.jianyu.ui.PrimaryAccent
import com.elio.jianyu.ui.TextPrimary
import com.elio.jianyu.ui.TextSecondary
import com.elio.jianyu.ui.components.CharacterAvatar
import com.elio.jianyu.ui.components.bounceClick
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch

@Composable
internal fun RoundtableRoundBubble(
    roundItem: ChatItem.RoundtableRound,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onEvent: (RoundtableEvent) -> Unit,
) {
    val messages = roundItem.messages
    if (messages.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { messages.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) pagerState.animateScrollToPage(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(CardBg.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .border(1.dp, PrimaryAccent.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "第 ${roundItem.roundIndex} 轮脑暴交锋",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldAccent,
            )
            Text(
                text = "${pagerState.currentPage + 1}/${messages.size}",
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                MessageBubble(
                    message = messages[page],
                    currentPlayingId = currentPlayingId,
                    allCharacters = allCharacters,
                    onEvent = onEvent,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            messages.forEachIndexed { index, message ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(if (isSelected) 36.dp else 28.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else Color.Transparent,
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) GoldAccent else TextSecondary.copy(alpha = 0.5f),
                            shape = CircleShape,
                        )
                        .clickable {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CharacterAvatar(
                        avatar = message.avatar,
                        name = message.senderName,
                        size = if (isSelected) 36.dp else 28.dp,
                        textSize = if (isSelected) 18.sp else 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun MessageBubble(
    message: Message,
    currentPlayingId: Long?,
    allCharacters: List<Character>,
    onEvent: (RoundtableEvent) -> Unit,
) {
    val isUser = message.senderId == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!isUser) {
            CharacterAvatar(
                avatar = message.avatar,
                name = message.senderName,
                size = 42.dp,
                textSize = 20.sp,
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            if (!isUser) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent,
                    modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
                )
            }

            val bubbleShape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp,
            )
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(
                        when {
                            isUser -> PrimaryAccent
                            message.senderId == "zhang_xuefeng" -> GoldAccent.copy(alpha = 0.15f)
                            else -> CardBg
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isUser -> PrimaryAccent
                            message.senderId == "zhang_xuefeng" -> GoldAccent.copy(alpha = 0.5f)
                            else -> PrimaryAccent.copy(alpha = 0.15f)
                        },
                        shape = bubbleShape,
                    )
                    .bounceClick()
                    .clickable { onEvent(RoundtableEvent.CopyMessageText(message.text)) }
                    .padding(14.dp),
            ) {
                if (isUser) {
                    Text(
                        text = message.text,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                } else {
                    MarkdownText(
                        markdown = message.text,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (!isUser && !message.isPending) {
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .bounceClick()
                        .clickable {
                            val voiceName = allCharacters
                                .find { it.id == message.senderId }
                                ?.voiceConfig
                                ?: "Aoede"
                            onEvent(RoundtableEvent.PlayAudio(message, voiceName))
                        },
                ) {
                    val isPlaying = currentPlayingId == message.id
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放TTS",
                        tint = if (isPlaying) GoldAccent else TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "播音中..." else "合成语音",
                        fontSize = 11.sp,
                        color = if (isPlaying) GoldAccent else TextSecondary,
                    )
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            CharacterAvatar(
                avatar = message.avatar,
                name = message.senderName,
                size = 42.dp,
                textSize = 20.sp,
            )
        }
    }
}

@Composable
internal fun TypingIndicatorBubble(character: Character) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        CharacterAvatar(
            avatar = character.avatar,
            name = character.name,
            size = 42.dp,
            textSize = 20.sp,
        )
        Spacer(Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = character.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryAccent,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
            )

            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 16.dp,
                        ),
                    )
                    .background(CardBg)
                    .border(
                        1.dp,
                        PrimaryAccent.copy(alpha = 0.15f),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 16.dp,
                        ),
                    )
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PrimaryAccent,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "正在思考如何交锋论证...",
                        color = TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
