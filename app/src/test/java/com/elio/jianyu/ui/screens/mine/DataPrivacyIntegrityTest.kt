package com.elio.jianyu.ui.screens.mine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataPrivacyIntegrityTest {
    private val resourceRouteSource: String by lazy {
        findRepositoryRoot()
            .resolve("app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt")
            .readText()
    }

    private val appSource: String by lazy {
        findRepositoryRoot()
            .resolve("app/src/main/java/com/elio/jianyu/ui/App.kt")
            .readText()
    }

    private val viewModelSource: String by lazy {
        findRepositoryRoot()
            .resolve("app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt")
            .readText()
    }

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
        assertTrue(source.contains("DocumentsContract.renameDocument"))
        assertTrue(source.contains("cleanupReadableExportDocument"))
        assertTrue(source.contains("openInputStream(temporaryUri)"))
        assertTrue(source.contains("未发布不完整文件"))
    }

    @Test
    fun copiedMaterialFilesDoNotRetainPersistableSourceUriPermission() {
        assertFalse(resourceRouteSource.contains("takePersistableUriPermission"))
        assertFalse(resourceRouteSource.contains("FLAG_GRANT_READ_URI_PERMISSION"))
    }

    @Test
    fun deleteAllReportsSuccessOnlyAfterEveryOwnedStoreIsCleared() {
        listOf(
            "AiManager.configuration(context).reset()",
            "AiManager.keys(context, provider).clear()",
            "TelemetryRepository.clearAllTelemetry(context)",
            "CloudInteractionSettings.setEnabled(context, false)",
            "AppPreferences.reset(context)",
            "officialSkillPreferencesCleared",
            "conversationPreferencesCleared",
            "SnapshotCatalog.clearAll(context)",
            "AndroidKeystoreSnapshotKeyProvider().deleteExisting()",
            "cancelAppOwnedWork(context)",
            "AudioPlaybackManager.stopAudio()",
            "clearAppOwnedAudioFiles(context)",
        ).forEach { marker ->
            assertTrue("删除全部数据缺少清理步骤：$marker", source.contains(marker))
        }
        assertTrue(source.contains("BackupOperationGate.forContext(context).withWriteLock"))
        assertTrue(source.contains("cleanupSucceeded"))
        assertTrue(source.contains("设备快照"))
        val mineBlock = appSource.substringAfter("mineContent = {").substringBefore("settingsContent = {")
        val dataPrivacyBlock = appSource.substringAfter("dataPrivacyContent = {").substringBefore("backupContent = {")
        assertFalse(mineBlock.contains("officialSkillPreferences ="))
        assertFalse(mineBlock.contains("onPrepareForLocalDataDeletion ="))
        assertFalse(mineBlock.contains("onClearConversationPreferences ="))
        assertTrue(dataPrivacyBlock.contains("officialSkillPreferences = officialSkillPreferences"))
        assertTrue(
            dataPrivacyBlock.contains(
                "onPrepareForLocalDataDeletion = viewModel::prepareForLocalDataDeletion",
            ),
        )
        assertTrue(
            dataPrivacyBlock.contains(
                "onClearConversationPreferences = viewModel::clearLocalPreferencesAfterDataDeletion",
            ),
        )
        assertTrue(viewModelSource.contains("cancelAndJoin()"))
        assertTrue(viewModelSource.contains("prefs.edit().clear().commit()"))
        assertTrue(viewModelSource.contains("conversationPreferences.clearAll()"))
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
