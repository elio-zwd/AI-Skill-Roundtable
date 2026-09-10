package com.elio.jianyu.ui.screens.skills

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogFilters
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogQuery
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.RecentOfficialSkillUse
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.skill.role.SkillRolePresentationCatalog

/**
 * 【角色】页消费的不可变卡片投影。
 *
 * 角色身份、名称、能力与执行资格始终来自 OfficialSkillCatalog；这里仅叠加
 * Presentation Manifest 的发现分类/编辑精选，以及用户收藏、最近使用和旧头像视觉补充。
 */
internal data class SkillRoleCardUi(
    val skillId: String,
    val name: String,
    val summary: String,
    val primaryType: OfficialSkillPrimaryType,
    val primaryDiscoveryCategory: SkillRoleDiscoveryCategory,
    val isPersonSimulation: Boolean,
    val avatarAssetPath: String?,
    val isFavorite: Boolean,
    val lastUsedAt: Long?,
    val isExecutable: Boolean,
    val featuredOrder: Int?,
    val officialSkill: OfficialSkillDefinition,
)

internal data class SkillRoleCatalogUiState(
    val allRoles: List<SkillRoleCardUi>,
    val visibleRoles: List<SkillRoleCardUi>,
    val featuredRoles: List<SkillRoleCardUi>,
    val recentRoles: List<SkillRoleCardUi>,
    val selectedCategory: SkillRoleDiscoveryCategory?,
)

/**
 * 纯投影函数：44 项 Catalog 是列表基数，legacy Character 只能补头像，不能裁掉角色。
 */
internal fun projectSkillRoleCatalog(
    catalog: OfficialSkillCatalog,
    presentationCatalog: SkillRolePresentationCatalog,
    query: String = "",
    selectedCategory: SkillRoleDiscoveryCategory? = null,
    filters: OfficialSkillCatalogFilters = OfficialSkillCatalogFilters(),
    favoriteIds: Set<String> = emptySet(),
    recentUses: List<RecentOfficialSkillUse> = emptyList(),
    legacyAvatarPaths: Map<String, String> = emptyMap(),
): SkillRoleCatalogUiState {
    val latestUseBySkillId = recentUses
        .asSequence()
        .filter { it.usedAt > 0L }
        .groupBy(RecentOfficialSkillUse::skillId)
        .mapValues { (_, uses) -> uses.maxOf(RecentOfficialSkillUse::usedAt) }

    val allRoles = catalog.skills
        .sortedWith(compareBy(OfficialSkillDefinition::defaultOrder, OfficialSkillDefinition::id))
        .map { skill ->
            val presentation = requireNotNull(presentationCatalog.findBySkillId(skill.id)) {
                "角色展示 Manifest 缺少官方 Skill：${skill.id}"
            }
            SkillRoleCardUi(
                skillId = skill.id,
                name = skill.nameZh,
                summary = skill.summary,
                primaryType = skill.primaryType,
                primaryDiscoveryCategory = presentation.primaryDiscoveryCategory,
                isPersonSimulation = skill.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
                avatarAssetPath = legacyAvatarPaths[skill.id]?.takeIf(String::isNotBlank),
                isFavorite = skill.id in favoriteIds,
                lastUsedAt = latestUseBySkillId[skill.id],
                isExecutable = skill.availability.executable,
                featuredOrder = presentation.featuredOrder,
                officialSkill = skill,
            )
        }

    val roleById = allRoles.associateBy(SkillRoleCardUi::skillId)
    val matchedIds = OfficialSkillCatalogQuery.apply(
        catalog = catalog,
        query = query,
        filters = filters.copy(favoritesOnly = false, recentOnly = false),
        favoriteIds = favoriteIds,
        recentSkillIds = latestUseBySkillId.keys,
    ).mapTo(linkedSetOf(), OfficialSkillDefinition::id)

    val visibleRoles = allRoles.filter { role ->
        role.skillId in matchedIds &&
            (selectedCategory == null || role.primaryDiscoveryCategory == selectedCategory)
    }

    val featuredRoles = allRoles
        .filter { it.featuredOrder != null }
        .sortedBy { requireNotNull(it.featuredOrder) }

    val recentRoles = latestUseBySkillId.entries
        .asSequence()
        .filter { it.key in roleById }
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
        .map { (skillId, _) -> requireNotNull(roleById[skillId]) }
        .toList()

    return SkillRoleCatalogUiState(
        allRoles = allRoles,
        visibleRoles = visibleRoles,
        featuredRoles = featuredRoles,
        recentRoles = recentRoles,
        selectedCategory = selectedCategory,
    )
}
