package com.elio.jianyu.execution

import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.StageEntity

data class ExecutionContextInput(
    val issue: IssueEntity,
    val stage: StageEntity,
    val participant: ExecutionParticipantSnapshotEntity,
    val currentRunId: String,
    val currentUserInput: String,
    val roundIndex: Int,
    val history: List<Message>,
    val contributions: List<ExecutionContextContribution> = emptyList(),
    val maxContextCharacters: Int = 24_000,
) {
    init {
        require(issue.id == stage.issueId)
        require(participant.runId == currentRunId)
        require(currentUserInput.isNotBlank())
        require(roundIndex >= 0)
        require(maxContextCharacters > 0)
    }
}

data class ExecutionModelRequest(
    val systemInstruction: String,
    val userContent: String,
)

/**
 * 构建冻结 Skill 的模型上下文。不会读取 Catalog 当前 Prompt，也不会自动注入资料或个人背景。
 */
class ExecutionContextBuilder {
    fun build(input: ExecutionContextInput): ExecutionModelRequest {
        val systemInstruction = input.participant.systemPrompt.trim()
        require(systemInstruction.isNotBlank()) { "冻结参与者缺少 System Prompt" }

        val eligibleHistory = input.history
            .asSequence()
            .filter { message ->
                message.issueId == input.issue.id &&
                    message.stageId == input.stage.id &&
                    !message.isPending &&
                    message.executionRunId != input.currentRunId
            }
            .sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
            .toList()

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
                    "阶段既有记录：\n" + eligibleHistory.joinToString("\n") { message ->
                        "${message.senderName}: ${message.text.trim()}"
                    },
                )
            }
            if (input.contributions.isNotEmpty()) {
                add(
                    "用户明确确认的上下文：\n" + input.contributions.joinToString("\n") {
                        contribution -> "[${contribution.sourceType}:${contribution.sourceId}] " +
                            contribution.content.trim()
                    },
                )
            }
            add("用户当前问题：${input.currentUserInput.trim()}")
            add("请直接给出你的独立观点；不要假设已经看到同一批次其他参与者的回答。")
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
}
