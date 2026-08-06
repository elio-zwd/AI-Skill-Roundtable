package com.elio.jianyu.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class IssueRelationType(val storageValue: String) {
    CONTINUATION("continuation"),
}

enum class IssuePurgeState(val storageValue: String) {
    REQUESTED("requested"),
    WAITING_FOR_TASKS("waiting_for_tasks"),
    CANCELING_TASKS("canceling_tasks"),
    DELETING_FILES("deleting_files"),
    READY_FOR_DATABASE_PURGE("ready_for_database_purge"),
    DATABASE_PURGING("database_purging"),
    FAILED_RETRYABLE("failed_retryable"),
    COMPLETED("completed"),
}

enum class IssuePurgeFailurePhase(val storageValue: String) {
    IMPACT("impact"),
    TASK_CANCEL("task_cancel"),
    AUDIO_DELETE_REQUEST("audio_delete_request"),
    FILE_DELETE("file_delete"),
    DATABASE_PURGE("database_purge"),
    STORAGE("storage"),
}

class IssueLifecycleV12Converters {
    @TypeConverter
    fun issueRelationTypeToStorage(value: IssueRelationType): String = value.storageValue

    @TypeConverter
    fun storageToIssueRelationType(value: String): IssueRelationType =
        IssueRelationType.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的议题关系类型")

    @TypeConverter
    fun issuePurgeStateToStorage(value: IssuePurgeState): String = value.storageValue

    @TypeConverter
    fun storageToIssuePurgeState(value: String): IssuePurgeState =
        IssuePurgeState.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("未知的彻底清除状态")

    @TypeConverter
    fun issuePurgeFailurePhaseToStorage(value: IssuePurgeFailurePhase?): String? =
        value?.storageValue

    @TypeConverter
    fun storageToIssuePurgeFailurePhase(value: String?): IssuePurgeFailurePhase? =
        value?.let { stored ->
            IssuePurgeFailurePhase.entries.firstOrNull { it.storageValue == stored }
                ?: throw IllegalArgumentException("未知的彻底清除失败阶段")
        }
}

@Entity(
    tableName = "issue_archive_events",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["issueId"]),
        Index(value = ["archiveOperationId"], unique = true),
        Index(value = ["issueId", "archivedAt"]),
    ],
)
data class IssueArchiveEventEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val archiveOperationId: String,
    val payloadHash: String,
    val summaryMarkdown: String,
    val currentStageIdSnapshot: String?,
    val stageCountSnapshot: Int,
    val runCountSnapshot: Int,
    val draftCountSnapshot: Int,
    val artifactCountSnapshot: Int,
    val audioAssetCountSnapshot: Int,
    val archivedAt: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "issue_resume_events",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = IssueArchiveEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["archiveEventId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["issueId"]),
        Index(value = ["archiveEventId"]),
        Index(value = ["resumeOperationId"], unique = true),
        Index(value = ["issueId", "resumedAt"]),
    ],
)
data class IssueResumeEventEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val archiveEventId: String,
    val resumeOperationId: String,
    val payloadHash: String,
    val changeNote: String,
    val resumedAt: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "issue_relations",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceIssueId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetIssueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = IssueArchiveEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceArchiveEventId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sourceIssueId"]),
        Index(value = ["targetIssueId"]),
        Index(value = ["sourceArchiveEventId"]),
        Index(value = ["operationId"], unique = true),
        Index(value = ["sourceIssueId", "targetIssueId", "relationType"]),
    ],
)
data class IssueRelationEntity(
    @PrimaryKey val id: String,
    val sourceIssueId: String?,
    val targetIssueId: String,
    val sourceArchiveEventId: String?,
    val operationId: String,
    val payloadHash: String,
    @ColumnInfo(defaultValue = "'continuation'")
    val relationType: IssueRelationType = IssueRelationType.CONTINUATION,
    val createdAt: Long,
    val sourcePurgedAt: Long? = null,
)

@Entity(
    tableName = "issue_purge_operations",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["issueId"], unique = true),
        Index(value = ["operationId"], unique = true),
        Index(value = ["state"]),
        Index(value = ["updatedAt"]),
    ],
)
data class IssuePurgeOperationEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val operationId: String,
    val payloadHash: String,
    val impactHash: String?,
    @ColumnInfo(defaultValue = "'requested'")
    val state: IssuePurgeState = IssuePurgeState.REQUESTED,
    val requestedAt: Long,
    val startedAt: Long? = null,
    val updatedAt: Long,
    val failedAt: Long? = null,
    val failureCode: String? = null,
    val failurePhase: IssuePurgeFailurePhase? = null,
    @ColumnInfo(defaultValue = "0")
    val retryCount: Int = 0,
)
