package com.elio.jianyu.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 原子创建交叉讨论整合 Run。
 *
 * 成功回应按正式 Response 根 Run 的 position 聚合；失败成员可在后续 Response retry Run 中
 * 补充成功输出。整合只快照本次讨论内真实成功输出和用户额外明确选择的消息。
 */
internal class CrossDiscussionSynthesisRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
) {
    private val json = Json

    suspend fun createCrossDiscussionSynthesis(
        command: CreateCrossDiscussionSynthesisCommand,
    ): RepositoryResult<CollaborationStartResult> = transactions.collaborationTransaction(
        "create_cross_discussion_synthesis",
    ) {
        val session = collaboration.getCrossDiscussionSession(command.sessionId)
            ?: return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.NotFound("cross_discussion_session", command.sessionId),
            )
        require(command.run.parentRunId == session.responseRunId)
        require(command.run.triggerMessageId == session.triggerMessageId)
        require(command.run.retryOfRunId == null)
        require(command.participant.sourceId == session.integratorSkillId)
        require(command.run.issueId == session.issueId)
        require(command.run.stageId == session.stageId)

        validateContextUsage(command.run, command.contextUsage)?.let { error ->
            return@collaborationTransaction RepositoryResult.Failure(error)
        }
        val aggregate = aggregateResponseOutcomes(session)
            ?: return@collaborationTransaction invalidState("response_roster_invalid")
        if (aggregate.successful.isEmpty()) {
            return@collaborationTransaction invalidState("no_successful_response")
        }
        if (aggregate.failedSourceIds.isNotEmpty() && !command.userAcceptedPartial) {
            return@collaborationTransaction invalidState(
                "partial_synthesis_confirmation_required",
            )
        }

        val successfulMessageIds = aggregate.successful.map { outcome ->
            outcome.state.outputMessageId
                ?: return@collaborationTransaction invalidState(
                    "successful_participant_missing_output",
                )
        }
        val selectedMessageIds = command.additionalSelectedMessageIds + successfulMessageIds
        if (selectedMessageIds.distinct().size != selectedMessageIds.size) {
            return@collaborationTransaction invalidState("duplicate_message_usage")
        }
        val expectedMessageUsage = buildMessageUsage(
            run = command.run,
            selectedMessageIds = selectedMessageIds,
            usedAt = command.createdAt,
        ) ?: return@collaborationTransaction invalidState("message_usage_invalid")

        core.getExecutionRunByIdempotencyKey(command.run.idempotencyKey)?.let { existingRun ->
            val existingUsage = collaboration.getMessageUsageSnapshotsForRun(existingRun.id)
            if (
                sameRunCreationPayload(existingRun, command.run) &&
                core.getParticipantSnapshots(existingRun.id) == listOf(command.participant) &&
                contextUsageMatches(existingRun.id, command.contextUsage) &&
                existingUsage == expectedMessageUsage &&
                session.synthesisRunId == existingRun.id
            ) {
                return@collaborationTransaction RepositoryResult.Success(
                    CollaborationStartResult(
                        runtime = loadRuntime(existingRun, aggregate.rootRun.id),
                        discussion = session,
                        messageUsage = existingUsage,
                    ),
                    idempotent = true,
                )
            }
            return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.IdempotencyConflict(
                    "create_cross_discussion_synthesis",
                    command.run.idempotencyKey,
                ),
            )
        }

        if (session.status !in setOf(
                CrossDiscussionStatus.AWAITING_SYNTHESIS,
                CrossDiscussionStatus.PARTIAL_SUCCESS,
            )
        ) {
            return@collaborationTransaction invalidState(
                "discussion_not_ready_for_synthesis",
            )
        }
        if (session.status == CrossDiscussionStatus.PARTIAL_SUCCESS && !command.userAcceptedPartial) {
            return@collaborationTransaction invalidState(
                "partial_synthesis_confirmation_required",
            )
        }

        val rootBudget = core.getRunBudget(aggregate.rootRun.id)
            ?: return@collaborationTransaction invalidState("response_budget_missing")
        if (rootBudget.closed) {
            return@collaborationTransaction invalidState("response_budget_closed")
        }
        if (rootBudget.maxApiCalls - rootBudget.usedApiCalls < 1) {
            return@collaborationTransaction invalidState("response_budget_exhausted")
        }

        core.insertExecutionRun(command.run)
        core.insertParticipantSnapshots(listOf(command.participant))
        core.insertParticipantStates(
            listOf(
                ExecutionParticipantStateEntity(
                    participantSnapshotId = command.participant.id,
                    runId = command.run.id,
                    updatedAt = command.createdAt,
                ),
            ),
        )
        check(
            core.setRequiredBudgetReserve(
                rootRunId = aggregate.rootRun.id,
                count = 1,
                updatedAt = command.createdAt,
            ) == 1,
        )
        val sortedUsage = command.contextUsage.sorted()
        if (sortedUsage.materials.isNotEmpty()) {
            core.insertMaterialUsages(sortedUsage.materials)
        }
        if (sortedUsage.personalContexts.isNotEmpty()) {
            core.insertPersonalContextUsages(sortedUsage.personalContexts)
        }
        if (expectedMessageUsage.isNotEmpty()) {
            collaboration.insertMessageUsageSnapshots(expectedMessageUsage)
        }

        val successfulSourceIds = aggregate.successful.map { it.source.sourceId }
        if (
            collaboration.compareAndSetCrossDiscussionSession(
                sessionId = session.id,
                expectedStatuses = listOf(session.status.storageValue),
                synthesisRunId = command.run.id,
                newStatus = CrossDiscussionStatus.SYNTHESIZING.storageValue,
                successfulParticipantIdsJson = json.encodeToString(successfulSourceIds),
                failedParticipantIdsJson = json.encodeToString(aggregate.failedSourceIds),
                partialSynthesisConfirmedAt = if (aggregate.failedSourceIds.isEmpty()) {
                    session.partialSynthesisConfirmedAt
                } else {
                    command.createdAt
                },
                updatedAt = command.createdAt,
                failureCode = null,
            ) != 1
        ) {
            return@collaborationTransaction invalidState("discussion_state_changed")
        }
        val updatedSession = requireNotNull(
            collaboration.getCrossDiscussionSession(session.id),
        )
        RepositoryResult.Success(
            CollaborationStartResult(
                runtime = loadRuntime(command.run, aggregate.rootRun.id),
                discussion = updatedSession,
                messageUsage = expectedMessageUsage,
            ),
        )
    }

    private suspend fun CollaborationTransactionScope.aggregateResponseOutcomes(
        session: CrossDiscussionSessionEntity,
    ): ResponseAggregate? {
        val responseRuns = collaboration.getResponseRunsForDiscussion(session.id)
        val rootRun = responseRuns.singleOrNull { run -> run.retryOfRunId == null }
            ?: return null
        if (rootRun.id != session.responseRunId && session.responseRunId !in responseRuns.map { it.id }) {
            return null
        }
        val rootParticipants = core.getParticipantSnapshots(rootRun.id).sortedBy { it.position }
        if (rootParticipants.size < 2) return null
        if (rootParticipants.map { it.sourceId }.distinct().size != rootParticipants.size) return null

        val attempts = responseRuns.flatMap { run ->
            val states = core.getParticipantStates(run.id)
                .associateBy(ExecutionParticipantStateEntity::participantSnapshotId)
            core.getParticipantSnapshots(run.id).mapNotNull { participant ->
                states[participant.id]?.let { state ->
                    ResponseAttempt(run, participant, state)
                }
            }
        }
        val successful = rootParticipants.mapNotNull { source ->
            attempts
                .filter { attempt ->
                    attempt.participant.sourceId == source.sourceId &&
                        attempt.state.status == ExecutionParticipantStatus.SUCCEEDED &&
                        attempt.state.outputMessageId != null
                }
                .maxWithOrNull(
                    compareBy<ResponseAttempt>({ it.run.createdAt }, { it.run.id }),
                )
                ?.let { attempt ->
                    SuccessfulResponse(source, attempt.run, attempt.participant, attempt.state)
                }
        }
        val successfulIds = successful.mapTo(mutableSetOf()) { it.source.sourceId }
        return ResponseAggregate(
            rootRun = rootRun,
            successful = successful,
            failedSourceIds = rootParticipants
                .map { it.sourceId }
                .filterNot { it in successfulIds },
        )
    }

    private suspend fun CollaborationTransactionScope.buildMessageUsage(
        run: ExecutionRunEntity,
        selectedMessageIds: List<Long>,
        usedAt: Long,
    ): List<ExecutionMessageUsageSnapshotEntity>? = selectedMessageIds.mapIndexed { index, messageId ->
        val message = core.getMessage(messageId) ?: return null
        if (
            message.issueId != run.issueId ||
            message.stageId != run.stageId ||
            message.isPending ||
            message.executionRunId == run.id ||
            message.text.isBlank()
        ) {
            return null
        }
        ExecutionMessageUsageSnapshotEntity(
            id = "${run.id}-message-usage-$index",
            runId = run.id,
            sourceMessageId = message.id,
            sourceExecutionRunId = message.executionRunId,
            sourceParticipantSnapshotId = message.participantSnapshotId,
            senderIdSnapshot = message.senderId,
            senderNameSnapshot = message.senderName,
            contentSnapshot = message.text,
            contentHash = ContextContentHasher.hash(message.text),
            usageOrder = index,
            usedAt = usedAt,
        )
    }

    private suspend fun CollaborationTransactionScope.loadRuntime(
        run: ExecutionRunEntity,
        rootRunId: String,
    ): ExecutionRuntimeSnapshot = ExecutionRuntimeSnapshot(
        run = run,
        participants = core.getParticipantSnapshots(run.id),
        participantStates = core.getParticipantStates(run.id),
        budget = requireNotNull(core.getRunBudget(rootRunId)),
    )

    private suspend fun CollaborationTransactionScope.contextUsageMatches(
        runId: String,
        requested: ContextUsageWriteSet,
    ): Boolean {
        val sorted = requested.sorted()
        return core.getMaterialUsagesForRun(runId) == sorted.materials &&
            core.getPersonalContextUsagesForRun(runId) == sorted.personalContexts
    }

    private suspend fun CollaborationTransactionScope.validateContextUsage(
        run: ExecutionRunEntity,
        usage: ContextUsageWriteSet,
    ): RepositoryError? {
        val materialValid = usage.materials.all { item ->
            item.runId == run.id &&
                item.issueId == run.issueId &&
                item.stageId == run.stageId &&
                item.materialReferenceId != null &&
                item.contentSnapshot?.isNotBlank() == true &&
                item.contentHash == ContextContentHasher.hash(requireNotNull(item.contentSnapshot)) &&
                item.networkAllowed &&
                item.userConfirmedAt > 0L
        }
        val personalValid = usage.personalContexts.all { item ->
            item.runId == run.id &&
                item.issueId == run.issueId &&
                item.stageId == run.stageId &&
                item.personalContextEntryId != null &&
                item.contentSnapshot?.isNotBlank() == true &&
                item.contentHash == ContextContentHasher.hash(requireNotNull(item.contentSnapshot)) &&
                item.networkAllowed &&
                item.userConfirmedAt > 0L
        }
        val materialIds = usage.materials.map { it.materialReferenceId }
        val personalIds = usage.personalContexts.map { it.personalContextEntryId }
        val expectationKeys = usage.sourceExpectations.map { it.sourceType to it.sourceId }
        if (
            !materialValid ||
            !personalValid ||
            materialIds.distinct().size != materialIds.size ||
            personalIds.distinct().size != personalIds.size ||
            expectationKeys.distinct().size != expectationKeys.size ||
            usage.sourceExpectations.size != usage.materials.size + usage.personalContexts.size
        ) {
            return RepositoryError.ConstraintViolation(
                "create_cross_discussion_synthesis",
                ContextValidationError.USAGE_SNAPSHOT_CONFLICT.code,
            )
        }
        val sourcesCurrent = usage.sourceExpectations.all { expectation ->
            when (expectation.sourceType) {
                ContextSourceType.MATERIAL -> core.getMaterialReference(expectation.sourceId)?.let { source ->
                    source.lifecycleState == ContextSourceLifecycle.ACTIVE &&
                        source.updatedAt == expectation.expectedUpdatedAt &&
                        source.contentHash == expectation.expectedContentHash
                } ?: false
                ContextSourceType.PERSONAL_CONTEXT ->
                    core.getPersonalContextEntry(expectation.sourceId)?.let { source ->
                        source.lifecycleState == ContextSourceLifecycle.ACTIVE &&
                            source.isEnabled &&
                            source.updatedAt == expectation.expectedUpdatedAt &&
                            source.contentHash == expectation.expectedContentHash
                    } ?: false
            }
        }
        return if (sourcesCurrent) null else {
            RepositoryError.InvalidState(
                "create_cross_discussion_synthesis",
                ContextValidationError.SOURCE_STALE.code,
            )
        }
    }

    private fun sameRunCreationPayload(
        existing: ExecutionRunEntity,
        requested: ExecutionRunEntity,
    ): Boolean = existing.id == requested.id &&
        existing.issueId == requested.issueId &&
        existing.stageId == requested.stageId &&
        existing.triggerMessageId == requested.triggerMessageId &&
        existing.idempotencyKey == requested.idempotencyKey &&
        existing.retryOfRunId == requested.retryOfRunId &&
        existing.runKind == requested.runKind &&
        existing.parentRunId == requested.parentRunId &&
        existing.discussionId == requested.discussionId &&
        existing.historyScope == requested.historyScope &&
        existing.createdAt == requested.createdAt

    private fun <T> invalidState(code: String): RepositoryResult<T> = RepositoryResult.Failure(
        RepositoryError.InvalidState("create_cross_discussion_synthesis", code),
    )

    private data class ResponseAttempt(
        val run: ExecutionRunEntity,
        val participant: ExecutionParticipantSnapshotEntity,
        val state: ExecutionParticipantStateEntity,
    )

    private data class SuccessfulResponse(
        val source: ExecutionParticipantSnapshotEntity,
        val run: ExecutionRunEntity,
        val participant: ExecutionParticipantSnapshotEntity,
        val state: ExecutionParticipantStateEntity,
    )

    private data class ResponseAggregate(
        val rootRun: ExecutionRunEntity,
        val successful: List<SuccessfulResponse>,
        val failedSourceIds: List<String>,
    )
}
