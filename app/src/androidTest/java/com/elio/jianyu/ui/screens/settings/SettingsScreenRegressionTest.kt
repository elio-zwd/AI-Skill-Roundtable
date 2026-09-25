package com.elio.jianyu.ui.screens.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.network.AiModel
import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.AiRuntimeConfiguration
import com.elio.jianyu.network.AiUseCase
import com.elio.jianyu.network.defaultModel
import com.elio.jianyu.telemetry.TelemetryLevel
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aiManagementScreen_keepsHomeCleanAndOpensProviderKeySheet() {
        composeRule.setContent {
            SkillRoundtableTheme {
                AiManagementScreen(
                    uiState = emptyAiManagementState(),
                    onBack = {},
                    onSelectProvider = { _, _ -> },
                    onSelectModel = { _, _ -> },
                    onSelectKeyProvider = {},
                    onInputChange = {},
                    onImport = {},
                    onToggleKey = { _, _ -> },
                    onValidateKey = {},
                    onRequestDeleteKey = {},
                    onRequestClearProviderKeys = {},
                    onDismissConfirmation = {},
                    onConfirmDeleteKey = {},
                    onConfirmClearProviderKeys = {},
                )
            }
        }

        composeRule.onNodeWithTag(AiManagementTestTags.ROOT).assertExists()
        composeRule.onNodeWithText("模型配置").assertIsDisplayed()
        composeRule.onNodeWithText("API Key").assertIsDisplayed()
        composeRule.onNodeWithText("模型说明").assertIsDisplayed()
        composeRule.onNodeWithTag(AiManagementTestTags.IMPORT_INPUT).assertDoesNotExist()

        composeRule.onNodeWithTag(AiManagementTestTags.keyProviderCard(AiProvider.GEMINI.name))
            .performClick()

        composeRule.onNodeWithTag(AiManagementTestTags.KEY_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(AiManagementTestTags.IMPORT_INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(AiManagementTestTags.IMPORT_BUTTON).assertExists()
    }

    @Test
    fun aiManagementScreen_opensModelSelectionSheetFromUseCaseRow() {
        composeRule.setContent {
            SkillRoundtableTheme {
                AiManagementScreen(
                    uiState = emptyAiManagementState(),
                    onBack = {},
                    onSelectProvider = { _, _ -> },
                    onSelectModel = { _, _ -> },
                    onSelectKeyProvider = {},
                    onInputChange = {},
                    onImport = {},
                    onToggleKey = { _, _ -> },
                    onValidateKey = {},
                    onRequestDeleteKey = {},
                    onRequestClearProviderKeys = {},
                    onDismissConfirmation = {},
                    onConfirmDeleteKey = {},
                    onConfirmClearProviderKeys = {},
                )
            }
        }

        composeRule.onNodeWithTag(AiManagementTestTags.useCase(AiUseCase.SESSION_TITLE.name))
            .performClick()

        composeRule.onNodeWithTag(AiManagementTestTags.MODEL_SHEET).assertIsDisplayed()
        composeRule.onNodeWithText("选择对话标题模型").assertIsDisplayed()
        listOf(
            AiModel.GEMINI_38_FLASH,
            AiModel.GEMINI_37_FLASH,
            AiModel.GEMINI_35_FLASH_LITE,
            AiModel.GEMINI_25_FLASH,
            AiModel.GEMINI_25_FLASH_LITE,
        ).forEach { model ->
            composeRule.onNodeWithTag(
                AiManagementTestTags.model(
                    "${AiUseCase.SESSION_TITLE.name}_${model.name}",
                ),
            ).assertExists()
        }
        composeRule.onNodeWithTag(
            AiManagementTestTags.model(
                "${AiUseCase.SESSION_TITLE.name}_${AiModel.GEMINI_25_FLASH_LITE.name}",
            ),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aiManagementScreen_webGroundingKeeps3xAndAdds25SearchModels() {
        composeRule.setContent {
            SkillRoundtableTheme {
                AiManagementScreen(
                    uiState = emptyAiManagementState(),
                    onBack = {},
                    onSelectProvider = { _, _ -> },
                    onSelectModel = { _, _ -> },
                    onSelectKeyProvider = {},
                    onInputChange = {},
                    onImport = {},
                    onToggleKey = { _, _ -> },
                    onValidateKey = {},
                    onRequestDeleteKey = {},
                    onRequestClearProviderKeys = {},
                    onDismissConfirmation = {},
                    onConfirmDeleteKey = {},
                    onConfirmClearProviderKeys = {},
                )
            }
        }

        composeRule.onNodeWithTag(AiManagementTestTags.useCase(AiUseCase.WEB_GROUNDING.name))
            .performClick()

        listOf(
            AiModel.GEMINI_38_FLASH,
            AiModel.GEMINI_35_FLASH_LITE,
            AiModel.GEMINI_25_FLASH,
            AiModel.GEMINI_25_FLASH_LITE,
        ).forEach { model ->
            composeRule.onNodeWithTag(
                AiManagementTestTags.model(
                    "${AiUseCase.WEB_GROUNDING.name}_${model.name}",
                ),
            ).assertExists()
        }
    }

    @Test
    fun telemetryScreen_exposesStableRoot() {
        composeRule.setContent {
            SkillRoundtableTheme {
                TelemetryScreen(
                    uiState = TelemetryUiState(
                        events = emptyList(),
                        level = TelemetryLevel.METADATA_ONLY,
                        storageError = null,
                        expandedEventId = null,
                        confirmation = null,
                        remainingContentDebugMinutes = null,
                        estimatedBytes = 0,
                        currentKeyId = null,
                        currentKeyAccount = null,
                        availableKeyCount = 0,
                        totalKeyCount = 0,
                    ),
                    onBack = {},
                    onSelectLevel = {},
                    onToggleContentDebug = {},
                    onClearTelemetry = {},
                    onDisableContentDebugAndPurge = {},
                    onToggleEvent = {},
                    onDismissConfirmation = {},
                    onConfirmContentDebug = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.TELEMETRY_ROOT).assertExists()
        composeRule.onNodeWithText("云端会话链优化").assertDoesNotExist()
    }

    private fun emptyAiManagementState() = AiManagementUiState(
        configuration = AiRuntimeConfiguration(
            AiUseCase.entries.associateWith { useCase ->
                defaultModel(useCase)
            },
        ),
        keyProvider = AiProvider.GEMINI,
        providerSummaries = mapOf(
            AiProvider.GEMINI to emptyList(),
            AiProvider.DEEPSEEK to emptyList(),
        ),
        storageError = null,
        currentKeyAccount = null,
        input = "",
        resultMessage = null,
        confirmation = null,
    )
}
