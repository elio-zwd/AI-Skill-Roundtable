package com.elio.jianyu.lifecycle

import com.elio.jianyu.audio.assets.AudioAssetLifecycleService
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RoundtableDatabase

fun interface IssuePurgeImpactProvider {
    suspend fun inspect(issueId: String): RepositoryResult<IssuePurgeImpactSnapshot>
}

/**
 * 从实际 Room 与受控音频文件服务生成不可恢复清理预览。
 *
 * Orphan 仅进入独立报告，不进入正式文件删除集合；外部 Material 定位符只计数，不读取或删除。
 */
class IssuePurgeImpactCalculator(
    private val database: RoundtableDatabase,
    private val audioLifecycleService: AudioAssetLifecycleService,
) : IssuePurgeImpactProvider {
    override suspend fun inspect(issueId: String): RepositoryResult<IssuePurgeImpactSnapshot> {
        if (issueId.isBlank()) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation("inspect_issue_purge", "issue_id_required"),
            )
        }
        return try {
            val lifecycle = database.jianyuRepositoryDao().getIssueLifecycle(issueId)
                ?: return RepositoryResult.Failure(RepositoryError.NotFound("issue", issueId))
            if (lifecycle.state != IssueLifecycleState.TRASHED) {
                return RepositoryResult.Failure(
                    RepositoryError.InvalidState("inspect_issue_purge", "purge_requires_trashed"),
                )
            }

            val dao = database.issueLifecycleV12Dao()
            val counts = linkedMapOf(
                "stages" to dao.countStages(issueId),
                "stage_advancements" to dao.countStageAdvancements(issueId),
                "stage_advancement_measures" to dao.countStageAdvancementMeasures(issueId),
                "stage_advancement_skill_members" to dao.countStageAdvancementSkillMembers(issueId),
                "stage_advancement_materials" to dao.countStageAdvancementMaterials(issueId),
                "stage_advancement_artifacts" to dao.countStageAdvancementArtifacts(issueId),
                "execution_runs" to dao.countExecutionRuns(issueId),
                "execution_participant_snapshots" to dao.countParticipantSnapshots(issueId),
                "execution_participant_states" to dao.countParticipantStates(issueId),
                "execution_run_budgets" to dao.countRunBudgets(issueId),
                "messages" to dao.countMessages(issueId),
                "pending_messages" to dao.countPendingMessages(issueId),
                "cross_discussion_sessions" to dao.countCrossDiscussions(issueId),
                "execution_message_usage_snapshots" to dao.countMessageUsages(issueId),
                "material_references" to dao.countMaterialReferences(issueId),
                "material_usage_snapshots" to dao.countMaterialUsages(issueId),
                "personal_context_usage_snapshots" to dao.countPersonalContextUsages(issueId),
                "stage_summary_drafts" to dao.countDrafts(issueId),
                "stage_summary_draft_revisions" to dao.countDraftRevisions(issueId),
                "confirmed_artifacts" to dao.countArtifacts(issueId),
                "artifact_message_sources" to dao.countArtifactMessageSources(issueId),
                "artifact_run_sources" to dao.countArtifactRunSources(issueId),
                "artifact_draft_sources" to dao.countArtifactDraftSources(issueId),
                "artifact_material_sources" to dao.countArtifactMaterialSources(issueId),
                "audio_assets" to dao.countAudioAssets(issueId),
                "issue_archive_events" to dao.countArchiveEvents(issueId),
                "issue_resume_events" to dao.countResumeEvents(issueId),
                "issue_relations" to dao.countIssueRelations(issueId),
            )
            val audioImpact = audioLifecycleService.inspectPurgeImpact(issueId)
            val assets = audioLifecycleService.listAudioAssetsForIssue(issueId)
            val missingIds = audioImpact.missingAssetIds.toSet()
            val formalFiles = assets.asSequence()
                .filter { it.id !in missingIds }
                .filter { it.deletedAt == null }
                .mapNotNull { asset ->
                    asset.storagePath?.takeIf(String::isNotBlank)?.let { path ->
                        PurgeFileImpact(path, asset.sizeBytes.coerceAtLeast(0L))
                    }
                }
                .sortedBy(PurgeFileImpact::relativePath)
                .toList()
            val externalObjectCount = countExternalMaterialLocators(issueId)
            val relatedCount = database.issueLifecycleV12Dao()
                .listRelationsFromIssue(issueId)
                .count { it.targetIssueId != issueId }

            RepositoryResult.Success(
                IssuePurgeImpactSnapshot(
                    issueId = issueId,
                    databaseCounts = counts,
                    formalFiles = formalFiles,
                    pendingWorkNames = audioImpact.uniqueWorkNames,
                    missingAssetIds = audioImpact.missingAssetIds,
                    orphanRelativePaths = audioImpact.orphanReport.files.map { it.relativePath },
                    relatedIssueCount = relatedCount,
                    externalObjectCount = externalObjectCount,
                ),
            )
        } catch (_: Exception) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure("inspect_issue_purge", retryable = true),
            )
        }
    }

    private fun countExternalMaterialLocators(issueId: String): Int {
        return database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM material_references " +
                "WHERE issueId = ? AND sourceLocator IS NOT NULL AND sourceLocator != ''",
            arrayOf(issueId),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0).coerceAtLeast(0) else 0
        }
    }
}
