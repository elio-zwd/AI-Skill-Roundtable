package com.elio.jianyu.skill.catalog

data class OfficialSkillCombinationMemberDraft(
    val skillId: String,
    val defaultResponsibility: String? = null,
)

data class OfficialSkillCombinationDraft(
    val name: String,
    val members: List<OfficialSkillCombinationMemberDraft>,
)

data class OfficialSkillCombinationDraftIssue(
    val code: String,
    val skillId: String? = null,
)

object OfficialSkillCombinationDraftValidator {
    private const val MAX_NAME_LENGTH = 80
    private const val MAX_RESPONSIBILITY_LENGTH = 200

    private val forbiddenResponsibilityFragments = listOf(
        "忽略系统",
        "忽略以上",
        "覆盖系统",
        "系统提示词",
        "开发者消息",
        "绕过安全",
        "解除限制",
        "越狱",
        "system prompt",
        "developer message",
        "bypass safety",
    )

    fun validate(
        draft: OfficialSkillCombinationDraft,
        catalog: OfficialSkillCatalog,
    ): List<OfficialSkillCombinationDraftIssue> {
        val issues = mutableListOf<OfficialSkillCombinationDraftIssue>()
        val normalizedName = draft.name.trim()
        if (normalizedName.isEmpty()) {
            issues += OfficialSkillCombinationDraftIssue("blank_name")
        } else if (normalizedName.length > MAX_NAME_LENGTH) {
            issues += OfficialSkillCombinationDraftIssue("name_too_long")
        }
        if (draft.members.isEmpty()) {
            issues += OfficialSkillCombinationDraftIssue("empty_members")
        }

        val ids = draft.members.map { it.skillId }
        if (ids.distinct().size != ids.size) {
            issues += OfficialSkillCombinationDraftIssue("duplicate_skill_id")
        }
        draft.members.forEach { member ->
            if (!catalog.containsOfficialId(member.skillId)) {
                issues += OfficialSkillCombinationDraftIssue(
                    code = "unknown_official_skill_id",
                    skillId = member.skillId,
                )
            }
            val responsibility = member.defaultResponsibility?.trim().orEmpty()
            if (responsibility.length > MAX_RESPONSIBILITY_LENGTH) {
                issues += OfficialSkillCombinationDraftIssue(
                    code = "default_responsibility_too_long",
                    skillId = member.skillId,
                )
            }
            if (forbiddenResponsibilityFragments.any { fragment ->
                    responsibility.contains(fragment, ignoreCase = true)
                }
            ) {
                issues += OfficialSkillCombinationDraftIssue(
                    code = "unsafe_default_responsibility",
                    skillId = member.skillId,
                )
            }
        }
        return issues
    }
}
