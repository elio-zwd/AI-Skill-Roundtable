package com.elio.jianyu.ui.screens.dialog

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogDarkThemeArchitectureTest {
    @Test
    fun dialogSurfaces_followMaterialThemeInsteadOfFixedLightColors() {
        val tokens = sourceFile("DialogTokens.kt").readText()
        val composer = sourceFile("components/DialogComposer.kt").readText()
        val messages = sourceFile("components/DialogMessageComponents.kt").readText()
        val roleStrip = sourceFile("components/SkillRoleStrip.kt").readText()

        assertTrue(tokens.contains("MaterialTheme.colorScheme.background"))
        assertTrue(tokens.contains("MaterialTheme.colorScheme.surface"))
        assertTrue(tokens.contains("MaterialTheme.colorScheme.onSurface"))
        assertTrue(tokens.contains("MaterialTheme.colorScheme.outlineVariant"))
        assertFalse(tokens.contains("val PageBackground = Color("))
        assertFalse(tokens.contains("val SurfaceWhite = Color("))

        assertTrue(composer.contains(".background(MaterialTheme.colorScheme.surface)"))
        assertFalse(composer.contains(".background(Color.White)"))
        assertFalse(composer.contains("color = Color(0xFFEAEBED)"))

        assertTrue(messages.contains(".background(MaterialTheme.colorScheme.surface)"))
        assertTrue(messages.contains(".background(MaterialTheme.colorScheme.primaryContainer)"))
        assertFalse(messages.contains(".background(Color.White)"))

        assertTrue(roleStrip.contains("MaterialTheme.colorScheme.surfaceContainer"))
    }

    private fun sourceFile(relativePath: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath")
}
