package com.elio.jianyu.data

internal class CollaborationRetryRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
) {
    suspend fun createCollaborationRetry(
        command: CreateCollaborationRetryCommand,
    ): RepositoryResult<CollaborationStartResult> = transactions.collaborationTransaction(
        "create_collaboration_retry",
    ) {
        val previous = core.getExecutionRun(command.previousRunId)
            ?: return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.NotFound("execution_run", command.previousRunId),
            )
        if (previous.runKind == ExecutionRunKind.STANDARD) {
            return@collaborationTransaction invalidState("standard_retry_uses_execution_coordinator")
        }
        if (!retryAllowed(previous)) {
            return@collaborationTransaction invalidState("run_not_retryable")
        }

        val previousParticipants = core.getParticipantSnapshots(previous.id)
        val statesByParticipant = core.getParticipantStates(previous.id)
            .associateBy(ExecutionParticipantStateEntity::participantSnapshotId)
        val retrySources = previousParticipants.filter { participant ->
            val status = statesByParticipant[participant.id]?.status
            status != null && shouldRetry(previous.runKind, status)
        }
        if (retrySources.isEmpty()) {
            return@collaborationTransaction invalidState("no_retryable_participant")
        }

        val requestedRun = previous.copy(
            id = command.newRunId,
            idempotencyKey = command.idempotencyKey,
            status = ExecutionRunStatus.NOT_STARTED,
            retryOfRunId = previous.id,
            createdAt = command.createdAt,
            updatedAt = command.createdAt,
            startedAt = null,
            finishedAt = null,
            stoppedAt = null,
            failureCode = null,
            failureMessage = null,
        )
        val requestedParticipants = retrySources.mapIndexed { index, participant ->
            participant.copy(
                id = "${command.newRunId}-participant-$index",
                runId = command.newRunId,
                position = index,
                createdAt = command.createdAt,
            )
        }

        core.getExecutionRunByIdempotencyKey(command.idempotencyKey)?.let { existing ->
            val existingParticipants = core.getParticipantSnapshots(existing.id)
            val usage = collaboration.getMessageUsageSnapshotsForRun(existing.id)
            if (
                sameRetryPayload(existing, requestedRun) &&
                existingParticipants == requestedParticipants &&
                usageMatchesClone(
                    sourceRunId = previous.id,
                    targetRunId = existing.id,
                    target = usage,
                )
            ) {
                val discussion = existing.discussionId?.let {
                    collaboration.getCrossDiscussionSession(it)
                }
                return@collaborationTransaction RepositoryResult.Success(
                    CollaborationStartResult(
                        runtime = loadRuntime(existing),
                        discussion = discussion,
                        messageUsage = usage,
                    ),
                    idempotent = true,
                )
            }
            return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.IdempotencyConflict(
                    "create_collaboration_retry",
                    command.idempotencyKey,
                ),
            )
        }

        val rootRunId = resolveBudgetRoot(previous)
        val budget = core.getRunBudget(rootRunId)
            ?: return@collaborationTransaction invalidState("budget_root_missing")
        if (budget.closed) {
            return@collaborationTransaction invalidState("budget_closed")
        }
        val requiredReserve = requestedParticipants.size +
            if (previous.runKind == ExecutionRunKind.CROSS_DISCUSSION_RESPONSE) 1 else 0
        if (budget.maxApiCalls - budget.usedApiCalls < requiredReserve) {
            return@collaborationTransaction invalidState("budget_exhausted")
        }

        core.insertExecutionRun(requestedRun)
        core.insertParticipantSnapshots(requestedParticipants)
        core.insertParticipantStates(
            requestedParticipants.map { participant ->
                ExecutionParticipantStateEntity(
                    participantSnapshotId = participant.id,
                    runId = requestedRun.id,
                    updatedAt = command.createdAt,
                )
            },
        )
        check(
            core.setRequiredBudgetReserve(
                rootRunId = rootRunId,
                reservedRequiredCalls = requiredReserve,
                updatedAt = command.createdAt,
            ) == 1,
        )

        cloneContextUsage(previous.id, requestedRun.id, command.createdAt)
        val messageUsage = cloneMessageUsage(previous.id, requestedRun.id, command.createdAt)
        val discussion = updateDiscussionForRetry(previous, requestedRun, command.createdAt)

        RepositoryResult.Success(
            CollaborationStartResult(
                runtime = loadRuntime(requestedRun),
                discussion = discussion,
                messageUsage = messageUsage,
            ),
        )
    }

    private suspend fun CollaborationTransactionScope.cloneContextUsage(
        sourceRunId: String,
        targetRunId: String,
        createdAt: Long,
    ) {
        val materials = core.getMaterialUsagesForRun(sourceRunId).mapIndexed { index, usage ->
            usage.copy(
                id = "$targetRunId-material-usage-$index",
                runId = targetRunId,
                createdAt = createdAt,
            )
        }
        val personal = core.getPersonalContextUsagesForRun(sourceRunId).mapIndexed { index, usage ->
            usage.copy(
                id = "$targetRunId-personal-usage-$index",
                runId = targetRunId,
                createdAt = createdAt,
            )
        }
        if (materials.isNotEmpty()) core.insertMaterialUsages(materials)
        if (personal.isNotEmpty()) core.insertPersonalContextUsages(personal)
    }

    private suspend fun CollaborationTransactionScope.cloneMessageUsage(
        sourceRunId: String,
        targetRunId: String,
        usedAt: Long,
    ): List<ExecutionMessageUsageSnapshotEntity> {
        val cloned = collaboration.getMessageUsageSnapshotsForRun(sourceRunId)
            .mapIndexed { index, usage ->
                usage.copy(
                    id = "$targetRunId-message-usage-$index",
                    runId = targetRunId,
                    usedAt = usedAt,
                )
            }
        if (cloned.isNotEmpty()) collaboration.insertMessageUsageSnapshots(cloned)
        return cloned
    }

    private suspend fun CollaborationTransactionScope.updateDiscussionForRetry(
        previous: ExecutionRunEntity,
        retry: ExecutionRunEntity,
        updatedAt: Long,
    ): CrossDiscussionSessionEntity? {
        val discussionId = previous.discussionId ?: return null
        val session = collaboration.getCrossDiscussionSession(discussionId)
            ?: throw RepositoryCompatibilityAbort("missing_cross_discussion_session")
        val targetStatus = when (previous.runKind) {
            ExecutionRunKind.CROSS_DISCUSSION_RESPONSE -> CrossDiscussionStatus.RESPONDING
            ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS -> CrossDiscussionStatus.SYNTHESIZING
            ExecutionRunKind.DIRECTED_RESPONSE,
            ExecutionRunKind.STANDARD -> return session
        }
        val expected = when (previous.runKind) {
            ExecutionRunKind.CROSS_DISCUSSION_RESPONSE -> setOf(
                CrossDiscussionStatus.PARTIAL_SUCCESS,
                CrossDiscussionStatus.FAILED,
                CrossDiscussionStatus.STOPPED,
                CrossDiscussionStatus.RESPONDING,
            )
            ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS -> setOf(
                CrossDiscussionStatus.SYNTHESIS_RETRYABLE,
                CrossDiscussionStatus.FAILED,
                CrossDiscussionStatus.STOPPED,
                CrossDiscussionStatus.SYNTHESIZING,
            )
            else -> emptySet()
        }
        if (
            collaboration.compareAndSetCrossDiscussionSession(
                sessionId = session.id,
                expectedStatuses = expected.map { it.storageValue },
                synthesisRunId = if (
                    previous.runKind == ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS
                ) retry.id else session.synthesisRunId,
                newStatus = targetStatus.storageValue,
                successfulParticipantIdsJson = session.successfulParticipantIdsJson,
                failedParticipantIdsJson = session.failedParticipantIdsJson,
                partialSynthesisConfirmedAt = session.partialSynthesisConfirmedAt,
                updatedAt = updatedAt,
                failureCode = null,
            ) != 1
        ) {
            throw RepositoryCompatibilityAbort("discussion_retry_state_changed")
        }
        return requireNotNull(collaboration.getCrossDiscussionSession(session.id))
    }

    private suspend fun CollaborationTransactionScope.loadRuntime(
        run: ExecutionRunEntity,
    ): ExecutionRuntimeSnapshot {
        val rootRunId = resolveBudgetRoot(run)
        return ExecutionRuntimeSnapshot(
            run = run,
            participants = core.getParticipantSnapshots(run.id),
            participantStates = core.getParticipantStates(run.id),
            budget = requireNotNull(core.getRunBudget(rootRunId)),
        )
    }

    private suspend fun CollaborationTransactionScope.resolveBudgetRoot(
        run: ExecutionRunEntity,
    ): String {
        var current = run
        val visited = mutableSetOf<String>()
        while (true) {
            check(visited.add(current.id)) { "Execution parent chain contains a cycle" }
            val parentId = current.retryOfRunId ?: current.parentRunId ?: return current.id
            current = core.getExecutionRun(parentId)
                ?: throw RepositoryCompatibilityAbort("missing_execution_parent")
        }
    }

    private suspend fun CollaborationTransactionScope.usageMatchesClone(
        sourceRunId: String,
        targetRunId: String,
        target: List<ExecutionMessageUsageSnapshotEntity>,
    ): Boolean {
        val source = collaboration.getMessageUsageSnapshotsForRun(sourceRunId)
        if (source.size != target.size) return false
        return source.zip(target).all { (left, right) ->
            right.runId == targetRunId &&
                left.sourceMessageId == right.sourceMessageId &&
                left.sourceExecutionRunId == right.sourceExecutionRunId &&
                left.sourceParticipantSnapshotId == right.sourceParticipantSnapshotId &&
                left.senderIdSnapshot == right.senderIdSnapshot &&
                left.senderNameSnapshot == right.senderNameSnapshot &&
                left.contentSnapshot == right.contentSnapshot &&
                left.contentHash == right.contentHash &&
                left.usageOrder == right.usageOrder
        }
    }

    private fun retryAllowed(run: ExecutionRunEntity): Boolean = when (run.runKind) {
        ExecutionRunKind.DIRECTED_RESPONSE -> run.status in setOf(
            ExecutionRunStatus.RETRYABLE,
            ExecutionRunStatus.STOPPED,
        )
        ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
        ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS -> run.status in setOf(
            ExecutionRunStatus.RETRYABLE,
            ExecutionRunStatus.STOPPED,
            ExecutionRunStatus.FAILED,
        )
        ExecutionRunKind.STANDARD -> false
    }

    private fun shouldRetry(
        runKind: ExecutionRunKind,
        status: ExecutionParticipantStatus,
    ): Boolean = when (runKind) {
        ExecutionRunKind.DIRECTED_RESPONSE -> status in RETRYABLE_PARTICIPANT_STATES
        ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
        ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS -> status != ExecutionParticipantStatus.SUCCEEDED
        ExecutionRunKind.STANDARD -> false
    }

    private fun sameRetryPayload(
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
        RepositoryError.InvalidState("create_collaboration_retry", code),
    )

    private companion object {
        val RETRYABLE_PARTICIPANT_STATES = setOf(
            ExecutionParticipantStatus.QUEUED,
            ExecutionParticipantStatus.RETRYABLE,
            ExecutionParticipantStatus.TIMED_OUT,
            ExecutionParticipantStatus.STOPPED,
        )
    }
}
