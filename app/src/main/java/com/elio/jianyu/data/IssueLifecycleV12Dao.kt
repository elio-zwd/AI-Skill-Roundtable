package com.elio.jianyu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface IssueLifecycleV12Dao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArchiveEvent(entity: IssueArchiveEventEntity)

    @Query("SELECT * FROM issue_archive_events WHERE id = :eventId LIMIT 1")
    suspend fun getArchiveEvent(eventId: String): IssueArchiveEventEntity?

    @Query("SELECT * FROM issue_archive_events WHERE archiveOperationId = :operationId LIMIT 1")
    suspend fun getArchiveEventByOperation(operationId: String): IssueArchiveEventEntity?

    @Query(
        "SELECT * FROM issue_archive_events WHERE issueId = :issueId " +
            "ORDER BY archivedAt DESC, id DESC LIMIT 1",
    )
    suspend fun getLatestArchiveEvent(issueId: String): IssueArchiveEventEntity?

    @Query(
        "SELECT * FROM issue_archive_events WHERE issueId = :issueId " +
            "ORDER BY archivedAt ASC, id ASC",
    )
    suspend fun listArchiveEvents(issueId: String): List<IssueArchiveEventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResumeEvent(entity: IssueResumeEventEntity)

    @Query("SELECT * FROM issue_resume_events WHERE resumeOperationId = :operationId LIMIT 1")
    suspend fun getResumeEventByOperation(operationId: String): IssueResumeEventEntity?

    @Query(
        "SELECT * FROM issue_resume_events WHERE issueId = :issueId " +
            "ORDER BY resumedAt ASC, id ASC",
    )
    suspend fun listResumeEvents(issueId: String): List<IssueResumeEventEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIssueRelation(entity: IssueRelationEntity)

    @Query("SELECT * FROM issue_relations WHERE operationId = :operationId LIMIT 1")
    suspend fun getIssueRelationByOperation(operationId: String): IssueRelationEntity?

    @Query(
        "SELECT * FROM issue_relations WHERE sourceIssueId = :issueId " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun listRelationsFromIssue(issueId: String): List<IssueRelationEntity>

    @Query(
        "SELECT * FROM issue_relations WHERE targetIssueId = :issueId " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun listRelationsToIssue(issueId: String): List<IssueRelationEntity>

    @Query(
        "UPDATE issue_relations SET sourceIssueId = NULL, sourceArchiveEventId = NULL, " +
            "sourcePurgedAt = :purgedAt WHERE sourceIssueId = :issueId",
    )
    suspend fun markRelationSourcesPurged(issueId: String, purgedAt: Long): Int

    @Query("DELETE FROM issue_relations WHERE targetIssueId = :issueId")
    suspend fun deleteRelationsTargetingIssue(issueId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurgeOperation(entity: IssuePurgeOperationEntity)

    @Update
    suspend fun updatePurgeOperation(entity: IssuePurgeOperationEntity): Int

    @Query("SELECT * FROM issue_purge_operations WHERE id = :operationId LIMIT 1")
    suspend fun getPurgeOperation(operationId: String): IssuePurgeOperationEntity?

    @Query("SELECT * FROM issue_purge_operations WHERE issueId = :issueId LIMIT 1")
    suspend fun getPurgeOperationForIssue(issueId: String): IssuePurgeOperationEntity?

    @Query("SELECT * FROM issue_purge_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun getPurgeOperationByIdempotencyKey(operationId: String): IssuePurgeOperationEntity?

    @Query(
        "SELECT * FROM issue_purge_operations WHERE state IN " +
            "('requested', 'waiting_for_tasks', 'canceling_tasks', 'deleting_files', " +
            "'ready_for_database_purge', 'database_purging', 'failed_retryable') " +
            "ORDER BY updatedAt ASC, id ASC",
    )
    suspend fun listRecoverablePurgeOperations(): List<IssuePurgeOperationEntity>

    @Query("DELETE FROM issue_purge_operations WHERE issueId = :issueId")
    suspend fun deletePurgeOperationForIssue(issueId: String): Int

    @Query("SELECT COUNT(*) FROM stages WHERE issueId = :issueId")
    suspend fun countStages(issueId: String): Long

    @Query("SELECT COUNT(*) FROM stage_advancements WHERE issueId = :issueId")
    suspend fun countStageAdvancements(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM stage_advancement_measures " +
            "WHERE issueId = :issueId",
    )
    suspend fun countStageAdvancementMeasures(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM stage_advancement_skill_members " +
            "WHERE issueId = :issueId",
    )
    suspend fun countStageAdvancementSkillMembers(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM stage_advancement_materials " +
            "WHERE issueId = :issueId",
    )
    suspend fun countStageAdvancementMaterials(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM stage_advancement_artifacts " +
            "WHERE issueId = :issueId",
    )
    suspend fun countStageAdvancementArtifacts(issueId: String): Long

    @Query("SELECT COUNT(*) FROM execution_runs WHERE issueId = :issueId")
    suspend fun countExecutionRuns(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM execution_participant_snapshots " +
            "WHERE runId IN (SELECT id FROM execution_runs WHERE issueId = :issueId)",
    )
    suspend fun countParticipantSnapshots(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM execution_participant_states " +
            "WHERE runId IN (SELECT id FROM execution_runs WHERE issueId = :issueId)",
    )
    suspend fun countParticipantStates(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM execution_run_budgets " +
            "WHERE rootRunId IN (SELECT id FROM execution_runs WHERE issueId = :issueId)",
    )
    suspend fun countRunBudgets(issueId: String): Long

    @Query("SELECT COUNT(*) FROM messages WHERE issueId = :issueId")
    suspend fun countMessages(issueId: String): Long

    @Query("SELECT COUNT(*) FROM messages WHERE issueId = :issueId AND isPending = 1")
    suspend fun countPendingMessages(issueId: String): Long

    @Query("SELECT COUNT(*) FROM cross_discussion_sessions WHERE issueId = :issueId")
    suspend fun countCrossDiscussions(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM execution_message_usage_snapshots " +
            "WHERE runId IN (SELECT id FROM execution_runs WHERE issueId = :issueId) " +
            "OR sourceMessageId IN (SELECT id FROM messages WHERE issueId = :issueId)",
    )
    suspend fun countMessageUsages(issueId: String): Long

    @Query("SELECT COUNT(*) FROM material_references WHERE issueId = :issueId")
    suspend fun countMaterialReferences(issueId: String): Long

    @Query("SELECT COUNT(*) FROM material_usage_snapshots WHERE issueId = :issueId")
    suspend fun countMaterialUsages(issueId: String): Long

    @Query("SELECT COUNT(*) FROM personal_context_usage_snapshots WHERE issueId = :issueId")
    suspend fun countPersonalContextUsages(issueId: String): Long

    @Query("SELECT COUNT(*) FROM stage_summary_drafts WHERE issueId = :issueId")
    suspend fun countDrafts(issueId: String): Long

    @Query("SELECT COUNT(*) FROM stage_summary_draft_revisions WHERE issueId = :issueId")
    suspend fun countDraftRevisions(issueId: String): Long

    @Query("SELECT COUNT(*) FROM confirmed_artifacts WHERE issueId = :issueId")
    suspend fun countArtifacts(issueId: String): Long

    @Query("SELECT COUNT(*) FROM artifact_message_sources WHERE issueId = :issueId")
    suspend fun countArtifactMessageSources(issueId: String): Long

    @Query("SELECT COUNT(*) FROM artifact_run_sources WHERE issueId = :issueId")
    suspend fun countArtifactRunSources(issueId: String): Long

    @Query("SELECT COUNT(*) FROM artifact_draft_sources WHERE issueId = :issueId")
    suspend fun countArtifactDraftSources(issueId: String): Long

    @Query("SELECT COUNT(*) FROM artifact_material_sources WHERE issueId = :issueId")
    suspend fun countArtifactMaterialSources(issueId: String): Long

    @Query("SELECT COUNT(*) FROM audio_assets WHERE issueId = :issueId")
    suspend fun countAudioAssets(issueId: String): Long

    @Query("SELECT COUNT(*) FROM issue_archive_events WHERE issueId = :issueId")
    suspend fun countArchiveEvents(issueId: String): Long

    @Query("SELECT COUNT(*) FROM issue_resume_events WHERE issueId = :issueId")
    suspend fun countResumeEvents(issueId: String): Long

    @Query(
        "SELECT COUNT(*) FROM issue_relations " +
            "WHERE sourceIssueId = :issueId OR targetIssueId = :issueId",
    )
    suspend fun countIssueRelations(issueId: String): Long

    @Query("SELECT legacyChatSessionId FROM issues WHERE id = :issueId LIMIT 1")
    suspend fun getCompatibilitySessionId(issueId: String): Long?

    @Query(
        "SELECT COUNT(*) FROM issues WHERE legacyChatSessionId = :sessionId AND id != :issueId",
    )
    suspend fun countOtherIssueSessionReferences(issueId: String, sessionId: Long): Long

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :sessionId AND issueId != :issueId")
    suspend fun countOtherMessagesForSession(issueId: String, sessionId: Long): Long

    @Query("DELETE FROM artifact_message_sources WHERE issueId = :issueId")
    suspend fun deleteArtifactMessageSources(issueId: String): Int

    @Query("DELETE FROM artifact_run_sources WHERE issueId = :issueId")
    suspend fun deleteArtifactRunSources(issueId: String): Int

    @Query("DELETE FROM artifact_draft_sources WHERE issueId = :issueId")
    suspend fun deleteArtifactDraftSources(issueId: String): Int

    @Query("DELETE FROM artifact_material_sources WHERE issueId = :issueId")
    suspend fun deleteArtifactMaterialSources(issueId: String): Int

    @Query("DELETE FROM audio_assets WHERE issueId = :issueId")
    suspend fun deleteAudioAssets(issueId: String): Int

    @Query(
        "DELETE FROM execution_message_usage_snapshots " +
            "WHERE runId IN (SELECT id FROM execution_runs WHERE issueId = :issueId) " +
            "OR sourceMessageId IN (SELECT id FROM messages WHERE issueId = :issueId)",
    )
    suspend fun deleteMessageUsages(issueId: String): Int

    @Query("DELETE FROM cross_discussion_sessions WHERE issueId = :issueId")
    suspend fun deleteCrossDiscussions(issueId: String): Int

    @Query(
        "DELETE FROM execution_participant_states " +
            "WHERE runId IN (SELECT id FROM execution_runs WHERE issueId = :issueId)",
    )
    suspend fun deleteParticipantStates(issueId: String): Int

    @Query(
        "DELETE FROM execution_run_budgets " +
            "WHERE rootRunId IN (SELECT id FROM execution_runs WHERE issueId = :issueId)",
    )
    suspend fun deleteRunBudgets(issueId: String): Int

    @Query("DELETE FROM messages WHERE issueId = :issueId")
    suspend fun deleteMessages(issueId: String): Int

    @Query(
        "DELETE FROM execution_participant_snapshots " +
            "WHERE runId IN (SELECT id FROM execution_runs WHERE issueId = :issueId)",
    )
    suspend fun deleteParticipantSnapshots(issueId: String): Int

    @Query("UPDATE execution_runs SET retryOfRunId = NULL WHERE issueId = :issueId")
    suspend fun clearRunRetryReferences(issueId: String): Int

    @Query("DELETE FROM execution_runs WHERE issueId = :issueId")
    suspend fun deleteExecutionRuns(issueId: String): Int

    @Query("DELETE FROM stage_advancement_measures WHERE issueId = :issueId")
    suspend fun deleteStageAdvancementMeasures(issueId: String): Int

    @Query("DELETE FROM stage_advancement_skill_members WHERE issueId = :issueId")
    suspend fun deleteStageAdvancementSkillMembers(issueId: String): Int

    @Query("DELETE FROM stage_advancement_materials WHERE issueId = :issueId")
    suspend fun deleteStageAdvancementMaterials(issueId: String): Int

    @Query("DELETE FROM stage_advancement_artifacts WHERE issueId = :issueId")
    suspend fun deleteStageAdvancementArtifacts(issueId: String): Int

    @Query("DELETE FROM stage_advancements WHERE issueId = :issueId")
    suspend fun deleteStageAdvancements(issueId: String): Int

    @Query("DELETE FROM confirmed_artifacts WHERE issueId = :issueId")
    suspend fun deleteArtifacts(issueId: String): Int

    @Query("DELETE FROM stage_summary_draft_revisions WHERE issueId = :issueId")
    suspend fun deleteDraftRevisions(issueId: String): Int

    @Query("DELETE FROM stage_summary_drafts WHERE issueId = :issueId")
    suspend fun deleteDrafts(issueId: String): Int

    @Query("DELETE FROM material_usage_snapshots WHERE issueId = :issueId")
    suspend fun deleteMaterialUsages(issueId: String): Int

    @Query("DELETE FROM material_references WHERE issueId = :issueId")
    suspend fun deleteMaterialReferences(issueId: String): Int

    @Query("DELETE FROM personal_context_usage_snapshots WHERE issueId = :issueId")
    suspend fun deletePersonalContextUsages(issueId: String): Int

    @Query("DELETE FROM stages WHERE issueId = :issueId")
    suspend fun deleteStages(issueId: String): Int

    @Query("DELETE FROM issue_resume_events WHERE issueId = :issueId")
    suspend fun deleteResumeEvents(issueId: String): Int

    @Query("DELETE FROM issue_archive_events WHERE issueId = :issueId")
    suspend fun deleteArchiveEvents(issueId: String): Int

    @Query("DELETE FROM issue_lifecycle WHERE issueId = :issueId")
    suspend fun deleteIssueLifecycle(issueId: String): Int

    @Query(
        "DELETE FROM chat_sessions WHERE id = :sessionId " +
            "AND NOT EXISTS (SELECT 1 FROM issues " +
            "WHERE legacyChatSessionId = :sessionId AND id != :issueId) " +
            "AND NOT EXISTS (SELECT 1 FROM messages WHERE chatId = :sessionId)",
    )
    suspend fun deleteCompatibilitySessionIfExclusive(issueId: String, sessionId: Long): Int

    @Query("DELETE FROM issues WHERE id = :issueId")
    suspend fun deleteIssue(issueId: String): Int
}
