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
    indices = [Index(value = ["legacyChatSessionId"], unique = true)],
)
data class IssueEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val legacyChatSessionId: Long? = null,
    @ColumnInfo(defaultValue = "'auto'")
    val defaultThinkingPolicy: IssueThinkingPolicy = IssueThinkingPolicy.AUTO,
)

enum class IssueThinkingPolicy(val storageValue: String) {
    AUTO("auto"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

enum class ExecutionThinkingLevel(val storageValue: String) {
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
}

enum class ExecutionThinkingSource(val storageValue: String) {
    ROUND_USER_OVERRIDE("round_user_override"),
    ISSUE_USER_DEFAULT("issue_user_default"),
    AUTO_ROUTED("auto_routed"),
}

@Entity(
    tableName = "stages",
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
        Index(value = ["issueId", "sequenceIndex"], unique = true),
        Index(value = ["id", "issueId"], unique = true),
    ],
)
data class StageEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val sequenceIndex: Int,
    val title: String,
    val objective: String,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class ExecutionRunStatus(val storageValue: String) {
    NOT_STARTED("not_started"),
    RUNNING("running"),
    PARTIAL_SUCCESS("partial_success"),
    SUCCEEDED("succeeded"),
    STOPPED("stopped"),
    FAILED("failed"),
    RETRYABLE("retryable"),
    COMPLETED("completed"),
}

enum class ExecutionRunKind(val storageValue: String) {
    STANDARD("standard"),
    DIRECTED_RESPONSE("directed_response"),
    CROSS_DISCUSSION_RESPONSE("cross_discussion_response"),
    CROSS_DISCUSSION_SYNTHESIS("cross_discussion_synthesis"),
}

enum class ExecutionHistoryScope(val storageValue: String) {
    FULL_STAGE("full_stage"),
    EXPLICIT_MESSAGES("explicit_messages"),
    NO_HISTORY("no_history"),
}

class CoreDomainConverters {
    @TypeConverter
    fun executionRunStatusToStorageValue(status: ExecutionRunStatus): String = status.storageValue

    @TypeConverter
    fun storageValueToExecutionRunStatus(value: String): ExecutionRunStatus =
        ExecutionRunStatus.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown execution run status: $value")

    @TypeConverter
    fun executionRunKindToStorageValue(kind: ExecutionRunKind): String = kind.storageValue

    @TypeConverter
    fun storageValueToExecutionRunKind(value: String): ExecutionRunKind =
        ExecutionRunKind.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown execution run kind: $value")

    @TypeConverter
    fun executionHistoryScopeToStorageValue(scope: ExecutionHistoryScope): String = scope.storageValue

    @TypeConverter
    fun storageValueToExecutionHistoryScope(value: String): ExecutionHistoryScope =
        ExecutionHistoryScope.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown execution history scope: $value")

    @TypeConverter
    fun issueThinkingPolicyToStorageValue(policy: IssueThinkingPolicy): String = policy.storageValue

    @TypeConverter
    fun storageValueToIssueThinkingPolicy(value: String): IssueThinkingPolicy =
        IssueThinkingPolicy.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown issue thinking policy: $value")

    @TypeConverter
    fun executionThinkingLevelToStorageValue(level: ExecutionThinkingLevel): String = level.storageValue

    @TypeConverter
    fun storageValueToExecutionThinkingLevel(value: String): ExecutionThinkingLevel =
        ExecutionThinkingLevel.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown execution thinking level: $value")

    @TypeConverter
    fun executionThinkingSourceToStorageValue(source: ExecutionThinkingSource): String = source.storageValue

    @TypeConverter
    fun storageValueToExecutionThinkingSource(value: String): ExecutionThinkingSource =
        ExecutionThinkingSource.entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unknown execution thinking source: $value")
}

@Entity(
    tableName = "execution_runs",
    foreignKeys = [
        ForeignKey(
            entity = StageEntity::class,
            parentColumns = ["id", "issueId"],
            childColumns = ["stageId", "issueId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["retryOfRunId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["stageId", "issueId"]),
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["id", "issueId"], unique = true),
        Index(value = ["id", "issueId", "stageId"], unique = true),
        Index(value = ["triggerMessageId"]),
        Index(value = ["retryOfRunId"]),
        Index(value = ["parentRunId"]),
        Index(value = ["discussionId"]),
        Index(value = ["stageId", "runKind"]),
    ],
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
    val failureMessage: String? = null,
    @ColumnInfo(defaultValue = "'standard'")
    val runKind: ExecutionRunKind = ExecutionRunKind.STANDARD,
    val parentRunId: String? = null,
    val discussionId: String? = null,
    @ColumnInfo(defaultValue = "'full_stage'")
    val historyScope: ExecutionHistoryScope = ExecutionHistoryScope.FULL_STAGE,
    val actualModelId: String,
    val actualThinkingLevel: ExecutionThinkingLevel,
    val thinkingLevelSource: ExecutionThinkingSource,
)

@Entity(
    tableName = "execution_participant_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = ExecutionRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["runId"]),
        Index(value = ["runId", "position"], unique = true),
        Index(value = ["runId", "sourceType", "sourceId"], unique = true),
        Index(value = ["id", "runId"], unique = true),
    ],
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
    val createdAt: Long,
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
        snapshots: List<ExecutionParticipantSnapshotEntity>,
    )

    @Transaction
    open suspend fun createIssueWithInitialStage(issue: IssueEntity, stage: StageEntity) {
        insertIssue(issue)
        insertStage(stage)
    }

    @Transaction
    open suspend fun createRunWithParticipants(
        run: ExecutionRunEntity,
        participants: List<ExecutionParticipantSnapshotEntity>,
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
            "WHERE runId = :runId ORDER BY position ASC",
    )
    abstract suspend fun getParticipantSnapshots(runId: String): List<ExecutionParticipantSnapshotEntity>

    @Query(
        "SELECT * FROM execution_runs WHERE stageId = :stageId " +
            "AND status IN ('not_started', 'running', 'partial_success', 'retryable') " +
            "ORDER BY createdAt ASC",
    )
    abstract suspend fun getActiveRunsForStage(stageId: String): List<ExecutionRunEntity>

    @Query("SELECT * FROM messages WHERE stageId = :stageId ORDER BY timestamp ASC, id ASC")
    abstract suspend fun getMessagesForStage(stageId: String): List<Message>

    @Query(
        "UPDATE messages SET issueId = :issueId, stageId = :stageId, " +
            "executionRunId = :executionRunId, participantSnapshotId = :participantSnapshotId " +
            "WHERE id = :messageId",
    )
    abstract suspend fun bindMessageToDomain(
        messageId: Long,
        issueId: String,
        stageId: String,
        executionRunId: String?,
        participantSnapshotId: String?,
    ): Int

    @Query("DELETE FROM issues WHERE id = :issueId")
    abstract suspend fun deleteIssueById(issueId: String)
}
