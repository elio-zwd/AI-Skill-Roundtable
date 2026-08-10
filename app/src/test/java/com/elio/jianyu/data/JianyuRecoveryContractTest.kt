package com.elio.jianyu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuRecoveryContractTest {
    @Test
    fun recoveryDerivesSuccessfulAndRetryableParticipantIdsFromPersistedState() {
        val run = run(status = ExecutionRunStatus.PARTIAL_SUCCESS)
        val participantA = participant("participant-a", "skill-a", 0)
        val participantB = participant("participant-b", "skill-b", 1)
        val core = recoveryCore(
            run = run,
            participants = listOf(participantA, participantB),
            messages = listOf(
                message(
                    id = 1001L,
                    participantId = participantA.id,
                    senderId = participantA.sourceId,
                    pending = false
                ),
                message(
                    id = 1002L,
                    participantId = participantB.id,
                    senderId = participantB.sourceId,
                    pending = true
                )
            )
        )

        assertEquals(setOf(participantA.id), core.successfulParticipantSnapshotIds())
        assertEquals(setOf(participantB.id), core.retryableParticipantSnapshotIds())
    }

    @Test
    fun completedRunDoesNotExposeRetryableParticipants() {
        val run = run(status = ExecutionRunStatus.SUCCEEDED)
        val participant = participant("participant-a", "skill-a", 0)
        val core = recoveryCore(
            run = run,
            participants = listOf(participant),
            messages = emptyList()
        )

        assertTrue(core.successfulParticipantSnapshotIds().isEmpty())
        assertTrue(core.retryableParticipantSnapshotIds().isEmpty())
    }

    private fun recoveryCore(
        run: ExecutionRunEntity,
        participants: List<ExecutionParticipantSnapshotEntity>,
        messages: List<Message>
    ): IssueRecoveryCore {
        val issue = IssueEntity(
            id = ISSUE_ID,
            title = "议题",
            createdAt = 10L,
            updatedAt = 10L
        )
        val stage = StageEntity(
            id = STAGE_ID,
            issueId = ISSUE_ID,
            sequenceIndex = 0,
            title = "初始阶段",
            objective = "理解问题",
            createdAt = 10L,
            updatedAt = 10L
        )
        return IssueRecoveryCore(
            issue = issue,
            lifecycle = IssueLifecycleEntity(
                issueId = ISSUE_ID,
                state = IssueLifecycleState.ACTIVE,
                stateChangedAt = 10L,
                updatedAt = 10L
            ),
            stages = listOf(stage),
            currentStage = stage,
            runs = listOf(run),
            activeOrRecoverableRuns = if (
                run.status in setOf(
                    ExecutionRunStatus.NOT_STARTED,
                    ExecutionRunStatus.RUNNING,
                    ExecutionRunStatus.PARTIAL_SUCCESS,
                    ExecutionRunStatus.RETRYABLE
                )
            ) {
                listOf(run)
            } else {
                emptyList()
            },
            participants = participants,
            messages = messages,
            pendingMessages = messages.filter { it.isPending }
        )
    }

    private fun run(status: ExecutionRunStatus): ExecutionRunEntity {
        return ExecutionRunEntity(
            id = RUN_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            idempotencyKey = "run-key",
            status = status,
            createdAt = 15L,
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
            updatedAt = 20L
        )
    }

    private fun participant(
        id: String,
        sourceId: String,
        position: Int
    ): ExecutionParticipantSnapshotEntity {
        return ExecutionParticipantSnapshotEntity(
            id = id,
            runId = RUN_ID,
            sourceType = "official_skill",
            sourceId = sourceId,
            displayName = sourceId,
            avatar = "A",
            skillAssetPath = "skills/$sourceId/SKILL.md",
            systemPrompt = "system",
            configurationJson = "{}",
            defaultResponsibility = "",
            position = position,
            createdAt = 15L
        )
    }

    private fun message(
        id: Long,
        participantId: String,
        senderId: String,
        pending: Boolean
    ): Message {
        return Message(
            id = id,
            chatId = 1L,
            senderId = senderId,
            senderName = senderId,
            avatar = "A",
            text = if (pending) "" else "已完成回答",
            timestamp = 25L,
            isPending = pending,
            roundIndex = 1,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = participantId
        )
    }

    companion object {
        private const val ISSUE_ID = "issue-1"
        private const val STAGE_ID = "stage-0"
        private const val RUN_ID = "run-1"
    }
}
