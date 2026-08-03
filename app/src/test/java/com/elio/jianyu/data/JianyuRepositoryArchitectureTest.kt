package com.elio.jianyu.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuRepositoryArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    @Test
    fun roomRemainsVersionSevenWithoutNewSchema() {
        val databaseSource = source("app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt")

        assertTrue(databaseSource.contains("version = 7"))
        assertFalse(databaseSource.contains("version = 8"))
        assertFalse(
            File(
                repositoryRoot,
                "app/schemas/com.elio.jianyu.data.RoundtableDatabase/8.json"
            ).exists()
        )
    }

    @Test
    fun viewModelsAndUiCannotAccessDomainDaosDirectly() {
        val guardedRoots = listOf(
            File(repositoryRoot, "app/src/main/java/com/elio/jianyu/viewmodel"),
            File(repositoryRoot, "app/src/main/java/com/elio/jianyu/ui")
        )

        val offenders = guardedRoots
            .filter(File::exists)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { file ->
                val text = file.readText()
                text.contains("coreDomainDao()") ||
                    text.contains("resourceLifecycleDao()") ||
                    text.contains("jianyuRepositoryDao()")
            }

        assertTrue("发现生产调用方绕过 Repository：${offenders.map { it.relativeTo(repositoryRoot) }}", offenders.isEmpty())
    }

    @Test
    fun repositoryHasDatabaseTransactionsWithoutNetworkOrFileDeletion() {
        val repositorySource = source(
            "app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt"
        )

        assertTrue(repositorySource.contains("withTransaction"))
        assertFalse(repositorySource.contains("com.elio.jianyu.network"))
        assertFalse(repositorySource.contains("Gemini"))
        assertFalse(repositorySource.contains("WorkManager"))
        assertFalse(repositorySource.contains("java.io.File"))
        assertFalse(repositorySource.contains(".delete()"))
    }

    @Test
    fun newDomainMessageInsertUsesAbortInsteadOfReplace() {
        val daoSource = source("app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt")
        val insertStart = daoSource.indexOf("suspend fun insertDomainMessage")
        val annotationStart = daoSource.lastIndexOf("@Insert", insertStart)
        val annotation = daoSource.substring(annotationStart, insertStart)

        assertTrue(annotation.contains("OnConflictStrategy.ABORT"))
        assertFalse(annotation.contains("OnConflictStrategy.REPLACE"))
    }

    private fun source(relativePath: String): String {
        return File(repositoryRoot, relativePath).readText()
    }
}
