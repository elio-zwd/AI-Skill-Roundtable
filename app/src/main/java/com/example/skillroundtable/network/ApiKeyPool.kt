package com.example.skillroundtable.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 用户自带 Gemini API Key 的统一管理入口。
 *
 * 完整 Key 只在网络调用边界短暂出现；UI 只能读取掩码摘要。
 */
object ApiKeyPool {
    private const val PREFS_NAME = "gemini_api_key_prefs"
    private const val KEY_LAST_USED_ID = "last_used_key_id"
    private const val MAX_KEYS = 50
    private const val BAN_DURATION_MS = 24 * 60 * 60 * 1000L

    val apiLogs = CopyOnWriteArrayList<ApiLog>()

    private val _summaries = MutableStateFlow<List<ApiKeySummary>>(emptyList())
    val summaries: StateFlow<List<ApiKeySummary>> = _summaries.asStateFlow()

    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()

    private var appContext: Context? = null
    private var store: EncryptedApiKeyStore? = null
    private var provider: CompositeApiKeyProvider? = null

    @Synchronized
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            val localStore = EncryptedApiKeyStore(context.applicationContext)
            store = localStore
            provider = CompositeApiKeyProvider(
                localProvider = LocalApiKeyProvider(localStore),
                remoteProvider = DisabledRemoteApiKeyProvider
            )
            migrateLegacyCustomKey(context.applicationContext)
            loadLogsFromPrefs()
        }
        refreshSummaries(context.applicationContext)
    }

    fun importBatch(context: Context, raw: String): BatchImportResult {
        ensureInitialized(context)
        val parsed = ApiKeyBatchParser.parse(raw)
        val existing = localRecords().toMutableList()
        val existingFingerprints = existing.mapTo(mutableSetOf()) { it.fingerprint }
        var duplicates = parsed.duplicates
        val candidates = parsed.keys.filter { key ->
            val isNew = existingFingerprints.add(fingerprintApiKey(key))
            if (!isNew) duplicates++
            isNew
        }
        val capacity = (MAX_KEYS - allRecords().size).coerceAtLeast(0)
        val accepted = candidates.take(capacity)
        val overflow = candidates.size - accepted.size
        var nextNumber = existing.mapNotNull { it.displayName.removePrefix("K").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
        val created = accepted.map { key ->
            ApiKeyRecord(
                id = "local-${UUID.randomUUID()}",
                displayName = "K${nextNumber++}",
                key = key,
                fingerprint = fingerprintApiKey(key)
            )
        }
        if (created.isNotEmpty()) {
            saveLocal(existing + created)
        }
        return BatchImportResult(
            added = created.size,
            duplicates = duplicates,
            invalid = parsed.invalid,
            overflow = overflow,
            importedIds = created.map(ApiKeyRecord::id)
        )
    }

    suspend fun validateKeys(context: Context, keyIds: List<String>) = coroutineScope {
        ensureInitialized(context)
        val semaphore = Semaphore(2)
        keyIds.distinct().map { keyId ->
            async { semaphore.withPermit { validateKey(context, keyId) } }
        }.awaitAll()
    }

    suspend fun validateKey(context: Context, keyId: String): ApiKeyValidationState {
        ensureInitialized(context)
        val record = localRecords().firstOrNull { it.id == keyId } ?: return ApiKeyValidationState.INVALID
        updateRecord(keyId) {
            it.copy(validationState = ApiKeyValidationState.CHECKING, validationMessage = null)
        }
        var state: ApiKeyValidationState
        var message: String?
        try {
            val response = RetrofitClient.service.validateApiKey(apiKey = record.key)
            when (response.code()) {
                in 200..299 -> {
                    state = ApiKeyValidationState.AVAILABLE
                    message = null
                }
                400, 401, 403 -> {
                    state = ApiKeyValidationState.INVALID
                    message = "鉴权失败（HTTP ${response.code()}）"
                }
                429 -> {
                    state = ApiKeyValidationState.RATE_LIMITED
                    message = "请求频率受限"
                    banKey(context, keyId)
                }
                else -> {
                    state = ApiKeyValidationState.NETWORK_ERROR
                    message = "验证服务返回 HTTP ${response.code()}"
                }
            }
        } catch (error: Exception) {
            state = ApiKeyValidationState.NETWORK_ERROR
            message = "网络验证失败"
        }
        updateRecord(keyId) {
            it.copy(
                validationState = state,
                validationMessage = message,
                lastValidatedAt = System.currentTimeMillis()
            )
        }
        return state
    }

    fun setKeyDisabled(context: Context, keyId: String, disabled: Boolean) {
        ensureInitialized(context)
        updateRecord(keyId) { it.copy(enabled = !disabled) }
    }

    fun deleteKey(context: Context, keyId: String): Boolean {
        ensureInitialized(context)
        val remaining = localRecords().filterNot { it.id == keyId }
        if (!saveLocal(remaining)) return false
        val prefs = getPrefs(context)
        val editor = prefs.edit().remove("ban_$keyId")
        prefs.all.forEach { (name, value) ->
            if (name.startsWith("session_key_") && value == keyId) editor.remove(name)
        }
        editor.apply()
        return true
    }

    fun clearAllKeys(context: Context): Boolean {
        ensureInitialized(context)
        val success = store?.clear() == true
        if (success) {
            val prefs = getPrefs(context)
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith("session_key_") || it.startsWith("ban_") }
                .forEach(editor::remove)
            editor.apply()
            refreshSummaries(context)
        }
        return success
    }

    fun getAvailableKeys(context: Context): List<ApiKeyInfo> {
        ensureInitialized(context)
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        return allRecords()
            .filter { record ->
                record.enabled &&
                    record.validationState != ApiKeyValidationState.INVALID &&
                    prefs.getLong("ban_${record.id}", 0L) <= now
            }
            .map { record ->
                ApiKeyInfo(record.id, record.key, record.displayName, record.source)
            }
    }

    fun getOrBindSessionKey(context: Context, sessionId: Long): ApiKeyInfo? {
        ensureInitialized(context)
        val prefs = getPrefs(context)
        val available = getAvailableKeys(context)
        val boundId = prefs.getString("session_key_$sessionId", null)
        available.firstOrNull { it.id == boundId }?.let { return it }
        val selected = getKeyAttemptOrder(context).firstOrNull() ?: return null
        bindSessionKey(context, sessionId, selected.id)
        return selected
    }

    fun bindSessionKey(context: Context, sessionId: Long, keyId: String) {
        getPrefs(context).edit().putString("session_key_$sessionId", keyId).apply()
    }

    fun getKeyAttemptOrder(context: Context): List<ApiKeyInfo> {
        val available = getAvailableKeys(context)
        val lastUsedId = getLastUsedKeyId(context)
        if (available.size <= 1 || lastUsedId == null) return available
        return available.filter { it.id != lastUsedId } + available.filter { it.id == lastUsedId }
    }

    fun getAttemptCount(context: Context): Int = getAvailableKeys(context).size.coerceAtLeast(1)

    fun findKeyId(key: String): String {
        val fingerprint = fingerprintApiKey(key)
        return allRecords().firstOrNull { it.fingerprint == fingerprint }?.displayName ?: "用户密钥"
    }

    fun getKeyStatuses(context: Context): List<KeyStatus> {
        ensureInitialized(context)
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        return allRecords().map { record ->
            val banExpire = prefs.getLong("ban_${record.id}", 0L)
            KeyStatus(
                id = record.id,
                displayName = record.displayName,
                maskedKey = maskApiKey(record.key),
                source = record.source,
                validationState = record.validationState,
                validationMessage = record.validationMessage,
                isBanned = banExpire > now,
                banExpireTime = banExpire,
                remainingBanTimeMs = (banExpire - now).coerceAtLeast(0L),
                isManualDisabled = !record.enabled
            )
        }
    }

    fun getSummary(context: Context, keyId: String): ApiKeySummary? {
        ensureInitialized(context)
        return summaries.value.firstOrNull { it.id == keyId }
    }

    fun banKey(context: Context, keyId: String) {
        getPrefs(context).edit().putLong("ban_$keyId", System.currentTimeMillis() + BAN_DURATION_MS).apply()
        refreshSummaries(context)
    }

    /**
     * 将特定 Key 标记为 INVALID 状态并存储消息。
     */
    fun markKeyInvalid(context: Context, keyId: String, message: String) {
        ensureInitialized(context)
        updateRecord(keyId) {
            it.copy(
                validationState = ApiKeyValidationState.INVALID,
                validationMessage = message,
                lastValidatedAt = System.currentTimeMillis()
            )
        }
    }

    /**
     * 将特定 Key 标记为冷却状态，定义具体冷却时长。
     */
    fun banKeyWithDuration(context: Context, keyId: String, durationMs: Long) {
        ensureInitialized(context)
        getPrefs(context).edit().putLong("ban_$keyId", System.currentTimeMillis() + durationMs).apply()
        refreshSummaries(context)
    }

    fun clearBans(context: Context) {
        ensureInitialized(context)
        val editor = getPrefs(context).edit()
        allRecords().forEach { editor.remove("ban_${it.id}") }
        editor.apply()
        refreshSummaries(context)
    }

    fun setLastUsedKeyId(context: Context, keyId: String) {
        getPrefs(context).edit().putString(KEY_LAST_USED_ID, keyId).apply()
    }

    fun getLastUsedKeyId(context: Context): String? = getPrefs(context).getString(KEY_LAST_USED_ID, null)

    fun addLog(log: ApiLog) {
        synchronized(apiLogs) {
            apiLogs.add(0, log)
            while (apiLogs.size > 50) apiLogs.removeAt(apiLogs.lastIndex)
        }
        saveLogsToPrefs()
    }

    private fun ensureInitialized(context: Context) {
        if (appContext == null) init(context)
    }

    private fun allRecords(): List<ApiKeyRecord> = provider?.getRecords().orEmpty()

    private fun localRecords(): List<ApiKeyRecord> = store?.read().orEmpty()

    private fun saveLocal(records: List<ApiKeyRecord>): Boolean {
        val success = store?.write(records) == true
        appContext?.let(::refreshSummaries)
        return success
    }

    private fun updateRecord(keyId: String, transform: (ApiKeyRecord) -> ApiKeyRecord) {
        val records = localRecords()
        if (records.none { it.id == keyId }) return
        saveLocal(records.map { if (it.id == keyId) transform(it) else it })
    }

    private fun refreshSummaries(context: Context) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        _storageError.value = store?.lastError
        _summaries.value = allRecords().map { record ->
            val banExpire = prefs.getLong("ban_${record.id}", 0L)
            ApiKeySummary(
                id = record.id,
                displayName = record.displayName,
                maskedKey = maskApiKey(record.key),
                source = record.source,
                enabled = record.enabled,
                validationState = record.validationState,
                validationMessage = record.validationMessage,
                lastValidatedAt = record.lastValidatedAt,
                banExpireTime = banExpire,
                remainingBanTimeMs = (banExpire - now).coerceAtLeast(0L)
            )
        }
    }

    private fun migrateLegacyCustomKey(context: Context) {
        val prefs = getPrefs(context)
        val legacy = prefs.getString("custom_api_key", null)?.trim()
        if (!legacy.isNullOrBlank() && legacy != "your_gemini_api_key_here" && localRecords().isEmpty()) {
            importBatch(context, legacy)
        }
        prefs.edit()
            .remove("custom_api_key")
            .remove("use_built_in_keys")
            .apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun loadLogsFromPrefs() {
        val context = appContext ?: return
        val jsonString = getPrefs(context).getString("telemetry_api_logs_json", null) ?: return
        try {
            val logs = Json.decodeFromString(ListSerializer(ApiLog.serializer()), jsonString)
            synchronized(apiLogs) {
                apiLogs.clear()
                apiLogs.addAll(logs)
            }
        } catch (_: Exception) {
            // 旧遥测损坏不影响应用启动。
        }
    }

    private fun saveLogsToPrefs() {
        val context = appContext ?: return
        try {
            val jsonString = Json.encodeToString(ListSerializer(ApiLog.serializer()), apiLogs.toList())
            getPrefs(context).edit().putString("telemetry_api_logs_json", jsonString).apply()
        } catch (_: Exception) {
            // 遥测写入失败不阻断主请求。
        }
    }
}
