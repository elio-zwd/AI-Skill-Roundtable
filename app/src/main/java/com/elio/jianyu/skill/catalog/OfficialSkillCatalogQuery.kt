package com.elio.jianyu.skill.catalog

import java.util.Locale

data class OfficialSkillCatalogFilters(
    val primaryTypes: Set<OfficialSkillPrimaryType> = emptySet(),
    val primaryValues: Set<OfficialSkillPrimaryValue> = emptySet(),
    val useModes: Set<OfficialSkillUseMode> = emptySet(),
    val networkRequirements: Set<OfficialSkillNetworkRequirement> = emptySet(),
    val materialRequirements: Set<OfficialSkillMaterialRequirement> = emptySet(),
    val risks: Set<OfficialSkillRiskLevel> = emptySet(),
    val publicationStatuses: Set<OfficialSkillPublicationStatus> = emptySet(),
    val executableOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    val recentOnly: Boolean = false,
)

object OfficialSkillCatalogQuery {
    fun apply(
        catalog: OfficialSkillCatalog,
        query: String = "",
        filters: OfficialSkillCatalogFilters = OfficialSkillCatalogFilters(),
        favoriteIds: Set<String> = emptySet(),
        recentSkillIds: Set<String> = emptySet(),
    ): List<OfficialSkillDefinition> {
        val normalizedQuery = query.normalizeForSearch()
        return catalog.skills.asSequence()
            .filter { it.availability.discoverable }
            .filter { skill -> normalizedQuery.isEmpty() || skill.matches(normalizedQuery) }
            .filter { skill -> filters.primaryTypes.isEmpty() || skill.primaryType in filters.primaryTypes }
            .filter { skill -> skill.matchesPrimaryValue(filters.primaryValues) }
            .filter { skill -> filters.useModes.isEmpty() || skill.useMode in filters.useModes }
            .filter { skill ->
                filters.networkRequirements.isEmpty() ||
                    skill.networkRequirement in filters.networkRequirements
            }
            .filter { skill ->
                filters.materialRequirements.isEmpty() ||
                    skill.materialRequirements.any(filters.materialRequirements::contains)
            }
            .filter { skill -> filters.risks.isEmpty() || skill.riskLevel in filters.risks }
            .filter { skill ->
                filters.publicationStatuses.isEmpty() ||
                    skill.publicationStatus in filters.publicationStatuses
            }
            .filter { skill -> !filters.executableOnly || skill.availability.executable }
            .filter { skill -> !filters.favoritesOnly || skill.id in favoriteIds }
            .filter { skill -> !filters.recentOnly || skill.id in recentSkillIds }
            .sortedWith(compareBy(OfficialSkillDefinition::defaultOrder, OfficialSkillDefinition::id))
            .toList()
    }

    private fun OfficialSkillDefinition.matches(normalizedQuery: String): Boolean {
        if (!availability.searchable) return false
        val searchableValues = buildList {
            add(id)
            add(nameZh)
            addAll(aliases)
            add(summary)
            addAll(domainTags)
            addAll(scenarioTags)
            addAll(outputTags)
            addAll(typicalScenarios)
        }
        return searchableValues.any { it.normalizeForSearch().contains(normalizedQuery) }
    }

    private fun OfficialSkillDefinition.matchesPrimaryValue(
        selected: Set<OfficialSkillPrimaryValue>,
    ): Boolean {
        if (selected.isEmpty()) return true
        if (primaryValue in selected) return true
        return primaryValue == OfficialSkillPrimaryValue.BOTH &&
            selected.any {
                it == OfficialSkillPrimaryValue.REALITY_SUPPORT ||
                    it == OfficialSkillPrimaryValue.THINKING_EXPANSION
            }
    }

    private fun String.normalizeForSearch(): String = trim().lowercase(Locale.ROOT)
}
