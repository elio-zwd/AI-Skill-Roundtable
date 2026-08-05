package com.elio.jianyu.audio.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioFileStoreConcurrencyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun replacementAttemptsUseDistinctPartFilesButShareStableFinalPath() {
        val root = temporaryFolder.newFolder("replacement-root")
        val attempts = ArrayDeque(listOf("attempt-old", "attempt-new"))
        val store = AudioFileStore(
            rootDirectory = root,
            temporaryAttemptIdProvider = { attempts.removeFirst() },
        )

        val oldAttempt = store.createPendingTarget("asset-1", AudioTargetFormat.WAV)
        val newAttempt = store.createPendingTarget("asset-1", AudioTargetFormat.WAV)

        assertNotEquals(oldAttempt.temporaryRelativePath, newAttempt.temporaryRelativePath)
        assertEquals(oldAttempt.finalRelativePath, newAttempt.finalRelativePath)
        assertTrue(oldAttempt.temporaryRelativePath.endsWith(".wav.part"))
        assertTrue(newAttempt.temporaryRelativePath.endsWith(".wav.part"))
        assertFalse(oldAttempt.temporaryRelativePath.contains("attempt-old"))
        assertFalse(newAttempt.temporaryRelativePath.contains("attempt-new"))
    }

    @Test
    fun cancellationRemovesEveryPartAttemptForAssetWithoutDeletingFinalFile() {
        val root = temporaryFolder.newFolder("cancel-root")
        val attempts = ArrayDeque(listOf("attempt-a", "attempt-b", "attempt-other"))
        val store = AudioFileStore(
            rootDirectory = root,
            temporaryAttemptIdProvider = { attempts.removeFirst() },
        )
        val first = store.createPendingTarget("asset-1", AudioTargetFormat.WAV)
        val second = store.createPendingTarget("asset-1", AudioTargetFormat.WAV)
        val other = store.createPendingTarget("asset-2", AudioTargetFormat.WAV)
        store.openPendingWriter(first).use { it.write(validWavBytes()) }
        store.openPendingWriter(second).use { it.write(validWavBytes()) }
        store.openPendingWriter(other).use { it.write(validWavBytes()) }

        val cleanup = store.removeTemporaryFilesForAsset("asset-1", AudioTargetFormat.WAV)

        assertEquals(2, cleanup.removedCount)
        assertTrue(cleanup.failedRelativePaths.isEmpty())
        assertFalse(first.temporaryFile.exists())
        assertFalse(second.temporaryFile.exists())
        assertTrue(other.temporaryFile.exists())
        assertFalse(first.finalFile.exists())
    }

    private fun validWavBytes(): ByteArray {
        val bytes = ByteArray(48)
        "RIFF".toByteArray().copyInto(bytes, destinationOffset = 0)
        "WAVE".toByteArray().copyInto(bytes, destinationOffset = 8)
        "fmt ".toByteArray().copyInto(bytes, destinationOffset = 12)
        "data".toByteArray().copyInto(bytes, destinationOffset = 36)
        bytes[44] = 1
        bytes[45] = 2
        bytes[46] = 3
        bytes[47] = 4
        return bytes
    }
}
