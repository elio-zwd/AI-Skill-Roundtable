package com.elio.jianyu.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSnapshotCleanupTest {
    @Test
    fun walCheckpointResultFailsClosedWhenPragmaReturnsNoRow() {
        val root = generateSequence(File(".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
        val source = File(
            root,
            "app/src/main/java/com/elio/jianyu/backup/DeviceSnapshotService.kt",
        ).readText()

        assertTrue(source.contains("PRAGMA wal_checkpoint(TRUNCATE)"))
        assertTrue(
            source.contains(
                "cursor.moveToFirst() && cursor.columnCount >= 3 && cursor.getInt(0) == 0",
            ),
        )
        assertFalse(source.contains("!cursor.moveToFirst() || cursor.getInt(0) == 0"))
    }

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
