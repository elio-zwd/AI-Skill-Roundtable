package com.elio.jianyu.backup.design

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BackupEnvelopePrototypeTest {
    @Test
    fun publicHeaderUsesExactCanonicalBytes() {
        val vector = BackupVectorTestSupport.load("portable-empty.json")
        val kdf = vector.getJSONObject("kdf")
        val fixed = vector.getJSONObject("fixedRandom")
        val expected = vector.getJSONObject("header").getString("canonicalCborHex").hexToBytes()

        val actual = BackupEnvelopePrototype.encodePortableHeader(
            salt = kdf.getString("saltHex").hexToBytes(),
            envelopeId = fixed.getString("envelopeIdHex").hexToBytes(),
        )

        assertArrayEquals(expected, actual)
    }

    @Test
    fun nonCanonicalIntegerEncodingIsRejected() {
        val nonCanonical = "a1011801".hexToBytes()

        runCatching {
            CanonicalCborPrototype.decodeCanonical(nonCanonical)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.INVALID_HEADER)
    }

    @Test
    fun unsupportedEnvelopeVersionIsRejectedBeforeKdf() {
        val file = publicEmptyFile().copyOf()
        file[8] = 0
        file[9] = 2

        runCatching {
            BackupEnvelopePrototype.parsePortable(file)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.UNSUPPORTED_ENVELOPE_VERSION)
    }

    @Test
    fun oversizedHeaderIsRejectedBeforeAllocation() {
        val file = publicEmptyFile().copyOf()
        file[12] = 0
        file[13] = 0
        file[14] = 0x10
        file[15] = 0x01

        runCatching {
            BackupEnvelopePrototype.parsePortable(file)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.INVALID_HEADER)
    }

    @Test
    fun authenticatedHeaderSaltMutationFailsAsAuthenticationError() {
        val vector = BackupVectorTestSupport.load("portable-empty.json")
        val file = publicEmptyFile().copyOf()
        val headerStart = 16
        val saltStartInsideHeader = 9
        file[headerStart + saltStartInsideHeader] =
            (file[headerStart + saltStartInsideHeader].toInt() xor 1).toByte()

        val parsed = BackupEnvelopePrototype.parsePortable(file)
        assertNotEquals(
            vector.getJSONObject("kdf").getString("saltHex"),
            parsed.header.kdfSalt!!.toLowerHex(),
        )
        runCatching {
            BackupCryptoPrototype.decryptPortableVectorFile(
                vector.getString("passwordDisplay"),
                file,
            )
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.AUTHENTICATION_FAILED)
    }

    @Test
    fun unknownKdfProfileIsRejectedBeforeArgonAllocation() {
        val header = portableHeader(profileId = 2L)
        val file = dummyEnvelope(header)

        runCatching {
            BackupEnvelopePrototype.parsePortable(file)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
    }

    @Test
    fun unknownHeaderFieldIsRejected() {
        val base = portableHeaderValues().toMutableMap()
        base[10L] = PrototypeCborValue.Unsigned(1L)
        val file = dummyEnvelope(
            CanonicalCborPrototype.encode(PrototypeCborValue.MapValue(base)),
        )

        runCatching {
            BackupEnvelopePrototype.parsePortable(file)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.INVALID_HEADER)
    }

    @Test
    fun requiredFeatureBitFailsClosed() {
        val base = portableHeaderValues().toMutableMap()
        base[9L] = PrototypeCborValue.Unsigned(1L)
        val file = dummyEnvelope(
            CanonicalCborPrototype.encode(PrototypeCborValue.MapValue(base)),
        )

        runCatching {
            BackupEnvelopePrototype.parsePortable(file)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
    }

    @Test
    fun portableAndSnapshotUseDifferentMagicAndDeviceKeyDoesNotReuseApiKeyAlias() {
        assertNotEquals(
            BackupDesignConstants.PORTABLE_MAGIC.toLowerHex(),
            BackupDesignConstants.SNAPSHOT_MAGIC.toLowerHex(),
        )
        assertNotEquals(
            BackupDesignConstants.SNAPSHOT_KEY_ALIAS,
            BackupDesignConstants.API_KEY_ALIAS_MUST_NOT_BE_REUSED,
        )
        val envelopeId = ByteArray(16) { it.toByte() }
        val snapshotHeader = BackupEnvelopePrototype.encodeSnapshotHeader(envelopeId)
        val snapshot = BackupEnvelopePrototype.parseSnapshot(dummyEnvelope(snapshotHeader, snapshot = true))
        assertEquals(BackupDesignConstants.FORMAT_KIND_SNAPSHOT, snapshot.header.formatKind)
        assertEquals(BackupDesignConstants.SNAPSHOT_DEVICE_KEY_SLOT_V1, snapshot.header.deviceKeySlot)
    }

    private fun publicEmptyFile(): ByteArray = BackupVectorTestSupport.load("portable-empty.json")
        .getJSONObject("ciphertext")
        .getString("completeFileHex")
        .hexToBytes()

    private fun portableHeader(profileId: Long): ByteArray {
        val values = portableHeaderValues().toMutableMap()
        values[3L] = PrototypeCborValue.Unsigned(profileId)
        return CanonicalCborPrototype.encode(PrototypeCborValue.MapValue(values))
    }

    private fun portableHeaderValues(): Map<Long, PrototypeCborValue> = linkedMapOf(
        1L to PrototypeCborValue.Unsigned(BackupDesignConstants.FORMAT_KIND_PORTABLE),
        2L to PrototypeCborValue.Unsigned(BackupDesignConstants.KDF_ARGON2ID),
        3L to PrototypeCborValue.Unsigned(BackupDesignConstants.KDF_PROFILE_ARGON2ID_V1),
        4L to PrototypeCborValue.Bytes(ByteArray(16) { it.toByte() }),
        5L to PrototypeCborValue.Unsigned(BackupDesignConstants.KEY_WRAP_AES_256_GCM),
        6L to PrototypeCborValue.Unsigned(BackupDesignConstants.STREAMING_AES_256_GCM_HKDF_1MB),
        7L to PrototypeCborValue.Unsigned(BackupDesignConstants.SERIALIZATION_DETERMINISTIC_CBOR_V1),
        8L to PrototypeCborValue.Bytes(ByteArray(16) { (it + 16).toByte() }),
        9L to PrototypeCborValue.Unsigned(0L),
    )

    private fun dummyEnvelope(
        header: ByteArray,
        snapshot: Boolean = false,
    ): ByteArray {
        val magic = if (snapshot) {
            BackupDesignConstants.SNAPSHOT_MAGIC
        } else {
            BackupDesignConstants.PORTABLE_MAGIC
        }
        return BackupEnvelopePrototype.buildOuterPrefix(magic, header) +
            ByteArray(BackupDesignConstants.WRAP_NONCE_BYTES) +
            ByteArray(BackupDesignConstants.ROOT_KEY_BYTES + BackupDesignConstants.GCM_TAG_BYTES) +
            byteArrayOf(BackupDesignConstants.STREAM_HEADER_BYTES.toByte()) +
            ByteArray(BackupDesignConstants.STREAM_HEADER_BYTES - 1) +
            ByteArray(BackupDesignConstants.GCM_TAG_BYTES)
    }
}
