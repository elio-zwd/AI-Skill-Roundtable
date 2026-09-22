package com.elio.jianyu.backup

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SnapshotCatalogEntry(
    val snapshotId: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val note: String = "",
)

/** Non-sensitive index; an entry is published only after full envelope verification. */
object SnapshotCatalog {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val INDEX_NAME = "index.json"

    fun list(context: Context): List<SnapshotCatalogEntry> {
        val directory = directory(context)
        val index = File(directory, INDEX_NAME)
        val entries = if (!index.isFile) emptyList() else runCatching {
            json.decodeFromString(ListSerializer(SnapshotCatalogEntry.serializer()), index.readText())
        }.getOrDefault(emptyList())
        return entries.filter { File(directory, "${it.snapshotId}${BackupProtocol.snapshotExtension}").isFile }
            .sortedByDescending { it.createdAt }
    }

    fun publish(context: Context, result: DeviceSnapshotResult, note: String = "") {
        val entries = list(context).filterNot { it.snapshotId == result.snapshotId } + SnapshotCatalogEntry(
            snapshotId = result.snapshotId,
            createdAt = result.file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
            sizeBytes = result.file.length(),
            note = note.take(200),
        )
        write(context, entries)
    }

    fun updateNote(context: Context, snapshotId: String, note: String) {
        val updated = list(context).map { if (it.snapshotId == snapshotId) it.copy(note = note.take(200)) else it }
        write(context, updated)
    }

    fun delete(context: Context, snapshotId: String) {
        val safe = snapshotId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,80}")) }
            ?: throw BackupException(BackupErrorCode.PATH_INVALID)
        val file = File(directory(context), "$safe${BackupProtocol.snapshotExtension}")
        if (file.exists() && !file.delete()) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        write(context, list(context).filterNot { it.snapshotId == safe })
    }

    private fun write(context: Context, entries: List<SnapshotCatalogEntry>) {
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        val target = File(directory, INDEX_NAME)
        val temporary = File(directory, "$INDEX_NAME.part")
        try {
            temporary.writeText(json.encodeToString(ListSerializer(SnapshotCatalogEntry.serializer()), entries), Charsets.UTF_8)
            if (!temporary.renameTo(target)) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        } catch (error: BackupException) {
            temporary.delete(); throw error
        } catch (error: Throwable) {
            temporary.delete(); throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED, error)
        }
    }

    private fun directory(context: Context): File = File(context.noBackupFilesDir, "jianyu-backup/snapshots")
}
