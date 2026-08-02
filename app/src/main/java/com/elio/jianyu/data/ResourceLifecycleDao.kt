package com.elio.jianyu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
abstract class ResourceLifecycleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertMaterialReference(entity: MaterialReferenceEntity)

    @Update
    abstract suspend fun updateMaterialReference(entity: MaterialReferenceEntity)

    @Query("DELETE FROM material_references WHERE id = :id")
    abstract suspend fun deleteMaterialReference(id: String)

    @Query("SELECT * FROM material_references WHERE id = :id")
    abstract suspend fun getMaterialReference(id: String): MaterialReferenceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertMaterialUsageSnapshotInternal(
        entity: MaterialUsageSnapshotEntity
    )

    @Transaction
    open suspend fun recordMaterialUsage(entity: MaterialUsageSnapshotEntity) {
        validateConfirmedUsage(entity.userConfirmedAt)
        insertMaterialUsageSnapshotInternal(entity)
    }

    @Query("SELECT * FROM material_usage_snapshots WHERE id = :id")
    abstract suspend fun getMaterialUsageSnapshot(id: String): MaterialUsageSnapshotEntity?

    @Query(
        "SELECT * FROM material_usage_snapshots " +
            "WHERE issueId = :issueId AND stageId = :stageId ORDER BY createdAt, id"
    )
    abstract suspend fun getMaterialUsageSnapshotsForStage(
        issueId: String,
        stageId: String
    ): List<MaterialUsageSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertPersonalContextEntry(entity: PersonalContextEntryEntity)

    @Update
    abstract suspend fun updatePersonalContextEntry(entity: PersonalContextEntryEntity)

    @Query("DELETE FROM personal_context_entries WHERE id = :id")
    abstract suspend fun deletePersonalContextEntry(id: String)

    @Query("SELECT * FROM personal_context_entries WHERE id = :id")
    abstract suspend fun getPersonalContextEntry(id: String): PersonalContextEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPersonalContextUsageSnapshotInternal(
        entity: PersonalContextUsageSnapshotEntity
    )

    @Transaction
    open suspend fun recordPersonalContextUsage(entity: PersonalContextUsageSnapshotEntity) {
        validateConfirmedUsage(entity.userConfirmedAt)
        insertPersonalContextUsageSnapshotInternal(entity)
    }

    @Query("SELECT * FROM personal_context_usage_snapshots WHERE id = :id")
    abstract suspend fun getPersonalContextUsageSnapshot(
        id: String
    ): PersonalContextUsageSnapshotEntity?

    @Query(
        "SELECT * FROM personal_context_usage_snapshots " +
            "WHERE issueId = :issueId AND stageId = :stageId ORDER BY createdAt, id"
    )
    abstract suspend fun getPersonalContextUsageSnapshotsForStage(
        issueId: String,
        stageId: String
    ): List<PersonalContextUsageSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertStageSummaryDraftInternal(entity: StageSummaryDraftEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertStageSummaryDraftRevisionInternal(
        entity: StageSummaryDraftRevisionEntity
    )

    @Transaction
    open suspend fun saveDraftWithRevision(
        draft: StageSummaryDraftEntity,
        revision: StageSummaryDraftRevisionEntity
    ) {
        require(
            draft.id == revision.draftIdSnapshot &&
                draft.issueId == revision.issueId &&
                draft.stageId == revision.stageId &&
                draft.revisionNumber == revision.revisionNumber
        ) { "草稿当前态与修订快照必须一致" }
        insertStageSummaryDraftRevisionInternal(revision)
        upsertStageSummaryDraftInternal(draft)
    }

    @Query("SELECT * FROM stage_summary_drafts WHERE issueId = :issueId AND stageId = :stageId")
    abstract suspend fun getStageSummaryDraft(
        issueId: String,
        stageId: String
    ): StageSummaryDraftEntity?

    @Query("DELETE FROM stage_summary_drafts WHERE issueId = :issueId AND stageId = :stageId")
    abstract suspend fun abandonStageSummaryDraft(issueId: String, stageId: String)

    @Query(
        "SELECT * FROM stage_summary_draft_revisions " +
            "WHERE issueId = :issueId AND stageId = :stageId ORDER BY revisionNumber"
    )
    abstract suspend fun getStageSummaryDraftRevisions(
        issueId: String,
        stageId: String
    ): List<StageSummaryDraftRevisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertConfirmedArtifactInternal(entity: ConfirmedArtifactEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertArtifactMessageSources(
        entities: List<ArtifactMessageSourceEntity>
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertArtifactRunSources(entities: List<ArtifactRunSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertArtifactDraftSources(
        entities: List<ArtifactDraftSourceEntity>
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertArtifactMaterialSources(
        entities: List<ArtifactMaterialSourceEntity>
    )

    @Transaction
    open suspend fun createArtifactWithSources(
        artifact: ConfirmedArtifactEntity,
        sources: ArtifactSources
    ) {
        validateArtifactRevision(artifact.id, artifact.revisionOfArtifactId)
        validateArtifactSources(artifact, sources)
        insertConfirmedArtifactInternal(artifact)
        if (sources.messages.isNotEmpty()) insertArtifactMessageSources(sources.messages)
        if (sources.runs.isNotEmpty()) insertArtifactRunSources(sources.runs)
        if (sources.draftRevisions.isNotEmpty()) {
            insertArtifactDraftSources(sources.draftRevisions)
        }
        if (sources.materials.isNotEmpty()) insertArtifactMaterialSources(sources.materials)
    }

    @Query("SELECT * FROM confirmed_artifacts WHERE id = :id")
    abstract suspend fun getConfirmedArtifact(id: String): ConfirmedArtifactEntity?

    @Query(
        "SELECT * FROM confirmed_artifacts " +
            "WHERE issueId = :issueId AND stageId = :stageId ORDER BY confirmedAt, id"
    )
    abstract suspend fun getConfirmedArtifactsForStage(
        issueId: String,
        stageId: String
    ): List<ConfirmedArtifactEntity>

    @Query("DELETE FROM confirmed_artifacts WHERE id = :id")
    abstract suspend fun deleteConfirmedArtifact(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAudioAssetInternal(entity: AudioAssetEntity)

    @Transaction
    open suspend fun createAudioAsset(entity: AudioAssetEntity) {
        validateAudioAssetSource(entity.sourceMessageId, entity.sourceArtifactId)
        require(entity.sizeBytes >= 0L) { "音频资产大小不能为负数" }
        insertAudioAssetInternal(entity)
    }

    @Update
    abstract suspend fun updateAudioAsset(entity: AudioAssetEntity)

    @Query("SELECT * FROM audio_assets WHERE id = :id")
    abstract suspend fun getAudioAsset(id: String): AudioAssetEntity?

    @Query(
        "SELECT * FROM audio_assets WHERE issueId = :issueId AND stageId = :stageId " +
            "ORDER BY createdAt, id"
    )
    abstract suspend fun getAudioAssetsForStage(
        issueId: String,
        stageId: String
    ): List<AudioAssetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOfficialSkillCombinationInternal(
        entity: OfficialSkillCombinationEntity
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOfficialSkillCombinationMembersInternal(
        entities: List<OfficialSkillCombinationMemberEntity>
    )

    @Transaction
    open suspend fun createOfficialCombination(
        combination: OfficialSkillCombinationEntity,
        members: List<OfficialSkillCombinationMemberEntity>
    ) {
        validateOfficialCombinationMembers(members)
        require(members.all { it.combinationId == combination.id }) {
            "组合成员必须属于当前组合"
        }
        insertOfficialSkillCombinationInternal(combination)
        if (members.isNotEmpty()) insertOfficialSkillCombinationMembersInternal(members)
    }

    @Update
    abstract suspend fun updateOfficialSkillCombination(entity: OfficialSkillCombinationEntity)

    @Query("SELECT * FROM official_skill_combinations WHERE id = :id")
    abstract suspend fun getOfficialSkillCombination(id: String): OfficialSkillCombinationEntity?

    @Query("DELETE FROM official_skill_combinations WHERE id = :id")
    abstract suspend fun deleteOfficialSkillCombination(id: String)

    @Query(
        "SELECT * FROM official_skill_combination_members " +
            "WHERE combinationId = :combinationId ORDER BY position"
    )
    abstract suspend fun getOfficialSkillCombinationMembers(
        combinationId: String
    ): List<OfficialSkillCombinationMemberEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertIssueLifecycle(entity: IssueLifecycleEntity)

    @Update
    abstract suspend fun updateIssueLifecycle(entity: IssueLifecycleEntity)

    @Query("SELECT * FROM issue_lifecycle WHERE issueId = :issueId")
    abstract suspend fun getIssueLifecycle(issueId: String): IssueLifecycleEntity?
}
