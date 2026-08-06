package com.elio.jianyu.collaboration

import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.StageAdvancementSkillMemberEntity
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillExecutionEligibilityResult
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode

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

data class CurrentStageRosterMember(
    val officialSkillId: String,
    val position: Int,
    val responsibility: String,
    val sourceRunId: String?,
    val sourceParticipantSnapshotId: String?,
)

sealed interface CurrentStageRosterSource {
    data class StandardRun(
        val roster: CollaborationRoster,
        val members: List<CurrentStageRosterMember>,
    ) : CurrentStageRosterSource

    data class AdvancementPlan(
        val stageId: String,
        val members: List<CurrentStageRosterMember>,
    ) : CurrentStageRosterSource

    data object NoRoster : CurrentStageRosterSource
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

    fun resolveSource(
        stageId: String,
        runs: List<ExecutionRunEntity>,
        participants: List<ExecutionParticipantSnapshotEntity>,
        plannedMembers: List<StageAdvancementSkillMemberEntity>,
    ): CurrentStageRosterSource {
        val standard = resolve(stageId, runs, participants)
        if (standard != null) {
            return CurrentStageRosterSource.StandardRun(
                roster = standard,
                members = standard.participants.map { participant ->
                    CurrentStageRosterMember(
                        officialSkillId = participant.sourceId,
                        position = participant.position,
                        responsibility = participant.defaultResponsibility,
                        sourceRunId = standard.sourceRunId,
                        sourceParticipantSnapshotId = participant.id,
                    )
                },
            )
        }
        val planned = plannedMembers
            .filter { it.stageId == stageId }
            .sortedWith(compareBy({ it.position }, { it.officialSkillId }))
        if (
            planned.isEmpty() ||
            planned.map { it.officialSkillId }.distinct().size != planned.size ||
            planned.map { it.position }.distinct().size != planned.size
        ) {
            return CurrentStageRosterSource.NoRoster
        }
        return CurrentStageRosterSource.AdvancementPlan(
            stageId = stageId,
            members = planned.map { member ->
                CurrentStageRosterMember(
                    officialSkillId = member.officialSkillId,
                    position = member.position,
                    responsibility = member.responsibility,
                    sourceRunId = member.sourceRunId,
                    sourceParticipantSnapshotId = member.sourceParticipantSnapshotId,
                )
            },
        )
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
    INTEGRATOR_NOT_ELIGIBLE_FOR_SYNTHESIS,
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

object SynthesisSkillEligibilityPolicy {
    const val DEFAULT_INTEGRATOR_SKILL_ID = "meeting-to-action"

    fun validate(
        definition: OfficialSkillDefinition?,
        audit: OfficialSkillExecutionEligibilityResult?,
    ): CollaborationValidationResult {
        if (
            definition == null ||
            audit == null ||
            definition.id != DEFAULT_INTEGRATOR_SKILL_ID ||
            !audit.eligible
        ) {
            return invalid(CollaborationValidationCode.INTEGRATOR_NOT_EXECUTABLE)
        }
        val workflowType = definition.primaryType in setOf(
            OfficialSkillPrimaryType.WORKFLOW_CAPABILITY,
            OfficialSkillPrimaryType.TASK_ASSISTANT,
        )
        val multiInputMode = definition.useMode in setOf(
            OfficialSkillUseMode.BOTH,
            OfficialSkillUseMode.MULTI_PREFERRED,
        )
        val forbidsMultipleViews = definition.integrityBoundaries.any { boundary ->
            val normalized = boundary.lowercase()
            ("不得" in normalized || "禁止" in normalized) &&
                ("多成员" in normalized || "其他成员" in normalized || "多方" in normalized)
        }
        return if (workflowType && multiInputMode && !forbidsMultipleViews) {
            CollaborationValidationResult.Valid
        } else {
            invalid(CollaborationValidationCode.INTEGRATOR_NOT_ELIGIBLE_FOR_SYNTHESIS)
        }
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
