package com.elio.jianyu.ui.screens.skills

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogFilters
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogQuery
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.RecentOfficialSkillUse
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.skill.role.SkillRolePresentationCatalog
import com.elio.jianyu.skill.role.officialSkillVisualAssetPath

/**
 * 【角色】页消费的不可变卡片投影。
 *
 * 角色身份、名称、能力与执行资格始终来自 OfficialSkillCatalog；这里仅叠加
 * Presentation Manifest 的发现分类/编辑精选，以及用户收藏、最近使用和正式视觉路径。
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
    val matchEvidences: List<SkillRoleMatchEvidence> = emptyList(),
    val relevanceTier: SkillRoleRelevanceTier = SkillRoleRelevanceTier.NO_MATCH,
)

internal data class SkillRoleCatalogUiState(
    val allRoles: List<SkillRoleCardUi>,
    val visibleRoles: List<SkillRoleCardUi>,
    val featuredRoles: List<SkillRoleCardUi>,
    val recentRoles: List<SkillRoleCardUi>,
    val selectedCategory: SkillRoleDiscoveryCategory?,
)

private val POST_KARPATHY_ALL_ROLE_IDS = listOf("zhang_xuefeng", "elon_musk")

/**
 * “全部角色”的展示顺序是 UI 编辑顺序；不改 OfficialSkillCatalog.defaultOrder，
 * 避免把目录调整扩散到执行 Manifest 与其他依赖 canonical 顺序的链路。
 */
private fun List<OfficialSkillDefinition>.orderedForAllRoles(): List<OfficialSkillDefinition> {
    val baseOrder = sortedWith(compareBy(OfficialSkillDefinition::defaultOrder, OfficialSkillDefinition::id))
    val movedRoles = POST_KARPATHY_ALL_ROLE_IDS.mapNotNull { id ->
        baseOrder.firstOrNull { it.id == id }
    }
    if (movedRoles.size != POST_KARPATHY_ALL_ROLE_IDS.size) return baseOrder

    val movedIds = POST_KARPATHY_ALL_ROLE_IDS.toSet()
    val remaining = baseOrder.filterNot { it.id in movedIds }
    val anchorIndex = remaining.indexOfFirst { it.id == "andrej_karpathy" }
    if (anchorIndex < 0) return baseOrder

    return buildList(baseOrder.size) {
        addAll(remaining.take(anchorIndex + 1))
        addAll(movedRoles)
        addAll(remaining.drop(anchorIndex + 1))
    }
}

/** 纯投影函数：44 项 Catalog 是列表基数，视觉路径由官方 Skill 类型统一解析。 */
internal fun projectSkillRoleCatalog(
    catalog: OfficialSkillCatalog,
    presentationCatalog: SkillRolePresentationCatalog,
    query: String = "",
    selectedCategory: SkillRoleDiscoveryCategory? = null,
    filters: OfficialSkillCatalogFilters = OfficialSkillCatalogFilters(),
    favoriteIds: Set<String> = emptySet(),
    recentUses: List<RecentOfficialSkillUse> = emptyList(),
): SkillRoleCatalogUiState {
    val latestUseBySkillId = recentUses
        .asSequence()
        .filter { it.usedAt > 0L }
        .groupBy(RecentOfficialSkillUse::skillId)
        .mapValues { (_, uses) -> uses.maxOf(RecentOfficialSkillUse::usedAt) }

    val allRoles = catalog.skills
        .orderedForAllRoles()
        .map { skill ->
            val presentation = requireNotNull(presentationCatalog.findBySkillId(skill.id)) {
                "角色展示 Manifest 缺少官方 Skill：" + skill.id
            }
            SkillRoleCardUi(
                skillId = skill.id,
                name = skill.nameZh,
                summary = skill.summary,
                primaryType = skill.primaryType,
                primaryDiscoveryCategory = presentation.primaryDiscoveryCategory,
                isPersonSimulation = skill.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
                avatarAssetPath = officialSkillVisualAssetPath(skill),
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
