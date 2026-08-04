package com.elio.jianyu.execution

import android.content.Context
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.skill.SkillConfig
import com.elio.jianyu.skill.SkillLoader
import com.elio.jianyu.skill.catalog.AndroidOfficialSkillAssetReader
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillExecutionEligibility
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ExecutionSkillSelection(
    val officialSkillId: String,
    val defaultResponsibility: String = "",
)

fun interface ExecutionSkillResolver {
    suspend fun resolve(
        runId: String,
        selections: List<ExecutionSkillSelection>,
        createdAt: Long,
    ): List<ExecutionParticipantSnapshotEntity>
}

class OfficialCatalogExecutionSkillResolver(
    context: Context,
    private val catalog: OfficialSkillCatalog,
    private val executionEligibility: OfficialSkillExecutionEligibility =
        OfficialSkillExecutionEligibility(
            catalog = catalog,
            assetReader = AndroidOfficialSkillAssetReader(context.applicationContext),
        ),
) : ExecutionSkillResolver {
    private val appContext = context.applicationContext
    private val json = Json { encodeDefaults = true }

    override suspend fun resolve(
        runId: String,
        selections: List<ExecutionSkillSelection>,
        createdAt: Long,
    ): List<ExecutionParticipantSnapshotEntity> {
        require(runId.isNotBlank())
        require(createdAt > 0L)
        require(selections.isNotEmpty()) { "执行至少需要一位已确认 Skill" }
        require(selections.map { it.officialSkillId }.distinct().size == selections.size) {
            "同一 Run 不得重复选择同一官方 Skill"
        }

        val configs = SkillLoader.loadSkillsConfig(appContext).associateBy(SkillConfig::id)
        return selections.mapIndexed { index, selection ->
            val definition = catalog.findById(selection.officialSkillId)
                ?: throw InvalidExecutionSkillException(
                    selection.officialSkillId,
                    "unknown_official_skill",
                )
            validateExecutable(definition)
            val audit = executionEligibility.audit(definition)
            if (!audit.eligible) {
                throw InvalidExecutionSkillException(
                    definition.id,
                    audit.issues.first().code.reasonCode,
                )
            }
            val assetPath = requireNotNull(definition.assetPath) {
                "可执行 Skill ${definition.id} 缺少资源路径"
            }
            val systemPrompt = SkillLoader.loadSkill(appContext, assetPath).trim()
            if (systemPrompt.isBlank()) {
                throw InvalidExecutionSkillException(definition.id, "missing_system_prompt")
            }
            val config = configs[definition.id]
            ExecutionParticipantSnapshotEntity(
                id = "$runId-participant-$index",
                runId = runId,
                sourceType = OFFICIAL_SKILL_SOURCE_TYPE,
                sourceId = definition.id,
                displayName = config?.name?.takeIf(String::isNotBlank) ?: definition.nameZh,
                avatar = config?.avatar?.takeIf(String::isNotBlank)
                    ?: definition.nameZh.take(1),
                skillAssetPath = assetPath,
                systemPrompt = systemPrompt,
                configurationJson = json.encodeToString(definition),
                defaultResponsibility = selection.defaultResponsibility.trim(),
                position = index,
                createdAt = createdAt,
            )
        }
    }

    private fun validateExecutable(definition: OfficialSkillDefinition) {
        if (!definition.availability.executable) {
            throw InvalidExecutionSkillException(definition.id, "skill_not_executable")
        }
        if (!definition.availability.hasAsset || definition.assetPath.isNullOrBlank()) {
            throw InvalidExecutionSkillException(definition.id, "missing_skill_asset")
        }
    }

    private companion object {
        const val OFFICIAL_SKILL_SOURCE_TYPE = "official_skill"
    }
}

class InvalidExecutionSkillException(
    val officialSkillId: String,
    val reasonCode: String,
) : IllegalArgumentException("Skill $officialSkillId cannot execute: $reasonCode")
