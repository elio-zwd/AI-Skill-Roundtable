package com.elio.jianyu.ui.screens.dialog.components

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.R
import com.elio.jianyu.ui.components.UserAvatar
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogAnswerStatus
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogMessageItem
import com.elio.jianyu.ui.screens.dialog.DialogTimelineItem
import com.elio.jianyu.ui.screens.dialog.DialogTokens
import dev.jeziellago.compose.markdowntext.MarkdownText

/**
 * 见域「对话」页面消息流组件
 * 1:1 像素级还原设计图
 */
@Composable
fun UserMessageBubble(
    message: DialogMessageItem.UserMessage,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        // 用户消息气泡主体
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column {
                Text(
                    text = message.text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                // 时间与蓝色双勾
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = message.timestamp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                    )
                    if (message.isDelivered) {
                        Icon(
                            imageVector = DialogIcons.DoneAll,
                            contentDescription = "已发送",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 用户头像由公共组件统一提供，避免对话页与「我的」页出现不同来源。
        UserAvatar(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}

@Composable
fun SkillMessageCard(
    message: DialogMessageItem.SkillMessage,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    onToggleExpanded: (() -> Unit)? = null,
    previewViewportHeight: Dp? = null,
) {
    val isPlanner = message.role.id == "planning_coach" || message.role.id == "planner"
    val isThinker = message.role.id == "systems_thinker" || message.role.id == "thinker"
    val avatarRes = message.role.avatarResId ?: if (isPlanner) R.drawable.avatar_planner else if (isThinker) R.drawable.avatar_thinker else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 2.dp),
    ) {
        // 姓名和头像位于正文上方，正文可使用整个回答区域宽度。
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. 角色姓名与时间
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                SkillRoleAvatar(
                    role = message.role.copy(avatarResId = avatarRes),
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.role.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.timestamp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.5.sp,
                )
                val statusLabel = when (message.answerStatus) {
                    DialogAnswerStatus.WAITING -> "等待中"
                    DialogAnswerStatus.GENERATING -> "生成中"
                    DialogAnswerStatus.STOPPED -> "已停止"
                    DialogAnswerStatus.FAILED -> "生成失败"
                    DialogAnswerStatus.COMPLETED -> null
                }
                if (statusLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusLabel,
                        color = if (message.answerStatus == DialogAnswerStatus.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 11.5.sp,
                    )
                }
            }

            // 2. 消息主体卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(18.dp),
                    ),
            ) {
                // 系统思考者右上角淡绿星光
                if (isThinker) {
                    Icon(
                        imageVector = DialogIcons.Sparkle,
                        contentDescription = null,
                        tint = Color(0xFF74C29E).copy(alpha = 0.5f),
                        modifier = Modifier
                            .padding(12.dp)
                            .size(16.dp)
                            .align(Alignment.TopEnd),
                    )
                }

                Column {
                    // 正文 Markdown
                    val viewportHeight = previewViewportHeight ?: LocalConfiguration.current.screenHeightDp.dp
                    val previewHeight = viewportHeight * 0.45f
                    val density = LocalDensity.current
                    val previewHeightPx = with(density) { previewHeight.roundToPx() }
                    val verticalPaddingPx = with(density) { 20.dp.roundToPx() }
                    val contentLimitPx = (previewHeightPx - verticalPaddingPx).coerceAtLeast(1)
                    var contentHeightPx by remember(message.id) { mutableIntStateOf(0) }
                    var previewCutHeightPx by remember(message.id, contentLimitPx) { mutableIntStateOf(contentLimitPx) }
                    var markdownView by remember(message.id) { mutableStateOf<TextView?>(null) }
                    val isLong = contentHeightPx > contentLimitPx
                    if (isLong && expanded && onToggleExpanded != null) {
                        Text(
                            text = "收起",
                            modifier = Modifier.clickable(onClick = onToggleExpanded).padding(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isLong && !expanded) {
                                Modifier.heightIn(
                                    max = with(density) {
                                        (previewCutHeightPx.coerceAtMost(contentLimitPx) + verticalPaddingPx).toDp()
                                    },
                                ).clipToBounds()
                            } else Modifier)
                            .testTag("answer_body_${message.id}")
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        MarkdownText(
                            markdown = message.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.5.sp,
                            isTextSelectable = true,
                            // 测量完整正文，再由外层裁剪预览；Markdown 的标题、列表和代码高度不能用行数估算。
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(unbounded = true)
                                .onSizeChanged { contentHeightPx = it.height },
                            afterSetMarkdown = { markdownView = it },
                            onTextLayout = {
                                markdownView?.let { view ->
                                    val cutHeight = markdownPreviewCutHeight(view, contentLimitPx)
                                    if (previewCutHeightPx != cutHeight) previewCutHeightPx = cutHeight
                                }
                            },
                        )
                        if (isLong && !expanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))),
                            )
                        }
                    }
                    if (isLong && onToggleExpanded != null) {
                        Text(
                            text = if (expanded) "收起" else "展开全文",
                            modifier = Modifier
                                .clickable(onClick = onToggleExpanded)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("answer_expand_${message.id}"),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (message.questionId != null &&
                        message.answerStatus in setOf(DialogAnswerStatus.FAILED, DialogAnswerStatus.STOPPED)
                    ) {
                        TextButton(
                            onClick = { onEvent(DialogEvent.RetryAnswer(message.questionId, message.role.id)) },
                            modifier = Modifier.testTag("answer_retry_${message.id}"),
                        ) {
                            Text("重试此角色")
                        }
                    }

                    // 水平分割线
                    if (!message.isStreaming) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

                        // 3. 消息操作栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 复制
                            MessageActionButton(
                                icon = DialogIcons.ThumbUp,
                                text = "复制",
                                onClick = {
                                    onEvent(DialogEvent.CopyMessage(message.id, message.text))
                                },
                                modifier = Modifier.weight(1f),
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.height(18.dp),
                            )

                            // 保存为成果
                            MessageActionButton(
                                icon = DialogIcons.Bookmark,
                                text = "保存为成果",
                                onClick = {
                                    onEvent(DialogEvent.SaveMessageAsArtifact(message.id))
                                },
                                modifier = Modifier.weight(1.25f),
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.height(18.dp),
                            )

                            // 更多
                            MessageActionButton(
                                icon = DialogIcons.MoreHoriz,
                                text = "更多",
                                onClick = {
                                    onEvent(DialogEvent.ClickMessageMore(message.id))
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 优先在完整段落后截断；段落过长时退到完整显示行。 */
private fun markdownPreviewCutHeight(view: TextView, limitPx: Int): Int {
    val layout = view.layout ?: return limitPx
    if (layout.lineCount == 0 || layout.height <= limitPx) return limitPx
    var line = layout.getLineForVertical(limitPx).coerceAtMost(layout.lineCount - 1)
    while (line >= 0 && layout.getLineBottom(line) > limitPx) line--
    if (line < 0) return limitPx

    val completeLineHeight = layout.getLineBottom(line)
    val renderedText = view.text.toString()
    val lastVisibleCharacter = layout.getLineEnd(line).coerceAtMost(renderedText.length)
    val paragraphEnd = renderedText.lastIndexOf("\n\n", startIndex = lastVisibleCharacter)
    if (paragraphEnd > 0) {
        val paragraphHeight = layout.getLineBottom(layout.getLineForOffset(paragraphEnd))
        if (paragraphHeight in (limitPx * 3 / 5)..limitPx) return paragraphHeight
    }
    return completeLineHeight
}

@Composable
fun AnswerPager(
    group: DialogTimelineItem.Answers,
    selectedRoleId: String?,
    expandedAnswerIds: Set<String>,
    readAnswerIds: Set<String>,
    onEvent: (DialogEvent) -> Unit,
    previewViewportHeight: Dp? = null,
) {
    val selectedIndex = group.replies.indexOfFirst { it.role.id == selectedRoleId }.coerceAtLeast(0)
    val selected = group.replies[selectedIndex]
    var dragDistance = remember(group.id, selected.id) { 0f }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(group.id, selected.id, group.replies.size) {
                val swipeThreshold = 72.dp.toPx()
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        val next = when {
                            dragDistance < -swipeThreshold -> (selectedIndex + 1).coerceAtMost(group.replies.lastIndex)
                            dragDistance > swipeThreshold -> (selectedIndex - 1).coerceAtLeast(0)
                            else -> selectedIndex
                        }
                        if (next != selectedIndex) onEvent(DialogEvent.SelectAnswer(group.questionId, group.replies[next].role.id))
                        dragDistance = 0f
                    },
                )
            }
            .testTag("answer_group_${group.questionId}"),
    ) {
        AnswerPagerControls(group, selectedRoleId, readAnswerIds, onEvent)
        SkillMessageCard(
            message = selected,
            onEvent = onEvent,
            expanded = selected.id in expandedAnswerIds,
            onToggleExpanded = { onEvent(DialogEvent.ToggleAnswerExpanded(selected.id)) },
            previewViewportHeight = previewViewportHeight,
        )
    }
}

@Composable
fun AnswerPagerControls(
    group: DialogTimelineItem.Answers,
    selectedRoleId: String?,
    readAnswerIds: Set<String>,
    onEvent: (DialogEvent) -> Unit,
    sticky: Boolean = false,
) {
    if (group.replies.size < 2) return
    val selectedIndex = group.replies.indexOfFirst { it.role.id == selectedRoleId }.coerceAtLeast(0)
    val selected = group.replies[selectedIndex]
    val unread = group.replies.any {
        it.id != selected.id && it.id !in readAnswerIds &&
            it.answerStatus == DialogAnswerStatus.COMPLETED
    }
    val tagSuffix = if (sticky) "_sticky" else ""
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                onEvent(DialogEvent.SelectAnswer(group.questionId, group.replies[selectedIndex - 1].role.id))
            },
            enabled = selectedIndex > 0,
            modifier = Modifier.testTag("answer_previous${tagSuffix}_${group.questionId}"),
        ) {
            Text("‹", fontSize = 24.sp)
        }
        Text(
            text = "${selected.role.name}  ${selectedIndex + 1} / ${group.replies.size}${if (unread) " · 有新回复" else ""}",
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = {
                onEvent(DialogEvent.SelectAnswer(group.questionId, group.replies[selectedIndex + 1].role.id))
            },
            enabled = selectedIndex < group.replies.lastIndex,
            modifier = Modifier.testTag("answer_next${tagSuffix}_${group.questionId}"),
        ) {
            Text("›", fontSize = 24.sp)
        }
    }
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
