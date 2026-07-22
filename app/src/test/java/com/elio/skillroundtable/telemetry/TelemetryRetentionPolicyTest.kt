package com.elio.skillroundtable.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryRetentionPolicyTest {
    @Test
    fun removesExpiredMetadataAndContentEvents() {
        val now = 10_000_000_000L
        val fresh = event("fresh", now - 1_000)
        val old = event("old", now - TelemetryRetentionPolicy.METADATA_RETENTION_MS - 1)
        val expiredContent = event("debug", now - 1_000, preview = "secret", expiresAt = now - 1)
        assertEquals(listOf("fresh"), TelemetryRetentionPolicy.prune(listOf(old, fresh, expiredContent), now).map { it.id })
    }

    @Test
    fun enforcesSeparateEventLimitsAndPurgesPreviews() {
        val now = 20_000_000_000L
        val metadata = (0 until 120).map { event("m$it", now - it) }
        val content = (0 until 30).map { event("c$it", now - it, preview = "preview", expiresAt = now + 1_000) }
        val pruned = TelemetryRetentionPolicy.prune(metadata + content, now)
        assertEquals(100, pruned.count { !it.containsContentPreview })
        assertEquals(20, pruned.count { it.containsContentPreview })
        val purged = TelemetryRetentionPolicy.purgePreviews(pruned)
        purged.forEach { assertNull(it.requestPreview) }
    }

    private fun event(id: String, timestamp: Long, preview: String? = null, expiresAt: Long? = null) = TelemetryEvent(
        id = id,
        timestamp = timestamp,
        durationMs = 1,
        endpoint = "POST /test",
        requestPreview = preview,
        expiresAt = expiresAt
    )
}
