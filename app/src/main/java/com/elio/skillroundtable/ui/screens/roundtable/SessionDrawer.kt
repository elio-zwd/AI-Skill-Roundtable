package com.elio.skillroundtable.ui.screens.roundtable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.skillroundtable.data.ChatSession
import com.elio.skillroundtable.ui.components.bounceClick
import com.elio.skillroundtable.ui.theme.skillRoundtableColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionDrawer(
    visible: Boolean,
    sessions: List<ChatSession>,
    currentSessionId: Long?,
    isAutoNextEnabled: Boolean,
    isSemanticRoutingEnabled: Boolean,
    onEvent: (RoundtableEvent) -> Unit,
) {
    val appColors = MaterialTheme.skillRoundtableColors

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onEvent(RoundtableEvent.DismissDrawer) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = false) {},
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "脑暴会议历史",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Divider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = appColors.textSecondary.copy(alpha = 0.2f),
                )

                Button(
                    onClick = { onEvent(RoundtableEvent.CreateSession) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .bounceClick()
                        .testTag(RoundtableTestTags.NEW_SESSION_BUTTON),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新建会议")
                    Spacer(Modifier.width(8.dp))
                    Text("开启全新圆桌脑暴")
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        items = sessions,
                        key = { session -> session.id },
                    ) { session ->
                        SessionDrawerItem(
                            session = session,
                            isSelected = session.id == currentSessionId,
                            onEvent = onEvent,
                        )
                    }
                }

                Divider(
                    color = appColors.textSecondary.copy(alpha = 0.2f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "圆桌脑暴设置",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textSecondary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    DrawerSettingSwitch(
                        label = "自动顺延发言 (TTS播毕)",
                        checked = isAutoNextEnabled,
                        onCheckedChange = {
                            onEvent(RoundtableEvent.AutoNextChanged(it))
                        },
                    )
                    DrawerSettingSwitch(
                        label = "专家自适应排序 (余弦路由)",
                        checked = isSemanticRoutingEnabled,
                        onCheckedChange = {
                            onEvent(RoundtableEvent.SemanticRoutingChanged(it))
                        },
                    )
                    Divider(
                        color = appColors.textSecondary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick()
                            .clickable { onEvent(RoundtableEvent.OpenTelemetry) }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "熔断诊断与遥测日志",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawerItem(
    session: ChatSession,
    isSelected: Boolean,
    onEvent: (RoundtableEvent) -> Unit,
) {
    val appColors = MaterialTheme.skillRoundtableColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = {
                    onEvent(RoundtableEvent.RequestRename(session.id, session.title))
                },
                onClick = { onEvent(RoundtableEvent.SelectSession(session.id)) },
            )
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                },
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else appColors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = session.title,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onEvent(RoundtableEvent.DeleteSession(session.id)) }) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = Color.Red.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun DrawerSettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
            ),
            modifier = Modifier.scale(0.7f),
        )
    }
}

@Composable
internal fun RenameSessionDialog(
    state: RenameSessionUiState,
    onEvent: (RoundtableEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(RoundtableEvent.DismissRename) },
        title = {
            Text(
                "重命名会议主题",
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            OutlinedTextField(
                value = state.title,
                onValueChange = { onEvent(RoundtableEvent.RenameTitleChanged(it)) },
                label = { Text("新主题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onEvent(RoundtableEvent.ConfirmRename) },
                enabled = state.title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(RoundtableEvent.DismissRename) }) {
                Text(
                    "取消",
                    color = MaterialTheme.skillRoundtableColors.textSecondary,
                )
            }
        },
    )
}
