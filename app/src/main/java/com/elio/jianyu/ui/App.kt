package com.elio.jianyu.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.elio.jianyu.JianyuAppRuntime
import com.elio.jianyu.JianyuAppRuntimeProvider
import com.elio.jianyu.runtime.JianyuRuntimeState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppTestTags {
    const val BOTTOM_NAVIGATION = JianyuAutomationTags.App.BOTTOM_NAVIGATION

    fun destination(destination: AppDestination): String =
        JianyuAutomationTags.Navigation.destination(destination.testTagSuffix)
}

/**
 * App 根宿主观察 Runtime 世代；维护期间移除全部数据库消费者，重开后使用全新的
 * ViewModelStore 与 NavController，旧 ViewModel 不会跨世代继续持有失效 DAO。
 */
@Composable
fun MainAppContent(
    onOfficialSkillUseRequested: (OfficialSkillUseRequest) -> Unit = {},
) {
    val application = LocalContext.current.applicationContext as Application
    val runtimeStateFlow = remember(application) {
        JianyuAppRuntimeProvider.observe(application)
    }
    val runtimeState by runtimeStateFlow.collectAsState()
    val runtimeRetryScope = rememberCoroutineScope()

    when (val state = runtimeState) {
        is JianyuRuntimeState.Ready -> RuntimeReadyMainAppContent(
            application = application,
            state = state,
            onOfficialSkillUseRequested = onOfficialSkillUseRequested,
        )
        else -> JianyuRuntimeStatusContent(
            state = state,
            onRetry = {
                runtimeRetryScope.launch(Dispatchers.IO) {
                    JianyuAppRuntimeProvider.retryOpen(application)
                }
            },
        )
    }
}

@Composable
private fun RuntimeReadyMainAppContent(
    application: Application,
    state: JianyuRuntimeState.Ready,
    onOfficialSkillUseRequested: (OfficialSkillUseRequest) -> Unit,
) {
    val runtimeLease = remember(state.generation) {
        JianyuAppRuntimeProvider.tryAcquireReady(state.generation)
    }
    if (runtimeLease == null) {
        JianyuRuntimeStatusContent(
            state = JianyuRuntimeState.Maintenance(state.generation),
            onRetry = {},
        )
        return
    }

    val storeOwner = remember(state.generation) { RuntimeGenerationViewModelStoreOwner() }
    DisposableEffect(state.generation, runtimeLease, storeOwner) {
        onDispose {
            storeOwner.viewModelStore.clear()
            runtimeLease.close()
        }
    }

    CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
        val roundtableViewModel: RoundtableViewModel = viewModel(
            viewModelStoreOwner = storeOwner,
            key = "roundtable-runtime-${state.generation}",
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application),
        )
        key(state.generation) {
            MainAppContent(
                viewModel = roundtableViewModel,
                appRuntime = runtimeLease.runtime,
                onOfficialSkillUseRequested = onOfficialSkillUseRequested,
            )
        }
    }
}

/** 非 Ready 状态的全局安全占位，不展示底层异常、路径或用户正文。 */
@Composable
internal fun JianyuRuntimeStatusContent(
    state: JianyuRuntimeState,
    onRetry: () -> Unit,
) {
    val unavailable = state is JianyuRuntimeState.Unavailable
    val tag = if (unavailable) {
        JianyuAutomationTags.App.RUNTIME_UNAVAILABLE
    } else {
        JianyuAutomationTags.App.RUNTIME_MAINTENANCE
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (!unavailable) {
            CircularProgressIndicator()
            Text(
                text = "正在安全暂停本地数据访问",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "完成数据库维护后将自动恢复。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            Text(
                text = "本地数据服务暂时不可用",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "数据库重新打开或验证失败。你可以重试；应用不会使用未验证的旧数据连接。",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(JianyuAutomationTags.App.RUNTIME_RETRY),
            ) {
                Text("重试")
            }
        }
    }
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(JianyuAutomationTags.App.CONTENT_ROOT)
            .semantics { testTagsAsResourceId = true },
        containerColor = MaterialTheme.colorScheme.background,
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
                        lifecycleRuntime = appRuntime.lifecycleRuntime,
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

@Composable
internal fun AppBottomNavigation(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.testTag(AppTestTags.BOTTOM_NAVIGATION),
        containerColor = MaterialTheme.colorScheme.surface,
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

private class RuntimeGenerationViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}
