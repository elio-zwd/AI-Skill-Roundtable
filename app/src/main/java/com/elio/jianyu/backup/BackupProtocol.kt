package com.elio.jianyu.backup

import java.io.IOException

/** PR09-13B production protocol constants. */
object BackupProtocol {
    val portableMagic: ByteArray = byteArrayOf(0x4a, 0x59, 0x42, 0x4b, 0x50, 0x0d, 0x0a, 0x1a)
    val snapshotMagic: ByteArray = byteArrayOf(0x4a, 0x59, 0x53, 0x4e, 0x50, 0x0d, 0x0a, 0x1a)

    const val envelopeVersion = 1
    const val headerEncodingDeterministicCbor = 1
    const val manifestVersion = 1L
    const val portableFormatKind = 1L
    const val snapshotFormatKind = 2L
    const val kdfNone = 0L
    const val kdfArgon2id = 1L
    const val kdfProfileNone = 0L
    const val kdfProfileArgon2idV1 = 1L
    const val keyWrapAes256Gcm = 1L
    const val streamingAes256GcmHkdf1Mb = 1L
    const val serializationDeterministicCborV1 = 1L
    const val snapshotDeviceKeySlotV1 = 1L

    const val argon2Version13 = 0x13
    const val argon2MemoryKiB = 65_536
    const val argon2Iterations = 3
    const val argon2Parallelism = 1
    const val kdfSaltBytes = 16
    const val rootKeyBytes = 32
    const val wrapNonceBytes = 12
    const val gcmTagBytes = 16
    const val envelopeIdBytes = 16
    const val streamingIkmBytes = 32
    const val streamingHeaderBytes = 40
    const val streamingSegmentBytes = 1_048_576
    const val blobChunkBytes = 262_144

    const val maxHeaderBytes = 4_096
    const val maxRecordBytes = 1_048_576
    const val maxLogicalEntries = 1_000_000L
    const val maxBlobs = 10_000L
    const val maxSingleBlobBytes = 68_719_476_736L
    const val maxTotalPlaintextBytes = 1_099_511_627_776L
    const val maxPasswordUtf8Bytes = 1_024

    const val recordManifest = 1L
    const val recordEntity = 2L
    const val recordBlobStart = 3L
    const val recordBlobChunk = 4L
    const val recordBlobEnd = 5L
    const val recordComplete = 255L

    const val portableFormatId = "jianyu-portable-backup/1"
    const val snapshotFormatId = "jianyu-device-snapshot/1"
    const val portableStreamInfo = "jianyu/portable-backup/v1/stream"
    const val snapshotStreamInfo = "jianyu/device-snapshot/v1/stream"
    const val snapshotKeyAlias = "jianyu_backup_snapshot_wrap_v1"
    const val apiKeyAlias = "skill_roundtable_api_key_v1"
    const val portableExtension = ".jybak"
    const val snapshotExtension = ".jysnap"
    const val portableMime = "application/vnd.jianyu.backup"
    const val snapshotMime = "application/vnd.jianyu.snapshot"
}

enum class BackupErrorCode(val storageValue: String) {
    INVALID_MAGIC("invalid_magic"),
    UNSUPPORTED_ENVELOPE_VERSION("unsupported_envelope_version"),
    UNSUPPORTED_MANIFEST_VERSION("unsupported_manifest_version"),
    UNSUPPORTED_KDF("unsupported_kdf"),
    KDF_PARAMETERS_OUT_OF_POLICY("kdf_parameters_out_of_policy"),
    KDF_RESOURCE_UNAVAILABLE("kdf_resource_unavailable"),
    UNSUPPORTED_AEAD("unsupported_aead"),
    UNSUPPORTED_REQUIRED_FEATURE("unsupported_required_feature"),
    INVALID_HEADER("invalid_header"),
    AUTHENTICATION_FAILED("authentication_failed"),
    TRUNCATED_PAYLOAD("truncated_payload"),
    TRAILING_DATA("trailing_data"),
    CHUNK_ORDER_INVALID("chunk_order_invalid"),
    DUPLICATE_CHUNK("duplicate_chunk"),
    ENTRY_LIMIT_EXCEEDED("entry_limit_exceeded"),
    ENTRY_SIZE_EXCEEDED("entry_size_exceeded"),
    TOTAL_SIZE_EXCEEDED("total_size_exceeded"),
    PATH_INVALID("path_invalid"),
    SOURCE_CHANGED("source_changed"),
    PURGE_IN_PROGRESS("purge_in_progress"),
    ACTIVE_WORK_IN_PROGRESS("active_work_in_progress"),
    INSUFFICIENT_SPACE("insufficient_space"),
    TARGET_WRITE_FAILED("target_write_failed"),
    VERIFICATION_FAILED("verification_failed"),
    OPERATION_CANCELED("operation_canceled"),
    TEMPORARY_CLEANUP_FAILED("temporary_cleanup_failed"),
    SNAPSHOT_KEY_UNAVAILABLE("snapshot_key_unavailable"),
    SNAPSHOT_CORRUPTED("snapshot_corrupted"),
    DATABASE_CHECKPOINT_FAILED("database_checkpoint_failed"),
    DATABASE_INTEGRITY_FAILED("database_integrity_failed"),
    OPERATION_ALREADY_RUNNING("operation_already_running"),
    UNSUPPORTED_LEGACY_DATA("unsupported_legacy_data"),
    PROVIDER_CAPABILITY_MISSING("provider_capability_missing"),
}

class BackupException(
    val code: BackupErrorCode,
    cause: Throwable? = null,
) : IOException(code.storageValue, cause)

/** Restricted deterministic CBOR value model used by V1 headers and records. */
sealed interface BackupCborValue {
    data class UInt(val value: Long) : BackupCborValue {
        init { require(value >= 0L) }
    }

    data class Bytes(val value: ByteArray) : BackupCborValue {
        override fun equals(other: Any?): Boolean = other is Bytes && value.contentEquals(other.value)
        override fun hashCode(): Int = value.contentHashCode()
    }

    data class Text(val value: String) : BackupCborValue
    data class ArrayValue(val values: List<BackupCborValue>) : BackupCborValue
    data class MapValue(val values: Map<Long, BackupCborValue>) : BackupCborValue
}

object BackupCanonicalCbor {
    fun encode(value: BackupCborValue): ByteArray = java.io.ByteArrayOutputStream().also {
        writeValue(it, value)
    }.toByteArray()

    fun decodeCanonical(bytes: ByteArray, error: BackupErrorCode = BackupErrorCode.INVALID_HEADER): BackupCborValue {
        val reader = Reader(bytes, error)
        val value = reader.readValue()
        if (!reader.atEnd() || !encode(value).contentEquals(bytes)) throw BackupException(error)
        return value
    }

    private fun writeValue(out: java.io.ByteArrayOutputStream, value: BackupCborValue) {
        when (value) {
            is BackupCborValue.UInt -> writeLength(out, 0, value.value)
            is BackupCborValue.Bytes -> {
                writeLength(out, 2, value.value.size.toLong()); out.write(value.value)
            }
            is BackupCborValue.Text -> {
                val encoded = value.value.toByteArray(Charsets.UTF_8)
                writeLength(out, 3, encoded.size.toLong()); out.write(encoded)
            }
            is BackupCborValue.ArrayValue -> {
                writeLength(out, 4, value.values.size.toLong()); value.values.forEach { writeValue(out, it) }
            }
            is BackupCborValue.MapValue -> {
                val entries = value.values.entries.sortedBy { it.key }
                require(entries.all { it.key >= 0L })
                writeLength(out, 5, entries.size.toLong())
                entries.forEach { (key, entry) -> writeLength(out, 0, key); writeValue(out, entry) }
            }
        }
    }

    private fun writeLength(out: java.io.ByteArrayOutputStream, major: Int, value: Long) {
        require(value >= 0L)
        val prefix = major shl 5
        when {
            value < 24 -> out.write(prefix or value.toInt())
            value <= 0xff -> { out.write(prefix or 24); out.write(value.toInt()) }
            value <= 0xffff -> {
                out.write(prefix or 25); out.write(java.nio.ByteBuffer.allocate(2).putShort(value.toShort()).array())
            }
            value <= 0xffff_ffffL -> {
                out.write(prefix or 26); out.write(java.nio.ByteBuffer.allocate(4).putInt(value.toInt()).array())
            }
            else -> { out.write(prefix or 27); out.write(java.nio.ByteBuffer.allocate(8).putLong(value).array()) }
        }
    }

    private class Reader(private val bytes: ByteArray, private val error: BackupErrorCode) {
        private var offset = 0
        fun atEnd() = offset == bytes.size
        fun readValue(): BackupCborValue {
            val initial = readByte()
            val major = initial ushr 5
            val additional = initial and 0x1f
            if (additional == 31) fail()
            return when (major) {
                0 -> BackupCborValue.UInt(readLength(additional))
                2 -> BackupCborValue.Bytes(readBytes(readLength(additional)))
                3 -> BackupCborValue.Text(readUtf8(readBytes(readLength(additional))))
                4 -> BackupCborValue.ArrayValue(List(checkedCount(readLength(additional))) { readValue() })
                5 -> {
                    val count = checkedCount(readLength(additional))
                    val values = linkedMapOf<Long, BackupCborValue>()
                    var previous = -1L
                    repeat(count) {
                        val key = (readValue() as? BackupCborValue.UInt)?.value ?: fail()
                        if (key <= previous || values.containsKey(key)) fail()
                        previous = key
                        values[key] = readValue()
                    }
                    BackupCborValue.MapValue(values)
                }
                else -> fail()
            }
        }

        private fun readLength(additional: Int): Long = when (additional) {
            in 0..23 -> additional.toLong()
            24 -> readByte().toLong().also { if (it < 24) fail() }
            25 -> readShort().toLong().also { if (it <= 0xff) fail() }
            26 -> readInt().also { if (it <= 0xffff) fail() }
            27 -> readLong().also { if (it < 0 || it <= 0xffff_ffffL) fail() }
            else -> fail()
        }

        private fun checkedCount(value: Long): Int {
            if (value > Int.MAX_VALUE) fail()
            return value.toInt()
        }

        private fun readBytes(length: Long): ByteArray {
            if (length > Int.MAX_VALUE) fail()
            val size = length.toInt()
            if (size < 0 || offset > bytes.size - size) fail()
            return bytes.copyOfRange(offset, offset + size).also { offset += size }
        }

        private fun readByte(): Int {
            if (offset >= bytes.size) fail()
            return bytes[offset++].toInt() and 0xff
        }

        private fun readShort(): Int = java.nio.ByteBuffer.wrap(readBytes(2)).short.toInt() and 0xffff
        private fun readInt(): Long = java.nio.ByteBuffer.wrap(readBytes(4)).int.toLong() and 0xffff_ffffL
        private fun readLong(): Long = java.nio.ByteBuffer.wrap(readBytes(8)).long

        private fun readUtf8(value: ByteArray): String = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(value)).toString()
        } catch (_: Exception) { fail() }

        private fun fail(): Nothing = throw BackupException(error)
    }
}

internal fun BackupCborValue.MapValue.requiredUInt(key: Long, error: BackupErrorCode = BackupErrorCode.VERIFICATION_FAILED): Long =
    (values[key] as? BackupCborValue.UInt)?.value ?: throw BackupException(error)

internal fun BackupCborValue.MapValue.requiredBytes(key: Long, error: BackupErrorCode = BackupErrorCode.VERIFICATION_FAILED): ByteArray =
    (values[key] as? BackupCborValue.Bytes)?.value ?: throw BackupException(error)

internal fun BackupCborValue.MapValue.requiredText(key: Long, error: BackupErrorCode = BackupErrorCode.VERIFICATION_FAILED): String =
    (values[key] as? BackupCborValue.Text)?.value ?: throw BackupException(error)
