package com.elio.jianyu.skill.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillExecutionContextEligibilityTest {
    private val gate = OfficialSkillExecutionContextEligibility()

    @Test
    fun requiredNetworkMaterialAndSensitiveConfirmationsReturnStableCodes() {
        val skill = skill(
            network = OfficialSkillNetworkRequirement.REQUIRED,
            materials = listOf(
                OfficialSkillMaterialRequirement.REQUIRED,
                OfficialSkillMaterialRequirement.USER_AUTHORIZED,
                OfficialSkillMaterialRequirement.SENSITIVE,
            ),
        )

        val result = gate.audit(skill, OfficialSkillExecutionContext())

        assertEquals(
            setOf(
                OfficialSkillExecutionContextCode.REQUIRED_MATERIAL_MISSING,
                OfficialSkillExecutionContextCode.MATERIAL_AUTHORIZATION_REQUIRED,
                OfficialSkillExecutionContextCode.SENSITIVE_MATERIAL_CONFIRMATION_REQUIRED,
                OfficialSkillExecutionContextCode.NETWORK_AUTHORIZATION_REQUIRED,
            ),
            result.issues.map { it.code }.toSet(),
        )
    }

    @Test
    fun personAndHighStakesRequireExplicitFinalConfirmation() {
        val skill = skill(
            primaryType = OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
            risk = OfficialSkillRiskLevel.HIGH_STAKES,
        )

        val result = gate.audit(skill, OfficialSkillExecutionContext())

        assertEquals(
            setOf(
                OfficialSkillExecutionContextCode.PERSON_DISCLAIMER_CONFIRMATION_REQUIRED,
                OfficialSkillExecutionContextCode.HIGH_STAKES_CONFIRMATION_REQUIRED,
            ),
            result.issues.map { it.code }.toSet(),
        )
    }

    @Test
    fun prohibitedMaterialRejectsExternalTransferBeforeRunCreation() {
        val skill = skill(
            id = "patent-disclosure-organizer",
            network = OfficialSkillNetworkRequirement.PROHIBITED_FOR_MATERIAL,
            materials = listOf(
                OfficialSkillMaterialRequirement.REQUIRED,
                OfficialSkillMaterialRequirement.USER_AUTHORIZED,
                OfficialSkillMaterialRequirement.SENSITIVE,
            ),
        )
        val context = OfficialSkillExecutionContext(
            materialProvided = true,
            materialAuthorized = true,
            sensitiveMaterialConfirmed = true,
            containsRestrictedMaterial = true,
            materialMayLeaveDevice = true,
        )

        val result = gate.audit(skill, context)

        assertEquals(
            listOf(OfficialSkillExecutionContextCode.MATERIAL_EXTERNAL_TRANSFER_PROHIBITED),
            result.issues.map { it.code },
        )
    }

    @Test
    fun budgetUseModeAndStageStateAreCheckedIndependently() {
        val skill = skill(useMode = OfficialSkillUseMode.SINGLE_ONLY)
        val context = OfficialSkillExecutionContext(
            selectedMode = OfficialSkillExecutionSelectedMode.MULTI,
            contextCharacters = 20_001,
            maxContextCharacters = 20_000,
            stageExecutable = false,
        )

        val result = gate.audit(skill, context)

        assertEquals(
            setOf(
                OfficialSkillExecutionContextCode.USE_MODE_NOT_SUPPORTED,
                OfficialSkillExecutionContextCode.CONTEXT_BUDGET_EXCEEDED,
                OfficialSkillExecutionContextCode.STAGE_NOT_EXECUTABLE,
            ),
            result.issues.map { it.code }.toSet(),
        )
    }

    @Test
    fun correctedContextBecomesEligibleWithoutChangingStaticPublication() {
        val skill = skill(
            primaryType = OfficialSkillPrimaryType.PERSON_PERSPECTIVE,
            risk = OfficialSkillRiskLevel.HIGH_STAKES,
            network = OfficialSkillNetworkRequirement.REQUIRED,
            materials = listOf(
                OfficialSkillMaterialRequirement.REQUIRED,
                OfficialSkillMaterialRequirement.USER_AUTHORIZED,
                OfficialSkillMaterialRequirement.SENSITIVE,
            ),
        )
        val context = OfficialSkillExecutionContext(
            materialProvided = true,
            materialAuthorized = true,
            sensitiveMaterialConfirmed = true,
            networkAuthorized = true,
            highStakesConfirmed = true,
            personDisclaimerConfirmed = true,
            materialMayLeaveDevice = false,
            contextCharacters = 10_000,
            maxContextCharacters = 20_000,
            selectedMode = OfficialSkillExecutionSelectedMode.SINGLE,
            stageExecutable = true,
        )

        val result = gate.audit(skill, context)

        assertTrue(result.eligible)
        assertEquals(
            listOf(OfficialSkillExecutionContextCode.ELIGIBLE),
            result.issues.map { it.code },
        )
    }

    private fun skill(
        id: String = "test-skill",
        primaryType: OfficialSkillPrimaryType = OfficialSkillPrimaryType.TASK_ASSISTANT,
        risk: OfficialSkillRiskLevel = OfficialSkillRiskLevel.GENERAL,
        network: OfficialSkillNetworkRequirement = OfficialSkillNetworkRequirement.NOT_NEEDED,
        materials: List<OfficialSkillMaterialRequirement> = listOf(
            OfficialSkillMaterialRequirement.NONE,
        ),
        useMode: OfficialSkillUseMode = OfficialSkillUseMode.BOTH,
    ): OfficialSkillDefinition = OfficialSkillDefinition(
        id = id,
        nameZh = "测试 Skill",
        summary = "测试上下文资格",
        primaryType = primaryType,
        primaryValue = OfficialSkillPrimaryValue.BOTH,
        domainTags = listOf("test"),
        scenarioTags = listOf("test"),
        inputTags = listOf("question"),
        outputTags = listOf("analysis"),
        useMode = useMode,
        networkRequirement = network,
        materialRequirements = materials,
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
        typicalScenarios = listOf("测试"),
        inputRequirements = listOf("输入"),
        outputForms = listOf("输出"),
        boundaries = listOf("边界一", "边界二"),
        sourceSummary = "原创测试定义",
        assetPath = "skills/official/test-skill/SKILL.md",
        defaultOrder = 1,
        personDisclaimer = if (primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE) {
            "AI 模拟视角，不代表本人"
        } else {
            null
        },
    )
}
