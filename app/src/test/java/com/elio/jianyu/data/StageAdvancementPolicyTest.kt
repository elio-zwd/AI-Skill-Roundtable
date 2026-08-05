package com.elio.jianyu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StageAdvancementPolicyTest {
    @Test
    fun dualDirectionMeasuresUseStableProductOrderAndStillDescribeOneStage() {
        val command = command(
            realitySupport = true,
            thinkingExpansion = true,
            measures = listOf(
                StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS,
                StageAdvancementMeasure.FORM_EXECUTION_PLAN,
                StageAdvancementMeasure.CLARIFY_NEXT_STEP,
                StageAdvancementMeasure.INTRODUCE_COUNTERARGUMENT,
            ),
        )

        val normalized = StageAdvancementPolicy.normalize(command)

        assertEquals(
            listOf(
                StageAdvancementMeasure.CLARIFY_NEXT_STEP,
                StageAdvancementMeasure.FORM_EXECUTION_PLAN,
                StageAdvancementMeasure.INTRODUCE_COUNTERARGUMENT,
                StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS,
            ),
            normalized.measures,
        )
        assertEquals("stage-new", normalized.newStageId)
    }

    @Test
    fun directionAndObjectiveAreRequired() {
        assertThrows(IllegalArgumentException::class.java) {
            StageAdvancementPolicy.normalize(
                command(realitySupport = false, thinkingExpansion = false),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StageAdvancementPolicy.normalize(command(objective = "  "))
        }
    }

    @Test
    fun measureFromUnselectedDirectionIsRejectedInsteadOfInferredFromCopy() {
        assertThrows(IllegalArgumentException::class.java) {
            StageAdvancementPolicy.normalize(
                command(
                    realitySupport = true,
                    thinkingExpansion = false,
                    measures = listOf(StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS),
                ),
            )
        }
    }

    @Test
    fun payloadHashIsStableAcrossInputMeasureOrder() {
        val first = command(
            measures = listOf(
                StageAdvancementMeasure.SET_CHECKPOINTS,
                StageAdvancementMeasure.CLARIFY_NEXT_STEP,
            ),
        )
        val second = first.copy(measures = first.measures.reversed())

        assertEquals(
            StageAdvancementPayloadHasher.hash(first),
            StageAdvancementPayloadHasher.hash(second),
        )
    }

    @Test
    fun editedObjectiveInvalidatesTheConfirmedPayload() {
        val confirmed = command(objective = "形成可以执行的两周计划")
        val edited = confirmed.copy(objective = "先验证最关键的执行阻碍")

        assertNotEquals(
            StageAdvancementPayloadHasher.hash(confirmed),
            StageAdvancementPayloadHasher.hash(edited),
        )
    }

    private fun command(
        realitySupport: Boolean = true,
        thinkingExpansion: Boolean = false,
        objective: String = "形成下一阶段计划",
        measures: List<StageAdvancementMeasure> = listOf(
            StageAdvancementMeasure.CLARIFY_NEXT_STEP,
        ),
    ) = AdvanceIssueCommand(
        operationId = "advance-operation-1",
        issueId = "issue-1",
        sourceStageId = "stage-current",
        newStageId = "stage-new",
        newStageTitle = "阶段 2",
        objective = objective,
        realitySupport = realitySupport,
        thinkingExpansion = thinkingExpansion,
        measures = measures,
        expectedOutput = "行动计划",
        roster = listOf(
            StageAdvancementSkillPlan(
                officialSkillId = "study-planner",
                position = 0,
                responsibility = "形成执行步骤",
                sourceRunId = "standard-run-1",
                sourceParticipantSnapshotId = "participant-1",
            ),
        ),
        inheritedMaterialIds = listOf("material-1"),
        inheritedArtifactIds = listOf("artifact-1"),
        confirmedAt = 1_000L,
    )
}
