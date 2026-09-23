package com.elio.jianyu.ui.screens.mine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataPrivacyIntegrityTest {
    private val source: String by lazy {
        findRepositoryRoot()
            .resolve("app/src/main/java/com/elio/jianyu/ui/screens/mine/DataPrivacyRoute.kt")
            .readText()
    }

    @Test
    fun readableExportFailsClosedInsteadOfSilentlyDroppingRepositoryFailures() {
        assertTrue(source.contains("requireExportSuccess("))
        assertFalse(source.contains(".valueOrNull().orEmpty()"))
        assertFalse(source.contains("return@mapNotNull null"))
    }

    @Test
    fun deleteAllReportsSuccessOnlyAfterEveryOwnedStoreIsCleared() {
        listOf(
            "AiManager.configuration(context).reset()",
            "AiManager.keys(context, provider).clear()",
            "TelemetryRepository.clearAllTelemetry(context)",
            "CloudInteractionSettings.setEnabled(context, false)",
            "AppPreferences.reset(context)",
            "SnapshotCatalog.clearAll(context)",
            "AndroidKeystoreSnapshotKeyProvider().deleteExisting()",
            "clearAppOwnedAudioFiles(context)",
        ).forEach { marker ->
            assertTrue("删除全部数据缺少清理步骤：$marker", source.contains(marker))
        }
        assertTrue(source.contains("BackupOperationGate.forContext(context).withWriteLock"))
        assertTrue(source.contains("cleanupSucceeded"))
        assertTrue(source.contains("设备快照"))
    }

    private fun findRepositoryRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            if (current.resolve("settings.gradle.kts").isFile) return current
            current = current.parentFile
        }
        error("无法定位仓库根目录")
    }
}
