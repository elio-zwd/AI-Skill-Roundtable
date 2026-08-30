package com.elio.jianyu.ui.screens.dialog.overlays

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.elio.jianyu.ui.screens.dialog.DialogOverlayType
import com.elio.jianyu.ui.screens.dialog.DialogTokens

/**
 * 输入区「+」二级功能 Bottom Sheet
 * 对应设计规范第 14.4 节
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerFeaturesBottomSheet(
    isOpen: Boolean,
    isSearchEnabled: Boolean,
    thinkingIntensity: String,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { onEvent(DialogEvent.DismissOverlay) },
            sheetState = sheetState,
            containerColor = DialogTokens.SurfaceWhite,
            shape = RoundedCornerShape(
                topStart = DialogTokens.RadiusSheetTop,
                topEnd = DialogTokens.RadiusSheetTop,
            ),
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // 1. 添加文件
                ComposerFeatureRow(
                    icon = DialogIcons.AttachFile,
                    title = "添加文件",
                    onClick = {
                        onEvent(DialogEvent.DismissOverlay)
                        onEvent(DialogEvent.AddFileAttachment)
                    },
                )
                HorizontalDivider(color = DialogTokens.NeutralBorder)

                // 2. 选择资料
                ComposerFeatureRow(
                    icon = DialogIcons.Folder,
                    title = "选择资料",
                    onClick = {
                        onEvent(DialogEvent.DismissOverlay)
                        onEvent(DialogEvent.SelectMaterials)
                    },
                )
                HorizontalDivider(color = DialogTokens.NeutralBorder)

                // 3. 本次参考内容
                ComposerFeatureRow(
                    icon = DialogIcons.Description,
                    title = "本次参考内容",
                    onClick = {
                        onEvent(DialogEvent.DismissOverlay)
                        onEvent(DialogEvent.ViewReferenceContent)
                    },
                )
                HorizontalDivider(color = DialogTokens.NeutralBorder)

                // 4. 联网搜索 · 已开启
                ComposerFeatureRow(
                    icon = DialogIcons.Language,
                    title = "联网搜索",
                    trailingText = if (isSearchEnabled) "已开启" else "已关闭",
                    trailingTextColor = if (isSearchEnabled) DialogTokens.StatusGreen else DialogTokens.TextTertiary,
                    onClick = {
                        onEvent(DialogEvent.ToggleSearchMode)
                    },
                )
                HorizontalDivider(color = DialogTokens.NeutralBorder)

                // 5. 思考强度
                ComposerFeatureRow(
                    icon = DialogIcons.GraphicEq,
                    title = "思考强度",
                    trailingText = thinkingIntensity,
                    trailingTextColor = DialogTokens.TextSecondary,
                    onClick = {
                        val next = when (thinkingIntensity) {
                            "极简" -> "标准"
                            "标准" -> "深度"
                            else -> "极简"
                        }
                        onEvent(DialogEvent.SelectThinkingIntensity(next))
                    },
                )
                HorizontalDivider(color = DialogTokens.NeutralBorder)

                // 6. 增加 Skill 角色
                ComposerFeatureRow(
                    icon = DialogIcons.PersonAdd,
                    title = "增加 Skill 角色",
                    onClick = {
                        onEvent(DialogEvent.SetOverlay(DialogOverlayType.SHEET_ADD_SKILL))
                    },
                )
                HorizontalDivider(color = DialogTokens.NeutralBorder)

                // 7. 交叉讨论
                ComposerFeatureRow(
                    icon = DialogIcons.Forum,
                    title = "交叉讨论",
                    onClick = {
                        onEvent(DialogEvent.DismissOverlay)
                        onEvent(DialogEvent.TriggerCrossDiscussion)
                    },
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ComposerFeatureRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    trailingTextColor: Color = DialogTokens.TextSecondary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = DialogTokens.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                color = DialogTokens.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    color = trailingTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = "›",
                color = DialogTokens.TextTertiary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
