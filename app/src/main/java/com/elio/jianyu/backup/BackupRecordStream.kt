package com.elio.jianyu.backup

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

data class BackupManifest(
    val formatId: String,
    val createdAt: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val sourceRoomVersion: Long,
    val logicalEntryCount: Long,
    val blobCount: Long,
    val totalDeclaredPlaintextBytes: Long = 0L,
    val backupScope: List<String> = emptyList(),
)

data class BackupEntityRecord(
    val logicalEntryId: String,
    val entityType: String,
    val payload: BackupCborValue,
    val schemaVersion: Long = 1L,
)

data class BackupBlobRecord(
    val logicalEntryId: String,
    val blobType: String,
    val mimeType: String,
    val data: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is BackupBlobRecord &&
        logicalEntryId == other.logicalEntryId && blobType == other.blobType && mimeType == other.mimeType && data.contentEquals(other.data)
    override fun hashCode(): Int = 31 * (31 * logicalEntryId.hashCode() + blobType.hashCode()) + data.contentHashCode()
}

data class BackupRecordStats(
    val recordCountBeforeComplete: Long,
    val entityCount: Long,
    val blobCount: Long,
    val totalPlaintextBytesBeforeComplete: Long,
)

object BackupRecordStream {
    fun build(
        manifest: BackupManifest,
        entities: List<BackupEntityRecord>,
        blobs: List<BackupBlobRecord> = emptyList(),
    ): ByteArray {
        if (manifest.logicalEntryCount != entities.size.toLong() || manifest.blobCount != blobs.size.toLong()) {
            throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
        }
        val transcript = ByteArrayOutputStream()
        var recordCountBeforeComplete = 0L
        val manifestValue = BackupCborValue.MapValue(
            linkedMapOf(
                1L to BackupCborValue.UInt(BackupProtocol.recordManifest),
                2L to BackupCborValue.UInt(BackupProtocol.manifestVersion),
                3L to BackupCborValue.Text(manifest.formatId),
                4L to BackupCborValue.UInt(nonNegativeTime(manifest.createdAt)),
                5L to BackupCborValue.Text(manifest.appVersionName),
                6L to BackupCborValue.UInt(manifest.appVersionCode),
                7L to BackupCborValue.UInt(manifest.sourceRoomVersion),
                8L to BackupCborValue.UInt(manifest.logicalEntryCount),
                9L to BackupCborValue.UInt(manifest.blobCount),
                10L to BackupCborValue.UInt(manifest.totalDeclaredPlaintextBytes),
                11L to BackupCborValue.UInt(0L),
                12L to BackupCborValue.ArrayValue(manifest.backupScope.map(BackupCborValue::Text)),
            ),
        )
        append(transcript, BackupCanonicalCbor.encode(manifestValue)); recordCountBeforeComplete++
        var sequence = 0L
        entities.forEach { entity ->
            validateLogicalId(entity.logicalEntryId)
            if (entity.entityType !in BackupProtocol.entityTypes || entity.schemaVersion != 1L) {
                throw BackupException(BackupErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
            }
            val payload = BackupCanonicalCbor.encode(entity.payload)
            val value = BackupCborValue.MapValue(
                linkedMapOf(
                    1L to BackupCborValue.UInt(BackupProtocol.recordEntity),
                    2L to BackupCborValue.UInt(sequence++),
                    3L to BackupCborValue.Text(entity.logicalEntryId),
                    4L to BackupCborValue.Text(entity.entityType),
                    5L to BackupCborValue.UInt(entity.schemaVersion),
                    6L to entity.payload,
                    7L to BackupCborValue.Bytes(sha256(payload)),
                ),
            )
            append(transcript, BackupCanonicalCbor.encode(value)); recordCountBeforeComplete++
        }
        blobs.forEach { blob ->
            validateLogicalId(blob.logicalEntryId)
            val hash = sha256(blob.data)
            append(transcript, BackupCanonicalCbor.encode(BackupCborValue.MapValue(linkedMapOf(
                1L to BackupCborValue.UInt(BackupProtocol.recordBlobStart),
                2L to BackupCborValue.UInt(sequence++),
                3L to BackupCborValue.Text(blob.logicalEntryId),
                4L to BackupCborValue.Text(blob.blobType),
                5L to BackupCborValue.Text(blob.mimeType),
                6L to BackupCborValue.UInt(blob.data.size.toLong()),
                7L to BackupCborValue.Bytes(hash),
                8L to BackupCborValue.UInt(BackupProtocol.blobChunkBytes.toLong()),
            )))); recordCountBeforeComplete++
            var chunkIndex = 0L
            var offset = 0
            while (offset < blob.data.size || (blob.data.isEmpty() && chunkIndex == 0L)) {
                val end = minOf(blob.data.size, offset + BackupProtocol.blobChunkBytes)
                val chunk = blob.data.copyOfRange(offset, end)
                append(transcript, BackupCanonicalCbor.encode(BackupCborValue.MapValue(linkedMapOf(
                    1L to BackupCborValue.UInt(BackupProtocol.recordBlobChunk),
                    2L to BackupCborValue.UInt(sequence++),
                    3L to BackupCborValue.Text(blob.logicalEntryId),
                    4L to BackupCborValue.UInt(chunkIndex++),
                    5L to BackupCborValue.Bytes(chunk),
                )))); recordCountBeforeComplete++
                offset = end
                if (blob.data.isEmpty()) break
            }
            append(transcript, BackupCanonicalCbor.encode(BackupCborValue.MapValue(linkedMapOf(
                1L to BackupCborValue.UInt(BackupProtocol.recordBlobEnd),
                2L to BackupCborValue.UInt(sequence++),
                3L to BackupCborValue.Text(blob.logicalEntryId),
                4L to BackupCborValue.UInt(blob.data.size.toLong()),
                5L to BackupCborValue.Bytes(hash),
                6L to BackupCborValue.UInt(chunkIndex),
            )))); recordCountBeforeComplete++
        }
        val beforeComplete = transcript.toByteArray()
        val complete = BackupCborValue.MapValue(linkedMapOf(
            1L to BackupCborValue.UInt(BackupProtocol.recordComplete),
            2L to BackupCborValue.UInt(recordCountBeforeComplete),
            3L to BackupCborValue.UInt(entities.size.toLong()),
            4L to BackupCborValue.UInt(blobs.size.toLong()),
            5L to BackupCborValue.UInt(beforeComplete.size.toLong()),
            6L to BackupCborValue.Bytes(sha256(beforeComplete)),
            7L to BackupCborValue.Bytes(sha256(BackupCanonicalCbor.encode(manifestValue))),
        ))
        return beforeComplete + frame(BackupCanonicalCbor.encode(complete))
    }

    fun verify(recordStream: ByteArray, expectedFormatId: String? = null): BackupRecordStats {
        if (recordStream.size.toLong() > BackupProtocol.maxTotalPlaintextBytes) throw BackupException(BackupErrorCode.TOTAL_SIZE_EXCEEDED)
        var offset = 0
        var recordCount = 0L
        var entityCount = 0L
        var blobCount = 0L
        var sequence = 0L
        var manifestBytes: ByteArray? = null
        var manifest: BackupManifest? = null
        var completeSeen = false
        val blobs = linkedMapOf<String, BlobState>()
        var transcriptBytesBeforeComplete = 0L
        while (offset < recordStream.size) {
            if (completeSeen) throw BackupException(BackupErrorCode.TRAILING_DATA)
            if (recordStream.size - offset < 4) throw BackupException(BackupErrorCode.TRUNCATED_PAYLOAD)
            val frameStart = offset
            val length = ByteBuffer.wrap(recordStream, offset, 4).order(ByteOrder.BIG_ENDIAN).int
            offset += 4
            if (length !in 1..BackupProtocol.maxRecordBytes) throw BackupException(BackupErrorCode.ENTRY_SIZE_EXCEEDED)
            if (length > recordStream.size - offset) throw BackupException(BackupErrorCode.TRUNCATED_PAYLOAD)
            val cbor = recordStream.copyOfRange(offset, offset + length)
            offset += length
            val value = BackupCanonicalCbor.decodeCanonical(cbor, BackupErrorCode.VERIFICATION_FAILED)
            val map = value as? BackupCborValue.MapValue ?: throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
            val type = map.requiredUInt(1L)
            if (recordCount == 0L && type != BackupProtocol.recordManifest) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
            when (type) {
                BackupProtocol.recordManifest -> {
                    if (recordCount != 0L || manifestBytes != null || map.values.keys != (1L..12L).toSet()) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    if (map.requiredUInt(2L) != BackupProtocol.manifestVersion) throw BackupException(BackupErrorCode.UNSUPPORTED_MANIFEST_VERSION)
                    val format = map.requiredText(3L)
                    if (expectedFormatId != null && expectedFormatId != format) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    val entries = map.requiredUInt(8L); val declaredBlobs = map.requiredUInt(9L); val declaredTotal = map.requiredUInt(10L)
                    if (entries > BackupProtocol.maxLogicalEntries || declaredBlobs > BackupProtocol.maxBlobs) throw BackupException(BackupErrorCode.ENTRY_LIMIT_EXCEEDED)
                    if (declaredTotal > BackupProtocol.maxTotalPlaintextBytes) throw BackupException(BackupErrorCode.TOTAL_SIZE_EXCEEDED)
                    if (map.requiredUInt(11L) != 0L) throw BackupException(BackupErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
                    val scope = (map.values[12L] as? BackupCborValue.ArrayValue)?.values?.map {
                        (it as? BackupCborValue.Text)?.value ?: throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    } ?: throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    manifest = BackupManifest(format, map.requiredUInt(4L), map.requiredText(5L), map.requiredUInt(6L), map.requiredUInt(7L), entries, declaredBlobs, declaredTotal, scope)
                    manifestBytes = cbor
                    recordCount++
                }
                BackupProtocol.recordEntity -> {
                    requireKeys(map, (1L..7L).toSet())
                    requireSequence(map, sequence++)
                    validateLogicalId(map.requiredText(3L))
                    val payload = map.values[6L] ?: throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    val expectedHash = map.requiredBytes(7L)
                    if (expectedHash.size != 32 || !expectedHash.contentEquals(sha256(BackupCanonicalCbor.encode(payload)))) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    if (map.requiredUInt(5L) != 1L || map.requiredText(4L).isBlank()) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    if (map.requiredText(4L) !in BackupProtocol.entityTypes) throw BackupException(BackupErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
                    entityCount++; recordCount++
                    if (entityCount + blobCount > BackupProtocol.maxLogicalEntries) throw BackupException(BackupErrorCode.ENTRY_LIMIT_EXCEEDED)
                }
                BackupProtocol.recordBlobStart -> {
                    requireKeys(map, (1L..8L).toSet())
                    requireSequence(map, sequence++)
                    val id = map.requiredText(3L); validateLogicalId(id)
                    if (blobs.containsKey(id)) throw BackupException(BackupErrorCode.DUPLICATE_CHUNK)
                    val size = map.requiredUInt(6L); val hash = map.requiredBytes(7L); val chunkSize = map.requiredUInt(8L)
                    if (size > BackupProtocol.maxSingleBlobBytes || hash.size != 32 || chunkSize != BackupProtocol.blobChunkBytes.toLong()) throw BackupException(BackupErrorCode.ENTRY_SIZE_EXCEEDED)
                    blobs[id] = BlobState(size, hash); blobCount++; recordCount++
                    if (blobCount > BackupProtocol.maxBlobs || entityCount + blobCount > BackupProtocol.maxLogicalEntries) throw BackupException(BackupErrorCode.ENTRY_LIMIT_EXCEEDED)
                }
                BackupProtocol.recordBlobChunk -> {
                    requireKeys(map, (1L..5L).toSet())
                    requireSequence(map, sequence++)
                    val id = map.requiredText(3L); validateLogicalId(id)
                    val state = blobs[id] ?: throw BackupException(BackupErrorCode.CHUNK_ORDER_INVALID)
                    val index = map.requiredUInt(4L)
                    if (index < state.nextChunk || index != state.nextChunk) throw BackupException(if (index < state.nextChunk) BackupErrorCode.DUPLICATE_CHUNK else BackupErrorCode.CHUNK_ORDER_INVALID)
                    val chunk = map.requiredBytes(5L)
                    if (chunk.size > BackupProtocol.blobChunkBytes) throw BackupException(BackupErrorCode.ENTRY_SIZE_EXCEEDED)
                    state.digest.update(chunk); state.actualSize = safeAdd(state.actualSize, chunk.size.toLong())
                    if (state.actualSize > state.declaredSize) throw BackupException(BackupErrorCode.ENTRY_SIZE_EXCEEDED)
                    state.nextChunk++; recordCount++
                }
                BackupProtocol.recordBlobEnd -> {
                    requireKeys(map, (1L..6L).toSet())
                    requireSequence(map, sequence++)
                    val id = map.requiredText(3L); validateLogicalId(id)
                    val state = blobs.remove(id) ?: throw BackupException(BackupErrorCode.CHUNK_ORDER_INVALID)
                    val actualSize = map.requiredUInt(4L); val actualHash = map.requiredBytes(5L); val chunks = map.requiredUInt(6L)
                    val computed = state.digest.digest()
                    if (actualSize != state.actualSize || actualSize != state.declaredSize || chunks != state.nextChunk || actualHash.size != 32 || !actualHash.contentEquals(computed) || !state.declaredHash.contentEquals(computed)) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    recordCount++
                }
                BackupProtocol.recordComplete -> {
                    if (map.values.keys != setOf(1L, 2L, 3L, 4L, 5L, 6L, 7L) || manifestBytes == null || blobs.isNotEmpty()) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    val transcript = recordStream.copyOfRange(0, frameStart)
                    if (map.requiredUInt(2L) != recordCount || map.requiredUInt(3L) != entityCount || map.requiredUInt(4L) != blobCount ||
                        map.requiredUInt(5L) != transcript.size.toLong() || map.requiredBytes(6L).let { it.size != 32 || !it.contentEquals(sha256(transcript)) } ||
                        map.requiredBytes(7L).let { it.size != 32 || !it.contentEquals(sha256(requireNotNull(manifestBytes))) }
                    ) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    manifest?.let {
                        if (it.logicalEntryCount != entityCount || it.blobCount != blobCount) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
                    }
                    transcriptBytesBeforeComplete = transcript.size.toLong()
                    completeSeen = true
                }
                else -> throw BackupException(BackupErrorCode.UNSUPPORTED_REQUIRED_FEATURE)
            }
        }
        if (!completeSeen) throw BackupException(BackupErrorCode.TRUNCATED_PAYLOAD)
        if (offset != recordStream.size) throw BackupException(BackupErrorCode.TRAILING_DATA)
        return BackupRecordStats(recordCount, entityCount, blobCount, transcriptBytesBeforeComplete)
    }

    private fun append(out: ByteArrayOutputStream, cbor: ByteArray) { out.write(frame(cbor)) }

    private fun frame(cbor: ByteArray): ByteArray = ByteBuffer.allocate(4 + cbor.size).order(ByteOrder.BIG_ENDIAN).putInt(cbor.size).put(cbor).array()

    private fun nonNegativeTime(value: Long): Long { if (value < 0L) throw BackupException(BackupErrorCode.VERIFICATION_FAILED); return value }

    private fun requireSequence(map: BackupCborValue.MapValue, expected: Long) {
        if (map.requiredUInt(2L) != expected) throw BackupException(BackupErrorCode.CHUNK_ORDER_INVALID)
    }

    private fun requireKeys(map: BackupCborValue.MapValue, expected: Set<Long>) {
        if (map.values.keys != expected) throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
    }

    private fun validateLogicalId(value: String) {
        if (value.isBlank() || value.length > 200 || value.indexOf('\u0000') >= 0 || value.startsWith('/') || value.startsWith('\\') || value.contains("../") || value.contains("..\\") || value.contains('/') || value.contains('\\')) throw BackupException(BackupErrorCode.PATH_INVALID)
    }

    private fun safeAdd(left: Long, right: Long): Long = try { Math.addExact(left, right) } catch (_: ArithmeticException) { throw BackupException(BackupErrorCode.TOTAL_SIZE_EXCEEDED) }
    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private data class BlobState(val declaredSize: Long, val declaredHash: ByteArray, val digest: MessageDigest = MessageDigest.getInstance("SHA-256"), var actualSize: Long = 0L, var nextChunk: Long = 0L)
}
