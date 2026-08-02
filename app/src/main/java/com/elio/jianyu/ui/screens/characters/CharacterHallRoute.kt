package com.elio.jianyu.ui.screens.characters

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elio.jianyu.data.Character
import com.elio.jianyu.viewmodel.RoundtableViewModel

@Composable
fun CharacterHallRoute(
    viewModel: RoundtableViewModel,
    characters: List<Character>,
) {
    val context = LocalContext.current
    val groups by viewModel.allGroups.collectAsState()
    val detailContent by viewModel.currentDetailSkillContent.collectAsState()
    var overlayState by remember { mutableStateOf(CharacterHallOverlayState()) }
    var isAddingCharacter by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<Character?>(null) }

    val uiState = mapCharacterHallUiState(
        characters = characters,
        groups = groups,
        overlayState = overlayState,
        detailContent = detailContent,
    )

    CharacterHallScreen(
        uiState = uiState,
        onEvent = { event ->
            val transition = reduceCharacterHallEvent(overlayState, event)
            overlayState = transition.state

            when (val effect = transition.effect) {
                null -> Unit
                CharacterHallEffect.AddCharacter -> isAddingCharacter = true
                is CharacterHallEffect.ApplyGroup -> {
                    viewModel.applyCharacterGroup(effect.group)
                    Toast.makeText(
                        context,
                        "已应用角色预设: ${effect.group.name}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is CharacterHallEffect.SaveGroup -> {
                    viewModel.saveCurrentActiveAsGroup(effect.name, effect.description)
                    Toast.makeText(
                        context,
                        "分组 [${effect.displayName}] 已保存！",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                CharacterHallEffect.InvalidGroupName -> {
                    Toast.makeText(context, "请输入分组名称", Toast.LENGTH_SHORT).show()
                }
                is CharacterHallEffect.DeleteGroup -> {
                    viewModel.deleteGroup(effect.group.id)
                    Toast.makeText(
                        context,
                        "分组 [${effect.group.name}] 已删除",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is CharacterHallEffect.LoadDetail -> {
                    viewModel.loadDetailSkill(effect.character, context)
                }
                CharacterHallEffect.ClearDetail -> viewModel.clearDetailSkill()
                is CharacterHallEffect.ToggleActive -> {
                    viewModel.addOrUpdateCharacter(
                        effect.character.copy(isActive = !effect.character.isActive),
                    )
                }
                is CharacterHallEffect.EditCharacter -> {
                    editingCharacter = effect.character
                }
                is CharacterHallEffect.DeleteCharacter -> {
                    viewModel.deleteCharacter(effect.characterId)
                    Toast.makeText(
                        context,
                        "智囊已被移出会议",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
    )

    if (isAddingCharacter) {
        AddEditCharacterDialog(
            character = null,
            onDismiss = { isAddingCharacter = false },
            onConfirm = { newCharacter ->
                viewModel.addOrUpdateCharacter(newCharacter)
                isAddingCharacter = false
                Toast.makeText(context, "新智囊已入席", Toast.LENGTH_SHORT).show()
            },
        )
    }

    editingCharacter?.let { character ->
        AddEditCharacterDialog(
            character = character,
            onDismiss = { editingCharacter = null },
            onConfirm = { updatedCharacter ->
                viewModel.addOrUpdateCharacter(updatedCharacter)
                editingCharacter = null
                Toast.makeText(context, "智囊设定已修改", Toast.LENGTH_SHORT).show()
            },
        )
    }
}
