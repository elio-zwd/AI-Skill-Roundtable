package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SkillRoleFilterSheet(
    visible: Boolean,
    appliedFilters: RoleDiscoveryFilters,
    onDismiss: () -> Unit,
    onApply: (RoleDiscoveryFilters) -> Unit,
    modifier: Modifier = Modifier,
    matchCountProvider: (RoleDiscoveryFilters) -> Int = { 0 },
    showMyUsageFilters: Boolean = true,
) {
    if (!visible) return

    var draftFilters by remember(appliedFilters) { mutableStateOf(appliedFilters) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val matchCount = remember(draftFilters, matchCountProvider) { matchCountProvider(draftFilters) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("skill_role_filter_sheet"),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            // Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("filter_sheet_close"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: 发现分类
                FilterSection(title = "发现分类") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkillRoleDiscoveryCategory.entries.forEach { category ->
                            val selected = category in draftFilters.categories
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) {
                                        draftFilters.categories - category
                                    } else {
                                        draftFilters.categories + category
                                    }
                                    draftFilters = draftFilters.copy(categories = updated)
                                },
                                label = { Text(category.displayName()) },
                            )
                        }
                    }
                }

                // Section 2: 角色类型
                FilterSection(title = "角色类型") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            OfficialSkillPrimaryType.PERSON_PERSPECTIVE to "AI 模拟人物",
                            OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR to "专业顾问",
                            OfficialSkillPrimaryType.TASK_ASSISTANT to "任务助手",
                            OfficialSkillPrimaryType.WORKFLOW_CAPABILITY to "工作流能力",
                        ).forEach { (type, label) ->
                            val selected = type in draftFilters.primaryTypes
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) {
                                        draftFilters.primaryTypes - type
                                    } else {
                                        draftFilters.primaryTypes + type
                                    }
                                    draftFilters = draftFilters.copy(primaryTypes = updated)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                // Section 3: 联网需求
                FilterSection(title = "联网需求") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RoleDiscoveryNetworkFilter.entries.forEach { networkFilter ->
                            val selected = draftFilters.networkRequirement == networkFilter
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    draftFilters = draftFilters.copy(networkRequirement = networkFilter)
                                },
                                label = { Text(networkFilter.label) },
                            )
                        }
                    }
                }

                // Section 4: 资料使用
                FilterSection(title = "资料使用") {
                    FilterChip(
                        selected = draftFilters.materialOnly,
                        onClick = {
                            draftFilters = draftFilters.copy(materialOnly = !draftFilters.materialOnly)
                        },
                        label = { Text("可使用资料") },
                    )
                }

                // Section 5: 我的使用（收藏页中按 G8 隐藏）
                if (showMyUsageFilters) {
                    FilterSection(title = "我的使用") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = draftFilters.favoritesOnly,
                                onClick = {
                                    draftFilters = draftFilters.copy(favoritesOnly = !draftFilters.favoritesOnly)
                                },
                                label = { Text("已收藏") },
                            )
                            FilterChip(
                                selected = draftFilters.recentOnly,
                                onClick = {
                                    draftFilters = draftFilters.copy(recentOnly = !draftFilters.recentOnly)
                                },
                                label = { Text("最近使用") },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bottom Actions Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            draftFilters = draftFilters.reset(
                                preserveFavorites = !showMyUsageFilters,
                            )
                        },
                        modifier = Modifier.testTag("filter_sheet_reset"),
                    ) {
                        Text("重置")
                    }

                    Button(
                        onClick = { onApply(draftFilters) },
                        modifier = Modifier.testTag("filter_sheet_apply"),
                    ) {
                        Text("查看 $matchCount 个角色")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}
