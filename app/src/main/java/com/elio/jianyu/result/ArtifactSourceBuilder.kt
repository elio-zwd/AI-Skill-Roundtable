package com.elio.jianyu.result

import com.elio.jianyu.data.MaterialUsageSnapshotEntity
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.StageSummaryDraftRevisionEntity

object ArtifactSourceBuilder {
    fun build(
        issueId: String,
        stageId: String,
        draftRevision: StageSummaryDraftRevisionEntity,
        selectedMessages: List<Message>,
        materialUsages: List<MaterialUsageSnapshotEntity>,
    ): ArtifactSourceBuildResult {
        if (draftRevision.issueId != issueId || draftRevision.stageId != stageId) {
            return ArtifactSourceBuildResult.Rejected(
                ArtifactSourceError.DRAFT_SCOPE_MISMATCH,
            )
        }
        if (selectedMessages.map { it.id }.distinct().size != selectedMessages.size) {
            return ArtifactSourceBuildResult.Rejected(
                ArtifactSourceError.DUPLICATE_MESSAGE_ID,
            )
        }
        selectedMessages.forEach { message ->
            if (message.issueId != issueId || message.stageId != stageId) {
                return ArtifactSourceBuildResult.Rejected(
                    ArtifactSourceError.MESSAGE_SCOPE_MISMATCH,
                )
            }
            if (message.isPending) {
                return ArtifactSourceBuildResult.Rejected(
                    ArtifactSourceError.MESSAGE_PENDING,
                )
            }
        }

        val orderedMessages = selectedMessages.sortedWith(
            compareBy<Message> { it.timestamp }.thenBy { it.id },
        )
        val runIds = orderedMessages.mapNotNull { it.executionRunId }.distinct().sorted()
        val relevantMaterialUsages = materialUsages.filter { usage ->
            usage.runId != null && usage.runId in runIds
        }
        if (relevantMaterialUsages.any { it.issueId != issueId || it.stageId != stageId }) {
            return ArtifactSourceBuildResult.Rejected(
                ArtifactSourceError.MATERIAL_SCOPE_MISMATCH,
            )
        }

        return ArtifactSourceBuildResult.Ready(
            ArtifactSourcePlan(
                messageIds = orderedMessages.map { it.id },
                runIds = runIds,
                draftRevisionIds = listOf(draftRevision.id),
                materialUsageSnapshotIds = relevantMaterialUsages
                    .sortedWith(compareBy<MaterialUsageSnapshotEntity> { it.createdAt }.thenBy { it.id })
                    .map { it.id }
                    .distinct(),
            ),
        )
    }
}
