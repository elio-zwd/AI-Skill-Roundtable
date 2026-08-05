package com.elio.jianyu.skill.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillExecutionEligibilityTest {
    @Test
    fun eligibleSkillPassesAllMetadataAssetAndContentGates() {
        val skill = validSkill()
        val eligibility = eligibility(skill, validSkillBody())

        val result = eligibility.audit(skill.id)

        assertTrue(result.eligible)
        assertEquals(
            listOf(OfficialSkillExecutionEligibilityCode.ELIGIBLE),
            result.issues.map { it.code },
        )
    }

    @Test
    fun auditReturnsStableOrderedMetadataAndAssetFailures() {
        val skill = validSkill().copy(
            availability = validSkill().availability.copy(
                hasAsset = false,
                recommendable = false,
            ),
            publicationStatus = OfficialSkillPublicationStatus.ORIGINALITY_OR_LICENSE_REVIEW,
            sourceStatus = OfficialSkillSourceStatus.IMPLEMENTATION_SOURCE_PENDING,
            nonExecutableReason = "仍待发布",
            assetPath = "../private/SKILL.md",
        )
        val eligibility = eligibility(skill, assetResult = OfficialSkillAssetReadResult.Missing)

        val result = eligibility.audit(skill.id)

        assertFalse(result.eligible)
        assertEquals(
            listOf(
                OfficialSkillExecutionEligibilityCode.NOT_RECOMMENDABLE,
                OfficialSkillExecutionEligibilityCode.MISSING_ASSET,
                OfficialSkillExecutionEligibilityCode.INVALID_ASSET_PATH,
                OfficialSkillExecutionEligibilityCode.PUBLICATION_NOT_READY,
                OfficialSkillExecutionEligibilityCode.SOURCE_NOT_VERIFIED,
                OfficialSkillExecutionEligibilityCode.NON_EXECUTABLE_REASON_PRESENT,
            ),
            result.issues.map { it.code },
        )
    }

    @Test
    fun auditDistinguishesMissingUnreadableAndEmptyAssets() {
        val skill = validSkill()

        assertEquals(
            OfficialSkillExecutionEligibilityCode.MISSING_ASSET,
            eligibility(skill, assetResult = OfficialSkillAssetReadResult.Missing)
                .audit(skill.id).issues.first().code,
        )
        assertEquals(
            OfficialSkillExecutionEligibilityCode.ASSET_UNREADABLE,
            eligibility(skill, assetResult = OfficialSkillAssetReadResult.Unreadable)
                .audit(skill.id).issues.first().code,
        )
        assertEquals(
            OfficialSkillExecutionEligibilityCode.ASSET_EMPTY,
            eligibility(skill, "   ")
                .audit(skill.id).issues.first().code,
        )
    }

    @Test
    fun auditDistinguishesRequiredSkillDocumentSections() {
        val skill = validSkill()
        val cases = listOf(
            "## 角色与目标" to OfficialSkillExecutionEligibilityCode.MISSING_BOUNDARY,
            "## 输入要求" to OfficialSkillExecutionEligibilityCode.MISSING_INPUT_RULE,
            "## 输出结构" to OfficialSkillExecutionEligibilityCode.MISSING_OUTPUT_RULE,
            "## 资料与个人背景边界" to OfficialSkillExecutionEligibilityCode.MISSING_PRIVACY_RULE,
            "## 联网规则" to OfficialSkillExecutionEligibilityCode.MISSING_NETWORK_RULE,
        )

        cases.forEach { (section, expected) ->
            val body = validSkillBody().replace(section, "## 已删除章节")
            val codes = eligibility(skill, body).audit(skill.id).issues.map { it.code }
            assertTrue("$section 应触发 $expected，实际为 $codes", expected in codes)
        }
    }

    @Test
    fun auditRejectsUnknownSkillWithoutLeakingAssetBody() {
        val secret = "PRIVATE-SKILL-BODY-DO-NOT-LOG"
        val eligibility = OfficialSkillExecutionEligibility(
            catalog = InMemoryOfficialSkillCatalog(listOf(validSkill())),
            assetReader = OfficialSkillAssetReader {
                OfficialSkillAssetReadResult.Success(secret)
            },
        )

        val unknown = eligibility.audit("unknown-skill")
        val empty = eligibility(validSkill(), "   $secret   ").audit("study-planner")
        val combinedDetails = (unknown.issues + empty.issues).joinToString { it.detail }

        assertEquals(
            OfficialSkillExecutionEligibilityCode.UNKNOWN_SKILL,
            unknown.issues.single().code,
        )
        assertFalse(combinedDetails.contains(secret))
    }

    private fun eligibility(
        skill: OfficialSkillDefinition,
        content: String,
    ): OfficialSkillExecutionEligibility = eligibility(
        skill = skill,
        assetResult = OfficialSkillAssetReadResult.Success(content),
    )

    private fun eligibility(
        skill: OfficialSkillDefinition,
        assetResult: OfficialSkillAssetReadResult,
    ): OfficialSkillExecutionEligibility = OfficialSkillExecutionEligibility(
        catalog = InMemoryOfficialSkillCatalog(listOf(skill)),
        assetReader = OfficialSkillAssetReader { assetResult },
    )

    private fun validSkill(): OfficialSkillDefinition = OfficialSkillDefinition(
        id = "study-planner",
        nameZh = "学习规划师",
        aliases = listOf("学习计划助手"),
        summary = "根据目标、基础、时间和反馈设计可调整的学习计划。",
        primaryType = OfficialSkillPrimaryType.TASK_ASSISTANT,
        primaryValue = OfficialSkillPrimaryValue.REALITY_SUPPORT,
        domainTags = listOf("learning_exam"),
        scenarioTags = listOf("plan"),
        inputTags = listOf("question"),
        outputTags = listOf("plan"),
        useMode = OfficialSkillUseMode.BOTH,
        networkRequirement = OfficialSkillNetworkRequirement.NOT_NEEDED,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.OPTIONAL),
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
        typicalScenarios = listOf("规划与行动拆解"),
        inputRequirements = listOf("明确的问题与目标"),
        outputForms = listOf("行动计划"),
        boundaries = listOf(
            "说明关键假设，不编造事实或来源。",
            "仅使用用户明确授权的最小必要资料和个人背景。",
        ),
        nonExecutableReason = null,
        sourceSummary = "见域原创设计并由 PR09-05B 生产实现。",
        assetPath = "skills/study-planner/SKILL.md",
        defaultOrder = 23,
    )

    private fun validSkillBody(): String = """
        # 学习规划师

        ## 角色与目标
        将用户确认的学习目标转化为可调整计划。

        ## 适用场景
        学习规划、复盘和行动拆解。

        ## 输入要求
        使用用户明确提供的目标、基础、时间和约束。

        ## 执行步骤
        澄清目标，拆解行动，设置检查节点。

        ## 输出结构
        输出假设、计划、检查节点和待确认事项。

        ## 事实与来源规则
        不编造事实或来源，区分已提供事实、推断和未知。

        ## 资料与个人背景边界
        只使用用户明确授权的最小必要资料和个人背景。

        ## 联网规则
        默认不联网；涉及变化事实时先说明尚未核验。

        ## 风险与限制
        不承诺成绩，不替用户作高风险最终决定。

        ## 不得执行的行为
        不伪造完成状态，不自动发送消息或创建日历。
    """.trimIndent()
}
