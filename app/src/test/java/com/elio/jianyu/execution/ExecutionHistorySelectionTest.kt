package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.StageEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionHistorySelectionTest {
    private val issue = IssueEntity("issue-1", "议题", 1, 1)
    private val stage = StageEntity("stage-1", issue.id, 0, "阶段", "目标", 1, 1)
    private val participant = ExecutionParticipantSnapshotEntity(
        id = "run-1-participant-0",
        runId = "run-1",
        sourceType = "official_skill",
        sourceId = "study-planner",
        displayName = "学习规划助手",
        avatar = "学",
        skillAssetPath = "skills/study-planner/SKILL.md",
        systemPrompt = "冻结提示词",
        configurationJson = "{}",
        defaultResponsibility = "独立分析",
        position = 0,
        createdAt = 1,
    )

    @Test
    fun explicitMessagesIncludeOnlyPersistedUsageSnapshotsInStableOrder() {
        val request = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-1",
                currentUserInput = "本次问题",
                roundIndex = 1,
                historyScope = ExecutionHistoryScope.EXPLICIT_MESSAGES,
                history = listOf(
                    ExecutionHistoryEntry(
                        sourceMessageId = 2,
                        senderName = "成员 B",
                        content = "第二条实际快照",
                        usageOrder = 1,
                    ),
                    ExecutionHistoryEntry(
                        sourceMessageId = 1,
                        senderName = "成员 A",
                        content = "第一条实际快照",
                        usageOrder = 0,
                    ),
                ),
            ),
        )

        assertTrue(request.userContent.indexOf("第一条实际快照") < request.userContent.indexOf("第二条实际快照"))
        assertTrue(request.userContent.contains("第一条实际快照"))
        assertTrue(request.userContent.contains("第二条实际快照"))
    }

    @Test
    fun noHistoryNeverFallsBackToFullStage() {
        val request = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-1",
                currentUserInput = "本次问题",
                roundIndex = 1,
                historyScope = ExecutionHistoryScope.NO_HISTORY,
                history = emptyList(),
            ),
        )

        assertFalse(request.userContent.contains("阶段既有记录"))
    }

    @Test
    fun synthesisInstructionPreservesDisagreementAndRejectsVoting() {
        val request = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant.copy(
                    sourceId = "meeting-to-action",
                    displayName = "会议行动助手",
                ),
                currentRunId = "run-1",
                currentUserInput = "围绕是否转型机器人行业整合本次讨论",
                roundIndex = 2,
                historyScope = ExecutionHistoryScope.EXPLICIT_MESSAGES,
                history = listOf(
                    ExecutionHistoryEntry(1, "成员 A", "建议转型", 0),
                    ExecutionHistoryEntry(2, "成员 B", "建议先补基础", 1),
                ),
                promptMode = ExecutionPromptMode.CROSS_DISCUSSION_SYNTHESIS,
            ),
        )

        assertTrue(request.userContent.contains("共识"))
        assertTrue(request.userContent.contains("分歧"))
        assertTrue(request.userContent.contains("适用条件"))
        assertTrue(request.userContent.contains("关键不确定性"))
        assertTrue(request.userContent.contains("明确建议"))
        assertTrue(request.userContent.contains("下一步"))
        assertTrue(request.userContent.contains("不得投票裁决"))
        assertTrue(request.userContent.contains("不得改写参与者原意"))
    }
}
