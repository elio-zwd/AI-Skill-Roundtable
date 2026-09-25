package com.elio.jianyu.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserAvatarRepositoryAndroidTest {
    private lateinit var context: Context
    private lateinit var repository: UserAvatarRepository
    private lateinit var publishedFile: File

    @Before
    fun setUp() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            repository = UserAvatarRepository(context)
            publishedFile = File(context.filesDir, "user-profile/avatar.jpg")
            repository.resetToDefault()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            repository.resetToDefault()
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("avatar-repository-test-") }
                ?.forEach(File::delete)
        }
    }

    @Test
    fun importAvatar_publishesSquare512FileAndNewRepositoryCanReadIt() = runBlocking {
        val source = createSourceBitmap(width = 1200, height = 800, color = Color.rgb(36, 104, 180))

        val result = repository.importAvatar(Uri.fromFile(source))

        assertEquals(UserAvatarMutationResult.Success, result)
        assertTrue(publishedFile.isFile)

        val output = BitmapFactory.decodeFile(publishedFile.absolutePath)
        assertNotNull(output)
        assertEquals(512, output.width)
        assertEquals(512, output.height)

        val restored = UserAvatarRepository(context).observeAvatar().first()
        assertTrue(restored.hasCustomAvatar)
        assertNotNull(restored.bitmap)
        assertEquals(512, restored.bitmap?.width)
        assertEquals(512, restored.bitmap?.height)
    }

    @Test
    fun importAvatar_replacesExistingPublishedFile() = runBlocking {
        val firstSource = createSourceBitmap(900, 700, Color.RED)
        val secondSource = createSourceBitmap(900, 700, Color.BLUE)

        assertEquals(UserAvatarMutationResult.Success, repository.importAvatar(Uri.fromFile(firstSource)))
        val firstHash = sha256(publishedFile)

        assertEquals(UserAvatarMutationResult.Success, repository.importAvatar(Uri.fromFile(secondSource)))
        val secondHash = sha256(publishedFile)

        assertNotEquals(firstHash, secondHash)
        assertTrue(repository.observeAvatar().first().hasCustomAvatar)
    }

    @Test
    fun invalidImage_doesNotDestroyExistingAvatar() = runBlocking {
        val goodSource = createSourceBitmap(800, 800, Color.GREEN)
        assertEquals(UserAvatarMutationResult.Success, repository.importAvatar(Uri.fromFile(goodSource)))
        val before = sha256(publishedFile)

        val broken = File(context.cacheDir, "avatar-repository-test-broken.jpg").apply {
            writeText("not-an-image")
        }
        val result = repository.importAvatar(Uri.fromFile(broken))

        assertEquals(UserAvatarMutationResult.InvalidImage, result)
        assertTrue(publishedFile.isFile)
        assertEquals(before, sha256(publishedFile))
    }

    @Test
    fun resetToDefault_deletesCustomAvatarAndPublishesEmptyState() = runBlocking {
        val source = createSourceBitmap(800, 800, Color.MAGENTA)
        assertEquals(UserAvatarMutationResult.Success, repository.importAvatar(Uri.fromFile(source)))
        assertTrue(publishedFile.isFile)

        val reset = repository.resetToDefault()

        assertEquals(UserAvatarMutationResult.Success, reset)
        assertFalse(publishedFile.exists())
        assertFalse(repository.observeAvatar().first().hasCustomAvatar)
    }

    private fun createSourceBitmap(width: Int, height: Int, color: Int): File {
        val file = File(
            context.cacheDir,
            "avatar-repository-test-\${System.nanoTime()}-\$width-\$height.jpg",
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        file.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
        return file
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
