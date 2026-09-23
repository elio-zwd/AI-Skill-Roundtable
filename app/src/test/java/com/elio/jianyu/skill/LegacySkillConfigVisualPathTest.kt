package com.elio.jianyu.skill

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySkillConfigVisualPathTest {
    @Test
    fun legacy20CharacterAvatars_pointToCanonicalPortraitDirectory() {
        val text = assetFile("skills_config.json").readText()
        val avatarPaths = Regex("\\"avatar\\"\\s*:\\s*\\"([^\\"]+)\\"")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(20, avatarPaths.size)
        assertTrue(
            "legacy 20 头像必须全部迁到 avatars/portraits/: $avatarPaths",
            avatarPaths.all { path ->
                path.startsWith("avatars/portraits/") && path.endsWith(".jpg")
            },
        )
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
