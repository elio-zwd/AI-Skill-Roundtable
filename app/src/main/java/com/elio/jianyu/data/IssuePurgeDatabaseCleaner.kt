package com.elio.jianyu.data

/**
 * 彻底清除数据库阶段。
 *
 * 调用前必须完成所有正式文件清理。全部 SQL 位于同一 Room 事务；任一失败会保留 Issue、
 * Lifecycle 与 Purge Operation，供协调器记录 `purge_database_failed` 并显式重试。
 */
class IssuePurgeDatabaseCleaner(
    database: RoundtableDatabase,
) {
    private val transactions = JianyuRepositoryTransactions(database)

    suspend fun purge(
        operationId: String,
        purgedAt: Long,
    ): RepositoryResult<Unit> {
        if (operationId.isBlank() || purgedAt <= 0L) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation("purge_issue_database", "invalid_argument"),
            )
        }
        return transactions.databaseTransaction("purge_issue_database") {
            val dao = issueLifecycleV12Dao()
            val operation = dao.getPurgeOperation(operationId)
                ?: return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("purge_operation", operationId),
                )
            if (operation.state != IssuePurgeState.DATABASE_PURGING) {
                return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "purge_issue_database",
                        "purge_database_phase_required",
                    ),
                )
            }
            val issueId = operation.issueId
            val lifecycle = jianyuRepositoryDao().getIssueLifecycle(issueId)
                ?: return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.CompatibilityFailure(
                        "purge_issue_database",
                        "missing_issue_lifecycle",
                    ),
                )
            if (lifecycle.state != IssueLifecycleState.TRASHED || lifecycle.purgeRequestedAt == null) {
                return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "purge_issue_database",
                        "purge_lifecycle_changed",
                    ),
                )
            }

            val compatibilitySessionId = dao.getCompatibilitySessionId(issueId)

            // 保留其他目标议题，只降级其来源关系；当前 Issue 作为目标的关系随当前主线删除。
            dao.markRelationSourcesPurged(issueId, purgedAt)
            dao.deleteRelationsTargetingIssue(issueId)

            // 所有来源表必须先于 Message、Run、Draft、Artifact 和 Material Usage 删除。
            dao.deleteArtifactMessageSources(issueId)
            dao.deleteArtifactRunSources(issueId)
            dao.deleteArtifactDraftSources(issueId)
            dao.deleteArtifactMaterialSources(issueId)
            dao.deleteMessageUsages(issueId)
            dao.deleteCrossDiscussions(issueId)
            dao.deleteAudioAssets(issueId)

            // Advancement 子表引用 Stage、Artifact 与 Material，必须优先清除。
            dao.deleteStageAdvancementMeasures(issueId)
            dao.deleteStageAdvancementSkillMembers(issueId)
            dao.deleteStageAdvancementMaterials(issueId)
            dao.deleteStageAdvancementArtifacts(issueId)
            dao.deleteStageAdvancements(issueId)

            // Participant State 可能引用输出 Message；Message 又引用 Participant 与 Run。
            dao.deleteParticipantStates(issueId)
            dao.deleteRunBudgets(issueId)
            dao.deleteMessages(issueId)
            dao.deleteParticipantSnapshots(issueId)
            dao.clearRunRetryReferences(issueId)
            dao.deleteExecutionRuns(issueId)

            dao.deleteArtifacts(issueId)
            dao.deleteDraftRevisions(issueId)
            dao.deleteDrafts(issueId)
            dao.deleteMaterialUsages(issueId)
            dao.deleteMaterialReferences(issueId)
            dao.deletePersonalContextUsages(issueId)
            dao.deleteStages(issueId)

            dao.deleteResumeEvents(issueId)
            dao.deleteArchiveEvents(issueId)

            compatibilitySessionId?.let { sessionId ->
                dao.deleteCompatibilitySessionIfExclusive(issueId, sessionId)
            }

            dao.deleteIssueLifecycle(issueId)
            dao.deletePurgeOperationForIssue(issueId)
            if (dao.deleteIssue(issueId) != 1) {
                throw IllegalStateException("Issue final delete failed")
            }

            val foreignKeyFailure = openHelper.writableDatabase.query(
                "PRAGMA foreign_key_check",
            ).use { cursor -> cursor.moveToFirst() }
            if (foreignKeyFailure) {
                throw IllegalStateException("foreign_key_check failed")
            }
            RepositoryResult.Success(Unit)
        }
    }
}
