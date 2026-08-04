package com.elio.jianyu.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuNavigationArchitectureTest {
    private val sourceRoot: File by lazy(::findMainSourceRoot)
    private val packageRoot: File
        get() = sourceRoot.resolve("com/elio/jianyu")
    private val uiRoot: File
        get() = packageRoot.resolve("ui")

    @Test
    fun app_assemblesAllJianyuRoutesAndKeepsLegacyCompatibilityEntries() {
        val source = uiRoot.resolve("App.kt").readText()
        listOf(
            "HomeRoute",
            "IssuesRoute",
            "IssueExecutionRoute",
            "ResourcesRoute",
            "SettingsRoute",
            "OfficialSkillNavigationRoute",
            "RoundtableRoute",
            "CharacterHallRoute",
            "AudioLibraryRoute",
            "ApiKeyManagerRoute",
            "TelemetryRoute",
        ).forEach { route ->
            assertTrue("App.kt 缺少 Route：$route", source.contains(route))
        }

        listOf(
            "chatDao(",
            "coreDomainDao(",
            "resourceLifecycleDao(",
            "jianyuRepositoryDao(",
            "saveIssue(",
            "createStage(",
            "createExecutionRun(",
            "confirmArtifact(",
        ).forEach { forbidden ->
            assertFalse("App.kt 不得直接执行数据操作：$forbidden", source.contains(forbidden))
        }
    }

    @Test
    fun appRuntime_sharesCatalogValidatorWithTheSingleJianyuRepository() {
        val runtimeSource = packageRoot.resolve("JianyuAppRuntime.kt").readText()
        val appSource = uiRoot.resolve("App.kt").readText()
        val issuesViewModelSource = uiRoot.resolve("screens/issues/IssuesViewModel.kt").readText()

        assertTrue(runtimeSource.contains("createOfficialSkillCatalogRuntime(context)"))
        assertTrue(runtimeSource.contains("officialSkillIdValidator = catalogRuntimeResult.runtime.validator"))
        assertTrue(runtimeSource.contains("is OfficialSkillCatalogRuntimeResult.Failure -> RoomJianyuRepository"))
        assertTrue(appSource.contains("repository = appRuntime.repository"))
        assertTrue(appSource.contains("runtimeResult = appRuntime.officialSkillCatalogRuntimeResult"))
        assertFalse(issuesViewModelSource.contains("RoundtableDatabase"))
        assertFalse(issuesViewModelSource.contains("RoomJianyuRepository"))
    }

    @Test
    fun placeholderSkillImplementation_isRemovedAfterOfficialCatalogIntegration() {
        assertFalse(uiRoot.resolve("screens/skillplaceholder").exists())
        assertTrue(uiRoot.resolve("screens/skills/OfficialSkillNavigationRoute.kt").isFile)
    }

    @Test
    fun navigationPackage_containsOnlyRouteContractsAndBackStackLogic() {
        val violations = uiRoot.resolve("navigation")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val source = file.readText()
                source.contains("ui.screens.") ||
                    source.contains("RoundtableDatabase") ||
                    source.contains("Dao") ||
                    source.contains("JianyuRepository")
            }
            .map { it.relativeTo(uiRoot).invariantSeparatorsPath }
            .toList()

        assertTrue("navigation/ 不得依赖页面或数据实现：$violations", violations.isEmpty())
    }

    @Test
    fun navigationShellScreens_doNotAccessDaosOrRepositoryWriteMethods() {
        val domains = listOf("home", "issues", "resources", "settings")
        val forbidden = listOf(
            ".chatDao(",
            ".characterDao(",
            ".coreDomainDao(",
            ".resourceLifecycleDao(",
            ".jianyuRepositoryDao(",
            ".saveIssue(",
            ".createStage(",
            ".createExecutionRun(",
            ".appendDomainMessage(",
            ".transitionRun(",
            ".confirmArtifact(",
            ".recordMaterialUsage(",
            ".recordPersonalContextUsage(",
            ".archiveIssue(",
            ".moveIssueToTrash(",
        )
        val violations = mutableListOf<String>()

        domains.forEach { domain ->
            uiRoot.resolve("screens/$domain")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val source = file.readText()
                    forbidden.forEach { symbol ->
                        if (source.contains(symbol)) {
                            violations += "${file.relativeTo(uiRoot).invariantSeparatorsPath}: $symbol"
                        }
                    }
                }
        }

        assertTrue("导航壳页面不得访问 DAO 或写接口：$violations", violations.isEmpty())
    }

    @Test
    fun navigationTestTags_areStableSemanticNames() {
        val appSource = uiRoot.resolve("App.kt").readText()
        val shellSource = uiRoot.resolve("components/JianyuPageShell.kt").readText()
        val resourcesSource = uiRoot.resolve("screens/resources/ResourcesRoute.kt").readText()
        val skillNavigationSource = uiRoot.resolve("screens/skills/OfficialSkillNavigationRoute.kt")
            .readText()

        assertTrue(appSource.contains("app_bottom_navigation"))
        listOf("home", "issues", "skills", "resources").forEach { suffix ->
            assertTrue(
                "缺少稳定一级目的地标签后缀：$suffix",
                uiRoot.resolve("navigation/AppDestination.kt").readText()
                    .contains("testTagSuffix = \"$suffix\""),
            )
        }
        assertTrue(shellSource.contains("global_settings_button"))
        assertTrue(skillNavigationSource.contains("GLOBAL_SETTINGS_BUTTON"))
        assertTrue(skillNavigationSource.contains("PAGE_BACK_BUTTON"))
        assertTrue(resourcesSource.contains("resources_tab_materials"))
        assertTrue(resourcesSource.contains("resources_tab_artifacts"))
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
