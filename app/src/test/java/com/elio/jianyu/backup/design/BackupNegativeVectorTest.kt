package com.elio.jianyu.backup.design

import org.junit.Test

class BackupNegativeVectorTest {
    @Test
    fun authenticatedEnvelopeMutationsFailAsAuthenticationError() {
        val vector = BackupVectorTestSupport.load("portable-empty.json")
        val password = vector.getString("passwordDisplay")
        val base = vector.getJSONObject("ciphertext").getString("completeFileHex").hexToBytes()
        val offsets = vectorOffsets()

        listOf(
            offsets.kdfSaltStart,
            offsets.wrappedRootKeyStart,
            offsets.streamingCiphertextStart + 1,
            offsets.streamingCiphertextStart + BackupDesignConstants.STREAM_HEADER_BYTES,
            base.lastIndex,
        ).forEach { offset ->
            val mutated = base.copyOf()
            mutated[offset] = (mutated[offset].toInt() xor 1).toByte()
            runCatching {
                BackupCryptoPrototype.decryptPortableVectorFile(password, mutated)
            }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.AUTHENTICATION_FAILED)
        }
    }

    @Test
    fun ciphertextTruncationAndRawAppendDoNotProducePartialSuccess() {
        val vector = BackupVectorTestSupport.load("portable-empty.json")
        val password = vector.getString("passwordDisplay")
        val base = vector.getJSONObject("ciphertext").getString("completeFileHex").hexToBytes()

        listOf(
            base.copyOf(base.size - 1),
            base + byteArrayOf(0),
        ).forEach { mutated ->
            runCatching {
                BackupCryptoPrototype.decryptPortableVectorFile(password, mutated)
            }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.AUTHENTICATION_FAILED)
        }
    }

    @Test
    fun structureErrorsAreRejectedBeforePasswordDerivation() {
        val base = BackupVectorTestSupport.load("portable-empty.json")
            .getJSONObject("ciphertext")
            .getString("completeFileHex")
            .hexToBytes()

        val unknownVersion = base.copyOf().apply {
            this[8] = 0
            this[9] = 2
        }
        runCatching {
            BackupEnvelopePrototype.parsePortable(unknownVersion)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.UNSUPPORTED_ENVELOPE_VERSION)

        val oversizedHeader = base.copyOf().apply {
            this[12] = 0
            this[13] = 0
            this[14] = 0x10
            this[15] = 0x01
        }
        runCatching {
            BackupEnvelopePrototype.parsePortable(oversizedHeader)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.INVALID_HEADER)
    }

    private fun vectorOffsets(): VectorOffsets {
        val offsets = BackupVectorTestSupport.load("negative-vectors.json").getJSONObject("offsets")
        return VectorOffsets(
            kdfSaltStart = offsets.getInt("kdfSaltStart"),
            wrappedRootKeyStart = offsets.getInt("wrappedRootKeyStart"),
            streamingCiphertextStart = offsets.getInt("streamingCiphertextStart"),
        )
    }

    private data class VectorOffsets(
        val kdfSaltStart: Int,
        val wrappedRootKeyStart: Int,
        val streamingCiphertextStart: Int,
    )
}
