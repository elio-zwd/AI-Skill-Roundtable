package com.elio.jianyu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDomainModelTest {
    @Test
    fun roundIndexRemainsResponseBatchAndDoesNotRepresentStage() {
        val message = Message(
            chatId = 10L,
            senderId = "character-1",
            senderName = "测试成员",
            avatar = "T",
            text = "response",
            roundIndex = 7,
            issueId = "issue-1",
            stageId = "stage-42"
        )

        assertEquals(7, message.roundIndex)
        assertEquals("stage-42", message.stageId)
        assertNotEquals(message.roundIndex.toString(), message.stageId)
    }

    @Test
    fun executionRunStatusCoversRecoveryStates() {
        val values = ExecutionRunStatus.entries.map { it.storageValue }.toSet()

        assertEquals(
            setOf(
                "not_started",
                "running",
                "partial_success",
                "succeeded",
                "stopped",
                "failed",
                "retryable",
                "completed"
            ),
            values
        )
    }

    @Test
    fun participantSnapshotKeepsHistoricalValues() {
        val snapshot = ExecutionParticipantSnapshotEntity(
            id = "snapshot-1",
            runId = "run-1",
            sourceType = "character",
            sourceId = "character-1",
            displayName = "历史名称",
            avatar = "H",
            skillAssetPath = "skills/history/SKILL.md",
            systemPrompt = "历史 Prompt",
            configurationJson = "{\"temperature\":0.4}",
            defaultResponsibility = "反方审查",
            position = 0,
            createdAt = 100L
        )

        val changedLiveValues = snapshot.copy(
            displayName = "实时新名称",
            systemPrompt = "实时新 Prompt"
        )

        assertEquals("历史名称", snapshot.displayName)
        assertEquals("历史 Prompt", snapshot.systemPrompt)
        assertTrue(changedLiveValues.displayName != snapshot.displayName)
        assertTrue(changedLiveValues.systemPrompt != snapshot.systemPrompt)
    }
}
