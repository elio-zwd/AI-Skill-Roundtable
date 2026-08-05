package com.elio.jianyu.collaboration

import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunKind

data class CollaborationRoster(
    val sourceRunId: String,
    val participants: List<ExecutionParticipantSnapshotEntity>,
) {
    init {
        require(sourceRunId.isNotBlank())
        require(participants.isNotEmpty())
        require(participants.all { it.runId == sourceRunId })
        require(participants.map { it.sourceId }.distinct().size == participants.size)
        require(participants.map { it.position }.distinct().size == participants.size)
    }
}

object CurrentStageRosterPolicy {
    fun resolve(
        stageId: String,
        runs: List<ExecutionRunEntity>,
        participants: List<ExecutionParticipantSnapshotEntity>,
    ): CollaborationRoster? {
        val sourceRun = runs
            .asSequence()
            .filter { run ->
                run.stageId == stageId &&
                    run.runKind == ExecutionRunKind.STANDARD &&
                    run.retryOfRunId == null &&
                    run.parentRunId == null
            }
            .maxWithOrNull(compareBy<ExecutionRunEntity>({ it.createdAt }, { it.id }))
            ?: return null
        val rosterParticipants = participants
            .filter { it.runId == sourceRun.id }
            .sortedWith(compareBy({ it.position }, { it.id }))
        if (rosterParticipants.isEmpty()) return null
        if (rosterParticipants.map { it.sourceId }.distinct().size != rosterParticipants.size) {
            return null
        }
        if (rosterParticipants.map { it.position }.distinct().size != rosterParticipants.size) {
            return null
        }
        return CollaborationRoster(sourceRun.id, rosterParticipants)
    }
}

enum class CollaborationValidationCode {
    VALID,
    NO_ROSTER,
    EXACTLY_ONE_PARTICIPANT_REQUIRED,
    AT_LEAST_TWO_PARTICIPANTS_REQUIRED,
    DUPLICATE_PARTICIPANT,
    NOT_IN_CURRENT_ROSTER,
    SKILL_NOT_EXECUTABLE,
    INTEGRATOR_NOT_EXECUTABLE,
}

data class CollaborationValidationResult(
    val code: CollaborationValidationCode,
) {
    val valid: Boolean
        get() = code == CollaborationValidationCode.VALID

    companion object {
        val Valid = CollaborationValidationResult(CollaborationValidationCode.VALID)
    }
}

object DirectedResponsePolicy {
    fun validate(
        roster: CollaborationRoster?,
        selectedSkillIds: List<String>,
        executableSkillIds: Set<String>,
    ): CollaborationValidationResult {
        if (roster == null) return invalid(CollaborationValidationCode.NO_ROSTER)
        if (selectedSkillIds.size != 1) {
            return invalid(CollaborationValidationCode.EXACTLY_ONE_PARTICIPANT_REQUIRED)
        }
        val selected = selectedSkillIds.single()
        if (roster.participants.none { it.sourceId == selected }) {
            return invalid(CollaborationValidationCode.NOT_IN_CURRENT_ROSTER)
        }
        if (selected !in executableSkillIds) {
            return invalid(CollaborationValidationCode.SKILL_NOT_EXECUTABLE)
        }
        return CollaborationValidationResult.Valid
    }
}

object CrossDiscussionPolicy {
    fun validate(
        roster: CollaborationRoster?,
        selectedSkillIds: List<String>,
        executableSkillIds: Set<String>,
        integratorSkillId: String,
        integratorExecutable: Boolean,
    ): CollaborationValidationResult {
        if (roster == null) return invalid(CollaborationValidationCode.NO_ROSTER)
        if (selectedSkillIds.size < 2) {
            return invalid(CollaborationValidationCode.AT_LEAST_TWO_PARTICIPANTS_REQUIRED)
        }
        if (selectedSkillIds.distinct().size != selectedSkillIds.size) {
            return invalid(CollaborationValidationCode.DUPLICATE_PARTICIPANT)
        }
        val rosterIds = roster.participants.mapTo(mutableSetOf()) { it.sourceId }
        if (selectedSkillIds.any { it !in rosterIds }) {
            return invalid(CollaborationValidationCode.NOT_IN_CURRENT_ROSTER)
        }
        if (selectedSkillIds.any { it !in executableSkillIds }) {
            return invalid(CollaborationValidationCode.SKILL_NOT_EXECUTABLE)
        }
        if (integratorSkillId.isBlank() || !integratorExecutable) {
            return invalid(CollaborationValidationCode.INTEGRATOR_NOT_EXECUTABLE)
        }
        return CollaborationValidationResult.Valid
    }
}

object CrossDiscussionProgressPolicy {
    fun afterResponse(
        participantStatuses: List<ExecutionParticipantStatus>,
    ): CrossDiscussionStatus {
        require(participantStatuses.isNotEmpty())
        val succeeded = participantStatuses.count { it == ExecutionParticipantStatus.SUCCEEDED }
        return when {
            succeeded == participantStatuses.size -> CrossDiscussionStatus.AWAITING_SYNTHESIS
            succeeded > 0 -> CrossDiscussionStatus.PARTIAL_SUCCESS
            else -> CrossDiscussionStatus.FAILED
        }
    }

    fun canCreateSynthesis(
        status: CrossDiscussionStatus,
        userAcceptedPartial: Boolean,
    ): Boolean = when (status) {
        CrossDiscussionStatus.AWAITING_SYNTHESIS -> true
        CrossDiscussionStatus.PARTIAL_SUCCESS -> userAcceptedPartial
        else -> false
    }
}

private fun invalid(code: CollaborationValidationCode) = CollaborationValidationResult(code)
