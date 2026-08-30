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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.DialogComposerState
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogTokens
import com.elio.jianyu.ui.screens.dialog.SkillRoleUiModel

/**
 * 选择本次回复角色 / @ Skill 角色 Bottom Sheet
 * 对应设计规范第 14.5 节
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetRoleSelectionBottomSheet(
    isOpen: Boolean,
    activeRoles: List<SkillRoleUiModel>,
    composerState: DialogComposerState,
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                // 居中标题
                Text(
                    text = "选择本次回复角色",
                    color = DialogTokens.TextPrimary,
                    fontSize = DialogTokens.FontSheetTitle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                )

                // 角色选项列表
                activeRoles.forEach { role ->
                    val isSelected = composerState.targetRole?.id == role.id
                    RoleOptionRow(
                        role = role,
                        isSelected = isSelected,
                        onClick = {
                            onEvent(DialogEvent.DismissOverlay)
                            onEvent(DialogEvent.SelectReplyTargetRole(role))
                        },
                    )
                    HorizontalDivider(color = DialogTokens.NeutralBorder)
                }

                if (activeRoles.size > 1) {
                    val isMultiSelected = composerState.isMultiRoleAnswer
                    MultiRoleAnswerOptionRow(
                        isSelected = isMultiSelected,
                        onClick = {
                            onEvent(DialogEvent.DismissOverlay)
                            onEvent(DialogEvent.SelectMultiRoleAnswer)
                        },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RoleOptionRow(
    role: SkillRoleUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(role.tintBg),
                contentAlignment = Alignment.Center,
            ) {
                if (role.avatarResId != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = role.avatarResId),
                        contentDescription = role.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Text(
                        text = role.avatarText.take(2),
                        color = role.accentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = role.name,
                    color = DialogTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = role.shortDescription,
                    color = DialogTokens.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = DialogIcons.Check,
                contentDescription = "已选中",
                tint = DialogTokens.InteractionBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MultiRoleAnswerOptionRow(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 多人图标
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(DialogTokens.BrandPurpleLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = DialogIcons.Groups,
                    contentDescription = null,
                    tint = DialogTokens.BrandPurple,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "多个角色分别回答",
                    color = DialogTokens.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "当前会话中的角色将各自独立回答",
                    color = DialogTokens.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = DialogIcons.Check,
                contentDescription = "已选中",
                tint = DialogTokens.InteractionBlue,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
