package com.elio.skillroundtable.ui.screens.characters

import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.CharacterGroup

internal const val PROTECTED_CHARACTER_ID = "zhang_xuefeng"

internal object CharacterHallTestTags {
    const val ROOT = "character_hall"
    const val ADD_BUTTON = "character_hall_add_button"
    const val SAVE_GROUP_BUTTON = "character_hall_save_group_button"
    const val GROUP_ROW = "character_group_row"
    const val CHARACTER_LIST = "character_list"
    const val LOADING_STATE = "character_loading_state"
    const val EMPTY_STATE = "character_empty_state"
    const val SAVE_GROUP_DIALOG = "character_save_group_dialog"
    const val DELETE_GROUP_DIALOG = "character_delete_group_dialog"
    const val DETAIL_SHEET = "character_detail_sheet"
    const val DETAIL_LOADING = "character_detail_loading"
    const val DETAIL_EMPTY = "character_detail_empty"
    const val DETAIL_CONTENT = "character_detail_content"
    const val ADD_EDIT_DIALOG = "character_add_edit_dialog"

    fun group(groupId: String) = "character_group_$groupId"
    fun character(characterId: String) = "character_item_$characterId"
    fun status(characterId: String) = "character_status_$characterId"
    fun more(characterId: String) = "character_more_$characterId"
}

internal data class CharacterHallUiState(
    val groups: List<CharacterGroupItemUiState> = emptyList(),
    val characters: List<CharacterItemUiState> = emptyList(),
    val canSaveCurrentGroup: Boolean = false,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val saveGroupForm: SaveCharacterGroupFormState? = null,
    val groupPendingDeletion: CharacterGroup? = null,
    val detail: CharacterDetailUiState? = null,
)

internal data class CharacterGroupItemUiState(
    val group: CharacterGroup,
    val canDelete: Boolean,
)

internal data class CharacterItemUiState(
    val character: Character,
    val statusLabel: String,
    val canDelete: Boolean,
)

internal data class CharacterDetailUiState(
    val character: Character,
    val content: CharacterDetailContentUiState,
)

internal sealed interface CharacterDetailContentUiState {
    data object Loading : CharacterDetailContentUiState
    data object Empty : CharacterDetailContentUiState
    data class Content(val markdown: String) : CharacterDetailContentUiState
}

internal data class SaveCharacterGroupFormState(
    val name: String = "",
    val description: String = "",
) {
    val isValid: Boolean
        get() = name.isNotBlank()
}

internal data class CharacterFormState(
    val source: Character? = null,
    val name: String = "",
    val avatar: String = "🧙",
    val tagline: String = "",
    val systemPrompt: String = "",
) {
    val isValid: Boolean
        get() = name.isNotBlank() && systemPrompt.isNotBlank()

    fun toCharacter(idFactory: () -> String = { "custom_${System.currentTimeMillis()}" }): Character? {
        if (!isValid) return null

        return source?.copy(
            name = name,
            avatar = avatar,
            tagline = tagline,
            systemPrompt = systemPrompt,
        ) ?: Character(
            id = idFactory(),
            name = name,
            avatar = avatar,
            tagline = tagline,
            systemPrompt = systemPrompt,
            skillAssetPath = "",
            order = 10,
            isActive = true,
            skillDescriptionVector = "",
        )
    }

    companion object {
        fun from(character: Character?): CharacterFormState {
            return CharacterFormState(
                source = character,
                name = character?.name.orEmpty(),
                avatar = character?.avatar ?: "🧙",
                tagline = character?.tagline.orEmpty(),
                systemPrompt = character?.systemPrompt.orEmpty(),
            )
        }
    }
}

internal data class CharacterHallOverlayState(
    val saveGroupForm: SaveCharacterGroupFormState? = null,
    val groupPendingDeletion: CharacterGroup? = null,
    val detailCharacter: Character? = null,
)

internal sealed interface CharacterHallEvent {
    data object AddCharacter : CharacterHallEvent
    data object OpenSaveGroup : CharacterHallEvent
    data object DismissSaveGroup : CharacterHallEvent
    data class SaveGroupNameChanged(val value: String) : CharacterHallEvent
    data class SaveGroupDescriptionChanged(val value: String) : CharacterHallEvent
    data object ConfirmSaveGroup : CharacterHallEvent
    data class GroupClicked(val group: CharacterGroup) : CharacterHallEvent
    data class GroupLongPressed(val group: CharacterGroup) : CharacterHallEvent
    data object DismissDeleteGroup : CharacterHallEvent
    data object ConfirmDeleteGroup : CharacterHallEvent
    data class CharacterClicked(val character: Character) : CharacterHallEvent
    data object DismissDetail : CharacterHallEvent
    data class ToggleActive(val character: Character) : CharacterHallEvent
    data class EditCharacter(val character: Character) : CharacterHallEvent
    data class DeleteCharacter(val character: Character) : CharacterHallEvent
}

internal sealed interface CharacterHallEffect {
    data object AddCharacter : CharacterHallEffect
    data class ApplyGroup(val group: CharacterGroup) : CharacterHallEffect
    data class SaveGroup(
        val name: String,
        val description: String,
        val displayName: String,
    ) : CharacterHallEffect
    data object InvalidGroupName : CharacterHallEffect
    data class DeleteGroup(val group: CharacterGroup) : CharacterHallEffect
    data class LoadDetail(val character: Character) : CharacterHallEffect
    data object ClearDetail : CharacterHallEffect
    data class ToggleActive(val character: Character) : CharacterHallEffect
    data class EditCharacter(val character: Character) : CharacterHallEffect
    data class DeleteCharacter(val characterId: String) : CharacterHallEffect
}

internal data class CharacterHallTransition(
    val state: CharacterHallOverlayState,
    val effect: CharacterHallEffect? = null,
)

internal fun mapCharacterHallUiState(
    characters: List<Character>,
    groups: List<CharacterGroup>,
    overlayState: CharacterHallOverlayState,
    detailContent: String?,
): CharacterHallUiState {
    val isLoading = characters.isEmpty() && groups.isEmpty()
    val detail = overlayState.detailCharacter?.let { character ->
        CharacterDetailUiState(
            character = character,
            content = when {
                detailContent == null -> CharacterDetailContentUiState.Loading
                detailContent.isBlank() -> CharacterDetailContentUiState.Empty
                else -> CharacterDetailContentUiState.Content(detailContent)
            },
        )
    }

    return CharacterHallUiState(
        groups = groups.map { group ->
            CharacterGroupItemUiState(
                group = group,
                canDelete = canDeleteGroup(group),
            )
        },
        characters = characters.map { character ->
            CharacterItemUiState(
                character = character,
                statusLabel = if (character.isActive) "入席" else "旁听",
                canDelete = canDeleteCharacter(character),
            )
        },
        canSaveCurrentGroup = characters.any { it.isActive },
        isLoading = isLoading,
        isEmpty = characters.isEmpty() && !isLoading,
        saveGroupForm = overlayState.saveGroupForm,
        groupPendingDeletion = overlayState.groupPendingDeletion,
        detail = detail,
    )
}

internal fun canDeleteGroup(group: CharacterGroup): Boolean = !group.isPreset

internal fun canDeleteCharacter(character: Character): Boolean =
    character.id != PROTECTED_CHARACTER_ID

internal fun reduceCharacterHallEvent(
    state: CharacterHallOverlayState,
    event: CharacterHallEvent,
): CharacterHallTransition {
    return when (event) {
        CharacterHallEvent.AddCharacter -> CharacterHallTransition(
            state = state,
            effect = CharacterHallEffect.AddCharacter,
        )

        CharacterHallEvent.OpenSaveGroup -> CharacterHallTransition(
            state = state.copy(saveGroupForm = SaveCharacterGroupFormState()),
        )

        CharacterHallEvent.DismissSaveGroup -> CharacterHallTransition(
            state = state.copy(saveGroupForm = null),
        )

        is CharacterHallEvent.SaveGroupNameChanged -> CharacterHallTransition(
            state = state.copy(
                saveGroupForm = state.saveGroupForm?.copy(name = event.value),
            ),
        )

        is CharacterHallEvent.SaveGroupDescriptionChanged -> CharacterHallTransition(
            state = state.copy(
                saveGroupForm = state.saveGroupForm?.copy(description = event.value),
            ),
        )

        CharacterHallEvent.ConfirmSaveGroup -> {
            val form = state.saveGroupForm
            when {
                form == null -> CharacterHallTransition(state)
                !form.isValid -> CharacterHallTransition(
                    state = state,
                    effect = CharacterHallEffect.InvalidGroupName,
                )
                else -> CharacterHallTransition(
                    state = state.copy(saveGroupForm = null),
                    effect = CharacterHallEffect.SaveGroup(
                        name = form.name.trim(),
                        description = form.description.trim(),
                        displayName = form.name,
                    ),
                )
            }
        }

        is CharacterHallEvent.GroupClicked -> CharacterHallTransition(
            state = state,
            effect = CharacterHallEffect.ApplyGroup(event.group),
        )

        is CharacterHallEvent.GroupLongPressed -> CharacterHallTransition(
            state = if (canDeleteGroup(event.group)) {
                state.copy(groupPendingDeletion = event.group)
            } else {
                state
            },
        )

        CharacterHallEvent.DismissDeleteGroup -> CharacterHallTransition(
            state = state.copy(groupPendingDeletion = null),
        )

        CharacterHallEvent.ConfirmDeleteGroup -> {
            val group = state.groupPendingDeletion
            if (group == null || !canDeleteGroup(group)) {
                CharacterHallTransition(state.copy(groupPendingDeletion = null))
            } else {
                CharacterHallTransition(
                    state = state.copy(groupPendingDeletion = null),
                    effect = CharacterHallEffect.DeleteGroup(group),
                )
            }
        }

        is CharacterHallEvent.CharacterClicked -> CharacterHallTransition(
            state = state.copy(detailCharacter = event.character),
            effect = CharacterHallEffect.LoadDetail(event.character),
        )

        CharacterHallEvent.DismissDetail -> CharacterHallTransition(
            state = state.copy(detailCharacter = null),
            effect = CharacterHallEffect.ClearDetail,
        )

        is CharacterHallEvent.ToggleActive -> CharacterHallTransition(
            state = state,
            effect = CharacterHallEffect.ToggleActive(event.character),
        )

        is CharacterHallEvent.EditCharacter -> CharacterHallTransition(
            state = state,
            effect = CharacterHallEffect.EditCharacter(event.character),
        )

        is CharacterHallEvent.DeleteCharacter -> CharacterHallTransition(
            state = state,
            effect = if (canDeleteCharacter(event.character)) {
                CharacterHallEffect.DeleteCharacter(event.character.id)
            } else {
                null
            },
        )
    }
}
