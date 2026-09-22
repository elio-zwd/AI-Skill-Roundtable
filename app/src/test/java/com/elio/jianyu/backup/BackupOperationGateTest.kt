package com.elio.jianyu.backup

import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupOperationGateTest {
    @Test
    fun fairWriteGateSerializesOperations() = runBlocking {
        val directory = Files.createTempDirectory("jianyu-gate-").toFile()
        try {
            val gate = BackupOperationGate(File(directory, "jianyu-backup/operation.lock"))
            val order = mutableListOf<Int>()
            awaitAll(
                async {
                    gate.withWriteLock {
                        order += 1
                        delay(20)
                        order += 2
                    }
                },
                async {
                    gate.withWriteLock {
                        order += 3
                        delay(20)
                        order += 4
                    }
                },
            )
            assertEquals(4, order.size)
            assertTrue(order == listOf(1, 2, 3, 4) || order == listOf(3, 4, 1, 2))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun externalFileLockIsReportedWithoutDeletingTheLockFile() = runBlocking {
        val directory = Files.createTempDirectory("jianyu-gate-").toFile()
        try {
            val lockFile = File(directory, "jianyu-backup/operation.lock").apply { parentFile?.mkdirs() }
            RandomAccessFile(lockFile, "rw").use { access ->
                val held = access.channel.lock()
                try {
                    val error = runCatching {
                        BackupOperationGate(lockFile).withWriteLock { Unit }
                    }.exceptionOrNull() as BackupException
                    assertEquals(BackupErrorCode.OPERATION_ALREADY_RUNNING, error.code)
                    assertTrue(lockFile.isFile)
                } finally {
                    held.release()
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
