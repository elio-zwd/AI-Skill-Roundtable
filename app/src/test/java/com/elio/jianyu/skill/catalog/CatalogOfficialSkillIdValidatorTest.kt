package com.elio.jianyu.skill.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogOfficialSkillIdValidatorTest {
    private val riskyOfficial = testSkill(
        id = "risky-official",
        executable = true,
        risk = OfficialSkillRiskLevel.HIGH_STAKES,
        publication = OfficialSkillPublicationStatus.NOTICE_AND_DISCLOSURE_REQUIRED,
    )
    private val gatedOfficial = testSkill(
        id = "gated-official",
        executable = false,
        risk = OfficialSkillRiskLevel.GENERAL,
        publication = OfficialSkillPublicationStatus.BLOCKED_REWORK,
    )
    private val validator = CatalogOfficialSkillIdValidator(
        InMemoryOfficialSkillCatalog(listOf(riskyOfficial, gatedOfficial)),
    )

    @Test
    fun acceptsOfficialIdentityRegardlessOfRiskOrExecutionState() = runBlocking {
        assertTrue(validator.isValid("risky-official"))
        assertTrue(validator.isValid("gated-official"))
    }

    @Test
    fun rejectsUnknownBlankAndWhitespaceIds() = runBlocking {
        assertFalse(validator.isValid("unknown"))
        assertFalse(validator.isValid(""))
        assertFalse(validator.isValid("   "))
        assertFalse(validator.isValid(" risky-official "))
    }

    private fun testSkill(
        id: String,
        executable: Boolean,
        risk: OfficialSkillRiskLevel,
        publication: OfficialSkillPublicationStatus,
    ) = OfficialSkillDefinition(
        id = id,
        nameZh = id,
        aliases = emptyList(),
        summary = id,
        primaryType = OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
        primaryValue = OfficialSkillPrimaryValue.THINKING_EXPANSION,
        domainTags = listOf("decision_risk"),
        scenarioTags = listOf("challenge"),
        inputTags = listOf("question"),
        outputTags = listOf("analysis"),
        useMode = OfficialSkillUseMode.BOTH,
        networkRequirement = OfficialSkillNetworkRequirement.OPTIONAL,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.OPTIONAL),
        riskLevel = risk,
        publicationStatus = publication,
        sourceStatus = OfficialSkillSourceStatus.EXISTING_ASSET_REVIEW_REQUIRED,
        availability = OfficialSkillAvailability(
            v1Target = true,
            hasAsset = true,
            discoverable = true,
            searchable = true,
            recommendable = true,
            executable = executable,
        ),
        typicalScenarios = listOf("测试"),
        inputRequirements = listOf("问题"),
        outputForms = listOf("分析"),
        boundaries = listOf("测试边界"),
        nonExecutableReason = if (executable) null else "等待门禁",
        personDisclaimer = "这是 AI 模拟视角，不代表本人。",
        integrityBoundaries = emptyList(),
        sourceSummary = "测试",
        assetPath = "skills/$id/SKILL.md",
        defaultOrder = 1,
    )
}
