package com.elio.jianyu.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillCatalogArchitectureTest {
    @Test
    fun skillFeatureDoesNotOwnRootNavigationOrCreateSecondNavHost() {
        val source = featureSource()

        assertFalse(source.contains("NavHost("))
        assertFalse(source.contains("rememberNavController"))
        assertFalse(source.contains("NavHostController"))
        assertFalse(source.contains("App.kt"))
    }

    @Test
    fun skillFeatureDoesNotAccessDaoGeminiOrExecutionRun() {
        val source = featureSource()

        assertFalse(source.contains("JianyuRepositoryDao"))
        assertFalse(source.contains("RoundtableDao"))
        assertFalse(source.contains("GenerativeModel"))
        assertFalse(source.contains("Gemini"))
        assertFalse(source.contains("ExecutionRunEntity"))
        assertFalse(source.contains("CreateExecutionRunCommand"))
    }

    @Test
    fun catalogManifestDoesNotCopySystemPrompts() {
        val manifest = projectFile("src/main/assets/official_skill_catalog_v1.json")
        assertNotNull(manifest)
        val text = requireNotNull(manifest).readText()

        assertFalse(text.contains("systemPrompt", ignoreCase = true))
        assertFalse(text.contains("system_prompt", ignoreCase = true))
        assertTrue(text.contains("sourceSummary"))
        assertTrue(text.contains("assetPath"))
    }

    @Test
    fun pr0905KeepsRoomV7AndDoesNotCreateSchema8() {
        val database = projectFile("src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt")
        assertNotNull(database)
        assertTrue(requireNotNull(database).readText().contains("version = 7"))

        val schemaRoot = projectFile("schemas")
        val schema8 = schemaRoot
            ?.walkTopDown()
            ?.firstOrNull { it.isFile && it.name == "8.json" }
        assertTrue("PR09-05 不得创建 Room v8 Schema", schema8 == null)
    }

    private fun featureSource(): String {
        val roots = listOf(
            projectFile("src/main/java/com/elio/jianyu/skill/catalog"),
            projectFile("src/main/java/com/elio/jianyu/ui/screens/skills"),
        ).filterNotNull()
        assertTrue("找不到 Skill Catalog 源码目录", roots.isNotEmpty())
        return roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }.joinToString("\n") { it.readText() }
    }

    private fun projectFile(relativeToApp: String): File? {
        return listOf(
            File(relativeToApp),
            File("app/$relativeToApp"),
        ).firstOrNull(File::exists)
    }
}
