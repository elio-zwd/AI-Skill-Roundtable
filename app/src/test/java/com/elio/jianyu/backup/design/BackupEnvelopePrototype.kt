package com.elio.jianyu.backup.design

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PR09-13A design prototype for strict Envelope parsing.
 *
 * Not a production API and not used by the app runtime.
 */
internal data class PrototypeBackupHeader(
    val formatKind: Long,
    val kdfId: Long,
    val kdfProfileId: Long,
    val kdfSalt: ByteArray?,
    val keyWrapAlgorithmId: Long,
    val streamingAlgorithmId: Long,
    val serializationId: Long,
    val envelopeId: ByteArray,
    val requiredFeatureBits: Long,
    val deviceKeySlot: Long?,
)

internal data class PrototypeParsedEnvelope(
    val magic: ByteArray,
    val header: PrototypeBackupHeader,
    val canonicalHeader: ByteArray,
    val wrapAad: ByteArray,
    val wrapNonce: ByteArray,
    val wrappedRootKey: ByteArray,
    val streamingCiphertext: ByteArray,
)

internal object BackupEnvelopePrototype {
    fun encodePortableHeader(
        salt: ByteArray,
        envelopeId: ByteArray,
    ): ByteArray {
        require(salt.size == BackupDesignConstants.KDF_SALT_BYTES)
        require(envelopeId.size == BackupDesignConstants.ENVELOPE_ID_BYTES)
        return CanonicalCborPrototype.encode(
            PrototypeCborValue.MapValue(
                linkedMapOf(
                    1L to PrototypeCborValue.Unsigned(BackupDesignConstants.FORMAT_KIND_PORTABLE),
                    2L to PrototypeCborValue.Unsigned(BackupDesignConstants.KDF_ARGON2ID),
                    3L to PrototypeCborValue.Unsigned(BackupDesignConstants.KDF_PROFILE_ARGON2ID_V1),
                    4L to PrototypeCborValue.Bytes(salt.copyOf()),
                    5L to PrototypeCborValue.Unsigned(BackupDesignConstants.KEY_WRAP_AES_256_GCM),
                    6L to PrototypeCborValue.Unsigned(BackupDesignConstants.STREAMING_AES_256_GCM_HKDF_1MB),
                    7L to PrototypeCborValue.Unsigned(BackupDesignConstants.SERIALIZATION_DETERMINISTIC_CBOR_V1),
                    8L to PrototypeCborValue.Bytes(envelopeId.copyOf()),
                    9L to PrototypeCborValue.Unsigned(0L),
                ),
            ),
        )
    }

    fun encodeSnapshotHeader(envelopeId: ByteArray): ByteArray {
        require(envelopeId.size == BackupDesignConstants.ENVELOPE_ID_BYTES)
        return CanonicalCborPrototype.encode(
            PrototypeCborValue.MapValue(
                linkedMapOf(
                    1L to PrototypeCborValue.Unsigned(BackupDesignConstants.FORMAT_KIND_SNAPSHOT),
                    2L to PrototypeCborValue.Unsigned(BackupDesignConstants.KDF_NONE),
                    3L to PrototypeCborValue.Unsigned(BackupDesignConstants.KDF_PROFILE_NONE),
                    5L to PrototypeCborValue.Unsigned(BackupDesignConstants.KEY_WRAP_AES_256_GCM),
                    6L to PrototypeCborValue.Unsigned(BackupDesignConstants.STREAMING_AES_256_GCM_HKDF_1MB),
                    7L to PrototypeCborValue.Unsigned(BackupDesignConstants.SERIALIZATION_DETERMINISTIC_CBOR_V1),
                    8L to PrototypeCborValue.Bytes(envelopeId.copyOf()),
                    9L to PrototypeCborValue.Unsigned(0L),
                    10L to PrototypeCborValue.Unsigned(BackupDesignConstants.SNAPSHOT_DEVICE_KEY_SLOT_V1),
                ),
            ),
        )
    }

    fun buildOuterPrefix(
        magic: ByteArray,
        canonicalHeader: ByteArray,
    ): ByteArray {
        require(magic.size == 8)
        require(canonicalHeader.isNotEmpty())
        require(canonicalHeader.size <= BackupDesignConstants.MAX_HEADER_BYTES)
        return ByteArrayOutputStream().apply {
            write(magic)
            writeU16(BackupDesignConstants.ENVELOPE_VERSION)
            writeU16(BackupDesignConstants.HEADER_ENCODING_DETERMINISTIC_CBOR)
            writeU32(canonicalHeader.size)
            write(canonicalHeader)
        }.toByteArray()
    }

    fun parsePortable(file: ByteArray): PrototypeParsedEnvelope = parse(
        file = file,
        expectedMagic = BackupDesignConstants.PORTABLE_MAGIC,
        expectedFormatKind = BackupDesignConstants.FORMAT_KIND_PORTABLE,
    )

    fun parseSnapshot(file: ByteArray): PrototypeParsedEnvelope = parse(
        file = file,
        expectedMagic = BackupDesignConstants.SNAPSHOT_MAGIC,
        expectedFormatKind = BackupDesignConstants.FORMAT_KIND_SNAPSHOT,
    )

    private fun parse(
        file: ByteArray,
        expectedMagic: ByteArray,
        expectedFormatKind: Long,
    ): PrototypeParsedEnvelope {
        val minimumLength = 8 + 2 + 2 + 4 + 1 +
            BackupDesignConstants.WRAP_NONCE_BYTES +
            BackupDesignConstants.ROOT_KEY_BYTES +
            BackupDesignConstants.GCM_TAG_BYTES +
            BackupDesignConstants.STREAM_HEADER_BYTES +
            BackupDesignConstants.GCM_TAG_BYTES
        if (file.size < minimumLength) {
            throw BackupDesignException(BackupDesignErrorCode.TRUNCATED_PAYLOAD)
        }

        val input = ByteBuffer.wrap(file).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(8).also(input::get)
        if (!magic.contentEquals(expectedMagic)) {
            throw BackupDesignException(BackupDesignErrorCode.INVALID_MAGIC)
        }

        val envelopeVersion = input.short.toInt() and 0xffff
        if (envelopeVersion != BackupDesignConstants.ENVELOPE_VERSION) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_ENVELOPE_VERSION)
        }

        val headerEncoding = input.short.toInt() and 0xffff
        if (headerEncoding != BackupDesignConstants.HEADER_ENCODING_DETERMINISTIC_CBOR) {
            throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        }

        val headerLength = input.int
        if (headerLength !in 1..BackupDesignConstants.MAX_HEADER_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        }
        if (input.remaining() < headerLength + BackupDesignConstants.WRAP_NONCE_BYTES +
            BackupDesignConstants.ROOT_KEY_BYTES + BackupDesignConstants.GCM_TAG_BYTES +
            BackupDesignConstants.STREAM_HEADER_BYTES + BackupDesignConstants.GCM_TAG_BYTES
        ) {
            throw BackupDesignException(BackupDesignErrorCode.TRUNCATED_PAYLOAD)
        }

        val canonicalHeader = ByteArray(headerLength).also(input::get)
        val header = decodeHeader(canonicalHeader, expectedFormatKind)
        val wrapAad = buildOuterPrefix(expectedMagic, canonicalHeader)
        val wrapNonce = ByteArray(BackupDesignConstants.WRAP_NONCE_BYTES).also(input::get)
        val wrappedRootKey = ByteArray(
            BackupDesignConstants.ROOT_KEY_BYTES + BackupDesignConstants.GCM_TAG_BYTES,
        ).also(input::get)
        val streamingCiphertext = ByteArray(input.remaining()).also(input::get)

        return PrototypeParsedEnvelope(
            magic = magic,
            header = header,
            canonicalHeader = canonicalHeader,
            wrapAad = wrapAad,
            wrapNonce = wrapNonce,
            wrappedRootKey = wrappedRootKey,
            streamingCiphertext = streamingCiphertext,
        )
    }

    private fun decodeHeader(
        canonicalHeader: ByteArray,
        expectedFormatKind: Long,
    ): PrototypeBackupHeader {
        val map = CanonicalCborPrototype.decodeCanonical(canonicalHeader) as? PrototypeCborValue.MapValue
            ?: throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        val formatKind = map.requireUnsigned(1L)
        if (formatKind != expectedFormatKind) {
            throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        }

        val allowedKeys = if (formatKind == BackupDesignConstants.FORMAT_KIND_PORTABLE) {
            setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)
        } else {
            setOf(1L, 2L, 3L, 5L, 6L, 7L, 8L, 9L, 10L)
        }
        if (map.values.keys != allowedKeys) {
            throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        }

        val kdfId = map.requireUnsigned(2L)
        val kdfProfileId = map.requireUnsigned(3L)
        val keyWrap = map.requireUnsigned(5L)
        val streaming = map.requireUnsigned(6L)
        val serialization = map.requireUnsigned(7L)
        val envelopeId = map.requireBytes(8L)
        val requiredFeatures = map.requireUnsigned(9L)

        if (envelopeId.size != BackupDesignConstants.ENVELOPE_ID_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        }
        if (keyWrap != BackupDesignConstants.KEY_WRAP_AES_256_GCM) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_AEAD)
        }
        if (streaming != BackupDesignConstants.STREAMING_AES_256_GCM_HKDF_1MB) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_AEAD)
        }
        if (serialization != BackupDesignConstants.SERIALIZATION_DETERMINISTIC_CBOR_V1) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
        }
        if (requiredFeatures != 0L) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
        }

        return if (formatKind == BackupDesignConstants.FORMAT_KIND_PORTABLE) {
            val salt = map.requireBytes(4L)
            if (kdfId != BackupDesignConstants.KDF_ARGON2ID) {
                throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_KDF)
            }
            if (kdfProfileId != BackupDesignConstants.KDF_PROFILE_ARGON2ID_V1) {
                throw BackupDesignException(BackupDesignErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
            }
            if (salt.size != BackupDesignConstants.KDF_SALT_BYTES) {
                throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
            }
            PrototypeBackupHeader(
                formatKind = formatKind,
                kdfId = kdfId,
                kdfProfileId = kdfProfileId,
                kdfSalt = salt,
                keyWrapAlgorithmId = keyWrap,
                streamingAlgorithmId = streaming,
                serializationId = serialization,
                envelopeId = envelopeId,
                requiredFeatureBits = requiredFeatures,
                deviceKeySlot = null,
            )
        } else {
            val deviceKeySlot = map.requireUnsigned(10L)
            if (kdfId != BackupDesignConstants.KDF_NONE || kdfProfileId != BackupDesignConstants.KDF_PROFILE_NONE) {
                throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
            }
            if (deviceKeySlot != BackupDesignConstants.SNAPSHOT_DEVICE_KEY_SLOT_V1) {
                throw BackupDesignException(BackupDesignErrorCode.SNAPSHOT_KEY_UNAVAILABLE)
            }
            PrototypeBackupHeader(
                formatKind = formatKind,
                kdfId = kdfId,
                kdfProfileId = kdfProfileId,
                kdfSalt = null,
                keyWrapAlgorithmId = keyWrap,
                streamingAlgorithmId = streaming,
                serializationId = serialization,
                envelopeId = envelopeId,
                requiredFeatureBits = requiredFeatures,
                deviceKeySlot = deviceKeySlot,
            )
        }
    }

    private fun ByteArrayOutputStream.writeU16(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array())
    }

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
    }
}
