package com.elio.jianyu.collaboration

import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollaborationPoliciesTest {
    @Test
    fun latestStandardRootRunDefinesRosterAndTemporaryRunsNeverReplaceIt() {
        val runs = listOf(
            run("standard-old", ExecutionRunKind.STANDARD, createdAt = 100),
            run("standard-current", ExecutionRunKind.STANDARD, createdAt = 200),
            run("directed-later", ExecutionRunKind.DIRECTED_RESPONSE, createdAt = 300),
            run("synthesis-latest", ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS, createdAt = 400),
        )
        val participants = listOf(
            participant("standard-old", "study-planner", 0),
            participant("standard-current", "study-planner", 0),
            participant("standard-current", "research-fact-checker", 1),
            participant("directed-later", "study-planner", 0),
            participant("synthesis-latest", "meeting-to-action", 0),
        )

        val roster = CurrentStageRosterPolicy.resolve(
            stageId = "stage-1",
            runs = runs,
            participants = participants,
        )

        assertEquals("standard-current", roster?.sourceRunId)
        assertEquals(
            listOf("study-planner", "research-fact-checker"),
            roster?.participants?.map { it.sourceId },
        )
    }

    @Test
    fun retryRunNeverDefinesANewRoster() {
        val runs = listOf(
            run("standard-root", ExecutionRunKind.STANDARD, createdAt = 100),
            run(
                id = "standard-retry",
                kind = ExecutionRunKind.STANDARD,
                createdAt = 200,
                retryOfRunId = "standard-root",
            ),
        )
        val participants = listOf(
            participant("standard-root", "study-planner", 0),
            participant("standard-root", "research-fact-checker", 1),
            participant("standard-retry", "research-fact-checker", 0),
        )

        val roster = CurrentStageRosterPolicy.resolve("stage-1", runs, participants)

        assertEquals("standard-root", roster?.sourceRunId)
        assertEquals(2, roster?.participants?.size)
    }

    @Test
    fun directedResponseAcceptsExactlyOneExecutableRosterMember() {
        val roster = CollaborationRoster(
            sourceRunId = "standard-root",
            participants = listOf(
                participant("standard-root", "study-planner", 0),
                participant("standard-root", "research-fact-checker", 1),
            ),
        )

        assertEquals(
            CollaborationValidationCode.EXACTLY_ONE_PARTICIPANT_REQUIRED,
            DirectedResponsePolicy.validate(
                roster = roster,
                selectedSkillIds = emptyList(),
                executableSkillIds = setOf("study-planner", "research-fact-checker"),
            ).code,
        )
        assertEquals(
            CollaborationValidationCode.NOT_IN_CURRENT_ROSTER,
            DirectedResponsePolicy.validate(
                roster = roster,
                selectedSkillIds = listOf("meeting-to-action"),
                executableSkillIds = setOf("meeting-to-action"),
            ).code,
        )
        assertEquals(
            CollaborationValidationCode.SKILL_NOT_EXECUTABLE,
            DirectedResponsePolicy.validate(
                roster = roster,
                selectedSkillIds = listOf("study-planner"),
                executableSkillIds = emptySet(),
            ).code,
        )
        assertTrue(
            DirectedResponsePolicy.validate(
                roster = roster,
                selectedSkillIds = listOf("study-planner"),
                executableSkillIds = setOf("study-planner"),
            ).valid,
        )
    }

    @Test
    fun standardFollowUpRequiresTheWholeCurrentRosterToRemainExecutable() {
        val roster = CollaborationRoster(
            sourceRunId = "standard-root",
            participants = listOf(
                participant("standard-root", "study-planner", 0),
                participant("standard-root", "research-fact-checker", 1),
            ),
        )

        assertEquals(
            CollaborationValidationCode.NO_ROSTER,
            StandardFollowUpPolicy.validate(
                roster = null,
                executableSkillIds = setOf("study-planner", "research-fact-checker"),
            ).code,
        )
        assertEquals(
            CollaborationValidationCode.SKILL_NOT_EXECUTABLE,
            StandardFollowUpPolicy.validate(
                roster = roster,
                executableSkillIds = setOf("study-planner"),
            ).code,
        )
        assertTrue(
            StandardFollowUpPolicy.validate(
                roster = roster,
                executableSkillIds = setOf("study-planner", "research-fact-checker"),
            ).valid,
        )
    }

    @Test
    fun standardOperationIdsAreDeterministicAndSeparatedFromOtherModes() {
        val first = CollaborationOperationIds.standard("operation-12345678")
        val repeated = CollaborationOperationIds.standard("operation-12345678")
        val directed = CollaborationOperationIds.directed("operation-12345678")

        assertEquals(first, repeated)
        assertEquals("standard-follow-up-operation-12345678", first.runId)
        assertEquals(first.runId, first.idempotencyKey)
        assertTrue(first.userMessageId > 0L)
        assertTrue(first.runId != directed.runId)
        assertTrue(first.userMessageId != directed.userMessageId)
    }

    @Test
    fun crossDiscussionRequiresTwoDistinctExecutableRosterMembersAndTransparentIntegrator() {
        val roster = CollaborationRoster(
            sourceRunId = "standard-root",
            participants = listOf(
                participant("standard-root", "study-planner", 0),
                participant("standard-root", "research-fact-checker", 1),
                participant("standard-root", "report-proposal-writer", 2),
            ),
        )

        assertEquals(
            CollaborationValidationCode.AT_LEAST_TWO_PARTICIPANTS_REQUIRED,
            CrossDiscussionPolicy.validate(
                roster = roster,
                selectedSkillIds = listOf("study-planner"),
                executableSkillIds = setOf("study-planner"),
                integratorSkillId = "meeting-to-action",
                integratorExecutable = true,
            ).code,
        )
        assertEquals(
            CollaborationValidationCode.DUPLICATE_PARTICIPANT,
            CrossDiscussionPolicy.validate(
                roster = roster,
                selectedSkillIds = listOf("study-planner", "study-planner"),
                executableSkillIds = setOf("study-planner"),
                integratorSkillId = "meeting-to-action",
                integratorExecutable = true,
            ).code,
        )
        assertEquals(
            CollaborationValidationCode.INTEGRATOR_NOT_EXECUTABLE,
            CrossDiscussionPolicy.validate(
                roster = roster,
                selectedSkillIds = listOf("study-planner", "research-fact-checker"),
                executableSkillIds = setOf("study-planner", "research-fact-checker"),
                integratorSkillId = "meeting-to-action",
                integratorExecutable = false,
            ).code,
        )
        assertTrue(
            CrossDiscussionPolicy.validate(
                roster = roster,
                selectedSkillIds = listOf("study-planner", "research-fact-checker"),
                executableSkillIds = setOf("study-planner", "research-fact-checker"),
                integratorSkillId = "meeting-to-action",
                integratorExecutable = true,
            ).valid,
        )
    }

    @Test
    fun partialSuccessWaitsForExplicitUserDecisionBeforeSynthesis() {
        val status = CrossDiscussionProgressPolicy.afterResponse(
            participantStatuses = listOf(
                ExecutionParticipantStatus.SUCCEEDED,
                ExecutionParticipantStatus.RETRYABLE,
            ),
        )

        assertEquals(CrossDiscussionStatus.PARTIAL_SUCCESS, status)
        assertTrue(!CrossDiscussionProgressPolicy.canCreateSynthesis(status, userAcceptedPartial = false))
        assertTrue(CrossDiscussionProgressPolicy.canCreateSynthesis(status, userAcceptedPartial = true))
    }

    @Test
    fun allFailedResponseNeverCreatesSynthesis() {
        val status = CrossDiscussionProgressPolicy.afterResponse(
            participantStatuses = listOf(
                ExecutionParticipantStatus.RETRYABLE,
                ExecutionParticipantStatus.FAILED,
            ),
        )

        assertEquals(CrossDiscussionStatus.FAILED, status)
        assertTrue(!CrossDiscussionProgressPolicy.canCreateSynthesis(status, userAcceptedPartial = true))
    }

    private fun run(
        id: String,
        kind: ExecutionRunKind,
        createdAt: Long,
        retryOfRunId: String? = null,
    ) = ExecutionRunEntity(
        id = id,
        issueId = "issue-1",
        stageId = "stage-1",
        triggerMessageId = 1,
        idempotencyKey = "key-$id",
        status = ExecutionRunStatus.SUCCEEDED,
        retryOfRunId = retryOfRunId,
        runKind = kind,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun participant(runId: String, skillId: String, position: Int) =
        ExecutionParticipantSnapshotEntity(
            id = "$runId-participant-$position",
            runId = runId,
            sourceType = "official_skill",
            sourceId = skillId,
            displayName = skillId,
            avatar = skillId.take(1),
            skillAssetPath = "skills/$skillId/SKILL.md",
            systemPrompt = "冻结提示词：$skillId",
            configurationJson = "{}",
            defaultResponsibility = "职责-$position",
            position = position,
            createdAt = 100,
        )
}
