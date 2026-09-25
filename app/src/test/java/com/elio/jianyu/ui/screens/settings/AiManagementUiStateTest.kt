package com.elio.jianyu.ui.screens.settings

import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.AiRuntimeConfiguration
import com.elio.jianyu.network.AiUseCase
import com.elio.jianyu.network.ApiKeySource
import com.elio.jianyu.network.ApiKeySummary
import com.elio.jianyu.network.ApiKeyValidationState
import com.elio.jianyu.network.BatchImportResult
import com.elio.jianyu.network.defaultModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiManagementUiStateTest {
    @Test
    fun availabilityExcludesDisabledInvalidAndCoolingKeys() {
        val state = AiManagementUiState(
            configuration = configuration(),
            keyProvider = AiProvider.DEEPSEEK,
            providerSummaries = mapOf(
                AiProvider.GEMINI to emptyList(),
                AiProvider.DEEPSEEK to listOf(
                    summary("available"),
                    summary("disabled", enabled = false),
                    summary("invalid", validationState = ApiKeyValidationState.INVALID),
                    summary("cooling", remainingBanTimeMs = 60_000L),
                ),
            ),
            storageError = null,
            currentKeyAccount = null,
            input = "key",
            resultMessage = null,
            confirmation = null,
        )

        assertEquals(1, state.availableKeyCount)
        assertEquals(1, state.availableKeyCount(AiProvider.DEEPSEEK))
        assertTrue(state.canImport)
    }

    @Test
    fun providerStatusUsesFriendlySummaryForEmptyAndConfiguredPools() {
        val state = AiManagementUiState(
            configuration = configuration(),
            keyProvider = AiProvider.GEMINI,
            providerSummaries = mapOf(
                AiProvider.GEMINI to emptyList(),
                AiProvider.DEEPSEEK to listOf(
                    summary("available"),
                    summary("disabled", enabled = false),
                ),
            ),
            storageError = null,
            currentKeyAccount = null,
            input = "",
            resultMessage = null,
            confirmation = null,
        )

        assertEquals("未配置 Key", state.providerStatus(AiProvider.GEMINI))
        assertEquals("2 个 Key · 1 个可用", state.providerStatus(AiProvider.DEEPSEEK))
    }

    @Test
    fun importAndTagContractsAreProviderScoped() {
        assertFalse(
            AiManagementUiState(
                configuration = configuration(),
                keyProvider = AiProvider.GEMINI,
                providerSummaries = mapOf(
                    AiProvider.GEMINI to emptyList(),
                    AiProvider.DEEPSEEK to emptyList(),
                ),
                storageError = null,
                currentKeyAccount = null,
                input = " ",
                resultMessage = null,
                confirmation = null,
            ).canImport,
        )
        assertEquals("新增 2，重复 1，非法 3，超限 4", aiBatchImportSummary(BatchImportResult(2, 1, 3, 4)))
        assertEquals(
            "ai_management_model_ROUNDTABLE_ANSWER_GEMINI_36_FLASH",
            AiManagementTestTags.model("ROUNDTABLE_ANSWER_GEMINI_36_FLASH"),
        )
        assertEquals(
            "ai_management_use_case_SESSION_TITLE",
            AiManagementTestTags.useCase("SESSION_TITLE"),
        )
        assertEquals(
            "ai_management_key_provider_GEMINI",
            AiManagementTestTags.keyProviderCard("GEMINI"),
        )
    }

    private fun summary(
        id: String,
        enabled: Boolean = true,
        validationState: ApiKeyValidationState = ApiKeyValidationState.AVAILABLE,
        remainingBanTimeMs: Long = 0L,
    ) = ApiKeySummary(
        id = id,
        displayName = id,
        maskedKey = "••••0000",
        source = ApiKeySource.LOCAL,
        enabled = enabled,
        validationState = validationState,
        validationMessage = null,
        lastValidatedAt = null,
        banExpireTime = 0L,
        remainingBanTimeMs = remainingBanTimeMs,
    )

    private fun configuration() = AiRuntimeConfiguration(
        AiUseCase.entries.associateWith { useCase -> defaultModel(useCase.supportedProviders.first()) },
    )
}
