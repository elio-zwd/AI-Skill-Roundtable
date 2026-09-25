package com.elio.jianyu.viewmodel

import com.elio.jianyu.data.Character
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.Material
import com.elio.jianyu.roundtable.TranscriptBuilder
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

    @Test
    fun materialEligibilityRequiresSameConversationAndCompatibleStage() {
        val formal = FormalConversationContext(issueId = "issue-a", stageId = "stage-a")
        val issueLevel = material("issue-level", issueId = "issue-a", stageId = null)
        val stageLevel = material("stage-level", issueId = "issue-a", stageId = "stage-a")
        val otherStage = material("other-stage", issueId = "issue-a", stageId = "stage-b")
        val otherConversation = material("other-issue", issueId = "issue-b", stageId = null)

        assertTrue(materialIsAvailableForConversation(issueLevel, formal))
        assertTrue(materialIsAvailableForConversation(stageLevel, formal))
        assertTrue(!materialIsAvailableForConversation(otherStage, formal))
        assertTrue(!materialIsAvailableForConversation(otherConversation, formal))
    }

    @Test
    fun baseContextBudgetIncludesLoadedSkillPromptCharacters() {
        val character = Character(
            id = "role-a",
            name = "角色 A",
            avatar = "A",
            tagline = "",
            systemPrompt = "",
            skillAssetPath = "skills/role-a/SKILL.md",
            order = 0,
        )

        val total = conversationBaseContextCharacters(
            messages = emptyList(),
            targetCharacters = listOf(character),
            responseMode = TranscriptBuilder.ResponseMode.INDEPENDENT,
            skillPromptCharacters = mapOf(character.id to 1_200),
        )

        assertTrue(total >= 1_200)
    }

    @Test
    fun baseContextBudgetCanReserveSkillKnowledgeCharacters() {
        val character = Character(
            id = "role-a",
            name = "角色 A",
            avatar = "A",
            tagline = "",
            systemPrompt = "",
            skillAssetPath = "skills/role-a/SKILL.md",
            order = 0,
        )

        val withoutReserve = conversationBaseContextCharacters(
            messages = emptyList(),
            targetCharacters = listOf(character),
            responseMode = TranscriptBuilder.ResponseMode.INDEPENDENT,
            skillPromptCharacters = mapOf(character.id to 1_200),
        )
        val withReserve = conversationBaseContextCharacters(
            messages = emptyList(),
            targetCharacters = listOf(character),
            responseMode = TranscriptBuilder.ResponseMode.INDEPENDENT,
            skillPromptCharacters = mapOf(character.id to 1_200),
            skillKnowledgeReserveCharacters = 9_000,
        )

        assertEquals(withoutReserve + 9_000, withReserve)
    }

    @Test
    fun roundtableThinkingIntensityMapsToProviderLevels() {
        assertEquals("minimal", roundtableThinkingLevel("极简"))
        assertEquals("medium", roundtableThinkingLevel("均衡"))
        assertEquals("high", roundtableThinkingLevel("深度"))
    }

    private fun material(id: String, issueId: String, stageId: String?): Material =
        Material(
            id = id,
            issueId = issueId,
            stageId = stageId,
            title = id,
            sourceType = "note",
            sourceLocator = null,
            content = "content",
            contentHash = "hash",
            sourcePublishedAt = null,
            sourceCapturedAt = null,
            sensitive = false,
            lifecycle = ContextSourceLifecycle.ACTIVE,
            createdAt = 1L,
            updatedAt = 1L,
        )
}
