package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogLoader
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import kotlinx.coroutines.launch

@Composable
internal fun SkillRoleRecentRoute(
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onBack: () -> Unit,
    onOpenSkillDetail: (String) -> Unit,
    onStartNewConversation: suspend (String) -> Boolean,
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

    // 基础角色全集投影
    val roleCatalog = projectSkillRoleCatalog(
        catalog = runtime.catalog,
        presentationCatalog = presentationCatalog,
        favoriteIds = favoriteIds,
        recentUses = recentUses,
    )

    var filters by remember { mutableStateOf(RoleDiscoveryFilters()) }
    var filterSheetVisible by rememberSaveable { mutableStateOf(false) }
    var clearMessage by remember { mutableStateOf<String?>(null) }

    val allRecentRoles = roleCatalog.recentRoles
    val visibleRecentRoles = applyDiscoveryFilters(
        roles = allRecentRoles,
        filters = filters.copy(favoritesOnly = false, recentOnly = false),
    )
    val sections = remember(visibleRecentRoles) {
        groupRecentRolesByDate(visibleRecentRoles)
    }

    Box(modifier = modifier.fillMaxSize()) {
        SkillRoleRecentScreen(
            sections = sections,
            filters = filters,
            hasAnyRecent = allRecentRoles.isNotEmpty(),
            message = clearMessage,
            onBack = onBack,
            onOpenFilters = { filterSheetVisible = true },
            onClearAllFilters = { filters = RoleDiscoveryFilters() },
            onOpenDetail = onOpenSkillDetail,
        onStartNewConversation = { skillId ->
            scope.launch {
                onStartNewConversation(skillId)
            }
        },
            onClearRecent = {
                scope.launch {
                    clearMessage = recentClearFeedback(runtime.preferences.clearRecentUses())
                }
            },
        )

        if (filterSheetVisible) {
            SkillRoleFilterSheet(
                visible = true,
                appliedFilters = filters,
                onDismiss = { filterSheetVisible = false },
                onApply = { updatedFilters ->
                    filters = updatedFilters.copy(favoritesOnly = false, recentOnly = false)
                    filterSheetVisible = false
                },
                matchCountProvider = { draft ->
                    applyDiscoveryFilters(
                        roles = allRecentRoles,
                        filters = draft.copy(favoritesOnly = false, recentOnly = false),
                    ).size
                },
                showMyUsageFilters = false,
            )
        }
    }
}
