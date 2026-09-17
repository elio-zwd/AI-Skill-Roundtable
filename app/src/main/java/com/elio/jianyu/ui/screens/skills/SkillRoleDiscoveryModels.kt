package com.elio.jianyu.ui.screens.skills

import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 搜索匹配依据（仅来自用户可读字段，不泄漏内部 raw token）。
 */
internal data class SkillRoleMatchEvidence(
    val label: String,
    val text: String,
)

/**
 * 搜索相关性层级（本地确定性规则，不调用大模型）。
 */
internal enum class SkillRoleRelevanceTier {
    EXACT_PREFIX_NAME_OR_ALIAS,  // 1. 名称/别名精确或前缀命中
    CONTAINS_NAME_OR_ALIAS,       // 2. 名称/别名包含命中
    SUMMARY_OR_SCENARIOS,         // 3. summary / typicalScenarios 命中
    OTHER_METADATA,               // 4. 其他可搜索 metadata 命中
    NO_MATCH,                     // 未命中
}

/**
 * 联网需求筛选枚举。
 */
internal enum class RoleDiscoveryNetworkFilter(val label: String) {
    ALL("不限"),
    NOT_NEEDED("可不联网"),
    OPTIONAL("可选联网"),
    REQUIRED("需要联网"),
}

/**
 * 面向用户的角色发现筛选模型（与底层 OfficialSkillCatalogFilters 分离）。
 */
internal data class RoleDiscoveryFilters(
    val categories: Set<SkillRoleDiscoveryCategory> = emptySet(),
    val primaryTypes: Set<OfficialSkillPrimaryType> = emptySet(),
    val networkRequirement: RoleDiscoveryNetworkFilter = RoleDiscoveryNetworkFilter.ALL,
    val materialOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val recentOnly: Boolean = false,
) {
    fun activeCount(includeFavorites: Boolean = true, includeRecent: Boolean = true): Int {
        var count = 0
        if (categories.isNotEmpty()) count += categories.size
        if (primaryTypes.isNotEmpty()) count += primaryTypes.size
        if (networkRequirement != RoleDiscoveryNetworkFilter.ALL) count += 1
        if (materialOnly) count += 1
        if (includeFavorites && favoritesOnly) count += 1
        if (includeRecent && recentOnly) count += 1
        return count
    }

    fun isDefault(includeFavorites: Boolean = true, includeRecent: Boolean = true): Boolean =
        activeCount(includeFavorites, includeRecent) == 0

    fun reset(preserveFavorites: Boolean = false, preserveRecent: Boolean = false): RoleDiscoveryFilters =
        RoleDiscoveryFilters(
            favoritesOnly = if (preserveFavorites) favoritesOnly else false,
            recentOnly = if (preserveRecent) recentOnly else false,
        )
}

/**
 * 最近使用按日期分组枚举。
 */
internal enum class SkillRoleRecentGroup(val title: String) {
    TODAY("今天"),
    YESTERDAY("昨天"),
    EARLIER("更早"),
}

/**
 * 最近使用的分组结果。
 */
internal data class SkillRoleRecentSection(
    val group: SkillRoleRecentGroup,
    val roles: List<SkillRoleCardUi>,
)

/**
 * 纯搜索匹配与相关性打分函数。
 */
internal fun searchSkillRoles(
    roles: List<SkillRoleCardUi>,
    query: String,
): List<SkillRoleCardUi> {
    val trimmed = query.trim().lowercase(Locale.ROOT)
    if (trimmed.isEmpty()) return roles

    return roles.mapNotNull { role ->
        val skill = role.officialSkill
        if (!skill.availability.searchable) return@mapNotNull null

        val nameLower = skill.nameZh.lowercase(Locale.ROOT)
        val aliasesLower = skill.aliases.map { it.lowercase(Locale.ROOT) }

        // 1. 确定 RelevanceTier
        val tier = when {
            nameLower.startsWith(trimmed) || aliasesLower.any { it.startsWith(trimmed) } ->
                SkillRoleRelevanceTier.EXACT_PREFIX_NAME_OR_ALIAS
            nameLower.contains(trimmed) || aliasesLower.any { it.contains(trimmed) } ->
                SkillRoleRelevanceTier.CONTAINS_NAME_OR_ALIAS
            skill.summary.lowercase(Locale.ROOT).contains(trimmed) ||
                skill.typicalScenarios.any { it.lowercase(Locale.ROOT).contains(trimmed) } ->
                SkillRoleRelevanceTier.SUMMARY_OR_SCENARIOS
            skill.outputForms.any { it.lowercase(Locale.ROOT).contains(trimmed) } ||
                skill.id.lowercase(Locale.ROOT).contains(trimmed) ||
                skill.domainTags.any { it.lowercase(Locale.ROOT).contains(trimmed) } ||
                skill.scenarioTags.any { it.lowercase(Locale.ROOT).contains(trimmed) } ||
                skill.outputTags.any { it.lowercase(Locale.ROOT).contains(trimmed) } ->
                SkillRoleRelevanceTier.OTHER_METADATA
            else -> SkillRoleRelevanceTier.NO_MATCH
        }

        if (tier == SkillRoleRelevanceTier.NO_MATCH) return@mapNotNull null

        // 2. 提取证据（最多 2 条，仅限可读字段）
        val evidences = mutableListOf<SkillRoleMatchEvidence>()

        // 匹配别名
        val matchedAlias = skill.aliases.firstOrNull { it.lowercase(Locale.ROOT).contains(trimmed) }
        if (matchedAlias != null && !nameLower.contains(trimmed)) {
            evidences.add(SkillRoleMatchEvidence(label = "匹配别名", text = matchedAlias))
        }

        // 适合场景
        val matchedScenario = skill.typicalScenarios.firstOrNull { it.lowercase(Locale.ROOT).contains(trimmed) }
        if (matchedScenario != null && evidences.size < 2) {
            evidences.add(SkillRoleMatchEvidence(label = "适合场景", text = matchedScenario))
        }

        // 相关输出
        val matchedOutput = skill.outputForms.firstOrNull { it.lowercase(Locale.ROOT).contains(trimmed) }
        if (matchedOutput != null && evidences.size < 2) {
            evidences.add(SkillRoleMatchEvidence(label = "相关输出", text = matchedOutput))
        }

        // 角色定位/简介
        if (evidences.size < 2 && skill.summary.lowercase(Locale.ROOT).contains(trimmed) && evidences.isEmpty()) {
            evidences.add(SkillRoleMatchEvidence(label = "角色定位", text = skill.summary))
        }

        role.copy(
            matchEvidences = evidences.take(2),
            relevanceTier = tier,
        )
    }.sortedWith(
        compareBy<SkillRoleCardUi> { it.relevanceTier.ordinal }
            .thenBy { it.officialSkill.defaultOrder }
            .thenBy { it.skillId }
    )
}

/**
 * 纯筛选函数。
 */
internal fun applyDiscoveryFilters(
    roles: List<SkillRoleCardUi>,
    filters: RoleDiscoveryFilters,
): List<SkillRoleCardUi> {
    return roles.filter { role ->
        val skill = role.officialSkill

        // 发现分类
        if (filters.categories.isNotEmpty() && role.primaryDiscoveryCategory !in filters.categories) {
            return@filter false
        }

        // 角色类型
        if (filters.primaryTypes.isNotEmpty() && skill.primaryType !in filters.primaryTypes) {
            return@filter false
        }

        // 联网需求
        when (filters.networkRequirement) {
            RoleDiscoveryNetworkFilter.ALL -> {}
            RoleDiscoveryNetworkFilter.NOT_NEEDED ->
                if (skill.networkRequirement != OfficialSkillNetworkRequirement.NOT_NEEDED) return@filter false
            RoleDiscoveryNetworkFilter.OPTIONAL ->
                if (skill.networkRequirement != OfficialSkillNetworkRequirement.OPTIONAL) return@filter false
            RoleDiscoveryNetworkFilter.REQUIRED ->
                if (skill.networkRequirement != OfficialSkillNetworkRequirement.REQUIRED) return@filter false
        }

        // 资料使用（匹配非 NONE 的资料需求）
        if (filters.materialOnly && skill.materialRequirements.all { it == OfficialSkillMaterialRequirement.NONE }) {
            return@filter false
        }

        // 已收藏
        if (filters.favoritesOnly && !role.isFavorite) {
            return@filter false
        }

        // 最近使用
        if (filters.recentOnly && (role.lastUsedAt == null || role.lastUsedAt <= 0L)) {
            return@filter false
        }

        true
    }
}

/**
 * 按本地日期分组最近使用角色。
 */
internal fun groupRecentRolesByDate(
    roles: List<SkillRoleCardUi>,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<SkillRoleRecentSection> {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val yesterday = today.minusDays(1)

    val todayList = mutableListOf<SkillRoleCardUi>()
    val yesterdayList = mutableListOf<SkillRoleCardUi>()
    val earlierList = mutableListOf<SkillRoleCardUi>()

    roles.forEach { role ->
        val lastUsed = role.lastUsedAt ?: return@forEach
        val usedDate = Instant.ofEpochMilli(lastUsed).atZone(zoneId).toLocalDate()
        when {
            usedDate.isEqual(today) -> todayList.add(role)
            usedDate.isEqual(yesterday) -> yesterdayList.add(role)
            else -> earlierList.add(role)
        }
    }

    return buildList {
        if (todayList.isNotEmpty()) {
            add(SkillRoleRecentSection(SkillRoleRecentGroup.TODAY, todayList))
        }
        if (yesterdayList.isNotEmpty()) {
            add(SkillRoleRecentSection(SkillRoleRecentGroup.YESTERDAY, yesterdayList))
        }
        if (earlierList.isNotEmpty()) {
            add(SkillRoleRecentSection(SkillRoleRecentGroup.EARLIER, earlierList))
        }
    }
}
