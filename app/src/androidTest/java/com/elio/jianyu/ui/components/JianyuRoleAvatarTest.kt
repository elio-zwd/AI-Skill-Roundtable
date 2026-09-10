package com.elio.jianyu.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JianyuRoleAvatarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun existingPersonAsset_isRenderedInsteadOfFallbackText() {
        composeRule.setContent {
            SkillRoundtableTheme {
                JianyuRoleAvatar(
                    name = "史蒂夫·乔布斯",
                    assetPath = "avatars/steve_jobs.jpg",
                    fallbackText = "乔布斯",
                    modifier = Modifier.size(56.dp).testTag("person_avatar"),
                )
            }
        }

        composeRule.onNodeWithContentDescription("史蒂夫·乔布斯").assertExists()
        composeRule.onNodeWithText("乔布斯").assertDoesNotExist()
    }

    @Test
    fun missingAsset_fallsBackAndKeepsAccessibleName() {
        composeRule.setContent {
            SkillRoundtableTheme {
                JianyuRoleAvatar(
                    name = "研究核查员",
                    assetPath = "avatars/not-present.jpg",
                    fallbackText = "核查",
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        composeRule.onNodeWithContentDescription("研究核查员").assertExists()
        composeRule.onNodeWithText("核查").assertExists()
    }

    @Test
    fun generatedFallbackLabel_isDeterministicForSameRoleName() {
        composeRule.setContent {
            SkillRoundtableTheme {
                Row {
                    JianyuRoleAvatar(
                        name = "Research Fact Checker",
                        modifier = Modifier.size(56.dp),
                    )
                    JianyuRoleAvatar(
                        name = "Research Fact Checker",
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }

        composeRule.onAllNodesWithText("RF").assertCountEquals(2)
    }
}
