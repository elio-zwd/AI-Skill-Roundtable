package com.elio.jianyu.ui.screens.skills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogLoader
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import kotlinx.coroutines.launch

/**
 * UI-02 专用角色目录 Route。
 *
 * 与旧 OfficialSkillCatalogRoute 分离，避免把组合编辑、旧 Tab 与旧 Dialog 详情继续带入
 * 新的【角色】一级页面。OfficialSkillCatalog 仍是 44 项身份/能力事实源。
 */
@Composable
internal fun SkillRoleCatalogRoute(
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onOpenSkillDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToRecent: () -> Unit = {},
) {
    val runtime = when (runtimeResult) {
        is OfficialSkillCatalogRuntimeResult.Failure -> {
            SkillRolePageScreen(
                uiState = OfficialSkillCatalogUiState(
                    isLoading = false,
                    catalogError = runtimeResult.message,
                ),
                onEvent = {},
                modifier = modifier,
            )
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
            SkillRolePageScreen(
                uiState = OfficialSkillCatalogUiState(
                    isLoading = false,
                    catalogError = presentationResult.message,
                ),
                onEvent = {},
                modifier = modifier,
            )
            return
        }
        is SkillRolePresentationLoadResult.Success -> presentationResult.catalog
    }

    val scope = rememberCoroutineScope()
    val favoriteIds by runtime.preferences.favoriteIds.collectAsState()
    val recentUses by runtime.preferences.recentUses.collectAsState()

    var discoveryFilters by remember { mutableStateOf(RoleDiscoveryFilters()) }
    var discoveryFilterSheetVisible by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val unfilteredRoleCatalog = projectSkillRoleCatalog(
        catalog = runtime.catalog,
        presentationCatalog = presentationCatalog,
        favoriteIds = favoriteIds,
        recentUses = recentUses,
    )
    val roleCatalog = unfilteredRoleCatalog.copy(
        visibleRoles = applyDiscoveryFilters(unfilteredRoleCatalog.allRoles, discoveryFilters),
    )

    SkillRolePageScreen(
        uiState = OfficialSkillCatalogUiState(
            isLoading = false,
            visibleSkills = roleCatalog.visibleRoles.map(SkillRoleCardUi::officialSkill),
            allSkills = runtime.catalog.skills,
            totalSkillCount = runtime.catalog.skills.size,
            favoriteIds = favoriteIds,
            recentUses = recentUses,
            roleCatalog = roleCatalog,
            discoveryFilters = discoveryFilters,
            discoveryFilterSheetVisible = discoveryFilterSheetVisible,
            message = message,
        ),
        onEvent = { event ->
            when (event) {
                is OfficialSkillCatalogEvent.SearchChanged,
                is OfficialSkillCatalogEvent.SectionChanged,
                is OfficialSkillCatalogEvent.FilterDialogChanged,
                is OfficialSkillCatalogEvent.TogglePrimaryType,
                is OfficialSkillCatalogEvent.TogglePrimaryValue,
                is OfficialSkillCatalogEvent.ToggleUseMode,
                is OfficialSkillCatalogEvent.ToggleNetwork,
                is OfficialSkillCatalogEvent.ToggleMaterial,
                is OfficialSkillCatalogEvent.ToggleRisk,
                is OfficialSkillCatalogEvent.TogglePublication,
                OfficialSkillCatalogEvent.ToggleExecutableOnly,
                OfficialSkillCatalogEvent.ClearFilters -> Unit
                is OfficialSkillCatalogEvent.OpenDetail -> {
                    if (runtime.catalog.containsOfficialId(event.skillId)) {
                        onOpenSkillDetail(event.skillId)
                    } else {
                        message = "未知官方 Skill ID"
                    }
                }
                OfficialSkillCatalogEvent.DismissDetail -> Unit
                is OfficialSkillCatalogEvent.ToggleFavorite -> scope.launch {
                    val shouldFavorite = event.skillId !in favoriteIds
                    if (!runtime.preferences.setFavorite(event.skillId, shouldFavorite)) {
                        message = "收藏保存失败或角色已失效"
                    }
                }
                is OfficialSkillCatalogEvent.UseSkill -> Unit
                is OfficialSkillCatalogEvent.CreateCombination,
                is OfficialSkillCatalogEvent.EditCombination,
                is OfficialSkillCatalogEvent.DeleteCombination,
                OfficialSkillCatalogEvent.DismissCombinationEditor,
                is OfficialSkillCatalogEvent.CombinationNameChanged,
                is OfficialSkillCatalogEvent.ToggleCombinationMember,
                is OfficialSkillCatalogEvent.MoveCombinationMember,
                is OfficialSkillCatalogEvent.CombinationResponsibilityChanged,
                OfficialSkillCatalogEvent.SaveCombination -> Unit
                OfficialSkillCatalogEvent.DismissMessage -> message = null
                OfficialSkillCatalogEvent.NavigateToSearch -> onNavigateToSearch()
                OfficialSkillCatalogEvent.NavigateToFavorites -> onNavigateToFavorites()
                OfficialSkillCatalogEvent.NavigateToRecent -> onNavigateToRecent()
                is OfficialSkillCatalogEvent.DiscoveryFilterSheetChanged -> {
                    discoveryFilterSheetVisible = event.visible
                }
                is OfficialSkillCatalogEvent.DiscoveryFiltersApplied -> {
                    discoveryFilters = event.filters
                    discoveryFilterSheetVisible = false
                }
            }
        },
        modifier = modifier,
    )
}
