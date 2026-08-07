package com.elio.jianyu.backup.design

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PR09-13A design prototype for framed record verification.
 *
 * Not a production API and not used by the app runtime.
 */
internal data class PrototypeRecordVerification(
    val recordCountBeforeComplete: Long,
    val entityCount: Long,
    val blobCount: Long,
    val totalPlaintextBytesBeforeComplete: Long,
)

internal object BackupRecordStreamPrototype {
    fun verify(recordStream: ByteArray): PrototypeRecordVerification {
        if (recordStream.size.toLong() > BackupDesignConstants.MAX_TOTAL_PLAINTEXT_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.TOTAL_SIZE_EXCEEDED)
        }
        var offset = 0
        var recordCount = 0L
        var entityCount = 0L
        var blobCount = 0L
        var nextSequence = 0L
        var manifestCbor: ByteArray? = null
        var completeSeen = false
        val activeBlobs = linkedMapOf<String, BlobState>()

        while (offset < recordStream.size) {
            if (completeSeen) throw BackupDesignException(BackupDesignErrorCode.TRAILING_DATA)
            if (recordStream.size - offset < 4) {
                throw BackupDesignException(BackupDesignErrorCode.TRUNCATED_PAYLOAD)
            }
            val frameStart = offset
            val recordLength = ByteBuffer.wrap(recordStream, offset, 4)
                .order(ByteOrder.BIG_ENDIAN)
                .int
            offset += 4
            if (recordLength !in 1..BackupDesignConstants.MAX_RECORD_BYTES) {
                throw BackupDesignException(BackupDesignErrorCode.ENTRY_SIZE_EXCEEDED)
            }
            if (recordLength > recordStream.size - offset) {
                throw BackupDesignException(BackupDesignErrorCode.TRUNCATED_PAYLOAD)
            }
            val recordCbor = recordStream.copyOfRange(offset, offset + recordLength)
            offset += recordLength
            val record = CanonicalCborPrototype.decodeCanonical(
                recordCbor,
                BackupDesignErrorCode.VERIFICATION_FAILED,
            ) as? PrototypeCborValue.MapValue
                ?: throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
            val recordType = record.unsigned(1L)

            if (recordCount == 0L && recordType != BackupDesignConstants.RECORD_MANIFEST) {
                throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
            }
            when (recordType) {
                BackupDesignConstants.RECORD_MANIFEST -> {
                    if (recordCount != 0L || manifestCbor != null) {
                        throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
                    }
                    if (record.unsigned(2L) != BackupDesignConstants.MANIFEST_VERSION.toLong()) {
                        throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_MANIFEST_VERSION)
                    }
                    val formatId = record.text(3L)
                    if (formatId != BackupDesignConstants.PORTABLE_FORMAT_ID &&
                        formatId != BackupDesignConstants.SNAPSHOT_FORMAT_ID
                    ) {
                        throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
                    }
                    val declaredEntries = record.unsigned(8L)
                    val declaredBlobs = record.unsigned(9L)
                    val declaredTotal = record.unsigned(10L)
                    checkLimits(declaredEntries, declaredBlobs, declaredTotal)
                    val requiredFeatures = record.unsigned(11L)
                    if (requiredFeatures != 0L) {
                        throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
                    }
                    manifestCbor = recordCbor
                    recordCount += 1
                }

                BackupDesignConstants.RECORD_ENTITY -> {
                    requireSequence(record, nextSequence++)
                    validateLogicalId(record.text(3L))
                    val payload = record.values[6L]
                        ?: throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
                    val expectedHash = record.bytes(7L)
                    if (expectedHash.size != 32 ||
                        !BackupCryptoPrototype.sha256(CanonicalCborPrototype.encode(payload)).contentEquals(expectedHash)
                    ) {
                        throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
                    }
                    entityCount += 1
                    recordCount += 1
                    if (entityCount + blobCount > BackupDesignConstants.MAX_LOGICAL_ENTRIES) {
                        throw BackupDesignException(BackupDesignErrorCode.ENTRY_LIMIT_EXCEEDED)
                    }
                }

                BackupDesignConstants.RECORD_BLOB_START -> {
                    requireSequence(record, nextSequence++)
                    val logicalId = record.text(3L).also(::validateLogicalId)
                    if (activeBlobs.containsKey(logicalId)) {
                        throw BackupDesignException(BackupDesignErrorCode.DUPLICATE_CHUNK)
                    }
                    val declaredSize = record.unsigned(6L)
                    val declaredHash = record.bytes(7L)
                    val chunkSize = record.unsigned(8L)
                    if (declaredSize > BackupDesignConstants.MAX_SINGLE_BLOB_BYTES ||
                        declaredHash.size != 32 ||
                        chunkSize != BackupDesignConstants.MAX_BLOB_CHUNK_BYTES.toLong()
                    ) {
                        throw BackupDesignException(BackupDesignErrorCode.ENTRY_SIZE_EXCEEDED)
                    }
                    activeBlobs[logicalId] = BlobState(
                        declaredSize = declaredSize,
                        declaredHash = declaredHash,
                    )
                    blobCount += 1
                    recordCount += 1
                    if (blobCount > BackupDesignConstants.MAX_BLOBS ||
                        entityCount + blobCount > BackupDesignConstants.MAX_LOGICAL_ENTRIES
                    ) {
                        throw BackupDesignException(BackupDesignErrorCode.ENTRY_LIMIT_EXCEEDED)
                    }
                }

                BackupDesignConstants.RECORD_BLOB_CHUNK -> {
                    requireSequence(record, nextSequence++)
                    val logicalId = record.text(3L).also(::validateLogicalId)
                    val state = activeBlobs[logicalId]
                        ?: throw BackupDesignException(BackupDesignErrorCode.CHUNK_ORDER_INVALID)
                    val chunkIndex = record.unsigned(4L)
                    if (chunkIndex < state.nextChunkIndex) {
                        throw BackupDesignException(BackupDesignErrorCode.DUPLICATE_CHUNK)
                    }
                    if (chunkIndex != state.nextChunkIndex) {
                        throw BackupDesignException(BackupDesignErrorCode.CHUNK_ORDER_INVALID)
                    }
                    val chunk = record.bytes(5L)
                    if (chunk.size > BackupDesignConstants.MAX_BLOB_CHUNK_BYTES) {
                        throw BackupDesignException(BackupDesignErrorCode.ENTRY_SIZE_EXCEEDED)
                    }
                    state.digest.update(chunk)
                    state.actualSize = safeAdd(state.actualSize, chunk.size.toLong())
                    if (state.actualSize > state.declaredSize ||
                        state.actualSize > BackupDesignConstants.MAX_SINGLE_BLOB_BYTES
                    ) {
                        throw BackupDesignException(BackupDesignErrorCode.ENTRY_SIZE_EXCEEDED)
                    }
                    state.nextChunkIndex += 1
                    recordCount += 1
                }

                BackupDesignConstants.RECORD_BLOB_END -> {
                    requireSequence(record, nextSequence++)
                    val logicalId = record.text(3L).also(::validateLogicalId)
                    val state = activeBlobs.remove(logicalId)
                        ?: throw BackupDesignException(BackupDesignErrorCode.CHUNK_ORDER_INVALID)
                    val actualSize = record.unsigned(4L)
                    val actualHash = record.bytes(5L)
                    val chunkCount = record.unsigned(6L)
                    val calculatedHash = state.digest.digest()
                    if (actualSize != state.actualSize ||
                        actualSize != state.declaredSize ||
                        chunkCount != state.nextChunkIndex ||
                        actualHash.size != 32 ||
                        !actualHash.contentEquals(calculatedHash) ||
                        !state.declaredHash.contentEquals(calculatedHash)
                    ) {
                        throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
                    }
                    recordCount += 1
                }

                BackupDesignConstants.RECORD_COMPLETE -> {
                    if (manifestCbor == null || activeBlobs.isNotEmpty()) {
                        throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
                    }
                    val expectedRecordCount = record.unsigned(2L)
                    val expectedEntityCount = record.unsigned(3L)
                    val expectedBlobCount = record.unsigned(4L)
                    val expectedBytes = record.unsigned(5L)
                    val expectedTranscriptHash = record.bytes(6L)
                    val expectedManifestHash = record.bytes(7L)
                    val transcript = recordStream.copyOfRange(0, frameStart)
                    if (expectedRecordCount != recordCount ||
                        expectedEntityCount != entityCount ||
                        expectedBlobCount != blobCount ||
                        expectedBytes != transcript.size.toLong() ||
                        expectedTranscriptHash.size != 32 ||
                        expectedManifestHash.size != 32 ||
                        !expectedTranscriptHash.contentEquals(BackupCryptoPrototype.sha256(transcript)) ||
                        !expectedManifestHash.contentEquals(BackupCryptoPrototype.sha256(manifestCbor))
                    ) {
                        throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
                    }
                    completeSeen = true
                    if (offset != recordStream.size) {
                        throw BackupDesignException(BackupDesignErrorCode.TRAILING_DATA)
                    }
                }

                else -> throw BackupDesignException(BackupDesignErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
            }
        }

        if (!completeSeen) throw BackupDesignException(BackupDesignErrorCode.TRUNCATED_PAYLOAD)
        return PrototypeRecordVerification(
            recordCountBeforeComplete = recordCount,
            entityCount = entityCount,
            blobCount = blobCount,
            totalPlaintextBytesBeforeComplete = recordStream.size.toLong(),
        )
    }

    private fun checkLimits(entries: Long, blobs: Long, total: Long) {
        if (entries > BackupDesignConstants.MAX_LOGICAL_ENTRIES ||
            blobs > BackupDesignConstants.MAX_BLOBS
        ) {
            throw BackupDesignException(BackupDesignErrorCode.ENTRY_LIMIT_EXCEEDED)
        }
        if (total > BackupDesignConstants.MAX_TOTAL_PLAINTEXT_BYTES) {
            throw BackupDesignException(BackupDesignErrorCode.TOTAL_SIZE_EXCEEDED)
        }
    }

    private fun requireSequence(record: PrototypeCborValue.MapValue, expected: Long) {
        if (record.unsigned(2L) != expected) {
            throw BackupDesignException(BackupDesignErrorCode.CHUNK_ORDER_INVALID)
        }
    }

    private fun validateLogicalId(value: String) {
        if (value.isBlank() || value.length > 200 || value.indexOf('\u0000') >= 0 ||
            value.startsWith('/') || value.startsWith('\\') ||
            value.contains("../") || value.contains("..\\") ||
            value.contains('/') || value.contains('\\')
        ) {
            throw BackupDesignException(BackupDesignErrorCode.PATH_INVALID)
        }
    }

    private fun safeAdd(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        throw BackupDesignException(BackupDesignErrorCode.TOTAL_SIZE_EXCEEDED)
    }

    private data class BlobState(
        val declaredSize: Long,
        val declaredHash: ByteArray,
        val digest: java.security.MessageDigest = java.security.MessageDigest.getInstance("SHA-256"),
        var actualSize: Long = 0L,
        var nextChunkIndex: Long = 0L,
    )

    private fun PrototypeCborValue.MapValue.unsigned(key: Long): Long =
        (values[key] as? PrototypeCborValue.Unsigned)?.value
            ?: throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)

    private fun PrototypeCborValue.MapValue.bytes(key: Long): ByteArray =
        (values[key] as? PrototypeCborValue.Bytes)?.value
            ?: throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)

    private fun PrototypeCborValue.MapValue.text(key: Long): String =
        (values[key] as? PrototypeCborValue.Text)?.value
            ?: throw BackupDesignException(BackupDesignErrorCode.VERIFICATION_FAILED)
}
