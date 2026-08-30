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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogTokens
import com.elio.jianyu.ui.screens.dialog.SkillCapabilityIconType
import com.elio.jianyu.ui.screens.dialog.SkillCapabilityItem
import com.elio.jianyu.ui.screens.dialog.SkillRoleDetailUiModel

/**
 * Skill 角色详情大型 Bottom Sheet
 * 对应设计规范第 14.3 节
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillRoleDetailBottomSheet(
    isOpen: Boolean,
    detail: SkillRoleDetailUiModel?,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isOpen && detail != null) {
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
                    .fillMaxHeight(0.88f)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 1. 顶部标题与关闭按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "Skill 角色详情",
                        color = DialogTokens.TextPrimary,
                        fontSize = DialogTokens.FontSheetTitle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DialogTokens.PageBackground)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = { onEvent(DialogEvent.DismissOverlay) },
                            )
                            .align(Alignment.CenterEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = DialogIcons.Close,
                            contentDescription = "关闭",
                            tint = DialogTokens.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. 角色 Hero 卡片（大半身肖像 + 姓名 + 状态 Pill + 描述）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(DialogTokens.RadiusHero))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    detail.role.tintBg,
                                    DialogTokens.SurfaceWhite,
                                ),
                            ),
                        )
                        .border(
                            width = DialogTokens.BorderThin,
                            color = detail.role.tintBorder,
                            shape = RoundedCornerShape(DialogTokens.RadiusHero),
                        )
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 左侧人物肖像容器（占宽约 38%-42%）
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(detail.role.tintBorder.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (detail.role.avatarResId != null) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = detail.role.avatarResId),
                                    contentDescription = detail.role.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                )
                            } else {
                                Text(
                                    text = detail.role.avatarText.take(2),
                                    color = detail.role.accentColor,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // 右侧角色信息
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.role.name,
                                color = DialogTokens.TextPrimary,
                                fontSize = DialogTokens.FontHeroTitle,
                                fontWeight = FontWeight.Bold,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // 绿色在会话状态 Pill
                            if (detail.isInCurrentSession) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DialogTokens.StatusGreen.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(DialogTokens.StatusGreen),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "已在当前会话",
                                        color = DialogTokens.StatusGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = detail.fullDescription,
                                color = DialogTokens.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                detail.identityDisclosure?.let { disclosure ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DialogTokens.BrandPurpleLight)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = disclosure,
                            color = DialogTokens.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 3. 三大能力卡（擅长、思维特点、表达特点）
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    detail.capabilities.forEach { cap ->
                        val icon = when (cap.iconType) {
                            SkillCapabilityIconType.CUBE -> DialogIcons.Cube
                            SkillCapabilityIconType.NETWORK -> DialogIcons.Network
                            SkillCapabilityIconType.CHAT -> DialogIcons.Chat
                        }
                        CapabilityCard(
                            icon = icon,
                            title = cap.title,
                            detail = cap.detail,
                            accentColor = detail.role.accentColor,
                            bgColor = detail.role.tintBg,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 4. 主 CTA：“让 TA 回答这次问题”（全宽紫色渐变大圆角按钮）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(DialogTokens.RadiusButton))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    DialogTokens.InteractionBlue,
                                    DialogTokens.BrandPurple,
                                ),
                            ),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                onEvent(DialogEvent.DismissOverlay)
                                onEvent(DialogEvent.LetSkillAnswerCurrent(detail.role.id))
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "让 TA 回答这次问题",
                        color = DialogTokens.SurfaceWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. 次级 CTA：“从当前会话移除”
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(DialogTokens.RadiusButton))
                        .background(DialogTokens.SurfaceWhite)
                        .border(
                            width = DialogTokens.BorderThin,
                            color = DialogTokens.RoleLavenderBorder,
                            shape = RoundedCornerShape(DialogTokens.RadiusButton),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = {
                                onEvent(DialogEvent.DismissOverlay)
                                onEvent(DialogEvent.RemoveSkillFromSession(detail.role.id))
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "从当前会话移除",
                        color = DialogTokens.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    icon: ImageVector,
    title: String,
    detail: String,
    accentColor: Color,
    bgColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DialogTokens.RadiusCard))
            .background(DialogTokens.SurfaceWhite)
            .border(
                width = DialogTokens.BorderThin,
                color = DialogTokens.NeutralBorder,
                shape = RoundedCornerShape(DialogTokens.RadiusCard),
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 图标方块容器
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = DialogTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    color = DialogTokens.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
