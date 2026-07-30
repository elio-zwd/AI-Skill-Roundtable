package com.elio.skillroundtable.ui.screens.characters

import com.elio.skillroundtable.data.CharacterGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterHallEventTest {
    @Test
    fun addCharacter_emitsTopLevelCallbackEffect() {
        val transition = reduceCharacterHallEvent(
            CharacterHallOverlayState(),
            CharacterHallEvent.AddCharacter,
        )

        assertEquals(CharacterHallEffect.AddCharacter, transition.effect)
    }

    @Test
    fun saveGroupFlow_updatesFormAndEmitsTrimmedValues() {
        val opened = reduceCharacterHallEvent(
            CharacterHallOverlayState(),
            CharacterHallEvent.OpenSaveGroup,
        )
        val named = reduceCharacterHallEvent(
            opened.state,
            CharacterHallEvent.SaveGroupNameChanged("  开发组  "),
        )
        val described = reduceCharacterHallEvent(
            named.state,
            CharacterHallEvent.SaveGroupDescriptionChanged("  技术角色  "),
        )
        val confirmed = reduceCharacterHallEvent(
            described.state,
            CharacterHallEvent.ConfirmSaveGroup,
        )

        assertTrue(opened.state.saveGroupForm != null)
        assertEquals(
            CharacterHallEffect.SaveGroup(
                name = "开发组",
                description = "技术角色",
                displayName = "  开发组  ",
            ),
            confirmed.effect,
        )
        assertNull(confirmed.state.saveGroupForm)
    }

    @Test
    fun blankGroupName_keepsDialogAndEmitsValidationEffect() {
        val state = CharacterHallOverlayState(
            saveGroupForm = SaveCharacterGroupFormState(name = "   "),
        )

        val transition = reduceCharacterHallEvent(
            state,
            CharacterHallEvent.ConfirmSaveGroup,
        )

        assertEquals(CharacterHallEffect.InvalidGroupName, transition.effect)
        assertTrue(transition.state.saveGroupForm != null)
    }

    @Test
    fun customGroupConfirmation_emitsDeleteEffectAndClosesDialog() {
        val group = CharacterGroup(
            id = "custom_group",
            name = "自定义组",
            description = "说明",
            characterIds = "a,b",
            isPreset = false,
        )
        val state = CharacterHallOverlayState(groupPendingDeletion = group)

        val transition = reduceCharacterHallEvent(
            state,
            CharacterHallEvent.ConfirmDeleteGroup,
        )

        assertEquals(CharacterHallEffect.DeleteGroup(group), transition.effect)
        assertNull(transition.state.groupPendingDeletion)
    }

    @Test
    fun presetGroupConfirmation_neverEmitsDeleteEffect() {
        val group = CharacterGroup(
            id = "preset_group",
            name = "预设组",
            description = "说明",
            characterIds = "a,b",
            isPreset = true,
        )
        val state = CharacterHallOverlayState(groupPendingDeletion = group)

        val transition = reduceCharacterHallEvent(
            state,
            CharacterHallEvent.ConfirmDeleteGroup,
        )

        assertNull(transition.effect)
        assertNull(transition.state.groupPendingDeletion)
    }
}
