package com.elio.jianyu.backup.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRecordAndScopePrototypeTest {
    @Test
    fun publicRecordStreamsRequireManifestAndComplete() {
        val empty = vectorRecordStream("portable-empty.json")
        val unicode = vectorRecordStream("portable-unicode-record.json")

        val emptyResult = BackupRecordStreamPrototype.verify(empty)
        val unicodeResult = BackupRecordStreamPrototype.verify(unicode)

        assertEquals(1L, emptyResult.recordCountBeforeComplete)
        assertEquals(0L, emptyResult.entityCount)
        assertEquals(0L, emptyResult.blobCount)
        assertEquals(2L, unicodeResult.recordCountBeforeComplete)
        assertEquals(1L, unicodeResult.entityCount)
        assertEquals(0L, unicodeResult.blobCount)
    }

    @Test
    fun truncationAndAuthenticatedRecordAfterCompleteFailClosed() {
        val stream = vectorRecordStream("portable-empty.json")

        runCatching {
            BackupRecordStreamPrototype.verify(stream.copyOf(stream.size - 1))
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.TRUNCATED_PAYLOAD)

        val authenticatedTrailingFrame = "00000003a10101".hexToBytes()
        runCatching {
            BackupRecordStreamPrototype.verify(stream + authenticatedTrailingFrame)
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.TRAILING_DATA)
    }

    @Test
    fun permanentSecretAndFilesystemObjectsAreNotWhitelisted() {
        val excluded = setOf(
            PrototypeBackupObjectType.API_KEY,
            PrototypeBackupObjectType.API_KEY_ENCRYPTED_FILE,
            PrototypeBackupObjectType.API_KEY_BINDING,
            PrototypeBackupObjectType.KEYSTORE_KEY,
            PrototypeBackupObjectType.BACKUP_PASSWORD,
            PrototypeBackupObjectType.APP_LOCK_CREDENTIAL,
            PrototypeBackupObjectType.ACCESS_TOKEN,
            PrototypeBackupObjectType.AUTHORIZATION_HEADER,
            PrototypeBackupObjectType.URI_GRANT,
            PrototypeBackupObjectType.ABSOLUTE_PATH,
            PrototypeBackupObjectType.TEMPORARY_PART_FILE,
            PrototypeBackupObjectType.CACHE_FILE,
            PrototypeBackupObjectType.ORPHAN_FILE,
            PrototypeBackupObjectType.UNCONFIRMED_EDITOR_MEMORY,
            PrototypeBackupObjectType.PENDING_MESSAGE,
            PrototypeBackupObjectType.RUNNING_RUN,
            PrototypeBackupObjectType.PENDING_AUDIO,
            PrototypeBackupObjectType.TELEMETRY_BODY,
            PrototypeBackupObjectType.PURGED_CONTENT,
            PrototypeBackupObjectType.EXTERNAL_URI_ORIGINAL_FILE,
            PrototypeBackupObjectType.STATIC_APK_SKILL_ASSET,
        )

        assertTrue(excluded.all { it in BackupDataScopePrototype.permanentExclusions })
        assertTrue(excluded.none { it in BackupDataScopePrototype.portableWhitelist })
        assertFalse(PrototypeBackupObjectType.MESSAGE in BackupDataScopePrototype.permanentExclusions)
        assertTrue(PrototypeBackupObjectType.MESSAGE in BackupDataScopePrototype.portableWhitelist)
    }

    @Test
    fun everyPurgeStateBlocksACompleteIssueBackup() {
        PrototypePurgeState.entries
            .filterNot { it == PrototypePurgeState.NONE }
            .forEach { purgeState ->
                runCatching {
                    BackupDataScopePrototype.requireIssueStable(
                        PrototypeIssueBackupState(purgeState = purgeState),
                    )
                }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.PURGE_IN_PROGRESS)
            }
    }

    @Test
    fun runningRunPendingMessageAndAudioWorkBlockBackup() {
        listOf(
            PrototypeIssueBackupState(hasRunningRun = true),
            PrototypeIssueBackupState(hasPendingMessage = true),
            PrototypeIssueBackupState(hasActiveAudioWork = true),
        ).forEach { state ->
            runCatching {
                BackupDataScopePrototype.requireIssueStable(state)
            }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.ACTIVE_WORK_IN_PROGRESS)
        }
    }

    @Test
    fun externalUriIsMetadataOnlyAndAbsolutePathIsRejected() {
        assertEquals(
            PrototypePortableLocator.PublicUrl("https://example.test/source"),
            BackupDataScopePrototype.portableSourceLocator(
                scheme = "https",
                rawLocator = "https://example.test/source",
            ),
        )
        assertEquals(
            PrototypePortableLocator.UnavailableAfterImport("content"),
            BackupDataScopePrototype.portableSourceLocator(
                scheme = "content",
                rawLocator = "content://provider/document/42",
            ),
        )
        assertEquals(
            PrototypePortableLocator.UnavailableAfterImport("file"),
            BackupDataScopePrototype.portableSourceLocator(
                scheme = "file",
                rawLocator = "file:///sdcard/private.txt",
            ),
        )
        runCatching {
            BackupDataScopePrototype.portableSourceLocator(
                scheme = null,
                rawLocator = "/data/user/0/com.elio.jianyu/private.txt",
            )
        }.exceptionOrNull()!!.requireBackupCode(BackupDesignErrorCode.PATH_INVALID)
    }

    private fun vectorRecordStream(name: String): ByteArray = BackupVectorTestSupport.load(name)
        .getJSONObject("recordStream")
        .getString("plaintextHex")
        .hexToBytes()
}
