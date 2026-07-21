package com.example.skillroundtable.telemetry

import android.util.Log
import com.example.skillroundtable.BuildConfig

/**
 * Debug-only operational logging. Callers must not pass prompts, replies, search terms or other user content.
 */
object PrivacySafeLogger {
    private const val MAX_LOG_CHARS = 500

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, safe(message))
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, safe(message))
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        val errorType = error?.javaClass?.simpleName?.takeIf(String::isNotBlank)
        val safeMessage = if (errorType == null) message else "$message (type=$errorType)"
        Log.e(tag, safe(safeMessage))
    }

    private fun safe(message: String): String {
        return truncateTelemetryText(TelemetryRedactor.redact(message), MAX_LOG_CHARS)
    }
}
