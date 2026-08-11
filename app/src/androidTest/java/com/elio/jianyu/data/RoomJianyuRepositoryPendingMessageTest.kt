package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomJianyuRepositoryPendingMessageTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: JianyuRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(database)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun pendingMessageCanStreamThenCompleteWithoutLateOverwrite() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        repository.appendDomainMessage(pendingMessageCommand()).successValue()

        val initialRecovery = repository.recoverIssue(ISSUE_ID).successValue()
        assertEquals(listOf(MESSAGE_ID), initialRecovery.core.pendingMessages.map { it.id })

        val partial = repository.updatePendingDomainMessage(
            updateCommand(text = "部分回答", keepPending = true)
        ).successValue()
        assertTrue(partial.isPending)
        assertEquals("部分回答", partial.text)

        val completed = repository.updatePendingDomainMessage(
            updateCommand(text = "完整回答", keepPending = false)
        ).successValue()
        assertFalse(completed.isPending)
        assertEquals("完整回答", completed.text)

        val repeated = repository.updatePendingDomainMessage(
            updateCommand(text = "完整回答", keepPending = false)
        )
        assertTrue(repeated.isIdempotentSuccess())

        val lateChunk = repository.updatePendingDomainMessage(
            updateCommand(text = "迟到片段", keepPending = true)
        )
        assertTrue(lateChunk.failureError() is RepositoryError.InvalidState)

        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        assertTrue(recovery.core.pendingMessages.isEmpty())
        assertEquals("完整回答", recovery.core.messages.single().text)
        assertEquals(setOf(PARTICIPANT_ID), recovery.core.successfulParticipantSnapshotIds())
        assertTrue(recovery.core.retryableParticipantSnapshotIds().isEmpty())
    }

    @Test
    fun pendingUpdateRejectsMismatchedDomainIdentity() = runBlocking {
        repository.saveIssue(issueCommand()).successValue()
        repository.createExecutionRun(runCommand()).successValue()
        repository.appendDomainMessage(pendingMessageCommand()).successValue()

        val mismatched = repository.updatePendingDomainMessage(
            updateCommand(text = "错误关系", keepPending = false).copy(
                participantSnapshotId = "other-participant"
            )
        )

        assertTrue(mismatched.failureError() is RepositoryError.IdempotencyConflict)
        val stored = repository.recoverIssue(ISSUE_ID).successValue().core.messages.single()
        assertTrue(stored.isPending)
        assertEquals("", stored.text)
    }

    private fun issueCommand(): SaveIssueCommand {
        return SaveIssueCommand(
            issueId = ISSUE_ID,
            title = "议题",
            initialStageId = STAGE_ID,
            initialStageTitle = "初始阶段",
            initialObjective = "理解问题",
            createdAt = 10L
        )
    }

    private fun runCommand(): CreateExecutionRunCommand {
        return CreateExecutionRunCommand(
            run = ExecutionRunEntity(
                id = RUN_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                idempotencyKey = "pending-run-key",
                createdAt = 15L,
                updatedAt = 15L,
                actualModelId = "gemini-3.6-flash",
                actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
                thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
            ),
            participants = listOf(
                ExecutionParticipantSnapshotEntity(
                    id = PARTICIPANT_ID,
                    runId = RUN_ID,
                    sourceType = "official_skill",
                    sourceId = SKILL_ID,
                    displayName = "Skill A",
                    avatar = "A",
                    skillAssetPath = "skills/skill-a/SKILL.md",
                    systemPrompt = "system",
                    configurationJson = "{}",
                    defaultResponsibility = "",
                    position = 0,
                    createdAt = 15L
                )
            )
        )
    }

    private fun pendingMessageCommand(): AppendDomainMessageCommand {
        return AppendDomainMessageCommand(
            messageId = MESSAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = PARTICIPANT_ID,
            senderId = SKILL_ID,
            senderName = "Skill A",
            avatar = "A",
            text = "",
            timestamp = 20L,
            isPending = true,
            roundIndex = 1,
            compatibilitySessionTitle = "议题"
        )
    }

    private fun updateCommand(
        text: String,
        keepPending: Boolean
    ): UpdatePendingDomainMessageCommand {
        return UpdatePendingDomainMessageCommand(
            messageId = MESSAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = PARTICIPANT_ID,
            text = text,
            keepPending = keepPending
        )
    }

    private fun <T> RepositoryResult<T>.successValue(): T {
        return (this as RepositoryResult.Success<T>).value
    }

    private fun RepositoryResult<*>.failureError(): RepositoryError {
        return (this as RepositoryResult.Failure).error
    }

    private fun RepositoryResult<*>.isIdempotentSuccess(): Boolean {
        return (this as? RepositoryResult.Success<*>)?.idempotent == true
    }

    companion object {
        private const val ISSUE_ID = "issue-pending"
        private const val STAGE_ID = "stage-pending"
        private const val RUN_ID = "run-pending"
        private const val PARTICIPANT_ID = "participant-pending"
        private const val SKILL_ID = "skill-a"
        private const val MESSAGE_ID = 2001L
    }
}
