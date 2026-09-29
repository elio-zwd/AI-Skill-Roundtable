package com.elio.jianyu.ui.screens.dialog.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogSessionInfo
import com.elio.jianyu.ui.screens.dialog.DialogTokens
import com.elio.jianyu.ui.components.JianyuShellTestTags
import com.elio.jianyu.ui.components.JianyuTopBarLayout
import com.elio.jianyu.ui.components.JianyuTopBarLevel

/**
 * 见域「对话」页面顶部导航栏
 * 1:1 像素级还原设计图
 */
@Composable
fun DialogTopBar(
    session: DialogSessionInfo,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
    roleStripExpanded: Boolean = true,
) {
    JianyuTopBarLayout(
        level = JianyuTopBarLevel.PRIMARY,
        modifier = modifier,
        navigationContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 20.dp),
                        onClick = { onEvent(DialogEvent.SetDrawerOpen(true)) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "打开会话记录",
                    tint = DialogTokens.TextPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        titleContent = {
            Column(
                modifier = Modifier.clickable { onEvent(DialogEvent.ToggleRoleStrip) },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = session.title,
                    color = DialogTokens.TextPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(JianyuShellTestTags.PAGE_TITLE),
                )
                Spacer(modifier = Modifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "当前会话 · ${session.roleCount} 个 Skill 角色",
                        modifier = Modifier.weight(1f),
                        color = DialogTokens.TextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (roleStripExpanded) "收缩 ▲" else "展开 ▼",
                        color = DialogTokens.BrandPurple,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_session_button")
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 18.dp),
                        onClick = { onEvent(DialogEvent.CreateNewSession) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = DialogIcons.EditNote,
                    contentDescription = "新建会话",
                    tint = DialogTokens.BrandPurple,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 18.dp),
                        onClick = { onEvent(DialogEvent.ToggleMoreMenu) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    tint = DialogTokens.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    )
}
