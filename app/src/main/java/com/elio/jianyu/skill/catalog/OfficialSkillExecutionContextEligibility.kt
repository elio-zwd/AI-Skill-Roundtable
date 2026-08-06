package com.elio.jianyu.skill.catalog

enum class OfficialSkillExecutionSelectedMode {
    SINGLE,
    MULTI,
}

enum class OfficialSkillExecutionContextCode(
    val reasonCode: String,
) {
    ELIGIBLE("eligible"),
    REQUIRED_MATERIAL_MISSING("required_material_missing"),
    MATERIAL_AUTHORIZATION_REQUIRED("material_authorization_required"),
    SENSITIVE_MATERIAL_CONFIRMATION_REQUIRED("sensitive_material_confirmation_required"),
    NETWORK_AUTHORIZATION_REQUIRED("network_authorization_required"),
    MATERIAL_EXTERNAL_TRANSFER_PROHIBITED("material_external_transfer_prohibited"),
    HIGH_STAKES_CONFIRMATION_REQUIRED("high_stakes_confirmation_required"),
    PERSON_DISCLAIMER_CONFIRMATION_REQUIRED("person_disclaimer_confirmation_required"),
    CONTEXT_BUDGET_EXCEEDED("context_budget_exceeded"),
    USE_MODE_NOT_SUPPORTED("use_mode_not_supported"),
    STAGE_NOT_EXECUTABLE("stage_not_executable"),
}

data class OfficialSkillExecutionContext(
    val materialProvided: Boolean = false,
    val materialAuthorized: Boolean = false,
    val sensitiveMaterialConfirmed: Boolean = false,
    val networkAuthorized: Boolean = false,
    val containsRestrictedMaterial: Boolean = false,
    val materialMayLeaveDevice: Boolean = false,
    val highStakesConfirmed: Boolean = false,
    val personDisclaimerConfirmed: Boolean = false,
    val contextCharacters: Int = 0,
    val maxContextCharacters: Int = Int.MAX_VALUE,
    val selectedMode: OfficialSkillExecutionSelectedMode = OfficialSkillExecutionSelectedMode.SINGLE,
    val stageExecutable: Boolean = true,
) {
    init {
        require(contextCharacters >= 0)
        require(maxContextCharacters >= 0)
    }
}

data class OfficialSkillExecutionContextIssue(
    val code: OfficialSkillExecutionContextCode,
    val skillId: String,
    val detail: String,
)

data class OfficialSkillExecutionContextResult(
    val skillId: String,
    val issues: List<OfficialSkillExecutionContextIssue>,
) {
    val eligible: Boolean
        get() = issues.size == 1 && issues.single().code == OfficialSkillExecutionContextCode.ELIGIBLE
}

/**
 * 判断一个已通过静态发布资格的 Skill 在本次资料、联网、风险和阶段上下文中能否启动。
 *
 * 结果只暴露稳定 Skill ID、错误码和短说明，不包含用户正文、Skill 资产或密钥。
 */
class OfficialSkillExecutionContextEligibility {
    fun audit(
        skill: OfficialSkillDefinition,
        context: OfficialSkillExecutionContext,
    ): OfficialSkillExecutionContextResult {
        val issues = mutableListOf<OfficialSkillExecutionContextIssue>()
        fun add(code: OfficialSkillExecutionContextCode, detail: String) {
            issues += OfficialSkillExecutionContextIssue(
                code = code,
                skillId = skill.id,
                detail = detail,
            )
        }

        if (
            OfficialSkillMaterialRequirement.REQUIRED in skill.materialRequirements &&
            !context.materialProvided
        ) {
            add(
                OfficialSkillExecutionContextCode.REQUIRED_MATERIAL_MISSING,
                "本次执行缺少该 Skill 明确要求的资料",
            )
        }
        if (
            OfficialSkillMaterialRequirement.USER_AUTHORIZED in skill.materialRequirements &&
            !context.materialAuthorized
        ) {
            add(
                OfficialSkillExecutionContextCode.MATERIAL_AUTHORIZATION_REQUIRED,
                "本次执行尚未获得资料使用授权",
            )
        }
        if (
            OfficialSkillMaterialRequirement.SENSITIVE in skill.materialRequirements &&
            !context.sensitiveMaterialConfirmed
        ) {
            add(
                OfficialSkillExecutionContextCode.SENSITIVE_MATERIAL_CONFIRMATION_REQUIRED,
                "本次执行尚未确认敏感资料范围",
            )
        }
        if (
            skill.networkRequirement == OfficialSkillNetworkRequirement.REQUIRED &&
            !context.networkAuthorized
        ) {
            add(
                OfficialSkillExecutionContextCode.NETWORK_AUTHORIZATION_REQUIRED,
                "该 Skill 需要联网核验，但用户尚未授权联网",
            )
        }
        if (
            skill.networkRequirement == OfficialSkillNetworkRequirement.PROHIBITED_FOR_MATERIAL &&
            context.containsRestrictedMaterial &&
            context.materialMayLeaveDevice
        ) {
            add(
                OfficialSkillExecutionContextCode.MATERIAL_EXTERNAL_TRANSFER_PROHIBITED,
                "禁止外传材料不能发送到外部模型或检索服务",
            )
        }
        if (
            skill.riskLevel in setOf(
                OfficialSkillRiskLevel.HIGH_STAKES,
                OfficialSkillRiskLevel.URGENT,
            ) && !context.highStakesConfirmed
        ) {
            add(
                OfficialSkillExecutionContextCode.HIGH_STAKES_CONFIRMATION_REQUIRED,
                "高后果或紧急主题需要用户完成最终风险确认",
            )
        }
        if (
            skill.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE &&
            !context.personDisclaimerConfirmed
        ) {
            add(
                OfficialSkillExecutionContextCode.PERSON_DISCLAIMER_CONFIRMATION_REQUIRED,
                "人物视角需要用户确认 AI 模拟身份声明",
            )
        }
        if (context.contextCharacters > context.maxContextCharacters) {
            add(
                OfficialSkillExecutionContextCode.CONTEXT_BUDGET_EXCEEDED,
                "本次上下文字符数超过允许预算",
            )
        }
        if (!supportsMode(skill.useMode, context.selectedMode)) {
            add(
                OfficialSkillExecutionContextCode.USE_MODE_NOT_SUPPORTED,
                "该 Skill 不支持当前单 Skill 或多 Skill 使用模式",
            )
        }
        if (!context.stageExecutable) {
            add(
                OfficialSkillExecutionContextCode.STAGE_NOT_EXECUTABLE,
                "当前阶段状态不允许开始新的执行",
            )
        }

        return if (issues.isEmpty()) {
            OfficialSkillExecutionContextResult(
                skillId = skill.id,
                issues = listOf(
                    OfficialSkillExecutionContextIssue(
                        code = OfficialSkillExecutionContextCode.ELIGIBLE,
                        skillId = skill.id,
                        detail = "本次上下文资格门禁通过",
                    ),
                ),
            )
        } else {
            OfficialSkillExecutionContextResult(skill.id, issues.toList())
        }
    }

    private fun supportsMode(
        useMode: OfficialSkillUseMode,
        selectedMode: OfficialSkillExecutionSelectedMode,
    ): Boolean = when (useMode) {
        OfficialSkillUseMode.SINGLE_ONLY -> selectedMode == OfficialSkillExecutionSelectedMode.SINGLE
        OfficialSkillUseMode.SINGLE_PREFERRED,
        OfficialSkillUseMode.MULTI_PREFERRED,
        OfficialSkillUseMode.BOTH,
        -> true
    }
}
