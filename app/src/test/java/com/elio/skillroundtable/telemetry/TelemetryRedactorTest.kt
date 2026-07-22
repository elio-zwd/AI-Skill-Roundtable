package com.elio.skillroundtable.telemetry

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
    fun redactsJwtGithubTokenAndJsonCredentialFieldsWithoutDestroyingChineseText() {
        val jwt = "eyJabcdefghijk.eyJabcdefghijk.abcdefghijklm"
        val github = "gh" + "p_" + "A".repeat(24)
        val result = TelemetryRedactor.redact(
            "普通中文内容 $jwt $github {\"apiKey\":\"plain-secret-value\"}"
        )
        assertTrue(result.startsWith("普通中文内容"))
        assertTrue(result.contains("[REDACTED_JWT]"))
        assertTrue(result.contains("[REDACTED_GITHUB_TOKEN]"))
        assertFalse(result.contains("plain-secret-value"))
    }

    @Test
    fun stripsUrlQueriesAndFragments() {
        assertEquals(
            "wss://example.test/live?[REDACTED_QUERY]",
            TelemetryRedactor.stripUrlQuery("wss://example.test/live?key=secret#fragment")
        )
        assertEquals(
            "https://example.test/path#[REDACTED_FRAGMENT]",
            TelemetryRedactor.stripUrlQuery("https://example.test/path#access-token")
        )
    }

    @Test
    fun longInputAndNonPositiveTruncationDoNotThrow() {
        val result = TelemetryRedactor.redact("普通内容".repeat(20_000))
        assertTrue(result.isNotBlank())
        assertEquals("…[已截断，原始长度 3]", truncateTelemetryText("123", 0))
    }

    @Test
    fun truncationReportsOriginalLength() {
        assertEquals("123…[已截断，原始长度 6]", truncateTelemetryText("123456", 3))
    }
}
