package com.example.skillroundtable.roundtable

import com.example.skillroundtable.data.Message
import com.example.skillroundtable.viewmodel.RoundActionState

object RoundActionStateResolver {
    fun resolve(
        selectedParticipantIds: List<String>,
        messagesSinceRun: List<Message>,
        isBudgetExceeded: Boolean
    ): RoundActionState {
        if (isBudgetExceeded) {
            return RoundActionState.BUDGET_EXCEEDED
        }
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
