package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomJianyuRepositoryProcessRecoveryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var database: RoundtableDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun databaseReopenRestoresActiveWorkWithoutWriting() = runBlocking {
        val firstDatabase = openDatabase()
        val firstRepository = RoomJianyuRepository(firstDatabase)
        firstRepository.saveIssue(issueCommand()).successValue()
        firstRepository.createExecutionRun(runCommand()).successValue()
        val running = firstRepository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 20L,
                startedAt = 20L,
            ),
        ).successValue()
        firstRepository.appendDomainMessage(pendingMessageCommand()).successValue()
        firstRepository.saveStageDraft(draftCommand()).successValue()
        firstRepository.recordMaterialUsage(materialUsage()).successValue()
        firstRepository.recordPersonalContextUsage(personalContextUsage()).successValue()
        firstDatabase.resourceLifecycleDao().createAudioAsset(
            AudioAssetEntity(
                id = AUDIO_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                sourceMessageId = MESSAGE_ID,
                storagePath = "/missing/audio-recovery.wav",
                mimeType = "audio/wav",
                format = "wav",
                sizeBytes = 0L,
                fileState = AudioFileState.MISSING,
                generationKey = "recovery-audio-key",
                createdAt = 30L,
                updatedAt = 30L,
            ),
        )
        firstDatabase.close()
        database = null

        val reopenedDatabase = openDatabase()
        val reopenedRepository = RoomJianyuRepository(reopenedDatabase)
        val recovery1 = reopenedRepository.recoverIssue(ISSUE_ID).successValue()
        val recovery2 = reopenedRepository.recoverIssue(ISSUE_ID).successValue()

        assertEquals(recovery1, recovery2)
        assertEquals(1, recovery1.core.stages.size)
        assertEquals(1, recovery1.core.runs.size)
        assertEquals(ExecutionRunStatus.RUNNING, recovery1.core.runs.single().status)
        assertEquals(running.updatedAt, recovery1.core.runs.single().updatedAt)
        assertEquals(listOf(MESSAGE_ID), recovery1.core.pendingMessages.map { it.id })
        assertEquals(listOf(DRAFT_ID), recovery1.resources.drafts.map { it.id })
        assertEquals(listOf(MATERIAL_USAGE_ID), recovery1.resources.materialUsages.map { it.id })
        assertEquals(listOf(CONTEXT_USAGE_ID), recovery1.resources.personalContextUsages.map { it.id })
        assertEquals(AudioFileState.MISSING, recovery1.resources.audioAssets.single().fileState)
        assertEquals(IssueLifecycleState.ACTIVE, recovery1.core.lifecycle.state)
        assertTrue(recovery1.core.successfulParticipantSnapshotIds().isEmpty())
        assertEquals(setOf(PARTICIPANT_ID), recovery1.core.retryableParticipantSnapshotIds())
        reopenedDatabase.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private fun openDatabase(): RoundtableDatabase {
        return Room.databaseBuilder(context, RoundtableDatabase::class.java, DATABASE_NAME)
            .addMigrations(*RoundtableDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
    }

    private fun issueCommand(): SaveIssueCommand {
        return SaveIssueCommand(
            issueId = ISSUE_ID,
            title = "恢复议题",
            initialStageId = STAGE_ID,
            initialStageTitle = "初始阶段",
            initialObjective = "验证进程恢复",
            createdAt = 10L,
        )
    }

    private fun runCommand(): CreateExecutionRunCommand {
        return CreateExecutionRunCommand(
            run = ExecutionRunEntity(
                id = RUN_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                idempotencyKey = "process-recovery-run-key",
                createdAt = 15L,
                updatedAt = 15L,
            ),
            participants = listOf(
                ExecutionParticipantSnapshotEntity(
                    id = PARTICIPANT_ID,
                    runId = RUN_ID,
                    sourceType = "official_skill",
                    sourceId = "skill-a",
                    displayName = "Skill A",
                    avatar = "A",
                    skillAssetPath = "skills/skill-a/SKILL.md",
                    systemPrompt = "system",
                    configurationJson = "{}",
                    defaultResponsibility = "",
                    position = 0,
                    createdAt = 15L,
                ),
            ),
        )
    }

    private fun pendingMessageCommand(): AppendDomainMessageCommand {
        return AppendDomainMessageCommand(
            messageId = MESSAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = PARTICIPANT_ID,
            senderId = "skill-a",
            senderName = "Skill A",
            avatar = "A",
            text = "",
            timestamp = 25L,
            isPending = true,
            roundIndex = 1,
            compatibilitySessionTitle = "恢复议题",
        )
    }

    private fun draftCommand(): SaveStageDraftCommand {
        val draft = StageSummaryDraftEntity(
            id = DRAFT_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            content = "恢复草稿",
            revisionNumber = 1,
            createdAt = 26L,
            updatedAt = 26L,
        )
        return SaveStageDraftCommand(
            draft = draft,
            revision = StageSummaryDraftRevisionEntity(
                id = REVISION_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                draftIdSnapshot = DRAFT_ID,
                revisionNumber = 1,
                contentSnapshot = draft.content,
                createdAt = 26L,
            ),
        )
    }

    private fun materialUsage(): MaterialUsageSnapshotEntity {
        return MaterialUsageSnapshotEntity(
            id = MATERIAL_USAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            runId = RUN_ID,
            titleSnapshot = "资料",
            sourceTypeSnapshot = "text",
            contentSnapshot = "资料快照",
            contentHash = "material-recovery-hash",
            userConfirmedAt = 27L,
            createdAt = 27L,
        )
    }

    private fun personalContextUsage(): PersonalContextUsageSnapshotEntity {
        return PersonalContextUsageSnapshotEntity(
            id = CONTEXT_USAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            runId = RUN_ID,
            titleSnapshot = "个人背景",
            contentSnapshot = "背景快照",
            contentHash = "context-recovery-hash",
            userConfirmedAt = 28L,
            createdAt = 28L,
        )
    }

    private fun <T> RepositoryResult<T>.successValue(): T {
        return (this as RepositoryResult.Success<T>).value
    }

    companion object {
        private const val DATABASE_NAME = "pr09-03-process-recovery.db"
        private const val ISSUE_ID = "issue-process-recovery"
        private const val STAGE_ID = "stage-process-recovery"
        private const val RUN_ID = "run-process-recovery"
        private const val PARTICIPANT_ID = "participant-process-recovery"
        private const val MESSAGE_ID = 4001L
        private const val DRAFT_ID = "draft-process-recovery"
        private const val REVISION_ID = "revision-process-recovery"
        private const val MATERIAL_USAGE_ID = "material-process-recovery"
        private const val CONTEXT_USAGE_ID = "context-process-recovery"
        private const val AUDIO_ID = "audio-process-recovery"
    }
}
