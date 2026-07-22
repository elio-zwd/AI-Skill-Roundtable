package com.elio.skillroundtable.telemetry

object TelemetryRedactor {
    private val pemPattern = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----", RegexOption.IGNORE_CASE)
    private val jwtPattern = Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b")
    private val bearerPattern = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}")
    private val queryKeyPattern = Regex("(?i)((?:^|[?&\\s])(?:key|api_key|access_token)=)[^&\\s]+")
    private val credentialFieldPattern = Regex("(?i)([\\\"']?(?:key|api[_-]?key|access[_-]?token|authorization|secret)[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\\"',\\s}]+)")
    private val googleKeyPattern = Regex("\\bAIza[A-Za-z0-9_-]{20,}\\b")
    private val githubTokenPattern = Regex("\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b")
    private val emailPattern = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val phonePattern = Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)")
    private val longBase64Pattern = Regex("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{96,}={0,2}(?![A-Za-z0-9+/])")
    private val longHexPattern = Regex("(?i)(?<![0-9a-f])[0-9a-f]{96,}(?![0-9a-f])")

    fun redact(input: String): String = runCatching {
        input
            .replace(pemPattern, "[REDACTED_PRIVATE_KEY]")
            .replace(jwtPattern, "[REDACTED_JWT]")
            .replace(bearerPattern, "$1[REDACTED_TOKEN]")
            .replace(queryKeyPattern, "$1[REDACTED]")
            .replace(credentialFieldPattern, "$1[REDACTED]")
            .replace(googleKeyPattern, "[REDACTED_API_KEY]")
            .replace(githubTokenPattern, "[REDACTED_GITHUB_TOKEN]")
            .replace(emailPattern, "[REDACTED_EMAIL]")
            .replace(phonePattern, "[REDACTED_PHONE]")
            .replace(longBase64Pattern, "[REDACTED_LONG_ENCODED_DATA]")
            .replace(longHexPattern, "[REDACTED_LONG_HEX]")
    }.getOrElse { "[REDACTION_FAILED]" }

    fun stripUrlQuery(url: String): String {
        val questionMark = url.indexOf('?')
        if (questionMark >= 0) return url.substring(0, questionMark) + "?[REDACTED_QUERY]"
        val fragment = url.indexOf('#')
        return if (fragment >= 0) url.substring(0, fragment) + "#[REDACTED_FRAGMENT]" else url
    }
}

fun truncateTelemetryText(input: String, maxChars: Int): String {
    val safeMaxChars = maxChars.coerceAtLeast(0)
    if (input.length <= safeMaxChars) return input
    return input.take(safeMaxChars) + "…[已截断，原始长度 ${input.length}]"
}
