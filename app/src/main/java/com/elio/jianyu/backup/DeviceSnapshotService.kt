package com.elio.jianyu.backup

import android.content.Context
import com.elio.jianyu.BuildConfig
import com.elio.jianyu.JianyuAppRuntime
import com.elio.jianyu.JianyuAppRuntimeProvider
import com.elio.jianyu.audio.assets.AudioFileResolution
import com.elio.jianyu.audio.assets.AudioFileStore
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.runtime.DatabaseMaintenanceOutcome
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

data class DeviceSnapshotResult(
    val snapshotId: String,
    val file: File,
    val generation: Long,
)

private data class SnapshotAudioSource(
    val id: String,
    val file: File,
    val mimeType: String,
    val expectedSize: Long,
)

/** Device-bound snapshot creation. Import/replacement is intentionally not part of PR09-13B. */
class DeviceSnapshotService(
    private val context: Context,
    private val gate: BackupOperationGate = BackupOperationGate.forContext(context),
    private val keyProvider: SnapshotWrappingKeyProvider = AndroidKeystoreSnapshotKeyProvider(),
) {
    private val audioFileStore = AudioFileStore(File(context.applicationContext.filesDir, "jianyu-audio"))

    suspend fun createSnapshot(snapshotId: String = UUID.randomUUID().toString()): DeviceSnapshotResult =
        gate.withWriteLock {
            val safeId = snapshotId.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,80}")) }
                ?: throw BackupException(BackupErrorCode.PATH_INVALID)
            val key = try { keyProvider.getOrCreate() } catch (error: BackupException) { throw error } catch (error: Throwable) {
                throw BackupException(BackupErrorCode.SNAPSHOT_KEY_UNAVAILABLE, error)
            }
            val snapshotDirectory = File(context.noBackupFilesDir, "jianyu-backup/snapshots")
            if (!snapshotDirectory.isDirectory && !snapshotDirectory.mkdirs()) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
            val finalFile = File(snapshotDirectory, "$safeId${BackupProtocol.snapshotExtension}")
            val temporaryFile = File(snapshotDirectory, "$safeId${BackupProtocol.snapshotExtension}.part")
            if (finalFile.exists()) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
            var audioSources = emptyList<SnapshotAudioSource>()
            try {
                val outcome = JianyuAppRuntimeProvider.withDatabaseClosed(
                    context = context.applicationContext,
                    beforeClose = { database ->
                        audioSources = preflightAndCollectAudio(database)
                    },
                    whileClosed = { databaseFile ->
                        writeAndVerifySnapshot(
                            key = key,
                            temporaryFile = temporaryFile,
                            finalFile = finalFile,
                            snapshotId = safeId,
                            databaseFile = databaseFile,
                            audioSources = audioSources,
                        )
                    },
                    afterReopen = { runtime -> verifyReopenedDatabase(runtime) },
                )
                when (outcome) {
                    is DatabaseMaintenanceOutcome.Success -> {
                        val result = DeviceSnapshotResult(safeId, finalFile, outcome.generation)
                        SnapshotCatalog.publish(context.applicationContext, result)
                        result
                    }
                    is DatabaseMaintenanceOutcome.Failure -> {
                        cleanup(temporaryFile)
                        throw mapMaintenanceFailure(outcome)
                    }
                }
            } catch (error: CancellationException) {
                cleanup(temporaryFile)
                throw BackupException(BackupErrorCode.OPERATION_CANCELED, error)
            } catch (error: BackupException) {
                cleanup(temporaryFile)
                throw error
            } catch (error: Throwable) {
                cleanup(temporaryFile)
                throw BackupException(BackupErrorCode.VERIFICATION_FAILED, error)
            }
        }

    private fun preflightAndCollectAudio(database: RoundtableDatabase): List<SnapshotAudioSource> {
        val sqlite = database.openHelper.writableDatabase
        fun count(sql: String): Long = sqlite.query(sql).use { cursor ->
            if (!cursor.moveToFirst()) 0L else cursor.getLong(0)
        }
        val integrity = sqlite.query("PRAGMA integrity_check").use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
        }
        if (!integrity) throw BackupException(BackupErrorCode.DATABASE_INTEGRITY_FAILED)
        if (count("SELECT COUNT(*) FROM execution_runs WHERE status IN ('running','partial_success','retryable')") > 0L ||
            count("SELECT COUNT(*) FROM messages WHERE isPending = 1") > 0L
        ) throw BackupException(BackupErrorCode.ACTIVE_WORK_IN_PROGRESS)
        if (count("SELECT COUNT(*) FROM audio_assets WHERE fileState = 'pending'") > 0L) {
            throw BackupException(BackupErrorCode.ACTIVE_WORK_IN_PROGRESS)
        }
        if (count("SELECT COUNT(*) FROM issue_purge_operations WHERE state IN ('requested','waiting_for_tasks','canceling_tasks','deleting_files','ready_for_database_purge','database_purging','failed_retryable')") > 0L) {
            throw BackupException(BackupErrorCode.PURGE_IN_PROGRESS)
        }
        if (count(
                "SELECT COUNT(*) FROM chat_sessions AS s WHERE NOT EXISTS (" +
                    "SELECT 1 FROM issues AS i WHERE i.legacyChatSessionId = s.id)",
            ) > 0L ||
            count("SELECT COUNT(*) FROM messages WHERE issueId IS NULL OR stageId IS NULL") > 0L
        ) throw BackupException(BackupErrorCode.UNSUPPORTED_LEGACY_DATA)
        val checkpoint = sqlite.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            !cursor.moveToFirst() || cursor.getInt(0) == 0
        }
        if (!checkpoint) throw BackupException(BackupErrorCode.DATABASE_CHECKPOINT_FAILED)

        return sqlite.query(
            "SELECT id, storagePath, mimeType, sizeBytes FROM audio_assets " +
                "WHERE fileState = 'available' AND deletedAt IS NULL AND purgeRequestedAt IS NULL",
        ).use { cursor ->
            val result = mutableListOf<SnapshotAudioSource>()
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val path = cursor.getString(1)
                val mime = cursor.getString(2)
                val expectedSize = cursor.getLong(3)
                val resolution = audioFileStore.resolve(path)
                val file = (resolution as? AudioFileResolution.Available)?.file
                    ?: throw BackupException(BackupErrorCode.SOURCE_CHANGED)
                result += SnapshotAudioSource(id, file, mime, expectedSize)
            }
            result
        }
    }

    private fun writeAndVerifySnapshot(
        key: SecretKey,
        temporaryFile: File,
        finalFile: File,
        snapshotId: String,
        databaseFile: File,
        audioSources: List<SnapshotAudioSource>,
    ): DeviceSnapshotResult {
        if (!databaseFile.isFile) throw BackupException(BackupErrorCode.SOURCE_CHANGED)
        val blobs = mutableListOf<BackupBlobRecord>()
        blobs += BackupBlobRecord("db-main", "database", "application/vnd.sqlite3", databaseFile.readBytes())
        audioSources.forEach { source ->
            if (!source.file.isFile || source.file.length() != source.expectedSize) throw BackupException(BackupErrorCode.SOURCE_CHANGED)
            blobs += BackupBlobRecord("audio-${source.id}", "audio_asset", source.mimeType, source.file.readBytes())
        }
        val input = SnapshotBackupInput(
            manifest = BackupManifest(
                formatId = BackupProtocol.snapshotFormatId,
                createdAt = System.currentTimeMillis(),
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                sourceRoomVersion = 14L,
                logicalEntryCount = 0L,
                blobCount = blobs.size.toLong(),
                backupScope = listOf("database_main", "audio_available"),
            ),
            entities = emptyList(),
            blobs = blobs,
        )
        val encrypted = BackupEnvelopeWriter.createSnapshot(key, input)
        temporaryFile.parentFile?.mkdirs()
        FileOutputStream(temporaryFile).use { output ->
            output.write(encrypted)
            output.flush()
            output.fd.sync()
        }
        val verifiedPlaintext = BackupCrypto.decryptSnapshot(key, temporaryFile.readBytes())
        BackupRecordStream.verify(verifiedPlaintext, BackupProtocol.snapshotFormatId)
        if (!temporaryFile.renameTo(finalFile)) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        return DeviceSnapshotResult(snapshotId, finalFile, 0L)
    }

    private fun verifyReopenedDatabase(runtime: JianyuAppRuntime) {
        val sqlite = runtime.database.openHelper.writableDatabase
        sqlite.query("SELECT 1").use { cursor -> if (!cursor.moveToFirst()) throw BackupException(BackupErrorCode.DATABASE_INTEGRITY_FAILED) }
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            if (cursor.moveToFirst()) throw BackupException(BackupErrorCode.DATABASE_INTEGRITY_FAILED)
        }
    }

    private fun mapMaintenanceFailure(outcome: DatabaseMaintenanceOutcome.Failure): BackupException = when (outcome.stage) {
        com.elio.jianyu.runtime.DatabaseMaintenanceStage.BEFORE_CLOSE ->
            if (outcome.cause is BackupException) outcome.cause as BackupException else BackupException(BackupErrorCode.DATABASE_CHECKPOINT_FAILED, outcome.cause)
        com.elio.jianyu.runtime.DatabaseMaintenanceStage.WHILE_CLOSED ->
            if (outcome.cause is BackupException) outcome.cause as BackupException else BackupException(BackupErrorCode.VERIFICATION_FAILED, outcome.cause)
        com.elio.jianyu.runtime.DatabaseMaintenanceStage.AFTER_REOPEN,
        com.elio.jianyu.runtime.DatabaseMaintenanceStage.REOPEN,
        com.elio.jianyu.runtime.DatabaseMaintenanceStage.CLOSE,
        com.elio.jianyu.runtime.DatabaseMaintenanceStage.QUIESCE -> BackupException(BackupErrorCode.DATABASE_INTEGRITY_FAILED, outcome.cause)
    }

    private fun cleanup(file: File) {
        if (file.exists()) file.delete()
    }
}


/**
 * Snapshot 会主动切换 Runtime 世代，不能由会随旧 UI 一起销毁的 Compose scope 拥有。
 * 调用方可以取消等待，但实际维护操作继续由应用级 scope 完成闭库、重开和校验。
 */
object DeviceSnapshotOperations {
    private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun createSnapshot(context: Context): DeviceSnapshotResult {
        val applicationContext = context.applicationContext
        val operation = operationScope.async {
            DeviceSnapshotService(applicationContext).createSnapshot()
        }
        return operation.await()
    }
}
