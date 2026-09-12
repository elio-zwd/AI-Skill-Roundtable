package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.ui.components.JianyuRoleAvatar
import com.elio.jianyu.ui.components.JianyuShellTestTags

@Composable
internal fun SkillRoleDetailScreen(
    role: SkillRoleCardUi,
    isFavorite: Boolean,
    canAddToCurrentConversation: Boolean,
    actionInProgress: Boolean,
    actionMessage: String?,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartNewConversation: () -> Unit,
    onAddToCurrentConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skill = role.officialSkill
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        RoleDetailHeader(
            isFavorite = isFavorite,
            onBack = onBack,
            onToggleFavorite = onToggleFavorite,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoleDetailHero(role)

            RoleDetailInfoCard(
                marker = "问",
                title = "适合的问题",
                items = skill.typicalScenarios,
            )
            RoleDetailInfoCard(
                marker = "思",
                title = "工作方式",
                items = listOf(skillRoleWorkingStyle(role)),
            )
            RoleDetailInfoCard(
                marker = "入",
                title = "输入要求",
                items = skill.inputRequirements,
            )
            RoleDetailInfoCard(
                marker = "出",
                title = "输出形式",
                items = skill.outputForms,
            )
            RoleDetailInfoCard(
                marker = "界",
                title = "边界",
                items = skill.boundaries + skill.integrityBoundaries,
            )
            RoleDetailSourceCard(role)

            skill.personDisclaimer
                ?.takeIf(String::isNotBlank)
                ?.let { disclaimer ->
                    RoleDetailInfoCard(
                        marker = "AI",
                        title = "AI 模拟说明",
                        items = listOf(disclaimer),
                        accent = true,
                    )
                }
        }

        RoleDetailActions(
            role = role,
            canAddToCurrentConversation = canAddToCurrentConversation,
            actionInProgress = actionInProgress,
            actionMessage = actionMessage,
            onStartNewConversation = onStartNewConversation,
            onAddToCurrentConversation = onAddToCurrentConversation,
        )
    }
}

@Composable
private fun RoleDetailHeader(
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(48.dp)
                .testTag(JianyuShellTestTags.PAGE_BACK_BUTTON),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
            )
        }
        Text(
            text = "Skill 角色详情",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(48.dp),
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RoleDetailHero(role: SkillRoleCardUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = roleDetailContainerColor(role.primaryDiscoveryCategory),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 170.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoleDetailIdentityVisual(
                role = role,
                modifier = Modifier
                    .width(142.dp)
                    .height(170.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = role.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoleDetailBadge(role.primaryDiscoveryCategory.displayName())
                    RoleDetailBadge(role.detailTypeLabel())
                }
                if (role.isPersonSimulation) {
                    RoleDetailBadge("AI 模拟角色")
                }
            }
        }
    }
}

@Composable
private fun RoleDetailIdentityVisual(
    role: SkillRoleCardUi,
    modifier: Modifier,
) {
    if (role.isPersonSimulation) {
        JianyuRoleAvatar(
            name = role.name,
            assetPath = role.avatarAssetPath ?: "avatars/${role.skillId}.jpg",
            fallbackContainerColor = roleDetailContainerColor(role.primaryDiscoveryCategory),
            fallbackContentColor = roleDetailContentColor(role.primaryDiscoveryCategory),
            modifier = modifier.clip(RoundedCornerShape(24.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(24.dp))
                .background(roleDetailContainerColor(role.primaryDiscoveryCategory)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = role.name.take(2),
                color = roleDetailContentColor(role.primaryDiscoveryCategory),
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RoleDetailInfoCard(
    marker: String,
    title: String,
    items: List<String>,
    accent: Boolean = false,
) {
    if (items.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (accent) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = marker,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleDetailSourceCard(role: SkillRoleCardUi) {
    val skill = role.officialSkill
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "源",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = "来源与能力依据",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = skill.sourceSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "能力类型：${role.detailTypeLabel()} · 发现分类：${role.primaryDiscoveryCategory.displayName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RoleDetailActions(
    role: SkillRoleCardUi,
    canAddToCurrentConversation: Boolean,
    actionInProgress: Boolean,
    actionMessage: String?,
    onStartNewConversation: () -> Unit,
    onAddToCurrentConversation: () -> Unit,
) {
    val skill = role.officialSkill
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onStartNewConversation,
                    enabled = role.isExecutable && !actionInProgress,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp),
                ) {
                    Text("开始新对话")
                }
                OutlinedButton(
                    onClick = onAddToCurrentConversation,
                    enabled = role.isExecutable && canAddToCurrentConversation && !actionInProgress,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 54.dp),
                ) {
                    Text("增加到当前会话")
                }
            }
            when {
                !role.isExecutable -> Text(
                    text = skill.nonExecutableReason ?: "该角色当前不可执行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !canAddToCurrentConversation -> Text(
                    text = "当前没有可加入的会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RoleDetailBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

private fun skillRoleWorkingStyle(role: SkillRoleCardUi): String = when (role.primaryType) {
    OfficialSkillPrimaryType.PERSON_PERSPECTIVE ->
        "以公开观点与表达风格提供模拟视角，用于启发与比较，不代表本人。"
    OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR ->
        "围绕问题给出结构化专业建议，并明确前提、限制与需要补充的信息。"
    OfficialSkillPrimaryType.TASK_ASSISTANT ->
        "围绕明确任务整理输入、执行步骤和可直接使用的输出。"
    OfficialSkillPrimaryType.WORKFLOW_CAPABILITY ->
        "按稳定工作流推进多步骤任务，并在关键节点保留检查与确认。"
}

private fun SkillRoleCardUi.detailTypeLabel(): String = when (primaryType) {
    OfficialSkillPrimaryType.PERSON_PERSPECTIVE -> "人物视角"
    OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR -> "专业顾问"
    OfficialSkillPrimaryType.TASK_ASSISTANT -> "任务助手"
    OfficialSkillPrimaryType.WORKFLOW_CAPABILITY -> "工作流"
}

@Composable
private fun roleDetailContainerColor(category: SkillRoleDiscoveryCategory): Color = when (category) {
    SkillRoleDiscoveryCategory.THINKING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
    SkillRoleDiscoveryCategory.CAREER -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.48f)
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
    SkillRoleDiscoveryCategory.COMMUNICATION -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.44f)
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.46f)
}

@Composable
private fun roleDetailContentColor(category: SkillRoleDiscoveryCategory): Color = when (category) {
    SkillRoleDiscoveryCategory.THINKING,
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> MaterialTheme.colorScheme.onPrimaryContainer
    SkillRoleDiscoveryCategory.CAREER,
    SkillRoleDiscoveryCategory.COMMUNICATION -> MaterialTheme.colorScheme.onSecondaryContainer
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING,
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> MaterialTheme.colorScheme.onTertiaryContainer
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> MaterialTheme.colorScheme.onSurfaceVariant
}
