package com.elio.jianyu.ui.screens.dialog.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogTokens

/**
 * 会话更多操作右上锚定 Popover
 * 对应设计规范第 14.6 节
 */
@Composable
fun SessionMoreMenuPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(x = (-8).dp, y = 4.dp),
        modifier = modifier
            .widthIn(min = 180.dp, max = 220.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(DialogTokens.RadiusButton))
            .clip(RoundedCornerShape(DialogTokens.RadiusButton))
            .background(DialogTokens.SurfaceWhite),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // 1. 重命名会话
            PopoverMenuItem(
                icon = DialogIcons.Edit,
                text = "重命名会话",
                onClick = {
                    onDismiss()
                    onEvent(DialogEvent.RenameSession(""))
                },
            )

            // 2. 保存 / 整理成果
            PopoverMenuItem(
                icon = DialogIcons.Star,
                text = "保存 / 整理成果",
                onClick = {
                    onDismiss()
                    onEvent(DialogEvent.SaveOrOrganizeArtifacts)
                },
            )

            // 3. 继续深入
            PopoverMenuItem(
                icon = DialogIcons.Sparkle,
                text = "继续深入",
                onClick = {
                    onDismiss()
                    onEvent(DialogEvent.ContinueDeeper)
                },
            )

            // 4. 导出会话
            PopoverMenuItem(
                icon = DialogIcons.Share,
                text = "导出会话",
                onClick = {
                    onDismiss()
                    onEvent(DialogEvent.ExportSession(""))
                },
            )

            // 5. 归档会话
            PopoverMenuItem(
                icon = DialogIcons.Archive,
                text = "归档会话",
                onClick = {
                    onDismiss()
                    onEvent(DialogEvent.ArchiveSession(""))
                },
            )

            // 分割线
            HorizontalDivider(
                color = DialogTokens.NeutralBorder,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp),
            )

            // 6. 删除会话 (红色危险项)
            PopoverMenuItem(
                icon = Icons.Default.Delete,
                text = "删除会话",
                textColor = DialogTokens.DestructiveRed,
                iconColor = DialogTokens.DestructiveRed,
                onClick = {
                    onDismiss()
                    onEvent(DialogEvent.DeleteSession(""))
                },
            )
        }
    }
}

@Composable
private fun PopoverMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    textColor: Color = DialogTokens.TextPrimary,
    iconColor: Color = DialogTokens.TextSecondary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
