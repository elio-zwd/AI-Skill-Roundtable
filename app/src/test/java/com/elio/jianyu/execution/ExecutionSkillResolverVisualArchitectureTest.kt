package com.elio.jianyu.execution

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionSkillResolverVisualArchitectureTest {
    @Test
    fun officialExecutionSnapshot_usesCanonicalOfficialVisualPath() {
        val source = sourceFile("ExecutionSkillResolver.kt").readText()

        assertTrue(source.contains("officialSkillVisualAssetPath"))
        assertTrue(source.contains("avatar = officialSkillVisualAssetPath(definition)"))
        assertFalse(source.contains("config?.avatar"))
        assertFalse(source.contains("definition.nameZh.take(1)"))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/execution/$name"),
        File("app/src/main/java/com/elio/jianyu/execution/$name"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/java/com/elio/jianyu/execution/$name")
}
