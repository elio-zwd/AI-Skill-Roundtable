package com.elio.jianyu.backup.design

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPublicVectorTest {
    @Test
    fun portableEmptyVectorReproducesEveryCryptographicLayer() {
        verifyPortableVector("portable-empty.json")
    }

    @Test
    fun unicodePasswordIsNfcNormalizedAndReproducesVector() {
        val vector = BackupVectorTestSupport.load("portable-unicode-record.json")
        val expectedPasswordBytes = vector.getString("passwordUtf8Hex").hexToBytes()

        assertArrayEquals(
            expectedPasswordBytes,
            BackupCryptoPrototype.normalizePasswordUtf8(vector.getString("passwordDisplay")),
        )
        verifyPortableVector("portable-unicode-record.json")
    }

    @Test
    fun wrongPasswordFailsWithSingleAuthenticationCode() {
        val vector = BackupVectorTestSupport.load("portable-empty.json")
        val file = vector.getJSONObject("ciphertext").getString("completeFileHex").hexToBytes()

        runCatching {
            BackupCryptoPrototype.decryptPortableVectorFile("wrong password", file)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.AUTHENTICATION_FAILED)
    }

    @Test
    fun testOnlyMultiSegmentReferenceMatchesTinkDecryption() {
        val vector = BackupVectorTestSupport.load("streaming-multisegment.json")
        val parameters = vector.getJSONObject("parameters")
        val plaintextInfo = vector.getJSONObject("plaintext")
        val ciphertextInfo = vector.getJSONObject("ciphertext")
        val ikm = parameters.getString("ikmHex").hexToBytes()
        val associatedData = parameters.getString("associatedDataHex").hexToBytes()
        val streamSalt = parameters.getString("streamSaltHex").hexToBytes()
        val noncePrefix = parameters.getString("noncePrefixHex").hexToBytes()
        val segmentSize = parameters.getInt("ciphertextSegmentSize")
        val plaintext = ByteArray(plaintextInfo.getInt("length")) { index ->
            ((index * 31 + 7) and 0xff).toByte()
        }
        val expectedCiphertext = ciphertextInfo.getString("hex").hexToBytes()

        assertEquals(plaintextInfo.getString("sha256"), BackupCryptoPrototype.sha256(plaintext).toLowerHex())
        assertEquals(
            parameters.getString("expectedDerivedKeyHex"),
            BackupCryptoPrototype.hkdfSha256(
                ikm = ikm,
                salt = streamSalt,
                info = associatedData,
                outputLength = 32,
            ).toLowerHex(),
        )

        val generated = BackupCryptoPrototype.encryptStreamingReference(
            ikm = ikm,
            associatedData = associatedData,
            plaintext = plaintext,
            streamSalt = streamSalt,
            noncePrefix = noncePrefix,
            ciphertextSegmentSize = segmentSize,
        )
        assertArrayEquals(expectedCiphertext, generated)
        assertEquals(ciphertextInfo.getString("sha256"), BackupCryptoPrototype.sha256(generated).toLowerHex())

        val tink = AesGcmHkdfStreaming(
            ikm,
            "HmacSha256",
            32,
            segmentSize,
            0,
        )
        val tinkPlaintext = tink.newDecryptingStream(
            ByteArrayInputStream(expectedCiphertext),
            associatedData,
        ).use { it.readBytes() }
        assertArrayEquals(plaintext, tinkPlaintext)

        val referencePlaintext = BackupCryptoPrototype.decryptStreamingReference(
            ikm = ikm,
            associatedData = associatedData,
            ciphertext = expectedCiphertext,
            ciphertextSegmentSize = segmentSize,
        )
        assertArrayEquals(plaintext, referencePlaintext)
        assertEquals(3, ciphertextInfo.getInt("expectedSegments"))
    }

    private fun verifyPortableVector(name: String) {
        val vector = BackupVectorTestSupport.load(name)
        val kdf = vector.getJSONObject("kdf")
        val fixed = vector.getJSONObject("fixedRandom")
        val header = vector.getJSONObject("header")
        val hierarchy = vector.getJSONObject("keyHierarchy")
        val records = vector.getJSONObject("recordStream")
        val ciphertext = vector.getJSONObject("ciphertext")
        val password = vector.getString("passwordDisplay")
        val salt = kdf.getString("saltHex").hexToBytes()
        val envelopeId = fixed.getString("envelopeIdHex").hexToBytes()
        val rootKey = fixed.getString("rootKeyHex").hexToBytes()
        val wrapNonce = fixed.getString("wrapNonceHex").hexToBytes()
        val streamSalt = fixed.getString("streamSaltHex").hexToBytes()
        val noncePrefix = fixed.getString("noncePrefixHex").hexToBytes()
        val recordStream = records.getString("plaintextHex").hexToBytes()

        val canonicalHeader = BackupEnvelopePrototype.encodePortableHeader(salt, envelopeId)
        assertEquals(header.getString("canonicalCborHex"), canonicalHeader.toLowerHex())
        val wrapAad = BackupEnvelopePrototype.buildOuterPrefix(
            BackupDesignConstants.PORTABLE_MAGIC,
            canonicalHeader,
        )
        assertEquals(header.getString("wrapAadHex"), wrapAad.toLowerHex())

        val kek = BackupCryptoPrototype.derivePortableKek(password, salt)
        assertEquals(kdf.getString("expectedKekHex"), kek.toLowerHex())
        val wrappedRootKey = BackupCryptoPrototype.wrapRootKey(
            kek = kek,
            nonce = wrapNonce,
            rootKey = rootKey,
            aad = wrapAad,
        )
        assertEquals(
            hierarchy.getString("wrappedRootKeyCiphertextAndTagHex"),
            wrappedRootKey.toLowerHex(),
        )
        val streamingIkm = BackupCryptoPrototype.derivePortableStreamingIkm(rootKey, envelopeId)
        assertEquals(hierarchy.getString("streamingIkmHex"), streamingIkm.toLowerHex())
        val streamAad = BackupCryptoPrototype.streamAssociatedData(wrapAad, wrapNonce, wrappedRootKey)
        assertEquals(hierarchy.getString("streamAadHex"), streamAad.toLowerHex())

        val generatedFile = BackupCryptoPrototype.buildPortableVectorFile(
            password = password,
            salt = salt,
            envelopeId = envelopeId,
            rootKey = rootKey,
            wrapNonce = wrapNonce,
            streamSalt = streamSalt,
            noncePrefix = noncePrefix,
            recordStream = recordStream,
        )
        assertEquals(ciphertext.getString("completeFileHex"), generatedFile.toLowerHex())
        assertEquals(
            ciphertext.getString("completeFileSha256"),
            BackupCryptoPrototype.sha256(generatedFile).toLowerHex(),
        )

        val decrypted = BackupCryptoPrototype.decryptPortableVectorFile(password, generatedFile)
        assertArrayEquals(recordStream, decrypted)
        val verified = BackupRecordStreamPrototype.verify(decrypted)
        assertTrue(verified.recordCountBeforeComplete >= 1L)
        assertEquals(
            records.getString("plaintextSha256"),
            BackupCryptoPrototype.sha256(recordStream).toLowerHex(),
        )
        kek.fill(0)
    }
}
