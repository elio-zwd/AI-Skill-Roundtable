package com.elio.jianyu.backup

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKey
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

data class ParsedBackupEnvelope(
    val isSnapshot: Boolean,
    val canonicalHeader: ByteArray,
    val formatKind: Long,
    val envelopeId: ByteArray,
    val kdfSalt: ByteArray?,
    val deviceKeySlot: Long?,
    val wrapAad: ByteArray,
    val wrapNonce: ByteArray,
    val wrappedRootKeyCiphertext: ByteArray,
    val wrappedRootKeyTag: ByteArray,
    val streamingCiphertext: ByteArray,
)

object BackupCrypto {
    fun normalizePasswordUtf8(password: String): ByteArray {
        if (password.isEmpty() || password.indexOf('\u0000') >= 0) {
            throw BackupException(BackupErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
        }
        validateSurrogates(password)
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFC)
        val encoded = normalized.toByteArray(Charsets.UTF_8)
        if (encoded.isEmpty() || encoded.size > BackupProtocol.maxPasswordUtf8Bytes) {
            throw BackupException(BackupErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
        }
        return encoded
    }

    fun derivePortableKek(password: String, salt: ByteArray): ByteArray {
        if (salt.size != BackupProtocol.kdfSaltBytes) throw BackupException(BackupErrorCode.INVALID_HEADER)
        val passwordBytes = normalizePasswordUtf8(password)
        return try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(BackupProtocol.argon2Version13)
                .withMemoryAsKB(BackupProtocol.argon2MemoryKiB)
                .withIterations(BackupProtocol.argon2Iterations)
                .withParallelism(BackupProtocol.argon2Parallelism)
                .withSalt(salt.copyOf())
                .build()
            val generator = Argon2BytesGenerator().apply { init(parameters) }
            ByteArray(BackupProtocol.rootKeyBytes).also { generator.generateBytes(passwordBytes, it) }
        } catch (error: OutOfMemoryError) {
            throw BackupException(BackupErrorCode.KDF_RESOURCE_UNAVAILABLE, error)
        } catch (error: BackupException) {
            throw error
        } catch (error: Throwable) {
            throw BackupException(BackupErrorCode.KDF_RESOURCE_UNAVAILABLE, error)
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun wrapRootKey(kek: ByteArray, nonce: ByteArray, rootKey: ByteArray, aad: ByteArray): ByteArray {
        require(nonce.size == BackupProtocol.wrapNonceBytes)
        require(rootKey.size == BackupProtocol.rootKeyBytes)
        return aesGcm(Cipher.ENCRYPT_MODE, kek, nonce, rootKey, aad)
    }

    fun wrapRootKey(kek: SecretKey, nonce: ByteArray, rootKey: ByteArray, aad: ByteArray): ByteArray =
        aesGcm(Cipher.ENCRYPT_MODE, kek, nonce, rootKey, aad)

    fun unwrapRootKey(kek: ByteArray, nonce: ByteArray, wrapped: ByteArray, aad: ByteArray): ByteArray {
        return try {
            aesGcm(Cipher.DECRYPT_MODE, kek, nonce, wrapped, aad).also {
                if (it.size != BackupProtocol.rootKeyBytes) throw BackupException(BackupErrorCode.AUTHENTICATION_FAILED)
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: Throwable) {
            throw BackupException(BackupErrorCode.AUTHENTICATION_FAILED, error)
        }
    }

    fun unwrapRootKey(kek: SecretKey, nonce: ByteArray, wrapped: ByteArray, aad: ByteArray): ByteArray {
        return try {
            aesGcm(Cipher.DECRYPT_MODE, kek, nonce, wrapped, aad).also {
                if (it.size != BackupProtocol.rootKeyBytes) throw BackupException(BackupErrorCode.AUTHENTICATION_FAILED)
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: Throwable) {
            throw BackupException(BackupErrorCode.AUTHENTICATION_FAILED, error)
        }
    }

    fun deriveStreamingIkm(rootKey: ByteArray, envelopeId: ByteArray, snapshot: Boolean): ByteArray {
        if (rootKey.size != BackupProtocol.rootKeyBytes || envelopeId.size != BackupProtocol.envelopeIdBytes) {
            throw BackupException(BackupErrorCode.INVALID_HEADER)
        }
        return hkdfSha256(
            ikm = rootKey,
            salt = envelopeId,
            info = (if (snapshot) BackupProtocol.snapshotStreamInfo else BackupProtocol.portableStreamInfo)
                .toByteArray(Charsets.US_ASCII),
            outputLength = BackupProtocol.streamingIkmBytes,
        )
    }

    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        require(outputLength in 1..(255 * 32))
        val prk = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(salt, "HmacSHA256")); doFinal(ikm)
        }
        val output = ByteArrayOutputStream(outputLength)
        var previous = ByteArray(0)
        var counter = 1
        try {
            while (output.size() < outputLength) {
                previous = Mac.getInstance("HmacSHA256").run {
                    init(SecretKeySpec(prk, "HmacSHA256"))
                    update(previous); update(info); update(counter.toByte()); doFinal()
                }
                output.write(previous)
                counter += 1
            }
            return output.toByteArray().copyOf(outputLength)
        } finally {
            prk.fill(0); previous.fill(0)
        }
    }

    fun streamAssociatedData(wrapAad: ByteArray, wrapNonce: ByteArray, wrappedRootKeyAndTag: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(wrapAad + wrapNonce + wrappedRootKeyAndTag)

    fun encryptStreaming(ikm: ByteArray, associatedData: ByteArray, plaintext: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        try {
            val streaming = AesGcmHkdfStreaming(
                ikm,
                "HmacSha256",
                BackupProtocol.rootKeyBytes,
                BackupProtocol.streamingSegmentBytes,
                0,
            )
            streaming.newEncryptingStream(output, associatedData).use { it.write(plaintext) }
            return output.toByteArray()
        } catch (error: BackupException) {
            throw error
        } catch (error: Throwable) {
            throw BackupException(BackupErrorCode.UNSUPPORTED_AEAD, error)
        } finally {
            ikm.fill(0)
        }
    }

    fun decryptStreaming(ikm: ByteArray, associatedData: ByteArray, ciphertext: ByteArray): ByteArray {
        try {
            val streaming = AesGcmHkdfStreaming(
                ikm,
                "HmacSha256",
                BackupProtocol.rootKeyBytes,
                BackupProtocol.streamingSegmentBytes,
                0,
            )
            return streaming.newDecryptingStream(ByteArrayInputStream(ciphertext), associatedData).use { it.readBytes() }
        } catch (error: BackupException) {
            throw error
        } catch (error: Throwable) {
            throw BackupException(BackupErrorCode.AUTHENTICATION_FAILED, error)
        } finally {
            ikm.fill(0)
        }
    }

    fun encodeHeader(isSnapshot: Boolean, salt: ByteArray?, envelopeId: ByteArray): ByteArray {
        if (envelopeId.size != BackupProtocol.envelopeIdBytes) throw BackupException(BackupErrorCode.INVALID_HEADER)
        val values = linkedMapOf<Long, BackupCborValue>(
            1L to BackupCborValue.UInt(if (isSnapshot) BackupProtocol.snapshotFormatKind else BackupProtocol.portableFormatKind),
            2L to BackupCborValue.UInt(if (isSnapshot) BackupProtocol.kdfNone else BackupProtocol.kdfArgon2id),
            3L to BackupCborValue.UInt(if (isSnapshot) BackupProtocol.kdfProfileNone else BackupProtocol.kdfProfileArgon2idV1),
            5L to BackupCborValue.UInt(BackupProtocol.keyWrapAes256Gcm),
            6L to BackupCborValue.UInt(BackupProtocol.streamingAes256GcmHkdf1Mb),
            7L to BackupCborValue.UInt(BackupProtocol.serializationDeterministicCborV1),
            8L to BackupCborValue.Bytes(envelopeId.copyOf()),
            9L to BackupCborValue.UInt(0L),
        )
        if (isSnapshot) {
            values[10L] = BackupCborValue.UInt(BackupProtocol.snapshotDeviceKeySlotV1)
        } else {
            if (salt == null || salt.size != BackupProtocol.kdfSaltBytes) throw BackupException(BackupErrorCode.INVALID_HEADER)
            values[4L] = BackupCborValue.Bytes(salt.copyOf())
        }
        return BackupCanonicalCbor.encode(BackupCborValue.MapValue(values))
    }

    fun buildWrapAad(magic: ByteArray, canonicalHeader: ByteArray): ByteArray {
        if (magic.size != 8 || canonicalHeader.isEmpty() || canonicalHeader.size > BackupProtocol.maxHeaderBytes) {
            throw BackupException(BackupErrorCode.INVALID_HEADER)
        }
        return ByteArrayOutputStream(8 + 8 + canonicalHeader.size).apply {
            write(magic)
            writeU16(BackupProtocol.envelopeVersion)
            writeU16(BackupProtocol.headerEncodingDeterministicCbor)
            writeU32(canonicalHeader.size)
            write(canonicalHeader)
        }.toByteArray()
    }

    fun buildEnvelope(
        magic: ByteArray,
        canonicalHeader: ByteArray,
        wrapNonce: ByteArray,
        wrappedRootKeyAndTag: ByteArray,
        streamingCiphertext: ByteArray,
    ): ByteArray {
        if (wrapNonce.size != BackupProtocol.wrapNonceBytes ||
            wrappedRootKeyAndTag.size != BackupProtocol.rootKeyBytes + BackupProtocol.gcmTagBytes
        ) throw BackupException(BackupErrorCode.INVALID_HEADER)
        return ByteArrayOutputStream().apply {
            write(buildWrapAad(magic, canonicalHeader))
            write(wrapNonce)
            write(wrappedRootKeyAndTag)
            write(streamingCiphertext)
        }.toByteArray()
    }

    fun parseEnvelope(file: ByteArray, snapshot: Boolean): ParsedBackupEnvelope {
        val magic = if (snapshot) BackupProtocol.snapshotMagic else BackupProtocol.portableMagic
        val minimum = 8 + 2 + 2 + 4 + 1 + BackupProtocol.wrapNonceBytes +
            BackupProtocol.rootKeyBytes + BackupProtocol.gcmTagBytes + BackupProtocol.streamingHeaderBytes +
            BackupProtocol.gcmTagBytes
        if (file.size < minimum) throw BackupException(BackupErrorCode.TRUNCATED_PAYLOAD)
        val input = ByteBuffer.wrap(file).order(ByteOrder.BIG_ENDIAN)
        val actualMagic = ByteArray(8).also(input::get)
        if (!actualMagic.contentEquals(magic)) throw BackupException(BackupErrorCode.INVALID_MAGIC)
        if ((input.short.toInt() and 0xffff) != BackupProtocol.envelopeVersion) {
            throw BackupException(BackupErrorCode.UNSUPPORTED_ENVELOPE_VERSION)
        }
        if ((input.short.toInt() and 0xffff) != BackupProtocol.headerEncodingDeterministicCbor) {
            throw BackupException(BackupErrorCode.INVALID_HEADER)
        }
        val headerLength = input.int
        if (headerLength !in 1..BackupProtocol.maxHeaderBytes) throw BackupException(BackupErrorCode.INVALID_HEADER)
        val remainingRequired = headerLength + BackupProtocol.wrapNonceBytes + BackupProtocol.rootKeyBytes +
            BackupProtocol.gcmTagBytes + BackupProtocol.streamingHeaderBytes + BackupProtocol.gcmTagBytes
        if (input.remaining() < remainingRequired) throw BackupException(BackupErrorCode.TRUNCATED_PAYLOAD)
        val header = ByteArray(headerLength).also(input::get)
        val decoded = BackupCanonicalCbor.decodeCanonical(header)
        val map = decoded as? BackupCborValue.MapValue ?: throw BackupException(BackupErrorCode.INVALID_HEADER)
        val expectedKeys = if (snapshot) setOf(1L, 2L, 3L, 5L, 6L, 7L, 8L, 9L, 10L) else setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)
        if (map.values.keys != expectedKeys) throw BackupException(BackupErrorCode.INVALID_HEADER)
        val formatKind = map.requiredUInt(1L, BackupErrorCode.INVALID_HEADER)
        if (formatKind != if (snapshot) BackupProtocol.snapshotFormatKind else BackupProtocol.portableFormatKind) {
            throw BackupException(BackupErrorCode.INVALID_HEADER)
        }
        val kdf = map.requiredUInt(2L, BackupErrorCode.INVALID_HEADER)
        val profile = map.requiredUInt(3L, BackupErrorCode.INVALID_HEADER)
        if (snapshot) {
            if (kdf != BackupProtocol.kdfNone || profile != BackupProtocol.kdfProfileNone) throw BackupException(BackupErrorCode.INVALID_HEADER)
        } else {
            if (kdf != BackupProtocol.kdfArgon2id) throw BackupException(BackupErrorCode.UNSUPPORTED_KDF)
            if (profile != BackupProtocol.kdfProfileArgon2idV1) throw BackupException(BackupErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
            if (map.requiredBytes(4L, BackupErrorCode.INVALID_HEADER).size != BackupProtocol.kdfSaltBytes) throw BackupException(BackupErrorCode.INVALID_HEADER)
        }
        if (map.requiredUInt(5L, BackupErrorCode.INVALID_HEADER) != BackupProtocol.keyWrapAes256Gcm ||
            map.requiredUInt(6L, BackupErrorCode.INVALID_HEADER) != BackupProtocol.streamingAes256GcmHkdf1Mb
        ) throw BackupException(BackupErrorCode.UNSUPPORTED_AEAD)
        if (map.requiredUInt(7L, BackupErrorCode.INVALID_HEADER) != BackupProtocol.serializationDeterministicCborV1 ||
            map.requiredUInt(9L, BackupErrorCode.INVALID_HEADER) != 0L
        ) throw BackupException(BackupErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
        val envelopeId = map.requiredBytes(8L, BackupErrorCode.INVALID_HEADER)
        if (envelopeId.size != BackupProtocol.envelopeIdBytes) throw BackupException(BackupErrorCode.INVALID_HEADER)
        val deviceKeySlot = if (snapshot) {
            map.requiredUInt(10L, BackupErrorCode.INVALID_HEADER).also {
                if (it != BackupProtocol.snapshotDeviceKeySlotV1) throw BackupException(BackupErrorCode.SNAPSHOT_KEY_UNAVAILABLE)
            }
        } else null
        val wrapAad = buildWrapAad(magic, header)
        val wrapNonce = ByteArray(BackupProtocol.wrapNonceBytes).also(input::get)
        val wrapped = ByteArray(BackupProtocol.rootKeyBytes + BackupProtocol.gcmTagBytes).also(input::get)
        return ParsedBackupEnvelope(
            isSnapshot = snapshot,
            canonicalHeader = header,
            formatKind = formatKind,
            envelopeId = envelopeId,
            kdfSalt = if (snapshot) null else map.requiredBytes(4L, BackupErrorCode.INVALID_HEADER),
            deviceKeySlot = deviceKeySlot,
            wrapAad = wrapAad,
            wrapNonce = wrapNonce,
            wrappedRootKeyCiphertext = wrapped.copyOf(BackupProtocol.rootKeyBytes),
            wrappedRootKeyTag = wrapped.copyOfRange(BackupProtocol.rootKeyBytes, wrapped.size),
            streamingCiphertext = ByteArray(input.remaining()).also(input::get),
        )
    }

    fun decryptPortable(password: String, file: ByteArray): ByteArray {
        val parsed = parseEnvelope(file, snapshot = false)
        val kek = derivePortableKek(password, requireNotNull(parsed.kdfSalt))
        val wrapped = parsed.wrappedRootKeyCiphertext + parsed.wrappedRootKeyTag
        val rootKey = try {
            unwrapRootKey(kek, parsed.wrapNonce, wrapped, parsed.wrapAad)
        } finally { kek.fill(0) }
        val ikm = deriveStreamingIkm(rootKey, parsed.envelopeId, snapshot = false).also { rootKey.fill(0) }
        return decryptStreaming(
            ikm,
            streamAssociatedData(parsed.wrapAad, parsed.wrapNonce, wrapped),
            parsed.streamingCiphertext,
        )
    }

    fun decryptSnapshot(wrappingKey: ByteArray, file: ByteArray): ByteArray {
        if (wrappingKey.size != BackupProtocol.rootKeyBytes) throw BackupException(BackupErrorCode.SNAPSHOT_KEY_UNAVAILABLE)
        val parsed = parseEnvelope(file, snapshot = true)
        val wrapped = parsed.wrappedRootKeyCiphertext + parsed.wrappedRootKeyTag
        val rootKey = unwrapRootKey(wrappingKey, parsed.wrapNonce, wrapped, parsed.wrapAad)
        val ikm = deriveStreamingIkm(rootKey, parsed.envelopeId, snapshot = true).also { rootKey.fill(0) }
        return decryptStreaming(
            ikm,
            streamAssociatedData(parsed.wrapAad, parsed.wrapNonce, wrapped),
            parsed.streamingCiphertext,
        )
    }

    fun decryptSnapshot(wrappingKey: SecretKey, file: ByteArray): ByteArray {
        val parsed = parseEnvelope(file, snapshot = true)
        val wrapped = parsed.wrappedRootKeyCiphertext + parsed.wrappedRootKeyTag
        val rootKey = unwrapRootKey(wrappingKey, parsed.wrapNonce, wrapped, parsed.wrapAad)
        val ikm = deriveStreamingIkm(rootKey, parsed.envelopeId, snapshot = true).also { rootKey.fill(0) }
        return decryptStreaming(
            ikm,
            streamAssociatedData(parsed.wrapAad, parsed.wrapNonce, wrapped),
            parsed.streamingCiphertext,
        )
    }

    private fun aesGcm(mode: Int, key: ByteArray, nonce: ByteArray, input: ByteArray, aad: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(aad)
            doFinal(input)
        }

    private fun aesGcm(mode: Int, key: SecretKey, nonce: ByteArray, input: ByteArray, aad: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, key, GCMParameterSpec(128, nonce))
            updateAAD(aad)
            doFinal(input)
        }

    private fun validateSurrogates(password: String) {
        var index = 0
        while (index < password.length) {
            val current = password[index]
            when {
                Character.isHighSurrogate(current) -> {
                    if (index + 1 >= password.length || !Character.isLowSurrogate(password[index + 1])) throw BackupException(BackupErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
                    index += 2
                }
                Character.isLowSurrogate(current) -> throw BackupException(BackupErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
                else -> index += 1
            }
        }
    }

    private fun ByteArrayOutputStream.writeU16(value: Int) {
        write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array())
    }

    private fun ByteArrayOutputStream.writeU32(value: Int) {
        write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
    }
}
