package com.elio.jianyu.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
        Index(value = ["lifecycleState"]),
        Index(value = ["deletedAt"])
    ]
)
data class MaterialReferenceEntity(
    @PrimaryKey val id: String,
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
    @ColumnInfo(defaultValue = "'active'")
    val lifecycleState: ContextSourceLifecycle = ContextSourceLifecycle.ACTIVE,
    @ColumnInfo(defaultValue = "0")
    val sensitive: Boolean = false,
    val disabledAt: Long? = null,
    val archivedAt: Long? = null,
    val deletedAt: Long? = null,
    val purgeRequestedAt: Long? = null,
    val purgedAt: Long? = null
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
    @PrimaryKey val id: String,
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
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0")
    val networkAllowed: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val sensitive: Boolean = true
)

@Entity(
    tableName = "personal_context_entries",
    indices = [
        Index(value = ["contentHash"]),
        Index(value = ["lifecycleState"]),
        Index(value = ["deletedAt"])
    ]
)
data class PersonalContextEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val contentHash: String,
    @ColumnInfo(defaultValue = "1")
    val isEnabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'active'")
    val lifecycleState: ContextSourceLifecycle = ContextSourceLifecycle.ACTIVE,
    @ColumnInfo(defaultValue = "0")
    val sensitive: Boolean = false,
    val disabledAt: Long? = null,
    val archivedAt: Long? = null,
    val deletedAt: Long? = null,
    val purgeRequestedAt: Long? = null,
    val purgedAt: Long? = null
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
    @PrimaryKey val id: String,
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
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0")
    val networkAllowed: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val sensitive: Boolean = true
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
    @PrimaryKey val id: String,
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
    @PrimaryKey val id: String,
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
    @PrimaryKey val id: String,
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
    @PrimaryKey val id: String,
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
    @PrimaryKey val id: String,
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
    @PrimaryKey val issueId: String,
    @ColumnInfo(defaultValue = "'active'")
    val state: IssueLifecycleState = IssueLifecycleState.ACTIVE,
    val previousState: IssueLifecycleState? = null,
    val stateChangedAt: Long,
    val updatedAt: Long,
    val archivedAt: Long? = null,
    val trashedAt: Long? = null,
    val purgeRequestedAt: Long? = null
)
