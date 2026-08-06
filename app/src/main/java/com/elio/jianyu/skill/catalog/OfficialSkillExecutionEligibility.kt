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
    MISSING_PERSON_SECTION("missing_person_section"),
    MISSING_HIGH_STAKES_SECTION("missing_high_stakes_section"),
    SPECIAL_BOUNDARY_MISSING("special_boundary_missing"),
    PLACEHOLDER_CONTENT("placeholder_content"),
    SENSITIVE_LITERAL_PRESENT("sensitive_literal_present"),
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
                    validateSkillContent(content, skill, issues)
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
        skill: OfficialSkillDefinition,
        issues: MutableList<OfficialSkillExecutionEligibilityIssue>,
    ) {
        fun add(code: OfficialSkillExecutionEligibilityCode, detail: String) {
            issues += OfficialSkillExecutionEligibilityIssue(code, skill.id, detail)
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
        if (
            skill.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE &&
            PERSON_REQUIRED_HEADINGS.any { it !in headings }
        ) {
            add(
                OfficialSkillExecutionEligibilityCode.MISSING_PERSON_SECTION,
                "人物视角资产缺少模拟身份、来源时效、不确定性或禁止冒充章节",
            )
        }
        if (
            skill.riskLevel in setOf(
                OfficialSkillRiskLevel.HIGH_STAKES,
                OfficialSkillRiskLevel.URGENT,
            ) && HIGH_STAKES_REQUIRED_HEADINGS.any { it !in headings }
        ) {
            add(
                OfficialSkillExecutionEligibilityCode.MISSING_HIGH_STAKES_SECTION,
                "高后果资产缺少地区时效、专业复核或紧急处理章节",
            )
        }

        val normalized = content.lowercase()
        if (PLACEHOLDER_MARKERS.any(normalized::contains)) {
            add(
                OfficialSkillExecutionEligibilityCode.PLACEHOLDER_CONTENT,
                "正式 Skill 资产不得包含占位标记",
            )
        }
        if (SENSITIVE_LITERALS.any(normalized::contains)) {
            add(
                OfficialSkillExecutionEligibilityCode.SENSITIVE_LITERAL_PRESENT,
                "正式 Skill 资产包含疑似密钥或开发环境字面量",
            )
        }

        when (skill.id) {
            "office-document-productivity" -> {
                if (
                    !content.contains("Markdown") ||
                    !content.contains("纯文本") ||
                    !content.contains("结构化表格") ||
                    !content.contains("不控制桌面") ||
                    content.contains("自动提交")
                ) {
                    add(
                        OfficialSkillExecutionEligibilityCode.SPECIAL_BOUNDARY_MISSING,
                        "办公文档助手能力边界不完整",
                    )
                }
            }
            "original-expression-naturalizer" -> {
                val required = listOf(
                    "不规避 AI 检测",
                    "不协助学术作弊",
                    "不伪造经历",
                    "不伪造事实",
                    "不冒充他人",
                    "不代写必须由本人独立完成的受限内容",
                )
                if (required.any { it !in content }) {
                    add(
                        OfficialSkillExecutionEligibilityCode.SPECIAL_BOUNDARY_MISSING,
                        "自然表达优化诚信边界不完整",
                    )
                }
            }
            "patent-disclosure-organizer" -> {
                if (
                    !content.contains("禁止外传") ||
                    !content.contains("脱敏摘要") ||
                    !content.contains("不得发送到外部模型")
                ) {
                    add(
                        OfficialSkillExecutionEligibilityCode.SPECIAL_BOUNDARY_MISSING,
                        "专利披露整理的禁止外传边界不完整",
                    )
                }
            }
            "culture-fortune-entertainment" -> {
                if (!content.contains("不得用于重大决策")) {
                    add(
                        OfficialSkillExecutionEligibilityCode.SPECIAL_BOUNDARY_MISSING,
                        "文化命理娱乐缺少重大决策边界",
                    )
                }
            }
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
        private val PERSON_REQUIRED_HEADINGS = setOf(
            "## AI 模拟身份声明",
            "## 公开来源与时效边界",
            "## 观点不确定性",
            "## 不得冒充本人",
        )
        private val HIGH_STAKES_REQUIRED_HEADINGS = setOf(
            "## 高后果边界",
            "## 当前地区与时效",
            "## 现实专业复核条件",
            "## 紧急情况处理",
        )
        private const val INPUT_HEADING = "## 输入要求"
        private const val OUTPUT_HEADING = "## 输出结构"
        private const val PRIVACY_HEADING = "## 资料与个人背景边界"
        private const val NETWORK_HEADING = "## 联网规则"
        private val PLACEHOLDER_MARKERS = setOf("todo", "tbd", "待补充", "占位内容")
        private val SENSITIVE_LITERALS = setOf("aiza", "sk-", ".env", "api_key=")

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
