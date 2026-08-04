package com.elio.jianyu.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialContextArchitectureTest {
    private val mainRoot = findMainSourceRoot()

    @Test
    fun materialContextUiDoesNotAccessDaoOrNetworkGateway() {
        val files = listOf(
            "ui/screens/resources/ResourcesRoute.kt",
            "ui/screens/resources/ResourcesViewModel.kt",
            "ui/screens/resources/ResourcesScreen.kt",
            "ui/screens/resources/ResourcesComponents.kt",
            "ui/screens/context/ContextConfirmationComponents.kt",
            "ui/screens/execution/IssueExecutionViewModel.kt",
        ).map(mainRoot::resolve)

        files.forEach { file ->
            val source = file.readText()
            assertFalse("${file.name} 不得访问 DAO", source.contains("ResourceLifecycleDao"))
            assertFalse("${file.name} 不得访问 DAO", source.contains("JianyuRepositoryDao"))
            assertFalse("${file.name} 不得调用 Gemini", source.contains("Gemini"))
            assertFalse("${file.name} 不得访问网络 Gateway", source.contains("NetworkGateway"))
        }
    }

    @Test
    fun repositoryDoesNotCallProviderAndContextBuilderRemainsUnique() {
        val repository = mainRoot.resolve("data/MaterialContextRepositoryComponent.kt").readText()
        assertFalse(repository.contains("Gemini"))
        assertFalse(repository.contains("NetworkGateway"))

        val builders = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sumOf { file -> Regex("class\\s+ExecutionContextBuilder").findAll(file.readText()).count() }
        assertEquals(1, builders)
    }

    @Test
    fun resourcesAndContextFollowRouteViewModelScreenComponentsLayers() {
        listOf(
            "ui/screens/resources/ResourcesRoute.kt",
            "ui/screens/resources/ResourcesViewModel.kt",
            "ui/screens/resources/ResourcesScreen.kt",
            "ui/screens/resources/ResourcesComponents.kt",
            "ui/screens/context/ContextConfirmationUiState.kt",
            "ui/screens/context/ContextConfirmationComponents.kt",
        ).forEach { path -> assertTrue("缺少 $path", mainRoot.resolve(path).isFile) }
    }

    private fun findMainSourceRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            val candidate = current.resolve("src/main/java/com/elio/jianyu")
            if (candidate.isDirectory) return candidate
            val appCandidate = current.resolve("app/src/main/java/com/elio/jianyu")
            if (appCandidate.isDirectory) return appCandidate
            current = current.parentFile
        }
        error("无法定位主源码目录")
    }
}
