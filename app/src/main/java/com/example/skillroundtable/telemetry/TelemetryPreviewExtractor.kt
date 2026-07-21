package com.example.skillroundtable.telemetry

import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

object TelemetryPreviewExtractor {
    const val MAX_REQUEST_BODY_BYTES = 16L * 1024
    const val MAX_RESPONSE_BODY_BYTES = 32L * 1024
    const val MAX_REQUEST_PREVIEW_CHARS = 2_000
    const val MAX_RESPONSE_PREVIEW_CHARS = 2_000
    const val MAX_ERROR_MESSAGE_CHARS = 500

    fun requestPreview(request: Request): String? = runCatching {
        val body = request.body ?: return null
        val contentLength = body.contentLength()
        if (contentLength < 0 || contentLength > MAX_REQUEST_BODY_BYTES) {
            return "[请求正文已省略，bytes=${if (contentLength < 0) "unknown" else contentLength}]"
        }
        val buffer = Buffer()
        body.writeTo(buffer)
        sanitizeJsonOrText(buffer.readUtf8(), isResponse = false, MAX_REQUEST_PREVIEW_CHARS)
    }.getOrNull()

    fun responsePreview(response: Response): PreviewResult = runCatching {
        val raw = response.peekBody(MAX_RESPONSE_BODY_BYTES).string()
        PreviewResult(
            preview = sanitizeJsonOrText(raw, isResponse = true, MAX_RESPONSE_PREVIEW_CHARS),
            hasThoughtStep = raw.contains(Regex("\\\"type\\\"\\s*:\\s*\\\"thought\\\""))
        )
    }.getOrElse { PreviewResult(null, false) }

    private fun sanitizeJsonOrText(raw: String, isResponse: Boolean, maxChars: Int): String {
        val normalized = raw.trim()
        val sanitized = when {
            normalized.startsWith("{") -> sanitizeObject(JSONObject(normalized), isResponse).toString(2)
            normalized.startsWith("[") -> sanitizeArray(JSONArray(normalized), isResponse).toString(2)
            else -> normalized
        }
        return truncateTelemetryText(TelemetryRedactor.redact(sanitized), maxChars)
    }

    private fun sanitizeObject(source: JSONObject, isResponse: Boolean): JSONObject {
        val output = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = source.opt(key)
            when {
                key == "data" && value is String -> output.put(key, "[附件已省略，encodedChars=${value.length}]")
                key == "previous_interaction_id" && value is String -> output.put(key, maskIdentifier(value))
                key == "summary" && isResponse -> output.put(key, "[THOUGHT_SUMMARY_OMITTED]")
                key == "result" && isResponse && value is JSONArray -> output.put(key, "[SEARCH_RESULTS_OMITTED count=${value.length()}]")
                key == "snippet" && isResponse -> output.put(key, "[SEARCH_SNIPPET_OMITTED]")
                key in setOf("url", "uri") && value is String -> output.put(key, TelemetryRedactor.stripUrlQuery(value))
                value is JSONObject -> output.put(key, sanitizeObject(value, isResponse))
                value is JSONArray -> output.put(key, sanitizeArray(value, isResponse))
                else -> output.put(key, value)
            }
        }
        return output
    }

    private fun sanitizeArray(source: JSONArray, isResponse: Boolean): JSONArray {
        val output = JSONArray()
        for (index in 0 until source.length()) {
            when (val value = source.opt(index)) {
                is JSONObject -> output.put(sanitizeObject(value, isResponse))
                is JSONArray -> output.put(sanitizeArray(value, isResponse))
                else -> output.put(value)
            }
        }
        return output
    }

    private fun maskIdentifier(value: String): String {
        if (value.length <= 10) return "[REDACTED_INTERACTION_ID]"
        return value.take(6) + "…" + value.takeLast(4)
    }
}

data class PreviewResult(val preview: String?, val hasThoughtStep: Boolean)
