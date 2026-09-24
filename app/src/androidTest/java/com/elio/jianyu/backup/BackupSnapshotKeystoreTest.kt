package com.elio.jianyu.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSnapshotKeystoreTest {
    @Test
    fun randomizedIvKeystoreKeyCreatesAndDecryptsSnapshot() {
        val alias = "jianyu_backup_snapshot_wrap_test_${System.nanoTime()}"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            val key = generator.generateKey()
            val manifest = BackupManifest(
                formatId = BackupProtocol.snapshotFormatId,
                createdAt = 1L,
                appVersionName = "test",
                appVersionCode = 1L,
                sourceRoomVersion = 14L,
                logicalEntryCount = 0L,
                blobCount = 0L,
            )

            val snapshot = BackupEnvelopeWriter.createSnapshot(
                key,
                SnapshotBackupInput(manifest = manifest, entities = emptyList()),
            )
            val parsed = BackupCrypto.parseEnvelope(snapshot, snapshot = true)
            assertEquals(BackupProtocol.wrapNonceBytes, parsed.wrapNonce.size)

            val plaintext = BackupCrypto.decryptSnapshot(key, snapshot)
            BackupRecordStream.verify(plaintext, BackupProtocol.snapshotFormatId)
        } finally {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
    }
}
