package com.elio.jianyu.ui.navigation

enum class AppDestination(
    val routePattern: String,
    val launchRoute: String,
    val label: String,
    val testTagSuffix: String,
    val showsBottomNavigation: Boolean,
    val isLegacyCompatibility: Boolean = false,
) {
    HOME(
        routePattern = "home",
        launchRoute = "home",
        label = "首页",
        testTagSuffix = "home",
        showsBottomNavigation = false,
    ),
    ISSUES(
        routePattern = "issues",
        launchRoute = JianyuNavigationRoutes.ISSUES_GRAPH,
        label = "议题",
        testTagSuffix = "issues",
        showsBottomNavigation = true,
    ),
    SKILLS(
        routePattern = "skills",
        launchRoute = JianyuNavigationRoutes.SKILLS_GRAPH,
        label = "Skill",
        testTagSuffix = "skills",
        showsBottomNavigation = true,
    ),
    RESOURCES(
        routePattern = "resources?tab={tab}",
        launchRoute = JianyuNavigationRoutes.resources(ResourceTab.MATERIALS),
        label = "资料与成果",
        testTagSuffix = "resources",
        showsBottomNavigation = true,
    ),
    SETTINGS(
        routePattern = "settings",
        launchRoute = "settings",
        label = "设置",
        testTagSuffix = "settings",
        showsBottomNavigation = false,
    ),
    ROUNDTABLE(
        routePattern = "roundtable",
        launchRoute = "roundtable",
        label = "圆桌脑暴",
        testTagSuffix = "legacy_roundtable",
        showsBottomNavigation = false,
        isLegacyCompatibility = true,
    ),
    CHARACTERS(
        routePattern = "characters",
        launchRoute = "characters",
        label = "智囊大厅",
        testTagSuffix = "legacy_characters",
        showsBottomNavigation = false,
        isLegacyCompatibility = true,
    ),
    AUDIO_LIBRARY(
        routePattern = "audio-library",
        launchRoute = "audio-library",
        label = "音频库",
        testTagSuffix = "legacy_audio_library",
        showsBottomNavigation = false,
        isLegacyCompatibility = true,
    ),
    API_KEYS(
        routePattern = "settings/api-keys",
        launchRoute = "settings/api-keys",
        label = "AI 管理",
        testTagSuffix = "api_keys",
        showsBottomNavigation = false,
    ),
    TELEMETRY(
        routePattern = "settings/telemetry",
        launchRoute = "settings/telemetry",
        label = "遥测与诊断",
        testTagSuffix = "telemetry",
        showsBottomNavigation = false,
    ),
    ;

    companion object {
        val startDestination: AppDestination = HOME

        val topLevelDestinations: List<AppDestination> = listOf(
            HOME,
            ISSUES,
            SKILLS,
            RESOURCES,
        )

        val legacyDestinations: List<AppDestination> = listOf(
            ROUNDTABLE,
            CHARACTERS,
            AUDIO_LIBRARY,
        )

        val telemetryPathFromRoundtable: List<AppDestination> = listOf(
            SETTINGS,
            TELEMETRY,
        )

        fun fromRoutePattern(routePattern: String?): AppDestination? =
            entries.firstOrNull { it.routePattern == routePattern }

        fun fromLaunchRoute(launchRoute: String?): AppDestination? =
            entries.firstOrNull { it.launchRoute == launchRoute }
    }
}
