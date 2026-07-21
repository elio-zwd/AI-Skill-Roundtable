package com.elio.skillroundtable.telemetry

object TelemetryRetentionPolicy {
    const val METADATA_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    const val CONTENT_DEBUG_RETENTION_MS = 24L * 60 * 60 * 1000
    const val MAX_METADATA_EVENTS = 100
    const val MAX_CONTENT_EVENTS = 20

    fun prune(events: List<TelemetryEvent>, now: Long): List<TelemetryEvent> {
        val retained = events
            .asSequence()
            .filter { event ->
                if (event.containsContentPreview) {
                    (event.expiresAt ?: (event.timestamp + CONTENT_DEBUG_RETENTION_MS)) > now
                } else {
                    event.timestamp + METADATA_RETENTION_MS > now
                }
            }
            .sortedByDescending(TelemetryEvent::timestamp)
            .toList()

        var metadataCount = 0
        var contentCount = 0
        return retained.filter { event ->
            if (event.containsContentPreview) {
                (++contentCount) <= MAX_CONTENT_EVENTS
            } else {
                (++metadataCount) <= MAX_METADATA_EVENTS
            }
        }
    }

    fun purgePreviews(events: List<TelemetryEvent>): List<TelemetryEvent> {
        return events.map { it.copy(requestPreview = null, responsePreview = null, expiresAt = null) }
    }
}
