package com.elio.jianyu.backup

import android.content.Context
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 协程安全的公平读写门禁。
 *
 * writer 先占用 turnstile，再等待现有 readers 排空；新 readers 会被挡在 turnstile 后，
 * 避免备份/快照长期饥饿。Mutex 不绑定线程，因此 block 挂起或切换 dispatcher 后仍可安全释放。
 */
private class SuspendReadWriteLock {
    private val turnstile = Mutex()
    private val resource = Mutex()
    private val readerState = Mutex()
    private var readers = 0

    suspend fun acquireRead() {
        turnstile.lock()
        turnstile.unlock()

        readerState.lock()
        try {
            if (readers == 0) {
                resource.lock()
            }
            readers += 1
        } finally {
            readerState.unlock()
        }
    }

    suspend fun releaseRead() {
        readerState.withLock {
            check(readers > 0) { "read lock released without acquisition" }
            readers -= 1
            if (readers == 0) {
                resource.unlock()
            }
        }
    }

    suspend fun acquireWrite() {
        turnstile.lock()
        try {
            resource.lock()
        } catch (error: Throwable) {
            turnstile.unlock()
            throw error
        }
    }

    fun releaseWrite() {
        resource.unlock()
        turnstile.unlock()
    }
}

/**
 * The single process/external-process gate for backup and snapshot operations.
 * Business writers take the read side; backup and snapshot writers take the write side.
 */
class BackupOperationGate internal constructor(private val lockFile: java.io.File) {
    private val processLock = SuspendReadWriteLock()

    suspend fun <T> withWriteLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        processLock.acquireWrite()
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
            processLock.releaseWrite()
        }
    }

    suspend fun <T> withReadLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        processLock.acquireRead()
        try {
            block()
        } finally {
            withContext(NonCancellable) {
                processLock.releaseRead()
            }
        }
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
