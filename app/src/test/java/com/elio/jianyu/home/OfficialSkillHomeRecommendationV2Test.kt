package com.elio.jianyu.home

import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillHomeRecommendationV2Test {
    private val catalog by lazy {
        val result = OfficialSkillCatalogParser.parse(
            assetFile("official_skill_catalog_v1.json").readText(),
            assetFile("official_skill_execution_manifest_v2.json").readText(),
        )
        (result as OfficialSkillCatalogLoadResult.Success).catalog
    }

    @Test
    fun everyOfficialSkillCanBeManuallySelectedIntoAValidPendingRoster() {
        assertEquals(44, catalog.skills.size)

        catalog.skills.forEach { definition ->
            val recommendation = HomeRecommendation(
                questionSummary = "验证 ${definition.id} 可手动选择",
                directions = emptySet(),
                mode = RecommendationMode.SINGLE,
                modeReason = "用户手动选择一个官方 Skill。",
                skills = listOf(definition.toSelectedSkill()),
                expectedOutput = definition.outputForms.first(),
                source = RecommendationSource.LOCAL_CATALOG,
            )

            assertTrue(
                "${definition.id} 不能进入合法待确认阵容",
                HomeRecommendationPolicy.validateForStart(catalog, recommendation).isEmpty(),
            )
        }
    }

    @Test
    fun productionRecommendationIsNotLimitedToTheHistoricalFirstFour() {
        val historical = setOf(
            "study-planner",
            "meeting-to-action",
            "report-proposal-writer",
            "research-fact-checker",
        )
        val queries = listOf(
            "合同条款和法律风险需要检查",
            "分析人工智能训练数据和错误样本",
            "梳理未公开专利交底的脱敏摘要",
            "把真实表达改得自然但不改变事实",
        )

        val recommendedIds = queries.flatMap { question ->
            val outcome = HomeRecommendationPolicy.recommend(
                catalog,
                HomeRecommendationRequest(question, emptySet()),
            ) as HomeRecommendationOutcome.Ready
            outcome.recommendation.skills.map(RecommendedSkill::skillId)
        }.toSet()

        assertTrue(recommendedIds.any { it !in historical })
    }

    @Test
    fun allPrimaryTypesAndValuesRemainRecommendableWithoutRiskOrFameSuppression() {
        val recommendable = catalog.skills.filter { it.availability.recommendable }

        assertEquals(44, recommendable.size)
        assertEquals(OfficialSkillPrimaryType.entries.toSet(), recommendable.map { it.primaryType }.toSet())
        assertEquals(OfficialSkillPrimaryValue.entries.toSet(), recommendable.map { it.primaryValue }.toSet())
        assertFalse(recommendable.any { !it.availability.executable })
        assertTrue(
            recommendable.filter { it.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE }
                .all { it.personDisclaimer?.contains("不代表本人") == true },
        )
    }

    private fun OfficialSkillDefinition.toSelectedSkill(): RecommendedSkill = RecommendedSkill(
        skillId = id,
        displayName = nameZh,
        responsibility = "执行 ${nameZh} 的正式职责",
        reason = "用户手动选择稳定官方 ID。",
        risk = when (riskLevel.name) {
            "GENERAL" -> RecommendationRisk.GENERAL
            "SENSITIVE" -> RecommendationRisk.SENSITIVE
            "HIGH_STAKES" -> RecommendationRisk.HIGH_STAKES
            else -> RecommendationRisk.URGENT
        },
        riskDisclosure = boundaries.joinToString("；"),
        freshnessDisclosure = "动态事实按日期核验。",
        networkRequirement = networkRequirement.name,
        materialRequirement = materialRequirements.joinToString(",") { it.name },
        expectedOutput = outputForms.first(),
        executable = availability.executable,
        selected = true,
        position = 0,
        isPersonPerspective = primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
        requiresHighStakesConfirmation = riskLevel.name in setOf("HIGH_STAKES", "URGENT"),
        requiresNetworkAuthorization = networkRequirement.name == "REQUIRED",
        requiresMaterial = materialRequirements.any { it.name == "REQUIRED" },
        requiresMaterialAuthorization = materialRequirements.any { it.name == "USER_AUTHORIZED" },
        requiresSensitiveMaterialConfirmation = materialRequirements.any { it.name == "SENSITIVE" },
        prohibitsExternalMaterial = networkRequirement.name == "PROHIBITED_FOR_MATERIAL",
    )

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
