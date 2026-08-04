package com.elio.jianyu.telemetry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionTelemetryRedactionTest {
    @Test
    fun keyIdIsRedactedBeforeLogging() {
        val redacted = TelemetryRedactor.redact(
            "Starting JianyuExecution stream with keyId=imported-key-42",
        )

        assertTrue(redacted.contains("keyId=[REDACTED]"))
        assertFalse(redacted.contains("imported-key-42"))
    }

    @Test
    fun snakeCaseKeyIdIsAlsoRedacted() {
        val redacted = TelemetryRedactor.redact("key_id: local-key-index-7")

        assertTrue(redacted.contains("key_id: [REDACTED]"))
        assertFalse(redacted.contains("local-key-index-7"))
    }
}
