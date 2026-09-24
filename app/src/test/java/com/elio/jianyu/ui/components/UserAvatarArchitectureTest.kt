package com.elio.jianyu.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAvatarArchitectureTest {
    @Test
    fun canonicalUserAvatar_prefersProvidedImageAndKeepsBundledFallbackCrop() {
        val source = sourceFile("UserAvatar.kt").readText()

        assertTrue(source.contains("LocalUserAvatarImage"))
        assertTrue(source.contains("val customImage = LocalUserAvatarImage.current"))
        assertTrue(source.contains("if (customImage != null)"))
        assertTrue(source.contains("bitmap = customImage"))
        assertTrue(source.contains("painter = painterResource(R.drawable.avatar_user)"))

        assertTrue(
            "默认 avatar_user 仍需顶部居中放大裁掉下方白底",
            source.contains("USER_AVATAR_SOURCE_ZOOM"),
        )
        assertTrue(source.contains("scaleX = USER_AVATAR_SOURCE_ZOOM"))
        assertTrue(source.contains("scaleY = USER_AVATAR_SOURCE_ZOOM"))
        assertTrue(source.contains("TransformOrigin(0.5f, 0f)"))

        val customBranch = source
            .substringAfter("if (customImage != null)")
            .substringBefore("} else {")
        assertFalse(
            "自定义头像不能套用默认头像的额外 zoom",
            customBranch.contains("USER_AVATAR_SOURCE_ZOOM"),
        )
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/components/$name"),
        File("app/src/main/java/com/elio/jianyu/ui/components/$name"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/components/$name")
}
