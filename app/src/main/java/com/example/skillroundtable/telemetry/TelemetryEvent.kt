package com.example.skillroundtable.telemetry

import kotlinx.serialization.Serializable

@Serializable
data class TelemetryEvent(
    val id: String,
    val timestamp: Long,
    val durationMs: Long,
    val endpoint: String,
    val model: String? = null,
    val keyId: String? = null,
    val statusCode: Int? = null,
    val failureType: String? = null,
    val retryCount: Int = 0,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val hasThoughtStep: Boolean = false,
    val requestPreview: String? = null,
    val responsePreview: String? = null,
    val expiresAt: Long? = null
) {
    val containsContentPreview: Boolean
        get() = requestPreview != null || responsePreview != null
}

object TelemetryEventFactory {
    fun create(
        level: TelemetryLevel,
        id: String,
        timestamp: Long,
        durationMs: Long,
        endpoint: String,
        model: String?,
        keyId: String?,
        statusCode: Int?,
        failureType: String?,
        requestPreview: String?,
        responsePreview: String?,
        hasThoughtStep: Boolean,
        contentExpiresAt: Long?
    ): TelemetryEvent? {
        if (level == TelemetryLevel.OFF) return null
        val contentEnabled = level == TelemetryLevel.CONTENT_DEBUG
        return TelemetryEvent(
            id = id,
            timestamp = timestamp,
            durationMs = durationMs,
            endpoint = endpoint,
            model = model,
            keyId = keyId,
            statusCode = statusCode,
            failureType = failureType,
            hasThoughtStep = hasThoughtStep,
            requestPreview = requestPreview.takeIf { contentEnabled },
            responsePreview = responsePreview.takeIf { contentEnabled },
            expiresAt = contentExpiresAt.takeIf { contentEnabled }
        )
    }
}
