package com.elio.skillroundtable.ui.navigation

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.skillroundtable.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavHostTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        navController = TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }

        composeRule.setContent {
            SkillRoundtableTheme {
                AppNavHost(
                    navController = navController,
                    roundtableContent = { DestinationMarker(AppDestination.ROUNDTABLE) },
                    charactersContent = { DestinationMarker(AppDestination.CHARACTERS) },
                    audioLibraryContent = { DestinationMarker(AppDestination.AUDIO_LIBRARY) },
                    apiKeysContent = { DestinationMarker(AppDestination.API_KEYS) },
                    telemetryContent = { DestinationMarker(AppDestination.TELEMETRY) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun coldStart_opensRoundtable() {
        composeRule.onNodeWithTag(AppDestination.ROUNDTABLE.route).assertIsDisplayed()
        assertCurrentDestination(AppDestination.ROUNDTABLE)
    }

    @Test
    fun topLevelNavigation_switchesWithoutChangingRouteContract() {
        composeRule.runOnIdle {
            navController.navigateToTopLevel(AppDestination.CHARACTERS)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.CHARACTERS.route).assertIsDisplayed()
        assertCurrentDestination(AppDestination.CHARACTERS)

        composeRule.runOnIdle {
            navController.navigateToTopLevel(AppDestination.AUDIO_LIBRARY)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.AUDIO_LIBRARY.route).assertIsDisplayed()
        assertCurrentDestination(AppDestination.AUDIO_LIBRARY)
    }

    @Test
    fun telemetryPath_returnsThroughApiKeysToRoundtable() {
        composeRule.runOnIdle {
            navController.navigateToTelemetryFromRoundtable()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.TELEMETRY.route).assertIsDisplayed()
        assertCurrentDestination(AppDestination.TELEMETRY)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.API_KEYS.route).assertIsDisplayed()
        assertCurrentDestination(AppDestination.API_KEYS)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.ROUNDTABLE.route).assertIsDisplayed()
        assertCurrentDestination(AppDestination.ROUNDTABLE)
    }

    private fun assertCurrentDestination(expected: AppDestination) {
        composeRule.runOnIdle {
            assertEquals(expected.route, navController.currentBackStackEntry?.destination?.route)
        }
    }
}

@androidx.compose.runtime.Composable
private fun DestinationMarker(destination: AppDestination) {
    Text(
        text = destination.label,
        modifier = Modifier.testTag(destination.route),
    )
}
