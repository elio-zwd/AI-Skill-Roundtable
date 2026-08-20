package com.elio.jianyu.roundtable

import com.elio.jianyu.data.Message
import com.elio.jianyu.viewmodel.RoundActionState

object RoundActionStateResolver {
    fun resolve(
        selectedParticipantIds: List<String>,
        messagesSinceRun: List<Message>,
    ): RoundActionState {
        if (selectedParticipantIds.isEmpty()) {
            return RoundActionState.CONTINUE_ROUND
        }

        val maxRound = messagesSinceRun.filterNot { it.isPending }.maxOfOrNull { it.roundIndex } ?: 1
        val answeredInMaxRound = messagesSinceRun
            .filter { it.roundIndex == maxRound && !it.isPending }
            .map { it.senderId }
            .toSet()

        val allAnswered = selectedParticipantIds.all { it in answeredInMaxRound }
        return if (allAnswered) {
            RoundActionState.START_NEXT_ROUND
        } else {
            RoundActionState.CONTINUE_ROUND
        }
    }
}
