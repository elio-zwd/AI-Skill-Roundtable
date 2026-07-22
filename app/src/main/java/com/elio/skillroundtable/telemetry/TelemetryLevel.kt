package com.elio.skillroundtable.telemetry

enum class TelemetryLevel {
    OFF,
    METADATA_ONLY,
    CONTENT_DEBUG
}

object TelemetryLevelResolver {
    fun resolve(requested: TelemetryLevel, isDebugBuild: Boolean): TelemetryLevel {
        return if (requested == TelemetryLevel.CONTENT_DEBUG && !isDebugBuild) {
            TelemetryLevel.METADATA_ONLY
        } else {
            requested
        }
    }
}
