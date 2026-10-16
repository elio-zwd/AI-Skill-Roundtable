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
        assertTrue(viewModel.contains("questionConversationContexts[questionRunId] = contextSelections"))
        assertTrue(viewModel.contains("val requestContext = questionConversationContexts[questionRunId]"))
        assertFalse(viewModel.contains("consumePendingConversationContext"))
        assertTrue(viewModel.contains("explicitlyConfirmedConversationContextSessions"))
        assertFalse(viewModel.contains("requireExplicitConfirmation = true"))
        assertFalse(viewModel.contains("重试前请重新打开“选择资料”"))
        assertTrue(route.contains("if (viewModel.confirmConversationContext(selections))"))
        assertTrue(route.contains("networkAllowed = true"))
        assertFalse(route.contains("允许本次发送给模型服务"))
    }

    @Test
    fun referenceContentNeverFallsBackToPendingSelection() {
        val root = findAppRoot()
        val route = root.resolve("src/main/java/com/elio/jianyu/ui/screens/dialog/DialogRoute.kt").readText()

        assertTrue(route.contains("val selected = viewModel.currentActiveConversationContextSelections()"))
        assertFalse(route.contains(".ifEmpty { viewModel.currentConversationContextSelections() }"))
    }

    @Test
    fun activeReferenceContextSurvivesExecutionAndDeletedSessionStateIsCleared() {
        val root = findAppRoot()
        val viewModel = root.resolve("src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt").readText()

        val executionRetentionComment =
            "保留最近一次真正执行所用的 active context，供“本次参考内容”在回复结束后查看。"
        assertTrue(viewModel.contains(executionRetentionComment))
        assertTrue(viewModel.contains("pendingConversationContexts.remove(sessionId)"))
        assertTrue(viewModel.contains("activeConversationContexts.remove(sessionId)"))
        assertTrue(viewModel.contains("userMsgIds.forEach(questionConversationContexts::remove)"))
        assertTrue(viewModel.contains("explicitlyConfirmedConversationContextSessions.remove(sessionId)"))
        assertTrue(viewModel.contains("formalContexts.remove(sessionId)"))
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
    fun sensitiveMaterialAndPersonalContextRequirePerRequestConfirmationWhileSkillKnowledgeIsExcluded() {
        val root = findAppRoot()
        val route = root.resolve("src/main/java/com/elio/jianyu/ui/screens/dialog/DialogRoute.kt").readText()

        assertFalse(route.contains("|| !appPreferences.confirmSensitiveContext"))
        assertFalse(route.contains("candidate.sensitiveConfirmed || !requireSensitiveConfirmation"))
        assertTrue(route.contains("candidate.sensitive && !candidate.sensitiveConfirmed"))
        assertTrue(route.contains("candidate.selected && candidate.sensitive"))
        assertTrue(route.contains("checked = candidate.sensitiveConfirmed"))
        assertTrue(route.contains("onChange(candidate.copy(sensitiveConfirmed = it))"))
        assertTrue(route.contains("sensitive = false"))
        assertTrue(route.contains("sensitiveConfirmed = false"))
        assertFalse(route.contains("sensitiveConfirmed = true"))
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
