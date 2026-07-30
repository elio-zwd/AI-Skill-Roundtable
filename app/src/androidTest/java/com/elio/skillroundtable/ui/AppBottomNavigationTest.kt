package com.elio.skillroundtable.ui

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.elio.skillroundtable.ui.navigation.AppDestination
import com.elio.skillroundtable.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppBottomNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomNavigation_exposesStableDestinationsAndDispatchesSelection() {
        var selectedDestination: AppDestination? = null

        composeRule.setContent {
            SkillRoundtableTheme {
                AppBottomNavigation(
                    currentDestination = AppDestination.ROUNDTABLE,
                    onDestinationSelected = { selectedDestination = it },
                )
            }
        }

        composeRule.onNodeWithTag(AppTestTags.BOTTOM_NAVIGATION).assertExists()
        AppDestination.topLevelDestinations.forEach { destination ->
            composeRule.onNodeWithTag(AppTestTags.destination(destination)).assertExists()
        }

        composeRule
            .onNodeWithTag(AppTestTags.destination(AppDestination.CHARACTERS))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(AppDestination.CHARACTERS, selectedDestination)
        }
    }
}
