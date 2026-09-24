package com.elio.jianyu.ui.screens.dialog.overlays

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddSkillRoleBottomSheetArchitectureTest {
    @Test
    fun recommendedAndAllSkillCards_delegateAvatarRenderingToSharedComponent() {
        val source = sourceFile("AddSkillRoleBottomSheet.kt").readText()

        val miniCard = source
            .substringAfter("private fun MiniSkillGridCard(")
            .substringBefore("@Composable\nprivate fun FullWidthSkillRow")

        val fullRow = source
            .substringAfter("private fun FullWidthSkillRow(")

        assertTrue(miniCard.contains("SkillRoleAvatar("))
        assertTrue(fullRow.contains("SkillRoleAvatar("))

        assertFalse(miniCard.contains("text = skill.avatarText.take(1)"))
        assertFalse(fullRow.contains("text = skill.avatarText.take(1)"))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/dialog/overlays/$name"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/overlays/$name"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/overlays/$name")
}
