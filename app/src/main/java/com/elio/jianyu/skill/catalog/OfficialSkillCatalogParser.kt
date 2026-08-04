package com.elio.jianyu.skill.catalog

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object OfficialSkillCatalogParser {
    const val DEFAULT_ASSET_PATH = "official_skill_catalog_v1.json"
    const val DEFAULT_EXECUTION_PUBLICATION_ASSET_PATH = "official_skill_execution_batch_v1.json"

    private const val EXPECTED_SCHEMA_VERSION = 1
    private const val EXPECTED_SKILL_COUNT = 44
    private const val EXPECTED_EXECUTION_BATCH_SIZE = 4
    private const val EXPECTED_EXECUTION_BATCH_ID = "jianyu-official-skill-execution-batch-v1"

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    fun loadFromAssets(
        context: Context,
        assetPath: String = DEFAULT_ASSET_PATH,
        executionPublicationAssetPath: String = DEFAULT_EXECUTION_PUBLICATION_ASSET_PATH,
    ): OfficialSkillCatalogLoadResult {
        return try {
            val baseSource = readAssetText(context, assetPath)
            val executionPublicationSource = readAssetText(context, executionPublicationAssetPath)
            parse(baseSource, executionPublicationSource)
        } catch (error: Exception) {
            OfficialSkillCatalogLoadResult.Failure(
                message = "无法读取官方 Skill Catalog 或执行发布 Manifest",
                cause = error,
            )
        }
    }

    /** 只解析稳定 44 项目录，供基础目录契约测试和历史治理检查使用。 */
    fun parse(source: String): OfficialSkillCatalogLoadResult {
        if (source.isBlank()) {
            return OfficialSkillCatalogLoadResult.Failure("官方 Skill Catalog 为空")
        }
        return try {
            val manifest = json.decodeFromString<OfficialSkillCatalogManifest>(source)
            catalogResult(manifest)
        } catch (error: Exception) {
            OfficialSkillCatalogLoadResult.Failure(
                message = "官方 Skill Catalog JSON 解析失败",
                cause = error,
            )
        }
    }

    /**
     * 生产解析入口。执行发布 Manifest 只能激活稳定目录中已存在的官方 ID，
     * 不允许复制或扩展第二份 Catalog。
     */
    fun parse(
        source: String,
        executionPublicationSource: String,
    ): OfficialSkillCatalogLoadResult {
        if (source.isBlank()) {
            return OfficialSkillCatalogLoadResult.Failure("官方 Skill Catalog 为空")
        }
        if (executionPublicationSource.isBlank()) {
            return OfficialSkillCatalogLoadResult.Failure("官方 Skill 执行发布 Manifest 为空")
        }
        return try {
            val manifest = json.decodeFromString<OfficialSkillCatalogManifest>(source)
            val baseIssues = validate(manifest)
            if (baseIssues.isNotEmpty()) {
                return failure(baseIssues)
            }

            val publication = json.decodeFromString<OfficialSkillExecutionPublicationManifest>(
                executionPublicationSource,
            )
            val publicationIssues = validateExecutionPublication(manifest, publication)
            if (publicationIssues.isNotEmpty()) {
                return failure(publicationIssues)
            }

            val effective = applyExecutionPublication(manifest, publication)
            val effectiveIssues = validate(effective)
            if (effectiveIssues.isNotEmpty()) {
                failure(effectiveIssues)
            } else {
                OfficialSkillCatalogLoadResult.Success(
                    InMemoryOfficialSkillCatalog(effective.skills),
                )
            }
        } catch (error: Exception) {
            OfficialSkillCatalogLoadResult.Failure(
                message = "官方 Skill Catalog 或执行发布 Manifest JSON 解析失败",
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
            if (skill.availability.executable) {
                if (!skill.availability.discoverable) {
                    issues += issue("executable_not_discoverable", "可执行 Skill 必须可发现", skill.id)
                }
                if (!skill.availability.searchable) {
                    issues += issue("executable_not_searchable", "可执行 Skill 必须可搜索", skill.id)
                }
                if (!skill.availability.recommendable) {
                    issues += issue("executable_not_recommendable", "可执行 Skill 必须可推荐", skill.id)
                }
                if (skill.publicationStatus != OfficialSkillPublicationStatus.PUBLISHABLE) {
                    issues += issue("executable_publication_not_ready", "可执行 Skill 必须达到可发布状态", skill.id)
                }
                if (skill.sourceStatus != OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE) {
                    issues += issue("executable_source_not_verified", "可执行 Skill 必须核验正式实现来源", skill.id)
                }
                if (!OfficialSkillExecutionEligibility.isSafeSkillAssetPath(skill.assetPath.orEmpty())) {
                    issues += issue("invalid_executable_asset_path", "可执行 Skill 资产路径不合法", skill.id)
                }
                if (skill.boundaries.isEmpty()) {
                    issues += issue("executable_missing_boundaries", "可执行 Skill 必须记录能力边界", skill.id)
                }
                if (skill.inputRequirements.isEmpty()) {
                    issues += issue("executable_missing_input_requirements", "可执行 Skill 必须记录输入要求", skill.id)
                }
                if (skill.outputForms.isEmpty()) {
                    issues += issue("executable_missing_output_forms", "可执行 Skill 必须记录输出形式", skill.id)
                }
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

    fun validateExecutionPublication(
        baseManifest: OfficialSkillCatalogManifest,
        publication: OfficialSkillExecutionPublicationManifest,
    ): List<OfficialSkillCatalogValidationIssue> {
        val issues = mutableListOf<OfficialSkillCatalogValidationIssue>()
        if (publication.schemaVersion != EXPECTED_SCHEMA_VERSION) {
            issues += issue("invalid_execution_schema_version", "执行发布仅支持 schemaVersion=1")
        }
        if (publication.batchId != EXPECTED_EXECUTION_BATCH_ID) {
            issues += issue("invalid_execution_batch_id", "执行发布批次标识不符合固定值")
        }
        if (publication.generatedFrom.isBlank()) {
            issues += issue("missing_execution_generation_source", "执行发布必须记录原创实现来源")
        }
        if (publication.skills.size != EXPECTED_EXECUTION_BATCH_SIZE) {
            issues += issue(
                "invalid_execution_batch_size",
                "首批执行发布必须精确包含 $EXPECTED_EXECUTION_BATCH_SIZE 项",
            )
        }
        val publicationIds = publication.skills.map(OfficialSkillExecutionPublication::id)
        if (publicationIds.distinct().size != publicationIds.size) {
            issues += issue("duplicate_execution_skill_id", "执行发布 Skill ID 不得重复")
        }
        val baseById = baseManifest.skills.associateBy(OfficialSkillDefinition::id)
        publication.skills.forEach { entry ->
            val base = baseById[entry.id]
            if (base == null) {
                issues += issue("unknown_execution_skill", "执行发布不得新增第 45 项", entry.id)
                return@forEach
            }
            if (entry.expectedDefaultOrder != base.defaultOrder) {
                issues += issue("execution_order_mismatch", "执行发布不得改写 defaultOrder", entry.id)
            }
            if (!base.availability.v1Target || !base.availability.discoverable || !base.availability.searchable) {
                issues += issue("execution_identity_not_ready", "执行发布条目必须属于可发现的 V1 固定目录", entry.id)
            }
            if (base.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE) {
                issues += issue("person_execution_forbidden", "首批不得发布人物视角 Skill", entry.id)
            }
            if (base.riskLevel in setOf(OfficialSkillRiskLevel.HIGH_STAKES, OfficialSkillRiskLevel.URGENT)) {
                issues += issue("high_stakes_execution_forbidden", "首批不得发布高后果或紧急 Skill", entry.id)
            }
            if (entry.publicationStatus != OfficialSkillPublicationStatus.PUBLISHABLE) {
                issues += issue("execution_publication_not_ready", "执行发布必须达到 PUBLISHABLE", entry.id)
            }
            if (entry.sourceStatus != OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE) {
                issues += issue("execution_source_not_verified", "执行发布必须核验正式实现来源", entry.id)
            }
            if (!OfficialSkillExecutionEligibility.isSafeSkillAssetPath(entry.assetPath)) {
                issues += issue("invalid_execution_asset_path", "执行发布资产路径不合法", entry.id)
            }
            if (entry.sourceSummary.isBlank()) {
                issues += issue("missing_execution_source_summary", "执行发布必须记录原创来源摘要", entry.id)
            }
            if (entry.boundaries.size < 2) {
                issues += issue("missing_execution_boundaries", "执行发布至少需要两条明确边界", entry.id)
            }
        }
        return issues
    }

    private fun applyExecutionPublication(
        baseManifest: OfficialSkillCatalogManifest,
        publication: OfficialSkillExecutionPublicationManifest,
    ): OfficialSkillCatalogManifest {
        val publicationsById = publication.skills.associateBy(OfficialSkillExecutionPublication::id)
        return baseManifest.copy(
            generatedFrom = baseManifest.generatedFrom + " + " + publication.generatedFrom,
            skills = baseManifest.skills.map { base ->
                val entry = publicationsById[base.id] ?: return@map base
                base.copy(
                    publicationStatus = entry.publicationStatus,
                    sourceStatus = entry.sourceStatus,
                    availability = base.availability.copy(
                        hasAsset = true,
                        recommendable = true,
                        executable = true,
                    ),
                    boundaries = entry.boundaries,
                    nonExecutableReason = null,
                    integrityBoundaries = entry.integrityBoundaries,
                    sourceSummary = entry.sourceSummary,
                    assetPath = entry.assetPath,
                )
            },
        )
    }

    private fun catalogResult(
        manifest: OfficialSkillCatalogManifest,
    ): OfficialSkillCatalogLoadResult {
        val issues = validate(manifest)
        return if (issues.isNotEmpty()) {
            failure(issues)
        } else {
            OfficialSkillCatalogLoadResult.Success(
                InMemoryOfficialSkillCatalog(manifest.skills),
            )
        }
    }

    private fun failure(
        issues: List<OfficialSkillCatalogValidationIssue>,
    ): OfficialSkillCatalogLoadResult.Failure = OfficialSkillCatalogLoadResult.Failure(
        message = issues.joinToString(separator = "; ") { validationIssue ->
            buildString {
                append(validationIssue.code)
                validationIssue.skillId?.let { append("[").append(it).append("]") }
                append(": ").append(validationIssue.detail)
            }
        },
    )

    private fun readAssetText(
        context: Context,
        assetPath: String,
    ): String = context.assets.open(assetPath).use { stream ->
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
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
