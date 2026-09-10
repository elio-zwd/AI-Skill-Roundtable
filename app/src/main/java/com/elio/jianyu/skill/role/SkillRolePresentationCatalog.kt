package com.elio.jianyu.skill.role

import android.content.Context
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SkillRolePresentationCatalog internal constructor(
    val entries: List<SkillRolePresentationEntry>,
) {
    private val bySkillId = entries.associateBy(SkillRolePresentationEntry::skillId)

    val featuredEntries: List<SkillRolePresentationEntry> = entries
        .filter { it.featuredOrder != null }
        .sortedBy { requireNotNull(it.featuredOrder) }

    fun findBySkillId(skillId: String): SkillRolePresentationEntry? = bySkillId[skillId]

    fun categoryOf(skillId: String): SkillRoleDiscoveryCategory? =
        bySkillId[skillId]?.primaryDiscoveryCategory
}

object SkillRolePresentationCatalogParser {
    private const val EXPECTED_SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        coerceInputValues = false
        explicitNulls = false
    }

    fun parse(
        source: String,
        officialSkillIds: Set<String>,
    ): SkillRolePresentationLoadResult {
        if (source.isBlank()) {
            return failure("invalid_manifest", "角色展示 Manifest 为空")
        }
        return try {
            val manifest = json.decodeFromString<SkillRolePresentationManifest>(source)
            validate(manifest, officialSkillIds)
        } catch (error: Exception) {
            SkillRolePresentationLoadResult.Failure(
                message = "invalid_manifest: 角色展示 Manifest JSON 解析失败",
                cause = error,
            )
        }
    }

    private fun validate(
        manifest: SkillRolePresentationManifest,
        officialSkillIds: Set<String>,
    ): SkillRolePresentationLoadResult {
        val issues = mutableListOf<String>()
        if (manifest.schemaVersion != EXPECTED_SCHEMA_VERSION) {
            issues += "unsupported_schema_version: 仅支持 schemaVersion=$EXPECTED_SCHEMA_VERSION"
        }

        val ids = manifest.entries.map { it.skillId }
        if (ids.any { it.isBlank() || it != it.trim() }) {
            issues += "invalid_skill_id: skillId 不得为空或包含首尾空白"
        }
        if (ids.distinct().size != ids.size) {
            issues += "duplicate_skill_id: skillId 不得重复"
        }

        val entryIds = ids.toSet()
        val unknownIds = entryIds - officialSkillIds
        if (unknownIds.isNotEmpty()) {
            issues += "unknown_skill_id: ${unknownIds.sorted().joinToString()}"
        }
        val missingIds = officialSkillIds - entryIds
        if (missingIds.isNotEmpty()) {
            issues += "missing_skill_id: ${missingIds.sorted().joinToString()}"
        }

        val featuredOrders = manifest.entries.mapNotNull(SkillRolePresentationEntry::featuredOrder)
        if (
            featuredOrders.any { it <= 0 } ||
            featuredOrders.distinct().size != featuredOrders.size ||
            featuredOrders.sorted() != (1..featuredOrders.size).toList()
        ) {
            issues += "invalid_featured_order: featuredOrder 必须唯一且连续为 1..N"
        }

        if (issues.isNotEmpty()) {
            return SkillRolePresentationLoadResult.Failure(issues.joinToString("; "))
        }
        return SkillRolePresentationLoadResult.Success(
            SkillRolePresentationCatalog(manifest.entries),
        )
    }

    private fun failure(code: String, detail: String) =
        SkillRolePresentationLoadResult.Failure("$code: $detail")
}

object SkillRolePresentationCatalogLoader {
    const val DEFAULT_ASSET_PATH = "skill_role_presentation_v1.json"

    fun load(
        context: Context,
        officialCatalog: OfficialSkillCatalog,
        assetPath: String = DEFAULT_ASSET_PATH,
    ): SkillRolePresentationLoadResult = try {
        val source = context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val officialIds = officialCatalog.skills.mapTo(linkedSetOf()) { it.id }
        SkillRolePresentationCatalogParser.parse(source, officialIds)
    } catch (error: Exception) {
        SkillRolePresentationLoadResult.Failure(
            message = "invalid_manifest: 无法读取角色展示 Manifest",
            cause = error,
        )
    }
}
