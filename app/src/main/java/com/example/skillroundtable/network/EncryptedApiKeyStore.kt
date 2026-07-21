package com.example.skillroundtable.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
private data class EncryptedApiKeyEnvelope(
    val version: Int = 1,
    val iv: String,
    val ciphertext: String
)

/**
 * 使用 Android Keystore 加密完整密钥池，并写入不会参与系统备份的目录。
 */
class EncryptedApiKeyStore(context: Context) {
    companion object {
        private const val KEY_ALIAS = "skill_roundtable_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val FILE_NAME = "gemini_api_keys.enc"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val lock = Any()

    @Volatile
    var lastError: String? = null
        private set

    fun read(): List<ApiKeyRecord> = synchronized(lock) {
        if (!atomicFile.baseFile.exists()) return@synchronized emptyList()
        try {
            val envelope = json.decodeFromString<EncryptedApiKeyEnvelope>(
                atomicFile.readFully().toString(Charsets.UTF_8)
            )
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(envelope.iv, Base64.NO_WRAP))
            )
            val plaintext = cipher.doFinal(Base64.decode(envelope.ciphertext, Base64.NO_WRAP))
            lastError = null
            json.decodeFromString(ListSerializer(ApiKeyRecord.serializer()), plaintext.toString(Charsets.UTF_8))
        } catch (error: Exception) {
            lastError = "密钥保险箱无法解密，请清空后重新导入"
            emptyList()
        }
    }

    fun write(records: List<ApiKeyRecord>): Boolean = synchronized(lock) {
        var stream: FileOutputStream? = null
        try {
            val plaintext = json.encodeToString(ListSerializer(ApiKeyRecord.serializer()), records)
                .toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val envelope = EncryptedApiKeyEnvelope(
                iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
                ciphertext = Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP)
            )
            stream = atomicFile.startWrite()
            stream.write(json.encodeToString(EncryptedApiKeyEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            lastError = null
            true
        } catch (error: Exception) {
            stream?.let(atomicFile::failWrite)
            lastError = "密钥保险箱写入失败"
            false
        }
    }

    fun clear(): Boolean = synchronized(lock) {
        return@synchronized try {
            atomicFile.delete()
            lastError = null
            true
        } catch (error: Exception) {
            lastError = "密钥保险箱清理失败"
            false
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
