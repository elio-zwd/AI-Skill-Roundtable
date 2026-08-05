package com.elio.jianyu.result

import com.elio.jianyu.data.Message

object StageMessageSelectionPolicy {
    fun select(
        issueId: String,
        stageId: String,
        messages: List<Message>,
        selectedMessageIds: List<Long>,
    ): StageMessageSelectionResult {
        if (selectedMessageIds.size != selectedMessageIds.distinct().size) {
            return StageMessageSelectionResult.Rejected(
                StageMessageSelectionError.DUPLICATE_MESSAGE_ID,
            )
        }
        if (selectedMessageIds.isEmpty()) {
            return StageMessageSelectionResult.Selected(emptyList())
        }

        val selectedIdSet = selectedMessageIds.toSet()
        val duplicatedAvailableId = messages.asSequence()
            .filter { it.id in selectedIdSet }
            .groupingBy { it.id }
            .eachCount()
            .any { it.value > 1 }
        if (duplicatedAvailableId) {
            return StageMessageSelectionResult.Rejected(
                StageMessageSelectionError.DUPLICATE_MESSAGE_ID,
            )
        }

        val messageById = messages.associateBy { it.id }
        val selected = ArrayList<StageMessageCandidate>(selectedMessageIds.size)
        selectedMessageIds.forEach { messageId ->
            val message = messageById[messageId]
                ?: return StageMessageSelectionResult.Rejected(
                    StageMessageSelectionError.MESSAGE_NOT_FOUND,
                )
            if (message.issueId != issueId || message.stageId != stageId) {
                return StageMessageSelectionResult.Rejected(
                    StageMessageSelectionError.MESSAGE_SCOPE_MISMATCH,
                )
            }
            if (message.isPending) {
                return StageMessageSelectionResult.Rejected(
                    StageMessageSelectionError.MESSAGE_PENDING,
                )
            }
            selected += StageMessageCandidate(
                messageId = message.id,
                runId = message.executionRunId,
                senderName = message.senderName,
                text = message.text,
                timestamp = message.timestamp,
                pending = false,
            )
        }

        return StageMessageSelectionResult.Selected(
            selected.sortedWith(
                compareBy<StageMessageCandidate> { it.timestamp }.thenBy { it.messageId },
            ),
        )
    }
}
