package com.elio.jianyu.data

data class CreateDirectedInteractionCommand(
    val userMessage: AppendDomainMessageCommand,
    val run: ExecutionRunEntity,
    val participant: ExecutionParticipantSnapshotEntity,
    val budget: ExecutionRuntimeBudgetConfig,
    val contextUsage: ContextUsageWriteSet = ContextUsageWriteSet(),
    val selectedMessageIds: List<Long> = emptyList(),
) {
    init {
        require(run.runKind == ExecutionRunKind.DIRECTED_RESPONSE)
        require(run.historyScope != ExecutionHistoryScope.FULL_STAGE)
        require(run.triggerMessageId == userMessage.messageId)
        require(participant.runId == run.id)
        require(participant.position == 0)
        require(selectedMessageIds.distinct().size == selectedMessageIds.size)
    }
}

data class CreateCrossDiscussionResponseCommand(
    val userMessage: AppendDomainMessageCommand,
    val session: CrossDiscussionSessionEntity,
    val run: ExecutionRunEntity,
    val participants: List<ExecutionParticipantSnapshotEntity>,
    val budget: ExecutionRuntimeBudgetConfig,
    val contextUsage: ContextUsageWriteSet = ContextUsageWriteSet(),
    val selectedMessageIds: List<Long> = emptyList(),
) {
    init {
        require(run.runKind == ExecutionRunKind.CROSS_DISCUSSION_RESPONSE)
        require(run.historyScope != ExecutionHistoryScope.FULL_STAGE)
        require(run.triggerMessageId == userMessage.messageId)
        require(run.discussionId == session.id)
        require(session.responseRunId == run.id)
        require(session.triggerMessageId == userMessage.messageId)
        require(participants.size >= 2)
        require(participants.all { it.runId == run.id })
        require(participants.map { it.sourceId }.distinct().size == participants.size)
        require(selectedMessageIds.distinct().size == selectedMessageIds.size)
        require(budget.maxApiCalls >= participants.size + 1)
    }
}

data class CreateCrossDiscussionSynthesisCommand(
    val sessionId: String,
    val run: ExecutionRunEntity,
    val participant: ExecutionParticipantSnapshotEntity,
    val contextUsage: ContextUsageWriteSet = ContextUsageWriteSet(),
    val userAcceptedPartial: Boolean,
    val createdAt: Long,
) {
    init {
        require(sessionId.isNotBlank())
        require(run.runKind == ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS)
        require(run.historyScope == ExecutionHistoryScope.EXPLICIT_MESSAGES)
        require(run.discussionId == sessionId)
        require(run.parentRunId != null)
        require(participant.runId == run.id)
        require(participant.position == 0)
        require(createdAt > 0L)
    }
}

data class TransitionCrossDiscussionCommand(
    val sessionId: String,
    val expectedStatuses: Set<CrossDiscussionStatus>,
    val newStatus: CrossDiscussionStatus,
    val synthesisRunId: String? = null,
    val successfulParticipantIds: List<String> = emptyList(),
    val failedParticipantIds: List<String> = emptyList(),
    val partialSynthesisConfirmedAt: Long? = null,
    val updatedAt: Long,
    val failureCode: String? = null,
) {
    init {
        require(sessionId.isNotBlank())
        require(expectedStatuses.isNotEmpty())
        require(successfulParticipantIds.distinct().size == successfulParticipantIds.size)
        require(failedParticipantIds.distinct().size == failedParticipantIds.size)
        require(successfulParticipantIds.none { it in failedParticipantIds })
        require(updatedAt > 0L)
    }
}

data class CollaborationStartResult(
    val runtime: ExecutionRuntimeSnapshot,
    val discussion: CrossDiscussionSessionEntity? = null,
    val messageUsage: List<ExecutionMessageUsageSnapshotEntity> = emptyList(),
)

data class StageCollaborationSnapshot(
    val discussions: List<CrossDiscussionSessionEntity>,
    val messageUsageByRun: Map<String, List<ExecutionMessageUsageSnapshotEntity>>,
)

internal interface JianyuCollaborationRepository {
    suspend fun createDirectedInteraction(
        command: CreateDirectedInteractionCommand,
    ): RepositoryResult<CollaborationStartResult>

    suspend fun createCrossDiscussionResponse(
        command: CreateCrossDiscussionResponseCommand,
    ): RepositoryResult<CollaborationStartResult>

    suspend fun createCrossDiscussionSynthesis(
        command: CreateCrossDiscussionSynthesisCommand,
    ): RepositoryResult<CollaborationStartResult>

    suspend fun transitionCrossDiscussion(
        command: TransitionCrossDiscussionCommand,
    ): RepositoryResult<CrossDiscussionSessionEntity>

    suspend fun getStageCollaboration(
        stageId: String,
    ): RepositoryResult<StageCollaborationSnapshot>

    suspend fun listExecutionMessageUsage(
        runId: String,
    ): RepositoryResult<List<ExecutionMessageUsageSnapshotEntity>>
}

suspend fun JianyuRepository.createDirectedInteraction(
    command: CreateDirectedInteractionCommand,
): RepositoryResult<CollaborationStartResult> = collaborationCapability(
    "create_directed_interaction",
)?.createDirectedInteraction(command) ?: missingCollaborationCapability("create_directed_interaction")

suspend fun JianyuRepository.createCrossDiscussionResponse(
    command: CreateCrossDiscussionResponseCommand,
): RepositoryResult<CollaborationStartResult> = collaborationCapability(
    "create_cross_discussion_response",
)?.createCrossDiscussionResponse(command)
    ?: missingCollaborationCapability("create_cross_discussion_response")

suspend fun JianyuRepository.createCrossDiscussionSynthesis(
    command: CreateCrossDiscussionSynthesisCommand,
): RepositoryResult<CollaborationStartResult> = collaborationCapability(
    "create_cross_discussion_synthesis",
)?.createCrossDiscussionSynthesis(command)
    ?: missingCollaborationCapability("create_cross_discussion_synthesis")

suspend fun JianyuRepository.transitionCrossDiscussion(
    command: TransitionCrossDiscussionCommand,
): RepositoryResult<CrossDiscussionSessionEntity> = collaborationCapability(
    "transition_cross_discussion",
)?.transitionCrossDiscussion(command) ?: missingCollaborationCapability("transition_cross_discussion")

suspend fun JianyuRepository.getStageCollaboration(
    stageId: String,
): RepositoryResult<StageCollaborationSnapshot> = collaborationCapability(
    "get_stage_collaboration",
)?.getStageCollaboration(stageId) ?: missingCollaborationCapability("get_stage_collaboration")

suspend fun JianyuRepository.listExecutionMessageUsage(
    runId: String,
): RepositoryResult<List<ExecutionMessageUsageSnapshotEntity>> = collaborationCapability(
    "list_execution_message_usage",
)?.listExecutionMessageUsage(runId) ?: missingCollaborationCapability("list_execution_message_usage")

private fun JianyuRepository.collaborationCapability(
    operation: String,
): JianyuCollaborationRepository? {
    @Suppress("UNUSED_VARIABLE")
    val operationName = operation
    return this as? JianyuCollaborationRepository
}

private fun <T> missingCollaborationCapability(operation: String): RepositoryResult<T> =
    RepositoryResult.Failure(
        RepositoryError.CompatibilityFailure(operation, "collaboration_not_supported"),
    )
