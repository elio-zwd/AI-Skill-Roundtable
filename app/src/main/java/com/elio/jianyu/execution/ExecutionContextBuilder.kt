package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.StageEntity

data class ExecutionHistoryEntry(
    val sourceMessageId: Long,
    val senderName: String,
    val content: String,
    val usageOrder: Int,
    val sourceExecutionRunId: String? = null,
    val sourceParticipantSnapshotId: String? = null,
) {
    init {
        require(sourceMessageId > 0L)
        require(senderName.isNotBlank())
        require(content.isNotBlank())
        require(usageOrder >= 0)
    }
}

enum class ExecutionPromptMode {
    INDEPENDENT_RESPONSE,
    CROSS_DISCUSSION_SYNTHESIS,
}

data class ExecutionContextInput(
    val issue: IssueEntity,
    val stage: StageEntity,
    val participant: ExecutionParticipantSnapshotEntity,
    val currentRunId: String,
    val currentUserInput: String,
    val roundIndex: Int,
    val history: List<ExecutionHistoryEntry>,
    val historyScope: ExecutionHistoryScope = ExecutionHistoryScope.FULL_STAGE,
    val contributions: List<ExecutionContextContribution> = emptyList(),
    val promptMode: ExecutionPromptMode = ExecutionPromptMode.INDEPENDENT_RESPONSE,
    val maxContextCharacters: Int = 24_000,
) {
    init {
        require(issue.id == stage.issueId)
        require(participant.runId == currentRunId)
        require(currentUserInput.isNotBlank())
        require(roundIndex >= 0)
        require(maxContextCharacters > 0)
        require(history.map { it.sourceMessageId }.distinct().size == history.size)
        require(history.map { it.usageOrder }.distinct().size == history.size)
        if (historyScope == ExecutionHistoryScope.NO_HISTORY) {
            require(history.isEmpty()) { "NO_HISTORY 不得携带消息快照" }
        }
    }
}

data class ExecutionModelRequest(
    val systemInstruction: String,
    val userContent: String,
)

/**
 * 构建冻结 Skill 的模型上下文。历史消息只能由调用方通过稳定快照显式提供。
 * 本类型不会读取 Catalog、Room、资料库或个人背景，也不会静默截断上下文。
 */
class ExecutionContextBuilder {
    fun build(input: ExecutionContextInput): ExecutionModelRequest {
        val frozenSkillPrompt = input.participant.systemPrompt.trim()
        require(frozenSkillPrompt.isNotBlank()) { "冻结参与者缺少 System Prompt" }
        val systemInstruction = "$frozenSkillPrompt\n\n$NETWORK_TRUTH_INSTRUCTION"

        val eligibleHistory = when (input.historyScope) {
            ExecutionHistoryScope.NO_HISTORY -> emptyList()
            ExecutionHistoryScope.FULL_STAGE,
            ExecutionHistoryScope.EXPLICIT_MESSAGES -> input.history
                .asSequence()
                .filter { it.sourceExecutionRunId != input.currentRunId }
                .sortedWith(
                    compareBy<ExecutionHistoryEntry>({ it.usageOrder }, { it.sourceMessageId }),
                )
                .toList()
        }

        val sections = buildList {
            add("议题：${input.issue.title.trim()}")
            add("当前阶段：${input.stage.title.trim()}")
            if (input.stage.objective.isNotBlank()) {
                add("阶段目标：${input.stage.objective.trim()}")
            }
            add("响应批次：${input.roundIndex}")
            if (input.participant.defaultResponsibility.isNotBlank()) {
                add("本组合中的关注点：${input.participant.defaultResponsibility.trim()}")
            }
            when (input.promptMode) {
                ExecutionPromptMode.INDEPENDENT_RESPONSE -> {
                    addHistory(eligibleHistory, "阶段既有记录：")
                    addContributions(input.contributions)
                    add("用户当前问题：${input.currentUserInput.trim()}")
                }
                ExecutionPromptMode.CROSS_DISCUSSION_SYNTHESIS -> {
                    add("本次交叉讨论焦点：${input.currentUserInput.trim()}")
                    addContributions(input.contributions)
                    addHistory(
                        eligibleHistory,
                        "用户明确选择的历史消息与本次实际成功成员原始回应：",
                    )
                }
            }
            add(finalInstruction(input.promptMode))
        }

        val content = sections.joinToString("\n\n")
        require(content.length <= input.maxContextCharacters) {
            "执行上下文超过稳定边界"
        }
        return ExecutionModelRequest(
            systemInstruction = systemInstruction,
            userContent = content,
        )
    }

    private fun MutableList<String>.addHistory(
        history: List<ExecutionHistoryEntry>,
        heading: String,
    ) {
        if (history.isEmpty()) return
        add(
            heading + "\n" + history.joinToString("\n") { entry ->
                "${entry.senderName}: ${entry.content}"
            },
        )
    }

    private fun MutableList<String>.addContributions(
        contributions: List<ExecutionContextContribution>,
    ) {
        if (contributions.isEmpty()) return
        add(
            "用户明确确认的资料与个人背景：\n" + contributions.joinToString("\n") {
                contribution -> "[${contribution.sourceType}:${contribution.sourceId}] " +
                    contribution.content
            },
        )
    }

    private fun finalInstruction(mode: ExecutionPromptMode): String = when (mode) {
        ExecutionPromptMode.INDEPENDENT_RESPONSE ->
            "请直接给出你的独立观点；不要假设已经看到同一批次其他参与者的回答。"
        ExecutionPromptMode.CROSS_DISCUSSION_SYNTHESIS -> SYNTHESIS_INSTRUCTION
    }

    private companion object {
        val SYNTHESIS_INSTRUCTION = """
            请仅依据上方明确允许的消息、已确认上下文与实际成功成员原始回应进行透明整合，
            并严格按以下结构输出：
            1. 共识
            2. 分歧
            3. 适用条件
            4. 关键不确定性
            5. 明确建议
            6. 下一步

            不得投票裁决，不得把多数意见包装成事实，不得隐藏少数观点，
            不得替未成功成员补写观点，不得改写参与者原意。
        """.trimIndent()

        val NETWORK_TRUTH_INSTRUCTION = """
            平台能力边界（优先于 Skill 中任何联网要求）：
            当前执行没有提供网页搜索工具，也没有可验证的实时来源。
            不得声称已经联网检索、实时核验或访问了某个来源，不得虚构引用。
            涉及时效性事实时必须明确标记“未联网核验”，并提示用户提供最新资料或自行复核。
        """.trimIndent()
    }
}
