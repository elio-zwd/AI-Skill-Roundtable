package com.elio.jianyu.ui.screens.mine

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.telemetry.TelemetryLevel
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MineScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun minePage_exposesApprovedContentAndUnavailableActions() {
        composeRule.setContent {
            SkillRoundtableTheme(darkTheme = false) {
                MineScreen(
                    uiState = MineUiState(
                        personalContextCount = 4,
                        modelDisplayName = "Gemini 3.6 Flash",
                        availableKeyCount = 2,
                        telemetryLevel = TelemetryLevel.OFF,
                    ),
                    onOpenSettings = {},
                    onOpenAiManagement = {},
                    onOpenTelemetry = {},
                )
            }
        }

        composeRule.onNodeWithTag(MineTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("我的").assertIsDisplayed()
        composeRule.onNodeWithTag(MineTestTags.PERSONAL_BACKGROUND_HERO).assertIsDisplayed()
        composeRule.onNodeWithText("已保存 4 项").assertIsDisplayed()
        composeRule.onNodeWithText("职业目标").assertIsDisplayed()
        composeRule.onNodeWithText("可用时间").assertIsDisplayed()
        composeRule.onNodeWithText("表达偏好").assertIsDisplayed()
        composeRule.onNodeWithText("AI 管理").assertIsDisplayed()
        composeRule.onNodeWithText("数据与隐私").assertIsDisplayed()
        composeRule.onNodeWithText("备份与恢复").assertIsDisplayed()
        composeRule.onNodeWithText("遥测与诊断").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("关于见域").performScrollTo().assertIsDisplayed()

        listOf(
            MineTestTags.PERSONAL_BACKGROUND_ACTION,
            MineTestTags.AVATAR_SWITCH_UNAVAILABLE,
            MineTestTags.DATA_PRIVACY_CARD,
            MineTestTags.BACKUP_RESTORE_CARD,
            MineTestTags.ABOUT_ENTRY,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsNotEnabled()
        }
    }

    @Test
    fun minePage_dispatchesOnlyImplementedSettingsAndDiagnosticsActions() {
        var settingsClicks = 0
        var aiClicks = 0
        var telemetryClicks = 0

        composeRule.setContent {
            SkillRoundtableTheme(darkTheme = false) {
                MineScreen(
                    uiState = MineUiState(),
                    onOpenSettings = { settingsClicks++ },
                    onOpenAiManagement = { aiClicks++ },
                    onOpenTelemetry = { telemetryClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(MineTestTags.SETTINGS_BUTTON).performClick()
        composeRule.onNodeWithTag(MineTestTags.AI_MANAGEMENT_CARD).performClick()
        composeRule.onNodeWithTag(MineTestTags.TELEMETRY_CARD).performClick()
        composeRule.runOnIdle {
            assertEquals(1, settingsClicks)
            assertEquals(1, aiClicks)
            assertEquals(1, telemetryClicks)
        }
    }
}
