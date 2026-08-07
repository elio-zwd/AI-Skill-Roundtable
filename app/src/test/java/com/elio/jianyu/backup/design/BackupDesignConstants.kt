package com.elio.jianyu.backup.design

/**
 * PR09-13A design prototype.
 *
 * Not a production API and not used by the app runtime.
 */
internal object BackupDesignConstants {
    val PORTABLE_MAGIC: ByteArray = byteArrayOf(
        0x4a, 0x59, 0x42, 0x4b, 0x50, 0x0d, 0x0a, 0x1a,
    )
    val SNAPSHOT_MAGIC: ByteArray = byteArrayOf(
        0x4a, 0x59, 0x53, 0x4e, 0x50, 0x0d, 0x0a, 0x1a,
    )

    const val ENVELOPE_VERSION = 1
    const val HEADER_ENCODING_DETERMINISTIC_CBOR = 1
    const val MANIFEST_VERSION = 1

    const val FORMAT_KIND_PORTABLE = 1L
    const val FORMAT_KIND_SNAPSHOT = 2L

    const val KDF_NONE = 0L
    const val KDF_ARGON2ID = 1L
    const val KDF_PROFILE_NONE = 0L
    const val KDF_PROFILE_ARGON2ID_V1 = 1L

    const val KEY_WRAP_AES_256_GCM = 1L
    const val STREAMING_AES_256_GCM_HKDF_1MB = 1L
    const val SERIALIZATION_DETERMINISTIC_CBOR_V1 = 1L
    const val SNAPSHOT_DEVICE_KEY_SLOT_V1 = 1L

    const val ARGON2_VERSION_13 = 0x13
    const val ARGON2_MEMORY_KIB = 65_536
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_PARALLELISM = 1
    const val KDF_SALT_BYTES = 16
    const val ROOT_KEY_BYTES = 32
    const val WRAP_NONCE_BYTES = 12
    const val GCM_TAG_BYTES = 16
    const val ENVELOPE_ID_BYTES = 16

    const val STREAM_DERIVED_KEY_BYTES = 32
    const val STREAM_HEADER_BYTES = 40
    const val STREAM_NONCE_PREFIX_BYTES = 7
    const val PRODUCTION_CIPHERTEXT_SEGMENT_BYTES = 1_048_576

    const val MAX_HEADER_BYTES = 4_096
    const val MAX_RECORD_BYTES = 1_048_576
    const val MAX_BLOB_CHUNK_BYTES = 262_144
    const val MAX_LOGICAL_ENTRIES = 1_000_000L
    const val MAX_BLOBS = 10_000L
    const val MAX_SINGLE_BLOB_BYTES = 68_719_476_736L
    const val MAX_TOTAL_PLAINTEXT_BYTES = 1_099_511_627_776L
    const val MAX_PASSWORD_UTF8_BYTES = 1_024

    const val RECORD_MANIFEST = 1L
    const val RECORD_ENTITY = 2L
    const val RECORD_BLOB_START = 3L
    const val RECORD_BLOB_CHUNK = 4L
    const val RECORD_BLOB_END = 5L
    const val RECORD_COMPLETE = 255L

    const val PORTABLE_FORMAT_ID = "jianyu-portable-backup/1"
    const val SNAPSHOT_FORMAT_ID = "jianyu-device-snapshot/1"
    const val PORTABLE_STREAM_INFO = "jianyu/portable-backup/v1/stream"
    const val SNAPSHOT_STREAM_INFO = "jianyu/device-snapshot/v1/stream"
    const val SNAPSHOT_KEY_ALIAS = "jianyu_backup_snapshot_wrap_v1"
    const val API_KEY_ALIAS_MUST_NOT_BE_REUSED = "skill_roundtable_api_key_v1"
}

internal enum class BackupDesignErrorCode(val storageValue: String) {
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
}

internal class BackupDesignException(
    val errorCode: BackupDesignErrorCode,
    cause: Throwable? = null,
) : Exception(errorCode.storageValue, cause)
