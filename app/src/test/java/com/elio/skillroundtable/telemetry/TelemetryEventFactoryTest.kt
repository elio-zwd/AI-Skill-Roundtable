package com.elio.skillroundtable.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEventFactoryTest {
    @Test
    fun offDoesNotCreateEvent() {
        assertNull(create(TelemetryLevel.OFF))
    }

    @Test
    fun metadataOnlyDropsContent() {
        val event = create(TelemetryLevel.METADATA_ONLY)!!
        assertNull(event.requestPreview)
        assertNull(event.responsePreview)
    }

    @Test
    fun contentDebugKeepsControlledPreview() {
        val event = create(TelemetryLevel.CONTENT_DEBUG)!!
        assertEquals("request", event.requestPreview)
        assertEquals("response", event.responsePreview)
        assertTrue(event.hasThoughtStep)
    }

    @Test
    fun releaseResolverRejectsContentDebug() {
        assertEquals(TelemetryLevel.METADATA_ONLY, TelemetryLevelResolver.resolve(TelemetryLevel.CONTENT_DEBUG, false))
    }

    private fun create(level: TelemetryLevel) = TelemetryEventFactory.create(
        level = level,
        id = "id",
        timestamp = 1,
        durationMs = 2,
        endpoint = "POST /test",
        model = "model",
        keyId = "K1",
        statusCode = 200,
        failureType = null,
        requestPreview = "request",
        responsePreview = "response",
        hasThoughtStep = true,
        contentExpiresAt = 10
    )
}
