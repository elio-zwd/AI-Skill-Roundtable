package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogLoader
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import kotlinx.coroutines.launch

@Composable
internal fun SkillRoleFavoritesRoute(
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onBack: () -> Unit,
    onOpenSkillDetail: (String) -> Unit,
    onBrowseAllRoles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = when (runtimeResult) {
        is OfficialSkillCatalogRuntimeResult.Failure -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("角色目录暂不可用：${runtimeResult.message}")
            }
            return
        }
        is OfficialSkillCatalogRuntimeResult.Success -> runtimeResult.runtime
    }

    val appContext = LocalContext.current.applicationContext
    val presentationResult = remember(appContext, runtime.catalog) {
        SkillRolePresentationCatalogLoader.load(appContext, runtime.catalog)
    }
    val presentationCatalog = when (presentationResult) {
        is SkillRolePresentationLoadResult.Failure -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("角色配置暂不可用：${presentationResult.message}")
            }
            return
        }
        is SkillRolePresentationLoadResult.Success -> presentationResult.catalog
    }

    val scope = rememberCoroutineScope()
    val favoriteIds by runtime.preferences.favoriteIds.collectAsState()
    val recentUses by runtime.preferences.recentUses.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(RoleDiscoveryFilters()) }
    var filterSheetVisible by rememberSaveable { mutableStateOf(false) }

    // 基础角色全集投影
    val roleCatalog = projectSkillRoleCatalog(
        catalog = runtime.catalog,
        presentationCatalog = presentationCatalog,
        favoriteIds = favoriteIds,
        recentUses = recentUses,
    )

    // 仅取收藏的角色
    val allFavoriteRoles = roleCatalog.allRoles.filter { it.isFavorite }

    // 收藏页内搜索
    val searchedRoles = searchSkillRoles(allFavoriteRoles, query)

    // 筛选（按 G8 规则，忽略 favoritesOnly 与 recentOnly）
    val visibleRoles = applyDiscoveryFilters(
        roles = searchedRoles,
        filters = filters.copy(favoritesOnly = false, recentOnly = false),
    )

    Box(modifier = modifier.fillMaxSize()) {
        SkillRoleFavoritesScreen(
            query = query,
            onQueryChange = { query = it },
            filters = filters,
            roles = visibleRoles,
            hasAnyFavorites = allFavoriteRoles.isNotEmpty(),
            onBack = onBack,
            onOpenFilters = { filterSheetVisible = true },
            onRemoveFilter = { filters = it },
            onClearAllFilters = { filters = RoleDiscoveryFilters() },
            onOpenDetail = onOpenSkillDetail,
            onRemoveFavorite = { skillId ->
                scope.launch {
                    runtime.preferences.setFavorite(skillId, false)
                }
            },
            onBrowseAllRoles = onBrowseAllRoles,
        )

        if (filterSheetVisible) {
            SkillRoleFilterSheet(
                visible = filterSheetVisible,
                appliedFilters = filters,
                onDismiss = { filterSheetVisible = false },
                onApply = { updatedFilters ->
                    filters = updatedFilters
                    filterSheetVisible = false
                },
                matchCountProvider = { draft ->
                    applyDiscoveryFilters(
                        roles = searchedRoles,
                        filters = draft.copy(favoritesOnly = false, recentOnly = false),
                    ).size
                },
                showMyUsageFilters = false, // 收藏页按 G8 隐藏已收藏/最近使用
            )
        }
    }
}
