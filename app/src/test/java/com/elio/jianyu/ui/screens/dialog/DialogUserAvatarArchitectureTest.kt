package com.elio.jianyu.ui.screens.dialog

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogUserAvatarArchitectureTest {
    @Test
    fun dialogRoute_observesSharedUserAvatarWithoutPersistingItIntoMessages() {
        val route = sourceFile("DialogRoute.kt").readText()
        val messageComponents = sourceFile("components/DialogMessageComponents.kt").readText()

        assertTrue(route.contains("UserAvatarRepository"))
        assertTrue(route.contains("LocalUserAvatarImage"))
        assertTrue(route.contains("CompositionLocalProvider"))
        assertTrue(route.contains("avatarRepository.observeAvatar()"))

        assertTrue(messageComponents.contains("UserAvatar("))
        assertFalse(messageComponents.contains("filesDir"))
        assertFalse(messageComponents.contains("avatar.jpg"))
    }

    private fun sourceFile(relativePath: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/dialog/$relativePath")
}
