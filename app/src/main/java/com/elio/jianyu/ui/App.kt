package com.elio.jianyu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elio.jianyu.JianyuAppRuntime
import com.elio.jianyu.JianyuAppRuntimeProvider
import com.elio.jianyu.skill.catalog.OfficialSkillUseRequest
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuNavigationIcons
import com.elio.jianyu.ui.navigation.AppDestination
import com.elio.jianyu.ui.navigation.AppNavHost
import com.elio.jianyu.ui.navigation.navigateToIssue
import com.elio.jianyu.ui.navigation.navigateToSecondary
import com.elio.jianyu.ui.navigation.navigateToTelemetryFromRoundtable
import com.elio.jianyu.ui.navigation.navigateToTopLevel
import com.elio.jianyu.ui.screens.characters.CharacterHallRoute
import com.elio.jianyu.ui.screens.execution.AudioEnabledIssueExecutionRoute
import com.elio.jianyu.ui.screens.home.HomeRoute
import com.elio.jianyu.ui.screens.issues.IssuesRoute
import com.elio.jianyu.ui.screens.library.AudioLibraryRoute
import com.elio.jianyu.ui.screens.resources.ResourcesRoute
import com.elio.jianyu.ui.screens.roundtable.RoundtableRoute
import com.elio.jianyu.ui.screens.settings.ApiKeyManagerRoute
import com.elio.jianyu.ui.screens.settings.SettingsRoute
import com.elio.jianyu.ui.screens.settings.TelemetryRoute
import com.elio.jianyu.ui.screens.skills.OfficialSkillNavigationRoute
import com.elio.jianyu.viewmodel.RoundtableViewModel

object AppTestTags {
    const val BOTTOM_NAVIGATION = JianyuAutomationTags.App.BOTTOM_NAVIGATION

    fun destination(destination: AppDestination): String =
        JianyuAutomationTags.Navigation.destination(destination.testTagSuffix)
}

@Composable
fun MainAppContent(
    viewModel: RoundtableViewModel = viewModel(),
    onOfficialSkillUseRequested: (OfficialSkillUseRequest) -> Unit = {},
) {
    val applicationContext = LocalContext.current.applicationContext
    val appRuntime = remember(applicationContext) {
        JianyuAppRuntimeProvider.get(applicationContext)
    }
    MainAppContent(
        viewModel = viewModel,
        appRuntime = appRuntime,
        onOfficialSkillUseRequested = onOfficialSkillUseRequested,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
internal fun MainAppContent(
    viewModel: RoundtableViewModel,
    appRuntime: JianyuAppRuntime,
    onOfficialSkillUseRequested: (OfficialSkillUseRequest) -> Unit = {},
) {
    val allCharacters by viewModel.allCharacters.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    val navController = rememberNavController()
    var pendingSkillId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSkillIntent by rememberSaveable { mutableStateOf<String?>(null) }
    val onUseOfficialSkill: (OfficialSkillUseRequest) -> Unit = { request ->
        pendingSkillId = request.skillId
        pendingSkillIntent = request.intent
        onOfficialSkillUseRequested(request)
        navController.navigateToTopLevel(AppDestination.HOME)
    }
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

    com.elio.jianyu.ui.components.JianyuBackgroundAtmosphere {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .testTag(JianyuAutomationTags.App.CONTENT_ROOT)
                .semantics { testTagsAsResourceId = true },
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                            repository = appRuntime.repository,
                            catalogRuntimeResult = appRuntime.officialSkillCatalogRuntimeResult,
                            executionCoordinator = appRuntime.executionCoordinator,
                            onOpenSettings = {
                                navController.navigateToSecondary(AppDestination.SETTINGS)
                            },
                            onNavigateToIssue = { issueId, stageId ->
                                navController.navigateToIssue(issueId, stageId)
                            },
                            onOpenSkillCatalog = {
                                navController.navigateToTopLevel(AppDestination.SKILLS)
                            },
                            skillUseRequest = pendingSkillId?.let { skillId ->
                                OfficialSkillUseRequest(skillId, pendingSkillIntent)
                            },
                            onSkillUseRequestConsumed = {
                                pendingSkillId = null
                                pendingSkillIntent = null
                            },
                        )
                    },
                    issuesContent = {
                        IssuesRoute(
                            repository = appRuntime.repository,
                            onOpenIssue = navController::navigateToIssue,
                            onOpenSettings = {
                                navController.navigateToSecondary(AppDestination.SETTINGS)
                            },
                        )
                    },
                    issueContent = { issueId, stageId ->
                        AudioEnabledIssueExecutionRoute(
                            repository = appRuntime.repository,
                            coordinator = appRuntime.executionCoordinator,
                            collaborationCoordinator = appRuntime.collaborationCoordinator,
                            stageResultService = appRuntime.stageResultService,
                            audioRuntime = appRuntime.audioRuntime,
                            issueId = issueId,
                            stageId = stageId,
                            onBack = { navController.popBackStack() },
                            onOpenStage = navController::navigateToIssue,
                        )
                    },
                    skillsContent = {
                        OfficialSkillNavigationRoute(
                            repository = appRuntime.repository,
                            runtimeResult = appRuntime.officialSkillCatalogRuntimeResult,
                            onOpenSettings = {
                                navController.navigateToSecondary(AppDestination.SETTINGS)
                            },
                            onUseSkill = onUseOfficialSkill,
                        )
                    },
                    skillDetailContent = { skillId ->
                        OfficialSkillNavigationRoute(
                            repository = appRuntime.repository,
                            runtimeResult = appRuntime.officialSkillCatalogRuntimeResult,
                            initialSkillId = skillId,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = {
                                navController.navigateToSecondary(AppDestination.SETTINGS)
                            },
                            onUseSkill = onUseOfficialSkill,
                        )
                    },
                    resourcesContent = { tab ->
                    ResourcesRoute(
                        repository = appRuntime.repository,
                        initialTab = tab,
                        onOpenSettings = {
                            navController.navigateToSecondary(AppDestination.SETTINGS)
                        },
                        onOpenIssue = navController::navigateToIssue,
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
}

@Composable
internal fun AppBottomNavigation(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        NavigationBar(
            modifier = Modifier.testTag(AppTestTags.BOTTOM_NAVIGATION),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            AppDestination.topLevelDestinations.forEach { destination ->
                val icon = when (destination) {
                    AppDestination.HOME -> Icons.Default.Home
                    AppDestination.ISSUES -> JianyuNavigationIcons.Issues
                    AppDestination.SKILLS -> JianyuNavigationIcons.Skills
                    AppDestination.RESOURCES -> JianyuNavigationIcons.Resources
                    else -> error("非一级目的地不能显示在底部导航")
                }
                NavigationBarItem(
                    selected = currentDestination == destination,
                    onClick = { onDestinationSelected(destination) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
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
                    modifier = Modifier
                        .semantics { contentDescription = destination.label }
                        .testTag(AppTestTags.destination(destination)),
                )
            }
        }
    }
}
