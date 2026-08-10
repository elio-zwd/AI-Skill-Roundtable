package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.StageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionContextBuilderTest {
    private val issue = IssueEntity(
        id = "issue-1",
        title = "是否转向机器人行业",
        createdAt = 100,
        updatedAt = 100,
    )
    private val stage = StageEntity(
        id = "stage-1",
        issueId = issue.id,
        sequenceIndex = 7,
        title = "评估路径",
        objective = "比较风险和收益",
        createdAt = 100,
        updatedAt = 100,
    )
    private val participant = ExecutionParticipantSnapshotEntity(
        id = "participant-1",
        runId = "run-2",
        sourceType = "official_skill",
        sourceId = "skill-a",
        displayName = "Skill A",
        avatar = "A",
        skillAssetPath = "skills/a/SKILL.md",
        systemPrompt = "从证据和风险出发分析。",
        configurationJson = "{}",
        defaultResponsibility = "关注转型风险",
        position = 0,
        createdAt = 100,
    )

    @Test
    fun sameBatchParticipantOutputIsNotReadByLaterParticipant() {
        val request = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-2",
                currentUserInput = "请给出建议",
                roundIndex = 3,
                history = listOf(
                    history(1, "上一批已确认结论", "run-1", 0),
                    history(2, "同批成员刚输出", "run-2", 1),
                ),
            ),
        )

        assertTrue(request.userContent.contains("上一批已确认结论"))
        assertFalse(request.userContent.contains("同批成员刚输出"))
    }

    @Test
    fun roundIndexIsResponseBatchAndNeverUsesStageSequenceIndex() {
        val request = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-2",
                currentUserInput = "请给出建议",
                roundIndex = 3,
                history = emptyList(),
            ),
        )

        assertTrue(request.userContent.contains("响应批次：3"))
        assertFalse(request.userContent.contains("响应批次：7"))
    }

    @Test
    fun personalContextIsIncludedOnlyThroughExplicitContribution() {
        val withoutContribution = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-2",
                currentUserInput = "请给出建议",
                roundIndex = 0,
                history = emptyList(),
            ),
        )
        val withContribution = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-2",
                currentUserInput = "请给出建议",
                roundIndex = 0,
                history = emptyList(),
                contributions = listOf(
                    ExecutionContextContribution(
                        sourceId = "profile-1",
                        sourceType = "personal_context",
                        content = "用户已确认：具备 Android 与 MCU 联调经验。",
                        contentHash = "hash-1",
                        userConfirmedAt = 200,
                        networkAllowed = true,
                        sensitive = false,
                    ),
                ),
            ),
        )

        assertFalse(withoutContribution.userContent.contains("Android 与 MCU"))
        assertTrue(withContribution.userContent.contains("Android 与 MCU"))
    }

    @Test
    fun defaultResponsibilityDoesNotReplaceSystemPrompt() {
        val request = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-2",
                currentUserInput = "请给出建议",
                roundIndex = 0,
                history = emptyList(),
            ),
        )

        assertTrue(request.systemInstruction.startsWith(participant.systemPrompt))
        assertTrue(request.userContent.contains(participant.defaultResponsibility))
        assertFalse(request.systemInstruction == participant.defaultResponsibility)
    }

    @Test
    fun everyPromptModeDeclaresThatWebSearchAndRealtimeVerificationAreUnavailable() {
        ExecutionPromptMode.entries.forEach { mode ->
            val request = ExecutionContextBuilder().build(
                ExecutionContextInput(
                    issue = issue,
                    stage = stage,
                    participant = participant,
                    currentRunId = "run-2",
                    currentUserInput = "请核验最新政策",
                    roundIndex = 0,
                    history = emptyList(),
                    promptMode = mode,
                ),
            )

            assertTrue(request.systemInstruction.contains("平台能力边界"))
            assertTrue(request.systemInstruction.contains("当前执行没有提供网页搜索工具"))
            assertTrue(request.systemInstruction.contains("不得声称已经联网检索"))
            assertTrue(request.systemInstruction.contains("未联网核验"))
        }
    }

    private fun history(
        id: Long,
        text: String,
        executionRunId: String,
        order: Int,
    ) = ExecutionHistoryEntry(
        sourceMessageId = id,
        senderName = "Skill",
        content = text,
        usageOrder = order,
        sourceExecutionRunId = executionRunId,
        sourceParticipantSnapshotId = "p-$id",
    )
}
