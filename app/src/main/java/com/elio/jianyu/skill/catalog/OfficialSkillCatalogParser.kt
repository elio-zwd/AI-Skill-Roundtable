package com.elio.jianyu.skill.catalog

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object OfficialSkillCatalogParser {
    const val DEFAULT_ASSET_PATH = "official_skill_catalog_v1.json"
    private const val EXPECTED_SCHEMA_VERSION = 1
    private const val EXPECTED_SKILL_COUNT = 44

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    fun loadFromAssets(
        context: Context,
        assetPath: String = DEFAULT_ASSET_PATH,
    ): OfficialSkillCatalogLoadResult {
        return try {
            val text = context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
            parse(text)
        } catch (error: Exception) {
            OfficialSkillCatalogLoadResult.Failure(
                message = "无法读取官方 Skill Catalog",
                cause = error,
            )
        }
    }

    fun parse(source: String): OfficialSkillCatalogLoadResult {
        if (source.isBlank()) {
            return OfficialSkillCatalogLoadResult.Failure("官方 Skill Catalog 为空")
        }
        return try {
            val manifest = json.decodeFromString<OfficialSkillCatalogManifest>(source)
            val issues = validate(manifest)
            if (issues.isNotEmpty()) {
                OfficialSkillCatalogLoadResult.Failure(
                    message = issues.joinToString(separator = "; ") { issue ->
                        buildString {
                            append(issue.code)
                            issue.skillId?.let { append("[").append(it).append("]") }
                            append(": ").append(issue.detail)
                        }
                    },
                )
            } else {
                OfficialSkillCatalogLoadResult.Success(
                    InMemoryOfficialSkillCatalog(manifest.skills),
                )
            }
        } catch (error: Exception) {
            OfficialSkillCatalogLoadResult.Failure(
                message = "官方 Skill Catalog JSON 解析失败",
                cause = error,
            )
        }
    }

    fun validate(manifest: OfficialSkillCatalogManifest): List<OfficialSkillCatalogValidationIssue> {
        val issues = mutableListOf<OfficialSkillCatalogValidationIssue>()
        if (manifest.schemaVersion != EXPECTED_SCHEMA_VERSION) {
            issues += issue("invalid_schema_version", "仅支持 schemaVersion=$EXPECTED_SCHEMA_VERSION")
        }
        if (manifest.catalogId != "jianyu-official-skill-catalog-v1") {
            issues += issue("invalid_catalog_id", "catalogId 不符合固定版本标识")
        }
        if (manifest.generatedFrom.isBlank()) {
            issues += issue("missing_generation_source", "必须记录规格来源")
        }
        if (manifest.skills.size != EXPECTED_SKILL_COUNT) {
            issues += issue("invalid_skill_count", "必须精确包含 $EXPECTED_SKILL_COUNT 项")
        }

        val ids = manifest.skills.map(OfficialSkillDefinition::id)
        if (ids.distinct().size != ids.size) {
            issues += issue("duplicate_skill_id", "官方 Skill ID 不得重复")
        }
        val orders = manifest.skills.map(OfficialSkillDefinition::defaultOrder)
        if (orders.distinct().size != orders.size || orders.sorted() != (1..EXPECTED_SKILL_COUNT).toList()) {
            issues += issue("invalid_default_order", "defaultOrder 必须唯一且连续为 1..$EXPECTED_SKILL_COUNT")
        }

        manifest.skills.forEach { skill ->
            if (skill.id.isBlank()) issues += issue("blank_id", "ID 不得为空", skill.id)
            if (skill.id != skill.id.trim()) issues += issue("untrimmed_id", "ID 不得包含首尾空白", skill.id)
            if (skill.nameZh.isBlank()) issues += issue("blank_name", "中文名不得为空", skill.id)
            if (skill.summary.isBlank()) issues += issue("blank_summary", "简介不得为空", skill.id)
            if (skill.domainTags.isEmpty()) issues += issue("missing_domain", "至少需要一个领域标签", skill.id)
            if (skill.scenarioTags.isEmpty()) issues += issue("missing_scenario", "至少需要一个场景标签", skill.id)
            if (skill.inputTags.isEmpty()) issues += issue("missing_input", "至少需要一个输入标签", skill.id)
            if (skill.outputTags.isEmpty()) issues += issue("missing_output", "至少需要一个输出标签", skill.id)
            if (skill.materialRequirements.isEmpty()) {
                issues += issue("missing_material_requirement", "至少需要一个资料要求", skill.id)
            }
            if (!skill.availability.v1Target) {
                issues += issue("not_v1_target", "44 项均必须属于 V1 阶段目标", skill.id)
            }
            if (skill.availability.hasAsset && skill.assetPath.isNullOrBlank()) {
                issues += issue("missing_asset_path", "存在资产时必须记录路径", skill.id)
            }
            if (!skill.availability.hasAsset && !skill.assetPath.isNullOrBlank()) {
                issues += issue("unexpected_asset_path", "无资产项目不得伪造资产路径", skill.id)
            }
            if (skill.availability.executable && !skill.availability.hasAsset) {
                issues += issue("executable_without_asset", "当前可执行必须有真实资产", skill.id)
            }
            if (skill.availability.executable && skill.nonExecutableReason != null) {
                issues += issue("executable_with_block_reason", "可执行项目不得保留不可执行原因", skill.id)
            }
            if (!skill.availability.executable && skill.nonExecutableReason.isNullOrBlank()) {
                issues += issue("missing_non_executable_reason", "不可执行项目必须说明原因", skill.id)
            }
            if (
                skill.publicationStatus == OfficialSkillPublicationStatus.BLOCKED_REWORK &&
                skill.availability.executable
            ) {
                issues += issue("blocked_but_executable", "阻断重构项目不得标记可执行", skill.id)
            }
            if (
                skill.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE &&
                skill.personDisclaimer.isNullOrBlank()
            ) {
                issues += issue("missing_person_disclaimer", "人物视角必须包含非本人声明", skill.id)
            }
            if (skill.id == "original-expression-naturalizer" && skill.integrityBoundaries.size < 6) {
                issues += issue("missing_integrity_boundaries", "去AI化助手必须包含完整诚信边界", skill.id)
            }
        }

        if (manifest.skills.count { it.id == "zhang_xuefeng" } != 1) {
            issues += issue("invalid_zhang_xuefeng_mapping", "张雪峰只能保留一个正式 ID")
        }
        if ("zhangxuefeng-perspective" in ids || "academic-ai-evasion" in ids) {
            issues += issue("forbidden_research_id", "研究 ID 不得成为额外官方候选")
        }
        if ("office-document-productivity" !in ids || "original-expression-naturalizer" !in ids) {
            issues += issue("missing_special_skill", "两个固定特殊 Skill ID 必须存在")
        }
        return issues
    }

    private fun issue(
        code: String,
        detail: String,
        skillId: String? = null,
    ) = OfficialSkillCatalogValidationIssue(
        code = code,
        skillId = skillId?.takeIf(String::isNotBlank),
        detail = detail,
    )
}
