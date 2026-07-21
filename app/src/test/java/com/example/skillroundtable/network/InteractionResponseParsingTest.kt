package com.example.skillroundtable.network

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionResponseParsingTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun decodesThoughtSummaryArrayBeforeModelOutput() {
        val payload = """
            {
              "id": "interaction-test-1",
              "object": "interaction",
              "model": "gemini-3.5-flash",
              "status": "completed",
              "steps": [
                {
                  "type": "thought",
                  "signature": "encrypted-signature",
                  "summary": [
                    {
                      "type": "text",
                      "text": "思考摘要不得影响最终答案解析"
                    }
                  ]
                },
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "最终回答"
                    }
                  ]
                }
              ],
              "usage": {
                "total_input_tokens": 10,
                "total_output_tokens": 20,
                "total_thought_tokens": 30,
                "total_tokens": 60
              }
            }
        """.trimIndent()

        val interaction = json.decodeFromString<Interaction>(payload)

        assertEquals("最终回答", interaction.outputText)
        assertEquals(1, interaction.steps.first().summary.size)
        assertEquals(
            "思考摘要不得影响最终答案解析",
            interaction.steps.first().summary.first().text
        )
    }

    @Test
    fun decodesEmptyThoughtSummaryUsedByBrokerResponses() {
        val payload = """
            {
              "id": "interaction-test-2",
              "status": "completed",
              "steps": [
                {
                  "type": "thought",
                  "signature": "encrypted-signature",
                  "summary": []
                },
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\"selectedFiles\":[],\"needSearch\":false,\"searchQueries\":[]}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val interaction = json.decodeFromString<Interaction>(payload)

        assertTrue(interaction.steps.first().summary.isEmpty())
        assertEquals(
            """{"selectedFiles":[],"needSearch":false,"searchQueries":[]}""",
            interaction.outputText
        )
    }

    @Test
    fun decodesThoughtStepWhenSummaryIsAbsent() {
        val payload = """
            {
              "id": "interaction-test-3",
              "status": "completed",
              "steps": [
                {
                  "type": "thought",
                  "signature": "encrypted-signature"
                },
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "无摘要时也能正常回答"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val interaction = json.decodeFromString<Interaction>(payload)

        assertTrue(interaction.steps.first().summary.isEmpty())
        assertEquals("无摘要时也能正常回答", interaction.outputText)
    }

    @Test
    fun toleratesPolymorphicBuiltInToolResult() {
        val payload = """
            {
              "id": "interaction-test-4",
              "status": "completed",
              "steps": [
                {
                  "type": "google_search_result",
                  "signature": "tool-signature",
                  "result": {
                    "items": [
                      {
                        "title": "Example",
                        "url": "https://example.test"
                      }
                    ]
                  }
                },
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "搜索后的最终回答"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val interaction = json.decodeFromString<Interaction>(payload)

        assertEquals("搜索后的最终回答", interaction.outputText)
    }
}
