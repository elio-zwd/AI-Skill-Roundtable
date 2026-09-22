package com.elio.jianyu.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

interface SnapshotWrappingKeyProvider {
    fun getOrCreate(): SecretKey
    fun getExisting(): SecretKey
}

/** Android Keystore key dedicated to device-bound snapshots. */
class AndroidKeystoreSnapshotKeyProvider : SnapshotWrappingKeyProvider {
    override fun getOrCreate(): SecretKey {
        val store = keyStore()
        val existing = store.getKey(BackupProtocol.snapshotKeyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                BackupProtocol.snapshotKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun getExisting(): SecretKey =
        (keyStore().getKey(BackupProtocol.snapshotKeyAlias, null) as? SecretKey)
            ?: throw BackupException(BackupErrorCode.SNAPSHOT_KEY_UNAVAILABLE)

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
}
