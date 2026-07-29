package com.elio.skillroundtable.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.ui.components.bounceClick
import com.elio.skillroundtable.ui.screens.characters.AddEditCharacterDialog
import com.elio.skillroundtable.ui.screens.characters.CharacterHallScreen
import com.elio.skillroundtable.ui.screens.library.AudioLibraryScreen
import com.elio.skillroundtable.ui.screens.roundtable.RoundtableBrainstormScreen
import com.elio.skillroundtable.ui.screens.settings.ApiKeyManagerScreen
import com.elio.skillroundtable.ui.screens.settings.ApiTelemetryScreen
import com.elio.skillroundtable.viewmodel.RoundtableViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent() {
    val viewModel: RoundtableViewModel = viewModel()
    val allSessions by viewModel.allSessions.collectAsState()
    val allCharacters by viewModel.allCharacters.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val isRoundtableRunning by viewModel.isRoundtableRunning.collectAsState()
    val typingCharacterIds by viewModel.typingCharacterIds.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val apiKeySummaries by viewModel.apiKeySummaries.collectAsState()
    val isAutoNextEnabled by viewModel.isAutoNextEnabled.collectAsState()
    val isSemanticRoutingEnabled by viewModel.isSemanticRoutingEnabled.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val roundActionState by viewModel.roundActionState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddCharacterDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<Character?>(null) }
    var showApiKeyManagerScreen by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }

    var renameSessionId by remember { mutableStateOf<Long?>(null) }
    var renameSessionTitle by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showTelemetryScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current

    if (showTelemetryScreen) {
        ApiTelemetryScreen(
            currentSessionId = currentSessionId,
            onBack = { showTelemetryScreen = false }
        )
    } else if (showApiKeyManagerScreen) {
        ApiKeyManagerScreen(
            currentSessionId = currentSessionId,
            onBack = { showApiKeyManagerScreen = false },
            onOpenTelemetry = { showTelemetryScreen = true }
        )
    } else {
        Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlateBg)
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFF232D42))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val tabs = listOf(
                            Triple("圆桌脑暴", Icons.Default.Home, 0),
                            Triple("智囊大厅", Icons.Default.Person, 1),
                            Triple("音频库", Icons.Default.PlayArrow, 2)
                        )
                        tabs.forEach { (label, icon, index) ->
                            val isSelected = selectedTab == index
                            val activeColor = if (isSelected) PrimaryAccent else TextSecondary.copy(alpha = 0.6f)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .bounceClick()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { selectedTab = index },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = activeColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = label,
                                    color = activeColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> {
                        RoundtableBrainstormScreen(
                            viewModel = viewModel,
                            allSessions = allSessions,
                            currentSession = currentSession,
                            currentMessages = currentMessages,
                            allCharacters = allCharacters,
                            isRoundtableRunning = isRoundtableRunning,
                            typingCharacterIds = typingCharacterIds,
                            hasApiKeys = apiKeySummaries.any { it.enabled },
                            isAutoNextEnabled = isAutoNextEnabled,
                            isSemanticRoutingEnabled = isSemanticRoutingEnabled,
                            searchMode = searchMode,
                            roundActionState = roundActionState,
                            onSearchModeChange = { viewModel.setSearchMode(it) },
                            onOpenApiKeyConfig = { showApiKeyManagerScreen = true },
                            onToggleDrawer = { showDrawer = !showDrawer },
                            onRenameSession = { id, title ->
                                renameSessionId = id
                                renameSessionTitle = title
                                showRenameDialog = true
                            }
                        )
                    }
                    1 -> {
                        CharacterHallScreen(
                            viewModel = viewModel,
                            characters = allCharacters,
                            onToggleActive = { char ->
                                viewModel.addOrUpdateCharacter(char.copy(isActive = !char.isActive))
                            },
                            onEditCharacter = { char ->
                                editingCharacter = char
                            },
                            onAddCharacter = { showAddCharacterDialog = true },
                            onDeleteCharacter = { id ->
                                viewModel.deleteCharacter(id)
                                Toast.makeText(context, "智囊已被移出会议", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    2 -> {
                        AudioLibraryScreen(
                            viewModel = viewModel,
                            allCharacters = allCharacters
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showDrawer && selectedTab == 0,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it })
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { showDrawer = false }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(300.dp)
                                .background(CardBg)
                                .clickable(enabled = false) {}
                        ) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "脑暴会议历史",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = PrimaryAccent
                            )
                            Divider(modifier = Modifier.padding(vertical = 8.dp), color = TextSecondary.copy(alpha = 0.2f))

                            Button(
                                onClick = {
                                    viewModel.createNewSession("关于新概念的圆桌会议 #${allSessions.size + 1}")
                                    showDrawer = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .bounceClick()
                                    .testTag("new_session_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "新建会议")
                                Spacer(Modifier.width(8.dp))
                                Text("开启全新圆桌脑暴")
                            }

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(
                                    items = allSessions,
                                    key = { it.id }
                                ) { session ->
                                    val isSelected = session.id == currentSessionId
                                    @OptIn(ExperimentalFoundationApi::class)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onLongClick = {
                                                    renameSessionId = session.id
                                                    renameSessionTitle = session.title
                                                    showRenameDialog = true
                                                },
                                                onClick = {
                                                    viewModel.selectSession(session.id)
                                                    showDrawer = false
                                                }
                                            )
                                            .background(if (isSelected) PrimaryAccent.copy(alpha = 0.2f) else Color.Transparent)
                                            .padding(horizontal = 24.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (isSelected) PrimaryAccent else TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title,
                                                fontSize = 15.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = TextPrimary
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red.copy(alpha = 0.7f))
                                        }
                                    }
                                }
                            }
                            Divider(color = TextSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "圆桌脑暴设置",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("自动顺延发言 (TTS播毕)", fontSize = 12.sp, color = TextPrimary)
                                    Switch(
                                        checked = isAutoNextEnabled,
                                        onCheckedChange = { viewModel.setAutoNextEnabled(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SecondaryAccent,
                                            checkedTrackColor = SecondaryAccent.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("专家自适应排序 (余弦路由)", fontSize = 12.sp, color = TextPrimary)
                                    Switch(
                                        checked = isSemanticRoutingEnabled,
                                        onCheckedChange = { viewModel.setSemanticRoutingEnabled(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = SecondaryAccent,
                                            checkedTrackColor = SecondaryAccent.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                                Divider(color = TextSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick()
                                        .clickable {
                                            showTelemetryScreen = true
                                            showDrawer = false
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("熔断诊断与遥测日志", fontSize = 12.sp, color = TextPrimary)
                                    }
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        action = {
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text("确定", color = Color.Yellow)
                            }
                        }
                    ) {
                        Text(errorMessage ?: "")
                    }
                }

                if (showAddCharacterDialog) {
                    AddEditCharacterDialog(
                        character = null,
                        onDismiss = { showAddCharacterDialog = false },
                        onConfirm = { newChar ->
                            viewModel.addOrUpdateCharacter(newChar)
                            showAddCharacterDialog = false
                            Toast.makeText(context, "新智囊已入席", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (editingCharacter != null) {
                    AddEditCharacterDialog(
                        character = editingCharacter,
                        onDismiss = { editingCharacter = null },
                        onConfirm = { updatedChar ->
                            viewModel.addOrUpdateCharacter(updatedChar)
                            editingCharacter = null
                            Toast.makeText(context, "智囊设定已修改", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                if (showRenameDialog) {
                    AlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = { Text("重命名会议主题", color = TextPrimary) },
                        text = {
                            OutlinedTextField(
                                value = renameSessionTitle,
                                onValueChange = { renameSessionTitle = it },
                                label = { Text("新主题") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (renameSessionTitle.isNotBlank() && renameSessionId != null) {
                                        viewModel.renameSession(renameSessionId!!, renameSessionTitle)
                                        showRenameDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Text("保存")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = false }) {
                                Text("取消", color = TextSecondary)
                            }
                        }
                    )
                }
            }
        }
    }
}
