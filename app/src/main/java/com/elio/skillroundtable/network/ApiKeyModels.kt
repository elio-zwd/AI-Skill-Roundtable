package com.elio.skillroundtable.network

import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
enum class ApiKeySource {
    LOCAL,
    REMOTE
}

@Serializable
enum class ApiKeyValidationState {
    UNVERIFIED,
    CHECKING,
    AVAILABLE,
    INVALID,
    NETWORK_ERROR,
    RATE_LIMITED
}

@Serializable
data class ApiKeyRecord(
    val id: String,
    val displayName: String,
    val key: String,
    val fingerprint: String,
    val source: ApiKeySource = ApiKeySource.LOCAL,
    val enabled: Boolean = true,
    val validationState: ApiKeyValidationState = ApiKeyValidationState.UNVERIFIED,
    val validationMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastValidatedAt: Long? = null
)

data class ApiKeySummary(
    val id: String,
    val displayName: String,
    val maskedKey: String,
    val source: ApiKeySource,
    val enabled: Boolean,
    val validationState: ApiKeyValidationState,
    val validationMessage: String?,
    val lastValidatedAt: Long?,
    val banExpireTime: Long,
    val remainingBanTimeMs: Long
)

data class BatchImportResult(
    val added: Int,
    val duplicates: Int,
    val invalid: Int,
    val overflow: Int,
    val importedIds: List<String> = emptyList()
)

data class ParsedKeyBatch(
    val keys: List<String>,
    val duplicates: Int,
    val invalid: Int
)

data class ApiKeyInfo(
    val id: String,
    val key: String,
    val account: String,
    val source: ApiKeySource = ApiKeySource.LOCAL
)

@Serializable
data class ApiLog(
    val keyId: String,
    val model: String,
    val requestTime: Long,
    val responseTime: Long,
    val statusCode: Int,
    val errorMessage: String? = null,
    val prompt: String = "",
    val responseText: String = ""
)

data class KeyStatus(
    val id: String,
    val displayName: String,
    val maskedKey: String,
    val source: ApiKeySource,
    val validationState: ApiKeyValidationState,
    val validationMessage: String?,
    val isBanned: Boolean,
    val banExpireTime: Long,
    val remainingBanTimeMs: Long,
    val isManualDisabled: Boolean
)

fun interface ApiKeyProvider {
    fun getRecords(): List<ApiKeyRecord>
}

class LocalApiKeyProvider(
    private val store: EncryptedApiKeyStore
) : ApiKeyProvider {
    override fun getRecords(): List<ApiKeyRecord> = store.read()
}

interface RemoteApiKeyProvider : ApiKeyProvider

object DisabledRemoteApiKeyProvider : RemoteApiKeyProvider {
    override fun getRecords(): List<ApiKeyRecord> = emptyList()
}

class CompositeApiKeyProvider(
    private val localProvider: ApiKeyProvider,
    private val remoteProvider: ApiKeyProvider
) : ApiKeyProvider {
    override fun getRecords(): List<ApiKeyRecord> {
        val fingerprints = mutableSetOf<String>()
        return (localProvider.getRecords() + remoteProvider.getRecords()).filter { record ->
            fingerprints.add(record.fingerprint)
        }
    }
}

object ApiKeyBatchParser {
    private const val MAX_KEY_LENGTH = 512

    fun parse(raw: String): ParsedKeyBatch {
        val normalized = raw.trim().removeSurrounding("[", "]")
        if (normalized.isBlank()) return ParsedKeyBatch(emptyList(), 0, 0)

        val unique = linkedSetOf<String>()
        var duplicates = 0
        var invalid = 0
        normalized
            .split(Regex("[,，\\r\\n]+"))
            .forEach { token ->
                val key = token.trim().removeSurrounding("\"").removeSurrounding("'").trim()
                when {
                    key.isBlank() -> Unit
                    key.length > MAX_KEY_LENGTH || key.any(Char::isWhitespace) -> invalid++
                    !unique.add(key) -> duplicates++
                }
            }
        return ParsedKeyBatch(unique.toList(), duplicates, invalid)
    }
}

fun fingerprintApiKey(key: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

fun maskApiKey(key: String): String = "••••••••${key.takeLast(4)}"
