package com.elio.skillroundtable.ui.screens.characters

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.CharacterGroup
import com.elio.skillroundtable.ui.CardBg
import com.elio.skillroundtable.ui.GoldAccent
import com.elio.skillroundtable.ui.PrimaryAccent
import com.elio.skillroundtable.ui.SecondaryAccent
import com.elio.skillroundtable.ui.SlateBg
import com.elio.skillroundtable.ui.TextPrimary
import com.elio.skillroundtable.ui.TextSecondary
import com.elio.skillroundtable.ui.components.CharacterAvatar
import com.elio.skillroundtable.ui.components.MarkdownRender
import com.elio.skillroundtable.ui.components.bounceClick
import com.elio.skillroundtable.viewmodel.RoundtableViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CharacterHallScreen(
    viewModel: RoundtableViewModel,
    characters: List<Character>,
    onToggleActive: (Character) -> Unit,
    onEditCharacter: (Character) -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteCharacter: (String) -> Unit
) {
    val context = LocalContext.current
    val groups by viewModel.allGroups.collectAsState()
    val detailContent by viewModel.currentDetailSkillContent.collectAsState()

    var showSaveGroupDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }

    var groupToDelete by remember { mutableStateOf<CharacterGroup?>(null) }
    var detailCharacter by remember { mutableStateOf<Character?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "智囊设定殿堂",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (characters.any { it.isActive }) {
                    IconButton(
                        onClick = {
                            groupName = ""
                            groupDesc = ""
                            showSaveGroupDialog = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .bounceClick()
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "存为分组",
                            tint = GoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onAddCharacter,
                    modifier = Modifier
                        .size(36.dp)
                        .bounceClick()
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "录入新智囊",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            items(groups) { group ->
                val bgColor = if (group.isPreset) PrimaryAccent.copy(alpha = 0.05f) else SecondaryAccent.copy(alpha = 0.05f)
                val strokeColor = if (group.isPreset) PrimaryAccent else SecondaryAccent
                Surface(
                    modifier = Modifier
                        .bounceClick()
                        .clip(RoundedCornerShape(6.dp))
                        .combinedClickable(
                            onClick = {
                                viewModel.applyCharacterGroup(group)
                                Toast.makeText(context, "已应用角色预设: ${group.name}", Toast.LENGTH_SHORT).show()
                            },
                            onLongClick = {
                                if (!group.isPreset) {
                                    groupToDelete = group
                                }
                            }
                        ),
                    color = bgColor,
                    border = BorderStroke(1.dp, strokeColor.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(strokeColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = group.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = characters,
                key = { it.id }
            ) { char ->
                var showMoreMenu by remember { mutableStateOf(false) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .bounceClick()
                        .clickable {
                            detailCharacter = char
                            viewModel.loadDetailSkill(char, context)
                        },
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, PrimaryAccent.copy(alpha = 0.15f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            CharacterAvatar(
                                avatar = char.avatar,
                                name = char.name,
                                size = 42.dp,
                                textSize = 20.sp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = char.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = char.tagline,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isActive = char.isActive
                            val capsuleBg = if (isActive) SecondaryAccent.copy(alpha = 0.08f) else Color.Transparent
                            val capsuleBorderColor = if (isActive) SecondaryAccent.copy(alpha = 0.4f) else TextSecondary.copy(alpha = 0.3f)
                            val capsuleTextColor = if (isActive) SecondaryAccent else TextSecondary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(capsuleBg)
                                    .border(0.5.dp, capsuleBorderColor, RoundedCornerShape(6.dp))
                                    .bounceClick()
                                    .clickable { onToggleActive(char) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isActive) "入席" else "旁听",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = capsuleTextColor
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.size(24.dp).bounceClick()
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "更多选项",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("修改设定") },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        onClick = {
                                            showMoreMenu = false
                                            onEditCharacter(char)
                                        }
                                    )
                                    if (char.id != "zhang_xuefeng") {
                                        DropdownMenuItem(
                                            text = { Text("请离会议", color = Color.Red.copy(alpha = 0.8f)) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red.copy(alpha = 0.8f)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onDeleteCharacter(char.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveGroupDialog) {
        AlertDialog(
            onDismissRequest = { showSaveGroupDialog = false },
            title = { Text("保存当前勾选为自定义分组", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将当前所有被选中的智囊席位另存为一个快速启动预设组。", color = TextSecondary, fontSize = 13.sp)
                    TextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        placeholder = { Text("分组名称 (如：智能开发组)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    TextField(
                        value = groupDesc,
                        onValueChange = { groupDesc = it },
                        placeholder = { Text("描述信息 (如：精选技术和产品大佬)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            viewModel.saveCurrentActiveAsGroup(groupName.trim(), groupDesc.trim())
                            showSaveGroupDialog = false
                            Toast.makeText(context, "分组 [${groupName}] 已保存！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "请输入分组名称", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveGroupDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardBg
        )
    }

    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("删除自定义预设分组", color = TextPrimary) },
            text = { Text("确定要删除 [${groupToDelete!!.name}] 吗？", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGroup(groupToDelete!!.id)
                        Toast.makeText(context, "分组 [${groupToDelete!!.name}] 已删除", Toast.LENGTH_SHORT).show()
                        groupToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                ) {
                    Text("确定删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = CardBg
        )
    }

    if (detailCharacter != null) {
        ModalBottomSheet(
            onDismissRequest = {
                detailCharacter = null
                viewModel.clearDetailSkill()
            },
            containerColor = CardBg,
            contentColor = TextPrimary,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary.copy(alpha = 0.5f)) }
        ) {
            val char = detailCharacter!!
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CharacterAvatar(
                        avatar = char.avatar,
                        name = char.name,
                        size = 80.dp,
                        textSize = 40.sp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = char.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "席位顺序: 第 ${char.order} 位",
                            fontSize = 12.sp,
                            color = GoldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryAccent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .border(1.dp, PrimaryAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "“ ${char.tagline} ”",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        lineHeight = 24.sp
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "角色思维模型与决策DNA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (detailContent == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryAccent)
                    }
                } else {
                    MarkdownRender(text = detailContent!!)
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCharacterDialog(
    character: Character?,
    onDismiss: () -> Unit,
    onConfirm: (Character) -> Unit
) {
    var name by remember { mutableStateOf(character?.name ?: "") }
    var avatar by remember { mutableStateOf(character?.avatar ?: "🧙") }
    var tagline by remember { mutableStateOf(character?.tagline ?: "") }
    var systemPrompt by remember { mutableStateOf(character?.systemPrompt ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (character == null) "录入新智囊入席" else "修改智囊 [${character.name}] 设定"
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("智囊名称", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：考研择校官") }
                    )
                }
                item {
                    Text("智囊头像路径 (Assets)", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = avatar,
                        onValueChange = { avatar = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：avatars/elon.jpg") }
                    )
                }
                item {
                    Text("一句座右铭/一句话简介", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例：用严苛的录取比劝退投机的学子") }
                    )
                }
                item {
                    Text("角色系统提示词 (System Prompt)", fontSize = 12.sp, color = TextSecondary)
                    TextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        placeholder = { Text("输入智囊的学术、商业观点，以及他说话的特定口吻、语气、立场规则。") },
                        maxLines = 15
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        val newId = character?.id ?: "custom_${System.currentTimeMillis()}"
                        val newOrder = character?.order ?: 10
                        onConfirm(
                            Character(
                                id = newId,
                                name = name,
                                avatar = avatar,
                                tagline = tagline,
                                systemPrompt = systemPrompt,
                                skillAssetPath = character?.skillAssetPath ?: "",
                                order = newOrder,
                                isActive = character?.isActive ?: true,
                                skillDescriptionVector = character?.skillDescriptionVector ?: ""
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text("确定入席")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = CardBg
    )
}
