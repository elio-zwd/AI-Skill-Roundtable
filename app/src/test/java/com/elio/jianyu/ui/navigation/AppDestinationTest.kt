package com.elio.jianyu.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun topLevelDestinations_areJianyuFourDestinations() {
        assertEquals(
            listOf(
                AppDestination.HOME,
                AppDestination.SKILLS,
                AppDestination.RESOURCES,
                AppDestination.MINE,
            ),
            AppDestination.topLevelDestinations,
        )
        assertEquals(
            listOf("对话", "角色", "资料", "我的"),
            AppDestination.topLevelDestinations.map { it.label },
        )
    }

    @Test
    fun home_isTheStartDestination() {
        assertEquals(AppDestination.HOME, AppDestination.startDestination)
    }

    @Test
    fun secondaryDestinations_doNotAppearInBottomNavigation() {
        assertFalse(AppDestination.ISSUES.showsBottomNavigation)
        assertFalse(AppDestination.SETTINGS.showsBottomNavigation)
        assertFalse(AppDestination.API_KEYS.showsBottomNavigation)
        assertFalse(AppDestination.TELEMETRY.showsBottomNavigation)
        AppDestination.topLevelDestinations.forEach { destination ->
            assertTrue(destination.showsBottomNavigation)
        }
    }

    @Test
    fun fromRoutePattern_mapsEveryKnownDestination() {
        AppDestination.entries.forEach { destination ->
            assertEquals(
                destination,
                AppDestination.fromRoutePattern(destination.routePattern),
            )
        }
    }

    @Test
    fun fromLaunchRoute_mapsEveryKnownDestination() {
        AppDestination.entries.forEach { destination ->
            assertEquals(
                destination,
                AppDestination.fromLaunchRoute(destination.launchRoute),
            )
        }
    }

    @Test
    fun unknownRoute_returnsNull() {
        assertNull(AppDestination.fromRoutePattern("unknown"))
        assertNull(AppDestination.fromLaunchRoute("unknown"))
    }
}
