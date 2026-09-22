package com.elio.jianyu.backup

import android.content.Context
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The single process/external-process gate for backup and snapshot operations.
 * Business writers take the read side; backup and snapshot writers take the write side.
 */
class BackupOperationGate internal constructor(private val lockFile: java.io.File) {
    private val processLock = ReentrantReadWriteLock(true)

    suspend fun <T> withWriteLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        processLock.writeLock().lock()
        var file: RandomAccessFile? = null
        var channel: FileChannel? = null
        var held: java.nio.channels.FileLock? = null
        try {
            lockFile.parentFile?.mkdirs()
            file = RandomAccessFile(lockFile, "rw")
            channel = file.channel
            held = try {
                channel!!.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (held == null) throw BackupException(BackupErrorCode.OPERATION_ALREADY_RUNNING)
            block()
        } finally {
            runCatching { held?.release() }
            runCatching { channel?.close() }
            runCatching { file?.close() }
            processLock.writeLock().unlock()
        }
    }

    suspend fun <T> withReadLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        processLock.readLock().lock()
        try { block() } finally { processLock.readLock().unlock() }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, BackupOperationGate>()

        fun forContext(context: Context): BackupOperationGate {
            val directory = java.io.File(context.noBackupFilesDir, "jianyu-backup")
            val file = java.io.File(directory, "operation.lock").absoluteFile
            return instances.computeIfAbsent(file.path.lowercase()) { BackupOperationGate(file) }
        }
    }
}
