package com.elio.jianyu.backup.design

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * PR09-13A cryptographic design prototype.
 *
 * Fixed random inputs are accepted only to reproduce public test vectors.
 * Not a production API and not used by the app runtime.
 */
internal object BackupCryptoPrototype {
    fun normalizePasswordUtf8(password: String): ByteArray {
        if (password.isEmpty() || password.indexOf('\u0000') >= 0) {
            throw BackupDesignException(BackupDesignErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
        }
        var index = 0
        while (index < password.length) {
            val current = password[index]
            when {
                Character.isHighSurrogate(current) -> {
                    if (index + 1 >= password.length || !Character.isLowSurrogate(password[index + 1])) {
                        throw BackupDesignException(BackupDesignErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
                    }
                    index += 2
                }
                Character.isLowSurrogate(current) ->
                    throw BackupDesignException(BackupDesignErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
                else -> index += 1
            }
        }
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFC)
            .toByteArray(StandardCharsets.UTF_8)
        if (normalized.isEmpty() || normalized.size > BackupDesignConstants.MAX_PASSWORD_UTF8_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.KDF_PARAMETERS_OUT_OF_POLICY)
        }
        return normalized
    }

    fun derivePortableKek(
        password: String,
        salt: ByteArray,
    ): ByteArray {
        if (salt.size != BackupDesignConstants.KDF_SALT_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        }
        val passwordBytes = normalizePasswordUtf8(password)
        return try {
            val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(BackupDesignConstants.ARGON2_VERSION_13)
                .withMemoryAsKB(BackupDesignConstants.ARGON2_MEMORY_KIB)
                .withIterations(BackupDesignConstants.ARGON2_ITERATIONS)
                .withParallelism(BackupDesignConstants.ARGON2_PARALLELISM)
                .withSalt(salt.copyOf())
                .build()
            val generator = Argon2BytesGenerator().apply { init(parameters) }
            ByteArray(BackupDesignConstants.ROOT_KEY_BYTES).also { output ->
                generator.generateBytes(passwordBytes, output)
            }
        } catch (error: OutOfMemoryError) {
            throw BackupDesignException(BackupDesignErrorCode.KDF_RESOURCE_UNAVAILABLE, error)
        } catch (error: BackupDesignException) {
            throw error
        } catch (error: Throwable) {
            throw BackupDesignException(BackupDesignErrorCode.KDF_RESOURCE_UNAVAILABLE, error)
        } finally {
            passwordBytes.fill(0)
        }
    }

    fun wrapRootKey(
        kek: ByteArray,
        nonce: ByteArray,
        rootKey: ByteArray,
        aad: ByteArray,
    ): ByteArray = aesGcm(
        mode = Cipher.ENCRYPT_MODE,
        key = kek,
        nonce = nonce,
        input = rootKey,
        aad = aad,
    )

    fun unwrapRootKey(
        kek: ByteArray,
        nonce: ByteArray,
        wrappedRootKey: ByteArray,
        aad: ByteArray,
    ): ByteArray = try {
        aesGcm(
            mode = Cipher.DECRYPT_MODE,
            key = kek,
            nonce = nonce,
            input = wrappedRootKey,
            aad = aad,
        ).also { rootKey ->
            if (rootKey.size != BackupDesignConstants.ROOT_KEY_BYTES) {
                throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED)
            }
        }
    } catch (error: BackupDesignException) {
        throw error
    } catch (error: Throwable) {
        throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED, error)
    }

    fun derivePortableStreamingIkm(
        rootKey: ByteArray,
        envelopeId: ByteArray,
    ): ByteArray = hkdfSha256(
        ikm = rootKey,
        salt = envelopeId,
        info = BackupDesignConstants.PORTABLE_STREAM_INFO.toByteArray(StandardCharsets.US_ASCII),
        outputLength = BackupDesignConstants.STREAM_DERIVED_KEY_BYTES,
    )

    fun deriveSnapshotStreamingIkm(
        rootKey: ByteArray,
        envelopeId: ByteArray,
    ): ByteArray = hkdfSha256(
        ikm = rootKey,
        salt = envelopeId,
        info = BackupDesignConstants.SNAPSHOT_STREAM_INFO.toByteArray(StandardCharsets.US_ASCII),
        outputLength = BackupDesignConstants.STREAM_DERIVED_KEY_BYTES,
    )

    fun streamAssociatedData(
        wrapAad: ByteArray,
        wrapNonce: ByteArray,
        wrappedRootKey: ByteArray,
    ): ByteArray = sha256(wrapAad + wrapNonce + wrappedRootKey)

    fun encryptStreamingReference(
        ikm: ByteArray,
        associatedData: ByteArray,
        plaintext: ByteArray,
        streamSalt: ByteArray,
        noncePrefix: ByteArray,
        ciphertextSegmentSize: Int,
    ): ByteArray {
        validateStreamingInputs(ikm, streamSalt, noncePrefix, ciphertextSegmentSize)
        val derivedKey = hkdfSha256(
            ikm = ikm,
            salt = streamSalt,
            info = associatedData,
            outputLength = BackupDesignConstants.STREAM_DERIVED_KEY_BYTES,
        )
        val header = byteArrayOf(BackupDesignConstants.STREAM_HEADER_BYTES.toByte()) +
            streamSalt + noncePrefix
        val firstPlaintextLimit = ciphertextSegmentSize - header.size - BackupDesignConstants.GCM_TAG_BYTES
        val laterPlaintextLimit = ciphertextSegmentSize - BackupDesignConstants.GCM_TAG_BYTES
        if (firstPlaintextLimit < 0 || laterPlaintextLimit <= 0) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_AEAD)
        }

        val segments = mutableListOf<ByteArray>()
        if (plaintext.size <= firstPlaintextLimit) {
            segments += plaintext
        } else {
            segments += plaintext.copyOfRange(0, firstPlaintextLimit)
            var offset = firstPlaintextLimit
            while (offset < plaintext.size) {
                val end = minOf(plaintext.size, offset + laterPlaintextLimit)
                segments += plaintext.copyOfRange(offset, end)
                offset = end
            }
        }

        return ByteArrayOutputStream().apply {
            write(header)
            segments.forEachIndexed { segmentIndex, segment ->
                val final = segmentIndex == segments.lastIndex
                val nonce = streamingNonce(noncePrefix, segmentIndex, final)
                write(
                    aesGcm(
                        mode = Cipher.ENCRYPT_MODE,
                        key = derivedKey,
                        nonce = nonce,
                        input = segment,
                        aad = ByteArray(0),
                    ),
                )
            }
        }.toByteArray()
    }

    fun decryptStreamingReference(
        ikm: ByteArray,
        associatedData: ByteArray,
        ciphertext: ByteArray,
        ciphertextSegmentSize: Int,
    ): ByteArray {
        if (ciphertext.size < BackupDesignConstants.STREAM_HEADER_BYTES + BackupDesignConstants.GCM_TAG_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED)
        }
        val headerLength = ciphertext[0].toInt() and 0xff
        if (headerLength != BackupDesignConstants.STREAM_HEADER_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED)
        }
        val streamSalt = ciphertext.copyOfRange(1, 33)
        val noncePrefix = ciphertext.copyOfRange(33, 40)
        validateStreamingInputs(ikm, streamSalt, noncePrefix, ciphertextSegmentSize)
        val derivedKey = hkdfSha256(
            ikm = ikm,
            salt = streamSalt,
            info = associatedData,
            outputLength = BackupDesignConstants.STREAM_DERIVED_KEY_BYTES,
        )

        var offset = BackupDesignConstants.STREAM_HEADER_BYTES
        var segmentIndex = 0
        val plaintext = ByteArrayOutputStream()
        try {
            while (offset < ciphertext.size) {
                val remaining = ciphertext.size - offset
                val segmentLength = minOf(ciphertextSegmentSize, remaining)
                if (segmentLength < BackupDesignConstants.GCM_TAG_BYTES) {
                    throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED)
                }
                val final = remaining <= ciphertextSegmentSize
                val encryptedSegment = ciphertext.copyOfRange(offset, offset + segmentLength)
                plaintext.write(
                    aesGcm(
                        mode = Cipher.DECRYPT_MODE,
                        key = derivedKey,
                        nonce = streamingNonce(noncePrefix, segmentIndex, final),
                        input = encryptedSegment,
                        aad = ByteArray(0),
                    ),
                )
                offset += segmentLength
                segmentIndex += 1
                if (final && offset != ciphertext.size) {
                    throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED)
                }
            }
        } catch (error: BackupDesignException) {
            throw error
        } catch (error: Throwable) {
            throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED, error)
        }
        if (segmentIndex == 0) {
            throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED)
        }
        return plaintext.toByteArray()
    }

    fun buildPortableVectorFile(
        password: String,
        salt: ByteArray,
        envelopeId: ByteArray,
        rootKey: ByteArray,
        wrapNonce: ByteArray,
        streamSalt: ByteArray,
        noncePrefix: ByteArray,
        recordStream: ByteArray,
        ciphertextSegmentSize: Int = BackupDesignConstants.PRODUCTION_CIPHERTEXT_SEGMENT_BYTES,
    ): ByteArray {
        val header = BackupEnvelopePrototype.encodePortableHeader(salt, envelopeId)
        val wrapAad = BackupEnvelopePrototype.buildOuterPrefix(
            BackupDesignConstants.PORTABLE_MAGIC,
            header,
        )
        val kek = derivePortableKek(password, salt)
        val wrappedRootKey = wrapRootKey(kek, wrapNonce, rootKey, wrapAad)
        val streamIkm = derivePortableStreamingIkm(rootKey, envelopeId)
        val streamAad = streamAssociatedData(wrapAad, wrapNonce, wrappedRootKey)
        val streamingCiphertext = encryptStreamingReference(
            ikm = streamIkm,
            associatedData = streamAad,
            plaintext = recordStream,
            streamSalt = streamSalt,
            noncePrefix = noncePrefix,
            ciphertextSegmentSize = ciphertextSegmentSize,
        )
        kek.fill(0)
        return wrapAad + wrapNonce + wrappedRootKey + streamingCiphertext
    }

    fun decryptPortableVectorFile(
        password: String,
        file: ByteArray,
    ): ByteArray {
        val parsed = BackupEnvelopePrototype.parsePortable(file)
        val salt = parsed.header.kdfSalt
            ?: throw BackupDesignException(BackupDesignErrorCode.INVALID_HEADER)
        val kek = derivePortableKek(password, salt)
        val rootKey = try {
            unwrapRootKey(kek, parsed.wrapNonce, parsed.wrappedRootKey, parsed.wrapAad)
        } finally {
            kek.fill(0)
        }
        val streamIkm = derivePortableStreamingIkm(rootKey, parsed.header.envelopeId)
        rootKey.fill(0)
        val streamAad = streamAssociatedData(
            parsed.wrapAad,
            parsed.wrapNonce,
            parsed.wrappedRootKey,
        )
        return decryptStreamingReference(
            ikm = streamIkm,
            associatedData = streamAad,
            ciphertext = parsed.streamingCiphertext,
            ciphertextSegmentSize = BackupDesignConstants.PRODUCTION_CIPHERTEXT_SEGMENT_BYTES,
        )
    }

    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int,
    ): ByteArray {
        require(outputLength in 1..(255 * 32))
        val extract = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(salt, "HmacSHA256"))
        }.doFinal(ikm)
        val output = ByteArrayOutputStream()
        var previous = ByteArray(0)
        var counter = 1
        while (output.size() < outputLength) {
            previous = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(extract, "HmacSHA256"))
                update(previous)
                update(info)
                update(counter.toByte())
            }.doFinal()
            output.write(previous)
            counter += 1
        }
        extract.fill(0)
        previous.fill(0)
        return output.toByteArray().copyOf(outputLength)
    }

    fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private fun validateStreamingInputs(
        ikm: ByteArray,
        streamSalt: ByteArray,
        noncePrefix: ByteArray,
        ciphertextSegmentSize: Int,
    ) {
        if (ikm.size != BackupDesignConstants.STREAM_DERIVED_KEY_BYTES ||
            streamSalt.size != BackupDesignConstants.STREAM_DERIVED_KEY_BYTES ||
            noncePrefix.size != BackupDesignConstants.STREAM_NONCE_PREFIX_BYTES ||
            ciphertextSegmentSize <= BackupDesignConstants.STREAM_HEADER_BYTES + BackupDesignConstants.GCM_TAG_BYTES
        ) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_AEAD)
        }
    }

    private fun streamingNonce(
        noncePrefix: ByteArray,
        segmentIndex: Int,
        final: Boolean,
    ): ByteArray = ByteBuffer.allocate(12)
        .order(ByteOrder.BIG_ENDIAN)
        .put(noncePrefix)
        .putInt(segmentIndex)
        .put(if (final) 1 else 0)
        .array()

    private fun aesGcm(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        if (key.size != BackupDesignConstants.ROOT_KEY_BYTES || nonce.size != BackupDesignConstants.WRAP_NONCE_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_AEAD)
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(
                    mode,
                    SecretKeySpec(key, "AES"),
                    GCMParameterSpec(BackupDesignConstants.GCM_TAG_BYTES * 8, nonce),
                )
                updateAAD(aad)
                doFinal(input)
            }
        } catch (error: GeneralSecurityException) {
            if (mode == Cipher.DECRYPT_MODE) {
                throw BackupDesignException(BackupDesignErrorCode.AUTHENTICATION_FAILED, error)
            }
            throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_AEAD, error)
        }
    }
}

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex length must be even" }
    return ByteArray(length / 2) { index ->
        substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

internal fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and 0xff)
}
