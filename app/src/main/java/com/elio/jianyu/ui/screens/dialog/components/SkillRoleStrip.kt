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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.elio.jianyu.R
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import com.elio.jianyu.ui.screens.dialog.DialogTokens
import com.elio.jianyu.ui.screens.dialog.SkillRoleUiModel

/**
 * 见域「对话」页面顶部 Skill 角色条
 * 1:1 像素级还原设计图
 */
@Composable
fun SkillRoleStrip(
    activeRoles: List<SkillRoleUiModel>,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(DialogTokens.SurfaceWhite)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(activeRoles, key = SkillRoleUiModel::id) { role ->
            SkillRoleCard(
                role = role,
                onClick = { onEvent(DialogEvent.ClickSkillCard(role.id)) },
                modifier = Modifier.width(142.dp),
            )
        }
        item {
            AddSkillRoleEntryCard(
                onClick = { onEvent(DialogEvent.ClickAddSkillCard) },
                modifier = Modifier.width(92.dp),
            )
        }
    }
}

/**
 * 单个平级 Skill 角色卡片（使用真实肖像）
 */
@Composable
private fun SkillRoleCard(
    role: SkillRoleUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlanner = role.id == "planning_coach" || role.id == "planner"
    val isThinker = role.id == "systems_thinker" || role.id == "thinker"

    // 卡片背景与描边
    val cardBg = if (isPlanner) Color(0xFFF5F4FE) else if (isThinker) Color(0xFFF0F9F5) else role.tintBg
    val cardBorder = if (isPlanner) Color(0xFFECEAFB) else if (isThinker) Color(0xFFDCF2E7) else role.tintBorder
    val avatarRes = role.avatarResId ?: if (isPlanner) R.drawable.avatar_planner else if (isThinker) R.drawable.avatar_thinker else null

    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(
                width = 1.dp,
                color = cardBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // 装饰星光
        if (isPlanner) {
            // 规划教练左上角浅紫星光
            Icon(
                imageVector = DialogIcons.Sparkle,
                contentDescription = null,
                tint = Color(0xFFB8A9E8).copy(alpha = 0.6f),
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.TopStart),
            )
        } else if (isThinker) {
            // 系统思考者右上角浅绿星光
            Icon(
                imageVector = DialogIcons.Sparkle,
                contentDescription = null,
                tint = Color(0xFF74C29E).copy(alpha = 0.7f),
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.TopEnd),
            )
        }

        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 真人肖像头像与右下角在线状态绿点
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                SkillRoleAvatar(
                    role = role.copy(avatarResId = avatarRes),
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )

                // 右下角绿色在线圆点（带白色描边）
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(DialogTokens.SurfaceWhite)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(DialogTokens.StatusGreen),
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 姓名与两行能力短描述
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = role.name,
                    color = DialogTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = role.shortDescription,
                    color = DialogTokens.TextSecondary,
                    fontSize = 10.5.sp,
                    lineHeight = 13.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 「增加 Skill 角色」入口卡片
 */
@Composable
private fun AddSkillRoleEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF7F7FA))
            .border(
                width = 1.dp,
                color = Color(0xFFEBEBF0),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = DialogIcons.Add,
                contentDescription = "增加",
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "增加",
                    color = DialogTokens.TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Skill 角色",
                    color = DialogTokens.TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
