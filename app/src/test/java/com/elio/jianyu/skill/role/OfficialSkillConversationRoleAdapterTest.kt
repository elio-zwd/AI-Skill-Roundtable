package com.elio.jianyu.skill.role

import com.elio.jianyu.data.Character
import com.elio.jianyu.skill.catalog.OfficialSkillAvailability
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillSourceStatus
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OfficialSkillConversationRoleAdapterTest {
    @Test
    fun officialFactsOverrideLegacyIdentityWhileStableVisualFieldsArePreserved() {
        val definition = definition(
            id = "meeting-to-action",
            name = "会议行动助手",
            order = 23,
            assetPath = "skills/meeting-to-action/SKILL.md",
        )
        val legacy = Character(
            id = definition.id,
            name = "旧名称",
            avatar = "avatars/custom-stable.jpg",
            tagline = "旧说明",
            systemPrompt = "旧 Prompt",
            skillAssetPath = "skills/old/SKILL.md",
            order = 2,
            isActive = false,
            skillDescriptionVector = "0.1,0.2",
            voiceConfig = "Kore",
        )

        val actual = buildOfficialSkillCompatibleCharacter(definition, legacy)

        assertEquals(definition.id, actual.id)
        assertEquals("会议行动助手", actual.name)
        assertEquals(definition.summary, actual.tagline)
        assertEquals("skills/meeting-to-action/SKILL.md", actual.skillAssetPath)
        assertEquals(23, actual.order)
        assertEquals("", actual.systemPrompt)
        assertEquals("avatars/custom-stable.jpg", actual.avatar)
        assertEquals("0.1,0.2", actual.skillDescriptionVector)
        assertEquals("Kore", actual.voiceConfig)
    }

    @Test
    fun functionalRoleWithoutLegacyAvatarUsesDeterministicNonPersonFallback() {
        val definition = definition(
            id = "meeting-to-action",
            name = "会议行动助手",
            order = 23,
            assetPath = "skills/meeting-to-action/SKILL.md",
        )

        val first = buildOfficialSkillCompatibleCharacter(definition, existing = null)
        val second = buildOfficialSkillCompatibleCharacter(definition, existing = null)

        assertEquals("会议", first.avatar)
        assertEquals(first.avatar, second.avatar)
    }

    @Test
    fun nonExecutableOfficialSkillCannotProduceCompatibilityRow() {
        val definition = definition(
            id = "blocked-role",
            name = "阻断角色",
            order = 99,
            assetPath = "skills/blocked-role/SKILL.md",
            executable = false,
        )

        assertThrows(IllegalArgumentException::class.java) {
            buildOfficialSkillCompatibleCharacter(definition, existing = null)
        }
    }

    private fun definition(
        id: String,
        name: String,
        order: Int,
        assetPath: String,
        executable: Boolean = true,
    ) = OfficialSkillDefinition(
        id = id,
        nameZh = name,
        aliases = emptyList(),
        summary = "把输入整理成明确行动项。",
        primaryType = OfficialSkillPrimaryType.TASK_ASSISTANT,
        primaryValue = OfficialSkillPrimaryValue.REALITY_SUPPORT,
        domainTags = listOf("office"),
        scenarioTags = listOf("meeting"),
        inputTags = listOf("notes"),
        outputTags = listOf("actions"),
        useMode = OfficialSkillUseMode.SINGLE_PREFERRED,
        networkRequirement = OfficialSkillNetworkRequirement.NOT_NEEDED,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.OPTIONAL),
        riskLevel = OfficialSkillRiskLevel.GENERAL,
        publicationStatus = OfficialSkillPublicationStatus.PUBLISHABLE,
        sourceStatus = OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE,
        availability = OfficialSkillAvailability(
            v1Target = true,
            hasAsset = true,
            discoverable = true,
            searchable = true,
            recommendable = true,
            executable = executable,
        ),
        typicalScenarios = listOf("会议整理"),
        inputRequirements = listOf("会议记录"),
        outputForms = listOf("行动项"),
        boundaries = emptyList(),
        sourceSummary = "官方资产",
        assetPath = assetPath,
        defaultOrder = order,
    )
}
