package com.elio.jianyu.ui.screens.skills

import com.elio.jianyu.skill.catalog.InMemoryOfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillAvailability
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillSourceStatus
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.skill.role.SkillRolePresentationCatalog
import com.elio.jianyu.skill.role.SkillRolePresentationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SkillRoleDiscoveryLogicTest {

    private val skill1 = testSkill(
        id = "career-coach",
        nameZh = "规划教练",
        aliases = listOf("职业顾问", "生涯规划"),
        summary = "协助进行职业决策和成长规划",
        typicalScenarios = listOf("职业选择与转岗规划", "升职加薪谈判"),
        outputForms = listOf("行动方案", "阶段规划"),
        domainTags = listOf("career_workplace"),
        primaryType = OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR,
        networkRequirement = OfficialSkillNetworkRequirement.NOT_NEEDED,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.OPTIONAL),
        defaultOrder = 1,
    )

    private val skill2 = testSkill(
        id = "feynman-technique",
        nameZh = "理查德·费曼",
        aliases = listOf("费曼思维", "以教促学"),
        summary = "用最简语言解释复杂概念并寻找知识盲区",
        typicalScenarios = listOf("学习新概念", "准备技术分享"),
        outputForms = listOf("浅显解释", "类比模型"),
        domainTags = listOf("thinking_methods"),
        primaryType = OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
        networkRequirement = OfficialSkillNetworkRequirement.OPTIONAL,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.NONE),
        defaultOrder = 2,
    )

    private val skill3 = testSkill(
        id = "legal-document-reviewer",
        nameZh = "法务合同审查员",
        aliases = listOf("合同法务"),
        summary = "识别合同潜在风险与不平等条款",
        typicalScenarios = listOf("租房合同排查", "劳动合同审阅"),
        outputForms = listOf("风险清单", "修改建议"),
        domainTags = listOf("office_operations"),
        primaryType = OfficialSkillPrimaryType.TASK_ASSISTANT,
        networkRequirement = OfficialSkillNetworkRequirement.REQUIRED,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.REQUIRED),
        defaultOrder = 3,
    )

    private val catalog: OfficialSkillCatalog = InMemoryOfficialSkillCatalog(listOf(skill1, skill2, skill3))

    private val presentationCatalog = SkillRolePresentationCatalog(
        entries = listOf(
            SkillRolePresentationEntry("career-coach", SkillRoleDiscoveryCategory.CAREER, 1),
            SkillRolePresentationEntry("feynman-technique", SkillRoleDiscoveryCategory.THINKING, 2),
            SkillRolePresentationEntry("legal-document-reviewer", SkillRoleDiscoveryCategory.OFFICE_TASKS, null),
        ),
    )

    @Test
    fun searchRanksExactPrefixHigherThanContainsAndSummary() {
        val baseCards = projectSkillRoleCatalog(
            catalog = catalog,
            presentationCatalog = presentationCatalog,
        ).allRoles

        // "规划" is exact prefix of "规划教练", contains in alias "生涯规划", and also in summary of skill1
        val results = searchSkillRoles(baseCards, "规划")
        assertEquals(1, results.size)
        assertEquals("career-coach", results.first().skillId)
        assertEquals(SkillRoleRelevanceTier.EXACT_PREFIX_NAME_OR_ALIAS, results.first().relevanceTier)

        // "复杂概念" is only in summary of skill2
        val resultsSummary = searchSkillRoles(baseCards, "复杂概念")
        assertEquals(1, resultsSummary.size)
        assertEquals("feynman-technique", resultsSummary.first().skillId)
        assertEquals(SkillRoleRelevanceTier.SUMMARY_OR_SCENARIOS, resultsSummary.first().relevanceTier)
    }

    @Test
    fun searchProducesZeroToTwoUserReadableEvidencesWithoutRawTokens() {
        val baseCards = projectSkillRoleCatalog(
            catalog = catalog,
            presentationCatalog = presentationCatalog,
        ).allRoles

        val results = searchSkillRoles(baseCards, "转岗")
        assertEquals(1, results.size)
        val card = results.first()
        assertTrue(card.matchEvidences.isNotEmpty())
        assertTrue(card.matchEvidences.size <= 2)
        // Check that evidence label is user readable and no raw snake_case tag leaked
        card.matchEvidences.forEach { evidence ->
            assertFalse(evidence.text.contains("career_workplace"))
            assertFalse(evidence.text.contains("_"))
            assertTrue(evidence.label.isNotBlank())
        }
    }

    @Test
    fun filtersApplyCategoriesAndAttributesCorrectly() {
        val baseCards = projectSkillRoleCatalog(
            catalog = catalog,
            presentationCatalog = presentationCatalog,
            favoriteIds = setOf("career-coach"),
        ).allRoles

        // Category filter
        val careerOnly = applyDiscoveryFilters(
            roles = baseCards,
            filters = RoleDiscoveryFilters(categories = setOf(SkillRoleDiscoveryCategory.CAREER)),
        )
        assertEquals(listOf("career-coach"), careerOnly.map { it.skillId })

        // PrimaryType filter
        val personOnly = applyDiscoveryFilters(
            roles = baseCards,
            filters = RoleDiscoveryFilters(primaryTypes = setOf(OfficialSkillPrimaryType.PERSON_PERSPECTIVE)),
        )
        assertEquals(listOf("feynman-technique"), personOnly.map { it.skillId })

        // Material filter (only skill1 and skill3 require/allow materials, skill2 has NONE)
        val materialOnly = applyDiscoveryFilters(
            roles = baseCards,
            filters = RoleDiscoveryFilters(materialOnly = true),
        )
        assertEquals(listOf("career-coach", "legal-document-reviewer"), materialOnly.map { it.skillId })

        // Favorites filter
        val favOnly = applyDiscoveryFilters(
            roles = baseCards,
            filters = RoleDiscoveryFilters(favoritesOnly = true),
        )
        assertEquals(listOf("career-coach"), favOnly.map { it.skillId })
    }

    @Test
    fun recentDateGroupingSeparatesTodayYesterdayAndEarlier() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.of(2026, 9, 17)
        val todayTime = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterdayTime = today.minusDays(1).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val earlierTime = today.minusDays(5).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

        val baseCards = projectSkillRoleCatalog(
            catalog = catalog,
            presentationCatalog = presentationCatalog,
            recentUses = listOf(
                com.elio.jianyu.skill.catalog.RecentOfficialSkillUse("career-coach", todayTime),
                com.elio.jianyu.skill.catalog.RecentOfficialSkillUse("feynman-technique", yesterdayTime),
                com.elio.jianyu.skill.catalog.RecentOfficialSkillUse("legal-document-reviewer", earlierTime),
            ),
        ).recentRoles

        val grouped = groupRecentRolesByDate(baseCards, nowMillis = todayTime, zoneId = zone)
        assertEquals(3, grouped.size)
        assertEquals(SkillRoleRecentGroup.TODAY, grouped[0].group)
        assertEquals(listOf("career-coach"), grouped[0].roles.map { it.skillId })

        assertEquals(SkillRoleRecentGroup.YESTERDAY, grouped[1].group)
        assertEquals(listOf("feynman-technique"), grouped[1].roles.map { it.skillId })

        assertEquals(SkillRoleRecentGroup.EARLIER, grouped[2].group)
        assertEquals(listOf("legal-document-reviewer"), grouped[2].roles.map { it.skillId })
    }

    private fun testSkill(
        id: String,
        nameZh: String,
        aliases: List<String>,
        summary: String,
        typicalScenarios: List<String>,
        outputForms: List<String>,
        domainTags: List<String>,
        primaryType: OfficialSkillPrimaryType,
        networkRequirement: OfficialSkillNetworkRequirement,
        materialRequirements: List<OfficialSkillMaterialRequirement>,
        defaultOrder: Int,
    ) = OfficialSkillDefinition(
        id = id,
        nameZh = nameZh,
        aliases = aliases,
        summary = summary,
        primaryType = primaryType,
        primaryValue = OfficialSkillPrimaryValue.REALITY_SUPPORT,
        domainTags = domainTags,
        scenarioTags = listOf("test"),
        inputTags = listOf("test"),
        outputTags = listOf("test"),
        useMode = OfficialSkillUseMode.BOTH,
        networkRequirement = networkRequirement,
        materialRequirements = materialRequirements,
        riskLevel = OfficialSkillRiskLevel.GENERAL,
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
        typicalScenarios = typicalScenarios,
        inputRequirements = listOf("输入"),
        outputForms = outputForms,
        boundaries = listOf("边界"),
        nonExecutableReason = null,
        personDisclaimer = null,
        integrityBoundaries = emptyList(),
        sourceSummary = "来源",
        assetPath = "skills/$id/SKILL.md",
        defaultOrder = defaultOrder,
    )
}
