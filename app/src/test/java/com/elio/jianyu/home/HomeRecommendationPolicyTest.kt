package com.elio.jianyu.home

import com.elio.jianyu.skill.catalog.InMemoryOfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillAvailability
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillSourceStatus
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecommendationPolicyTest {
    @Test
    fun recommendation_hasNonBlankReasonResponsibilityAndStablePositions() {
        val catalog = InMemoryOfficialSkillCatalog(
            listOf(
                skill(
                    id = "career_advisor",
                    value = OfficialSkillPrimaryValue.REALITY_SUPPORT,
                    order = 1,
                    executable = true,
                    domainTags = listOf("career", "plan"),
                ),
                skill(
                    id = "critical_thinker",
                    value = OfficialSkillPrimaryValue.THINKING_EXPANSION,
                    order = 2,
                    executable = true,
                    domainTags = listOf("decision", "challenge"),
                ),
            ),
        )

        val outcome = HomeRecommendationPolicy.recommend(
            catalog,
            HomeRecommendationRequest(
                question = "如何规划职业转型并检查盲区？",
                directions = setOf(
                    ValueDirection.REALITY_SUPPORT,
                    ValueDirection.THINKING_EXPANSION,
                ),
            ),
        )

        val recommendation = (outcome as HomeRecommendationOutcome.Ready).recommendation
        assertEquals(RecommendationMode.MULTI, recommendation.mode)
        assertEquals(listOf(0, 1), recommendation.skills.map { it.position })
        assertTrue(recommendation.skills.all { it.reason.isNotBlank() })
        assertTrue(recommendation.skills.all { it.responsibility.isNotBlank() })
        assertEquals(
            recommendation.skills.size,
            recommendation.skills.map { it.skillId }.distinct().size,
        )
    }

    @Test
    fun highRiskPerson_isNotAutomaticallyRankedBelowOtherwiseEquivalentSkill() {
        val highRiskPerson = skill(
            id = "person_first",
            value = OfficialSkillPrimaryValue.THINKING_EXPANSION,
            order = 1,
            executable = true,
            risk = OfficialSkillRiskLevel.HIGH_STAKES,
            primaryType = OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
            domainTags = listOf("decision"),
        )
        val generalAdvisor = skill(
            id = "advisor_second",
            value = OfficialSkillPrimaryValue.THINKING_EXPANSION,
            order = 2,
            executable = true,
            risk = OfficialSkillRiskLevel.GENERAL,
            domainTags = listOf("decision"),
        )

        val outcome = HomeRecommendationPolicy.recommend(
            InMemoryOfficialSkillCatalog(listOf(highRiskPerson, generalAdvisor)),
            HomeRecommendationRequest(
                question = "帮我检查这个决定",
                directions = setOf(ValueDirection.THINKING_EXPANSION),
            ),
        ) as HomeRecommendationOutcome.Ready

        assertEquals("person_first", outcome.recommendation.skills.first().skillId)
        assertTrue(outcome.recommendation.skills.first().riskDisclosure.isNotBlank())
    }

    @Test
    fun unknownDuplicateOrNonExecutableSelectedSkill_isRejectedForStart() {
        val catalog = InMemoryOfficialSkillCatalog(
            listOf(
                skill("skill-a", OfficialSkillPrimaryValue.BOTH, 1, executable = true),
                skill("skill-b", OfficialSkillPrimaryValue.BOTH, 2, executable = false),
            ),
        )
        val base = HomeRecommendationPolicy.recommend(
            catalog,
            HomeRecommendationRequest("分析这个问题", emptySet()),
        ) as HomeRecommendationOutcome.Ready
        val validSkill = base.recommendation.skills.first { it.skillId == "skill-a" }
        val nonExecutable = base.recommendation.skills.first { it.skillId == "skill-b" }

        assertEquals(
            setOf(HomeRecommendationValidationError.DUPLICATE_SKILL),
            HomeRecommendationPolicy.validateForStart(
                catalog,
                base.recommendation.copy(skills = listOf(validSkill, validSkill.copy(position = 1))),
            ),
        )
        assertEquals(
            setOf(HomeRecommendationValidationError.UNKNOWN_SKILL),
            HomeRecommendationPolicy.validateForStart(
                catalog,
                base.recommendation.copy(
                    skills = listOf(validSkill.copy(skillId = "missing", position = 0)),
                ),
            ),
        )
        assertEquals(
            setOf(HomeRecommendationValidationError.NON_EXECUTABLE_SKILL),
            HomeRecommendationPolicy.validateForStart(
                catalog,
                base.recommendation.copy(
                    skills = listOf(nonExecutable.copy(selected = true, position = 0)),
                ),
            ),
        )
    }

    @Test
    fun catalogWithCandidatesButNoExecutableSkill_returnsNoExecutableState() {
        val outcome = HomeRecommendationPolicy.recommend(
            InMemoryOfficialSkillCatalog(
                listOf(skill("draft-skill", OfficialSkillPrimaryValue.BOTH, 1, executable = false)),
            ),
            HomeRecommendationRequest("需要一个建议", emptySet()),
        )

        assertTrue(outcome is HomeRecommendationOutcome.NoExecutableSkill)
        assertFalse((outcome as HomeRecommendationOutcome.NoExecutableSkill).candidates.isEmpty())
    }

    @Test
    fun emptyRecommendableCatalog_returnsNoSuitableSkill() {
        val hidden = skill(
            id = "hidden",
            value = OfficialSkillPrimaryValue.BOTH,
            order = 1,
            executable = true,
        ).copy(
            availability = OfficialSkillAvailability(
                v1Target = true,
                hasAsset = true,
                discoverable = true,
                searchable = true,
                recommendable = false,
                executable = true,
            ),
        )

        val outcome = HomeRecommendationPolicy.recommend(
            InMemoryOfficialSkillCatalog(listOf(hidden)),
            HomeRecommendationRequest("需要一个建议", emptySet()),
        )

        assertTrue(outcome is HomeRecommendationOutcome.NoSuitableSkill)
    }

    private fun skill(
        id: String,
        value: OfficialSkillPrimaryValue,
        order: Int,
        executable: Boolean,
        risk: OfficialSkillRiskLevel = OfficialSkillRiskLevel.GENERAL,
        primaryType: OfficialSkillPrimaryType = OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR,
        domainTags: List<String> = listOf("analysis"),
    ): OfficialSkillDefinition = OfficialSkillDefinition(
        id = id,
        nameZh = id,
        summary = "帮助用户分析问题并形成可执行输出",
        primaryType = primaryType,
        primaryValue = value,
        domainTags = domainTags,
        scenarioTags = listOf("decide"),
        inputTags = listOf("question"),
        outputTags = listOf("analysis", "plan"),
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
            executable = executable,
        ),
        typicalScenarios = listOf("问题分析"),
        inputRequirements = listOf("明确问题"),
        outputForms = listOf("分析", "行动计划"),
        boundaries = listOf("结果需要用户结合现实条件复核"),
        nonExecutableReason = if (executable) null else "尚未通过执行门禁",
        personDisclaimer = if (primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE) {
            "这是 AI 模拟视角，不代表本人。"
        } else {
            null
        },
        sourceSummary = "测试正式来源",
        assetPath = "skills/$id/SKILL.md",
        defaultOrder = order,
    )
}
