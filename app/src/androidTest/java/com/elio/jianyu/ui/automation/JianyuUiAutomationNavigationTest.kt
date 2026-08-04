package com.elio.jianyu.ui.automation

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JianyuUiAutomationNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_exposesUniqueApplicationAndHomeRoots() {
        composeRule
            .onAllNodesWithTag(JianyuAutomationTags.App.CONTENT_ROOT)
            .assertCountEquals(1)
        composeRule
            .onNodeWithTag(JianyuAutomationTags.App.CONTENT_ROOT)
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule
            .onAllNodesWithTag(JianyuAutomationTags.Screen.HOME)
            .assertCountEquals(1)
        composeRule
            .onNodeWithTag(JianyuAutomationTags.Navigation.HOME)
            .assertIsSelected()
    }

    @Test
    fun topLevelNavigation_exposesEveryDestinationRoot() {
        navigateAndAssert(
            destinationTag = JianyuAutomationTags.Navigation.ISSUES,
            screenTag = JianyuAutomationTags.Screen.ISSUES,
        )
        navigateAndAssert(
            destinationTag = JianyuAutomationTags.Navigation.SKILLS,
            screenTag = JianyuAutomationTags.Screen.SKILLS,
        )
        navigateAndAssert(
            destinationTag = JianyuAutomationTags.Navigation.RESOURCES,
            screenTag = JianyuAutomationTags.Screen.RESOURCES,
        )
        navigateAndAssert(
            destinationTag = JianyuAutomationTags.Navigation.HOME,
            screenTag = JianyuAutomationTags.Screen.HOME,
        )
    }

    @Test
    fun settingsOpenAndBack_restoreOriginWithoutBottomNavigationDuplication() {
        composeRule
            .onNodeWithTag(JianyuAutomationTags.Shell.GLOBAL_SETTINGS_BUTTON)
            .performClick()
        composeRule
            .onNodeWithTag(JianyuAutomationTags.Screen.SETTINGS)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(JianyuAutomationTags.App.BOTTOM_NAVIGATION)
            .assertDoesNotExist()

        composeRule
            .onNodeWithTag(JianyuAutomationTags.Shell.PAGE_BACK_BUTTON)
            .performClick()

        composeRule
            .onNodeWithTag(JianyuAutomationTags.Screen.HOME)
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithTag(JianyuAutomationTags.App.BOTTOM_NAVIGATION)
            .assertCountEquals(1)
    }

    @Test
    fun resourcesLibrary_exposesMaterialsAndPersonalContextContentRoots() {
        navigateAndAssert(
            destinationTag = JianyuAutomationTags.Navigation.RESOURCES,
            screenTag = JianyuAutomationTags.Screen.RESOURCES,
        )
        waitForTag(JianyuAutomationTags.Resources.MATERIALS_CONTENT)
        composeRule
            .onNodeWithTag(JianyuAutomationTags.Resources.MATERIALS_CONTENT)
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag(JianyuAutomationTags.Resources.PERSONAL_CONTEXT_LIBRARY)
            .performClick()
        waitForTag(JianyuAutomationTags.Resources.PERSONAL_CONTEXT_CONTENT)
        composeRule
            .onNodeWithTag(JianyuAutomationTags.Resources.PERSONAL_CONTEXT_CONTENT)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(JianyuAutomationTags.Resources.MATERIALS_CONTENT)
            .assertDoesNotExist()
    }

    private fun navigateAndAssert(destinationTag: String, screenTag: String) {
        composeRule.onNodeWithTag(destinationTag).performClick()
        waitForTag(screenTag)
        composeRule.onNodeWithTag(screenTag).assertIsDisplayed()
        composeRule.onNodeWithTag(destinationTag).assertIsSelected()
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
