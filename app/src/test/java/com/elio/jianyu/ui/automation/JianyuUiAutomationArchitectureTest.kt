package com.elio.jianyu.ui.automation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuUiAutomationArchitectureTest {
    private val sourceRoot: File by lazy(::findMainSourceRoot)
    private val uiRoot: File
        get() = sourceRoot.resolve("com/elio/jianyu/ui")

    @Test
    fun centralizedContract_isPresentAndExportedToUiAutomator() {
        val contractFile = uiRoot.resolve("automation/JianyuAutomationTags.kt")
        assertTrue("缺少统一的见域 UI 自动化标签契约", contractFile.isFile)

        val contractSource = contractFile.takeIf(File::isFile)?.readText().orEmpty()
        val appSource = uiRoot.resolve("App.kt").readText()
        assertTrue(contractSource.contains("object JianyuAutomationTags"))
        assertTrue(contractSource.contains("jianyu_app_content_root"))

        val scaffoldIndex = appSource.indexOf("Scaffold(")
        val rootTagIndex = appSource.indexOf(
            ".testTag(JianyuAutomationTags.App.CONTENT_ROOT)",
        )
        val exportIndex = appSource.indexOf(
            ".semantics { testTagsAsResourceId = true }",
        )
        assertTrue("App 根标签必须挂载在 Scaffold 及底部导航的共同祖先上", rootTagIndex >= 0)
        assertTrue("UIAutomator 标签导出必须挂载在 Scaffold 及底部导航的共同祖先上", exportIndex >= 0)
        assertTrue("无法定位 Scaffold 根节点", scaffoldIndex >= 0)
        assertTrue("App 根标签不得仅挂载在 Scaffold content 子树", rootTagIndex < scaffoldIndex)
        assertTrue("UIAutomator 标签导出不得遗漏 Scaffold bottomBar", exportIndex < scaffoldIndex)
    }

    @Test
    fun corePages_exposeStableAutomationRegions() {
        val expectations = mapOf(
            "screens/resources/ResourcesScreen.kt" to listOf(
                "MATERIALS_CONTENT",
                "PERSONAL_CONTEXT_CONTENT",
            ),
            "screens/execution/IssueExecutionScreen.kt" to listOf(
                "PARTICIPANTS",
            ),
            "screens/context/ContextConfirmationComponents.kt" to listOf(
                "VALIDATION_ERRORS",
            ),
        )

        expectations.forEach { (path, symbols) ->
            val source = uiRoot.resolve(path).readText()
            symbols.forEach { symbol ->
                assertTrue("$path 缺少稳定自动化区域：$symbol", source.contains(symbol))
            }
        }
    }

    @Test
    fun temporaryHomePlaceholder_isNotPromotedToFrozenContract() {
        val contractSource = uiRoot.resolve("automation/JianyuAutomationTags.kt")
            .takeIf(File::isFile)
            ?.readText()
            .orEmpty()

        assertFalse(
            "PR09-06 前的首页占位标签不得升级为长期自动化契约",
            contractSource.contains("home_question_placeholder"),
        )
    }

    private fun findMainSourceRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            val moduleSource = current.resolve("src/main/java")
            if (moduleSource.resolve("com/elio/jianyu/MainActivity.kt").isFile) {
                return moduleSource
            }

            val repositorySource = current.resolve("app/src/main/java")
            if (repositorySource.resolve("com/elio/jianyu/MainActivity.kt").isFile) {
                return repositorySource
            }
            current = current.parentFile
        }
        error("无法定位 app/src/main/java")
    }
}
