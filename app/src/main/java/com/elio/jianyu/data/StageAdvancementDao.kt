package com.elio.jianyu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface StageAdvancementDao {
    @Query("SELECT * FROM issues WHERE id = :issueId LIMIT 1")
    suspend fun getIssue(issueId: String): IssueEntity?

    @Query("SELECT * FROM stages WHERE id = :stageId LIMIT 1")
    suspend fun getStage(stageId: String): StageEntity?

    @Query(
        "SELECT * FROM stages WHERE issueId = :issueId " +
            "ORDER BY sequenceIndex DESC, id DESC LIMIT 1",
    )
    suspend fun getLatestStage(issueId: String): StageEntity?

    @Query("SELECT MAX(sequenceIndex) FROM stages WHERE issueId = :issueId")
    suspend fun getMaxStageSequence(issueId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStage(entity: StageEntity)

    @Query("DELETE FROM stages WHERE id = :stageId AND issueId = :issueId")
    suspend fun deleteStage(issueId: String, stageId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAdvancement(entity: StageAdvancementEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMeasures(entities: List<StageAdvancementMeasureEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSkillMembers(entities: List<StageAdvancementSkillMemberEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterials(entities: List<StageAdvancementMaterialEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtifacts(entities: List<StageAdvancementArtifactEntity>)

    @Query("SELECT * FROM stage_advancements WHERE operationId = :operationId LIMIT 1")
    suspend fun getAdvancementByOperationId(operationId: String): StageAdvancementEntity?

    @Query("SELECT * FROM stage_advancements WHERE stageId = :stageId LIMIT 1")
    suspend fun getAdvancement(stageId: String): StageAdvancementEntity?

    @Query(
        "SELECT advancement.* FROM stage_advancements advancement " +
            "INNER JOIN stages stage ON stage.id = advancement.stageId " +
            "WHERE advancement.issueId = :issueId " +
            "ORDER BY stage.sequenceIndex ASC, advancement.stageId ASC",
    )
    suspend fun getAdvancementsForIssue(issueId: String): List<StageAdvancementEntity>

    @Query(
        "SELECT * FROM stage_advancement_measures WHERE stageId = :stageId " +
            "ORDER BY position ASC, measure ASC",
    )
    suspend fun getMeasures(stageId: String): List<StageAdvancementMeasureEntity>

    @Query(
        "SELECT * FROM stage_advancement_skill_members WHERE stageId = :stageId " +
            "ORDER BY position ASC, officialSkillId ASC",
    )
    suspend fun getSkillMembers(stageId: String): List<StageAdvancementSkillMemberEntity>

    @Query(
        "SELECT * FROM stage_advancement_materials WHERE stageId = :stageId " +
            "ORDER BY position ASC, materialReferenceId ASC",
    )
    suspend fun getMaterials(stageId: String): List<StageAdvancementMaterialEntity>

    @Query(
        "SELECT * FROM stage_advancement_artifacts WHERE stageId = :stageId " +
            "ORDER BY position ASC, artifactId ASC",
    )
    suspend fun getArtifacts(stageId: String): List<StageAdvancementArtifactEntity>

    @Query("SELECT * FROM material_references WHERE id = :materialId LIMIT 1")
    suspend fun getMaterial(materialId: String): MaterialReferenceEntity?

    @Query("SELECT * FROM confirmed_artifacts WHERE id = :artifactId LIMIT 1")
    suspend fun getArtifact(artifactId: String): ConfirmedArtifactEntity?

    @Query("SELECT * FROM execution_runs WHERE id = :runId LIMIT 1")
    suspend fun getRun(runId: String): ExecutionRunEntity?

    @Query("SELECT * FROM execution_participant_snapshots WHERE id = :snapshotId LIMIT 1")
    suspend fun getParticipantSnapshot(snapshotId: String): ExecutionParticipantSnapshotEntity?

    @Query(
        "SELECT COUNT(*) FROM execution_runs WHERE issueId = :issueId AND stageId = :stageId " +
            "AND status IN ('not_started', 'running')",
    )
    suspend fun countBlockingRuns(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM cross_discussion_sessions " +
            "WHERE issueId = :issueId AND stageId = :stageId " +
            "AND status IN ('responding', 'synthesizing')",
    )
    suspend fun countBlockingDiscussions(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM messages WHERE issueId = :issueId AND stageId = :stageId " +
            "AND isPending = 1",
    )
    suspend fun countPendingMessages(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM execution_runs WHERE issueId = :issueId AND stageId = :stageId")
    suspend fun countRuns(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE issueId = :issueId AND stageId = :stageId")
    suspend fun countMessages(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM stage_summary_drafts WHERE issueId = :issueId AND stageId = :stageId")
    suspend fun countDrafts(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM stage_summary_draft_revisions " +
            "WHERE issueId = :issueId AND stageId = :stageId",
    )
    suspend fun countDraftRevisions(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM confirmed_artifacts WHERE issueId = :issueId AND stageId = :stageId")
    suspend fun countArtifacts(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM material_references WHERE issueId = :issueId AND stageId = :stageId")
    suspend fun countMaterialReferences(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM material_usage_snapshots " +
            "WHERE issueId = :issueId AND stageId = :stageId",
    )
    suspend fun countMaterialUsages(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM personal_context_usage_snapshots " +
            "WHERE issueId = :issueId AND stageId = :stageId",
    )
    suspend fun countPersonalContextUsages(issueId: String, stageId: String): Int

    @Query("SELECT COUNT(*) FROM audio_assets WHERE issueId = :issueId AND stageId = :stageId")
    suspend fun countAudioAssets(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM cross_discussion_sessions " +
            "WHERE issueId = :issueId AND stageId = :stageId",
    )
    suspend fun countDiscussions(issueId: String, stageId: String): Int

    @Query(
        "SELECT COUNT(*) FROM execution_message_usage_snapshots usage " +
            "INNER JOIN execution_runs run ON run.id = usage.runId " +
            "WHERE run.issueId = :issueId AND run.stageId = :stageId",
    )
    suspend fun countMessageUsages(issueId: String, stageId: String): Int

    @Query("DELETE FROM stage_advancement_measures WHERE stageId = :stageId")
    suspend fun deleteMeasures(stageId: String): Int

    @Query("DELETE FROM stage_advancement_skill_members WHERE stageId = :stageId")
    suspend fun deleteSkillMembers(stageId: String): Int

    @Query("DELETE FROM stage_advancement_materials WHERE stageId = :stageId")
    suspend fun deleteMaterials(stageId: String): Int

    @Query("DELETE FROM stage_advancement_artifacts WHERE stageId = :stageId")
    suspend fun deleteArtifacts(stageId: String): Int

    @Query("DELETE FROM stage_advancements WHERE stageId = :stageId AND issueId = :issueId")
    suspend fun deleteAdvancement(issueId: String, stageId: String): Int
}
