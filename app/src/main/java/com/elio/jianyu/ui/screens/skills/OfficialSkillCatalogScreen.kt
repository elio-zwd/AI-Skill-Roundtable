package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.ui.components.JianyuStateCard

@Composable
internal fun OfficialSkillCatalogScreen(
    uiState: OfficialSkillCatalogUiState,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(OfficialSkillCatalogTestTags.ROOT),
    ) {
        when {
            uiState.isLoading -> {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag(OfficialSkillCatalogTestTags.LOADING),
                )
                Spacer(Modifier.weight(1f))
            }
            uiState.catalogError != null -> {
                Spacer(Modifier.height(24.dp))
                JianyuStateCard(
                    title = "角色目录暂不可用",
                    message = uiState.catalogError,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag(OfficialSkillCatalogTestTags.ERROR),
                )
            }
            uiState.roleCatalog == null -> {
                Spacer(Modifier.height(24.dp))
                JianyuStateCard(
                    title = "角色目录暂不可用",
                    message = "角色展示数据尚未完成加载。",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .testTag(OfficialSkillCatalogTestTags.ERROR),
                )
            }
            else -> {
                SkillRoleCatalogContent(
                    uiState = uiState,
                    roleCatalog = uiState.roleCatalog,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (uiState.filterDialogVisible) {
        SkillRoleFilterDialog(
            filters = uiState.filters,
            onEvent = onEvent,
        )
    }

    // Task 5 会把旧 Dialog 替换为全屏二级详情；Task 4 先保持既有详情入口可用。
    uiState.selectedSkill?.let { skill ->
        OfficialSkillDetailDialog(
            skill = skill,
            isFavorite = skill.id in uiState.favoriteIds,
            onDismiss = { onEvent(OfficialSkillCatalogEvent.DismissDetail) },
            onToggleFavorite = {
                onEvent(OfficialSkillCatalogEvent.ToggleFavorite(skill.id))
            },
            onAddToCombination = {
                onEvent(OfficialSkillCatalogEvent.CreateCombination(skill.id))
            },
            onUse = { onEvent(OfficialSkillCatalogEvent.UseSkill(skill.id)) },
        )
    }

    // 组合数据能力不在新角色一级页暴露，但保留旧编辑状态兼容，避免删除现有能力。
    uiState.combinationEditor?.let { editor ->
        OfficialSkillCombinationEditorDialog(
            editor = editor,
            catalogSkills = uiState.allSkills.ifEmpty { uiState.visibleSkills },
            onEvent = onEvent,
        )
    }

    uiState.message?.let { message ->
        OfficialSkillCatalogMessageDialog(
            message = message,
            onDismiss = { onEvent(OfficialSkillCatalogEvent.DismissMessage) },
        )
    }
}

@Composable
private fun SkillRoleCatalogContent(
    uiState: OfficialSkillCatalogUiState,
    roleCatalog: SkillRoleCatalogUiState,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCategoryName by rememberSaveable(roleCatalog.selectedCategory) {
        mutableStateOf(roleCatalog.selectedCategory?.name.orEmpty())
    }
    val selectedCategory = selectedCategoryName
        .takeIf(String::isNotBlank)
        ?.let { encoded ->
            runCatching { SkillRoleDiscoveryCategory.valueOf(encoded) }.getOrNull()
        }
    val favoritesOnly = uiState.section == OfficialSkillCatalogSection.FAVORITES
    val categoryRoles = roleCatalog.visibleRoles.filter { role ->
        selectedCategory == null || role.primaryDiscoveryCategory == selectedCategory
    }
    val displayedRoles = if (favoritesOnly) {
        categoryRoles.filter(SkillRoleCardUi::isFavorite)
    } else {
        categoryRoles
    }
    val displayedIds = displayedRoles.mapTo(linkedSetOf(), SkillRoleCardUi::skillId)
    val featuredRoles = roleCatalog.featuredRoles.filter { it.skillId in displayedIds }
    val recentRoles = roleCatalog.recentRoles.filter { it.skillId in displayedIds }.take(2)

    SkillRolePageHeader(
        favoritesOnly = favoritesOnly,
        onToggleFavorites = {
            onEvent(
                OfficialSkillCatalogEvent.SectionChanged(
                    if (favoritesOnly) {
                        OfficialSkillCatalogSection.DISCOVER
                    } else {
                        OfficialSkillCatalogSection.FAVORITES
                    },
                ),
            )
        },
        onOpenFilters = {
            onEvent(OfficialSkillCatalogEvent.FilterDialogChanged(true))
        },
    )

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag(OfficialSkillCatalogTestTags.LIST),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "search_and_categories") {
            SkillRoleSearchAndCategories(
                query = uiState.query,
                selectedCategory = selectedCategory,
                onQueryChanged = {
                    onEvent(OfficialSkillCatalogEvent.SearchChanged(it))
                },
                onCategorySelected = { category ->
                    selectedCategoryName = category?.name.orEmpty()
                },
            )
        }

        if (favoritesOnly) {
            item(key = "favorites_title") {
                SkillRoleSectionTitle("收藏角色")
            }
            if (displayedRoles.isEmpty()) {
                item(key = "favorites_empty") {
                    SkillRoleEmptyState(
                        title = "还没有符合条件的收藏角色",
                        message = "可回到全部角色收藏常用角色，或调整当前搜索与筛选。",
                    )
                }
            } else if (selectedCategory == null) {
                items(displayedRoles, key = { "favorite_${it.skillId}" }) { role ->
                    RoleListRow(role, onEvent)
                }
            } else {
                item(key = "favorites_grid") {
                    RoleGrid(displayedRoles, onEvent)
                }
            }
        } else if (uiState.query.isNotBlank()) {
            item(key = "search_title") {
                SkillRoleSectionTitle("搜索结果")
            }
            if (displayedRoles.isEmpty()) {
                item(key = "search_empty") {
                    SkillRoleEmptyState(
                        title = "没有找到匹配角色",
                        message = "可缩短关键词、切换分类或清除筛选条件。",
                    )
                }
            } else if (selectedCategory == null) {
                items(displayedRoles, key = { "search_${it.skillId}" }) { role ->
                    RoleListRow(role, onEvent)
                }
            } else {
                item(key = "search_grid") {
                    RoleGrid(displayedRoles, onEvent)
                }
            }
        } else if (selectedCategory == null) {
            if (featuredRoles.isNotEmpty()) {
                item(key = "featured_title") {
                    SkillRoleSectionTitle("推荐角色")
                }
                item(key = "featured_hero") {
                    SkillRoleFeaturedHero(
                        role = featuredRoles.first(),
                        onOpenDetail = {
                            onEvent(OfficialSkillCatalogEvent.OpenDetail(featuredRoles.first().skillId))
                        },
                        onToggleFavorite = {
                            onEvent(OfficialSkillCatalogEvent.ToggleFavorite(featuredRoles.first().skillId))
                        },
                    )
                }
                if (featuredRoles.size > 1) {
                    item(key = "featured_secondary") {
                        SkillRoleFeaturedSecondaryRow(
                            roles = featuredRoles.drop(1).take(2),
                            onOpenDetail = {
                                onEvent(OfficialSkillCatalogEvent.OpenDetail(it))
                            },
                            onToggleFavorite = {
                                onEvent(OfficialSkillCatalogEvent.ToggleFavorite(it))
                            },
                        )
                    }
                }
            }

            if (recentRoles.isNotEmpty()) {
                item(key = "recent_title") {
                    SkillRoleSectionTitle(
                        title = "最近使用",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(recentRoles, key = { "recent_${it.skillId}" }) { role ->
                    RoleListRow(role, onEvent)
                }
            }

            item(key = "all_title") {
                SkillRoleSectionTitle(
                    title = "全部角色",
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (displayedRoles.isEmpty()) {
                item(key = "all_empty") {
                    SkillRoleEmptyState(
                        title = "没有符合筛选条件的角色",
                        message = "可清除筛选后查看完整角色目录。",
                    )
                }
            } else {
                items(displayedRoles, key = { "all_${it.skillId}" }) { role ->
                    RoleListRow(role, onEvent)
                }
            }
        } else {
            item(key = "category_info") {
                SkillRoleCategoryInfoCard(selectedCategory)
            }
            if (displayedRoles.isEmpty()) {
                item(key = "category_empty") {
                    SkillRoleEmptyState(
                        title = "这个分类下暂无匹配角色",
                        message = "当前搜索或筛选条件没有结果；不会自动切回全部。",
                    )
                }
            } else {
                item(key = "category_grid") {
                    RoleGrid(displayedRoles, onEvent)
                }
            }
        }

        item(key = "bottom_space") {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RoleListRow(
    role: SkillRoleCardUi,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    SkillRoleListRow(
        role = role,
        onOpenDetail = { onEvent(OfficialSkillCatalogEvent.OpenDetail(role.skillId)) },
        onToggleFavorite = { onEvent(OfficialSkillCatalogEvent.ToggleFavorite(role.skillId)) },
    )
}

@Composable
private fun RoleGrid(
    roles: List<SkillRoleCardUi>,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    SkillRoleCategoryGrid(
        roles = roles,
        onOpenDetail = { onEvent(OfficialSkillCatalogEvent.OpenDetail(it)) },
        onToggleFavorite = { onEvent(OfficialSkillCatalogEvent.ToggleFavorite(it)) },
    )
}
