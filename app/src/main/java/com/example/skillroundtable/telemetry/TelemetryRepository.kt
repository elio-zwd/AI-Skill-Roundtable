package com.example.skillroundtable.telemetry

import android.content.Context
import com.example.skillroundtable.BuildConfig
import com.example.skillroundtable.network.ApiLog
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

    @Synchronized
    fun init(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        migrateLegacyTelemetry(applicationContext)
        loadSettings(applicationContext)
        loadEvents(applicationContext)
        enforceExpiry(System.currentTimeMillis())
        if (_level.value != TelemetryLevel.CONTENT_DEBUG) purgePreviews()
        CloudInteractionSettings.init(applicationContext)
    }

    fun currentLevel(): TelemetryLevel {
        enforceExpiry(System.currentTimeMillis())
        return _level.value
    }

    fun setLevel(context: Context, requested: TelemetryLevel): Boolean {
        if (appContext == null) init(context)
        val resolved = TelemetryLevelResolver.resolve(requested, BuildConfig.DEBUG)
        if (requested == TelemetryLevel.CONTENT_DEBUG && resolved != requested) return false
        _level.value = resolved
        val settings = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val editor = settings.edit().putString(KEY_LEVEL, resolved.name)
        if (resolved != TelemetryLevel.CONTENT_DEBUG) {
            editor.remove(KEY_DEBUG_ENABLED_AT).remove(KEY_DEBUG_EXPIRES_AT)
        }
        editor.apply()
        if (resolved != TelemetryLevel.CONTENT_DEBUG) purgePreviews()
        return true
    }

    fun enableContentDebug(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        if (!BuildConfig.DEBUG) return false
        if (appContext == null) init(context)
        val expiresAt = now + TelemetryRetentionPolicy.CONTENT_DEBUG_RETENTION_MS
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LEVEL, TelemetryLevel.CONTENT_DEBUG.name)
            .putLong(KEY_DEBUG_ENABLED_AT, now)
            .putLong(KEY_DEBUG_EXPIRES_AT, expiresAt)
            .apply()
        _level.value = TelemetryLevel.CONTENT_DEBUG
        return true
    }

    fun contentDebugExpiresAtOrNull(): Long? = appContext?.let(::contentDebugExpiresAt)

    fun contentDebugExpiresAt(context: Context): Long? {
        if (appContext == null) init(context)
        val value = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_DEBUG_EXPIRES_AT, 0L)
        return value.takeIf { it > 0L }
    }

    fun disableContentDebugAndPurgePreviews(context: Context): Boolean {
        if (appContext == null) init(context)
        val settings = context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val committed = settings.edit()
            .putString(KEY_LEVEL, TelemetryLevel.METADATA_ONLY.name)
            .remove(KEY_DEBUG_ENABLED_AT)
            .remove(KEY_DEBUG_EXPIRES_AT)
            .commit()
        _level.value = TelemetryLevel.METADATA_ONLY
        purgePreviews()
        return committed
    }

    fun record(event: TelemetryEvent) {
        val context = appContext ?: return
        val level = currentLevel()
        if (level == TelemetryLevel.OFF) return
        val normalized = if (level == TelemetryLevel.CONTENT_DEBUG) {
            val expiresAt = contentDebugExpiresAt(context)
            event.copy(expiresAt = event.expiresAt ?: expiresAt)
        } else {
            event.copy(requestPreview = null, responsePreview = null, expiresAt = null)
        }
        val updated = TelemetryRetentionPolicy.prune(listOf(normalized) + _events.value, System.currentTimeMillis())
        _events.value = updated
        updateLegacyLogs(updated)
        persistEvents(context, updated)
    }

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

    fun clearAllTelemetry(context: Context): Boolean {
        if (appContext == null) init(context)
        _events.value = emptyList()
        legacyLogs.clear()
        return context.applicationContext.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_EVENTS).commit()
    }

    fun estimatedBytes(): Int {
        return runCatching {
            json.encodeToString(ListSerializer(TelemetryEvent.serializer()), _events.value)
                .toByteArray(Charsets.UTF_8).size
        }.getOrDefault(0)
    }

    private fun purgePreviews() {
        val context = appContext ?: return
        val purged = TelemetryRetentionPolicy.purgePreviews(_events.value)
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
                .apply()
            _level.value = TelemetryLevel.METADATA_ONLY
            purgePreviews()
        }
        val pruned = TelemetryRetentionPolicy.prune(_events.value, now)
        if (pruned != _events.value) {
            _events.value = pruned
            updateLegacyLogs(pruned)
            persistEvents(context, pruned)
        }
    }

    private fun loadSettings(context: Context) {
        val saved = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LEVEL, TelemetryLevel.METADATA_ONLY.name)
        val requested = runCatching { TelemetryLevel.valueOf(saved.orEmpty()) }
            .getOrDefault(TelemetryLevel.METADATA_ONLY)
        _level.value = TelemetryLevelResolver.resolve(requested, BuildConfig.DEBUG)
    }

    private fun loadEvents(context: Context) {
        val prefs = context.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_EVENTS, null)
        if (raw.isNullOrBlank()) {
            _events.value = emptyList()
            updateLegacyLogs(emptyList())
            return
        }
        val loaded = runCatching {
            json.decodeFromString(ListSerializer(TelemetryEvent.serializer()), raw)
        }.onFailure {
            _storageError.value = "遥测数据损坏，已安全清空。"
            prefs.edit().remove(KEY_EVENTS).apply()
        }.getOrDefault(emptyList())
        val pruned = TelemetryRetentionPolicy.prune(loaded, System.currentTimeMillis())
        _events.value = pruned
        updateLegacyLogs(pruned)
    }

    private fun persistEvents(context: Context, events: List<TelemetryEvent>) {
        runCatching {
            val encoded = json.encodeToString(ListSerializer(TelemetryEvent.serializer()), events)
            context.getSharedPreferences(EVENTS_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
                .putString(KEY_EVENTS, encoded)
                .apply()
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
        context.getSharedPreferences(LEGACY_KEY_PREFS, Context.MODE_PRIVATE)
            .edit().remove(LEGACY_LOGS_KEY).commit()
        eventPrefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }
}
