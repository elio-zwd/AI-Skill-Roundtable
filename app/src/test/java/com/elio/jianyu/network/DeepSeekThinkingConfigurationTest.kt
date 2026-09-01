package com.elio.jianyu.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekThinkingConfigurationTest {
    @Test
    fun appThinkingLevelsMapToDeepSeekSupportedEffort() {
        assertEquals("low", deepSeekThinkingConfiguration("minimal")?.reasoningEffort)
        assertEquals("low", deepSeekThinkingConfiguration("low")?.reasoningEffort)
        assertEquals("high", deepSeekThinkingConfiguration("medium")?.reasoningEffort)
        assertEquals("high", deepSeekThinkingConfiguration("high")?.reasoningEffort)
    }

    @Test
    fun requestSerializesExplicitThinkingControls() {
        val configuration = requireNotNull(deepSeekThinkingConfiguration("medium"))
        val payload = Json.encodeToString(
            DeepSeekChatCompletionRequest(
                model = AiModel.DEEPSEEK_V4_FLASH.modelId,
                messages = listOf(DeepSeekMessage(role = "user", content = "test")),
                thinking = configuration.thinking,
                reasoningEffort = configuration.reasoningEffort,
            ),
        )

        assertTrue(payload.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(payload.contains("\"reasoning_effort\":\"high\""))
        assertFalse(payload.contains("reasoning_content"))
    }
}
