package com.elio.jianyu.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class ExecutionParticipantStatus(val storageValue: String) {
    QUEUED("queued"),
    RUNNING("running"),
    STREAMING("streaming"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    TIMED_OUT("timed_out"),
    STOPPED("stopped"),
    RETRYABLE("retryable"),
}

class ExecutionRuntimeConverters {
    @TypeConverter
    fun participantStatusToStorageValue(status: ExecutionParticipantStatus): String =
        status.storageValue

    @TypeConverter
    fun storageValueToParticipantStatus(value: String): ExecutionParticipantStatus =
        ExecutionParticipantStatus.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown execution participant status: $value")
}

@Entity(
    tableName = "execution_participant_states",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionParticipantSnapshotEntity::class,
            parentColumns = ["id", "runId"],
            childColumns = ["participantSnapshotId", "runId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["outputMessageId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["participantSnapshotId", "runId"]),
        Index(value = ["runId"]),
        Index(value = ["runId", "status"]),
        Index(value = ["outputMessageId"]),
    ],
)
data class ExecutionParticipantStateEntity(
    @PrimaryKey val participantSnapshotId: String,
    val runId: String,
    @ColumnInfo(defaultValue = "'queued'")
    val status: ExecutionParticipantStatus = ExecutionParticipantStatus.QUEUED,
    @ColumnInfo(defaultValue = "0")
    val attemptCount: Int = 0,
    val outputMessageId: Long? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val lastErrorCode: String? = null,
    val lastErrorMessage: String? = null,
    @ColumnInfo(defaultValue = "0")
    val hasIncompleteOutput: Boolean = false,
    val updatedAt: Long,
)

@Entity(
    tableName = "execution_run_budgets",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["rootRunId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class ExecutionRunBudgetEntity(
    @PrimaryKey val rootRunId: String,
    @ColumnInfo(defaultValue = "0")
    val usedApiCalls: Int = 0,
    val maxCharacters: Int,
    val maxSearchQueriesPerCharacter: Int,
    val maxOutputTokensPerAnswer: Int,
    @ColumnInfo(defaultValue = "0")
    val closed: Boolean = false,
    val updatedAt: Long,
)
