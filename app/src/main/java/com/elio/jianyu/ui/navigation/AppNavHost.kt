package com.elio.jianyu.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    homeContent: @Composable () -> Unit,
    issuesContent: @Composable () -> Unit,
    issueContent: @Composable (issueId: String?, stageId: String?) -> Unit,
    skillsContent: @Composable () -> Unit,
    skillDetailContent: @Composable (skillId: String?) -> Unit,
    resourcesContent: @Composable (ResourceTab) -> Unit,
    settingsContent: @Composable () -> Unit,
    roundtableContent: @Composable () -> Unit,
    charactersContent: @Composable () -> Unit,
    audioLibraryContent: @Composable () -> Unit,
    apiKeysContent: @Composable () -> Unit,
    telemetryContent: @Composable () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.startDestination.routePattern,
        modifier = modifier,
    ) {
        composable(AppDestination.HOME.routePattern) {
            homeContent()
        }
        navigation(
            route = JianyuNavigationRoutes.ISSUES_GRAPH,
            startDestination = AppDestination.ISSUES.routePattern,
        ) {
            composable(AppDestination.ISSUES.routePattern) {
                issuesContent()
            }
            composable(
                route = JianyuNavigationRoutes.ISSUE_DETAIL_PATTERN,
                arguments = listOf(
                    navArgument(JianyuNavigationRoutes.ISSUE_ID_ARGUMENT) {
                        type = NavType.StringType
                    },
                    navArgument(JianyuNavigationRoutes.STAGE_ID_ARGUMENT) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = JianyuNavigationRoutes.ISSUE_DEEP_LINK_PATTERN
                    },
                ),
            ) { entry ->
                issueContent(
                    entry.arguments?.getString(JianyuNavigationRoutes.ISSUE_ID_ARGUMENT),
                    entry.arguments?.getString(JianyuNavigationRoutes.STAGE_ID_ARGUMENT),
                )
            }
        }
        navigation(
            route = JianyuNavigationRoutes.SKILLS_GRAPH,
            startDestination = AppDestination.SKILLS.routePattern,
        ) {
            composable(AppDestination.SKILLS.routePattern) {
                skillsContent()
            }
            composable(
                route = JianyuNavigationRoutes.SKILL_DETAIL_PATTERN,
                arguments = listOf(
                    navArgument(JianyuNavigationRoutes.SKILL_ID_ARGUMENT) {
                        type = NavType.StringType
                    },
                ),
                deepLinks = listOf(
                    navDeepLink {
                        uriPattern = JianyuNavigationRoutes.SKILL_DEEP_LINK_PATTERN
                    },
                ),
            ) { entry ->
                skillDetailContent(
                    entry.arguments?.getString(JianyuNavigationRoutes.SKILL_ID_ARGUMENT),
                )
            }
        }
        composable(
            route = AppDestination.RESOURCES.routePattern,
            arguments = listOf(
                navArgument(JianyuNavigationRoutes.RESOURCE_TAB_ARGUMENT) {
                    type = NavType.StringType
                    defaultValue = ResourceTab.MATERIALS.routeValue
                },
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = JianyuNavigationRoutes.RESOURCES_DEEP_LINK_PATTERN
                },
            ),
        ) { entry ->
            resourcesContent(
                ResourceTab.fromRouteValue(
                    entry.arguments?.getString(JianyuNavigationRoutes.RESOURCE_TAB_ARGUMENT),
                ),
            )
        }
        composable(AppDestination.SETTINGS.routePattern) {
            settingsContent()
        }

        // 旧页面仅作为兼容 Route 保留，不再出现在新底部导航。
        composable(AppDestination.ROUNDTABLE.routePattern) {
            roundtableContent()
        }
        composable(AppDestination.CHARACTERS.routePattern) {
            charactersContent()
        }
        composable(AppDestination.AUDIO_LIBRARY.routePattern) {
            audioLibraryContent()
        }
        composable(AppDestination.API_KEYS.routePattern) {
            apiKeysContent()
        }
        composable(AppDestination.TELEMETRY.routePattern) {
            telemetryContent()
        }
    }
}

fun NavHostController.navigateToTopLevel(destination: AppDestination) {
    require(destination in AppDestination.topLevelDestinations) {
        "${destination.name} 不是见域一级目的地"
    }
    val alreadySelected = currentDestination?.hierarchy?.any { entry ->
        entry.route == destination.routePattern || entry.route == destination.launchRoute
    } == true
    if (alreadySelected) return

    navigate(destination.launchRoute) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

fun NavHostController.navigateToSecondary(destination: AppDestination) {
    require(destination !in AppDestination.topLevelDestinations) {
        "${destination.name} 不是次级或兼容目的地"
    }
    navigate(destination.launchRoute) {
        launchSingleTop = true
    }
}

fun NavHostController.navigateToIssue(issueId: String, stageId: String? = null) {
    ensureTopLevelParent(
        graphRoute = JianyuNavigationRoutes.ISSUES_GRAPH,
        destination = AppDestination.ISSUES,
    )
    navigate(JianyuNavigationRoutes.issue(issueId, stageId)) {
        launchSingleTop = true
    }
}

fun NavHostController.navigateToSkillDetail(skillId: String) {
    ensureTopLevelParent(
        graphRoute = JianyuNavigationRoutes.SKILLS_GRAPH,
        destination = AppDestination.SKILLS,
    )
    navigate(JianyuNavigationRoutes.skillDetail(skillId)) {
        launchSingleTop = true
    }
}

private fun NavHostController.ensureTopLevelParent(
    graphRoute: String,
    destination: AppDestination,
) {
    val alreadyInGraph = currentDestination?.hierarchy?.any { entry ->
        entry.route == graphRoute
    } == true
    if (!alreadyInGraph) {
        navigateToTopLevel(destination)
    }
}

fun NavHostController.navigateToTelemetryFromRoundtable() {
    AppDestination.telemetryPathFromRoundtable.forEach { destination ->
        navigateToSecondary(destination)
    }
}
