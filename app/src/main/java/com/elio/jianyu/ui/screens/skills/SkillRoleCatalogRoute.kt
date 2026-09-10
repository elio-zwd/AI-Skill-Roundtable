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
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogFilters
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode
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
) {
    val runtime = when (runtimeResult) {
        is OfficialSkillCatalogRuntimeResult.Failure -> {
            OfficialSkillCatalogScreen(
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
            OfficialSkillCatalogScreen(
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

    var query by rememberSaveable { mutableStateOf("") }
    var sectionName by rememberSaveable { mutableStateOf(OfficialSkillCatalogSection.DISCOVER.name) }
    val section = runCatching { OfficialSkillCatalogSection.valueOf(sectionName) }
        .getOrDefault(OfficialSkillCatalogSection.DISCOVER)
    var filters by remember { mutableStateOf(OfficialSkillCatalogFilters()) }
    var filterDialogVisible by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val roleCatalog = projectSkillRoleCatalog(
        catalog = runtime.catalog,
        presentationCatalog = presentationCatalog,
        query = query,
        filters = filters,
        favoriteIds = favoriteIds,
        recentUses = recentUses,
    )

    OfficialSkillCatalogScreen(
        uiState = OfficialSkillCatalogUiState(
            isLoading = false,
            query = query,
            filters = filters,
            filterDialogVisible = filterDialogVisible,
            section = section,
            visibleSkills = roleCatalog.visibleRoles.map(SkillRoleCardUi::officialSkill),
            allSkills = runtime.catalog.skills,
            totalSkillCount = runtime.catalog.skills.size,
            favoriteIds = favoriteIds,
            recentUses = recentUses,
            roleCatalog = roleCatalog,
            message = message,
        ),
        onEvent = { event ->
            when (event) {
                is OfficialSkillCatalogEvent.SearchChanged -> query = event.value
                is OfficialSkillCatalogEvent.SectionChanged -> sectionName = event.value.name
                is OfficialSkillCatalogEvent.FilterDialogChanged -> filterDialogVisible = event.visible
                is OfficialSkillCatalogEvent.TogglePrimaryType -> {
                    filters = filters.copy(primaryTypes = filters.primaryTypes.toggleRoleFilter(event.value))
                }
                is OfficialSkillCatalogEvent.TogglePrimaryValue -> {
                    filters = filters.copy(primaryValues = filters.primaryValues.toggleRoleFilter(event.value))
                }
                is OfficialSkillCatalogEvent.ToggleUseMode -> {
                    filters = filters.copy(useModes = filters.useModes.toggleRoleFilter(event.value))
                }
                is OfficialSkillCatalogEvent.ToggleNetwork -> {
                    filters = filters.copy(
                        networkRequirements = filters.networkRequirements.toggleRoleFilter(event.value),
                    )
                }
                is OfficialSkillCatalogEvent.ToggleMaterial -> {
                    filters = filters.copy(
                        materialRequirements = filters.materialRequirements.toggleRoleFilter(event.value),
                    )
                }
                is OfficialSkillCatalogEvent.ToggleRisk -> {
                    filters = filters.copy(risks = filters.risks.toggleRoleFilter(event.value))
                }
                is OfficialSkillCatalogEvent.TogglePublication -> {
                    filters = filters.copy(
                        publicationStatuses = filters.publicationStatuses.toggleRoleFilter(event.value),
                    )
                }
                OfficialSkillCatalogEvent.ToggleExecutableOnly -> {
                    filters = filters.copy(executableOnly = !filters.executableOnly)
                }
                OfficialSkillCatalogEvent.ClearFilters -> filters = OfficialSkillCatalogFilters()
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
            }
        },
        modifier = modifier,
    )
}

private fun <T> Set<T>.toggleRoleFilter(value: T): Set<T> =
    if (value in this) this - value else this + value
