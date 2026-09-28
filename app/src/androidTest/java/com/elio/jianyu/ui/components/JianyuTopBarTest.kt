package com.elio.jianyu.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JianyuTopBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryAndSecondary_useStableMinimumHeightsAndTouchTargets() {
        var backClicks = 0
        composeRule.setContent {
            SkillRoundtableTheme {
                androidx.compose.foundation.layout.Column {
                    JianyuTopBar(
                        title = "一级页面",
                        subtitle = "说明",
                        level = JianyuTopBarLevel.PRIMARY,
                    )
                    JianyuTopBar(
                        title = "二级页面",
                        level = JianyuTopBarLevel.SECONDARY,
                        onBack = { backClicks++ },
                        actions = {
                            IconButton(
                                onClick = {},
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("top_bar_action"),
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            }
                        },
                    )
                }
            }
        }

        assertTrue(
            composeRule.onAllNodesWithTag(JianyuShellTestTags.TOP_BAR)[0]
                .getUnclippedBoundsInRoot()
                .let { it.bottom - it.top } >= 72.dp,
        )
        assertTrue(
            composeRule.onAllNodesWithTag(JianyuShellTestTags.TOP_BAR)[1]
                .getUnclippedBoundsInRoot()
                .let { it.bottom - it.top } >= 56.dp,
        )
        assertTrue(
            composeRule.onNodeWithTag("top_bar_action")
                .getUnclippedBoundsInRoot()
                .let { it.bottom - it.top } >= 48.dp,
        )
        composeRule.onNodeWithTag(JianyuShellTestTags.PAGE_BACK_BUTTON).performClick()
        composeRule.runOnIdle { assertEquals(1, backClicks) }
    }

    @Test
    fun secondaryTitle_remainsVisibleAtTwoHundredPercentFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                SkillRoundtableTheme {
                    JianyuTopBar(
                        title = "放大字号下仍可返回的长页面标题",
                        subtitle = "标题栏允许自然增高",
                        level = JianyuTopBarLevel.SECONDARY,
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(JianyuShellTestTags.PAGE_TITLE).assertIsDisplayed()
        assertTrue(
            composeRule.onNodeWithTag(JianyuShellTestTags.TOP_BAR)
                .getUnclippedBoundsInRoot()
                .let { it.bottom - it.top } >= 56.dp,
        )
    }
}
