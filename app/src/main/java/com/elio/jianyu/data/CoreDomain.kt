package com.elio.jianyu.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverter

@Entity(
    tableName = "issues",
    indices = [
        Index(value = ["legacyChatSessionId"], unique = true)
    ]
)
data class IssueEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val legacyChatSessionId: Long? = null
)

@Entity(
    tableName = "stages",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issueId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["issueId"]),
        Index(value = ["issueId", "sequenceIndex"], unique = true),
        Index(value = ["id", "issueId"], unique = true)
    ]
)
data class StageEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val sequenceIndex: Int,
    val title: String,
    val objective: String,
    val createdAt: Long,
    val updatedAt: Long
)

enum class ExecutionRunStatus(val storageValue: String) {
    NOT_STARTED("not_started"),
    RUNNING("running"),
    PARTIAL_SUCCESS("partial_success"),
    SUCCEEDED("succeeded"),
    STOPPED("stopped"),
    FAILED("failed"),
    RETRYABLE("retryable"),
    COMPLETED("completed")
}

class CoreDomainConverters {
    @TypeConverter
    fun executionRunStatusToStorageValue(status: ExecutionRunStatus): String {
        return status.storageValue
    }

    @TypeConverter
    fun storageValueToExecutionRunStatus(value: String): ExecutionRunStatus {
        return ExecutionRunStatus.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown execution run status: $value")
    }
}

@Entity(
    tableName = "execution_runs",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["retryOfRunId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["id", "issueId", "stageId"], unique = true),
        Index(value = ["triggerMessageId"]),
        Index(value = ["retryOfRunId"])
    ]
)
data class ExecutionRunEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val stageId: String,
    val triggerMessageId: Long? = null,
    val idempotencyKey: String,
    @ColumnInfo(defaultValue = "'not_started'")
    val status: ExecutionRunStatus = ExecutionRunStatus.NOT_STARTED,
    val retryOfRunId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val stoppedAt: Long? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null
)

@Entity(
    tableName = "execution_participant_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["runId"]),
        Index(value = ["runId", "position"], unique = true),
        Index(value = ["runId", "sourceType", "sourceId"], unique = true),
        Index(value = ["id", "runId"], unique = true)
    ]
)
data class ExecutionParticipantSnapshotEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val sourceType: String,
    val sourceId: String,
    val displayName: String,
    val avatar: String,
    val skillAssetPath: String,
    val systemPrompt: String,
    val configurationJson: String,
    val defaultResponsibility: String,
    val position: Int,
    val createdAt: Long
)

@Dao
abstract class CoreDomainDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertIssue(issue: IssueEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertStage(stage: StageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertExecutionRun(run: ExecutionRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertParticipantSnapshots(
        snapshots: List<ExecutionParticipantSnapshotEntity>
    )

    @Transaction
    open suspend fun createIssueWithInitialStage(
        issue: IssueEntity,
        stage: StageEntity
    ) {
        insertIssue(issue)
        insertStage(stage)
    }

    @Transaction
    open suspend fun createRunWithParticipants(
        run: ExecutionRunEntity,
        participants: List<ExecutionParticipantSnapshotEntity>
    ) {
        insertExecutionRun(run)
        insertParticipantSnapshots(participants)
    }

    @Query("SELECT * FROM issues WHERE id = :issueId LIMIT 1")
    abstract suspend fun getIssue(issueId: String): IssueEntity?

    @Query("SELECT * FROM stages WHERE issueId = :issueId ORDER BY sequenceIndex ASC")
    abstract suspend fun getStagesForIssue(issueId: String): List<StageEntity>

    @Query("SELECT * FROM execution_runs WHERE id = :runId LIMIT 1")
    abstract suspend fun getExecutionRun(runId: String): ExecutionRunEntity?

    @Query(
        "SELECT * FROM execution_participant_snapshots " +
            "WHERE runId = :runId ORDER BY position ASC"
    )
    abstract suspend fun getParticipantSnapshots(
        runId: String
    ): List<ExecutionParticipantSnapshotEntity>

    @Query(
        "SELECT * FROM execution_runs " +
            "WHERE stageId = :stageId " +
            "AND status IN ('not_started', 'running', 'partial_success', 'retryable') " +
            "ORDER BY createdAt ASC"
    )
    abstract suspend fun getActiveRunsForStage(stageId: String): List<ExecutionRunEntity>

    @Query(
        "SELECT * FROM messages " +
            "WHERE stageId = :stageId " +
            "ORDER BY timestamp ASC, id ASC"
    )
    abstract suspend fun getMessagesForStage(stageId: String): List<Message>

    @Query(
        "UPDATE messages SET " +
            "issueId = :issueId, " +
            "stageId = :stageId, " +
            "executionRunId = :executionRunId, " +
            "participantSnapshotId = :participantSnapshotId " +
            "WHERE id = :messageId"
    )
    abstract suspend fun bindMessageToDomain(
        messageId: Long,
        issueId: String,
        stageId: String,
        executionRunId: String?,
        participantSnapshotId: String?
    ): Int

    @Query("DELETE FROM issues WHERE id = :issueId")
    abstract suspend fun deleteIssueById(issueId: String)
}
