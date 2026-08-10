package com.elio.jianyu.collaboration

import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionThinkingLevel
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.data.StageAdvancementSkillMemberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentStageRosterSourceTest {
    @Test
    fun latestStandardRootRunHasPriorityOverAdvancementPlan() {
        val source = CurrentStageRosterPolicy.resolveSource(
            stageId = "stage-2",
            runs = listOf(
                run("standard-old", ExecutionRunKind.STANDARD, createdAt = 100),
                run("directed-newer", ExecutionRunKind.DIRECTED_RESPONSE, createdAt = 300),
                run("standard-current", ExecutionRunKind.STANDARD, createdAt = 200),
            ),
            participants = listOf(
                participant("standard-old", "old-skill", 0),
                participant("directed-newer", "temporary-skill", 0),
                participant("standard-current", "study-planner", 0),
            ),
            plannedMembers = listOf(plan("research-fact-checker", 0)),
        )

        val standard = source as CurrentStageRosterSource.StandardRun
        assertEquals("standard-current", standard.roster.sourceRunId)
        assertEquals(listOf("study-planner"), standard.members.map { it.officialSkillId })
    }

    @Test
    fun advancementPlanDefinesRosterBeforeFirstStandardRun() {
        val source = CurrentStageRosterPolicy.resolveSource(
            stageId = "stage-2",
            runs = listOf(
                run("directed-only", ExecutionRunKind.DIRECTED_RESPONSE, createdAt = 300),
                run("cross-only", ExecutionRunKind.CROSS_DISCUSSION_RESPONSE, createdAt = 400),
            ),
            participants = listOf(
                participant("directed-only", "temporary-a", 0),
                participant("cross-only", "temporary-b", 0),
            ),
            plannedMembers = listOf(
                plan("study-planner", 1),
                plan("research-fact-checker", 0),
            ),
        )

        val plan = source as CurrentStageRosterSource.AdvancementPlan
        assertEquals(
            listOf("research-fact-checker", "study-planner"),
            plan.members.map { it.officialSkillId },
        )
    }

    @Test
    fun noRunAndNoPlanReturnsNoRoster() {
        val source = CurrentStageRosterPolicy.resolveSource(
            stageId = "stage-2",
            runs = emptyList(),
            participants = emptyList(),
            plannedMembers = emptyList(),
        )

        assertTrue(source is CurrentStageRosterSource.NoRoster)
    }

    private fun run(id: String, kind: ExecutionRunKind, createdAt: Long) =
        ExecutionRunEntity(
            id = id,
            issueId = "issue-1",
            stageId = "stage-2",
            triggerMessageId = 1L,
            idempotencyKey = "key-$id",
            status = ExecutionRunStatus.SUCCEEDED,
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
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
            systemPrompt = "prompt-$skillId",
            configurationJson = "{}",
            defaultResponsibility = "职责-$position",
            position = position,
            createdAt = 100L,
        )

    private fun plan(skillId: String, position: Int) =
        StageAdvancementSkillMemberEntity(
            stageId = "stage-2",
            issueId = "issue-1",
            officialSkillId = skillId,
            position = position,
            responsibility = "计划职责-$position",
            sourceRunId = "standard-stage-1",
            sourceParticipantSnapshotId = "snapshot-$position",
            catalogVersionBasis = null,
            confirmedAt = 200L,
        )
}
