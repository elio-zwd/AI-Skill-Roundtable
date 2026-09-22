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
    }

    @Test
    fun backupRestore_mergesThroughRepositoryService() {
        val root = findAppRoot()
        val route = root.resolve("src/main/java/com/elio/jianyu/ui/screens/mine/BackupRoute.kt").readText()
        val service = root.resolve("src/main/java/com/elio/jianyu/data/BackupImportService.kt").readText()

        assertTrue(route.contains("repository.importBackup"))
        assertFalse(route.contains("repository.confirmArtifact("))
        assertTrue(service.contains("suspend fun JianyuRepository.importBackup"))
        assertTrue(service.contains("restored_conversation"))
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
