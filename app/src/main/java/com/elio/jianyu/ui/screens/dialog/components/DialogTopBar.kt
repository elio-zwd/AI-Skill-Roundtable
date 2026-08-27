package com.elio.jianyu.ui.screens.dialog.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogSessionInfo
import com.elio.jianyu.ui.screens.dialog.DialogTokens

/**
 * 见域「对话」页面顶部导航栏
 * 1:1 像素级还原设计图
 */
@Composable
fun DialogTopBar(
    session: DialogSessionInfo,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DialogTokens.SurfaceWhite)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 1. 左侧抽屉菜单按钮 (Hamburger)
            Box(
                modifier = Modifier
                    .size(40.dp)
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

            Spacer(modifier = Modifier.width(6.dp))

            // 2. 中间标题与副标题
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = session.title,
                    color = DialogTokens.TextPrimary,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "${session.roleCount} 个 Skill 角色",
                    color = DialogTokens.TextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 3. 右侧新建/编辑会话按钮 (纯紫色方框带斜笔图标)
            Box(
                modifier = Modifier
                    .size(38.dp)
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

            Spacer(modifier = Modifier.width(2.dp))

            // 4. 右侧三点更多按钮
            Box(
                modifier = Modifier
                    .size(38.dp)
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
        }
    }
}
