package com.elio.jianyu.skill.role

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 编译级契约：角色展示分类必须包含 UI-02 已批准的 7 个发现分类。
 * 运行行为由 JVM 的 SkillRolePresentationCatalogTest 覆盖。
 */
class SkillRolePresentationCatalogContractTest {
    @Test
    fun approvedDiscoveryCategoriesRemainAvailable() {
        assertEquals(
            setOf(
                SkillRoleDiscoveryCategory.THINKING,
                SkillRoleDiscoveryCategory.CAREER,
                SkillRoleDiscoveryCategory.RESEARCH_LEARNING,
                SkillRoleDiscoveryCategory.PRODUCT_CREATION,
                SkillRoleDiscoveryCategory.COMMUNICATION,
                SkillRoleDiscoveryCategory.OFFICE_TASKS,
                SkillRoleDiscoveryCategory.LIFE_TOOLS,
            ),
            SkillRoleDiscoveryCategory.entries.toSet(),
        )
    }
}
