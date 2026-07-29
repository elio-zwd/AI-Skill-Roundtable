package com.elio.skillroundtable.ui.screens.characters

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.viewmodel.RoundtableViewModel

/**
 * 保留 PR07-B 冻结的调用签名，将旧入口转发到新的 Route 边界。
 */
@Composable
fun CharacterHallScreen(
    viewModel: RoundtableViewModel,
    characters: List<Character>,
    onToggleActive: (Character) -> Unit,
    onEditCharacter: (Character) -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteCharacter: (String) -> Unit,
) {
    CharacterHallRoute(
        viewModel = viewModel,
        characters = characters,
        onToggleActive = onToggleActive,
        onEditCharacter = onEditCharacter,
        onAddCharacter = onAddCharacter,
        onDeleteCharacter = onDeleteCharacter,
    )
}

@Composable
internal fun CharacterHallRoute(
    viewModel: RoundtableViewModel,
    characters: List<Character>,
    onToggleActive: (Character) -> Unit,
    onEditCharacter: (Character) -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteCharacter: (String) -> Unit,
) {
    val context = LocalContext.current
    val groups by viewModel.allGroups.collectAsState()
    val detailContent by viewModel.currentDetailSkillContent.collectAsState()
    var overlayState by remember { mutableStateOf(CharacterHallOverlayState()) }

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
                CharacterHallEffect.AddCharacter -> onAddCharacter()
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
                is CharacterHallEffect.ToggleActive -> onToggleActive(effect.character)
                is CharacterHallEffect.EditCharacter -> onEditCharacter(effect.character)
                is CharacterHallEffect.DeleteCharacter -> onDeleteCharacter(effect.characterId)
            }
        },
    )
}
