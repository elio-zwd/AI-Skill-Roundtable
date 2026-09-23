package com.elio.jianyu.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceSnapshotCleanupTest {
    @Test
    fun failedSnapshotCleanupRemovesTemporaryAndPublishedFiles() {
        val directory = Files.createTempDirectory("jianyu-snapshot-cleanup").toFile()
        try {
            val temporary = File(directory, "snapshot.jysnap.part").apply { writeText("partial") }
            val published = File(directory, "snapshot.jysnap").apply { writeText("verified-but-unpublished") }

            cleanupFailedSnapshotArtifacts(
                temporaryFile = temporary,
                finalFile = published,
                cause = IllegalStateException("after_reopen_failed"),
            )

            assertFalse(temporary.exists())
            assertFalse(published.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
