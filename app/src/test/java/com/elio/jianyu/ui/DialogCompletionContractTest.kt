package com.elio.jianyu.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 防止首页对话再次退回“复制/未接入”占位路径。 */
class DialogCompletionContractTest {
    @Test
    fun dialogActions_areBackedByFormalOperations() {
        val root = findAppRoot()
        val route = root.resolve("src/main/java/com/elio/jianyu/ui/screens/dialog/DialogRoute.kt").readText()
        val viewModel = root.resolve("src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt").readText()

        assertFalse(route.contains("该能力尚未接入当前对话"))
        assertFalse(route.contains("可到资料页整理为成果"))
        assertTrue(route.contains("saveMessageAsArtifact"))
        assertTrue(route.contains("loadAvailableConversationContext"))
        assertTrue(route.contains("attachTextMaterial"))
        assertTrue(viewModel.contains("ensureFormalConversation"))
        assertTrue(viewModel.contains("artifact-dialog-message-"))
        assertTrue(viewModel.contains("appendSelectedConversationContext"))
        assertTrue(viewModel.contains("prepareAndRecordConversationContextUsage"))
        assertTrue(viewModel.contains("expectedSourceHash"))
        assertTrue(viewModel.contains("issueId = formal.issueId"))
        assertFalse(viewModel.contains("retryConversationContexts"))
        assertFalse(viewModel.contains("consumePendingConversationContext"))
        assertTrue(viewModel.contains("explicitlyConfirmedConversationContextSessions"))
        assertTrue(viewModel.contains("requireExplicitConfirmation = true"))
        assertTrue(viewModel.contains("\"confirmation_required\""))
        assertTrue(route.contains("if (viewModel.confirmConversationContext(selections))"))
    }

    @Test
    fun backupRouteUsesFormalExportAndKeepsImportExplicitlyClosed() {
        val root = findAppRoot()
        val route = root.resolve("src/main/java/com/elio/jianyu/ui/screens/mine/BackupRoute.kt").readText()
        val service = root.resolve("src/main/java/com/elio/jianyu/backup/PortableBackupService.kt").readText()

        assertTrue(route.contains("PortableBackupService"))
        assertTrue(route.contains("DeviceSnapshotOperations"))
        assertTrue(route.contains("PR09-14A/14B"))
        assertTrue(service.contains("createToUri"))
        assertFalse(service.contains("importBackup"))
    }


    @Test
    fun sensitiveContextAlwaysRequiresPerRequestConfirmation() {
        val root = findAppRoot()
        val route = root.resolve("src/main/java/com/elio/jianyu/ui/screens/dialog/DialogRoute.kt").readText()

        assertFalse(route.contains("|| !appPreferences.confirmSensitiveContext"))
        assertFalse(route.contains("candidate.sensitiveConfirmed || !requireSensitiveConfirmation"))
        assertFalse(route.contains("已按设置跳过再次确认"))
        assertTrue(route.contains("it.sensitive && !it.sensitiveConfirmed"))
        assertTrue(route.contains("我已查看并确认发送敏感内容"))
        assertTrue(route.contains("敏感内容已隐藏；选中后查看并确认。"))
    }

    private fun findAppRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (true) {
            if (current.resolve("src/main/java/com/elio/jianyu/MainActivity.kt").isFile) return current
            if (current.resolve("app/src/main/java/com/elio/jianyu/MainActivity.kt").isFile) {
                return current.resolve("app")
            }
            current = current.parentFile ?: error("无法定位 app 模块")
        }
    }
}
