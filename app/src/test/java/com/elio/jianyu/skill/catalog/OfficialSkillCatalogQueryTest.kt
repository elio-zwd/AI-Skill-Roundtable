package com.elio.jianyu.skill.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillCatalogQueryTest {
    private val person = skill(
        id = "risk-person",
        name = "风险人物",
        order = 1,
        type = OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
        value = OfficialSkillPrimaryValue.THINKING_EXPANSION,
        risk = OfficialSkillRiskLevel.HIGH_STAKES,
        aliases = listOf("人物别名"),
        domains = listOf("decision_risk"),
        scenarios = listOf("challenge"),
        outputs = listOf("analysis"),
    )
    private val task = skill(
        id = "office-helper",
        name = "办公助手",
        order = 2,
        type = OfficialSkillPrimaryType.TASK_ASSISTANT,
        value = OfficialSkillPrimaryValue.REALITY_SUPPORT,
        risk = OfficialSkillRiskLevel.GENERAL,
        aliases = listOf("Office Helper"),
        domains = listOf("office_operations"),
        scenarios = listOf("write"),
        outputs = listOf("draft"),
    )
    private val workflow = skill(
        id = "research-flow",
        name = "调研流程",
        order = 3,
        type = OfficialSkillPrimaryType.WORKFLOW_CAPABILITY,
        value = OfficialSkillPrimaryValue.BOTH,
        risk = OfficialSkillRiskLevel.SENSITIVE,
        aliases = emptyList(),
        domains = listOf("writing_research"),
        scenarios = listOf("research", "verify"),
        outputs = listOf("review_report"),
    )
    private val catalog = InMemoryOfficialSkillCatalog(listOf(person, task, workflow))

    @Test
    fun search_matchesChineseNameIdAliasDomainScenarioAndOutput() {
        assertEquals(listOf("office-helper"), query("办公").map { it.id })
        assertEquals(listOf("office-helper"), query("OFFICE-HELPER").map { it.id })
        assertEquals(listOf("office-helper"), query("office helper").map { it.id })
        assertEquals(listOf("risk-person"), query("decision_risk").map { it.id })
        assertEquals(listOf("research-flow"), query("verify").map { it.id })
        assertEquals(listOf("research-flow"), query("review_report").map { it.id })
    }

    @Test
    fun search_trimsWhitespaceAndEmptyQueryKeepsStableOrder() {
        assertEquals(listOf("office-helper"), query("  办公  ").map { it.id })
        assertEquals(listOf("risk-person", "office-helper", "research-flow"), query(" ").map { it.id })
        assertTrue(query("不存在").isEmpty())
    }

    @Test
    fun filtersCoverTypeValueRiskExecutionFavoriteAndRecent() {
        val result = OfficialSkillCatalogQuery.apply(
            catalog = catalog,
            query = "",
            filters = OfficialSkillCatalogFilters(
                primaryTypes = setOf(OfficialSkillPrimaryType.TASK_ASSISTANT),
                primaryValues = setOf(OfficialSkillPrimaryValue.REALITY_SUPPORT),
                risks = setOf(OfficialSkillRiskLevel.GENERAL),
                executableOnly = true,
                favoritesOnly = true,
                recentOnly = true,
            ),
            favoriteIds = setOf("office-helper"),
            recentSkillIds = setOf("office-helper"),
        )

        assertEquals(listOf("office-helper"), result.map { it.id })
    }

    @Test
    fun riskDoesNotChangeDefaultPersonOrdering() {
        val sameType = InMemoryOfficialSkillCatalog(
            listOf(
                person.copy(id = "high-risk", defaultOrder = 1, riskLevel = OfficialSkillRiskLevel.HIGH_STAKES),
                person.copy(id = "general", defaultOrder = 2, riskLevel = OfficialSkillRiskLevel.GENERAL),
            ),
        )

        assertEquals(
            listOf("high-risk", "general"),
            OfficialSkillCatalogQuery.apply(sameType).map { it.id },
        )
    }

    private fun query(text: String): List<OfficialSkillDefinition> =
        OfficialSkillCatalogQuery.apply(catalog = catalog, query = text)

    private fun skill(
        id: String,
        name: String,
        order: Int,
        type: OfficialSkillPrimaryType,
        value: OfficialSkillPrimaryValue,
        risk: OfficialSkillRiskLevel,
        aliases: List<String>,
        domains: List<String>,
        scenarios: List<String>,
        outputs: List<String>,
    ) = OfficialSkillDefinition(
        id = id,
        nameZh = name,
        aliases = aliases,
        summary = "$name 简介",
        primaryType = type,
        primaryValue = value,
        domainTags = domains,
        scenarioTags = scenarios,
        inputTags = listOf("question"),
        outputTags = outputs,
        useMode = OfficialSkillUseMode.BOTH,
        networkRequirement = OfficialSkillNetworkRequirement.OPTIONAL,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.OPTIONAL),
        riskLevel = risk,
        publicationStatus = OfficialSkillPublicationStatus.PUBLISHABLE,
        sourceStatus = OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE,
        availability = OfficialSkillAvailability(
            v1Target = true,
            hasAsset = true,
            discoverable = true,
            searchable = true,
            recommendable = true,
            executable = true,
        ),
        typicalScenarios = scenarios,
        inputRequirements = listOf("问题"),
        outputForms = outputs,
        boundaries = listOf("不编造事实"),
        nonExecutableReason = null,
        personDisclaimer = null,
        integrityBoundaries = emptyList(),
        sourceSummary = "测试来源",
        assetPath = "skills/$id/SKILL.md",
        defaultOrder = order,
    )
}
