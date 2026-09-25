package com.elio.jianyu.ui.components

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAvatarArchitectureTest {
    @Test
    fun canonicalUserAvatar_cropsBundledSourceWhitespaceInsideCallerShape() {
        val source = sourceFile("UserAvatar.kt").readText()

        assertTrue(
            "UserAvatar 应使用容器承接调用方的 CircleShape clip",
            source.contains("Box(modifier = modifier)"),
        )
        assertTrue(
            "当前固定 avatar_user 源图需要顶部居中放大，裁掉下方白底",
            source.contains("USER_AVATAR_SOURCE_ZOOM"),
        )
        assertTrue(source.contains("scaleX = USER_AVATAR_SOURCE_ZOOM"))
        assertTrue(source.contains("scaleY = USER_AVATAR_SOURCE_ZOOM"))
        assertTrue(source.contains("TransformOrigin(0.5f, 0f)"))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/components/$name"),
        File("app/src/main/java/com/elio/jianyu/ui/components/$name"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/components/$name")
}
