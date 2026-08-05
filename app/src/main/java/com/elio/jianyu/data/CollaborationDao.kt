package com.elio.jianyu.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface CollaborationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCrossDiscussionSession(entity: CrossDiscussionSessionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessageUsageSnapshots(entities: List<ExecutionMessageUsageSnapshotEntity>)

    @Query("SELECT * FROM cross_discussion_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getCrossDiscussionSession(sessionId: String): CrossDiscussionSessionEntity?

    @Query(
        "SELECT * FROM cross_discussion_sessions WHERE idempotencyKey = :idempotencyKey LIMIT 1",
    )
    suspend fun getCrossDiscussionSessionByIdempotencyKey(
        idempotencyKey: String,
    ): CrossDiscussionSessionEntity?

    @Query(
        "SELECT * FROM cross_discussion_sessions " +
            "WHERE stageId = :stageId ORDER BY createdAt ASC, id ASC",
    )
    suspend fun getCrossDiscussionSessionsForStage(
        stageId: String,
    ): List<CrossDiscussionSessionEntity>

    @Query(
        "SELECT * FROM execution_runs " +
            "WHERE discussionId = :discussionId " +
            "AND runKind = 'cross_discussion_response' " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun getResponseRunsForDiscussion(
        discussionId: String,
    ): List<ExecutionRunEntity>

    @Query(
        "SELECT * FROM execution_message_usage_snapshots " +
            "WHERE runId = :runId ORDER BY usageOrder ASC, sourceMessageId ASC",
    )
    suspend fun getMessageUsageSnapshotsForRun(
        runId: String,
    ): List<ExecutionMessageUsageSnapshotEntity>

    @Query(
        "UPDATE cross_discussion_sessions SET " +
            "responseRunId = :responseRunId, synthesisRunId = :synthesisRunId, " +
            "status = :newStatus, " +
            "successfulParticipantIdsJson = :successfulParticipantIdsJson, " +
            "failedParticipantIdsJson = :failedParticipantIdsJson, " +
            "partialSynthesisConfirmedAt = :partialSynthesisConfirmedAt, " +
            "updatedAt = :updatedAt, failureCode = :failureCode " +
            "WHERE id = :sessionId AND status IN (:expectedStatuses)",
    )
    suspend fun compareAndSetCrossDiscussionSession(
        sessionId: String,
        expectedStatuses: List<String>,
        responseRunId: String,
        synthesisRunId: String?,
        newStatus: String,
        successfulParticipantIdsJson: String,
        failedParticipantIdsJson: String,
        partialSynthesisConfirmedAt: Long?,
        updatedAt: Long,
        failureCode: String?,
    ): Int
}
