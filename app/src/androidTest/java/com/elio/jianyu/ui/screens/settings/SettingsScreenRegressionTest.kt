package com.elio.jianyu.ui.screens.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    fun aiManagementScreen_exposesRootAndImportControls() {
        composeRule.setContent {
            SkillRoundtableTheme {
                AiManagementScreen(
                    uiState = AiManagementUiState(
                        configuration = AiRuntimeConfiguration(
                            AiUseCase.entries.associateWith { useCase ->
                                defaultModel(useCase.supportedProviders.first())
                            },
                        ),
                        keyProvider = AiProvider.GEMINI,
                        summaries = emptyList(),
                        storageError = null,
                        currentKeyAccount = null,
                        input = "",
                        resultMessage = null,
                        confirmation = null,
                    ),
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
        composeRule.onNodeWithTag(AiManagementTestTags.IMPORT_INPUT).assertExists()
        composeRule.onNodeWithTag(AiManagementTestTags.IMPORT_BUTTON).assertExists()
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
                        cloudInteractionEnabled = false,
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
                    onCloudInteractionChange = {},
                    onToggleEvent = {},
                    onDismissConfirmation = {},
                    onConfirmContentDebug = {},
                    onConfirmCloudInteraction = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.TELEMETRY_ROOT).assertExists()
    }
}
