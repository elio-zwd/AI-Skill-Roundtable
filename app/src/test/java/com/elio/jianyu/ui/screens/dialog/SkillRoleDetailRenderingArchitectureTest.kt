package com.elio.jianyu.ui.screens.dialog

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRoleDetailRenderingArchitectureTest {
    @Test
    fun dialogSkillRoleDetail_usesSharedAssetAvatarRenderer() {
        val source = sourceFile().readText()

        assertTrue(source.contains("JianyuRoleAvatar("))
        assertTrue(source.contains("assetPath = detail.role.avatarUrl.takeIf(String::isNotBlank)"))
        assertTrue(source.contains("avatarResId = detail.role.avatarResId"))
        assertFalse(source.contains("if (detail.role.avatarResId != null)"))
    }

    private fun sourceFile(): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/dialog/overlays/SkillRoleDetailBottomSheet.kt"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/overlays/SkillRoleDetailBottomSheet.kt"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/overlays/SkillRoleDetailBottomSheet.kt")
}
