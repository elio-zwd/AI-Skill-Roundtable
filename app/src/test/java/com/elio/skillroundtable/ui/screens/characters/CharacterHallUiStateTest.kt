package com.elio.skillroundtable.ui.screens.characters

import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.CharacterGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterHallUiStateTest {
    @Test
    fun mapCharacterHallUiState_mapsGroupsCharactersAndActiveStatus() {
        val preset = group(id = "preset", isPreset = true)
        val custom = group(id = "custom", isPreset = false)
        val active = character(id = "active", isActive = true)
        val inactive = character(id = "inactive", isActive = false)

        val state = mapCharacterHallUiState(
            characters = listOf(active, inactive),
            groups = listOf(preset, custom),
            overlayState = CharacterHallOverlayState(),
            detailContent = null,
        )

        assertEquals(listOf("preset", "custom"), state.groups.map { it.group.id })
        assertFalse(state.groups.first().canDelete)
        assertTrue(state.groups.last().canDelete)
        assertEquals("入席", state.characters.first().statusLabel)
        assertEquals("旁听", state.characters.last().statusLabel)
        assertTrue(state.canSaveCurrentGroup)
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
    }

    @Test
    fun mapCharacterHallUiState_distinguishesLoadingAndEmptyStates() {
        val loading = mapCharacterHallUiState(
            characters = emptyList(),
            groups = emptyList(),
            overlayState = CharacterHallOverlayState(),
            detailContent = null,
        )
        val empty = mapCharacterHallUiState(
            characters = emptyList(),
            groups = listOf(group(id = "preset", isPreset = true)),
            overlayState = CharacterHallOverlayState(),
            detailContent = null,
        )

        assertTrue(loading.isLoading)
        assertFalse(loading.isEmpty)
        assertFalse(empty.isLoading)
        assertTrue(empty.isEmpty)
    }

    @Test
    fun detailState_mapsLoadingEmptyAndContent() {
        val selected = character(id = "detail")
        val overlay = CharacterHallOverlayState(detailCharacter = selected)

        val loading = mapCharacterHallUiState(
            characters = listOf(selected),
            groups = emptyList(),
            overlayState = overlay,
            detailContent = null,
        )
        val empty = mapCharacterHallUiState(
            characters = listOf(selected),
            groups = emptyList(),
            overlayState = overlay,
            detailContent = "   ",
        )
        val content = mapCharacterHallUiState(
            characters = listOf(selected),
            groups = emptyList(),
            overlayState = overlay,
            detailContent = "# 决策模型",
        )

        assertTrue(loading.detail?.content is CharacterDetailContentUiState.Loading)
        assertTrue(empty.detail?.content is CharacterDetailContentUiState.Empty)
        assertEquals(
            "# 决策模型",
            (content.detail?.content as CharacterDetailContentUiState.Content).markdown,
        )
    }

    @Test
    fun saveGroupForm_requiresNonBlankName() {
        assertFalse(SaveCharacterGroupFormState().isValid)
        assertFalse(SaveCharacterGroupFormState(name = "   ").isValid)
        assertTrue(SaveCharacterGroupFormState(name = "开发组").isValid)
    }

    @Test
    fun characterForm_requiresNameAndSystemPrompt() {
        assertFalse(CharacterFormState().isValid)
        assertFalse(CharacterFormState(name = "测试", systemPrompt = " ").isValid)
        assertTrue(CharacterFormState(name = "测试", systemPrompt = "规则").isValid)
    }

    @Test
    fun characterForm_editPreservesUntouchedFields() {
        val original = character(
            id = "custom_existing",
            name = "旧名称",
            order = 27,
            isActive = false,
            skillAssetPath = "skills/custom/SKILL.md",
            skillDescriptionVector = "1,2,3",
            voiceConfig = "Fenrir",
        )
        val form = CharacterFormState.from(original).copy(
            name = "新名称",
            avatar = "avatars/custom.jpg",
            tagline = "新简介",
            systemPrompt = "新规则",
        )

        val updated = form.toCharacter { "unused" }

        assertEquals("custom_existing", updated?.id)
        assertEquals(27, updated?.order)
        assertFalse(updated?.isActive ?: true)
        assertEquals("skills/custom/SKILL.md", updated?.skillAssetPath)
        assertEquals("1,2,3", updated?.skillDescriptionVector)
        assertEquals("Fenrir", updated?.voiceConfig)
        assertEquals("新名称", updated?.name)
    }

    @Test
    fun characterForm_newCharacterUsesCurrentDefaultsAndGeneratedId() {
        val form = CharacterFormState(
            name = "新角色",
            avatar = "🧙",
            tagline = "简介",
            systemPrompt = "规则",
        )

        val created = form.toCharacter { "custom_fixed" }

        assertEquals("custom_fixed", created?.id)
        assertEquals(10, created?.order)
        assertTrue(created?.isActive == true)
        assertEquals("", created?.skillAssetPath)
        assertEquals("", created?.skillDescriptionVector)
        assertEquals("Aoede", created?.voiceConfig)
    }

    @Test
    fun protectedCharacterCannotBeDeleted() {
        assertFalse(canDeleteCharacter(character(id = PROTECTED_CHARACTER_ID)))
        assertTrue(canDeleteCharacter(character(id = "ordinary")))
    }

    @Test
    fun reducer_onlyCustomGroupCanOpenDeleteConfirmation() {
        val preset = group(id = "preset", isPreset = true)
        val custom = group(id = "custom", isPreset = false)
        val initial = CharacterHallOverlayState()

        val presetTransition = reduceCharacterHallEvent(
            initial,
            CharacterHallEvent.GroupLongPressed(preset),
        )
        val customTransition = reduceCharacterHallEvent(
            initial,
            CharacterHallEvent.GroupLongPressed(custom),
        )

        assertNull(presetTransition.state.groupPendingDeletion)
        assertNull(presetTransition.effect)
        assertEquals(custom, customTransition.state.groupPendingDeletion)
        assertNull(customTransition.effect)
    }

    @Test
    fun reducer_emitsMajorCallbackEffects() {
        val char = character(id = "ordinary")
        val group = group(id = "custom", isPreset = false)

        assertEquals(
            CharacterHallEffect.ApplyGroup(group),
            reduceCharacterHallEvent(
                CharacterHallOverlayState(),
                CharacterHallEvent.GroupClicked(group),
            ).effect,
        )
        assertEquals(
            CharacterHallEffect.ToggleActive(char),
            reduceCharacterHallEvent(
                CharacterHallOverlayState(),
                CharacterHallEvent.ToggleActive(char),
            ).effect,
        )
        assertEquals(
            CharacterHallEffect.EditCharacter(char),
            reduceCharacterHallEvent(
                CharacterHallOverlayState(),
                CharacterHallEvent.EditCharacter(char),
            ).effect,
        )
        assertEquals(
            CharacterHallEffect.DeleteCharacter(char.id),
            reduceCharacterHallEvent(
                CharacterHallOverlayState(),
                CharacterHallEvent.DeleteCharacter(char),
            ).effect,
        )
    }

    @Test
    fun reducer_openAndDismissDetailKeepsClearBoundaryExplicit() {
        val char = character(id = "detail")
        val opened = reduceCharacterHallEvent(
            CharacterHallOverlayState(),
            CharacterHallEvent.CharacterClicked(char),
        )
        val dismissed = reduceCharacterHallEvent(
            opened.state,
            CharacterHallEvent.DismissDetail,
        )

        assertEquals(char, opened.state.detailCharacter)
        assertEquals(CharacterHallEffect.LoadDetail(char), opened.effect)
        assertNull(dismissed.state.detailCharacter)
        assertEquals(CharacterHallEffect.ClearDetail, dismissed.effect)
    }

    @Test
    fun keyTestTagsRemainStable() {
        assertEquals("character_hall", CharacterHallTestTags.ROOT)
        assertEquals("character_hall_add_button", CharacterHallTestTags.ADD_BUTTON)
        assertEquals("character_hall_save_group_button", CharacterHallTestTags.SAVE_GROUP_BUTTON)
        assertEquals("character_detail_sheet", CharacterHallTestTags.DETAIL_SHEET)
        assertEquals("character_add_edit_dialog", CharacterHallTestTags.ADD_EDIT_DIALOG)
    }

    private fun character(
        id: String,
        name: String = "角色",
        order: Int = 1,
        isActive: Boolean = true,
        skillAssetPath: String = "",
        skillDescriptionVector: String = "",
        voiceConfig: String = "Aoede",
    ) = Character(
        id = id,
        name = name,
        avatar = "🧙",
        tagline = "简介",
        systemPrompt = "规则",
        skillAssetPath = skillAssetPath,
        order = order,
        isActive = isActive,
        skillDescriptionVector = skillDescriptionVector,
        voiceConfig = voiceConfig,
    )

    private fun group(
        id: String,
        isPreset: Boolean,
    ) = CharacterGroup(
        id = id,
        name = id,
        description = "说明",
        characterIds = "a,b",
        isPreset = isPreset,
    )
}
