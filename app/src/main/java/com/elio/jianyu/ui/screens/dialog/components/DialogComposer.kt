package com.elio.jianyu.ui.screens.dialog.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.DialogComposerState
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogSearchState
import com.elio.jianyu.ui.screens.dialog.DialogTokens

/**
 * 见域「对话」页面输入区与联网状态 Chip
 * 1:1 像素级还原设计图
 */
@Composable
fun DialogComposer(
    composerState: DialogComposerState,
    searchState: DialogSearchState,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        // 1. 联网搜索状态 Chip（置于输入框左上方）
        SearchStatusChip(
            searchState = searchState,
            onClick = { onEvent(DialogEvent.ToggleSearchMode) },
            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
        )

        // 2. 对话编辑器容器（纯白、大圆角 24dp、极淡阴影）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = RoundedCornerShape(26.dp),
                    spotColor = Color(0x1A64748B),
                )
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = Color(0xFFEAEBED),
                    shape = RoundedCornerShape(26.dp),
                )
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧浅紫圆底「+」按钮
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3E8FF))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onEvent(DialogEvent.ClickPlusButton) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = DialogIcons.Add,
                        contentDescription = "添加功能",
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 中间输入框区域
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // @ 角色高亮前缀
                        if (composerState.targetRole != null) {
                            TargetRoleTag(
                                roleName = composerState.targetRole.name,
                                onClear = { onEvent(DialogEvent.ClearReplyTargetRole) },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        } else if (composerState.isMultiRoleAnswer) {
                            TargetRoleTag(
                                roleName = "多个角色分别回答",
                                onClear = { onEvent(DialogEvent.ClearReplyTargetRole) },
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }

                        // 文本输入框 (BasicTextField 自适应居中与多行)
                        androidx.compose.foundation.text.BasicTextField(
                            value = composerState.inputText,
                            onValueChange = { onEvent(DialogEvent.InputTextChanged(it)) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.5.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFF1E293B),
                            ),
                            maxLines = 4,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF2563EB)),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (composerState.inputText.isEmpty()) {
                                        Text(
                                            text = "输入问题或想法...",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 14.5.sp,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 右侧「@」按钮
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onEvent(DialogEvent.ClickAtButton) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = DialogIcons.AlternateEmail,
                        contentDescription = "选择回复角色",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 亮蓝色圆形发送按钮（纸飞机图标）
                val isSendActive = composerState.inputText.isNotBlank()
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSendActive) Color(0xFF2563EB)
                            else Color(0xFF2563EB).copy(alpha = 0.5f),
                        )
                        .clickable(
                            enabled = isSendActive,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onEvent(DialogEvent.SendMessage) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = DialogIcons.SendPlane,
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

/**
 * 联网搜索状态 Chip（胶囊形）
 */
@Composable
private fun SearchStatusChip(
    searchState: DialogSearchState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFEEF5FF))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = DialogIcons.Language,
            contentDescription = "联网搜索",
            tint = Color(0xFF2563EB),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "联网搜索",
            color = Color(0xFF2563EB),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = searchState.statusText,
            color = Color(0xFF10B981),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "▾",
            color = Color(0xFF10B981),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * @ 角色前缀高亮标签
 */
@Composable
private fun TargetRoleTag(
    roleName: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEEF5FF))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "@$roleName",
            color = Color(0xFF2563EB),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "取消点名",
            tint = Color(0xFF2563EB),
            modifier = Modifier
                .size(13.dp)
                .clickable(onClick = onClear),
        )
    }
}
