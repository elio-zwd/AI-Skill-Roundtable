package com.elio.jianyu.ui.screens.characters

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.CardBg
import com.elio.jianyu.ui.GoldAccent
import com.elio.jianyu.ui.PrimaryAccent
import com.elio.jianyu.ui.SecondaryAccent
import com.elio.jianyu.ui.TextPrimary
import com.elio.jianyu.ui.TextSecondary
import com.elio.jianyu.ui.components.CharacterAvatar
import com.elio.jianyu.ui.components.bounceClick

@Composable
internal fun CharacterHallHeader(
    canSaveCurrentGroup: Boolean,
    onSaveGroup: () -> Unit,
    onAddCharacter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "智囊设定殿堂",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canSaveCurrentGroup) {
                IconButton(
                    onClick = onSaveGroup,
                    modifier = Modifier
                        .size(36.dp)
                        .bounceClick()
                        .testTag(CharacterHallTestTags.SAVE_GROUP_BUTTON),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "存为分组",
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            IconButton(
                onClick = onAddCharacter,
                modifier = Modifier
                    .size(36.dp)
                    .bounceClick()
                    .testTag(CharacterHallTestTags.ADD_BUTTON),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "录入新智囊",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CharacterGroupBar(
    groups: List<CharacterGroupItemUiState>,
    onGroupClick: (CharacterGroupItemUiState) -> Unit,
    onGroupLongClick: (CharacterGroupItemUiState) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag(CharacterHallTestTags.GROUP_ROW),
    ) {
        items(
            items = groups,
            key = { item -> item.group.id },
        ) { item ->
            val group = item.group
            val backgroundColor = if (group.isPreset) {
                PrimaryAccent.copy(alpha = 0.05f)
            } else {
                SecondaryAccent.copy(alpha = 0.05f)
            }
            val strokeColor = if (group.isPreset) PrimaryAccent else SecondaryAccent

            Surface(
                modifier = Modifier
                    .bounceClick()
                    .clip(RoundedCornerShape(6.dp))
                    .combinedClickable(
                        onClick = { onGroupClick(item) },
                        onLongClick = { onGroupLongClick(item) },
                    )
                    .testTag(CharacterHallTestTags.group(group.id)),
                color = backgroundColor,
                border = BorderStroke(1.dp, strokeColor.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(strokeColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = group.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CharacterList(
    state: CharacterHallUiState,
    onCharacterClick: (CharacterItemUiState) -> Unit,
    onToggleActive: (CharacterItemUiState) -> Unit,
    onEditCharacter: (CharacterItemUiState) -> Unit,
    onDeleteCharacter: (CharacterItemUiState) -> Unit,
) {
    when {
        state.isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CharacterHallTestTags.LOADING_STATE),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = PrimaryAccent)
            }
        }

        state.isEmpty -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CharacterHallTestTags.EMPTY_STATE),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无智囊角色",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        else -> {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CharacterHallTestTags.CHARACTER_LIST),
            ) {
                items(
                    items = state.characters,
                    key = { item -> item.character.id },
                ) { item ->
                    CharacterRow(
                        item = item,
                        onClick = { onCharacterClick(item) },
                        onToggleActive = { onToggleActive(item) },
                        onEditCharacter = { onEditCharacter(item) },
                        onDeleteCharacter = { onDeleteCharacter(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterRow(
    item: CharacterItemUiState,
    onClick: () -> Unit,
    onToggleActive: () -> Unit,
    onEditCharacter: () -> Unit,
    onDeleteCharacter: () -> Unit,
) {
    val character = item.character
    var showMoreMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick()
            .clickable(onClick = onClick)
            .testTag(CharacterHallTestTags.character(character.id)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, PrimaryAccent.copy(alpha = 0.15f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                CharacterAvatar(
                    avatar = character.avatar,
                    name = character.name,
                    size = 42.dp,
                    textSize = 20.sp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = character.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = character.tagline,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CharacterStatusCapsule(
                    item = item,
                    onClick = onToggleActive,
                )

                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .size(24.dp)
                            .bounceClick()
                            .testTag(CharacterHallTestTags.more(character.id)),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多选项",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("修改设定") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onEditCharacter()
                            },
                        )
                        if (item.canDelete) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "请离会议",
                                        color = Color.Red.copy(alpha = 0.8f),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.Red.copy(alpha = 0.8f),
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    onDeleteCharacter()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterStatusCapsule(
    item: CharacterItemUiState,
    onClick: () -> Unit,
) {
    val isActive = item.character.isActive
    val capsuleBackground = if (isActive) {
        SecondaryAccent.copy(alpha = 0.08f)
    } else {
        Color.Transparent
    }
    val capsuleBorderColor = if (isActive) {
        SecondaryAccent.copy(alpha = 0.4f)
    } else {
        TextSecondary.copy(alpha = 0.3f)
    }
    val capsuleTextColor = if (isActive) SecondaryAccent else TextSecondary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(capsuleBackground)
            .border(0.5.dp, capsuleBorderColor, RoundedCornerShape(6.dp))
            .bounceClick()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(CharacterHallTestTags.status(item.character.id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.statusLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = capsuleTextColor,
        )
    }
}
