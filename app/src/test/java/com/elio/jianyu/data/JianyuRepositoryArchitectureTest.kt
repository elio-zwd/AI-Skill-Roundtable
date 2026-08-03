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

        assertTrue(
            "发现生产调用方绕过 Repository：${offenders.map { it.relativeTo(repositoryRoot) }}",
            offenders.isEmpty()
        )
    }

    @Test
    fun repositoryUsesSingleTransactionCoordinatorWithoutNetworkOrFileDeletion() {
        val transactionSource = source(
            "app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt"
        )
        val repositoryFiles = listOf(
            "app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt",
            "app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt"
        ).map(::source)

        assertTrue(transactionSource.contains("withTransaction"))
        assertTrue(transactionSource.contains("catch (error: CancellationException)"))
        assertTrue(transactionSource.indexOf("catch (error: CancellationException)") < transactionSource.indexOf("catch (error: IllegalStateException)"))
        repositoryFiles.forEach { repositorySource ->
            assertFalse(repositorySource.contains("com.elio.jianyu.network"))
            assertFalse(repositorySource.contains("Gemini"))
            assertFalse(repositorySource.contains("WorkManager"))
            assertFalse(repositorySource.contains("java.io.File"))
            assertFalse(repositorySource.contains(".delete()"))
        }
    }

    @Test
    fun publicRepositoryIsFacadeInsteadOfGodObject() {
        val facade = source("app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt")
        val lineCount = facade.lineSequence().count()

        assertTrue("公共 Repository 门面不应重新膨胀，当前行数：$lineCount", lineCount < 220)
        assertTrue(facade.contains("IssueExecutionRepositoryComponent"))
        assertTrue(facade.contains("ResourceRepositoryComponent"))
        assertTrue(facade.contains("LifecycleRecoveryRepositoryComponent"))
        assertFalse(facade.contains("withTransaction"))
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
