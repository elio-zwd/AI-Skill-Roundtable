package com.elio.skillroundtable.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    roundtableContent: @Composable () -> Unit,
    charactersContent: @Composable () -> Unit,
    audioLibraryContent: @Composable () -> Unit,
    apiKeysContent: @Composable () -> Unit,
    telemetryContent: @Composable () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.startDestination.route,
        modifier = modifier,
    ) {
        composable(AppDestination.ROUNDTABLE.route) {
            roundtableContent()
        }
        composable(AppDestination.CHARACTERS.route) {
            charactersContent()
        }
        composable(AppDestination.AUDIO_LIBRARY.route) {
            audioLibraryContent()
        }
        composable(AppDestination.API_KEYS.route) {
            apiKeysContent()
        }
        composable(AppDestination.TELEMETRY.route) {
            telemetryContent()
        }
    }
}

fun NavHostController.navigateToTopLevel(destination: AppDestination) {
    require(destination.showsBottomNavigation) {
        "${destination.name} 不是顶层目的地"
    }
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavHostController.navigateToSecondary(destination: AppDestination) {
    require(!destination.showsBottomNavigation) {
        "${destination.name} 不是二级目的地"
    }
    navigate(destination.route) {
        launchSingleTop = true
    }
}

fun NavHostController.navigateToTelemetryFromRoundtable() {
    AppDestination.telemetryPathFromRoundtable.forEach(::navigateToSecondary)
}
