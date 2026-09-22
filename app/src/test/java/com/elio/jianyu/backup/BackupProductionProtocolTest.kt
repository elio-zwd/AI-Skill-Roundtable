package com.elio.jianyu.backup

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupProductionProtocolTest {
    @Test
    fun portablePublicVectorsRemainByteCompatibleAtTheReaderBoundary() {
        listOf("portable-empty.json", "portable-unicode-record.json").forEach { name ->
            val vector = load(name)
            val file = vector.getJSONObject("ciphertext").getString("completeFileHex").hex()
            val plain = vector.getJSONObject("recordStream").getString("plaintextHex").hex()
            assertArrayEquals(plain, BackupCrypto.decryptPortable(vector.getString("passwordDisplay"), file))
            val stats = BackupRecordStream.verify(plain, vector.getString("formatId"))
            assertEquals(if (name.startsWith("portable-empty")) 1L else 2L, stats.recordCountBeforeComplete)
        }
    }

    @Test
    fun passwordNormalizationAndKdfMatchFrozenUnicodeVector() {
        val vector = load("portable-unicode-record.json")
        val salt = vector.getJSONObject("kdf").getString("saltHex").hex()
        assertArrayEquals(vector.getString("passwordUtf8Hex").hex(), BackupCrypto.normalizePasswordUtf8(vector.getString("passwordDisplay")))
        assertArrayEquals(vector.getJSONObject("kdf").getString("expectedKekHex").hex(), BackupCrypto.derivePortableKek(vector.getString("passwordDisplay"), salt))
    }

    @Test
    fun malformedEnvelopeAndWrongPasswordFailClosed() {
        val vector = load("portable-empty.json")
        val file = vector.getJSONObject("ciphertext").getString("completeFileHex").hex()
        val wrong = assertThrows(BackupException::class.java) { BackupCrypto.decryptPortable("wrong password", file) }
        assertEquals(BackupErrorCode.AUTHENTICATION_FAILED, wrong.code)
        val version = file.copyOf().apply { this[9] = 2 }
        val versionError = assertThrows(BackupException::class.java) { BackupCrypto.parseEnvelope(version, false) }
        assertEquals(BackupErrorCode.UNSUPPORTED_ENVELOPE_VERSION, versionError.code)
    }

    @Test
    fun recordBuilderProducesSelfVerifyingCanonicalStream() {
        val stream = BackupRecordStream.build(
            manifest = BackupManifest(
                formatId = BackupProtocol.portableFormatId,
                createdAt = 1L,
                appVersionName = "test",
                appVersionCode = 1L,
                sourceRoomVersion = 14L,
                logicalEntryCount = 1L,
                blobCount = 1L,
                backupScope = listOf("issue"),
            ),
            entities = listOf(
                BackupEntityRecord("issue-1", "issue", BackupCborValue.MapValue(mapOf(1L to BackupCborValue.Text("title")))),
            ),
            blobs = listOf(BackupBlobRecord("audio-1", "audio_asset", "audio/ogg", byteArrayOf(1, 2, 3))),
        )
        val stats = BackupRecordStream.verify(stream, BackupProtocol.portableFormatId)
        assertEquals(1L, stats.entityCount)
        assertEquals(1L, stats.blobCount)
    }

    private fun load(name: String): JSONObject {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val file = File(root, "docs/testing/vectors/pr-09-13a/$name")
        return JSONObject(file.readText())
    }

    private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
