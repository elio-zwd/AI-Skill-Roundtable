package com.elio.jianyu.ui.screens.settings

import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.AiRuntimeConfiguration
import com.elio.jianyu.network.AiUseCase
import com.elio.jianyu.network.defaultModel
import com.elio.jianyu.network.ApiKeySource
import com.elio.jianyu.network.ApiKeySummary
import com.elio.jianyu.network.ApiKeyValidationState
import com.elio.jianyu.network.BatchImportResult
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
            summaries = listOf(
                summary("available"),
                summary("disabled", enabled = false),
                summary("invalid", validationState = ApiKeyValidationState.INVALID),
                summary("cooling", remainingBanTimeMs = 60_000L),
            ),
            storageError = null,
            currentKeyAccount = null,
            input = "key",
            resultMessage = null,
            confirmation = null,
        )

        assertEquals(1, state.availableKeyCount)
        assertTrue(state.canImport)
    }

    @Test
    fun importAndTagContractsAreProviderScoped() {
        assertFalse(
            AiManagementUiState(
                configuration = configuration(),
                keyProvider = AiProvider.GEMINI,
                summaries = emptyList(),
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
