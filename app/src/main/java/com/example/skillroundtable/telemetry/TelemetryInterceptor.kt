package com.example.skillroundtable.telemetry

import com.example.skillroundtable.BuildConfig
import com.example.skillroundtable.network.ApiKeyPool
import java.io.IOException
import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Response

class TelemetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val initialLevel = TelemetryRepository.currentLevel()
        if (initialLevel == TelemetryLevel.OFF) return chain.proceed(request)

        val startedAt = System.currentTimeMillis()
        val endpoint = "${request.method} ${request.url.encodedPath}"
        val model = Regex("models/([^:/]+)").find(request.url.encodedPath)?.groupValues?.getOrNull(1)
        val apiKey = request.url.queryParameter("key")
        val keyId = apiKey?.takeIf(String::isNotBlank)?.let(ApiKeyPool::findKeyId)
        val contentDebugAtStart = initialLevel == TelemetryLevel.CONTENT_DEBUG && BuildConfig.DEBUG
        val requestPreview = if (contentDebugAtStart) TelemetryPreviewExtractor.requestPreview(request) else null

        var response: Response? = null
        var failure: Throwable? = null
        var responsePreview: String? = null
        var hasThoughtStep = false
        var contentDebugAtCompletion = false
        try {
            response = chain.proceed(request)
            contentDebugAtCompletion = contentDebugAtStart &&
                TelemetryRepository.currentLevel() == TelemetryLevel.CONTENT_DEBUG
            if (contentDebugAtCompletion) {
                val preview = TelemetryPreviewExtractor.responsePreview(response)
                responsePreview = preview.preview
                hasThoughtStep = preview.hasThoughtStep
            }
            return response
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            runCatching {
                val completedAt = System.currentTimeMillis()
                val event = TelemetryEventFactory.create(
                    level = initialLevel,
                    id = UUID.randomUUID().toString(),
                    timestamp = startedAt,
                    durationMs = (completedAt - startedAt).coerceAtLeast(0L),
                    endpoint = endpoint,
                    model = model,
                    keyId = keyId,
                    statusCode = response?.code,
                    failureType = classifyFailure(failure, response?.code),
                    requestPreview = requestPreview,
                    responsePreview = responsePreview,
                    hasThoughtStep = hasThoughtStep,
                    contentExpiresAt = if (contentDebugAtCompletion) {
                        TelemetryRepository.contentDebugExpiresAtOrNull()
                    } else {
                        null
                    }
                )
                if (event != null) TelemetryRepository.record(event)
            }
        }
    }

    private fun classifyFailure(error: Throwable?, statusCode: Int?): String? {
        return when {
            error is IOException -> "NETWORK"
            error != null -> error.javaClass.simpleName.take(80)
            statusCode == 429 -> "RATE_LIMITED"
            statusCode != null && statusCode in 400..499 -> "HTTP_4XX"
            statusCode != null && statusCode in 500..599 -> "HTTP_5XX"
            else -> null
        }
    }
}
