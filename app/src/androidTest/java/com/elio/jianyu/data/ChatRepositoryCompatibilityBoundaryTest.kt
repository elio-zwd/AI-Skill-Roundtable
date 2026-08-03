package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatRepositoryCompatibilityBoundaryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: JianyuRepository
    private lateinit var legacyRepository: ChatRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(database)
        legacyRepository = ChatRepository(database.chatDao())
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun legacyStartupCleanupPreservesDomainPendingMessage() = runBlocking {
        val legacySessionId = legacyRepository.createSession("旧会话")
        legacyRepository.insertMessage(
            Message(
                chatId = legacySessionId,
                senderId = "legacy-skill",
                senderName = "Legacy",
                avatar = "L",
                text = "",
                timestamp = 5L,
                isPending = true
            )
        )
        createDomainPendingMessage()

        legacyRepository.removeAllPendingMessages()

        assertTrue(legacyRepository.getMessages(legacySessionId).isEmpty())
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        assertEquals(listOf(DOMAIN_MESSAGE_ID), recovery.core.pendingMessages.map { it.id })
    }

    @Test
    fun domainCompatibilitySessionIsHiddenAndCannotBeDeletedByLegacyRepository() = runBlocking {
        val legacySessionId = legacyRepository.createSession("旧会话")
        createDomainPendingMessage()
        val before = repository.recoverIssue(ISSUE_ID).successValue()
        val domainSessionId = requireNotNull(before.core.issue.legacyChatSessionId)

        val visibleSessionIds = legacyRepository.allSessions.first().map { it.id }
        assertTrue(legacySessionId in visibleSessionIds)
        assertFalse(domainSessionId in visibleSessionIds)

        legacyRepository.deleteSession(domainSessionId)
        legacyRepository.updatePendingMessageText(DOMAIN_MESSAGE_ID, "旧路径越权更新")
        legacyRepository.updateMessageAudio(
            DOMAIN_MESSAGE_ID,
            path = "/tmp/should-not-be-written.wav",
            format = "wav",
            size = 12L
        )
        legacyRepository.deleteMessageById(DOMAIN_MESSAGE_ID)

        val after = repository.recoverIssue(ISSUE_ID).successValue()
        assertEquals(DOMAIN_MESSAGE_ID, after.core.messages.single().id)
        assertEquals("", after.core.messages.single().text)
        assertTrue(after.core.messages.single().isPending)
        assertEquals(null, after.core.messages.single().audioFilePath)
        assertNotNull(database.chatDao().getSessionById(domainSessionId))
    }

    private suspend fun createDomainPendingMessage() {
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "领域议题",
                initialStageId = STAGE_ID,
                initialStageTitle = "初始阶段",
                initialObjective = "理解问题",
                createdAt = 10L
            )
        ).successValue()
        repository.createExecutionRun(
            CreateExecutionRunCommand(
                run = ExecutionRunEntity(
                    id = RUN_ID,
                    issueId = ISSUE_ID,
                    stageId = STAGE_ID,
                    idempotencyKey = "compat-run-key",
                    createdAt = 15L,
                    updatedAt = 15L
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
        ).successValue()
        repository.appendDomainMessage(
            AppendDomainMessageCommand(
                messageId = DOMAIN_MESSAGE_ID,
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
                compatibilitySessionTitle = "领域议题"
            )
        ).successValue()
    }

    private fun <T> RepositoryResult<T>.successValue(): T {
        return (this as RepositoryResult.Success<T>).value
    }

    companion object {
        private const val ISSUE_ID = "issue-compatibility"
        private const val STAGE_ID = "stage-compatibility"
        private const val RUN_ID = "run-compatibility"
        private const val PARTICIPANT_ID = "participant-compatibility"
        private const val SKILL_ID = "skill-a"
        private const val DOMAIN_MESSAGE_ID = 3001L
    }
}
