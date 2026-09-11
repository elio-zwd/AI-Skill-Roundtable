package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogFilters
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.ui.components.JianyuRoleAvatar

internal val skillRoleDiscoveryCategories: List<SkillRoleDiscoveryCategory?> = listOf(
    null,
    SkillRoleDiscoveryCategory.THINKING,
    SkillRoleDiscoveryCategory.CAREER,
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING,
    SkillRoleDiscoveryCategory.PRODUCT_CREATION,
    SkillRoleDiscoveryCategory.COMMUNICATION,
    SkillRoleDiscoveryCategory.OFFICE_TASKS,
    SkillRoleDiscoveryCategory.LIFE_TOOLS,
)

internal fun SkillRoleDiscoveryCategory?.displayName(): String = when (this) {
    null -> "全部"
    SkillRoleDiscoveryCategory.THINKING -> "思考方法"
    SkillRoleDiscoveryCategory.CAREER -> "职业成长"
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING -> "研究学习"
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> "产品创造"
    SkillRoleDiscoveryCategory.COMMUNICATION -> "沟通表达"
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> "办公事务"
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> "生活工具"
}

internal fun SkillRoleDiscoveryCategory.description(): String = when (this) {
    SkillRoleDiscoveryCategory.THINKING -> "检查假设、比较框架、识别风险与盲区"
    SkillRoleDiscoveryCategory.CAREER -> "求职、职业选择、职场协作与考试路径"
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING -> "学习理解、技术研究与事实核查"
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> "产品、创业、经营、增长与竞品判断"
    SkillRoleDiscoveryCategory.COMMUNICATION -> "内容、传播、表达、谈判与现实沟通文案"
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> "会议、文档、合同、人事、交接与申报材料"
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> "消费、自我管理、关系、礼仪与文化类日常辅助"
}

@Composable
internal fun SkillRolePageHeader(
    favoritesOnly: Boolean,
    onToggleFavorites: () -> Unit,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Skill 角色",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "和不同的思考方式对话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onToggleFavorites,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("收藏")
                }
                TextButton(
                    onClick = onOpenFilters,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(OfficialSkillCatalogTestTags.FILTER_BUTTON),
                ) {
                    Text("筛选")
                }
            }
        }
    }
}

@Composable
internal fun SkillRoleSearchAndCategories(
    query: String,
    selectedCategory: SkillRoleDiscoveryCategory?,
    onQueryChanged: (String) -> Unit,
    onCategorySelected: (SkillRoleDiscoveryCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            placeholder = { Text("搜索角色、能力或问题") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag(OfficialSkillCatalogTestTags.SEARCH),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            skillRoleDiscoveryCategories.forEach { category ->
                FilterChip(
                    selected = category == selectedCategory,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.displayName()) },
                    modifier = Modifier.heightIn(min = 40.dp),
                )
            }
        }
        Text(
            text = "按角色最稳定的思考方式分类",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SkillRoleSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}

@Composable
internal fun SkillRoleCategoryInfoCard(
    category: SkillRoleDiscoveryCategory,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = category.displayName(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = category.description(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SkillRoleFeaturedHero(
    role: SkillRoleCardUi,
    onOpenDetail: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JianyuRoleAvatar(
                name = role.name,
                assetPath = role.productionAvatarPath(),
                fallbackContainerColor = roleFallbackContainerColor(role.primaryDiscoveryCategory),
                fallbackContentColor = roleFallbackContentColor(role.primaryDiscoveryCategory),
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = role.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (role.isPersonSimulation) {
                            Text(
                                text = "AI 模拟角色",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    FavoriteRoleButton(role, onToggleFavorite)
                }
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    role.officialSkill.domainTags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
                Text(
                    text = "查看角色",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
internal fun SkillRoleFeaturedSecondaryRow(
    roles: List<SkillRoleCardUi>,
    onOpenDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val singleColumn = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        if (singleColumn) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                roles.forEach { role ->
                    SkillRoleGridCard(
                        role = role,
                        onOpenDetail = { onOpenDetail(role.skillId) },
                        onToggleFavorite = { onToggleFavorite(role.skillId) },
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                roles.forEach { role ->
                    SkillRoleGridCard(
                        role = role,
                        onOpenDetail = { onOpenDetail(role.skillId) },
                        onToggleFavorite = { onToggleFavorite(role.skillId) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (roles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun SkillRoleListRow(
    role: SkillRoleCardUi,
    onOpenDetail: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            JianyuRoleAvatar(
                name = role.name,
                assetPath = role.productionAvatarPath(),
                fallbackContainerColor = roleFallbackContainerColor(role.primaryDiscoveryCategory),
                fallbackContentColor = roleFallbackContentColor(role.primaryDiscoveryCategory),
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = role.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (role.isPersonSimulation) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "AI 模拟角色",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = role.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FavoriteRoleButton(role, onToggleFavorite)
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SkillRoleCategoryGrid(
    roles: List<SkillRoleCardUi>,
    onOpenDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val singleColumn = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.15f
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (singleColumn) {
                roles.forEach { role ->
                    SkillRoleGridCard(
                        role = role,
                        onOpenDetail = { onOpenDetail(role.skillId) },
                        onToggleFavorite = { onToggleFavorite(role.skillId) },
                    )
                }
            } else {
                roles.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        pair.forEach { role ->
                            SkillRoleGridCard(
                                role = role,
                                onOpenDetail = { onOpenDetail(role.skillId) },
                                onToggleFavorite = { onToggleFavorite(role.skillId) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillRoleGridCard(
    role: SkillRoleCardUi,
    onOpenDetail: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .clickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                JianyuRoleAvatar(
                    name = role.name,
                    assetPath = role.productionAvatarPath(),
                    fallbackContainerColor = roleFallbackContainerColor(role.primaryDiscoveryCategory),
                    fallbackContentColor = roleFallbackContentColor(role.primaryDiscoveryCategory),
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(15.dp)),
                )
                Spacer(Modifier.weight(1f))
                FavoriteRoleButton(role, onToggleFavorite)
            }
            Text(
                text = role.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (role.isPersonSimulation) {
                Text(
                    text = "AI 模拟角色",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = role.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FavoriteRoleButton(
    role: SkillRoleCardUi,
    onToggleFavorite: () -> Unit,
) {
    IconButton(
        onClick = onToggleFavorite,
        modifier = Modifier
            .size(48.dp)
            .testTag(OfficialSkillCatalogTestTags.favorite(role.skillId)),
    ) {
        Icon(
            imageVector = if (role.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (role.isFavorite) "取消收藏 ${role.name}" else "收藏 ${role.name}",
            tint = if (role.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SkillRoleEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SkillRoleFilterDialog(
    filters: OfficialSkillCatalogFilters,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(OfficialSkillCatalogEvent.FilterDialogChanged(false)) },
        modifier = Modifier.testTag(OfficialSkillCatalogTestTags.FILTER_DIALOG),
        title = { Text("筛选角色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("角色类型", style = MaterialTheme.typography.labelLarge)
                OfficialSkillPrimaryType.entries.forEach { type ->
                    FilterChip(
                        selected = type in filters.primaryTypes,
                        onClick = { onEvent(OfficialSkillCatalogEvent.TogglePrimaryType(type)) },
                        label = { Text(type.roleTypeDisplayName()) },
                    )
                }
                FilterChip(
                    selected = filters.executableOnly,
                    onClick = { onEvent(OfficialSkillCatalogEvent.ToggleExecutableOnly) },
                    label = { Text("只看当前可执行") },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(OfficialSkillCatalogEvent.ClearFilters) }) {
                Text("清除")
            }
        },
        confirmButton = {
            Button(onClick = { onEvent(OfficialSkillCatalogEvent.FilterDialogChanged(false)) }) {
                Text("完成")
            }
        },
    )
}

private fun OfficialSkillPrimaryType.roleTypeDisplayName(): String = when (this) {
    OfficialSkillPrimaryType.PERSON_PERSPECTIVE -> "人物视角"
    OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR -> "专业顾问"
    OfficialSkillPrimaryType.TASK_ASSISTANT -> "任务助手"
    OfficialSkillPrimaryType.WORKFLOW_CAPABILITY -> "工作流能力"
}

private fun SkillRoleCardUi.productionAvatarPath(): String? =
    avatarAssetPath ?: if (isPersonSimulation) "avatars/$skillId.jpg" else null

@Composable
private fun roleFallbackContainerColor(category: SkillRoleDiscoveryCategory): Color = when (category) {
    SkillRoleDiscoveryCategory.THINKING -> MaterialTheme.colorScheme.primaryContainer
    SkillRoleDiscoveryCategory.CAREER -> MaterialTheme.colorScheme.secondaryContainer
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING -> MaterialTheme.colorScheme.tertiaryContainer
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> MaterialTheme.colorScheme.primaryContainer
    SkillRoleDiscoveryCategory.COMMUNICATION -> MaterialTheme.colorScheme.secondaryContainer
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> MaterialTheme.colorScheme.surfaceVariant
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun roleFallbackContentColor(category: SkillRoleDiscoveryCategory): Color = when (category) {
    SkillRoleDiscoveryCategory.THINKING -> MaterialTheme.colorScheme.onPrimaryContainer
    SkillRoleDiscoveryCategory.CAREER -> MaterialTheme.colorScheme.onSecondaryContainer
    SkillRoleDiscoveryCategory.RESEARCH_LEARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    SkillRoleDiscoveryCategory.PRODUCT_CREATION -> MaterialTheme.colorScheme.onPrimaryContainer
    SkillRoleDiscoveryCategory.COMMUNICATION -> MaterialTheme.colorScheme.onSecondaryContainer
    SkillRoleDiscoveryCategory.OFFICE_TASKS -> MaterialTheme.colorScheme.onSurfaceVariant
    SkillRoleDiscoveryCategory.LIFE_TOOLS -> MaterialTheme.colorScheme.onTertiaryContainer
}
