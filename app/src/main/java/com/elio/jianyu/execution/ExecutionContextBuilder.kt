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
        val systemInstruction = input.participant.systemPrompt.trim()
        require(systemInstruction.isNotBlank()) { "冻结参与者缺少 System Prompt" }

        val eligibleHistory = when (input.historyScope) {
            ExecutionHistoryScope.NO_HISTORY -> emptyList()
            ExecutionHistoryScope.FULL_STAGE,
            ExecutionHistoryScope.EXPLICIT_MESSAGES -> input.history.sortedWith(
                compareBy<ExecutionHistoryEntry>({ it.usageOrder }, { it.sourceMessageId }),
            )
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
            if (eligibleHistory.isNotEmpty()) {
                add(
                    historyHeading(input.promptMode) + "\n" +
                        eligibleHistory.joinToString("\n") { entry ->
                            "${entry.senderName}: ${entry.content}"
                        },
                )
            }
            if (input.contributions.isNotEmpty()) {
                add(
                    "用户明确确认的上下文：\n" + input.contributions.joinToString("\n") {
                        contribution -> "[${contribution.sourceType}:${contribution.sourceId}] " +
                            contribution.content
                    },
                )
            }
            add(currentInputHeading(input.promptMode) + input.currentUserInput.trim())
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

    private fun historyHeading(mode: ExecutionPromptMode): String = when (mode) {
        ExecutionPromptMode.INDEPENDENT_RESPONSE -> "阶段既有记录："
        ExecutionPromptMode.CROSS_DISCUSSION_SYNTHESIS -> "本次交叉讨论中实际成功的成员原始回应："
    }

    private fun currentInputHeading(mode: ExecutionPromptMode): String = when (mode) {
        ExecutionPromptMode.INDEPENDENT_RESPONSE -> "用户当前问题："
        ExecutionPromptMode.CROSS_DISCUSSION_SYNTHESIS -> "本次交叉讨论焦点："
    }

    private fun finalInstruction(mode: ExecutionPromptMode): String = when (mode) {
        ExecutionPromptMode.INDEPENDENT_RESPONSE ->
            "请直接给出你的独立观点；不要假设已经看到同一批次其他参与者的回答。"
        ExecutionPromptMode.CROSS_DISCUSSION_SYNTHESIS -> SYNTHESIS_INSTRUCTION
    }

    private companion object {
        val SYNTHESIS_INSTRUCTION = """
            请仅依据上方实际成功的成员原始回应进行透明整合，并严格按以下结构输出：
            1. 共识
            2. 分歧
            3. 适用条件
            4. 关键不确定性
            5. 明确建议
            6. 下一步

            不得投票裁决，不得把多数意见包装成事实，不得隐藏少数观点，
            不得替未成功成员补写观点，不得改写参与者原意。
        """.trimIndent()
    }
}
