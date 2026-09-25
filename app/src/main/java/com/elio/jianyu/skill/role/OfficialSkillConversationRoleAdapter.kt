package com.elio.jianyu.skill.role

import android.content.Context
import com.elio.jianyu.data.Character
import com.elio.jianyu.data.CharacterRepository
import com.elio.jianyu.skill.SkillLoader
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition

/**
 * 把 44 项官方 Skill 按需投影为旧圆桌仍能消费的 Character 行。
 *
 * OfficialSkillDefinition 始终是身份/能力事实源；旧 Character 只保留向量和声音等运行期字段。
 * 角色视觉统一由官方 Skill 视觉契约决定，避免 legacy 文本头像或旧路径继续覆盖正式资源。
 * systemPrompt 不复制进兼容行，旧圆桌执行时仍根据正式 skillAssetPath 动态读取 SKILL.md。
 */
internal class OfficialSkillConversationRoleAdapter(
    context: Context,
    private val characterRepository: CharacterRepository,
) {
    private val appContext = context.applicationContext

    suspend fun ensureCompatibleCharacter(
        definition: OfficialSkillDefinition,
    ): Character? {
        if (!definition.availability.executable) return null
        val assetPath = definition.assetPath?.takeIf(String::isNotBlank) ?: return null

        // 在写兼容行前验证正式资产确实能解析；Prompt 只用于门禁，不复制到 legacy 行。
        if (SkillLoader.loadSkill(appContext, assetPath).isBlank()) return null

        val existing = characterRepository.getCharacterById(definition.id)
        val compatible = buildOfficialSkillCompatibleCharacter(
            definition = definition,
            existing = existing,
        )
        characterRepository.insert(compatible)
        return compatible
    }
}

internal fun buildOfficialSkillCompatibleCharacter(
    definition: OfficialSkillDefinition,
    existing: Character?,
): Character {
    require(definition.availability.executable) {
        "不可执行的官方 Skill 不能生成对话兼容角色：" + definition.id
    }
    val assetPath = requireNotNull(definition.assetPath?.takeIf(String::isNotBlank)) {
        "可执行官方 Skill 缺少 assetPath：" + definition.id
    }
    val stableExisting = existing?.takeIf { it.id == definition.id }

    return Character(
        id = definition.id,
        name = definition.nameZh,
        avatar = officialSkillVisualAssetPath(definition),
        tagline = definition.summary,
        systemPrompt = "",
        skillAssetPath = assetPath,
        order = definition.defaultOrder,
        isActive = true,
        skillDescriptionVector = stableExisting?.skillDescriptionVector.orEmpty(),
        voiceConfig = stableExisting?.voiceConfig?.takeIf(String::isNotBlank) ?: "Aoede",
    )
}
