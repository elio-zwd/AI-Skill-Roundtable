package com.elio.jianyu.execution

import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionModelDefaultsTest {
    @Test
    fun newExecutionCommandsUseGemini38ByDefault() {
        val selection = ExecutionSkillSelection(officialSkillId = "skill-a")

        val start = ExecutionStartCommand(
            runId = "run-start",
            issueId = "issue-1",
            stageId = "stage-1",
            triggerMessageId = null,
            idempotencyKey = "start-key",
            selections = listOf(selection),
            currentUserInput = "question",
            roundIndex = 0,
            userConfirmedAt = 1L,
        )
        val retry = ExecutionRetryCommand(
            previousRunId = "run-old",
            newRunId = "run-retry",
            idempotencyKey = "retry-key",
            currentUserInput = "question",
            roundIndex = 1,
            userConfirmedAt = 2L,
        )
        val prepared = ExecutionPreparedRunCommand(
            runId = "run-prepared",
            issueId = "issue-1",
            stageId = "stage-1",
            currentUserInput = "question",
            roundIndex = 0,
            userConfirmedAt = 3L,
        )

        listOf(start.model, retry.model, prepared.model).forEach { model ->
            assertEquals("gemini-3.8-flash", model)
        }
    }
}
