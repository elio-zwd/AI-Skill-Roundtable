package com.elio.jianyu.ui.navigation

enum class AppDestination(
    val routePattern: String,
    val launchRoute: String,
    val label: String,
    val testTagSuffix: String,
    val showsBottomNavigation: Boolean,
) {
    HOME(
        routePattern = "home",
        launchRoute = "home",
        label = "对话",
        testTagSuffix = "home",
        showsBottomNavigation = true,
    ),
    ISSUES(
        routePattern = "issues",
        launchRoute = JianyuNavigationRoutes.ISSUES_GRAPH,
        label = "议题",
        testTagSuffix = "issues",
        showsBottomNavigation = false,
    ),
    SKILLS(
        routePattern = "skills",
        launchRoute = JianyuNavigationRoutes.SKILLS_GRAPH,
        label = "角色",
        testTagSuffix = "skills",
        showsBottomNavigation = true,
    ),
    RESOURCES(
        routePattern = "resources?tab={tab}",
        launchRoute = JianyuNavigationRoutes.resources(ResourceTab.MATERIALS),
        label = "资料",
        testTagSuffix = "resources",
        showsBottomNavigation = true,
    ),
    MINE(
        routePattern = "mine",
        launchRoute = "mine",
        label = "我的",
        testTagSuffix = "mine",
        showsBottomNavigation = true,
    ),
    SETTINGS(
        routePattern = "settings",
        launchRoute = "settings",
        label = "设置",
        testTagSuffix = "settings",
        showsBottomNavigation = false,
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
            SKILLS,
            RESOURCES,
            MINE,
        )

        fun fromRoutePattern(routePattern: String?): AppDestination? =
            entries.firstOrNull { it.routePattern == routePattern }

        fun fromLaunchRoute(launchRoute: String?): AppDestination? =
            entries.firstOrNull { it.launchRoute == launchRoute }
    }
}
