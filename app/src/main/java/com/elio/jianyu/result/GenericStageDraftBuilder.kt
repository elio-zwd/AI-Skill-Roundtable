package com.elio.jianyu.result

object GenericStageDraftBuilder {
    private const val TEMPLATE = """## 阶段概述

## 已形成的判断

## 主要分歧

## 行动项

## 待确认事项"""

    fun build(
        sourceMessages: List<StageMessageCandidate> = emptyList(),
    ): StageDraftSeed {
        val orderedSources = sourceMessages.sortedWith(
            compareBy<StageMessageCandidate> { it.timestamp }.thenBy { it.messageId },
        )
        val content = if (orderedSources.isEmpty()) {
            TEMPLATE
        } else {
            buildString {
                append(TEMPLATE)
                append("\n\n## 选定来源消息")
                orderedSources.forEachIndexed { index, message ->
                    append("\n\n### 来源消息 ")
                    append(index + 1)
                    if (message.senderName.isNotBlank()) {
                        append(" · ")
                        append(message.senderName)
                    }
                    append("\n\n")
                    append(message.text)
                }
            }
        }
        return StageDraftSeed(
            content = content,
            sourceMessages = orderedSources,
            confirmed = false,
        )
    }
}
