package com.elio.jianyu.backup

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SnapshotCatalogFileReplaceTest {
    @Test
    fun replacingExistingCatalogPublishesNewContentAndConsumesTemporaryFile() {
        val directory = Files.createTempDirectory("jianyu-snapshot-catalog-test").toFile()
        try {
            val target = directory.resolve("index.json").apply { writeText("old") }
            val temporary = directory.resolve("index.json.part").apply { writeText("new") }

            replaceSnapshotCatalogFile(temporary, target)

            assertEquals("new", target.readText())
            assertFalse(temporary.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
