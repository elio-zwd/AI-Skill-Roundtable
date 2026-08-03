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
    fun settingsAndLegacyDestinations_doNotAppearInBottomNavigation() {
        assertFalse(AppDestination.SETTINGS.showsBottomNavigation)
        assertFalse(AppDestination.API_KEYS.showsBottomNavigation)
        assertFalse(AppDestination.TELEMETRY.showsBottomNavigation)
        assertFalse(AppDestination.ROUNDTABLE.showsBottomNavigation)
        assertFalse(AppDestination.CHARACTERS.showsBottomNavigation)
        assertFalse(AppDestination.AUDIO_LIBRARY.showsBottomNavigation)
        AppDestination.topLevelDestinations.forEach { destination ->
            assertTrue(destination.showsBottomNavigation)
        }
    }

    @Test
    fun legacyDestinations_remainExplicitCompatibilityRoutes() {
        assertEquals(
            listOf(
                AppDestination.ROUNDTABLE,
                AppDestination.CHARACTERS,
                AppDestination.AUDIO_LIBRARY,
            ),
            AppDestination.legacyDestinations,
        )
        assertEquals(
            listOf("roundtable", "characters", "audio-library"),
            AppDestination.legacyDestinations.map { it.routePattern },
        )
    }

    @Test
    fun telemetryFromRoundtable_returnsThroughApiKeys() {
        assertEquals(
            listOf(
                AppDestination.API_KEYS,
                AppDestination.TELEMETRY,
            ),
            AppDestination.telemetryPathFromRoundtable,
        )
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
    fun fromRoutePattern_rejectsUnknownOrMissingRoute() {
        assertNull(AppDestination.fromRoutePattern(null))
        assertNull(AppDestination.fromRoutePattern("unknown"))
    }

    @Test
    fun routePatterns_areUnique() {
        assertEquals(
            AppDestination.entries.size,
            AppDestination.entries.map { it.routePattern }.toSet().size,
        )
    }
}
