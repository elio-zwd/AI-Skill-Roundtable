package com.elio.skillroundtable.telemetry

import android.util.Log
import com.elio.skillroundtable.BuildConfig

/**
 * Debug-only operational logging. Callers must not pass prompts, replies, search terms or other user content.
 * Android Log failures are swallowed so plain JVM tests never fall back to stdout with sensitive data.
 */
object PrivacySafeLogger {
    private const val MAX_LOG_CHARS = 500

    fun d(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.d(tag, safe(message)) }
    }

    fun w(tag: String, message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.w(tag, safe(message)) }
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        val errorType = error?.javaClass?.simpleName?.takeIf(String::isNotBlank)
        val safeMessage = if (errorType == null) message else "$message (type=$errorType)"
        runCatching { Log.e(tag, safe(safeMessage)) }
    }

    private fun safe(message: String): String {
        return truncateTelemetryText(TelemetryRedactor.redact(message), MAX_LOG_CHARS)
    }
}
