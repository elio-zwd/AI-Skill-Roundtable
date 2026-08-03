package com.elio.jianyu.ui.navigation

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
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
                    homeContent = { DestinationMarker(AppDestination.HOME.routePattern) },
                    issuesContent = { DestinationMarker(AppDestination.ISSUES.routePattern) },
                    issueContent = { issueId, stageId ->
                        DestinationMarker(issueMarker(issueId, stageId))
                    },
                    skillsContent = { DestinationMarker(AppDestination.SKILLS.routePattern) },
                    skillDetailContent = { skillId ->
                        DestinationMarker(skillMarker(skillId))
                    },
                    resourcesContent = { tab ->
                        DestinationMarker(resourcesMarker(tab))
                    },
                    settingsContent = { DestinationMarker(AppDestination.SETTINGS.routePattern) },
                    roundtableContent = { DestinationMarker(AppDestination.ROUNDTABLE.routePattern) },
                    charactersContent = { DestinationMarker(AppDestination.CHARACTERS.routePattern) },
                    audioLibraryContent = {
                        DestinationMarker(AppDestination.AUDIO_LIBRARY.routePattern)
                    },
                    apiKeysContent = { DestinationMarker(AppDestination.API_KEYS.routePattern) },
                    telemetryContent = { DestinationMarker(AppDestination.TELEMETRY.routePattern) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun coldStart_opensHome() {
        composeRule.onNodeWithTag(AppDestination.HOME.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.HOME.routePattern)
    }

    @Test
    fun topLevelNavigation_switchesAcrossFourDestinations() {
        composeRule.runOnIdle {
            navController.navigateToTopLevel(AppDestination.ISSUES)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.ISSUES.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.ISSUES.routePattern)

        composeRule.runOnIdle {
            navController.navigateToTopLevel(AppDestination.SKILLS)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.SKILLS.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.SKILLS.routePattern)

        composeRule.runOnIdle {
            navController.navigateToTopLevel(AppDestination.RESOURCES)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(resourcesMarker(ResourceTab.MATERIALS)).assertIsDisplayed()
        assertCurrentRoute(AppDestination.RESOURCES.routePattern)
    }

    @Test
    fun repeatedTopLevelNavigation_doesNotCreateDuplicateDestination() {
        composeRule.runOnIdle {
            navController.navigateToTopLevel(AppDestination.ISSUES)
            navController.navigateToTopLevel(AppDestination.ISSUES)
        }
        composeRule.waitForIdle()
        assertCurrentRoute(AppDestination.ISSUES.routePattern)

        composeRule.runOnIdle {
            assertTrue(navController.popBackStack())
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.HOME.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.HOME.routePattern)
    }

    @Test
    fun settings_returnsToTheOriginTopLevelDestination() {
        composeRule.runOnIdle {
            navController.navigateToTopLevel(AppDestination.SKILLS)
            navController.navigateToSecondary(AppDestination.SETTINGS)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.SETTINGS.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.SETTINGS.routePattern)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.SKILLS.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.SKILLS.routePattern)
    }

    @Test
    fun issueRoute_passesStableIssueAndStageIds() {
        composeRule.runOnIdle {
            navController.navigateToIssue("issue-42", "stage-3")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(issueMarker("issue-42", "stage-3")).assertIsDisplayed()
        assertCurrentRoute(JianyuNavigationRoutes.ISSUE_DETAIL_PATTERN)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.ISSUES.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.ISSUES.routePattern)
    }

    @Test
    fun skillDetailRoute_passesStableOfficialSkillIdAndReturnsToCatalog() {
        composeRule.runOnIdle {
            navController.navigateToSkillDetail("zhang_xuefeng")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(skillMarker("zhang_xuefeng")).assertIsDisplayed()
        assertCurrentRoute(JianyuNavigationRoutes.SKILL_DETAIL_PATTERN)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.SKILLS.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.SKILLS.routePattern)
    }

    @Test
    fun resourcesRoute_selectsRequestedTabAndDefaultsToMaterials() {
        composeRule.runOnIdle {
            navController.navigate(JianyuNavigationRoutes.resources(ResourceTab.ARTIFACTS))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(resourcesMarker(ResourceTab.ARTIFACTS)).assertIsDisplayed()

        composeRule.runOnIdle {
            navController.navigate("resources?tab=invalid")
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(resourcesMarker(ResourceTab.MATERIALS)).assertIsDisplayed()
    }

    @Test
    fun legacyTelemetryPath_returnsThroughApiKeysToRoundtable() {
        composeRule.runOnIdle {
            navController.navigateToSecondary(AppDestination.ROUNDTABLE)
            navController.navigateToTelemetryFromRoundtable()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.TELEMETRY.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.TELEMETRY.routePattern)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.API_KEYS.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.API_KEYS.routePattern)

        composeRule.runOnIdle { navController.popBackStack() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AppDestination.ROUNDTABLE.routePattern).assertIsDisplayed()
        assertCurrentRoute(AppDestination.ROUNDTABLE.routePattern)
    }

    private fun assertCurrentRoute(expectedRoutePattern: String) {
        composeRule.runOnIdle {
            assertEquals(
                expectedRoutePattern,
                navController.currentBackStackEntry?.destination?.route,
            )
        }
    }
}

private fun issueMarker(issueId: String?, stageId: String?): String =
    "issue:${issueId.orEmpty()}:${stageId.orEmpty()}"

private fun skillMarker(skillId: String?): String =
    "skill:${skillId.orEmpty()}"

private fun resourcesMarker(tab: ResourceTab): String =
    "resources:${tab.routeValue}"

@androidx.compose.runtime.Composable
private fun DestinationMarker(tag: String) {
    Text(
        text = tag,
        modifier = Modifier.testTag(tag),
    )
}
