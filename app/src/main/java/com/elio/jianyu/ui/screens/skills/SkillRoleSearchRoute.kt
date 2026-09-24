package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
internal fun SkillRoleSearchRoute(
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onBack: () -> Unit,
    onOpenSkillDetail: (String) -> Unit,
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

    // 搜索与相关性排序
    val searchedRoles = searchSkillRoles(roleCatalog.allRoles, query)

    // 筛选过滤
    val visibleRoles = applyDiscoveryFilters(searchedRoles, filters)

    Box(modifier = modifier.fillMaxSize()) {
        SkillRoleSearchScreen(
            query = query,
            onQueryChange = { query = it },
            filters = filters,
            roles = visibleRoles,
            onBack = onBack,
            onOpenFilters = { filterSheetVisible = true },
            onRemoveFilter = { filters = it },
            onClearAllFilters = { filters = RoleDiscoveryFilters() },
            onOpenDetail = onOpenSkillDetail,
            onToggleFavorite = { skillId ->
                scope.launch {
                    val isFav = skillId in favoriteIds
                    runtime.preferences.setFavorite(skillId, !isFav)
                }
            },
        )

        // 筛选抽屉
        if (filterSheetVisible) {
            SkillRoleFilterSheet(
                visible = filterSheetVisible,
                appliedFilters = filters,
                onDismiss = { filterSheetVisible = false },
                onApply = { updatedFilters ->
                    filters = updatedFilters
                    filterSheetVisible = false
                },
                matchCountProvider = { draft -> applyDiscoveryFilters(searchedRoles, draft).size },
                showMyUsageFilters = true,
            )
        }
    }
}
