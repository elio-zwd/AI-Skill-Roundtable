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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.ChatSession
import com.elio.skillroundtable.ui.components.bounceClick
import com.elio.skillroundtable.ui.navigation.AppDestination
import com.elio.skillroundtable.ui.navigation.AppNavHost
import com.elio.skillroundtable.ui.navigation.navigateToSecondary
import com.elio.skillroundtable.ui.navigation.navigateToTelemetryFromRoundtable
import com.elio.skillroundtable.ui.navigation.navigateToTopLevel
import com.elio.skillroundtable.ui.screens.characters.AddEditCharacterDialog
import com.elio.skillroundtable.ui.screens.characters.CharacterHallScreen
import com.elio.skillroundtable.ui.screens.library.AudioLibraryScreen
import com.elio.skillroundtable.ui.screens.roundtable.RoundtableBrainstormScreen
import com.elio.skillroundtable.ui.screens.settings.ApiKeyManagerScreen
import com.elio.skillroundtable.ui.screens.settings.ApiTelemetryScreen
import com.elio.skillroundtable.ui.theme.skillRoundtableColors
import com.elio.skillroundtable.ui.theme.skillRoundtableSpacing
import com.elio.skillroundtable.viewmodel.RoundtableViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: RoundtableViewModel = viewModel(),
) {
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

    var showAddCharacterDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<Character?>(null) }
    var showDrawer by remember { mutableStateOf(false) }
    var renameSessionId by remember { mutableStateOf<Long?>(null) }
    var renameSessionTitle by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = AppDestination.fromRoute(backStackEntry?.destination?.route)
        ?: AppDestination.startDestination
    val showsBottomNavigation = currentDestination.showsBottomNavigation
    val contentWindowInsets = if (showsBottomNavigation) {
        ScaffoldDefaults.contentWindowInsets
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    Scaffold(
        contentWindowInsets = contentWindowInsets,
        bottomBar = {
            if (showsBottomNavigation) {
                AppBottomNavigation(
                    currentDestination = currentDestination,
                    onDestinationSelected = { destination ->
                        navController.navigateToTopLevel(destination)
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize(),
                roundtableContent = {
                    Box(modifier = Modifier.fillMaxSize()) {
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
                            onSearchModeChange = viewModel::setSearchMode,
                            onOpenApiKeyConfig = {
                                navController.navigateToSecondary(AppDestination.API_KEYS)
                            },
                            onToggleDrawer = { showDrawer = !showDrawer },
                            onRenameSession = { id, title ->
                                renameSessionId = id
                                renameSessionTitle = title
                                showRenameDialog = true
                            },
                        )

                        SessionDrawer(
                            visible = showDrawer,
                            allSessions = allSessions,
                            currentSessionId = currentSessionId,
                            isAutoNextEnabled = isAutoNextEnabled,
                            isSemanticRoutingEnabled = isSemanticRoutingEnabled,
                            onDismiss = { showDrawer = false },
                            onCreateSession = {
                                viewModel.createNewSession(
                                    "关于新概念的圆桌会议 #${allSessions.size + 1}",
                                )
                                showDrawer = false
                            },
                            onSelectSession = { sessionId ->
                                viewModel.selectSession(sessionId)
                                showDrawer = false
                            },
                            onDeleteSession = viewModel::deleteSession,
                            onRenameSession = { sessionId, title ->
                                renameSessionId = sessionId
                                renameSessionTitle = title
                                showRenameDialog = true
                            },
                            onAutoNextEnabledChange = viewModel::setAutoNextEnabled,
                            onSemanticRoutingEnabledChange = viewModel::setSemanticRoutingEnabled,
                            onOpenTelemetry = {
                                showDrawer = false
                                navController.navigateToTelemetryFromRoundtable()
                            },
                        )
                    }
                },
                charactersContent = {
                    CharacterHallScreen(
                        viewModel = viewModel,
                        characters = allCharacters,
                        onToggleActive = { character ->
                            viewModel.addOrUpdateCharacter(
                                character.copy(isActive = !character.isActive),
                            )
                        },
                        onEditCharacter = { character -> editingCharacter = character },
                        onAddCharacter = { showAddCharacterDialog = true },
                        onDeleteCharacter = { characterId ->
                            viewModel.deleteCharacter(characterId)
                            Toast.makeText(
                                context,
                                "智囊已被移出会议",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                },
                audioLibraryContent = {
                    AudioLibraryScreen(
                        viewModel = viewModel,
                        allCharacters = allCharacters,
                    )
                },
                apiKeysContent = {
                    ApiKeyManagerScreen(
                        currentSessionId = currentSessionId,
                        onBack = { navController.popBackStack() },
                        onOpenTelemetry = {
                            navController.navigateToSecondary(AppDestination.TELEMETRY)
                        },
                    )
                },
                telemetryContent = {
                    ApiTelemetryScreen(
                        currentSessionId = currentSessionId,
                        onBack = { navController.popBackStack() },
                    )
                },
            )

            if (showsBottomNavigation && errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError) {
                            Text("确定", color = Color.Yellow)
                        }
                    },
                ) {
                    Text(errorMessage.orEmpty())
                }
            }

            if (showsBottomNavigation && showAddCharacterDialog) {
                AddEditCharacterDialog(
                    character = null,
                    onDismiss = { showAddCharacterDialog = false },
                    onConfirm = { newCharacter ->
                        viewModel.addOrUpdateCharacter(newCharacter)
                        showAddCharacterDialog = false
                        Toast.makeText(context, "新智囊已入席", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (showsBottomNavigation && editingCharacter != null) {
                AddEditCharacterDialog(
                    character = editingCharacter,
                    onDismiss = { editingCharacter = null },
                    onConfirm = { updatedCharacter ->
                        viewModel.addOrUpdateCharacter(updatedCharacter)
                        editingCharacter = null
                        Toast.makeText(context, "智囊设定已修改", Toast.LENGTH_SHORT).show()
                    },
                )
            }

            if (showsBottomNavigation && showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = {
                        Text(
                            "重命名会议主题",
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    text = {
                        OutlinedTextField(
                            value = renameSessionTitle,
                            onValueChange = { renameSessionTitle = it },
                            label = { Text("新主题") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val sessionId = renameSessionId
                                if (renameSessionTitle.isNotBlank() && sessionId != null) {
                                    viewModel.renameSession(sessionId, renameSessionTitle)
                                    showRenameDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRenameDialog = false }) {
                            Text(
                                "取消",
                                color = MaterialTheme.skillRoundtableColors.textSecondary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AppBottomNavigation(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    val spacing = MaterialTheme.skillRoundtableSpacing
    val appColors = MaterialTheme.skillRoundtableColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(appColors.divider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.bottomNavigationHeight)
                .padding(horizontal = spacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            AppDestination.topLevelDestinations.forEach { destination ->
                val isSelected = currentDestination == destination
                val activeColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    appColors.textSecondary.copy(alpha = 0.6f)
                }
                val icon = when (destination) {
                    AppDestination.ROUNDTABLE -> Icons.Default.Home
                    AppDestination.CHARACTERS -> Icons.Default.Person
                    AppDestination.AUDIO_LIBRARY -> Icons.Default.PlayArrow
                    AppDestination.API_KEYS,
                    AppDestination.TELEMETRY,
                    -> error("二级目的地不能显示在底部导航")
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .bounceClick()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onDestinationSelected(destination)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = destination.label,
                        tint = activeColor,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = destination.label,
                        color = activeColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawer(
    visible: Boolean,
    allSessions: List<ChatSession>,
    currentSessionId: Long?,
    isAutoNextEnabled: Boolean,
    isSemanticRoutingEnabled: Boolean,
    onDismiss: () -> Unit,
    onCreateSession: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onAutoNextEnabledChange: (Boolean) -> Unit,
    onSemanticRoutingEnabledChange: (Boolean) -> Unit,
    onOpenTelemetry: () -> Unit,
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
                .clickable(onClick = onDismiss),
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
                    onClick = onCreateSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .bounceClick()
                        .testTag("new_session_button"),
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
                        items = allSessions,
                        key = { session -> session.id },
                    ) { session ->
                        val isSelected = session.id == currentSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onLongClick = {
                                        onRenameSession(session.id, session.title)
                                    },
                                    onClick = { onSelectSession(session.id) },
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
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    appColors.textSecondary
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = { onDeleteSession(session.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = Color.Red.copy(alpha = 0.7f),
                                )
                            }
                        }
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "自动顺延发言 (TTS播毕)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(
                            checked = isAutoNextEnabled,
                            onCheckedChange = onAutoNextEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(
                                    alpha = 0.3f,
                                ),
                            ),
                            modifier = Modifier.scale(0.7f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "专家自适应排序 (余弦路由)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Switch(
                            checked = isSemanticRoutingEnabled,
                            onCheckedChange = onSemanticRoutingEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.secondary,
                                checkedTrackColor = MaterialTheme.colorScheme.secondary.copy(
                                    alpha = 0.3f,
                                ),
                            ),
                            modifier = Modifier.scale(0.7f),
                        )
                    }
                    Divider(
                        color = appColors.textSecondary.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick()
                            .clickable(onClick = onOpenTelemetry)
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
