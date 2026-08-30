package com.elio.jianyu.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiArchitectureGuardrailTest {
    private val sourceRoot: File by lazy(::findMainSourceRoot)
    private val packageRoot: File
        get() = sourceRoot.resolve("com/elio/jianyu")
    private val uiRoot: File
        get() = packageRoot.resolve("ui")

    @Test
    fun mainActivity_remainsAThinAndroidEntryPoint() {
        val source = packageRoot.resolve("MainActivity.kt").readText()

        assertTrue("MainActivity 应保持在约 80 行以内", source.lineSequence().count() <= 80)
        assertTrue(source.contains("SkillRoundtableTheme"))
        assertTrue(source.contains("MainAppContent"))
        assertFalse(source.contains("ui.screens."))
        assertFalse(source.contains("RoundtableViewModel"))
    }

    @Test
    fun app_usesRouteEntriesWithoutPageSpecificDialogsOrToasts() {
        val source = uiRoot.resolve("App.kt").readText()
        val requiredRoutes = listOf(
            "DialogRoute",
            "AiManagementRoute",
            "TelemetryRoute",
        )
        requiredRoutes.forEach { route -> assertTrue("App.kt 缺少 $route", source.contains(route)) }

        val forbidden = listOf(
            "AddEditCharacterDialog",
            "CharacterHallScreen",
            "AudioLibraryScreen",
            "AiManagementScreen",
            "ApiTelemetryScreen",
            "android.widget.Toast",
        )
        forbidden.forEach { symbol ->
            assertFalse("App.kt 不应保留页面专属实现 $symbol", source.contains(symbol))
        }
    }

    @Test
    fun routeFiles_doNotKeepCompatibilityScreenFacades() {
        val forbiddenDeclarations = mapOf(
            "screens/dialog/DialogRoute.kt" to "fun DialogScreen(",
            "screens/settings/AiManagementRoute.kt" to "fun AiManagementScreen(",
            "screens/settings/TelemetryRoute.kt" to "fun ApiTelemetryScreen(",
        )

        forbiddenDeclarations.forEach { (relativePath, declaration) ->
            val source = uiRoot.resolve(relativePath).readText()
            assertFalse("$relativePath 仍保留旧兼容入口", source.contains(declaration))
        }
    }

    @Test
    fun onlyRouteFiles_dependOnRoundtableViewModel() {
        val violations = uiRoot.resolve("screens")
            .walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" && !file.name.endsWith("Route.kt") }
            .filter { file -> file.readText().contains("RoundtableViewModel") }
            .map { file -> file.relativeTo(uiRoot).invariantSeparatorsPath }
            .toList()

        assertTrue("纯 Screen/Components 不应依赖 ViewModel: $violations", violations.isEmpty())
    }

    @Test
    fun pageDomains_doNotImportOtherPageInternals() {
        val domains = setOf("dialog", "issues", "resources", "settings", "skills")
        val importPattern = Regex(
            "import com\\.elio\\.jianyu\\.ui\\.screens\\.([a-z]+)\\.",
        )
        val violations = mutableListOf<String>()

        domains.forEach { domain ->
            uiRoot.resolve("screens/$domain")
                .walkTopDown()
                .filter { file -> file.isFile && file.extension == "kt" }
                .forEach { file ->
                    importPattern.findAll(file.readText()).forEach { match ->
                        val importedDomain = match.groupValues[1]
                        if (importedDomain != domain) {
                            violations += "${file.relativeTo(uiRoot).invariantSeparatorsPath} -> $importedDomain"
                        }
                    }
                }
        }

        assertTrue("页面域存在跨域内部引用: $violations", violations.isEmpty())
    }

    @Test
    fun navigationAndTheme_haveNoPageSpecificImports() {
        val violations = listOf("navigation", "theme")
            .flatMap { directory ->
                uiRoot.resolve(directory)
                    .walkTopDown()
                    .filter { file -> file.isFile && file.extension == "kt" }
                    .filter { file -> file.readText().contains("ui.screens.") }
                    .map { file -> file.relativeTo(uiRoot).invariantSeparatorsPath }
                    .toList()
            }

        assertTrue("navigation/theme 不应包含页面专属引用: $violations", violations.isEmpty())
    }

    @Test
    fun legacyTokens_areAliasesRatherThanDuplicateColorDefinitions() {
        val source = uiRoot.resolve("LegacyUiTokens.kt").readText()

        assertFalse("兼容 Token 不得重复定义颜色值", source.contains("Color("))
        listOf(
            "SlateBackground",
            "CardBackground",
            "BrandPrimary",
            "BrandSecondary",
            "BrandGold",
            "AppTextPrimary",
            "AppTextSecondary",
        ).forEach { token -> assertTrue("缺少主题别名 $token", source.contains(token)) }
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
