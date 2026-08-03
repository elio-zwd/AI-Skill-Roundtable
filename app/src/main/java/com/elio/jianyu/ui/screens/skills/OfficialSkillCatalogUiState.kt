package com.elio.jianyu.ui.screens.skills

import com.elio.jianyu.data.OfficialSkillCombinationSnapshot
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogFilters
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode
import com.elio.jianyu.skill.catalog.RecentOfficialSkillUse

internal object OfficialSkillCatalogTestTags {
    const val ROOT = "official_skill_catalog"
    const val LOADING = "official_skill_catalog_loading"
    const val ERROR = "official_skill_catalog_error"
    const val SEARCH = "official_skill_catalog_search"
    const val FILTER_BUTTON = "official_skill_catalog_filter_button"
    const val FILTER_DIALOG = "official_skill_catalog_filter_dialog"
    const val LIST = "official_skill_catalog_list"
    const val EMPTY = "official_skill_catalog_empty"
    const val DETAIL = "official_skill_catalog_detail"
    const val FAVORITES_TAB = "official_skill_catalog_favorites"
    const val RECENT_TAB = "official_skill_catalog_recent"
    const val COMBINATIONS_TAB = "official_skill_catalog_combinations"
    const val COMBINATION_EDITOR = "official_skill_combination_editor"
    const val COMBINATION_ERROR = "official_skill_combination_error"

    fun skill(skillId: String) = "official_skill_$skillId"
    fun skillStatus(skillId: String) = "official_skill_status_$skillId"
    fun favorite(skillId: String) = "official_skill_favorite_$skillId"
    fun combination(combinationId: String) = "official_skill_combination_$combinationId"
}

internal enum class OfficialSkillCatalogSection {
    DISCOVER,
    FAVORITES,
    RECENT,
    COMBINATIONS,
}

internal data class OfficialSkillCombinationMemberEditorState(
    val skillId: String,
    val defaultResponsibility: String = "",
)

internal data class OfficialSkillCombinationEditorState(
    val combinationId: String,
    val name: String,
    val members: List<OfficialSkillCombinationMemberEditorState>,
    val createdAt: Long,
    val expectedUpdatedAt: Long?,
    val isSaving: Boolean = false,
    val validationMessage: String? = null,
)

internal data class OfficialSkillCatalogUiState(
    val isLoading: Boolean = true,
    val catalogError: String? = null,
    val query: String = "",
    val filters: OfficialSkillCatalogFilters = OfficialSkillCatalogFilters(),
    val filterDialogVisible: Boolean = false,
    val section: OfficialSkillCatalogSection = OfficialSkillCatalogSection.DISCOVER,
    val visibleSkills: List<OfficialSkillDefinition> = emptyList(),
    val allSkills: List<OfficialSkillDefinition> = visibleSkills,
    val totalSkillCount: Int = 0,
    val favoriteIds: Set<String> = emptySet(),
    val recentUses: List<RecentOfficialSkillUse> = emptyList(),
    val selectedSkill: OfficialSkillDefinition? = null,
    val combinations: List<OfficialSkillCombinationSnapshot> = emptyList(),
    val combinationsLoading: Boolean = false,
    val combinationError: String? = null,
    val combinationEditor: OfficialSkillCombinationEditorState? = null,
    val message: String? = null,
)

internal sealed interface OfficialSkillCatalogEvent {
    data class SearchChanged(val value: String) : OfficialSkillCatalogEvent
    data class SectionChanged(val value: OfficialSkillCatalogSection) : OfficialSkillCatalogEvent
    data class FilterDialogChanged(val visible: Boolean) : OfficialSkillCatalogEvent
    data class TogglePrimaryType(val value: OfficialSkillPrimaryType) : OfficialSkillCatalogEvent
    data class TogglePrimaryValue(val value: OfficialSkillPrimaryValue) : OfficialSkillCatalogEvent
    data class ToggleUseMode(val value: OfficialSkillUseMode) : OfficialSkillCatalogEvent
    data class ToggleNetwork(val value: OfficialSkillNetworkRequirement) : OfficialSkillCatalogEvent
    data class ToggleMaterial(val value: OfficialSkillMaterialRequirement) : OfficialSkillCatalogEvent
    data class ToggleRisk(val value: OfficialSkillRiskLevel) : OfficialSkillCatalogEvent
    data class TogglePublication(val value: OfficialSkillPublicationStatus) : OfficialSkillCatalogEvent
    data object ToggleExecutableOnly : OfficialSkillCatalogEvent
    data object ClearFilters : OfficialSkillCatalogEvent
    data class OpenDetail(val skillId: String) : OfficialSkillCatalogEvent
    data object DismissDetail : OfficialSkillCatalogEvent
    data class ToggleFavorite(val skillId: String) : OfficialSkillCatalogEvent
    data class UseSkill(val skillId: String) : OfficialSkillCatalogEvent
    data class CreateCombination(val seedSkillId: String? = null) : OfficialSkillCatalogEvent
    data class EditCombination(val combinationId: String) : OfficialSkillCatalogEvent
    data class DeleteCombination(val combinationId: String) : OfficialSkillCatalogEvent
    data object DismissCombinationEditor : OfficialSkillCatalogEvent
    data class CombinationNameChanged(val value: String) : OfficialSkillCatalogEvent
    data class ToggleCombinationMember(val skillId: String) : OfficialSkillCatalogEvent
    data class MoveCombinationMember(val skillId: String, val offset: Int) : OfficialSkillCatalogEvent
    data class CombinationResponsibilityChanged(
        val skillId: String,
        val value: String,
    ) : OfficialSkillCatalogEvent
    data object SaveCombination : OfficialSkillCatalogEvent
    data object DismissMessage : OfficialSkillCatalogEvent
}

internal fun OfficialSkillDefinition.statusLabels(): List<String> = buildList {
    if (availability.discoverable) add("可发现")
    if (availability.recommendable) add("可推荐")
    if (availability.executable) {
        add("可执行")
    } else {
        add("待门禁")
    }
    when (publicationStatus) {
        OfficialSkillPublicationStatus.BLOCKED_REWORK -> add("阻断重构")
        OfficialSkillPublicationStatus.ORIGINALITY_OR_LICENSE_REVIEW -> add("许可或原创性待核验")
        OfficialSkillPublicationStatus.NOTICE_AND_DISCLOSURE_REQUIRED -> add("待补声明与生产核验")
        OfficialSkillPublicationStatus.PUBLISHABLE -> add("可发布")
    }
}

internal fun OfficialSkillDefinition.listBoundaryHint(): String? = when (id) {
    "original-expression-naturalizer" -> "只整理真实内容；不规避检测、不伪造事实或经历。"
    "office-document-productivity" -> "只生成文档内容，不控制桌面 Office 或本地软件。"
    else -> personDisclaimer
}
