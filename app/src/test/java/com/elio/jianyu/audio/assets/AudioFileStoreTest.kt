package com.elio.jianyu.audio.assets

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun validWavUsesPartFileAndAtomicCommitWithRelativeOpaquePath() {
        val root = temporaryFolder.newFolder("jianyu-audio")
        val store = AudioFileStore(rootDirectory = root)
        val target = store.createPendingTarget(
            audioAssetId = "asset-private-source-1",
            format = AudioTargetFormat.WAV,
        )

        assertTrue(target.temporaryRelativePath.endsWith(".wav.part"))
        assertTrue(target.finalRelativePath.endsWith(".wav"))
        assertFalse(target.finalRelativePath.contains("asset-private-source-1"))
        assertFalse(target.finalRelativePath.startsWith(root.absolutePath))

        store.openPendingWriter(target).use { writer ->
            writer.write(validWavBytes())
        }

        assertTrue(target.temporaryFile.exists())
        assertFalse(target.finalFile.exists())
        assertTrue(store.validatePending(target) is AudioFileValidation.Valid)

        val commit = store.commit(target)
        assertTrue(commit is AudioFileCommitResult.Success)
        val committed = (commit as AudioFileCommitResult.Success).file
        assertEquals(target.finalRelativePath, committed.relativePath)
        assertEquals(AudioTargetFormat.WAV, committed.format)
        assertEquals("audio/wav", committed.mimeType)
        assertTrue(committed.sizeBytes > 44L)
        assertFalse(target.temporaryFile.exists())
        assertTrue(target.finalFile.exists())
    }

    @Test
    fun zeroByteAndInvalidHeadersAreRejectedWithoutFinalFile() {
        val root = temporaryFolder.newFolder("invalid-audio")
        val store = AudioFileStore(rootDirectory = root)

        val zero = store.createPendingTarget("zero", AudioTargetFormat.WAV)
        store.openPendingWriter(zero).use { }
        assertEquals(
            AudioFileStoreErrorCode.EMPTY_AUDIO,
            (store.validatePending(zero) as AudioFileValidation.Invalid).errorCode,
        )
        assertTrue(store.commit(zero) is AudioFileCommitResult.Failure)
        assertFalse(zero.finalFile.exists())

        val invalidWav = store.createPendingTarget("invalid-wav", AudioTargetFormat.WAV)
        store.openPendingWriter(invalidWav).use { writer -> writer.write(ByteArray(64) { 1 }) }
        assertEquals(
            AudioFileStoreErrorCode.INVALID_AUDIO_FORMAT,
            (store.validatePending(invalidWav) as AudioFileValidation.Invalid).errorCode,
        )

        val invalidAac = store.createPendingTarget("invalid-aac", AudioTargetFormat.AAC_ADTS)
        store.openPendingWriter(invalidAac).use { writer -> writer.write(ByteArray(16) { 2 }) }
        assertEquals(
            AudioFileStoreErrorCode.INVALID_AUDIO_FORMAT,
            (store.validatePending(invalidAac) as AudioFileValidation.Invalid).errorCode,
        )
    }

    @Test
    fun pathTraversalAndAbsolutePathsAreRejected() {
        val root = temporaryFolder.newFolder("path-root")
        val store = AudioFileStore(rootDirectory = root)

        assertEquals(
            AudioFileStoreErrorCode.PATH_REJECTED,
            (store.resolve("../escape.wav") as AudioFileResolution.Rejected).errorCode,
        )
        assertEquals(
            AudioFileStoreErrorCode.PATH_REJECTED,
            (store.resolve(root.resolve("absolute.wav").absolutePath) as AudioFileResolution.Rejected).errorCode,
        )
    }

    @Test
    fun insufficientStorageIsReportedBeforeAnyFileIsCreated() {
        val root = temporaryFolder.newFolder("space-root")
        val store = AudioFileStore(
            rootDirectory = root,
            usableSpaceProvider = { 1_024L },
        )

        val result = store.preflight(
            estimatedOutputBytes = 2_048L,
            minimumReservationBytes = 512L,
            safetyMarginBytes = 128L,
        )

        assertTrue(result is AudioStoragePreflight.Insufficient)
        val insufficient = result as AudioStoragePreflight.Insufficient
        assertEquals(1_024L, insufficient.usableBytes)
        assertEquals(2_176L, insufficient.requiredBytes)
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun atomicMoveFailureLeavesPartFileAndNoFinalFile() {
        val root = temporaryFolder.newFolder("move-root")
        val store = AudioFileStore(
            rootDirectory = root,
            atomicMover = AudioAtomicMover { _, _ -> throw IOException("simulated-move-failure") },
        )
        val target = store.createPendingTarget("asset-move", AudioTargetFormat.WAV)
        store.openPendingWriter(target).use { writer -> writer.write(validWavBytes()) }

        val commit = store.commit(target)

        assertTrue(commit is AudioFileCommitResult.Failure)
        assertEquals(
            AudioFileStoreErrorCode.ATOMIC_MOVE_FAILED,
            (commit as AudioFileCommitResult.Failure).errorCode,
        )
        assertTrue(target.temporaryFile.exists())
        assertFalse(target.finalFile.exists())
    }

    @Test
    fun missingAndOrphanInspectionNeverDeletesFiles() {
        val root = temporaryFolder.newFolder("reconcile-root")
        val store = AudioFileStore(rootDirectory = root)

        assertTrue(store.resolve("missing.wav") is AudioFileResolution.Missing)

        val referenced = store.createPendingTarget("referenced", AudioTargetFormat.WAV)
        store.openPendingWriter(referenced).use { it.write(validWavBytes()) }
        val referencedCommit = store.commit(referenced) as AudioFileCommitResult.Success

        val orphan = store.createPendingTarget("orphan", AudioTargetFormat.AAC_ADTS)
        store.openPendingWriter(orphan).use { it.write(validAacBytes()) }
        val orphanCommit = store.commit(orphan) as AudioFileCommitResult.Success

        val report = store.scanOrphans(setOf(referencedCommit.file.relativePath))

        assertEquals(listOf(orphanCommit.file.relativePath), report.files.map { it.relativePath })
        assertTrue(orphan.finalFile.exists())
        assertTrue(referenced.finalFile.exists())
        assertFalse(report.files.single().relativePath.startsWith(root.absolutePath))
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

    private fun validAacBytes(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(),
            0xF1.toByte(),
            0x50,
            0x80.toByte(),
            0x00,
            0x1F,
            0xFC.toByte(),
            0x01,
        )
    }
}
