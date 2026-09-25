package com.elio.jianyu.network

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiInteractionRequestContractTest {
    @Test
    fun gemini38InteractionRequestUsesOfficialModelAndSnakeCaseThinkingFields() {
        val model = AiModel.GEMINI_38_FLASH
        val request = CreateInteractionRequest(
            model = model.modelId,
            input = JsonPrimitive("test"),
            tools = listOf(Tool(type = "google_search")),
            store = true,
            generationConfig = InteractionGenerationConfig(
                maxOutputTokens = 4096,
                thinkingLevel = model.geminiInteractionThinkingLevel("minimal"),
                thinkingSummaries = "auto",
            ),
        )

        val body = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject
        val generationConfig = body.getValue("generation_config").jsonObject

        assertEquals("gemini-3.8-flash", body.getValue("model").jsonPrimitive.content)
        assertEquals("low", generationConfig.getValue("thinking_level").jsonPrimitive.content)
        assertEquals("4096", generationConfig.getValue("max_output_tokens").jsonPrimitive.content)
        assertEquals("auto", generationConfig.getValue("thinking_summaries").jsonPrimitive.content)
        assertEquals(
            "google_search",
            body.getValue("tools").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content,
        )
        assertTrue(body.getValue("store").jsonPrimitive.content.toBoolean())
        assertFalse(body.containsKey("generationConfig"))
        assertFalse(generationConfig.containsKey("thinkingLevel"))
    }

    @Test
    fun gemini25FlashInteractionRequestKeepsGoogleSearchOnUnifiedInteractionsPath() {
        val model = AiModel.GEMINI_25_FLASH
        val request = CreateInteractionRequest(
            model = model.modelId,
            input = JsonPrimitive("latest facts"),
            tools = listOf(Tool(type = "google_search")),
            store = false,
            generationConfig = InteractionGenerationConfig(
                thinkingLevel = model.geminiInteractionThinkingLevel("minimal"),
            ),
        )

        val body = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject
        val generationConfig = body.getValue("generation_config").jsonObject

        assertEquals("gemini-2.5-flash", body.getValue("model").jsonPrimitive.content)
        assertEquals("low", generationConfig.getValue("thinking_level").jsonPrimitive.content)
        assertEquals(
            "google_search",
            body.getValue("tools").jsonArray.single().jsonObject.getValue("type").jsonPrimitive.content,
        )
        assertFalse(body.getValue("store").jsonPrimitive.content.toBoolean())
    }
}
