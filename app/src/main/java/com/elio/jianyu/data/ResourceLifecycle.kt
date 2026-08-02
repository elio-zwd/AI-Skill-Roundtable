package com.elio.jianyu.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.Update

enum class SnapshotContentState(val storageValue: String) {
    AVAILABLE("available"),
    PURGED("purged")
}

enum class AudioFileState(val storageValue: String) {
    PENDING("pending"),
    AVAILABLE("available"),
    MISSING("missing"),
    FAILED("failed")
}

enum class IssueLifecycleState(val storageValue: String) {
    ACTIVE("active"),
    ARCHIVED("archived"),
    TRASHED("trashed")
}

class ResourceLifecycleConverters {
    @TypeConverter
    fun snapshotContentStateToStorage(value: SnapshotContentState): String = value.storageValue

    @TypeConverter
    fun storageToSnapshotContentState(value: String): SnapshotContentState {
        return SnapshotContentState.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的快照内容状态")
    }

    @TypeConverter
    fun audioFileStateToStorage(value: AudioFileState): String = value.storageValue

    @TypeConverter
    fun storageToAudioFileState(value: String): AudioFileState {
        return AudioFileState.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的音频文件状态")
    }

    @TypeConverter
    fun issueLifecycleStateToStorage(value: IssueLifecycleState): String = value.storageValue

    @TypeConverter
    fun storageToIssueLifecycleState(value: String): IssueLifecycleState {
        return IssueLifecycleState.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的议题生命周期状态")
    }
}

@Entity(
    tableName = "material_references",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["id", "issueId"], unique = true),
        Index(value = ["issueId"]),
        Index(value = ["stageId", "issueId"]),
        Index(value = ["sourceLocator"]),
        Index(value = ["deletedAt"])
    ]
)
data class MaterialReferenceEntity(
    val id: String,
    val issueId: String,
    val stageId: String? = null,
    val title: String,
    val sourceType: String,
    val sourceLocator: String? = null,
    val content: String,
    val contentHash: String,
    val sourcePublishedAt: Long? = null,
    val sourceCapturedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val purgeRequestedAt: Long? = null
)

@Entity(
    tableName = "material_usage_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id", "issueId", "stageId"],
            childColumns = ["runId", "issueId", "stageId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MaterialReferenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialReferenceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["id", "issueId"], unique = true),
        Index(value = ["issueId"]),
        Index(value = ["stageId", "issueId"]),
        Index(value = ["runId", "issueId", "stageId"]),
        Index(value = ["materialReferenceId"]),
        Index(value = ["runId", "materialReferenceId"], unique = true)
    ]
)
data class MaterialUsageSnapshotEntity(
    val id: String,
    val issueId: String,
    val stageId: String,
    val runId: String? = null,
    val materialReferenceId: String? = null,
    val titleSnapshot: String,
    val sourceTypeSnapshot: String,
    val sourceLocatorSnapshot: String? = null,
    val contentSnapshot: String?,
    val contentHash: String,
    @ColumnInfo(defaultValue = "'available'")
    val contentState: SnapshotContentState = SnapshotContentState.AVAILABLE,
    val sourcePublishedAtSnapshot: Long? = null,
    val sourceCapturedAtSnapshot: Long? = null,
    val userConfirmedAt: Long,
    val createdAt: Long
)

@Entity(
    tableName = "personal_context_entries",
    indices = [
        Index(value = ["contentHash"]),
        Index(value = ["deletedAt"])
    ]
)
data class PersonalContextEntryEntity(
    val id: String,
    val title: String,
    val content: String,
    val contentHash: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val purgeRequestedAt: Long? = null
)

@Entity(
    tableName = "personal_context_usage_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id", "issueId", "stageId"],
            childColumns = ["runId", "issueId", "stageId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PersonalContextEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["personalContextEntryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["id", "issueId"], unique = true),
        Index(value = ["issueId"]),
        Index(value = ["stageId", "issueId"]),
        Index(value = ["runId", "issueId", "stageId"]),
        Index(value = ["personalContextEntryId"]),
        Index(value = ["runId", "personalContextEntryId"], unique = true)
    ]
)
data class PersonalContextUsageSnapshotEntity(
    val id: String,
    val issueId: String,
    val stageId: String,
    val runId: String? = null,
    val personalContextEntryId: String? = null,
    val titleSnapshot: String,
    val contentSnapshot: String?,
    val contentHash: String,
    @ColumnInfo(defaultValue = "'available'")
    val contentState: SnapshotContentState = SnapshotContentState.AVAILABLE,
    val userConfirmedAt: Long,
    val createdAt: Long
)

@Entity(
    tableName = "stage_summary_drafts",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["issueId", "stageId"], unique = true),
        Index(value = ["stageId", "issueId"])
    ]
)
data class StageSummaryDraftEntity(
    val id: String,
    val issueId: String,
    val stageId: String,
    val content: String,
    val revisionNumber: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "stage_summary_draft_revisions",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["id", "issueId"], unique = true),
        Index(value = ["stageId", "issueId"]),
        Index(value = ["issueId", "stageId", "revisionNumber"], unique = true),
        Index(value = ["draftIdSnapshot"])
    ]
)
data class StageSummaryDraftRevisionEntity(
    val id: String,
    val issueId: String,
    val stageId: String,
    val draftIdSnapshot: String,
    val revisionNumber: Int,
    val contentSnapshot: String,
    val createdAt: Long
)

@Entity(
    tableName = "confirmed_artifacts",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ConfirmedArtifactEntity::class,
            parentColumns = ["id"],
            childColumns = ["revisionOfArtifactId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["id", "issueId"], unique = true),
        Index(value = ["id", "issueId", "stageId"], unique = true),
        Index(value = ["stageId", "issueId"]),
        Index(value = ["revisionOfArtifactId"]),
        Index(value = ["confirmedAt"])
    ]
)
data class ConfirmedArtifactEntity(
    val id: String,
    val issueId: String,
    val stageId: String,
    val title: String,
    val content: String,
    val artifactType: String,
    val contentFormat: String,
    val confirmedAt: Long,
    val revisionOfArtifactId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "artifact_message_sources",
    primaryKeys = ["artifactId", "messageId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfirmedArtifactEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["artifactId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["messageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["artifactId", "issueId"]),
        Index(value = ["messageId", "issueId"])
    ]
)
data class ArtifactMessageSourceEntity(
    val artifactId: String,
    val issueId: String,
    val messageId: Long,
    val createdAt: Long
)

@Entity(
    tableName = "artifact_run_sources",
    primaryKeys = ["artifactId", "runId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfirmedArtifactEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["artifactId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["runId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["artifactId", "issueId"]),
        Index(value = ["runId", "issueId"])
    ]
)
data class ArtifactRunSourceEntity(
    val artifactId: String,
    val issueId: String,
    val runId: String,
    val createdAt: Long
)

@Entity(
    tableName = "artifact_draft_sources",
    primaryKeys = ["artifactId", "draftRevisionId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfirmedArtifactEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["artifactId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = StageSummaryDraftRevisionEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["draftRevisionId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["artifactId", "issueId"]),
        Index(value = ["draftRevisionId", "issueId"])
    ]
)
data class ArtifactDraftSourceEntity(
    val artifactId: String,
    val issueId: String,
    val draftRevisionId: String,
    val createdAt: Long
)

@Entity(
    tableName = "artifact_material_sources",
    primaryKeys = ["artifactId", "materialUsageSnapshotId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfirmedArtifactEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["artifactId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MaterialUsageSnapshotEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["materialUsageSnapshotId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["artifactId", "issueId"]),
        Index(value = ["materialUsageSnapshotId", "issueId"])
    ]
)
data class ArtifactMaterialSourceEntity(
    val artifactId: String,
    val issueId: String,
    val materialUsageSnapshotId: String,
    val createdAt: Long
)

@Entity(
    tableName = "audio_assets",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id", "issueId", "stageId"],
            childColumns = ["sourceMessageId", "issueId", "stageId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ConfirmedArtifactEntity::class,
            parentColumns = ["id", "issueId", "stageId"],
            childColumns = ["sourceArtifactId", "issueId", "stageId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["sourceMessageId", "issueId", "stageId"]),
        Index(value = ["sourceArtifactId", "issueId", "stageId"]),
        Index(value = ["storagePath"], unique = true),
        Index(value = ["generationKey"], unique = true),
        Index(value = ["deletedAt"])
    ]
)
data class AudioAssetEntity(
    val id: String,
    val issueId: String,
    val stageId: String,
    val sourceMessageId: Long? = null,
    val sourceArtifactId: String? = null,
    val storagePath: String,
    val mimeType: String,
    val format: String,
    val sizeBytes: Long,
    @ColumnInfo(defaultValue = "'pending'")
    val fileState: AudioFileState = AudioFileState.PENDING,
    val generationKey: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val purgeRequestedAt: Long? = null
)

@Entity(
    tableName = "official_skill_combinations",
    indices = [
        Index(value = ["name"]),
        Index(value = ["deletedAt"])
    ]
)
data class OfficialSkillCombinationEntity(
    val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "official_skill_combination_members",
    primaryKeys = ["combinationId", "officialSkillId"],
    foreignKeys = [
        ForeignKey(
            entity = OfficialSkillCombinationEntity::class,
            parentColumns = ["id"],
            childColumns = ["combinationId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["combinationId"]),
        Index(value = ["combinationId", "position"], unique = true)
    ]
)
data class OfficialSkillCombinationMemberEntity(
    val combinationId: String,
    val officialSkillId: String,
    val position: Int,
    val defaultResponsibility: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "issue_lifecycle",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["state"]),
        Index(value = ["purgeRequestedAt"])
    ]
)
data class IssueLifecycleEntity(
    val issueId: String,
    @ColumnInfo(defaultValue = "'active'")
    val state: IssueLifecycleState = IssueLifecycleState.ACTIVE,
    val previousState: IssueLifecycleState? = null,
    val stateChangedAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val purgeRequestedAt: Long? = null
)

data class ArtifactSources(
    val messages: List<ArtifactMessageSourceEntity> = emptyList(),
    val runs: List<ArtifactRunSourceEntity> = emptyList(),
    val draftRevisions: List<ArtifactDraftSourceEntity> = emptyList(),
    val materials: List<ArtifactMaterialSourceEntity> = emptyList()
)

fun validateAudioAssetSource(
    sourceMessageId: Long?,
    sourceArtifactId: String?
) {
    require((sourceMessageId == null) != (sourceArtifactId == null)) {
        "音频资产必须且只能关联一个来源"
    }
}

fun validateOfficialCombinationMembers(members: List<OfficialSkillCombinationMemberEntity>) {
    require(members.all { it.officialSkillId.isNotBlank() }) { "官方 Skill ID 不能为空" }
    require(members.all { it.position >= 0 }) { "成员顺序不能为负数" }
    require(members.map { it.officialSkillId }.distinct().size == members.size) {
        "同一组合不能重复保存官方 Skill"
    }
    require(members.map { it.position }.distinct().size == members.size) {
        "同一组合的成员顺序必须唯一"
    }
}

fun validateArtifactRevision(
    artifactId: String,
    revisionOfArtifactId: String?
) {
    require(revisionOfArtifactId == null || revisionOfArtifactId != artifactId) {
        "成果不能修订自身"
    }
}

private fun validateConfirmedUsage(confirmedAt: Long) {
    require(confirmedAt > 0L) { "只有用户明确确认的内容才能记录为已使用" }
}

private fun validateArtifactSources(
    artifact: ConfirmedArtifactEntity,
    sources: ArtifactSources
) {
    val allMatchArtifact = sources.messages.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    } && sources.runs.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    } && sources.draftRevisions.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    } && sources.materials.all {
        it.artifactId == artifact.id && it.issueId == artifact.issueId
    }
    require(allMatchArtifact) { "成果来源必须属于当前成果及同一议题" }
}

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
