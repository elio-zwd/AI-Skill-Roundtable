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
                AppDestination.ISSUES,
                AppDestination.SKILLS,
                AppDestination.RESOURCES,
            ),
            AppDestination.topLevelDestinations,
        )
        assertEquals(
            listOf("首页", "议题", "Skill", "资料与成果"),
            AppDestination.topLevelDestinations.map { it.label },
        )
    }

    @Test
    fun home_isTheStartDestination() {
        assertEquals(AppDestination.HOME, AppDestination.startDestination)
    }

    @Test
    fun secondaryDestinations_doNotAppearInBottomNavigation() {
        assertFalse(AppDestination.SETTINGS.showsBottomNavigation)
        assertFalse(AppDestination.API_KEYS.showsBottomNavigation)
        assertFalse(AppDestination.TELEMETRY.showsBottomNavigation)
        assertFalse(AppDestination.HOME.showsBottomNavigation)
        AppDestination.topLevelDestinations.filter { it != AppDestination.HOME }.forEach { destination ->
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
