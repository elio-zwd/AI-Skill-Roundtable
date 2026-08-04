package com.elio.jianyu.execution

import com.elio.jianyu.data.ContextContentHasher
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.ContextUsageWriteSet

object ExecutionContextGate {
    fun validate(
        contributions: List<ExecutionContextContribution>,
        usage: ContextUsageWriteSet,
    ): ExecutionFailure? {
        if (contributions.isEmpty() && usage.isEmpty) return null
        if (contributions.any { !it.networkAllowed }) {
            return ExecutionFailure(
                ExecutionErrorCode.CONTEXT_NETWORK_NOT_ALLOWED,
                "所选资料或个人背景尚未允许发送给模型服务，请重新确认或移除。",
            )
        }
        val keys = contributions.map { it.sourceType to it.sourceId }
        if (keys.distinct().size != keys.size) {
            return conflict()
        }
        if (contributions.any { contribution ->
                contribution.content.isBlank() ||
                    contribution.contentHash != ContextContentHasher.hash(contribution.content)
            }
        ) {
            return conflict()
        }
        val usageByKey = buildMap {
            usage.materials.forEach { snapshot ->
                snapshot.materialReferenceId?.let { sourceId ->
                    put(ContextSourceType.MATERIAL.storageValue to sourceId, snapshot)
                }
            }
            usage.personalContexts.forEach { snapshot ->
                snapshot.personalContextEntryId?.let { sourceId ->
                    put(ContextSourceType.PERSONAL_CONTEXT.storageValue to sourceId, snapshot)
                }
            }
        }
        if (usageByKey.size != contributions.size) return conflict()
        val exact = contributions.all { contribution ->
            when (val snapshot = usageByKey[contribution.sourceType to contribution.sourceId]) {
                is com.elio.jianyu.data.MaterialUsageSnapshotEntity ->
                    snapshot.contentSnapshot == contribution.content &&
                        snapshot.contentHash == contribution.contentHash &&
                        snapshot.userConfirmedAt == contribution.userConfirmedAt &&
                        snapshot.networkAllowed == contribution.networkAllowed &&
                        snapshot.sensitive == contribution.sensitive
                is com.elio.jianyu.data.PersonalContextUsageSnapshotEntity ->
                    snapshot.contentSnapshot == contribution.content &&
                        snapshot.contentHash == contribution.contentHash &&
                        snapshot.userConfirmedAt == contribution.userConfirmedAt &&
                        snapshot.networkAllowed == contribution.networkAllowed &&
                        snapshot.sensitive == contribution.sensitive
                else -> false
            }
        }
        return if (exact) null else conflict()
    }

    private fun conflict(): ExecutionFailure = ExecutionFailure(
        ExecutionErrorCode.CONTEXT_USAGE_CONFLICT,
        "上下文确认内容与实际使用快照不一致，请重新查看并确认。",
    )
}
