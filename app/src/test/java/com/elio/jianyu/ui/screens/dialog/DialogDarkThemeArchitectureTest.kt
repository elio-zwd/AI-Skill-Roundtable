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
        val addSkillSheet = sourceFile("overlays/AddSkillRoleBottomSheet.kt").readText()
        val historyDrawer = sourceFile("overlays/ConversationHistoryDrawer.kt").readText()

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
        assertFalse(roleStrip.contains(".background(Color(0xFFF7F7FA))"))

        assertTrue(addSkillSheet.contains("MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertTrue(addSkillSheet.contains("MaterialTheme.colorScheme.surfaceContainer"))
        assertFalse(addSkillSheet.contains(".background(Color(0xFFF1F5F9))"))
        assertFalse(addSkillSheet.contains(".background(skill.tintBg)"))

        assertTrue(historyDrawer.contains("MaterialTheme.colorScheme.surfaceContainerHigh"))
        assertTrue(historyDrawer.contains("MaterialTheme.colorScheme.primaryContainer"))
        assertFalse(historyDrawer.contains(".background(Color(0xFFF1F5F9))"))

        val state = sourceFile("DialogUiState.kt").readText()
        val moreMenu = sourceFile("overlays/SessionMoreMenuPopover.kt").readText()
        val featuresSheet = sourceFile("overlays/ComposerFeaturesBottomSheet.kt").readText()
        assertFalse(state.contains("accentColor = DialogTokens.TextPrimary"))
        assertTrue(moreMenu.contains("textColor: Color? = null"))
        assertTrue(moreMenu.contains("iconColor: Color? = null"))
        assertTrue(featuresSheet.contains("trailingTextColor: Color? = null"))
    }

    private fun sourceFile(relativePath: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath")
}
