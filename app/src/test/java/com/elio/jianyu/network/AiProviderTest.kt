package com.elio.jianyu.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {
    @Test
    fun geminiModelCatalogExposesCurrentFlashModelsAndUses38AsProviderDefault() {
        val geminiModels = AiModel.entries.filter { it.provider == AiProvider.GEMINI }

        assertEquals(AiModel.GEMINI_38_FLASH, defaultModel(AiProvider.GEMINI))
        assertEquals(
            listOf(
                AiModel.GEMINI_38_FLASH,
                AiModel.GEMINI_37_FLASH,
                AiModel.GEMINI_36_FLASH,
                AiModel.GEMINI_35_FLASH,
                AiModel.GEMINI_35_FLASH_LITE,
                AiModel.GEMINI_31_FLASH_LITE,
                AiModel.GEMINI_25_FLASH,
                AiModel.GEMINI_25_FLASH_LITE,
            ),
            geminiModels,
        )
        assertEquals("gemini-3.8-flash", AiModel.GEMINI_38_FLASH.modelId)
        assertEquals("gemini-3.7-flash", AiModel.GEMINI_37_FLASH.modelId)
        assertEquals("gemini-3.5-flash-lite", AiModel.GEMINI_35_FLASH_LITE.modelId)
        assertEquals("gemini-2.5-flash", AiModel.GEMINI_25_FLASH.modelId)
        assertEquals("gemini-2.5-flash-lite", AiModel.GEMINI_25_FLASH_LITE.modelId)
        assertTrue(geminiModels.all { it.supportsWebGrounding })
        assertEquals(AiModel.DEEPSEEK_V4_FLASH, defaultModel(AiProvider.DEEPSEEK))
        assertTrue(AiModel.entries.filter { it.provider == AiProvider.DEEPSEEK }.contains(AiModel.DEEPSEEK_V4_PRO))
    }

    @Test
    fun webGroundingUses25FlashByDefaultWithoutChangingOtherGeminiDefaults() {
        assertEquals(AiModel.GEMINI_25_FLASH, defaultModel(AiUseCase.WEB_GROUNDING))
        assertEquals(AiModel.GEMINI_38_FLASH, defaultModel(AiUseCase.SESSION_TITLE))
        assertEquals(AiModel.GEMINI_38_FLASH, defaultModel(AiUseCase.MATERIAL_BROKER))
        assertEquals(AiModel.GEMINI_38_FLASH, defaultModel(AiUseCase.ROUNDTABLE_ANSWER))
        assertEquals(AiModel.GEMINI_38_FLASH, defaultModel(AiUseCase.ISSUE_EXECUTION))
    }

    @Test
    fun geminiInteractionThinkingLevelAdaptsMinimalByModel() {
        val minimalExpectations = mapOf(
            AiModel.GEMINI_38_FLASH to "low",
            AiModel.GEMINI_37_FLASH to "low",
            AiModel.GEMINI_36_FLASH to "minimal",
            AiModel.GEMINI_35_FLASH to "minimal",
            AiModel.GEMINI_35_FLASH_LITE to "minimal",
            AiModel.GEMINI_31_FLASH_LITE to "minimal",
            AiModel.GEMINI_25_FLASH to "low",
            AiModel.GEMINI_25_FLASH_LITE to "low",
        )

        minimalExpectations.forEach { (model, expectedMinimal) ->
            assertEquals(expectedMinimal, model.geminiInteractionThinkingLevel("minimal"))
            assertEquals("low", model.geminiInteractionThinkingLevel("low"))
            assertEquals("medium", model.geminiInteractionThinkingLevel("medium"))
            assertEquals("high", model.geminiInteractionThinkingLevel("high"))
        }
    }

    @Test
    fun geminiInteractionThinkingLevelRejectsUnsupportedLevel() {
        assertThrows(IllegalArgumentException::class.java) {
            AiModel.GEMINI_38_FLASH.geminiInteractionThinkingLevel("ultra")
        }
    }

    @Test
    fun issueExecutionKeepsStableInternalIdButUsesCurrentArtifactTerminology() {
        assertEquals("ISSUE_EXECUTION", AiUseCase.ISSUE_EXECUTION.name)
        assertEquals("成果生成", AiUseCase.ISSUE_EXECUTION.displayName)
        assertEquals("生成并整理可保存的成果内容", AiUseCase.ISSUE_EXECUTION.description)
    }

    @Test
    fun modelSelectionIsIndependentForEachTextUseCase() {
        val configuration = AiRuntimeConfiguration(
            mapOf(
                AiUseCase.SESSION_TITLE to AiModel.DEEPSEEK_V4_FLASH,
                AiUseCase.MATERIAL_BROKER to AiModel.GEMINI_31_FLASH_LITE,
                AiUseCase.WEB_GROUNDING to AiModel.GEMINI_25_FLASH,
                AiUseCase.ROUNDTABLE_ANSWER to AiModel.DEEPSEEK_V4_PRO,
                AiUseCase.ISSUE_EXECUTION to AiModel.GEMINI_37_FLASH,
            ),
        )

        assertEquals(AiModel.DEEPSEEK_V4_FLASH, configuration.modelFor(AiUseCase.SESSION_TITLE))
        assertEquals(AiModel.GEMINI_31_FLASH_LITE, configuration.modelFor(AiUseCase.MATERIAL_BROKER))
        assertEquals(AiModel.GEMINI_25_FLASH, configuration.modelFor(AiUseCase.WEB_GROUNDING))
        assertEquals(AiModel.DEEPSEEK_V4_PRO, configuration.modelFor(AiUseCase.ROUNDTABLE_ANSWER))
        assertEquals(AiModel.GEMINI_37_FLASH, configuration.modelFor(AiUseCase.ISSUE_EXECUTION))
    }
}
