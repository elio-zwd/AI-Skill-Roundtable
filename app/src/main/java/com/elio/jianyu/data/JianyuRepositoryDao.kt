package com.elio.jianyu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
internal interface JianyuRepositoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIssue(entity: IssueEntity)

    @Update
    suspend fun updateIssue(entity: IssueEntity): Int

    @Query("SELECT * FROM issues WHERE id = :issueId LIMIT 1")
    suspend fun getIssue(issueId: String): IssueEntity?

    @Query("SELECT * FROM issues ORDER BY updatedAt DESC, id ASC")
    suspend fun getAllIssues(): List<IssueEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStage(entity: StageEntity)

    @Query("SELECT * FROM stages WHERE id = :stageId LIMIT 1")
    suspend fun getStage(stageId: String): StageEntity?

    @Query("SELECT * FROM stages WHERE issueId = :issueId ORDER BY sequenceIndex ASC")
    suspend fun getStagesForIssue(issueId: String): List<StageEntity>

    @Query("SELECT MAX(sequenceIndex) FROM stages WHERE issueId = :issueId")
    suspend fun getMaxStageSequence(issueId: String): Int?

    @Query("DELETE FROM stages WHERE id = :stageId AND issueId = :issueId")
    suspend fun deleteStage(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM execution_runs WHERE stageId = :stageId AND issueId = :issueId")
    suspend fun countRunsForStage(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE stageId = :stageId AND issueId = :issueId")
    suspend fun countMessagesForStage(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM stage_summary_drafts WHERE stageId = :stageId AND issueId = :issueId")
    suspend fun countDraftsForStage(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM stage_summary_draft_revisions " +
            "WHERE stageId = :stageId AND issueId = :issueId"
    )
    suspend fun countDraftRevisionsForStage(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM confirmed_artifacts WHERE stageId = :stageId AND issueId = :issueId")
    suspend fun countArtifactsForStage(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM material_references WHERE stageId = :stageId AND issueId = :issueId")
    suspend fun countMaterialReferencesForStage(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM material_usage_snapshots " +
            "WHERE stageId = :stageId AND issueId = :issueId"
    )
    suspend fun countMaterialUsagesForStage(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM personal_context_usage_snapshots " +
            "WHERE stageId = :stageId AND issueId = :issueId"
    )
    suspend fun countPersonalContextUsagesForStage(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM audio_assets WHERE stageId = :stageId AND issueId = :issueId")
    suspend fun countAudioAssetsForStage(issueId: String, stageId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExecutionRun(entity: ExecutionRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParticipantSnapshots(entities: List<ExecutionParticipantSnapshotEntity>)

    @Query("SELECT * FROM execution_runs WHERE id = :runId LIMIT 1")
    suspend fun getExecutionRun(runId: String): ExecutionRunEntity?

    @Query("SELECT * FROM execution_runs WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun getExecutionRunByIdempotencyKey(idempotencyKey: String): ExecutionRunEntity?

    @Query("SELECT * FROM execution_runs WHERE issueId = :issueId ORDER BY createdAt ASC, id ASC")
    suspend fun getExecutionRunsForIssue(issueId: String): List<ExecutionRunEntity>

    @Query(
        "SELECT * FROM execution_participant_snapshots " +
            "WHERE runId = :runId ORDER BY position ASC"
    )
    suspend fun getParticipantSnapshots(runId: String): List<ExecutionParticipantSnapshotEntity>

    @Query(
        "SELECT * FROM execution_participant_snapshots " +
            "WHERE runId IN (SELECT id FROM execution_runs WHERE issueId = :issueId) " +
            "ORDER BY runId ASC, position ASC"
    )
    suspend fun getParticipantSnapshotsForIssue(
        issueId: String
    ): List<ExecutionParticipantSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParticipantStates(entities: List<ExecutionParticipantStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRunBudget(entity: ExecutionRunBudgetEntity)

    @Query(
        "SELECT * FROM execution_participant_states " +
            "WHERE runId = :runId ORDER BY participantSnapshotId ASC"
    )
    suspend fun getParticipantStates(runId: String): List<ExecutionParticipantStateEntity>

    @Query(
        "SELECT state.* FROM execution_participant_states state " +
            "INNER JOIN execution_runs run ON run.id = state.runId " +
            "WHERE run.issueId = :issueId ORDER BY state.runId ASC, state.participantSnapshotId ASC"
    )
    suspend fun getParticipantStatesForIssue(issueId: String): List<ExecutionParticipantStateEntity>

    @Query(
        "SELECT * FROM execution_participant_states " +
            "WHERE participantSnapshotId = :participantSnapshotId LIMIT 1"
    )
    suspend fun getParticipantState(
        participantSnapshotId: String
    ): ExecutionParticipantStateEntity?

    @Query("SELECT * FROM execution_run_budgets WHERE rootRunId = :rootRunId LIMIT 1")
    suspend fun getRunBudget(rootRunId: String): ExecutionRunBudgetEntity?

    @Query(
        "UPDATE execution_participant_states SET status = :newStatus, " +
            "attemptCount = attemptCount + :attemptIncrement, " +
            "outputMessageId = :outputMessageId, startedAt = :startedAt, " +
            "finishedAt = :finishedAt, lastErrorCode = :lastErrorCode, " +
            "lastErrorMessage = :lastErrorMessage, " +
            "hasIncompleteOutput = :hasIncompleteOutput, updatedAt = :updatedAt " +
            "WHERE participantSnapshotId = :participantSnapshotId " +
            "AND runId = :runId AND status IN (:expectedStatuses)"
    )
    suspend fun compareAndSetParticipantState(
        participantSnapshotId: String,
        runId: String,
        expectedStatuses: List<String>,
        newStatus: String,
        attemptIncrement: Int,
        outputMessageId: Long?,
        startedAt: Long?,
        finishedAt: Long?,
        lastErrorCode: String?,
        lastErrorMessage: String?,
        hasIncompleteOutput: Boolean,
        updatedAt: Long
    ): Int

    @Query(
        "UPDATE execution_run_budgets SET " +
            "usedApiCalls = usedApiCalls + :count, " +
            "reservedRequiredCalls = MIN(reservedRequiredCalls, :reserveAfter), " +
            "updatedAt = :updatedAt " +
            "WHERE rootRunId = :rootRunId AND closed = 0 " +
            "AND usedApiCalls + :count + :reserveAfter <= maxApiCalls"
    )
    suspend fun consumeRequiredBudget(
        rootRunId: String,
        count: Int,
        reserveAfter: Int,
        updatedAt: Long
    ): Int

    @Query(
        "UPDATE execution_run_budgets SET " +
            "usedApiCalls = usedApiCalls + :count, updatedAt = :updatedAt " +
            "WHERE rootRunId = :rootRunId AND closed = 0 " +
            "AND usedApiCalls + :count + MAX(reservedRequiredCalls, :reserveForRequired) " +
            "<= maxApiCalls"
    )
    suspend fun consumeOptionalBudget(
        rootRunId: String,
        count: Int,
        reserveForRequired: Int,
        updatedAt: Long
    ): Int

    @Query(
        "UPDATE execution_run_budgets SET reservedRequiredCalls = :count, " +
            "updatedAt = :updatedAt WHERE rootRunId = :rootRunId AND closed = 0"
    )
    suspend fun setRequiredBudgetReserve(
        rootRunId: String,
        count: Int,
        updatedAt: Long
    ): Int

    @Query(
        "UPDATE execution_run_budgets SET closed = 1, updatedAt = :updatedAt " +
            "WHERE rootRunId = :rootRunId AND closed = 0"
    )
    suspend fun closeRunBudget(rootRunId: String, updatedAt: Long): Int

    @Query(
        "UPDATE execution_runs SET status = :newStatus, updatedAt = :updatedAt, " +
            "startedAt = :startedAt, finishedAt = :finishedAt, stoppedAt = :stoppedAt, " +
            "failureCode = :failureCode, failureMessage = :failureMessage " +
            "WHERE id = :runId AND status IN (:expectedStatuses)"
    )
    suspend fun compareAndSetRunStatus(
        runId: String,
        expectedStatuses: List<String>,
        newStatus: String,
        updatedAt: Long,
        startedAt: Long?,
        finishedAt: Long?,
        stoppedAt: Long?,
        failureCode: String?,
        failureMessage: String?
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCompatibilitySession(entity: ChatSession): Long

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getCompatibilitySession(sessionId: Long): ChatSession?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDomainMessage(entity: Message): Long

    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: Long): Message?

    @Query(
        "UPDATE messages SET text = :text, isPending = :keepPending " +
            "WHERE id = :messageId AND issueId = :issueId AND stageId = :stageId " +
            "AND ((:executionRunId IS NULL AND executionRunId IS NULL) " +
            "OR executionRunId = :executionRunId) " +
            "AND ((:participantSnapshotId IS NULL AND participantSnapshotId IS NULL) " +
            "OR participantSnapshotId = :participantSnapshotId) " +
            "AND isPending = 1"
    )
    suspend fun compareAndSetPendingDomainMessage(
        messageId: Long,
        issueId: String,
        stageId: String,
        executionRunId: String?,
        participantSnapshotId: String?,
        text: String,
        keepPending: Boolean
    ): Int

    @Query("SELECT * FROM messages WHERE issueId = :issueId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForIssue(issueId: String): List<Message>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDraft(entity: StageSummaryDraftEntity)

    @Update
    suspend fun updateDraft(entity: StageSummaryDraftEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDraftRevision(entity: StageSummaryDraftRevisionEntity)

    @Query(
        "SELECT * FROM stage_summary_drafts " +
            "WHERE issueId = :issueId AND stageId = :stageId LIMIT 1"
    )
    suspend fun getDraft(issueId: String, stageId: String): StageSummaryDraftEntity?

    @Query("SELECT * FROM stage_summary_draft_revisions WHERE id = :revisionId LIMIT 1")
    suspend fun getDraftRevision(revisionId: String): StageSummaryDraftRevisionEntity?

    @Query(
        "SELECT * FROM stage_summary_drafts WHERE issueId = :issueId " +
            "ORDER BY updatedAt ASC, id ASC"
    )
    suspend fun getDraftsForIssue(issueId: String): List<StageSummaryDraftEntity>

    @Query(
        "SELECT * FROM stage_summary_draft_revisions WHERE issueId = :issueId " +
            "ORDER BY stageId ASC, revisionNumber ASC"
    )
    suspend fun getDraftRevisionsForIssue(issueId: String): List<StageSummaryDraftRevisionEntity>

    @Query("DELETE FROM stage_summary_drafts WHERE issueId = :issueId AND stageId = :stageId")
    suspend fun deleteDraft(issueId: String, stageId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtifact(entity: ConfirmedArtifactEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtifactMessageSources(entities: List<ArtifactMessageSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtifactRunSources(entities: List<ArtifactRunSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtifactDraftSources(entities: List<ArtifactDraftSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtifactMaterialSources(entities: List<ArtifactMaterialSourceEntity>)

    @Query("SELECT * FROM confirmed_artifacts WHERE id = :artifactId LIMIT 1")
    suspend fun getArtifact(artifactId: String): ConfirmedArtifactEntity?

    @Query(
        "SELECT * FROM confirmed_artifacts WHERE issueId = :issueId " +
            "ORDER BY confirmedAt ASC, id ASC"
    )
    suspend fun getArtifactsForIssue(issueId: String): List<ConfirmedArtifactEntity>

    @Query("SELECT * FROM artifact_message_sources WHERE artifactId = :artifactId ORDER BY messageId")
    suspend fun getArtifactMessageSources(artifactId: String): List<ArtifactMessageSourceEntity>

    @Query("SELECT * FROM artifact_run_sources WHERE artifactId = :artifactId ORDER BY runId")
    suspend fun getArtifactRunSources(artifactId: String): List<ArtifactRunSourceEntity>

    @Query(
        "SELECT * FROM artifact_draft_sources " +
            "WHERE artifactId = :artifactId ORDER BY draftRevisionId"
    )
    suspend fun getArtifactDraftSources(artifactId: String): List<ArtifactDraftSourceEntity>

    @Query(
        "SELECT * FROM artifact_material_sources " +
            "WHERE artifactId = :artifactId ORDER BY materialUsageSnapshotId"
    )
    suspend fun getArtifactMaterialSources(artifactId: String): List<ArtifactMaterialSourceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterialReference(entity: MaterialReferenceEntity)

    @Update
    suspend fun updateMaterialReference(entity: MaterialReferenceEntity): Int

    @Query("SELECT * FROM material_references WHERE id = :id LIMIT 1")
    suspend fun getMaterialReference(id: String): MaterialReferenceEntity?

    @Query("SELECT * FROM material_references ORDER BY updatedAt DESC, id ASC")
    suspend fun getAllMaterialReferences(): List<MaterialReferenceEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterialUsage(entity: MaterialUsageSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterialUsages(entities: List<MaterialUsageSnapshotEntity>)

    @Query("SELECT * FROM material_usage_snapshots WHERE id = :id LIMIT 1")
    suspend fun getMaterialUsage(id: String): MaterialUsageSnapshotEntity?

    @Query(
        "SELECT * FROM material_usage_snapshots WHERE runId = :runId " +
            "ORDER BY userConfirmedAt ASC, materialReferenceId ASC, id ASC"
    )
    suspend fun getMaterialUsagesForRun(runId: String): List<MaterialUsageSnapshotEntity>

    @Query(
        "SELECT * FROM material_usage_snapshots WHERE runId = :runId " +
            "AND materialReferenceId = :sourceId LIMIT 1"
    )
    suspend fun getMaterialUsageForRunAndSource(
        runId: String,
        sourceId: String,
    ): MaterialUsageSnapshotEntity?

    @Query(
        "SELECT * FROM material_usage_snapshots WHERE materialReferenceId = :sourceId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getMaterialUsagesForSource(sourceId: String): List<MaterialUsageSnapshotEntity>

    @Query(
        "SELECT * FROM material_usage_snapshots WHERE issueId = :issueId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getMaterialUsagesForIssue(issueId: String): List<MaterialUsageSnapshotEntity>

    @Query(
        "UPDATE material_usage_snapshots SET titleSnapshot = '', sourceTypeSnapshot = '', " +
            "sourceLocatorSnapshot = NULL, contentSnapshot = NULL, contentHash = '', " +
            "contentState = 'purged', networkAllowed = 0, sensitive = 0 " +
            "WHERE materialReferenceId = :sourceId"
    )
    suspend fun purgeMaterialUsageSnapshots(sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPersonalContextEntry(entity: PersonalContextEntryEntity)

    @Update
    suspend fun updatePersonalContextEntry(entity: PersonalContextEntryEntity): Int

    @Query("SELECT * FROM personal_context_entries WHERE id = :id LIMIT 1")
    suspend fun getPersonalContextEntry(id: String): PersonalContextEntryEntity?

    @Query("SELECT * FROM personal_context_entries ORDER BY updatedAt DESC, id ASC")
    suspend fun getAllPersonalContextEntries(): List<PersonalContextEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPersonalContextUsage(entity: PersonalContextUsageSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPersonalContextUsages(entities: List<PersonalContextUsageSnapshotEntity>)

    @Query("SELECT * FROM personal_context_usage_snapshots WHERE id = :id LIMIT 1")
    suspend fun getPersonalContextUsage(id: String): PersonalContextUsageSnapshotEntity?

    @Query(
        "SELECT * FROM personal_context_usage_snapshots WHERE runId = :runId " +
            "ORDER BY userConfirmedAt ASC, personalContextEntryId ASC, id ASC"
    )
    suspend fun getPersonalContextUsagesForRun(runId: String): List<PersonalContextUsageSnapshotEntity>

    @Query(
        "SELECT * FROM personal_context_usage_snapshots WHERE runId = :runId " +
            "AND personalContextEntryId = :sourceId LIMIT 1"
    )
    suspend fun getPersonalContextUsageForRunAndSource(
        runId: String,
        sourceId: String,
    ): PersonalContextUsageSnapshotEntity?

    @Query(
        "SELECT * FROM personal_context_usage_snapshots WHERE personalContextEntryId = :sourceId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getPersonalContextUsagesForSource(
        sourceId: String,
    ): List<PersonalContextUsageSnapshotEntity>

    @Query(
        "SELECT * FROM personal_context_usage_snapshots WHERE issueId = :issueId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getPersonalContextUsagesForIssue(
        issueId: String
    ): List<PersonalContextUsageSnapshotEntity>

    @Query(
        "UPDATE personal_context_usage_snapshots SET titleSnapshot = '', contentSnapshot = NULL, " +
            "contentHash = '', contentState = 'purged', networkAllowed = 0, sensitive = 0 " +
            "WHERE personalContextEntryId = :sourceId"
    )
    suspend fun purgePersonalContextUsageSnapshots(sourceId: String): Int

    @Query(
        "SELECT * FROM audio_assets WHERE issueId = :issueId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getAudioAssetsForIssue(issueId: String): List<AudioAssetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOfficialSkillCombination(entity: OfficialSkillCombinationEntity)

    @Update
    suspend fun updateOfficialSkillCombination(entity: OfficialSkillCombinationEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOfficialSkillCombinationMembers(
        entities: List<OfficialSkillCombinationMemberEntity>
    )

    @Query("DELETE FROM official_skill_combination_members WHERE combinationId = :combinationId")
    suspend fun deleteOfficialSkillCombinationMembers(combinationId: String): Int

    @Query("SELECT * FROM official_skill_combinations WHERE id = :combinationId LIMIT 1")
    suspend fun getOfficialSkillCombination(
        combinationId: String
    ): OfficialSkillCombinationEntity?

    @Query(
        "SELECT * FROM official_skill_combinations WHERE deletedAt IS NULL " +
            "ORDER BY updatedAt DESC, id ASC"
    )
    suspend fun getActiveOfficialSkillCombinations(): List<OfficialSkillCombinationEntity>

    @Query(
        "SELECT * FROM official_skill_combination_members " +
            "WHERE combinationId = :combinationId ORDER BY position ASC"
    )
    suspend fun getOfficialSkillCombinationMembers(
        combinationId: String
    ): List<OfficialSkillCombinationMemberEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIssueLifecycle(entity: IssueLifecycleEntity)

    @Update
    suspend fun updateIssueLifecycle(entity: IssueLifecycleEntity): Int

    @Query("SELECT * FROM issue_lifecycle WHERE issueId = :issueId LIMIT 1")
    suspend fun getIssueLifecycle(issueId: String): IssueLifecycleEntity?

    @Query("SELECT * FROM issue_lifecycle ORDER BY updatedAt DESC, issueId ASC")
    suspend fun getAllIssueLifecycles(): List<IssueLifecycleEntity>
}
