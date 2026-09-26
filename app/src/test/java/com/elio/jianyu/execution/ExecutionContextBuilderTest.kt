package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.StageEntity
import com.elio.jianyu.skill.knowledge.SkillKnowledgeHit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
    fun everyPromptModeRequiresExplicitToolPermissionForRealtimeClaims() {
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
            assertTrue(request.systemInstruction.contains("本次是否提供网页搜索工具由执行请求的权限说明决定"))
            assertTrue(request.systemInstruction.contains("未提供工具时不得声称已经联网检索"))
            assertTrue(request.systemInstruction.contains("未联网核验"))
        }
    }


    @Test
    fun skillKnowledgeIsIncludedBeforeUserConfirmedContextWithoutReplacingRoleCore() {
        val request = ExecutionContextBuilder().build(
            ExecutionContextInput(
                issue = issue,
                stage = stage,
                participant = participant,
                currentRunId = "run-2",
                currentUserInput = "请给出建议",
                roundIndex = 0,
                history = emptyList(),
                skillKnowledge = ExecutionSkillKnowledgeContext(
                    knowledgeMap = "- 费曼研究 [KNOWLEDGE]",
                    hits = listOf(
                        SkillKnowledgeHit(
                            skillId = "skill-a",
                            documentId = "doc-1",
                            relativePath = "references/research.md",
                            title = "费曼研究",
                            headingPath = "教学",
                            content = "先确认自己真正理解了什么。",
                            score = 0.9f,
                            retrievalOrder = 0,
                        ),
                    ),
                ),
                contributions = listOf(
                    ExecutionContextContribution(
                        sourceId = "material-1",
                        sourceType = "material",
                        content = "用户明确选择的资料",
                        contentHash = "hash-1",
                        userConfirmedAt = 200,
                        networkAllowed = true,
                        sensitive = false,
                    ),
                ),
            ),
        )

        assertTrue(request.systemInstruction.startsWith(participant.systemPrompt))
        assertTrue(request.userContent.contains("=== Skill Knowledge Map ==="))
        assertTrue(request.userContent.contains("先确认自己真正理解了什么。"))
        assertTrue(request.userContent.contains("用户明确选择的资料"))
        assertTrue(
            request.userContent.indexOf("=== Skill Knowledge Map ===") <
                request.userContent.indexOf("用户明确确认的资料与个人背景"),
        )
    }

    @Test
    fun skillKnowledgeStillObeysStableContextCharacterLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionContextBuilder().build(
                ExecutionContextInput(
                    issue = issue,
                    stage = stage,
                    participant = participant,
                    currentRunId = "run-2",
                    currentUserInput = "问题",
                    roundIndex = 0,
                    history = emptyList(),
                    skillKnowledge = ExecutionSkillKnowledgeContext(
                        knowledgeMap = "- doc [KNOWLEDGE]",
                        hits = listOf(
                            SkillKnowledgeHit(
                                skillId = "skill-a",
                                documentId = "doc",
                                relativePath = "references/doc.md",
                                title = "doc",
                                headingPath = "主题",
                                content = "长".repeat(500),
                                score = 1f,
                                retrievalOrder = 0,
                            ),
                        ),
                    ),
                    maxContextCharacters = 200,
                ),
            )
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
