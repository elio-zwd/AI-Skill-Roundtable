package com.elio.jianyu.network

import android.content.Context
import com.elio.jianyu.network.keys.ApiKeyLease
import com.elio.jianyu.network.retry.ApiCallFailure
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

data class ProviderSessionKey(
    val id: String,
    val account: String,
    val provider: AiProvider,
)

/**
 * 一个提供商的 BYOK 密钥管理入口。
 *
 * 负责该提供商的加密持久化、导入、状态、冷却和会话绑定；不负责发起业务请求或解析协议。
 */
class ProviderKeyRepository(
    context: Context,
    private val provider: AiProvider,
) {
    private companion object {
        const val KEY_LAST_USED_ID = "last_used_key_id"
        const val MAX_KEYS = 50
        const val DEFAULT_BAN_DURATION_MS = 24 * 60 * 60 * 1000L
    }

    private val appContext = context.applicationContext
    private val store = EncryptedApiKeyStore(appContext, "${provider.storageId}_api_keys.enc")
    private val prefs = appContext.getSharedPreferences(
        "${provider.storageId}_api_key_prefs",
        Context.MODE_PRIVATE,
    )

    private val _summaries = MutableStateFlow<List<ApiKeySummary>>(emptyList())
    val summaries: StateFlow<List<ApiKeySummary>> = _summaries.asStateFlow()

    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()

    init {
        refreshSummaries()
    }

    fun importBatch(raw: String): BatchImportResult {
        val parsed = ApiKeyBatchParser.parse(raw)
        val existing = records().toMutableList()
        val existingFingerprints = existing.mapTo(mutableSetOf()) { it.fingerprint }
        var duplicates = parsed.duplicates
        val candidates = parsed.keys.filter { key ->
            val isNew = existingFingerprints.add(fingerprintApiKey(key))
            if (!isNew) duplicates++
            isNew
        }
        val accepted = candidates.take((MAX_KEYS - existing.size).coerceAtLeast(0))
        val overflow = candidates.size - accepted.size
        var nextNumber = existing.mapNotNull { it.displayName.removePrefix(providerKeyPrefix()).toIntOrNull() }
            .maxOrNull()?.plus(1) ?: 1
        val created = accepted.map { key ->
            ApiKeyRecord(
                id = "${provider.storageId}-${UUID.randomUUID()}",
                displayName = "${providerKeyPrefix()}${nextNumber++}",
                key = key,
                fingerprint = fingerprintApiKey(key),
            )
        }
        if (created.isNotEmpty()) save(existing + created)
        return BatchImportResult(
            added = created.size,
            duplicates = duplicates,
            invalid = parsed.invalid,
            overflow = overflow,
            importedIds = created.map(ApiKeyRecord::id),
        )
    }

    suspend fun validateKeys(
        keyIds: List<String>,
        validator: suspend (String) -> ApiKeyValidationState,
    ) = coroutineScope {
        val semaphore = Semaphore(2)
        keyIds.distinct().map { keyId ->
            async { semaphore.withPermit { validateKey(keyId, validator) } }
        }.awaitAll()
    }

    suspend fun validateKey(
        keyId: String,
        validator: suspend (String) -> ApiKeyValidationState,
    ): ApiKeyValidationState {
        val record = records().firstOrNull { it.id == keyId } ?: return ApiKeyValidationState.INVALID
        update(keyId) { it.copy(validationState = ApiKeyValidationState.CHECKING, validationMessage = null) }
        val state = runCatching { validator(record.key) }.getOrElse { ApiKeyValidationState.NETWORK_ERROR }
        if (state == ApiKeyValidationState.RATE_LIMITED) ban(keyId, DEFAULT_BAN_DURATION_MS)
        update(keyId) {
            it.copy(
                validationState = state,
                validationMessage = validationMessage(state),
                lastValidatedAt = System.currentTimeMillis(),
            )
        }
        return state
    }

    fun setDisabled(keyId: String, disabled: Boolean) {
        update(keyId) { it.copy(enabled = !disabled) }
    }

    fun delete(keyId: String): Boolean {
        val remaining = records().filterNot { it.id == keyId }
        if (!save(remaining)) return false
        val editor = prefs.edit().remove("ban_$keyId").remove("rate_limit_count_$keyId")
        prefs.all.forEach { (name, value) ->
            if (name.startsWith("session_key_") && value == keyId) editor.remove(name)
        }
        editor.apply()
        return true
    }

    fun clear(): Boolean {
        val success = store.clear()
        if (!success) return false
        prefs.edit().clear().apply()
        refreshSummaries()
        return true
    }

    fun hasAvailableKeys(): Boolean = availableRecords().isNotEmpty()

    fun createAttemptPlan(sessionId: Long, preferredKeyId: String? = null): List<ApiKeyLease> {
        val available = availableRecords().map { record ->
            ApiKeyLease(
                keyId = record.id,
                displayName = record.displayName,
                provider = provider,
                source = record.source,
            )
        }
        return ProviderKeyAttemptPlanner.create(
            available = available,
            sessionBoundKeyId = prefs.getString("session_key_$sessionId", null),
            lastUsedKeyId = prefs.getString(KEY_LAST_USED_ID, null),
            preferredKeyId = preferredKeyId,
        )
    }

    fun getOrBindSessionKey(sessionId: Long): ProviderSessionKey? {
        val selected = createAttemptPlan(sessionId).firstOrNull() ?: return null
        bindSessionKey(sessionId, selected.keyId)
        return ProviderSessionKey(selected.keyId, selected.displayName, provider)
    }

    fun findKeyIdOrNull(secret: String): String? = records()
        .firstOrNull { it.fingerprint == fingerprintApiKey(secret) }
        ?.displayName

    fun getKeyStatuses(): List<KeyStatus> {
        val now = System.currentTimeMillis()
        return records().map { record ->
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
                isManualDisabled = !record.enabled,
            )
        }
    }

    fun clearBans() {
        val editor = prefs.edit()
        records().forEach { record ->
            editor.remove("ban_${record.id}")
            editor.remove("rate_limit_count_${record.id}")
        }
        editor.apply()
        refreshSummaries()
    }

    internal fun recordSuccess(sessionId: Long, keyId: String) {
        prefs.edit()
            .putString("session_key_$sessionId", keyId)
            .putString(KEY_LAST_USED_ID, keyId)
            .remove("rate_limit_count_$keyId")
            .apply()
    }

    internal fun recordFailure(keyId: String, failure: ApiCallFailure) {
        when (failure) {
            is ApiCallFailure.Http -> when (failure.code) {
                401, 403 -> update(keyId) {
                    it.copy(
                        validationState = ApiKeyValidationState.INVALID,
                        validationMessage = "鉴权失败或权限不足 (HTTP ${failure.code})",
                        lastValidatedAt = System.currentTimeMillis(),
                    )
                }
                429 -> ban(keyId, failure.retryAfterMs ?: nextCooldownDuration(keyId))
            }
            else -> Unit
        }
    }

    internal fun secretFor(keyId: String): String? = records()
        .firstOrNull { it.id == keyId }
        ?.key

    private fun records(): List<ApiKeyRecord> = store.read()

    private fun availableRecords(): List<ApiKeyRecord> {
        val now = System.currentTimeMillis()
        return records().filter { record ->
            record.enabled &&
                record.validationState != ApiKeyValidationState.INVALID &&
                prefs.getLong("ban_${record.id}", 0L) <= now
        }
    }

    private fun bindSessionKey(sessionId: Long, keyId: String) {
        prefs.edit().putString("session_key_$sessionId", keyId).apply()
    }

    private fun ban(keyId: String, durationMs: Long) {
        prefs.edit()
            .putLong("ban_$keyId", System.currentTimeMillis() + durationMs)
            .putInt("rate_limit_count_$keyId", prefs.getInt("rate_limit_count_$keyId", 0) + 1)
            .apply()
        refreshSummaries()
    }

    private fun nextCooldownDuration(keyId: String): Long = when (
        prefs.getInt("rate_limit_count_$keyId", 0)
    ) {
        0 -> 60 * 1000L
        1 -> 5 * 60 * 1000L
        2 -> 30 * 60 * 1000L
        else -> DEFAULT_BAN_DURATION_MS
    }

    private fun update(keyId: String, transform: (ApiKeyRecord) -> ApiKeyRecord) {
        val current = records()
        if (current.none { it.id == keyId }) return
        save(current.map { if (it.id == keyId) transform(it) else it })
    }

    private fun save(records: List<ApiKeyRecord>): Boolean {
        val success = store.write(records)
        refreshSummaries()
        return success
    }

    private fun refreshSummaries() {
        val now = System.currentTimeMillis()
        _storageError.value = store.lastError
        _summaries.value = records().map { record ->
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
                remainingBanTimeMs = (banExpire - now).coerceAtLeast(0L),
            )
        }
    }

    private fun providerKeyPrefix(): String = when (provider) {
        AiProvider.GEMINI -> "G"
        AiProvider.DEEPSEEK -> "D"
    }

    private fun validationMessage(state: ApiKeyValidationState): String? = when (state) {
        ApiKeyValidationState.AVAILABLE -> null
        ApiKeyValidationState.INVALID -> "鉴权失败"
        ApiKeyValidationState.NETWORK_ERROR -> "网络验证失败"
        ApiKeyValidationState.RATE_LIMITED -> "请求频率受限"
        ApiKeyValidationState.UNVERIFIED,
        ApiKeyValidationState.CHECKING -> null
    }
}
