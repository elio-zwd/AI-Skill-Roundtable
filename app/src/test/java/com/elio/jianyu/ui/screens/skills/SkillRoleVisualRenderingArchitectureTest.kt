package com.elio.jianyu.ui.screens.skills

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRoleVisualRenderingArchitectureTest {
    @Test
    fun rolePageIdentityVisual_alwaysDelegatesToJianyuRoleAvatar() {
        val source = sourceFile("SkillRolePageScreen.kt").readText()
        val function = source
            .substringAfter("private fun RoleIdentityVisual(")
            .substringBefore("@Composable\nprivate fun RoleBadge")

        assertTrue(function.contains("JianyuRoleAvatar("))
        assertTrue(function.contains("assetPath = role.avatarAssetPath"))
        assertFalse(function.contains("if (role.isPersonSimulation)"))
        assertFalse(function.contains("role.name.take(2)"))
        assertFalse(source.contains("private fun SkillRoleCardUi.visualAvatarPath()"))
    }

    @Test
    fun roleDetailIdentityVisual_alwaysDelegatesToJianyuRoleAvatar() {
        val source = sourceFile("SkillRoleDetailScreen.kt").readText()
        val function = source
            .substringAfter("private fun RoleDetailIdentityVisual(")
            .substringBefore("@Composable\nprivate fun RoleDetailInfoCard")

        assertTrue(function.contains("JianyuRoleAvatar("))
        assertTrue(function.contains("assetPath = role.avatarAssetPath"))
        assertFalse(function.contains("if (role.isPersonSimulation)"))
        assertFalse(function.contains("role.name.take(2)"))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/skills/$name"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/skills/$name"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/java/com/elio/jianyu/ui/screens/skills/$name")
}
