package com.elio.skillroundtable.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.ui.components.bounceClick
import com.elio.skillroundtable.ui.navigation.AppDestination
import com.elio.skillroundtable.ui.navigation.AppNavHost
import com.elio.skillroundtable.ui.navigation.navigateToSecondary
import com.elio.skillroundtable.ui.navigation.navigateToTelemetryFromRoundtable
import com.elio.skillroundtable.ui.navigation.navigateToTopLevel
import com.elio.skillroundtable.ui.screens.characters.AddEditCharacterDialog
import com.elio.skillroundtable.ui.screens.characters.CharacterHallScreen
import com.elio.skillroundtable.ui.screens.library.AudioLibraryScreen
import com.elio.skillroundtable.ui.screens.roundtable.RoundtableRoute
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
    val allCharacters by viewModel.allCharacters.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    var showAddCharacterDialog by remember { mutableStateOf(false) }
    var editingCharacter by remember { mutableStateOf<Character?>(null) }

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
                    RoundtableRoute(
                        viewModel = viewModel,
                        onOpenApiKeyConfig = {
                            navController.navigateToSecondary(AppDestination.API_KEYS)
                        },
                        onOpenTelemetry = {
                            navController.navigateToTelemetryFromRoundtable()
                        },
                    )
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
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
