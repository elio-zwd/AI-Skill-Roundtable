package com.elio.jianyu.skill

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySkillConfigVisualPathTest {
    @Test
    fun legacy20CharacterAvatars_pointToCanonicalPortraitDirectory() {
        val text = assetFile("skills_config.json").readText()
        val avatarPaths = Regex("""["]avatar["]\\s*:\\s*["]([^"]+)["]""")
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

    @Test
    fun metadataExtractor_seedPaths_preserveCanonicalPortraitDirectory() {
        val text = repositoryFile("workspace/tools/extract_skills_metadata.py").readText()
        val avatarPaths = Regex("""["]avatar["]\\s*:\\s*["]([^"]+)["]""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(20, avatarPaths.size)
        assertTrue(
            "metadata 脚本不得把 legacy 头像重新生成到 avatars/ 根目录: $avatarPaths",
            avatarPaths.all { path ->
                path.startsWith("avatars/portraits/") && path.endsWith(".jpg")
            },
        )
    }

    private fun repositoryFile(path: String): File = listOf(
        File(path),
        File("../$path"),
    ).firstOrNull(File::isFile) ?: File(path)

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
