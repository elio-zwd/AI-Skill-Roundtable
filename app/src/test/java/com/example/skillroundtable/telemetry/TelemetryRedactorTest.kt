package com.example.skillroundtable.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRedactorTest {
    @Test
    fun redactsSecretsAndPersonalIdentifiers() {
        val fakeGeminiKey = "AI" + "za" + "A".repeat(30)
        val input = "key=$fakeGeminiKey Bearer abcdefghijklmnop user@example.com 13800138000"
        val result = TelemetryRedactor.redact(input)
        assertFalse(result.contains(fakeGeminiKey))
        assertFalse(result.contains("abcdefghijklmnop"))
        assertFalse(result.contains("user@example.com"))
        assertFalse(result.contains("13800138000"))
        assertTrue(result.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun redactsJwtAndGithubTokenWithoutDestroyingChineseText() {
        val jwt = "eyJabcdefghijk.eyJabcdefghijk.abcdefghijklm"
        val github = "gh" + "p_" + "A".repeat(24)
        val result = TelemetryRedactor.redact("普通中文内容 $jwt $github")
        assertTrue(result.startsWith("普通中文内容"))
        assertTrue(result.contains("[REDACTED_JWT]"))
        assertTrue(result.contains("[REDACTED_GITHUB_TOKEN]"))
    }

    @Test
    fun truncationReportsOriginalLength() {
        assertEquals("123…[已截断，原始长度 6]", truncateTelemetryText("123456", 3))
    }
}
