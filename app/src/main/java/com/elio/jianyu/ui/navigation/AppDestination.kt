package com.elio.jianyu.ui.navigation

enum class AppDestination(
    val route: String,
    val label: String,
    val showsBottomNavigation: Boolean,
) {
    ROUNDTABLE(
        route = "roundtable",
        label = "圆桌脑暴",
        showsBottomNavigation = true,
    ),
    CHARACTERS(
        route = "characters",
        label = "智囊大厅",
        showsBottomNavigation = true,
    ),
    AUDIO_LIBRARY(
        route = "audio-library",
        label = "音频库",
        showsBottomNavigation = true,
    ),
    API_KEYS(
        route = "settings/api-keys",
        label = "API Key 管理",
        showsBottomNavigation = false,
    ),
    TELEMETRY(
        route = "settings/telemetry",
        label = "遥测与诊断",
        showsBottomNavigation = false,
    ),
    ;

    companion object {
        val startDestination: AppDestination = ROUNDTABLE

        val topLevelDestinations: List<AppDestination> = listOf(
            ROUNDTABLE,
            CHARACTERS,
            AUDIO_LIBRARY,
        )

        val telemetryPathFromRoundtable: List<AppDestination> = listOf(
            API_KEYS,
            TELEMETRY,
        )

        fun fromRoute(route: String?): AppDestination? =
            entries.firstOrNull { destination -> destination.route == route }
    }
}
