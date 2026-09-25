package com.elio.jianyu.skill.role

import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType

/** 官方 Skill 在用户界面的稳定视觉类型。 */
internal enum class OfficialSkillVisualKind {
    PORTRAIT,
    TOOL,
}

/** 只有纯工作流能力按“工具”展示；其余官方 Skill 都作为可对话角色使用人物头像。 */
internal fun officialSkillVisualKind(definition: OfficialSkillDefinition): OfficialSkillVisualKind =
    if (definition.primaryType == OfficialSkillPrimaryType.WORKFLOW_CAPABILITY) {
        OfficialSkillVisualKind.TOOL
    } else {
        OfficialSkillVisualKind.PORTRAIT
    }

/** 视觉资源路径只由官方 Skill 身份和类型决定，避免页面或 legacy Character 各自猜路径。 */
internal fun officialSkillVisualAssetPath(definition: OfficialSkillDefinition): String =
    when (officialSkillVisualKind(definition)) {
        OfficialSkillVisualKind.PORTRAIT -> "avatars/portraits/" + definition.id + ".jpg"
        OfficialSkillVisualKind.TOOL -> "avatars/tools/" + definition.id + ".png"
    }
