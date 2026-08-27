package com.elio.jianyu.ui.screens.dialog.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.elio.jianyu.ui.screens.dialog.DialogIcons
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.ui.screens.dialog.AddSkillCatalogUiModel
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.DialogTokens
import com.elio.jianyu.ui.screens.dialog.SkillRoleUiModel

/**
 * 增加 Skill 角色大型 Bottom Sheet
 * 对应设计规范第 14.2 节
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSkillRoleBottomSheet(
    isOpen: Boolean,
    catalog: AddSkillCatalogUiModel,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { onEvent(DialogEvent.DismissOverlay) },
            sheetState = sheetState,
            containerColor = DialogTokens.SurfaceWhite,
            shape = RoundedCornerShape(
                topStart = DialogTokens.RadiusSheetTop,
                topEnd = DialogTokens.RadiusSheetTop,
            ),
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(horizontal = 20.dp),
            ) {
                // 1. 标题与关闭按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = "增加 Skill 角色",
                        color = DialogTokens.TextPrimary,
                        fontSize = DialogTokens.FontSheetTitle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DialogTokens.PageBackground)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = { onEvent(DialogEvent.DismissOverlay) },
                            )
                            .align(Alignment.CenterEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = DialogTokens.TextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 搜索框 (胶囊形，居中自适应排版)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(
                            width = 1.dp,
                            color = Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = catalog.searchQuery,
                            onValueChange = { onEvent(DialogEvent.SearchSkillsToAdd(it)) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 13.5.sp,
                                color = DialogTokens.TextPrimary,
                            ),
                            singleLine = true,
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(DialogTokens.BrandPurple),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (catalog.searchQuery.isEmpty()) {
                                        Text(
                                            text = "搜索 Skill 角色",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 13.5.sp,
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (catalog.searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = DialogIcons.Close,
                                contentDescription = "清空",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onEvent(DialogEvent.SearchSkillsToAdd("")) },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. 角色列表内容
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 分组一：最近使用
                    if (catalog.recentUsed.isNotEmpty()) {
                        item {
                            SectionHeader(title = "最近使用")
                        }
                        item {
                            TwoColumnSkillGrid(
                                skills = catalog.recentUsed,
                                onAddSkill = { onEvent(DialogEvent.AddSkillToSession(it)) },
                            )
                        }
                    }

                    // 分组二：推荐角色
                    if (catalog.recommended.isNotEmpty()) {
                        item {
                            SectionHeader(title = "推荐角色")
                        }
                        item {
                            TwoColumnSkillGrid(
                                skills = catalog.recommended,
                                onAddSkill = { onEvent(DialogEvent.AddSkillToSession(it)) },
                            )
                        }
                    }

                    // 分组三：全部角色
                    if (catalog.allSkills.isNotEmpty()) {
                        item {
                            SectionHeader(title = "全部角色")
                        }
                        items(catalog.allSkills, key = { it.id }) { skill ->
                            FullWidthSkillRow(
                                skill = skill,
                                onAddSkill = { onEvent(DialogEvent.AddSkillToSession(skill.id)) },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = DialogTokens.TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * 双列网格卡片展示
 */
@Composable
private fun TwoColumnSkillGrid(
    skills: List<SkillRoleUiModel>,
    onAddSkill: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        skills.chunked(2).forEach { rowSkills ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowSkills.forEach { skill ->
                    MiniSkillGridCard(
                        skill = skill,
                        onAddSkill = { onAddSkill(skill.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowSkills.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniSkillGridCard(
    skill: SkillRoleUiModel,
    onAddSkill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DialogTokens.RadiusCard))
            .background(skill.tintBg)
            .border(
                width = DialogTokens.BorderThin,
                color = skill.tintBorder,
                shape = RoundedCornerShape(DialogTokens.RadiusCard),
            )
            .padding(10.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(skill.tintBorder),
                    contentAlignment = Alignment.Center,
                ) {
                    if (skill.avatarResId != null) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = skill.avatarResId),
                            contentDescription = skill.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = skill.avatarText.take(1),
                            color = skill.accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // 增加按钮 或 已加入标签
                if (skill.isInCurrentSession) {
                    Text(
                        text = "已加入",
                        color = DialogTokens.TextTertiary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(DialogTokens.BorderThin, DialogTokens.BrandPurple, RoundedCornerShape(12.dp))
                            .clickable(onClick = onAddSkill)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "+ 增加",
                            color = DialogTokens.BrandPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = skill.name,
                color = DialogTokens.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = skill.shortDescription,
                color = DialogTokens.TextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FullWidthSkillRow(
    skill: SkillRoleUiModel,
    onAddSkill: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DialogTokens.PageBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(skill.tintBorder),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = skill.avatarText.take(1),
                    color = skill.accentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    color = DialogTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = skill.shortDescription,
                    color = DialogTokens.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (skill.isInCurrentSession) {
            Text(
                text = "已加入",
                color = DialogTokens.TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(DialogTokens.BorderThin, DialogTokens.BrandPurple, RoundedCornerShape(14.dp))
                    .clickable(onClick = onAddSkill)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "+ 增加",
                    color = DialogTokens.BrandPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
