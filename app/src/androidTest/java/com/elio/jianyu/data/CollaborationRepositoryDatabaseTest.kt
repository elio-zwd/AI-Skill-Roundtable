package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollaborationRepositoryDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: JianyuRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(
            database = database,
            officialSkillIdValidator = OfficialSkillIdValidator { true },
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun directedCreationAtomicallyPersistsUserRunParticipantBudgetAndActualMessageSnapshot() = runBlocking {
        saveIssue()
        appendHistory(MESSAGE_HISTORY_ID, STAGE_ID, "历史正文\r\n第二行")
        val command = directedCommand(selectedMessageIds = listOf(MESSAGE_HISTORY_ID))

        val first = repository.createDirectedInteraction(command).successValue()
        val repeated = repository.createDirectedInteraction(command)
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        val usage = repository.listExecutionMessageUsage(DIRECTED_RUN_ID).successValue()

        assertEquals(ExecutionRunKind.DIRECTED_RESPONSE, first.runtime.run.runKind)
        assertEquals(ExecutionHistoryScope.EXPLICIT_MESSAGES, first.runtime.run.historyScope)
        assertEquals(USER_MESSAGE_ID, first.runtime.run.triggerMessageId)
        assertEquals(1, first.runtime.participants.size)
        assertEquals(2, recovery.core.messages.size)
        assertEquals(1, usage.size)
        assertEquals("历史正文\r\n第二行", usage.single().contentSnapshot)
        assertEquals(
            ContextContentHasher.hash("历史正文\r\n第二行"),
            usage.single().contentHash,
        )
        assertEquals(0, usage.single().usageOrder)
        assertTrue((repeated as RepositoryResult.Success).idempotent)
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun standardCreationAtomicallyPersistsFullRosterAndSupportsExactReplay() = runBlocking {
        saveIssue()
        val command = standardCommand()

        val first = repository.createStandardInteraction(command).successValue()
        val repeated = repository.createStandardInteraction(command)
        val conflicting = repository.createStandardInteraction(
            command.copy(
                userMessage = command.userMessage.copy(text = "同一操作不能替换原问题"),
            ),
        )
        val recovery = repository.recoverIssue(ISSUE_ID).successValue()
        val usage = repository.listExecutionMessageUsage(STANDARD_RUN_ID).successValue()

        assertEquals(ExecutionRunKind.STANDARD, first.runtime.run.runKind)
        assertEquals(ExecutionHistoryScope.FULL_STAGE, first.runtime.run.historyScope)
        assertEquals(STANDARD_USER_MESSAGE_ID, first.runtime.run.triggerMessageId)
        assertEquals(
            listOf("study-planner", "research-fact-checker"),
            first.runtime.participants.map { it.sourceId },
        )
        assertEquals(STANDARD_RUN_ID, first.runtime.budget.rootRunId)
        assertEquals(listOf(STANDARD_USER_MESSAGE_ID), recovery.core.messages.map { it.id })
        assertTrue(usage.isEmpty())
        assertTrue((repeated as RepositoryResult.Success).idempotent)
        assertTrue(conflicting is RepositoryResult.Failure)
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun invalidCrossStageMessageSelectionRollsBackEveryDirectedFact() = runBlocking {
        saveIssue()
        val secondStage = repository.createStage(
            CreateStageCommand(
                issueId = ISSUE_ID,
                stageId = OTHER_STAGE_ID,
                title = "Other",
                objective = "Other",
                createdAt = 120L,
            ),
        ).successValue()
        appendHistory(MESSAGE_HISTORY_ID, secondStage.id, "其他阶段正文")

        val result = repository.createDirectedInteraction(
            directedCommand(selectedMessageIds = listOf(MESSAGE_HISTORY_ID)),
        )

        assertTrue(result is RepositoryResult.Failure)
        assertNull(database.jianyuRepositoryDao().getExecutionRun(DIRECTED_RUN_ID))
        assertTrue(database.jianyuRepositoryDao().getParticipantSnapshots(DIRECTED_RUN_ID).isEmpty())
        assertTrue(database.jianyuRepositoryDao().getParticipantStates(DIRECTED_RUN_ID).isEmpty())
        assertTrue(database.collaborationDao().getMessageUsageSnapshotsForRun(DIRECTED_RUN_ID).isEmpty())
        assertNull(database.jianyuRepositoryDao().getMessage(USER_MESSAGE_ID))
        assertNull(database.jianyuRepositoryDao().getRunBudget(DIRECTED_RUN_ID))
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun crossResponseAndSynthesisShareBudgetAndSnapshotOnlySuccessfulOutputs() = runBlocking {
        saveIssue()
        val response = repository.createCrossDiscussionResponse(crossResponseCommand()).successValue()
        assertFalse(response.runtime.budget.closed)

        markParticipantSucceeded(
            response.runtime.participants[0],
            RESPONSE_MESSAGE_A,
            "成员 A 原始观点",
            210L,
        )
        markParticipantSucceeded(
            response.runtime.participants[1],
            RESPONSE_MESSAGE_B,
            "成员 B 保留分歧",
            220L,
        )
        repository.transitionRun(
            TransitionRunCommand(
                runId = RESPONSE_RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.RUNNING,
                updatedAt = 205L,
                startedAt = 205L,
            ),
        ).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = RESPONSE_RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.RUNNING),
                newStatus = ExecutionRunStatus.SUCCEEDED,
                updatedAt = 230L,
                startedAt = 205L,
                finishedAt = 230L,
            ),
        ).successValue()
        repository.transitionCrossDiscussion(
            TransitionCrossDiscussionCommand(
                sessionId = DISCUSSION_ID,
                expectedStatuses = setOf(CrossDiscussionStatus.RESPONDING),
                newStatus = CrossDiscussionStatus.AWAITING_SYNTHESIS,
                successfulParticipantIds = listOf("study-planner", "research-fact-checker"),
                updatedAt = 231L,
            ),
        ).successValue()

        val synthesis = repository.createCrossDiscussionSynthesis(
            synthesisCommand(),
        ).successValue()
        val usage = repository.listExecutionMessageUsage(SYNTHESIS_RUN_ID).successValue()

        assertEquals(ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS, synthesis.runtime.run.runKind)
        assertEquals(RESPONSE_RUN_ID, synthesis.runtime.run.parentRunId)
        assertNull(synthesis.runtime.run.retryOfRunId)
        assertEquals(RESPONSE_RUN_ID, synthesis.runtime.budget.rootRunId)
        assertEquals(
            listOf(RESPONSE_MESSAGE_A, RESPONSE_MESSAGE_B),
            usage.map { it.sourceMessageId },
        )
        assertEquals(listOf(0, 1), usage.map { it.usageOrder })
        assertEquals(
            listOf("成员 A 原始观点", "成员 B 保留分歧"),
            usage.map { it.contentSnapshot },
        )
        assertEquals(CrossDiscussionStatus.SYNTHESIZING, synthesis.discussion?.status)
        assertEquals(SYNTHESIS_RUN_ID, synthesis.discussion?.synthesisRunId)
        assertEquals(0, foreignKeyViolations())
    }

    private suspend fun saveIssue() {
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "Issue",
                initialStageId = STAGE_ID,
                initialStageTitle = "Stage",
                initialObjective = "Objective",
                createdAt = 100L,
            ),
        ).successValue()
    }

    private suspend fun appendHistory(messageId: Long, stageId: String, text: String) {
        repository.appendDomainMessage(
            AppendDomainMessageCommand(
                messageId = messageId,
                issueId = ISSUE_ID,
                stageId = stageId,
                executionRunId = null,
                participantSnapshotId = null,
                senderId = "user",
                senderName = "你",
                avatar = "我",
                text = text,
                timestamp = 130L,
                isPending = false,
                roundIndex = 0,
                compatibilitySessionTitle = "Issue",
            ),
        ).successValue()
    }

    private fun directedCommand(
        selectedMessageIds: List<Long>,
    ): CreateDirectedInteractionCommand = CreateDirectedInteractionCommand(
        userMessage = userMessage(USER_MESSAGE_ID, "本次点名问题", 150L),
        run = ExecutionRunEntity(
            id = DIRECTED_RUN_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            triggerMessageId = USER_MESSAGE_ID,
            idempotencyKey = "directed-command",
            createdAt = 150L,
            updatedAt = 150L,
            runKind = ExecutionRunKind.DIRECTED_RESPONSE,
            historyScope = if (selectedMessageIds.isEmpty()) {
                ExecutionHistoryScope.NO_HISTORY
            } else {
                ExecutionHistoryScope.EXPLICIT_MESSAGES
            },
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
        ),
        participant = participant(DIRECTED_RUN_ID, "study-planner", 0, 150L),
        budget = ExecutionRuntimeBudgetConfig(),
        selectedMessageIds = selectedMessageIds,
    )

    private fun standardCommand(): CreateStandardInteractionCommand =
        CreateStandardInteractionCommand(
            userMessage = userMessage(
                id = STANDARD_USER_MESSAGE_ID,
                text = "继续追问当前阵容",
                time = 140L,
            ),
            run = ExecutionRunEntity(
                id = STANDARD_RUN_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                triggerMessageId = STANDARD_USER_MESSAGE_ID,
                idempotencyKey = "standard-command",
                createdAt = 140L,
                updatedAt = 140L,
                runKind = ExecutionRunKind.STANDARD,
                historyScope = ExecutionHistoryScope.FULL_STAGE,
                actualModelId = "gemini-3.6-flash",
                actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
                thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
            ),
            participants = listOf(
                participant(STANDARD_RUN_ID, "study-planner", 0, 140L),
                participant(STANDARD_RUN_ID, "research-fact-checker", 1, 140L),
            ),
            budget = ExecutionRuntimeBudgetConfig(),
        )

    private fun crossResponseCommand(): CreateCrossDiscussionResponseCommand {
        val userMessage = userMessage(CROSS_USER_MESSAGE_ID, "交叉讨论焦点", 180L)
        val run = ExecutionRunEntity(
            id = RESPONSE_RUN_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            triggerMessageId = CROSS_USER_MESSAGE_ID,
            idempotencyKey = "response-command",
            createdAt = 180L,
            updatedAt = 180L,
            runKind = ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
            discussionId = DISCUSSION_ID,
            historyScope = ExecutionHistoryScope.NO_HISTORY,
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.HIGH,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
        )
        return CreateCrossDiscussionResponseCommand(
            userMessage = userMessage,
            session = CrossDiscussionSessionEntity(
                id = DISCUSSION_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                triggerMessageId = CROSS_USER_MESSAGE_ID,
                responseRunId = RESPONSE_RUN_ID,
                integratorSkillId = "meeting-to-action",
                status = CrossDiscussionStatus.RESPONDING,
                idempotencyKey = "discussion-command",
                createdAt = 180L,
                updatedAt = 180L,
            ),
            run = run,
            participants = listOf(
                participant(RESPONSE_RUN_ID, "study-planner", 0, 180L),
                participant(RESPONSE_RUN_ID, "research-fact-checker", 1, 180L),
            ),
            budget = ExecutionRuntimeBudgetConfig(),
        )
    }

    private fun synthesisCommand(): CreateCrossDiscussionSynthesisCommand =
        CreateCrossDiscussionSynthesisCommand(
            sessionId = DISCUSSION_ID,
            run = ExecutionRunEntity(
                id = SYNTHESIS_RUN_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                triggerMessageId = CROSS_USER_MESSAGE_ID,
                idempotencyKey = "synthesis-command",
                createdAt = 240L,
                updatedAt = 240L,
                runKind = ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS,
                parentRunId = RESPONSE_RUN_ID,
                discussionId = DISCUSSION_ID,
                historyScope = ExecutionHistoryScope.EXPLICIT_MESSAGES,
                actualModelId = "gemini-3.6-flash",
                actualThinkingLevel = ExecutionThinkingLevel.HIGH,
                thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
            ),
            participant = participant(SYNTHESIS_RUN_ID, "meeting-to-action", 0, 240L),
            userAcceptedPartial = false,
            createdAt = 240L,
        )

    private suspend fun markParticipantSucceeded(
        participant: ExecutionParticipantSnapshotEntity,
        messageId: Long,
        text: String,
        time: Long,
    ) {
        repository.appendDomainMessage(
            AppendDomainMessageCommand(
                messageId = messageId,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                executionRunId = RESPONSE_RUN_ID,
                participantSnapshotId = participant.id,
                senderId = participant.sourceId,
                senderName = participant.displayName,
                avatar = participant.avatar,
                text = text,
                timestamp = time,
                isPending = false,
                roundIndex = 1,
                compatibilitySessionTitle = "Issue",
            ),
        ).successValue()
        repository.transitionExecutionParticipant(
            TransitionExecutionParticipantCommand(
                participantSnapshotId = participant.id,
                runId = participant.runId,
                expectedStatuses = setOf(ExecutionParticipantStatus.QUEUED),
                newStatus = ExecutionParticipantStatus.RUNNING,
                startedAt = time,
                updatedAt = time,
            ),
        ).successValue()
        repository.transitionExecutionParticipant(
            TransitionExecutionParticipantCommand(
                participantSnapshotId = participant.id,
                runId = participant.runId,
                expectedStatuses = setOf(ExecutionParticipantStatus.RUNNING),
                newStatus = ExecutionParticipantStatus.SUCCEEDED,
                outputMessageId = messageId,
                startedAt = time,
                finishedAt = time + 1,
                updatedAt = time + 1,
            ),
        ).successValue()
    }

    private fun participant(
        runId: String,
        skillId: String,
        position: Int,
        createdAt: Long,
    ) = ExecutionParticipantSnapshotEntity(
        id = "$runId-participant-$position",
        runId = runId,
        sourceType = "official_skill",
        sourceId = skillId,
        displayName = skillId,
        avatar = skillId.take(1),
        skillAssetPath = "skills/$skillId/SKILL.md",
        systemPrompt = "冻结提示词-$skillId",
        configurationJson = "{}",
        defaultResponsibility = "职责-$position",
        position = position,
        createdAt = createdAt,
    )

    private fun userMessage(id: Long, text: String, time: Long) = AppendDomainMessageCommand(
        messageId = id,
        issueId = ISSUE_ID,
        stageId = STAGE_ID,
        executionRunId = null,
        participantSnapshotId = null,
        senderId = "user",
        senderName = "你",
        avatar = "我",
        text = text,
        timestamp = time,
        isPending = false,
        roundIndex = 1,
        compatibilitySessionTitle = "Issue",
    )

    private fun foreignKeyViolations(): Int = database.openHelper.writableDatabase
        .query("PRAGMA foreign_key_check")
        .use { it.count }

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private companion object {
        const val ISSUE_ID = "collaboration-issue"
        const val STAGE_ID = "collaboration-stage"
        const val OTHER_STAGE_ID = "collaboration-stage-other"
        const val DIRECTED_RUN_ID = "directed-run"
        const val STANDARD_RUN_ID = "standard-run"
        const val RESPONSE_RUN_ID = "response-run"
        const val SYNTHESIS_RUN_ID = "synthesis-run"
        const val DISCUSSION_ID = "discussion-12345678"
        const val MESSAGE_HISTORY_ID = 10_001L
        const val USER_MESSAGE_ID = 10_002L
        const val STANDARD_USER_MESSAGE_ID = 10_006L
        const val CROSS_USER_MESSAGE_ID = 10_003L
        const val RESPONSE_MESSAGE_A = 10_004L
        const val RESPONSE_MESSAGE_B = 10_005L
    }
}
