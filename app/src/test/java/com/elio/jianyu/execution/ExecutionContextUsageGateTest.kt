package com.elio.jianyu.execution

import com.elio.jianyu.data.ContextContentHasher
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.ContextUsageWriteSet
import com.elio.jianyu.data.MaterialUsageSnapshotEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionContextUsageGateTest {
    @Test
    fun networkDeniedFailsBeforeRuntimeCreation() {
        val contribution = contribution(networkAllowed = false)

        val failure = ExecutionContextGate.validate(
            contributions = listOf(contribution),
            usage = ContextUsageWriteSet(
                materials = listOf(materialUsage(contribution)),
            ),
        )

        assertEquals(ExecutionErrorCode.CONTEXT_NETWORK_NOT_ALLOWED, failure?.code)
    }

    @Test
    fun contributionAndUsageMustDescribeTheSameExactSnapshot() {
        val contribution = contribution(networkAllowed = true)
        val mismatched = materialUsage(contribution).copy(contentSnapshot = "被静默替换的正文")

        val failure = ExecutionContextGate.validate(
            contributions = listOf(contribution),
            usage = ContextUsageWriteSet(materials = listOf(mismatched)),
        )

        assertEquals(ExecutionErrorCode.CONTEXT_USAGE_CONFLICT, failure?.code)
    }

    @Test
    fun emptyContextRemainsBackwardCompatible() {
        val failure = ExecutionContextGate.validate(
            contributions = emptyList(),
            usage = ContextUsageWriteSet(),
        )

        assertTrue(failure == null)
    }

    private fun contribution(networkAllowed: Boolean): ExecutionContextContribution {
        val content = "用户确认的精确摘录"
        return ExecutionContextContribution(
            sourceId = "material-1",
            sourceType = ContextSourceType.MATERIAL.storageValue,
            content = content,
            contentHash = ContextContentHasher.hash(content),
            userConfirmedAt = 2_000L,
            networkAllowed = networkAllowed,
            sensitive = true,
        )
    }

    private fun materialUsage(
        contribution: ExecutionContextContribution,
    ): MaterialUsageSnapshotEntity = MaterialUsageSnapshotEntity(
        id = "run-1-material-material-1",
        issueId = "issue-1",
        stageId = "stage-1",
        runId = "run-1",
        materialReferenceId = "material-1",
        titleSnapshot = "资料",
        sourceTypeSnapshot = "note",
        contentSnapshot = contribution.content,
        contentHash = contribution.contentHash,
        userConfirmedAt = contribution.userConfirmedAt,
        createdAt = 2_001L,
        networkAllowed = contribution.networkAllowed,
        sensitive = contribution.sensitive,
    )
}
