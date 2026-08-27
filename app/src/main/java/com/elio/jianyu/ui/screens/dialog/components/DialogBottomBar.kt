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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons

/**
 * 见域「对话」页面底部一级导航栏
 * 1:1 像素级还原设计图
 */
@Composable
fun DialogBottomBar(
    selectedTabIndex: Int = 0,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = Color(0xFFF1F3F5), thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            // 1. 对话（高亮深蓝）
            BottomNavItem(
                icon = DialogIcons.ChatBubble,
                label = "对话",
                isSelected = selectedTabIndex == 0,
                onClick = { onEvent(DialogEvent.NavigateBottomTab(0)) },
                modifier = Modifier.weight(1f),
            )

            // 2. 资料
            BottomNavItem(
                icon = DialogIcons.Folder,
                label = "资料",
                isSelected = selectedTabIndex == 1,
                onClick = { onEvent(DialogEvent.NavigateBottomTab(1)) },
                modifier = Modifier.weight(1f),
            )

            // 3. 成果
            BottomNavItem(
                icon = DialogIcons.StarOutline,
                label = "成果",
                isSelected = selectedTabIndex == 2,
                onClick = { onEvent(DialogEvent.NavigateBottomTab(2)) },
                modifier = Modifier.weight(1f),
            )

            // 4. 我的
            BottomNavItem(
                icon = DialogIcons.PersonOutline,
                label = "我的",
                isSelected = selectedTabIndex == 3,
                onClick = { onEvent(DialogEvent.NavigateBottomTab(3)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 26.dp),
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = activeColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                color = activeColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}
