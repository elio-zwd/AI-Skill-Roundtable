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
        val contentInsetsIndex = appSource.indexOf(
            "contentWindowInsets = contentWindowInsets",
            startIndex = scaffoldIndex,
        )
        assertTrue("无法定位 Scaffold 根节点", scaffoldIndex >= 0)
        assertTrue("无法定位 Scaffold 参数边界", contentInsetsIndex > scaffoldIndex)

        val scaffoldRootConfiguration = appSource.substring(
            scaffoldIndex,
            contentInsetsIndex,
        )
        assertTrue(
            "App 根标签必须直接挂载在 Scaffold 根节点",
            scaffoldRootConfiguration.contains(
                ".testTag(JianyuAutomationTags.App.CONTENT_ROOT)",
            ),
        )
        assertTrue(
            "UIAutomator 标签导出必须覆盖 Scaffold content 与 bottomBar",
            scaffoldRootConfiguration.contains(
                ".semantics { testTagsAsResourceId = true }",
            ),
        )
    }

    @Test
    fun corePages_exposeStableAutomationRegions() {
        val expectations = mapOf(
            "screens/home/HomeScreen.kt" to listOf(
                "QUESTION_INPUT",
                "SAVE_ISSUE_ONLY_BUTTON",
                "RECOMMENDATION_REQUEST_BUTTON",
                "RECOMMENDATION_RESULT",
                "CONTEXT_CONFIRMED_SUMMARY",
                "FINAL_REVIEW",
                "START_ISSUE_BUTTON",
            ),
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
    fun realHomeInputReplacesTemporaryPlaceholder() {
        val contractSource = uiRoot.resolve("automation/JianyuAutomationTags.kt")
            .takeIf(File::isFile)
            ?.readText()
            .orEmpty()
        val homeRouteSource = uiRoot.resolve("screens/home/HomeRoute.kt").readText()
        val homeScreenSource = uiRoot.resolve("screens/home/HomeScreen.kt").readText()

        assertFalse(
            "首页占位标签不得保留在中央契约",
            contractSource.contains("home_question_placeholder"),
        )
        assertFalse(
            "首页占位标签不得保留在 Route",
            homeRouteSource.contains("home_question_placeholder"),
        )
        assertTrue(
            "正式首页必须落地真实问题输入标签",
            homeScreenSource.contains("HomeTestTags.QUESTION_INPUT"),
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
