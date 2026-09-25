package com.elio.jianyu.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAvatarPrivacyArchitectureTest {
    @Test
    fun avatarUpload_doesNotRequestBroadPhotoPermissionsOrPersistExternalUri() {
        val manifest = repositoryFile("app/src/main/AndroidManifest.xml").readText()
        val repository = sourceFile("UserAvatarRepository.kt").readText()
        val backupRules = repositoryFile("app/src/main/res/xml/backup_rules.xml").readText()
        val extractionRules = repositoryFile("app/src/main/res/xml/data_extraction_rules.xml").readText()

        assertFalse(manifest.contains("READ_MEDIA_IMAGES"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))

        assertTrue(repository.contains("File(appContext.filesDir, AVATAR_RELATIVE_PATH)"))
        assertTrue(repository.contains("const val USER_PROFILE_DIRECTORY = \"user-profile\""))
        assertTrue(
            repository.contains(
                "const val AVATAR_RELATIVE_PATH = \"\$USER_PROFILE_DIRECTORY/avatar.jpg\"",
            ),
        )
        assertFalse(repository.contains("SharedPreferences"))
        assertFalse(repository.contains("Room"))
        assertFalse(repository.contains("takePersistableUriPermission"))
        assertFalse(repository.contains("uri.toString()"))

        assertFalse(backupRules.contains("domain=\"file\""))
        assertFalse(extractionRules.contains("domain=\"file\""))
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/data/$name"),
        File("app/src/main/java/com/elio/jianyu/data/$name"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/data/$name")

    private fun repositoryFile(path: String): File = listOf(
        File(path),
        File("../$path"),
    ).firstOrNull(File::isFile) ?: File(path)
}
