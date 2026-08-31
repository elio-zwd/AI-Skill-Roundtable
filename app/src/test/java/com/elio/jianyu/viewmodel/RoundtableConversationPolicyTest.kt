package com.elio.jianyu.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Skill 角色本次回复范围的业务边界测试。 */
class RoundtableConversationPolicyTest {

    @Test
    fun noTargetUsesCurrentSessionRolesInStableDistinctOrder() {
        val resolved = resolveRequestedSkillRoleIds(
            participantIds = listOf("role_b", "role_a", "role_b"),
            targetCharacterId = null,
        )

        assertEquals(listOf("role_b", "role_a"), resolved)
    }

    @Test
    fun targetMustBelongToCurrentSession() {
        val resolved = resolveRequestedSkillRoleIds(
            participantIds = listOf("role_a", "role_b"),
            targetCharacterId = "role_from_previous_session",
        )

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun validTargetOnlySelectsThatRole() {
        val resolved = resolveRequestedSkillRoleIds(
            participantIds = listOf("role_a", "role_b"),
            targetCharacterId = "role_b",
        )

        assertEquals(listOf("role_b"), resolved)
    }
}
