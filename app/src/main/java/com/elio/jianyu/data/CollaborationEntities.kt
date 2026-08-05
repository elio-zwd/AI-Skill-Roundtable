package com.elio.jianyu.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class CrossDiscussionStatus(val storageValue: String) {
    RESPONDING("responding"),
    PARTIAL_SUCCESS("partial_success"),
    AWAITING_SYNTHESIS("awaiting_synthesis"),
    SYNTHESIZING("synthesizing"),
    SYNTHESIS_RETRYABLE("synthesis_retryable"),
    SUCCEEDED("succeeded"),
    STOPPED("stopped"),
    FAILED("failed"),
}

class CollaborationConverters {
    @TypeConverter
    fun crossDiscussionStatusToStorageValue(status: CrossDiscussionStatus): String =
        status.storageValue

    @TypeConverter
    fun storageValueToCrossDiscussionStatus(value: String): CrossDiscussionStatus =
        CrossDiscussionStatus.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown cross discussion status: $value")
}

@Entity(
    tableName = "cross_discussion_sessions",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["triggerMessageId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["responseRunId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["synthesisRunId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["triggerMessageId"]),
        Index(value = ["responseRunId"], unique = true),
        Index(value = ["synthesisRunId"], unique = true),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["stageId", "status"]),
    ],
)
data class CrossDiscussionSessionEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val stageId: String,
    val triggerMessageId: Long,
    val responseRunId: String,
    val synthesisRunId: String? = null,
    val integratorSkillId: String,
    val status: CrossDiscussionStatus,
    val idempotencyKey: String,
    @ColumnInfo(defaultValue = "'[]'")
    val successfulParticipantIdsJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'")
    val failedParticipantIdsJson: String = "[]",
    val partialSynthesisConfirmedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val failureCode: String? = null,
)

@Entity(
    tableName = "execution_message_usage_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["sourceMessageId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["runId"]),
        Index(value = ["sourceMessageId"]),
        Index(value = ["runId", "sourceMessageId"], unique = true),
        Index(value = ["runId", "usageOrder"], unique = true),
        Index(value = ["sourceExecutionRunId"]),
        Index(value = ["sourceParticipantSnapshotId"]),
    ],
)
data class ExecutionMessageUsageSnapshotEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val sourceMessageId: Long,
    val sourceExecutionRunId: String? = null,
    val sourceParticipantSnapshotId: String? = null,
    val senderIdSnapshot: String,
    val senderNameSnapshot: String,
    val contentSnapshot: String,
    val contentHash: String,
    val usageOrder: Int,
    val usedAt: Long,
)
