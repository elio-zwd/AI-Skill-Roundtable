package com.elio.jianyu.ui.screens.dialog.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.R
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogMessageItem
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
                .background(Color(0xFFEEF4FD))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column {
                Text(
                    text = message.text,
                    color = Color(0xFF1E293B),
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
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                    )
                    if (message.isDelivered) {
                        Icon(
                            imageVector = DialogIcons.DoneAll,
                            contentDescription = "已发送",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 用户真实肖像头像
        Image(
            painter = painterResource(id = R.drawable.avatar_user),
            contentDescription = "用户头像",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0xFFD6E4F8), CircleShape),
        )
    }
}

@Composable
fun SkillMessageCard(
    message: DialogMessageItem.SkillMessage,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlanner = message.role.id == "planning_coach" || message.role.id == "planner"
    val isThinker = message.role.id == "systems_thinker" || message.role.id == "thinker"
    val avatarRes = message.role.avatarResId ?: if (isPlanner) R.drawable.avatar_planner else if (isThinker) R.drawable.avatar_thinker else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 左侧真实人物圆形头像
        Box(
            modifier = Modifier
                .size(38.dp)
                .padding(top = 2.dp),
        ) {
            if (avatarRes != null) {
                Image(
                    painter = painterResource(id = avatarRes),
                    contentDescription = message.role.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(
                            1.dp,
                            if (isPlanner) Color(0xFFE0D8FB) else Color(0xFFC7EBD9),
                            CircleShape,
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(message.role.tintBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = message.role.avatarText.take(2),
                        color = message.role.accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // 右侧内容区：姓名 + 时间 + 白底大卡片
        Column(modifier = Modifier.weight(1f)) {
            // 1. 角色姓名与时间
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Text(
                    text = message.role.name,
                    color = Color(0xFF1E293B),
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message.timestamp,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                )
            }

            // 2. 消息主体卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .border(
                        width = 1.dp,
                        color = Color(0xFFEAEBED),
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
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        MarkdownText(
                            markdown = message.text,
                            color = Color(0xFF1E293B),
                            fontSize = 14.5.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // 水平分割线
                    HorizontalDivider(color = Color(0xFFF1F3F5), thickness = 1.dp)

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
                            color = Color(0xFFF1F3F5),
                            modifier = Modifier.height(18.dp),
                        )

                        // 整理内容
                        MessageActionButton(
                            icon = DialogIcons.Bookmark,
                            text = "整理内容",
                            onClick = {
                                onEvent(DialogEvent.SaveMessageAsArtifact(message.id))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
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
            tint = Color(0xFF64748B),
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = Color(0xFF64748B),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
