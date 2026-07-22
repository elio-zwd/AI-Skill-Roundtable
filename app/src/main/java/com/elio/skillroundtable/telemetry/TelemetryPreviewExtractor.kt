package com.elio.skillroundtable.telemetry

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
        if (body.isDuplex() || body.isOneShot()) {
            return "[请求正文已省略，bodyType=streaming]"
        }
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
            hasThoughtStep = raw.contains(
                Regex("\\\"type\\\"\\s*:\\s*\\\"thought\\\"", RegexOption.IGNORE_CASE)
            )
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
            val normalizedKey = key.lowercase()
            val value = source.opt(key)
            when {
                normalizedKey == "data" && value is String -> {
                    val mimeType = source.optString("mimeType", source.optString("mime_type", "unknown"))
                    output.put(key, "[附件已省略，mimeType=$mimeType, encodedChars=${value.length}]")
                }
                normalizedKey in INTERACTION_ID_KEYS && value is String -> output.put(key, maskIdentifier(value))
                isResponse && normalizedKey == "id" && value is String -> output.put(key, maskIdentifier(value))
                isResponse && normalizedKey == "summary" -> output.put(key, "[THOUGHT_SUMMARY_OMITTED]")
                isResponse && normalizedKey == "signature" -> output.put(key, "[THOUGHT_SIGNATURE_OMITTED]")
                isResponse && normalizedKey in SEARCH_CONTENT_KEYS -> output.put(key, omittedSearchValue(value))
                normalizedKey in URL_KEYS && value is String -> output.put(key, TelemetryRedactor.stripUrlQuery(value))
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

    private fun omittedSearchValue(value: Any?): String {
        val count = when (value) {
            is JSONArray -> value.length()
            is JSONObject -> value.length()
            else -> null
        }
        return if (count == null) "[SEARCH_CONTENT_OMITTED]" else "[SEARCH_CONTENT_OMITTED count=$count]"
    }

    private fun maskIdentifier(value: String): String {
        if (value.length <= 10) return "[REDACTED_INTERACTION_ID]"
        return value.take(6) + "…" + value.takeLast(4)
    }

    private val INTERACTION_ID_KEYS = setOf(
        "previous_interaction_id",
        "previousinteractionid",
        "interaction_id",
        "interactionid"
    )
    private val SEARCH_CONTENT_KEYS = setOf(
        "result",
        "snippet",
        "websearchqueries",
        "groundingchunks",
        "groundingmetadata"
    )
    private val URL_KEYS = setOf("url", "uri")
}

data class PreviewResult(val preview: String?, val hasThoughtStep: Boolean)
