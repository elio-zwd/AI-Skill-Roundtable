package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.ui.components.JianyuRoleAvatar

@Composable
internal fun SkillRoleSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    filters: RoleDiscoveryFilters,
    roles: List<SkillRoleCardUi>,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit,
    onRemoveFilter: (RoleDiscoveryFilters) -> Unit,
    onClearAllFilters: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val activeFilterCount = filters.activeCount()

    LaunchedEffect(Unit) {
        // 进入页面后请求焦点弹起键盘
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("skill_role_search_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar: Back button + Search input + Filter button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("search_screen_back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("search_screen_input"),
                    placeholder = {
                        Text(
                            text = "搜索角色、能力或问题",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { onQueryChange("") },
                                modifier = Modifier.testTag("search_screen_clear_query"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清除搜索",
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )

                // 筛选按钮
                BadgedBox(
                    badge = {
                        if (activeFilterCount > 0) {
                            Badge { Text(activeFilterCount.toString()) }
                        }
                    },
                ) {
                    FilterChip(
                        selected = activeFilterCount > 0,
                        onClick = onOpenFilters,
                        label = { Text("筛选") },
                        modifier = Modifier.testTag("search_screen_filter_button"),
                    )
                }
            }

            // Active Filters Chips (if any)
            if (activeFilterCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    filters.categories.forEach { category ->
                        FilterChip(
                            selected = true,
                            onClick = {
                                onRemoveFilter(filters.copy(categories = filters.categories - category))
                            },
                            label = { Text(category.displayName()) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    filters.primaryTypes.forEach { type ->
                        val label = when (type) {
                            OfficialSkillPrimaryType.PERSON_PERSPECTIVE -> "AI 模拟人物"
                            OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR -> "专业顾问"
                            OfficialSkillPrimaryType.TASK_ASSISTANT -> "任务助手"
                            OfficialSkillPrimaryType.WORKFLOW_CAPABILITY -> "工作流能力"
                        }
                        FilterChip(
                            selected = true,
                            onClick = {
                                onRemoveFilter(filters.copy(primaryTypes = filters.primaryTypes - type))
                            },
                            label = { Text(label) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    if (filters.networkRequirement != RoleDiscoveryNetworkFilter.ALL) {
                        FilterChip(
                            selected = true,
                            onClick = {
                                onRemoveFilter(filters.copy(networkRequirement = RoleDiscoveryNetworkFilter.ALL))
                            },
                            label = { Text(filters.networkRequirement.label) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    if (filters.materialOnly) {
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveFilter(filters.copy(materialOnly = false)) },
                            label = { Text("可使用资料") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    if (filters.favoritesOnly) {
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveFilter(filters.copy(favoritesOnly = false)) },
                            label = { Text("已收藏") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    if (filters.recentOnly) {
                        FilterChip(
                            selected = true,
                            onClick = { onRemoveFilter(filters.copy(recentOnly = false)) },
                            label = { Text("最近使用") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "移除",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }

                    TextButton(onClick = onClearAllFilters) {
                        Text("清除全部", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Results Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "共找到 ${roles.size} 个角色",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (query.isNotBlank()) {
                    Text(
                        text = "按相关性排序",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Results List or Empty State
            if (roles.isEmpty()) {
                SearchEmptyState(
                    onClearSearchAndFilters = {
                        onQueryChange("")
                        onClearAllFilters()
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(roles, key = { it.skillId }) { role ->
                        SkillRoleSearchCard(
                            role = role,
                            onClick = { onOpenDetail(role.skillId) },
                            onToggleFavorite = { onToggleFavorite(role.skillId) },
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillRoleSearchCard(
    role: SkillRoleCardUi,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("search_role_card_${role.skillId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                JianyuRoleAvatar(
                    name = role.name,
                    assetPath = role.avatarAssetPath,
                    fallbackText = role.name.take(2),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = role.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (role.isPersonSimulation) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = "AI 模拟角色",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    Text(
                        text = role.primaryDiscoveryCategory.displayName(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("search_favorite_button_${role.skillId}"),
                ) {
                    Icon(
                        imageVector = if (role.isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = if (role.isFavorite) "取消收藏" else "收藏",
                        tint = if (role.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(
                text = role.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            // 匹配依据（0～2 条）
            if (role.matchEvidences.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    role.matchEvidences.forEach { evidence ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = "${evidence.label}：${evidence.text}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(
    onClearSearchAndFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )

            Text(
                text = "没有找到匹配的角色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "尝试更换关键词，或清空当前筛选条件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onClearSearchAndFilters,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("清除搜索与筛选")
            }
        }
    }
}
