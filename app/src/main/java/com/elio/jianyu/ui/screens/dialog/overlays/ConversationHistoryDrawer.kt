package com.elio.jianyu.ui.screens.dialog.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.ConversationDrawerUiModel
import com.elio.jianyu.ui.screens.dialog.ConversationGroupUiModel
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogTokens
import com.elio.jianyu.ui.screens.dialog.SessionSummaryUiModel

/**
 * 会话记录左侧抽屉 Drawer
 * 对应设计规范第 14.1 节
 */
@Composable
fun ConversationHistoryDrawer(
    isOpen: Boolean,
    drawerData: ConversationDrawerUiModel,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isOpen) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DialogTokens.ScrimOverlay)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onEvent(DialogEvent.SetDrawerOpen(false)) },
                ),
        ) {
            // 抽屉卡片（占屏幕约 68% 宽度）
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.72f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // 阻止点击穿透
                    )
                    .clip(
                        RoundedCornerShape(
                            topEnd = DialogTokens.RadiusSheetTop,
                            bottomEnd = DialogTokens.RadiusSheetTop,
                        ),
                    )
                    .background(DialogTokens.SurfaceWhite)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // 1. 顶部：标题 + 关闭 ×
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (drawerData.isShowingArchived) "已归档会话" else "会话记录",
                            color = DialogTokens.TextPrimary,
                            fontSize = DialogTokens.FontSheetTitle,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true),
                                    onClick = { onEvent(DialogEvent.SetDrawerOpen(false)) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = DialogIcons.Close,
                                contentDescription = "关闭",
                                tint = DialogTokens.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. 新建会话按钮（大号浅紫背景全宽按钮）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(DialogTokens.RadiusButton))
                            .background(DialogTokens.BrandPurpleLight)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = {
                                    onEvent(DialogEvent.SetDrawerOpen(false))
                                    onEvent(DialogEvent.CreateNewSession)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = DialogIcons.Add,
                                contentDescription = null,
                                tint = DialogTokens.BrandPurple,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "新建会话",
                                color = DialogTokens.BrandPurple,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. 搜索栏 (胶囊形，居中自适应排版)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(
                                width = 1.dp,
                                color = Color(0xFFE2E8F0),
                                shape = RoundedCornerShape(20.dp),
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = DialogIcons.Search,
                                contentDescription = "搜索",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = drawerData.searchQuery,
                                onValueChange = { onEvent(DialogEvent.SearchSessions(it)) },
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 13.5.sp,
                                    color = DialogTokens.TextPrimary,
                                ),
                                singleLine = true,
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(DialogTokens.BrandPurple),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart,
                                    ) {
                                        if (drawerData.searchQuery.isEmpty()) {
                                            Text(
                                                text = "搜索会话记录",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 13.5.sp,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            if (drawerData.searchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = DialogIcons.Close,
                                    contentDescription = "清空",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onEvent(DialogEvent.SearchSessions("")) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. 最近会话标题
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        Icon(
                            imageVector = DialogIcons.AccessTime,
                            contentDescription = null,
                            tint = DialogTokens.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (drawerData.isShowingArchived) "已归档" else "最近会话",
                            color = DialogTokens.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // 5. 分组会话列表（今天、昨天、更早）
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        drawerData.groups.forEach { group ->
                            item {
                                Text(
                                    text = group.groupName,
                                    color = DialogTokens.TextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
                                )
                            }
                            items(group.sessions, key = { it.id }) { session ->
                                DrawerSessionItem(
                                    session = session,
                                    onClick = {
                                        onEvent(DialogEvent.SetDrawerOpen(false))
                                        onEvent(DialogEvent.SelectSession(session.id))
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 6. 底部固定已归档会话入口
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(DialogTokens.RadiusButton))
                            .background(DialogTokens.BrandPurpleLight.copy(alpha = 0.5f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = { onEvent(DialogEvent.OpenArchivedSessions) },
                            )
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = DialogIcons.Archive,
                                    contentDescription = null,
                                    tint = DialogTokens.BrandPurple,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (drawerData.isShowingArchived) {
                                        "返回最近会话"
                                    } else {
                                        "已归档会话 (${drawerData.archivedCount})"
                                    },
                                    color = DialogTokens.BrandPurple,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Text(
                                text = "›",
                                color = DialogTokens.BrandPurple,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 抽屉单个会话项
 */
@Composable
private fun DrawerSessionItem(
    session: SessionSummaryUiModel,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (session.isSelected) DialogTokens.BrandPurpleLight
                else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            // 当前会话左侧指示条
            if (session.isSelected) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(DialogTokens.BrandPurple),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                // 标题与时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = session.title,
                        color = DialogTokens.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (session.isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = session.time,
                        color = DialogTokens.TextTertiary,
                        fontSize = 11.sp,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 一行预览文本
                Text(
                    text = session.previewText,
                    color = DialogTokens.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 重叠微型头像 + 角色数
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 头像小叠堆
                    Row(modifier = Modifier.padding(end = 4.dp)) {
                        session.roleAvatars.forEachIndexed { index, avatar ->
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == 0) DialogTokens.RoleLavenderBorder
                                        else DialogTokens.RoleMintBorder,
                                    )
                                    .border(1.dp, DialogTokens.SurfaceWhite, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = avatar.take(1),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DialogTokens.TextPrimary,
                                )
                            }
                        }
                    }
                    Text(
                        text = session.roleCountText,
                        color = DialogTokens.TextTertiary,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
