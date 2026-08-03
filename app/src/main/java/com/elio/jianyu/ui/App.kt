package com.elio.jianyu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elio.jianyu.ui.navigation.AppDestination
import com.elio.jianyu.ui.navigation.AppNavHost
import com.elio.jianyu.ui.navigation.navigateToIssue
import com.elio.jianyu.ui.navigation.navigateToSecondary
import com.elio.jianyu.ui.navigation.navigateToTelemetryFromRoundtable
import com.elio.jianyu.ui.navigation.navigateToTopLevel
import com.elio.jianyu.ui.screens.characters.CharacterHallRoute
import com.elio.jianyu.ui.screens.home.HomeRoute
import com.elio.jianyu.ui.screens.issues.IssueRecoveryRoute
import com.elio.jianyu.ui.screens.issues.IssuesRoute
import com.elio.jianyu.ui.screens.library.AudioLibraryRoute
import com.elio.jianyu.ui.screens.resources.ResourcesRoute
import com.elio.jianyu.ui.screens.roundtable.RoundtableRoute
import com.elio.jianyu.ui.screens.settings.ApiKeyManagerRoute
import com.elio.jianyu.ui.screens.settings.SettingsRoute
import com.elio.jianyu.ui.screens.settings.TelemetryRoute
import com.elio.jianyu.ui.screens.skillplaceholder.SkillDetailPlaceholderRoute
import com.elio.jianyu.ui.screens.skillplaceholder.SkillPlaceholderRoute
import com.elio.jianyu.viewmodel.RoundtableViewModel

object AppTestTags {
    const val BOTTOM_NAVIGATION = "app_bottom_navigation"

    fun destination(destination: AppDestination): String =
        "app_destination_${destination.testTagSuffix}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: RoundtableViewModel = viewModel(),
) {
    val allCharacters by viewModel.allCharacters.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoutePattern = backStackEntry?.destination?.route
    val currentDestination = AppDestination.fromRoutePattern(currentRoutePattern)
        ?: if (currentRoutePattern == null) AppDestination.startDestination else null
    val currentTopLevel = currentDestination?.takeIf { it.showsBottomNavigation }
    val showsBottomNavigation = currentTopLevel != null
    val contentWindowInsets = if (showsBottomNavigation) {
        ScaffoldDefaults.contentWindowInsets
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    Scaffold(
        contentWindowInsets = contentWindowInsets,
        bottomBar = {
            if (currentTopLevel != null) {
                AppBottomNavigation(
                    currentDestination = currentTopLevel,
                    onDestinationSelected = navController::navigateToTopLevel,
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
                homeContent = {
                    HomeRoute(
                        onOpenSettings = {
                            navController.navigateToSecondary(AppDestination.SETTINGS)
                        },
                    )
                },
                issuesContent = {
                    IssuesRoute(
                        onOpenIssue = navController::navigateToIssue,
                        onOpenSettings = {
                            navController.navigateToSecondary(AppDestination.SETTINGS)
                        },
                    )
                },
                issueContent = { issueId, stageId ->
                    IssueRecoveryRoute(
                        issueId = issueId,
                        stageId = stageId,
                        onBack = { navController.popBackStack() },
                    )
                },
                skillsContent = {
                    SkillPlaceholderRoute(
                        onOpenSettings = {
                            navController.navigateToSecondary(AppDestination.SETTINGS)
                        },
                    )
                },
                skillDetailContent = { skillId ->
                    SkillDetailPlaceholderRoute(
                        skillId = skillId,
                        onBack = { navController.popBackStack() },
                    )
                },
                resourcesContent = { tab ->
                    ResourcesRoute(
                        initialTab = tab,
                        onOpenSettings = {
                            navController.navigateToSecondary(AppDestination.SETTINGS)
                        },
                    )
                },
                settingsContent = {
                    SettingsRoute(
                        onBack = { navController.popBackStack() },
                        onOpenApiKeys = {
                            navController.navigateToSecondary(AppDestination.API_KEYS)
                        },
                        onOpenTelemetry = {
                            navController.navigateToSecondary(AppDestination.TELEMETRY)
                        },
                    )
                },
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
                    CharacterHallRoute(
                        viewModel = viewModel,
                        characters = allCharacters,
                    )
                },
                audioLibraryContent = {
                    AudioLibraryRoute(
                        viewModel = viewModel,
                        allCharacters = allCharacters,
                    )
                },
                apiKeysContent = {
                    ApiKeyManagerRoute(
                        currentSessionId = currentSessionId,
                        onBack = { navController.popBackStack() },
                        onOpenTelemetry = {
                            navController.navigateToSecondary(AppDestination.TELEMETRY)
                        },
                    )
                },
                telemetryContent = {
                    TelemetryRoute(
                        currentSessionId = currentSessionId,
                        onBack = { navController.popBackStack() },
                    )
                },
            )
        }
    }
}

@Composable
internal fun AppBottomNavigation(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.testTag(AppTestTags.BOTTOM_NAVIGATION),
    ) {
        AppDestination.topLevelDestinations.forEach { destination ->
            val icon = when (destination) {
                AppDestination.HOME -> Icons.Default.Home
                AppDestination.ISSUES -> Icons.Default.List
                AppDestination.SKILLS -> Icons.Default.Person
                AppDestination.RESOURCES -> Icons.Default.PlayArrow
                else -> error("非一级目的地不能显示在底部导航")
            }
            NavigationBarItem(
                selected = currentDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.testTag(AppTestTags.destination(destination)),
            )
        }
    }
}
