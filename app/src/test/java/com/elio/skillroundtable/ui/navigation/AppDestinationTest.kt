package com.elio.skillroundtable.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDestinationTest {
    @Test
    fun topLevelDestinations_keepStableOrderAndLabels() {
        assertEquals(
            listOf(
                AppDestination.ROUNDTABLE,
                AppDestination.CHARACTERS,
                AppDestination.AUDIO_LIBRARY,
            ),
            AppDestination.topLevelDestinations,
        )
        assertEquals(
            listOf("圆桌脑暴", "智囊大厅", "音频库"),
            AppDestination.topLevelDestinations.map { it.label },
        )
    }

    @Test
    fun secondaryDestinations_hideBottomNavigation() {
        assertFalse(AppDestination.API_KEYS.showsBottomNavigation)
        assertFalse(AppDestination.TELEMETRY.showsBottomNavigation)
        assertTrue(AppDestination.ROUNDTABLE.showsBottomNavigation)
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
    fun fromRoute_mapsEveryKnownDestination() {
        AppDestination.entries.forEach { destination ->
            assertEquals(destination, AppDestination.fromRoute(destination.route))
        }
    }

    @Test
    fun fromRoute_rejectsUnknownOrMissingRoute() {
        assertNull(AppDestination.fromRoute(null))
        assertNull(AppDestination.fromRoute("unknown"))
    }

    @Test
    fun routes_areUniqueAndRoundtableIsTheStartDestination() {
        assertEquals(
            AppDestination.entries.size,
            AppDestination.entries.map { it.route }.toSet().size,
        )
        assertEquals(AppDestination.ROUNDTABLE, AppDestination.startDestination)
    }
}
