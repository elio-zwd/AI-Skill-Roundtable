package com.elio.skillroundtable.network

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class EncryptedApiKeyStoreTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val encryptedFile: File
        get() = File(context.noBackupFilesDir, ENCRYPTED_FILE_NAME)

    @Before
    fun setUp() {
        deleteEncryptedState()
    }

    @After
    fun tearDown() {
        deleteEncryptedState()
    }

    @Test
    fun writeThenRead_returnsOriginalRecords() {
        val store = EncryptedApiKeyStore(context)
        val records = testRecords()

        assertTrue(store.write(records))
        assertEquals(records, store.read())
        assertEquals(null, store.lastError)
    }

    @Test
    fun encryptedFile_isStoredInNoBackupDirectoryWithoutPlaintextKeys() {
        val store = EncryptedApiKeyStore(context)
        val records = testRecords()

        assertTrue(store.write(records))
        assertTrue(encryptedFile.isFile)
        assertEquals(
            context.noBackupFilesDir.canonicalFile,
            encryptedFile.parentFile?.canonicalFile,
        )

        val persistedText = encryptedFile.readText(Charsets.UTF_8)
        records.forEach { record ->
            assertFalse("Persisted file must not contain an API key in plaintext", persistedText.contains(record.key))
            assertFalse("Persisted file must not contain the display name in plaintext", persistedText.contains(record.displayName))
        }
    }

    @Test
    fun clear_removesEncryptedFileAndReturnsEmptyRecords() {
        val store = EncryptedApiKeyStore(context)
        assertTrue(store.write(testRecords()))
        assertTrue(encryptedFile.exists())

        assertTrue(store.clear())
        assertFalse(encryptedFile.exists())
        assertTrue(store.read().isEmpty())
        assertEquals(null, store.lastError)
    }

    @Test
    fun corruptedCiphertext_failsSafelyWithoutReturningRecords() {
        val store = EncryptedApiKeyStore(context)
        assertTrue(store.write(testRecords()))

        encryptedFile.writeText(
            """{"version":1,"iv":"AA==","ciphertext":"AA=="}""",
            Charsets.UTF_8,
        )

        assertTrue(store.read().isEmpty())
        assertNotNull(store.lastError)
        assertTrue(encryptedFile.exists())
    }

    private fun testRecords(): List<ApiKeyRecord> = listOf(
        ApiKeyRecord(
            id = "key-1",
            displayName = "Primary test key",
            key = "test-api-key-value-that-must-never-appear-in-plaintext-1",
            fingerprint = fingerprintApiKey("test-api-key-value-that-must-never-appear-in-plaintext-1"),
            source = ApiKeySource.LOCAL,
            enabled = true,
            validationState = ApiKeyValidationState.AVAILABLE,
            validationMessage = "validated",
            createdAt = 1_700_000_000_000L,
            lastValidatedAt = 1_700_000_001_000L,
        ),
        ApiKeyRecord(
            id = "key-2",
            displayName = "Disabled test key",
            key = "test-api-key-value-that-must-never-appear-in-plaintext-2",
            fingerprint = fingerprintApiKey("test-api-key-value-that-must-never-appear-in-plaintext-2"),
            source = ApiKeySource.REMOTE,
            enabled = false,
            validationState = ApiKeyValidationState.RATE_LIMITED,
            validationMessage = null,
            createdAt = 1_700_000_002_000L,
            lastValidatedAt = null,
        ),
    )

    private fun deleteEncryptedState() {
        File(context.noBackupFilesDir, ENCRYPTED_FILE_NAME).delete()
        File(context.noBackupFilesDir, "$ENCRYPTED_FILE_NAME.bak").delete()
        File(context.noBackupFilesDir, "$ENCRYPTED_FILE_NAME.new").delete()

        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            if (containsAlias(KEY_ALIAS)) {
                deleteEntry(KEY_ALIAS)
            }
        }
    }

    companion object {
        private const val KEY_ALIAS = "skill_roundtable_api_key_v1"
        private const val ENCRYPTED_FILE_NAME = "gemini_api_keys.enc"
    }
}
