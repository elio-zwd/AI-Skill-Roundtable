package com.elio.jianyu.skill.role

import kotlinx.serialization.Serializable

/** 用户侧【角色】页的主发现分类。分类只决定发现入口，不限制 Skill 的跨领域能力。 */
@Serializable
enum class SkillRoleDiscoveryCategory {
    THINKING,
    CAREER,
    RESEARCH_LEARNING,
    PRODUCT_CREATION,
    COMMUNICATION,
    OFFICE_TASKS,
    LIFE_TOOLS,
}

/**
 * OfficialSkillCatalog 缺失、且必须由产品人工决定的最小展示元数据。
 * 名称、能力、风险、发布状态、assetPath 等仍只来自官方 Catalog。
 */
@Serializable
data class SkillRolePresentationEntry(
    val skillId: String,
    val primaryDiscoveryCategory: SkillRoleDiscoveryCategory,
    val featuredOrder: Int? = null,
)

@Serializable
internal data class SkillRolePresentationManifest(
    val schemaVersion: Int,
    val entries: List<SkillRolePresentationEntry>,
)

sealed interface SkillRolePresentationLoadResult {
    data class Success(
        val catalog: SkillRolePresentationCatalog,
    ) : SkillRolePresentationLoadResult

    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : SkillRolePresentationLoadResult
}
