package com.elio.skillroundtable.telemetry

import android.content.Context
import com.elio.skillroundtable.BuildConfig
import com.elio.skillroundtable.network.ApiLog
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object TelemetryRepository {
    private const val SETTINGS_PREFS = "telemetry_settings"
    private const val EVENTS_PREFS = "roundtable_telemetry_prefs"
    private const val KEY_LEVEL = "telemetry_level"
    private const val KEY_DEBUG_ENABLED_AT = "content_debug_enabled_at"
    private const val KEY_DEBUG_EXPIRES_AT = "content_debug_expires_at"
    private const val KEY_EVENTS = "telemetry_events_json"
    private const val KEY_SCHEMA_VERSION = "telemetry_schema_version"
    private const val KEY_LEGACY_MIGRATED = "legacy_api_logs_removed"
    private const val SCHEMA_VERSION = 1
    private const val LEGACY_KEY_PREFS = "gemini_api_key_prefs"
    private const val LEGACY_LOGS_KEY = "telemetry_api_logs_json"
    private const val MAX_METADATA_CHARS = 500

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _level = MutableStateFlow(TelemetryLevel.METADATA_ONLY)
    val level: StateFlow<TelemetryLevel> = _level.asStateFlow()
    private val _events = MutableStateFlow<List<TelemetryEvent>>(emptyList())
    val events: StateFlow<List<TelemetryEvent>> = _events.asStateFlow()
    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()

    val legacyLogs = CopyOnWriteArrayList<ApiLog>()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val applicationContext = context.applicationContext
        appContext = applicationContext
        migrateLegacyTelemetry(applicationContext)
        loadSettings(applicationContext)
        loadEvents(applicationContext)
        enforceExpiry(System.currentTimeMillis())
        if (_level.value != TelemetryLevel.CONTENT_DEBUG) purgePreviewsLocked()
        CloudInteractionSettings.init(applicationContext)
        initialized = true
    }

    @Synchronized
    fun currentLevel(): TelemetryLevel {
        enforceExpiry(System.currentTimeMillis())
        return _level.value
    }

    @Synchronized
    fun setLevel(context: Context, requested: TelemetryLevel): Boolean {
        ensureInitialized(context)
        if (requested == TelemetryLevel.CONTENT_DEBUG) return false
        val resolved = TelemetryLevelResolver.resolve(requested, BuildConfig.DEBUG)
        _level.value = resolved
        val committed = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LEVEL, resolved.name)
            .remove(KEY_DEBUG_ENABLED_AT)
            .remove(KEY_DEBUG_EXPIRES_AT)
            .commit()
        purgePreviewsLocked()
        return committed
    }

    @Synchronized
    fun enableContentDebug(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!BuildConfig.DEBUG) return false
        ensureInitialized(context)
        val expiresAt = now + TelemetryRetentionPolicy.CONTENT_DEBUG_RETENTION_MS
        val committed = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LEVEL, TelemetryLevel.CONTENT_DEBUG.name)
            .putLong(KEY_DEBUG_ENABLED_AT, now)
            .putLong(KEY_DEBUG_EXPIRES_AT, expiresAt)
            .commit()
        if (committed) _level.value = TelemetryLevel.CONTENT_DEBUG
        return committed
    }

    @Synchronized
    fun contentDebugExpiresAtOrNull(): Long? {
        val context = appContext ?: return null
        return contentDebugExpiresAt(context)
    }

    @Synchronized
    fun contentDebugExpiresAt(context: Context): Long? {
        ensureInitialized(context)
        val now = System.currentTimeMillis()
        enforceExpiry(now)
        if (_level.value != TelemetryLevel.CONTENT_DEBUG) return null
        return readActiveContentDebugExpiry(context.applicationContext, now)
    }

    @Synchronized
    fun disableContentDebugAndPurgePreviews(context: Context): Boolean {
        ensureInitialized(context)
        val settings = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val committed = settings.edit()
            .putString(KEY_LEVEL, TelemetryLevel.METADATA_ONLY.name)
            .remove(KEY_DEBUG_ENABLED_AT)
            .remove(KEY_DEBUG_EXPIRES_AT)
            .commit()
        _level.value = TelemetryLevel.METADATA_ONLY
        purgePreviewsLocked()
        return committed
    }

    @Synchronized
    fun record(event: TelemetryEvent) {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        val activeLevel = currentLevel()
        if (activeLevel == TelemetryLevel.OFF) return
        val contentExpiresAt = if (activeLevel == TelemetryLevel.CONTENT_DEBUG) {
            readActiveContentDebugExpiry(context, now)
        } else {
            null
        }
        val normalized = normalizeEvent(event, contentExpiresAt)
        val updated = TelemetryRetentionPolicy.prune(listOf(normalized) + _events.value, now)
        _events.value = updated
        updateLegacyLogs(updated)
        persistEvents(context, updated)
    }

    @Synchronized
    fun recordLegacy(log: ApiLog) {
        val event = TelemetryEvent(
            id = UUID.randomUUID().toString(),
            timestamp = log.requestTime,
            durationMs = (log.responseTime - log.requestTime).coerceAtLeast(0L),
            endpoint = "legacy",
            model = log.model,
            keyId = log.keyId,
            statusCode = log.statusCode.takeIf { it >= 0 },
            failureType = log.errorMessage?.let { "LEGACY_ERROR" },
            requestPreview = log.prompt.takeIf { it.isNotBlank() },
            responsePreview = log.responseText.takeIf { it.isNotBlank() }
        )
        record(event)
    }

    @Synchronized
    fun clearAllTelemetry(context: Context): Boolean {
        ensureInitialized(context)
        _events.value = emptyList()
        legacyLogs.clear()
        val committed = context.applicationContext.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EVENTS)
            .remove(KEY_SCHEMA_VERSION)
            .commit()
        if (committed) _storageError.value = null
        return committed
    }

    @Synchronized
    fun estimatedBytes(): Int {
        return runCatching {
            json.encodeToString(ListSerializer(TelemetryEvent.serializer()), _events.value)
                .toByteArray(Charsets.UTF_8).size
        }.getOrDefault(0)
    }

    private fun ensureInitialized(context: Context) {
        if (!initialized) init(context)
    }

    private fun normalizeEvent(event: TelemetryEvent, contentExpiresAt: Long?): TelemetryEvent {
        val contentEnabled = contentExpiresAt != null
        return event.copy(
            durationMs = event.durationMs.coerceAtLeast(0L),
            endpoint = sanitizeMetadata(event.endpoint),
            model = event.model?.let(::sanitizeMetadata),
            keyId = event.keyId?.let(::sanitizeMetadata),
            failureType = event.failureType?.let {
                truncateTelemetryText(
                    TelemetryRedactor.redact(it),
                    TelemetryPreviewExtractor.MAX_ERROR_MESSAGE_CHARS
                )
            },
            retryCount = event.retryCount.coerceAtLeast(0),
            inputTokens = event.inputTokens?.coerceAtLeast(0),
            outputTokens = event.outputTokens?.coerceAtLeast(0),
            requestPreview = event.requestPreview
                ?.takeIf { contentEnabled }
                ?.let { sanitizePreview(it, TelemetryPreviewExtractor.MAX_REQUEST_PREVIEW_CHARS) },
            responsePreview = event.responsePreview
                ?.takeIf { contentEnabled }
                ?.let { sanitizePreview(it, TelemetryPreviewExtractor.MAX_RESPONSE_PREVIEW_CHARS) },
            expiresAt = contentExpiresAt
        )
    }

    private fun readActiveContentDebugExpiry(context: Context, now: Long): Long? {
        if (_level.value != TelemetryLevel.CONTENT_DEBUG) return null
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DEBUG_EXPIRES_AT, 0L)
            .takeIf { it > now }
    }

    private fun sanitizeMetadata(value: String): String {
        return truncateTelemetryText(TelemetryRedactor.redact(value), MAX_METADATA_CHARS)
    }

    private fun sanitizePreview(value: String, maxChars: Int): String {
        return truncateTelemetryText(TelemetryRedactor.redact(value), maxChars)
    }

    private fun purgePreviewsLocked() {
        val context = appContext ?: return
        val purged = TelemetryRetentionPolicy.prune(
            TelemetryRetentionPolicy.purgePreviews(_events.value),
            System.currentTimeMillis()
        )
        if (purged == _events.value) return
        _events.value = purged
        updateLegacyLogs(purged)
        persistEvents(context, purged)
    }

    private fun enforceExpiry(now: Long) {
        val context = appContext ?: return
        val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val expiresAt = settings.getLong(KEY_DEBUG_EXPIRES_AT, 0L)
        if (_level.value == TelemetryLevel.CONTENT_DEBUG && (expiresAt <= 0L || expiresAt <= now)) {
            settings.edit()
                .putString(KEY_LEVEL, TelemetryLevel.METADATA_ONLY.name)
                .remove(KEY_DEBUG_ENABLED_AT)
                .remove(KEY_DEBUG_EXPIRES_AT)
                .commit()
            _level.value = TelemetryLevel.METADATA_ONLY
            purgePreviewsLocked()
        }
        val pruned = TelemetryRetentionPolicy.prune(_events.value, now)
        if (pruned != _events.value) {
            _events.value = pruned
            updateLegacyLogs(pruned)
            persistEvents(context, pruned)
        }
    }

    private fun loadSettings(context: Context) {
        val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val saved = settings.getString(KEY_LEVEL, TelemetryLevel.METADATA_ONLY.name)
        val requested = runCatching { TelemetryLevel.valueOf(saved.orEmpty()) }
            .getOrDefault(TelemetryLevel.METADATA_ONLY)
        val resolved = TelemetryLevelResolver.resolve(requested, BuildConfig.DEBUG)
        _level.value = resolved
        if (resolved != requested) {
            settings.edit()
                .putString(KEY_LEVEL, resolved.name)
                .remove(KEY_DEBUG_ENABLED_AT)
                .remove(KEY_DEBUG_EXPIRES_AT)
                .commit()
        }
    }

    private fun loadEvents(context: Context) {
        val prefs = context.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EVENTS, null)
        if (raw.isNullOrBlank()) {
            _events.value = emptyList()
            updateLegacyLogs(emptyList())
            return
        }
        val loadedResult = runCatching {
            json.decodeFromString(ListSerializer(TelemetryEvent.serializer()), raw)
        }
        if (loadedResult.isFailure) {
            _storageError.value = "遥测数据损坏，已安全清空。"
            prefs.edit().remove(KEY_EVENTS).remove(KEY_SCHEMA_VERSION).commit()
            _events.value = emptyList()
            updateLegacyLogs(emptyList())
            return
        }
        val loaded = loadedResult.getOrThrow()
        val pruned = TelemetryRetentionPolicy.prune(loaded, System.currentTimeMillis())
        _events.value = pruned
        updateLegacyLogs(pruned)
        if (pruned != loaded) persistEvents(context, pruned)
    }

    private fun persistEvents(context: Context, events: List<TelemetryEvent>) {
        runCatching {
            val encoded = json.encodeToString(ListSerializer(TelemetryEvent.serializer()), events)
            context.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                .putString(KEY_EVENTS, encoded)
                .apply()
            _storageError.value = null
        }.onFailure {
            _storageError.value = "遥测写入失败，但网络请求未受影响。"
        }
    }

    private fun updateLegacyLogs(events: List<TelemetryEvent>) {
        legacyLogs.clear()
        legacyLogs.addAll(events.map { event ->
            ApiLog(
                keyId = event.keyId ?: "none",
                model = event.model ?: event.endpoint,
                requestTime = event.timestamp,
                responseTime = event.timestamp + event.durationMs,
                statusCode = event.statusCode ?: -1,
                errorMessage = event.failureType,
                prompt = event.requestPreview.orEmpty(),
                responseText = event.responsePreview.orEmpty()
            )
        })
    }

    private fun migrateLegacyTelemetry(context: Context) {
        val eventPrefs = context.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE)
        if (eventPrefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return
        val removed = context.getSharedPreferences(LEGACY_KEY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_LOGS_KEY)
            .commit()
        if (removed) {
            eventPrefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).commit()
        } else {
            _storageError.value = "旧版明文遥测清理失败，将在下次启动重试。"
        }
    }
}
