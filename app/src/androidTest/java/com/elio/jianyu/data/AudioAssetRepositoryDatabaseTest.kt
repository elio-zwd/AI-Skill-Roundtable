package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.audio.assets.AudioAssetCreateResult
import com.elio.jianyu.audio.assets.AudioAssetLifecycleRepositoryPort
import com.elio.jianyu.audio.assets.AudioAssetRepositoryPort
import com.elio.jianyu.audio.assets.AudioAssetRetryResetResult
import com.elio.jianyu.audio.assets.AudioAssetSource
import com.elio.jianyu.audio.assets.AudioDeleteWriteResult
import com.elio.jianyu.audio.assets.AudioGenerationConfig
import com.elio.jianyu.audio.assets.AudioGenerationKeyFactory
import com.elio.jianyu.audio.assets.AudioSourceLoadResult
import com.elio.jianyu.audio.assets.AudioSourceReference
import com.elio.jianyu.audio.assets.AudioTargetFormat
import com.elio.jianyu.audio.assets.CreatePendingAudioCommand
import com.elio.jianyu.audio.assets.MarkAudioAvailableCommand
import com.elio.jianyu.audio.assets.PersistAudioDeleteRequestCommand
import com.elio.jianyu.audio.assets.ResetAudioForRetryCommand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioAssetRepositoryDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: RoomJianyuRepository
    private lateinit var audioRepository: AudioAssetRepositoryPort
    private lateinit var lifecycleRepository: AudioAssetLifecycleRepositoryPort

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(database)
        val roomAudioRepository = RoomAudioAssetRepository(database, nowProvider = { 1_000L })
        audioRepository = roomAudioRepository
        lifecycleRepository = roomAudioRepository
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "音频议题",
                initialStageId = STAGE_ID,
                initialStageTitle = "当前阶段",
                initialObjective = "验证独立音频资产",
                createdAt = 10L,
            ),
        ).expectSuccess()
        repository.appendDomainMessage(
            AppendDomainMessageCommand(
                messageId = MESSAGE_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                senderId = "skill-a",
                senderName = "Skill A",
                avatar = "A",
                text = MESSAGE_CONTENT,
                timestamp = 20L,
                isPending = false,
                roundIndex = 0,
                compatibilitySessionTitle = "音频议题",
            ),
        ).expectSuccess()
        repository.appendDomainMessage(
            AppendDomainMessageCommand(
                messageId = PENDING_MESSAGE_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                senderId = "skill-b",
                senderName = "Skill B",
                avatar = "B",
                text = "尚未完成",
                timestamp = 21L,
                isPending = true,
                roundIndex = 0,
                compatibilitySessionTitle = "音频议题",
            ),
        ).expectSuccess()
        repository.confirmArtifact(
            ConfirmArtifactCommand(
                artifact = ConfirmedArtifactEntity(
                    id = ARTIFACT_ID,
                    issueId = ISSUE_ID,
                    stageId = STAGE_ID,
                    title = "已确认成果",
                    content = ARTIFACT_CONTENT,
                    artifactType = "note",
                    contentFormat = "markdown",
                    confirmedAt = 30L,
                    createdAt = 30L,
                    updatedAt = 30L,
                ),
                sources = ArtifactSources(),
            ),
        ).expectSuccess()
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun completedMessageAndConfirmedArtifactAreLoadableWhilePendingAndCrossStageAreRejected() =
        runBlocking {
            val message = audioRepository.loadSource(
                AudioSourceReference.Message(ISSUE_ID, STAGE_ID, MESSAGE_ID),
            )
            val artifact = audioRepository.loadSource(
                AudioSourceReference.Artifact(ISSUE_ID, STAGE_ID, ARTIFACT_ID),
            )
            val pending = audioRepository.loadSource(
                AudioSourceReference.Message(ISSUE_ID, STAGE_ID, PENDING_MESSAGE_ID),
            )
            val crossStage = audioRepository.loadSource(
                AudioSourceReference.Message(ISSUE_ID, "another-stage", MESSAGE_ID),
            )

            assertTrue(message is AudioSourceLoadResult.Ready)
            assertEquals(MESSAGE_CONTENT, (message as AudioSourceLoadResult.Ready).snapshot.content)
            assertTrue(message.snapshot.source is AudioAssetSource.CompletedMessage)
            assertTrue(artifact is AudioSourceLoadResult.Ready)
            assertEquals(ARTIFACT_CONTENT, (artifact as AudioSourceLoadResult.Ready).snapshot.content)
            assertTrue(artifact.snapshot.source is AudioAssetSource.ConfirmedArtifact)
            assertEquals(
                com.elio.jianyu.audio.assets.AudioGenerationErrorCode.PENDING_MESSAGE,
                (pending as AudioSourceLoadResult.Rejected).errorCode,
            )
            assertEquals(
                com.elio.jianyu.audio.assets.AudioGenerationErrorCode.CROSS_STAGE,
                (crossStage as AudioSourceLoadResult.Rejected).errorCode,
            )
        }

    @Test
    fun generationKeyIsIdempotentAndAvailableTransitionUsesCompareAndSet() = runBlocking {
        val source = (audioRepository.loadSource(
            AudioSourceReference.Message(ISSUE_ID, STAGE_ID, MESSAGE_ID),
        ) as AudioSourceLoadResult.Ready).snapshot.source
        val config = AudioGenerationConfig(
            voiceProfileId = DEFAULT_VOICE_PROFILE,
            targetFormat = AudioTargetFormat.WAV,
            parameterVersion = 1,
        )
        val key = AudioGenerationKeyFactory.create(source, config)
        val command = CreatePendingAudioCommand(AUDIO_ID, source, config, key)

        val first = audioRepository.createPending(command)
        val repeated = audioRepository.createPending(command.copy(audioAssetId = "other-id"))
        val available = audioRepository.markAvailable(
            MarkAudioAvailableCommand(
                audioAssetId = AUDIO_ID,
                relativePath = "audio.wav",
                mimeType = "audio/wav",
                format = AudioTargetFormat.WAV,
                sizeBytes = 128L,
            ),
        )
        val lateFailure = audioRepository.markFailed(AUDIO_ID, AudioFileState.PENDING)
        val stored = audioRepository.loadAsset(AUDIO_ID)

        assertTrue(first is AudioAssetCreateResult.Created)
        assertTrue(repeated is AudioAssetCreateResult.Existing)
        assertEquals(AUDIO_ID, (repeated as AudioAssetCreateResult.Existing).asset.id)
        assertTrue(available)
        assertFalse(lateFailure)
        assertEquals(AudioFileState.AVAILABLE, stored?.fileState)
        assertEquals("audio.wav", stored?.storagePath)
        assertEquals(1, lifecycleRepository.listAudioAssetsForIssue(ISSUE_ID).size)
        assertEquals(1, lifecycleRepository.listAudioAssetsForStage(ISSUE_ID, STAGE_ID).size)
    }

    @Test
    fun persistedDeleteRequestBlocksLateAvailableAndIsIdempotent() = runBlocking {
        val pending = createPendingAsset()
        val requested = lifecycleRepository.requestDelete(
            PersistAudioDeleteRequestCommand(
                audioAssetId = pending.id,
                expectedState = AudioFileState.PENDING,
                requestedAt = 100L,
            ),
        )
        val repeated = lifecycleRepository.requestDelete(
            PersistAudioDeleteRequestCommand(
                audioAssetId = pending.id,
                expectedState = AudioFileState.PENDING,
                requestedAt = 101L,
            ),
        )
        val lateAvailable = audioRepository.markAvailable(
            MarkAudioAvailableCommand(
                audioAssetId = pending.id,
                relativePath = "late.wav",
                mimeType = "audio/wav",
                format = AudioTargetFormat.WAV,
                sizeBytes = 64L,
            ),
        )

        assertTrue(requested is AudioDeleteWriteResult.Requested)
        assertTrue(repeated is AudioDeleteWriteResult.AlreadyRequested)
        assertFalse(lateAvailable)
        assertEquals(100L, audioRepository.loadAsset(pending.id)?.purgeRequestedAt)
    }

    @Test
    fun canceledAssetCanOnlyResetWithMatchingExpectedStateAndSource() = runBlocking {
        val pending = createPendingAsset()
        assertTrue(audioRepository.markCanceled(pending.id, AudioFileState.PENDING))
        assertFalse(audioRepository.markAvailable(
            MarkAudioAvailableCommand(
                audioAssetId = pending.id,
                relativePath = "late.wav",
                mimeType = "audio/wav",
                format = AudioTargetFormat.WAV,
                sizeBytes = 64L,
            ),
        ))

        val current = requireNotNull(audioRepository.loadAsset(pending.id))
        val rejected = audioRepository.resetForRetry(
            ResetAudioForRetryCommand(
                audioAssetId = current.id,
                expectedState = AudioFileState.FAILED,
                source = current.source,
                config = current.config,
                generationKey = current.generationKey,
            ),
        )
        val reset = audioRepository.resetForRetry(
            ResetAudioForRetryCommand(
                audioAssetId = current.id,
                expectedState = AudioFileState.CANCELED,
                source = current.source,
                config = current.config,
                generationKey = current.generationKey,
            ),
        )

        assertTrue(rejected is AudioAssetRetryResetResult.Rejected)
        assertTrue(reset is AudioAssetRetryResetResult.Reset)
        assertEquals(AudioFileState.PENDING, audioRepository.loadAsset(current.id)?.fileState)
    }

    private suspend fun createPendingAsset(): com.elio.jianyu.audio.assets.AudioAssetRecord {
        val source = (audioRepository.loadSource(
            AudioSourceReference.Artifact(ISSUE_ID, STAGE_ID, ARTIFACT_ID),
        ) as AudioSourceLoadResult.Ready).snapshot.source
        val config = AudioGenerationConfig(
            voiceProfileId = DEFAULT_VOICE_PROFILE,
            targetFormat = AudioTargetFormat.WAV,
            parameterVersion = 1,
        )
        val created = audioRepository.createPending(
            CreatePendingAudioCommand(
                audioAssetId = AUDIO_ID,
                source = source,
                config = config,
                generationKey = AudioGenerationKeyFactory.create(source, config),
            ),
        )
        assertTrue(created is AudioAssetCreateResult.Created)
        return (created as AudioAssetCreateResult.Created).asset
    }

    private fun <T> RepositoryResult<T>.expectSuccess(): T {
        assertTrue("预期 Repository 成功，实际为 $this", this is RepositoryResult.Success)
        return (this as RepositoryResult.Success).value
    }

    private companion object {
        const val ISSUE_ID = "audio-issue"
        const val STAGE_ID = "audio-stage"
        const val MESSAGE_ID = 101L
        const val PENDING_MESSAGE_ID = 102L
        const val ARTIFACT_ID = "audio-artifact"
        const val AUDIO_ID = "audio-asset-1"
        const val MESSAGE_CONTENT = "这是已经完成的正式消息。"
        const val ARTIFACT_CONTENT = "这是用户已经确认的成果。"
        const val DEFAULT_VOICE_PROFILE = "jianyu-default"
    }
}
