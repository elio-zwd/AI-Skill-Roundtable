package com.elio.jianyu.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.ui.navigation.AppDestination
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBottomNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigation_exposesExactlyFourJianyuDestinationsAndDispatchesSelection() {
        var selectedDestination: AppDestination? = null

        composeRule.setContent {
            SkillRoundtableTheme {
                AppBottomNavigation(
                    currentDestination = AppDestination.HOME,
                    onDestinationSelected = { selectedDestination = it },
                )
            }
        }

        composeRule.onNodeWithTag(AppTestTags.BOTTOM_NAVIGATION).assertExists()
        AppDestination.topLevelDestinations.forEach { destination ->
            composeRule.onNodeWithTag(AppTestTags.destination(destination)).assertExists()
            composeRule.onNodeWithContentDescription(destination.label).assertExists()
        }
        composeRule
            .onNodeWithTag(AppTestTags.destination(AppDestination.HOME))
            .assertIsSelected()

        listOf(
            AppDestination.SETTINGS,
            AppDestination.ROUNDTABLE,
            AppDestination.CHARACTERS,
            AppDestination.AUDIO_LIBRARY,
            AppDestination.API_KEYS,
            AppDestination.TELEMETRY,
        ).forEach { hiddenDestination ->
            composeRule
                .onNodeWithTag(AppTestTags.destination(hiddenDestination))
                .assertDoesNotExist()
        }

        composeRule
            .onNodeWithTag(AppTestTags.destination(AppDestination.ISSUES))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(AppDestination.ISSUES, selectedDestination)
        }
    }
}
