package com.elio.jianyu.skill.catalog

enum class OfficialSkillExecutionEligibilityCode(
    val reasonCode: String,
) {
    ELIGIBLE("eligible"),
    UNKNOWN_SKILL("unknown_skill"),
    NOT_V1_TARGET("not_v1_target"),
    NOT_DISCOVERABLE("not_discoverable"),
    NOT_SEARCHABLE("not_searchable"),
    NOT_RECOMMENDABLE("not_recommendable"),
    MISSING_ASSET("missing_asset"),
    INVALID_ASSET_PATH("invalid_asset_path"),
    ASSET_UNREADABLE("asset_unreadable"),
    ASSET_EMPTY("asset_empty"),
    PUBLICATION_NOT_READY("publication_not_ready"),
    SOURCE_NOT_VERIFIED("source_not_verified"),
    MISSING_BOUNDARY("missing_boundary"),
    MISSING_INPUT_RULE("missing_input_rule"),
    MISSING_OUTPUT_RULE("missing_output_rule"),
    MISSING_PRIVACY_RULE("missing_privacy_rule"),
    MISSING_NETWORK_RULE("missing_network_rule"),
    NON_EXECUTABLE_REASON_PRESENT("non_executable_reason_present"),
}

data class OfficialSkillExecutionEligibilityIssue(
    val code: OfficialSkillExecutionEligibilityCode,
    val skillId: String,
    val detail: String,
)

data class OfficialSkillExecutionEligibilityResult(
    val skillId: String,
    val issues: List<OfficialSkillExecutionEligibilityIssue>,
) {
    val eligible: Boolean
        get() = issues.size == 1 &&
            issues.single().code == OfficialSkillExecutionEligibilityCode.ELIGIBLE
}

fun interface OfficialSkillAssetReader {
    fun read(assetPath: String): OfficialSkillAssetReadResult
}

sealed interface OfficialSkillAssetReadResult {
    data class Success(
        val content: String,
    ) : OfficialSkillAssetReadResult

    data object Missing : OfficialSkillAssetReadResult

    data object Unreadable : OfficialSkillAssetReadResult
}

/**
 * 可在 JVM 测试运行的正式执行资格审计器。
 *
 * 审计只返回稳定 ID、错误码和短说明，禁止返回 Skill 正文、用户数据或密钥。
 */
class OfficialSkillExecutionEligibility(
    private val catalog: OfficialSkillCatalog,
    private val assetReader: OfficialSkillAssetReader,
) {
    fun audit(officialSkillId: String): OfficialSkillExecutionEligibilityResult {
        val skill = catalog.findById(officialSkillId)
            ?: return failure(
                officialSkillId,
                OfficialSkillExecutionEligibilityCode.UNKNOWN_SKILL,
                "官方 Skill ID 不存在于固定目录",
            )
        return audit(skill)
    }

    fun audit(skill: OfficialSkillDefinition): OfficialSkillExecutionEligibilityResult {
        val issues = mutableListOf<OfficialSkillExecutionEligibilityIssue>()
        fun add(code: OfficialSkillExecutionEligibilityCode, detail: String) {
            issues += OfficialSkillExecutionEligibilityIssue(code, skill.id, detail)
        }

        if (!skill.availability.v1Target) {
            add(OfficialSkillExecutionEligibilityCode.NOT_V1_TARGET, "Skill 不属于 V1 目标")
        }
        if (!skill.availability.discoverable) {
            add(OfficialSkillExecutionEligibilityCode.NOT_DISCOVERABLE, "Skill 不可发现")
        }
        if (!skill.availability.searchable) {
            add(OfficialSkillExecutionEligibilityCode.NOT_SEARCHABLE, "Skill 不可搜索")
        }
        if (!skill.availability.recommendable) {
            add(OfficialSkillExecutionEligibilityCode.NOT_RECOMMENDABLE, "Skill 不可推荐")
        }
        if (!skill.availability.hasAsset || skill.assetPath.isNullOrBlank()) {
            add(OfficialSkillExecutionEligibilityCode.MISSING_ASSET, "缺少正式 Skill 资产")
        }
        val assetPath = skill.assetPath.orEmpty()
        if (assetPath.isNotBlank() && !isSafeSkillAssetPath(assetPath)) {
            add(OfficialSkillExecutionEligibilityCode.INVALID_ASSET_PATH, "Skill 资产路径不合法")
        }
        if (skill.publicationStatus != OfficialSkillPublicationStatus.PUBLISHABLE) {
            add(OfficialSkillExecutionEligibilityCode.PUBLICATION_NOT_READY, "发布状态尚未达到可发布")
        }
        if (skill.sourceStatus != OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE) {
            add(OfficialSkillExecutionEligibilityCode.SOURCE_NOT_VERIFIED, "正式实现来源尚未核验")
        }
        if (!skill.nonExecutableReason.isNullOrBlank()) {
            add(
                OfficialSkillExecutionEligibilityCode.NON_EXECUTABLE_REASON_PRESENT,
                "可执行条目仍保留不可执行原因",
            )
        }

        if (issues.isNotEmpty()) {
            return OfficialSkillExecutionEligibilityResult(skill.id, issues.toList())
        }

        when (val asset = assetReader.read(assetPath)) {
            OfficialSkillAssetReadResult.Missing -> add(
                OfficialSkillExecutionEligibilityCode.MISSING_ASSET,
                "APK assets 中不存在正式 Skill 资产",
            )
            OfficialSkillAssetReadResult.Unreadable -> add(
                OfficialSkillExecutionEligibilityCode.ASSET_UNREADABLE,
                "正式 Skill 资产无法读取",
            )
            is OfficialSkillAssetReadResult.Success -> {
                val content = asset.content.trim()
                if (content.isBlank()) {
                    add(OfficialSkillExecutionEligibilityCode.ASSET_EMPTY, "正式 Skill 资产为空")
                } else {
                    validateSkillContent(content, skill.id, issues)
                }
            }
        }

        return if (issues.isEmpty()) {
            OfficialSkillExecutionEligibilityResult(
                skillId = skill.id,
                issues = listOf(
                    OfficialSkillExecutionEligibilityIssue(
                        code = OfficialSkillExecutionEligibilityCode.ELIGIBLE,
                        skillId = skill.id,
                        detail = "正式执行资格门禁通过",
                    ),
                ),
            )
        } else {
            OfficialSkillExecutionEligibilityResult(skill.id, issues.toList())
        }
    }

    private fun validateSkillContent(
        content: String,
        skillId: String,
        issues: MutableList<OfficialSkillExecutionEligibilityIssue>,
    ) {
        fun add(code: OfficialSkillExecutionEligibilityCode, detail: String) {
            issues += OfficialSkillExecutionEligibilityIssue(code, skillId, detail)
        }

        val headings = content.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("## ") }
            .toSet()

        if (GENERAL_REQUIRED_HEADINGS.any { it !in headings }) {
            add(
                OfficialSkillExecutionEligibilityCode.MISSING_BOUNDARY,
                "Skill 资产缺少角色、场景、步骤、事实、风险或禁止行为章节",
            )
        }
        if (INPUT_HEADING !in headings) {
            add(OfficialSkillExecutionEligibilityCode.MISSING_INPUT_RULE, "Skill 资产缺少输入规则")
        }
        if (OUTPUT_HEADING !in headings) {
            add(OfficialSkillExecutionEligibilityCode.MISSING_OUTPUT_RULE, "Skill 资产缺少输出规则")
        }
        if (PRIVACY_HEADING !in headings) {
            add(OfficialSkillExecutionEligibilityCode.MISSING_PRIVACY_RULE, "Skill 资产缺少隐私边界")
        }
        if (NETWORK_HEADING !in headings) {
            add(OfficialSkillExecutionEligibilityCode.MISSING_NETWORK_RULE, "Skill 资产缺少联网规则")
        }
    }

    companion object {
        private val GENERAL_REQUIRED_HEADINGS = setOf(
            "## 角色与目标",
            "## 适用场景",
            "## 执行步骤",
            "## 事实与来源规则",
            "## 风险与限制",
            "## 不得执行的行为",
        )
        private const val INPUT_HEADING = "## 输入要求"
        private const val OUTPUT_HEADING = "## 输出结构"
        private const val PRIVACY_HEADING = "## 资料与个人背景边界"
        private const val NETWORK_HEADING = "## 联网规则"

        fun isSafeSkillAssetPath(assetPath: String): Boolean {
            if (assetPath.isBlank()) return false
            if (!assetPath.startsWith("skills/")) return false
            if (!assetPath.endsWith("/SKILL.md")) return false
            if (assetPath.startsWith('/') || assetPath.startsWith('\\')) return false
            if ('\\' in assetPath || ':' in assetPath) return false
            val segments = assetPath.split('/')
            if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
            return segments.size >= 3
        }

        private fun failure(
            skillId: String,
            code: OfficialSkillExecutionEligibilityCode,
            detail: String,
        ): OfficialSkillExecutionEligibilityResult = OfficialSkillExecutionEligibilityResult(
            skillId = skillId,
            issues = listOf(
                OfficialSkillExecutionEligibilityIssue(
                    code = code,
                    skillId = skillId,
                    detail = detail,
                ),
            ),
        )
    }
}
