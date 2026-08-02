package com.elio.jianyu.ui.screens.characters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.data.Character
import com.elio.jianyu.data.CharacterGroup
import com.elio.jianyu.ui.CardBg
import com.elio.jianyu.ui.GoldAccent
import com.elio.jianyu.ui.PrimaryAccent
import com.elio.jianyu.ui.TextPrimary
import com.elio.jianyu.ui.TextSecondary
import com.elio.jianyu.ui.components.CharacterAvatar
import com.elio.jianyu.ui.components.MarkdownRender

@Composable
internal fun SaveCharacterGroupDialog(
    form: SaveCharacterGroupFormState,
    onEvent: (CharacterHallEvent) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(CharacterHallTestTags.SAVE_GROUP_DIALOG),
        onDismissRequest = { onEvent(CharacterHallEvent.DismissSaveGroup) },
        title = {
            Text(
                "保存当前勾选为自定义分组",
                color = TextPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "将当前所有被选中的智囊席位另存为一个快速启动预设组。",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
                TextField(
                    value = form.name,
                    onValueChange = { value ->
                        onEvent(CharacterHallEvent.SaveGroupNameChanged(value))
                    },
                    placeholder = { Text("分组名称 (如：智能开发组)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextField(
                    value = form.description,
                    onValueChange = { value ->
                        onEvent(CharacterHallEvent.SaveGroupDescriptionChanged(value))
                    },
                    placeholder = { Text("描述信息 (如：精选技术和产品大佬)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onEvent(CharacterHallEvent.ConfirmSaveGroup) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CharacterHallEvent.DismissSaveGroup) }) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = CardBg,
    )
}

@Composable
internal fun DeleteCharacterGroupDialog(
    group: CharacterGroup,
    onEvent: (CharacterHallEvent) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(CharacterHallTestTags.DELETE_GROUP_DIALOG),
        onDismissRequest = { onEvent(CharacterHallEvent.DismissDeleteGroup) },
        title = {
            Text(
                "删除自定义预设分组",
                color = TextPrimary,
            )
        },
        text = {
            Text(
                "确定要删除 [${group.name}] 吗？",
                color = TextSecondary,
            )
        },
        confirmButton = {
            Button(
                onClick = { onEvent(CharacterHallEvent.ConfirmDeleteGroup) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red.copy(alpha = 0.8f),
                ),
            ) {
                Text("确定删除")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(CharacterHallEvent.DismissDeleteGroup) }) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = CardBg,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CharacterDetailBottomSheet(
    detail: CharacterDetailUiState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier.testTag(CharacterHallTestTags.DETAIL_SHEET),
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        contentColor = TextPrimary,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = TextSecondary.copy(alpha = 0.5f),
            )
        },
    ) {
        val character = detail.character
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CharacterAvatar(
                    avatar = character.avatar,
                    name = character.name,
                    size = 80.dp,
                    textSize = 40.sp,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = character.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        text = "席位顺序: 第 ${character.order} 位",
                        fontSize = 12.sp,
                        color = GoldAccent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        PrimaryAccent.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(
                        1.dp,
                        PrimaryAccent.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(16.dp),
            ) {
                Text(
                    text = "“ ${character.tagline} ”",
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    lineHeight = 24.sp,
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "角色思维模型与决策DNA",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            when (val content = detail.content) {
                CharacterDetailContentUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag(CharacterHallTestTags.DETAIL_LOADING),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = PrimaryAccent)
                    }
                }

                CharacterDetailContentUiState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag(CharacterHallTestTags.DETAIL_EMPTY),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "暂无角色思维模型详情",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }

                is CharacterDetailContentUiState.Content -> {
                    Box(modifier = Modifier.testTag(CharacterHallTestTags.DETAIL_CONTENT)) {
                        MarkdownRender(text = content.markdown)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCharacterDialog(
    character: Character?,
    onDismiss: () -> Unit,
    onConfirm: (Character) -> Unit,
) {
    var form by remember(character) {
        mutableStateOf(CharacterFormState.from(character))
    }

    AlertDialog(
        modifier = Modifier.testTag(CharacterHallTestTags.ADD_EDIT_DIALOG),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (character == null) {
                    "录入新智囊入席"
                } else {
                    "修改智囊 [${character.name}] 设定"
                },
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text("智囊名称", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = form.name,
                        onValueChange = { value -> form = form.copy(name = value) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：考研择校官") },
                    )
                }
                item {
                    Text(
                        "智囊头像路径 (Assets)",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    TextField(
                        value = form.avatar,
                        onValueChange = { value -> form = form.copy(avatar = value) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：avatars/elon.jpg") },
                    )
                }
                item {
                    Text(
                        "一句座右铭/一句话简介",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    TextField(
                        value = form.tagline,
                        onValueChange = { value -> form = form.copy(tagline = value) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：用严苛的录取比劝退投机的学子") },
                    )
                }
                item {
                    Text(
                        "角色系统提示词 (System Prompt)",
                        fontSize = 12.sp,
                        color = TextSecondary,
                    )
                    TextField(
                        value = form.systemPrompt,
                        onValueChange = { value -> form = form.copy(systemPrompt = value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = {
                            Text(
                                "输入智囊的学术、商业观点，以及他说话的特定口吻、语气、立场规则。",
                            )
                        },
                        maxLines = 15,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    form.toCharacter()?.let(onConfirm)
                },
                enabled = form.isValid,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            ) {
                Text("确定入席")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = CardBg,
    )
}
