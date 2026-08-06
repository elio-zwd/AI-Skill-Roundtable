package com.elio.jianyu.lifecycle

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueTrashRetentionArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    @Test
    fun lifecycleSchemaHasNoTrashExpirationOrAutomaticPurgeSchedule() {
        val entities = source(
            "app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Entities.kt",
        )
        val migration = source(
            "app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Migration.kt",
        )
        val scheduler = source(
            "app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeScheduler.kt",
        )

        listOf("expiresAt", "expirationAt", "autoPurgeAt", "retentionDays", "ttl").forEach { token ->
            assertFalse("生命周期 Schema 不得包含自动过期字段：$token", entities.contains(token))
            assertFalse("Migration 不得引入自动过期字段：$token", migration.contains(token))
        }
        assertFalse(scheduler.contains("PeriodicWorkRequest"))
        assertFalse(scheduler.contains("enqueueUniquePeriodicWork"))
        assertTrue(scheduler.contains("OneTimeWorkRequestBuilder"))
        assertTrue(scheduler.contains("ExistingWorkPolicy.KEEP"))
    }

    @Test
    fun lowStorageMonitorIsReadOnlyAndCannotDeleteFilesOrSchedulePurge() {
        val monitor = source(
            "app/src/main/java/com/elio/jianyu/lifecycle/IssueTrashStorageMonitor.kt",
        )
        val dialog = source(
            "app/src/main/java/com/elio/jianyu/ui/screens/issues/IssueLifecycleDialogs.kt",
        )

        assertTrue(monitor.contains("StatFs"))
        assertTrue(monitor.contains("walkTopDown"))
        assertFalse(monitor.contains(".delete()"))
        assertFalse(monitor.contains("deleteRecursively"))
        assertFalse(monitor.contains("WorkManager"))
        assertFalse(monitor.contains("IssuePurgeScheduler"))
        assertTrue(dialog.contains("系统不会自动清理议题或 Orphan"))
        assertTrue(dialog.contains("手动管理"))
    }

    @Test
    fun appStartupOnlyRecoversPersistedPurgeOperationsAndNeverCleansTrash() {
        val runtime = source("app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt")
        val coordinator = source(
            "app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeCoordinator.kt",
        )

        assertTrue(runtime.contains("recoverPendingOperations"))
        assertFalse(runtime.contains("clearTrash"))
        assertFalse(runtime.contains("purgeExpired"))
        assertFalse(runtime.contains("deleteOrphan"))
        assertTrue(coordinator.contains("listRecoverableIssuePurgeOperations"))
        assertTrue(coordinator.contains("if (operation.state == IssuePurgeState.FAILED_RETRYABLE) continue"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()
}
