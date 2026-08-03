package com.elio.jianyu.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.MainActivity
import com.elio.jianyu.ui.components.JianyuShellTestTags
import com.elio.jianyu.ui.navigation.AppDestination
import com.elio.jianyu.ui.screens.resources.ResourcesTestTags
import com.elio.jianyu.ui.screens.settings.SettingsShellTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationRestorationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityRecreation_keepsTopLevelDestinationAndResourcesTab() {
        composeRule
            .onNodeWithTag(AppTestTags.destination(AppDestination.RESOURCES))
            .performClick()
        composeRule.onNodeWithTag(ResourcesTestTags.SCREEN).assertIsDisplayed()

        composeRule.onNodeWithTag(ResourcesTestTags.ARTIFACTS_TAB).performClick()
        composeRule.onNodeWithTag(ResourcesTestTags.ARTIFACTS_TAB).assertIsSelected()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResourcesTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ResourcesTestTags.ARTIFACTS_TAB).assertIsSelected()
        composeRule
            .onNodeWithTag(AppTestTags.destination(AppDestination.RESOURCES))
            .assertIsSelected()
    }

    @Test
    fun settingsSystemBack_returnsToOriginDestination() {
        composeRule
            .onNodeWithTag(AppTestTags.destination(AppDestination.RESOURCES))
            .performClick()
        composeRule.onNodeWithTag(JianyuShellTestTags.GLOBAL_SETTINGS_BUTTON).performClick()
        composeRule.onNodeWithTag(SettingsShellTestTags.SCREEN).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ResourcesTestTags.SCREEN).assertIsDisplayed()
        composeRule
            .onNodeWithTag(AppTestTags.destination(AppDestination.RESOURCES))
            .assertIsSelected()
    }
}
