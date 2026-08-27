package com.elio.jianyu.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {
    @Test
    fun defaultsMatchEachProviderAndModelsStayProviderScoped() {
        assertEquals(AiModel.GEMINI_35_FLASH, defaultModel(AiProvider.GEMINI))
        assertEquals(AiModel.DEEPSEEK_V4_FLASH, defaultModel(AiProvider.DEEPSEEK))
        assertTrue(AiModel.entries.filter { it.provider == AiProvider.GEMINI }.contains(AiModel.GEMINI_36_FLASH))
        assertTrue(AiModel.entries.filter { it.provider == AiProvider.DEEPSEEK }.contains(AiModel.DEEPSEEK_V4_PRO))
    }

    @Test
    fun modelSelectionIsIndependentForEachTextUseCase() {
        val configuration = AiRuntimeConfiguration(
            mapOf(
                AiUseCase.SESSION_TITLE to AiModel.DEEPSEEK_V4_FLASH,
                AiUseCase.MATERIAL_BROKER to AiModel.GEMINI_31_FLASH_LITE,
                AiUseCase.WEB_GROUNDING to AiModel.GEMINI_36_FLASH,
                AiUseCase.ROUNDTABLE_ANSWER to AiModel.DEEPSEEK_V4_PRO,
                AiUseCase.ISSUE_EXECUTION to AiModel.GEMINI_35_FLASH,
            ),
        )

        assertEquals(AiModel.DEEPSEEK_V4_FLASH, configuration.modelFor(AiUseCase.SESSION_TITLE))
        assertEquals(AiModel.GEMINI_31_FLASH_LITE, configuration.modelFor(AiUseCase.MATERIAL_BROKER))
        assertEquals(AiModel.GEMINI_36_FLASH, configuration.modelFor(AiUseCase.WEB_GROUNDING))
        assertEquals(AiModel.DEEPSEEK_V4_PRO, configuration.modelFor(AiUseCase.ROUNDTABLE_ANSWER))
        assertEquals(AiModel.GEMINI_35_FLASH, configuration.modelFor(AiUseCase.ISSUE_EXECUTION))
    }
}
