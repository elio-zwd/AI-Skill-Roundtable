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
    PERSONAL_CONTEXT(
        routePattern = "mine/personal-context",
        launchRoute = "mine/personal-context",
        label = "个人背景",
        testTagSuffix = "personal_context",
        showsBottomNavigation = false,
    ),
    SKILL_SEARCH(
        routePattern = JianyuNavigationRoutes.SKILL_SEARCH_PATTERN,
        launchRoute = JianyuNavigationRoutes.SKILL_SEARCH_PATTERN,
        label = "搜索角色",
        testTagSuffix = "skill_search",
        showsBottomNavigation = false,
    ),
    SKILL_FAVORITES(
        routePattern = JianyuNavigationRoutes.SKILL_FAVORITES_PATTERN,
        launchRoute = JianyuNavigationRoutes.SKILL_FAVORITES_PATTERN,
        label = "收藏的角色",
        testTagSuffix = "skill_favorites",
        showsBottomNavigation = false,
    ),
    SKILL_RECENT(
        routePattern = JianyuNavigationRoutes.SKILL_RECENT_PATTERN,
        launchRoute = JianyuNavigationRoutes.SKILL_RECENT_PATTERN,
        label = "最近使用",
        testTagSuffix = "skill_recent",
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
