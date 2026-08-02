package com.elio.jianyu.ui.screens.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.SlateBg

@Composable
internal fun CharacterHallScreen(
    uiState: CharacterHallUiState,
    onEvent: (CharacterHallEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
            .testTag(CharacterHallTestTags.ROOT),
    ) {
        CharacterHallHeader(
            canSaveCurrentGroup = uiState.canSaveCurrentGroup,
            onSaveGroup = { onEvent(CharacterHallEvent.OpenSaveGroup) },
            onAddCharacter = { onEvent(CharacterHallEvent.AddCharacter) },
        )

        CharacterGroupBar(
            groups = uiState.groups,
            onGroupClick = { item ->
                onEvent(CharacterHallEvent.GroupClicked(item.group))
            },
            onGroupLongClick = { item ->
                onEvent(CharacterHallEvent.GroupLongPressed(item.group))
            },
        )

        Spacer(Modifier.height(12.dp))

        CharacterList(
            state = uiState,
            onCharacterClick = { item ->
                onEvent(CharacterHallEvent.CharacterClicked(item.character))
            },
            onToggleActive = { item ->
                onEvent(CharacterHallEvent.ToggleActive(item.character))
            },
            onEditCharacter = { item ->
                onEvent(CharacterHallEvent.EditCharacter(item.character))
            },
            onDeleteCharacter = { item ->
                onEvent(CharacterHallEvent.DeleteCharacter(item.character))
            },
        )
    }

    uiState.saveGroupForm?.let { form ->
        SaveCharacterGroupDialog(
            form = form,
            onEvent = onEvent,
        )
    }

    uiState.groupPendingDeletion?.let { group ->
        DeleteCharacterGroupDialog(
            group = group,
            onEvent = onEvent,
        )
    }

    uiState.detail?.let { detail ->
        CharacterDetailBottomSheet(
            detail = detail,
            onDismiss = { onEvent(CharacterHallEvent.DismissDetail) },
        )
    }
}
