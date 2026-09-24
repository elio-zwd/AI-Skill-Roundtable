package com.elio.jianyu.ui.screens.mine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MineAvatarArchitectureTest {
    @Test
    fun mineRoute_usesPhotoPickerRepositoryAndSharedAvatarProvider() {
        val route = sourceFile("MineRoute.kt").readText()

        assertTrue(route.contains("UserAvatarRepository"))
        assertTrue(route.contains("ActivityResultContracts.PickVisualMedia"))
        assertTrue(route.contains("PickVisualMediaRequest"))
        assertTrue(route.contains("ModalBottomSheet"))
        assertTrue(route.contains("LocalUserAvatarImage"))
        assertFalse(route.contains("READ_MEDIA_IMAGES"))
        assertFalse(route.contains("READ_EXTERNAL_STORAGE"))
    }

    @Test
    fun mineScreen_exposesFormalAvatarEditAction() {
        val screen = sourceFile("MineScreen.kt").readText()

        assertTrue(screen.contains("onEditAvatar"))
        assertTrue(screen.contains("MineTestTags.AVATAR_EDIT_BUTTON"))
        assertFalse(screen.contains("AVATAR_SWITCH_UNAVAILABLE"))
        assertFalse(screen.contains("头像切换（待产品定义）"))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/mine/$name"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/mine/$name"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/mine/$name")
}
