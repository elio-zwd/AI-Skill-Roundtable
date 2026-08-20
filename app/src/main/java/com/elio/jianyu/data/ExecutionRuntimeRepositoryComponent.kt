package com.elio.jianyu.data

import com.elio.jianyu.execution.ExecutionStateMachine

internal class ExecutionRuntimeRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
) {
    suspend fun createExecutionRuntime(
        command: CreateExecutionRuntimeCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        return transactions.transaction("create_execution_runtime") {
            require(command.run.id.isNotBlank())
            require(command.run.idempotencyKey.isNotBlank())
            require(command.run.status == ExecutionRunStatus.NOT_STARTED)
            require(command.budgetRootRunId.isNotBlank())
            require(command.participants.isNotEmpty())
            require(validateParticipantPayload(command.participants))
            require(command.participants.all { it.runId == command.run.id })

            val existing = getExecutionRunByIdempotencyKey(command.run.idempotencyKey)
            if (existing != null) {
                val participants = getParticipantSnapshots(existing.id)
                if (
                    sameCreationPayload(existing, command.run) &&
                    participants == command.participants.sortedBy { it.position } &&
                    contextUsageMatches(existing.id, command.contextUsage)
                ) {
                    return@transaction loadRuntimeSnapshot(existing)
                        .withIdempotentFlag()
                }
                return@transaction RepositoryResult.Failure(
                    RepositoryError.IdempotencyConflict(
                        "create_execution_runtime",
                        command.run.idempotencyKey,
                    ),
                )
            }

            val stage = getStage(command.run.stageId)
            if (stage == null || stage.issueId != command.run.issueId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", command.run.stageId),
                )
            }

            val activeRun = getExecutionRunsForIssue(command.run.issueId).firstOrNull { run ->
                run.stageId == command.run.stageId &&
                    run.status in setOf(
                        ExecutionRunStatus.NOT_STARTED,
                        ExecutionRunStatus.RUNNING,
                        ExecutionRunStatus.PARTIAL_SUCCESS,
                    )
            }
            if (activeRun != null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "create_execution_runtime",
                        "stage_has_active_run",
                    ),
                )
            }

            val parentRun = command.run.retryOfRunId?.let { getExecutionRun(it) }
            if (command.run.retryOfRunId != null) {
                if (
                    parentRun == null ||
                    parentRun.issueId != command.run.issueId ||
                    parentRun.stageId != command.run.stageId ||
                    parentRun.status !in setOf(
                        ExecutionRunStatus.RETRYABLE,
                        ExecutionRunStatus.STOPPED,
                    )
                ) {
                    return@transaction RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "create_execution_runtime",
                            "retry_parent_invalid",
                        ),
                    )
                }
            }

            validateContextUsage(command)?.let { error ->
                return@transaction RepositoryResult.Failure(error)
            }

            insertExecutionRun(command.run)
            val participants = command.participants.sortedBy { it.position }
            insertParticipantSnapshots(participants)
            insertParticipantStates(
                participants.map { participant ->
                    ExecutionParticipantStateEntity(
                        participantSnapshotId = participant.id,
                        runId = command.run.id,
                        updatedAt = command.run.createdAt,
                    )
                },
            )

            if (command.budgetRootRunId == command.run.id) {
                insertRunBudget(
                    ExecutionRunBudgetEntity(
                        rootRunId = command.run.id,
                        maxCharacters = command.budget.maxCharacters,
                        maxSearchQueriesPerCharacter =
                            command.budget.maxSearchQueriesPerCharacter,
                        maxOutputTokensPerAnswer = command.budget.maxOutputTokensPerAnswer,
                        updatedAt = command.run.createdAt,
                    ),
                )
            } else if (getRunBudget(command.budgetRootRunId) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "create_execution_runtime",
                        "retry_budget_root_missing",
                    ),
                )
            }

            if (command.contextUsage.materials.isNotEmpty()) {
                insertMaterialUsages(command.contextUsage.materials.sortedBy { it.id })
            }
            if (command.contextUsage.personalContexts.isNotEmpty()) {
                insertPersonalContextUsages(command.contextUsage.personalContexts.sortedBy { it.id })
            }

            loadRuntimeSnapshot(command.run)
        }
    }

    suspend fun getExecutionRuntime(
        runId: String,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        return transactions.transaction("get_execution_runtime") {
            val run = getExecutionRun(runId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("execution_run", runId),
                )
            loadRuntimeSnapshot(run)
        }
    }

    suspend fun transitionExecutionParticipant(
        command: TransitionExecutionParticipantCommand,
    ): RepositoryResult<ExecutionParticipantStateEntity> {
        return transactions.transaction("transition_execution_participant") {
            require(command.expectedStatuses.isNotEmpty())
            require(command.attemptIncrement >= 0)
            require(command.updatedAt > 0L)
            val existing = getParticipantState(command.participantSnapshotId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound(
                        "execution_participant_state",
                        command.participantSnapshotId,
                    ),
                )
            if (existing.runId != command.runId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "transition_execution_participant",
                        "participant_run_mismatch",
                    ),
                )
            }
            if (!canTransitionParticipant(existing.status, command.newStatus)) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "transition_execution_participant",
                        "illegal_transition",
                    ),
                )
            }
            val target = existing.copy(
                status = command.newStatus,
                attemptCount = existing.attemptCount + command.attemptIncrement,
                outputMessageId = command.outputMessageId ?: existing.outputMessageId,
                startedAt = command.startedAt ?: existing.startedAt,
                finishedAt = command.finishedAt ?: existing.finishedAt,
                lastErrorCode = command.lastErrorCode,
                lastErrorMessage = command.lastErrorMessage,
                hasIncompleteOutput = command.hasIncompleteOutput,
                updatedAt = command.updatedAt,
            )
            if (target == existing) {
                return@transaction RepositoryResult.Success(existing, idempotent = true)
            }
            val changed = compareAndSetParticipantState(
                participantSnapshotId = command.participantSnapshotId,
                runId = command.runId,
                expectedStatuses = command.expectedStatuses.map { it.storageValue },
                newStatus = target.status.storageValue,
                attemptIncrement = command.attemptIncrement,
                outputMessageId = target.outputMessageId,
                startedAt = target.startedAt,
                finishedAt = target.finishedAt,
                lastErrorCode = target.lastErrorCode,
                lastErrorMessage = target.lastErrorMessage,
                hasIncompleteOutput = target.hasIncompleteOutput,
                updatedAt = target.updatedAt,
            )
            if (changed != 1) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "transition_execution_participant",
                        "expected_state_mismatch",
                    ),
                )
            }
            RepositoryResult.Success(
                getParticipantState(command.participantSnapshotId)
                    ?: throw IllegalStateException("Participant state update disappeared"),
            )
        }
    }

    suspend fun recordExecutionApiCall(
        command: RecordExecutionApiCallCommand,
    ): RepositoryResult<ExecutionRunBudgetEntity> {
        return transactions.transaction("record_execution_api_call") {
            require(command.rootRunId.isNotBlank())
            require(command.count > 0)
            require(command.updatedAt > 0L)
            val existing = getRunBudget(command.rootRunId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("execution_run_budget", command.rootRunId),
                )
            val changed = recordExecutionApiCall(
                rootRunId = command.rootRunId,
                count = command.count,
                updatedAt = command.updatedAt,
            )
            if (changed != 1) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "record_execution_api_call",
                        if (existing.closed) "budget_closed" else "usage_record_failed",
                    ),
                )
            }
            RepositoryResult.Success(
                getRunBudget(command.rootRunId)
                    ?: throw IllegalStateException("Budget update disappeared"),
            )
        }
    }

    suspend fun closeExecutionBudget(
        rootRunId: String,
        updatedAt: Long,
    ): RepositoryResult<ExecutionRunBudgetEntity> {
        return transactions.transaction("close_execution_budget") {
            require(rootRunId.isNotBlank() && updatedAt > 0L)
            val budget = getRunBudget(rootRunId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("execution_run_budget", rootRunId),
                )
            if (budget.closed) {
                return@transaction RepositoryResult.Success(budget, idempotent = true)
            }
            if (closeRunBudget(rootRunId, updatedAt) != 1) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "close_execution_budget",
                        "budget_close_failed",
                    ),
                )
            }
            RepositoryResult.Success(requireNotNull(getRunBudget(rootRunId)))
        }
    }

    suspend fun recoverInterruptedExecution(
        command: RecoverInterruptedExecutionCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        return transactions.transaction("recover_interrupted_execution") {
            require(command.runId.isNotBlank() && command.updatedAt > 0L)
            val run = getExecutionRun(command.runId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("execution_run", command.runId),
                )
            val states = getParticipantStates(command.runId)
            states.filter {
                it.status == ExecutionParticipantStatus.RUNNING ||
                    it.status == ExecutionParticipantStatus.STREAMING
            }.forEach { state ->
                compareAndSetParticipantState(
                    participantSnapshotId = state.participantSnapshotId,
                    runId = state.runId,
                    expectedStatuses = listOf(
                        ExecutionParticipantStatus.RUNNING.storageValue,
                        ExecutionParticipantStatus.STREAMING.storageValue,
                    ),
                    newStatus = ExecutionParticipantStatus.RETRYABLE.storageValue,
                    attemptIncrement = 0,
                    outputMessageId = state.outputMessageId,
                    startedAt = state.startedAt,
                    finishedAt = command.updatedAt,
                    lastErrorCode = "process_interrupted",
                    lastErrorMessage = "运行被系统中断，可由用户显式重试。",
                    hasIncompleteOutput = state.outputMessageId != null,
                    updatedAt = command.updatedAt,
                )
                state.outputMessageId?.let { messageId ->
                    val message = getMessage(messageId)
                    if (message?.isPending == true) {
                        compareAndSetPendingDomainMessage(
                            messageId = message.id,
                            issueId = requireNotNull(message.issueId),
                            stageId = requireNotNull(message.stageId),
                            executionRunId = message.executionRunId,
                            participantSnapshotId = message.participantSnapshotId,
                            text = message.text,
                            keepPending = false,
                        )
                    }
                }
            }

            val latestStates = getParticipantStates(command.runId)
            val targetStatus = ExecutionStateMachine.aggregate(
                latestStates.map { it.status },
                retryableParticipantIds = latestStates.indices
                    .filterTo(mutableSetOf()) { index ->
                        latestStates[index].status != ExecutionParticipantStatus.SUCCEEDED
                    },
            )
            if (
                run.status == ExecutionRunStatus.RUNNING ||
                run.status == ExecutionRunStatus.PARTIAL_SUCCESS
            ) {
                compareAndSetRunStatus(
                    runId = run.id,
                    expectedStatuses = listOf(
                        ExecutionRunStatus.RUNNING.storageValue,
                        ExecutionRunStatus.PARTIAL_SUCCESS.storageValue,
                    ),
                    newStatus = targetStatus.storageValue,
                    updatedAt = command.updatedAt,
                    startedAt = run.startedAt,
                    finishedAt = command.updatedAt,
                    stoppedAt = run.stoppedAt,
                    failureCode = "process_interrupted",
                    failureMessage = "运行被系统中断，可由用户显式重试。",
                )
            }
            loadRuntimeSnapshot(
                getExecutionRun(command.runId)
                    ?: throw IllegalStateException("Recovered run disappeared"),
            )
        }
    }

    private suspend fun JianyuRepositoryDao.loadRuntimeSnapshot(
        run: ExecutionRunEntity,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        val rootRunId = resolveRootRunId(run)
        val budget = getRunBudget(rootRunId)
            ?: return RepositoryResult.Failure(
                RepositoryError.NotFound("execution_run_budget", rootRunId),
            )
        return RepositoryResult.Success(
            ExecutionRuntimeSnapshot(
                run = run,
                participants = getParticipantSnapshots(run.id),
                participantStates = getParticipantStates(run.id),
                budget = budget,
            ),
        )
    }

    private suspend fun JianyuRepositoryDao.resolveRootRunId(
        run: ExecutionRunEntity,
    ): String {
        var current = run
        val visited = mutableSetOf<String>()
        while (current.retryOfRunId != null) {
            check(visited.add(current.id)) { "Retry chain contains a cycle" }
            current = getExecutionRun(requireNotNull(current.retryOfRunId))
                ?: throw RepositoryCompatibilityAbort("missing_retry_parent")
        }
        return current.id
    }

    private suspend fun JianyuRepositoryDao.contextUsageMatches(
        runId: String,
        requested: ContextUsageWriteSet,
    ): Boolean {
        val sorted = requested.sorted()
        return getMaterialUsagesForRun(runId) == sorted.materials &&
            getPersonalContextUsagesForRun(runId) == sorted.personalContexts
    }

    private suspend fun JianyuRepositoryDao.validateContextUsage(
        command: CreateExecutionRuntimeCommand,
    ): RepositoryError? {
        val allMaterialValid = command.contextUsage.materials.all { usage ->
            usage.runId == command.run.id &&
                usage.issueId == command.run.issueId &&
                usage.stageId == command.run.stageId &&
                usage.materialReferenceId != null &&
                usage.contentSnapshot?.isNotBlank() == true &&
                usage.contentHash == ContextContentHasher.hash(requireNotNull(usage.contentSnapshot)) &&
                usage.networkAllowed &&
                usage.userConfirmedAt > 0L
        }
        val allPersonalValid = command.contextUsage.personalContexts.all { usage ->
            usage.runId == command.run.id &&
                usage.issueId == command.run.issueId &&
                usage.stageId == command.run.stageId &&
                usage.personalContextEntryId != null &&
                usage.contentSnapshot?.isNotBlank() == true &&
                usage.contentHash == ContextContentHasher.hash(requireNotNull(usage.contentSnapshot)) &&
                usage.networkAllowed &&
                usage.userConfirmedAt > 0L
        }
        val materialKeys = command.contextUsage.materials.map { it.materialReferenceId }
        val personalKeys = command.contextUsage.personalContexts.map { it.personalContextEntryId }
        val expectedKeys = command.contextUsage.sourceExpectations.map { it.sourceType to it.sourceId }
        val expectedUnique = expectedKeys.distinct().size == expectedKeys.size
        val expectedCountMatches = command.contextUsage.sourceExpectations.size ==
            command.contextUsage.materials.size + command.contextUsage.personalContexts.size
        val snapshotPayloadValid =
            allMaterialValid && allPersonalValid &&
                materialKeys.distinct().size == materialKeys.size &&
                personalKeys.distinct().size == personalKeys.size &&
                expectedUnique && expectedCountMatches
        if (!snapshotPayloadValid) {
            return RepositoryError.ConstraintViolation(
                "create_execution_runtime",
                ContextValidationError.USAGE_SNAPSHOT_CONFLICT.code,
            )
        }

        val currentSourcesMatch = command.contextUsage.sourceExpectations.all { expectation ->
            when (expectation.sourceType) {
                ContextSourceType.MATERIAL -> getMaterialReference(expectation.sourceId)?.let { source ->
                    source.lifecycleState == ContextSourceLifecycle.ACTIVE &&
                        source.updatedAt == expectation.expectedUpdatedAt &&
                        source.contentHash == expectation.expectedContentHash
                } ?: false
                ContextSourceType.PERSONAL_CONTEXT ->
                    getPersonalContextEntry(expectation.sourceId)?.let { source ->
                        source.lifecycleState == ContextSourceLifecycle.ACTIVE &&
                            source.updatedAt == expectation.expectedUpdatedAt &&
                            source.contentHash == expectation.expectedContentHash
                    } ?: false
            }
        }
        return if (currentSourcesMatch) {
            null
        } else {
            RepositoryError.InvalidState(
                "create_execution_runtime",
                ContextValidationError.SOURCE_STALE.code,
            )
        }
    }

    private fun sameCreationPayload(
        existing: ExecutionRunEntity,
        requested: ExecutionRunEntity,
    ): Boolean = existing.id == requested.id &&
        existing.issueId == requested.issueId &&
        existing.stageId == requested.stageId &&
        existing.triggerMessageId == requested.triggerMessageId &&
        existing.idempotencyKey == requested.idempotencyKey &&
        existing.retryOfRunId == requested.retryOfRunId &&
        existing.createdAt == requested.createdAt

    private fun canTransitionParticipant(
        current: ExecutionParticipantStatus,
        target: ExecutionParticipantStatus,
    ): Boolean {
        if (current == target) return true
        return when (current) {
            ExecutionParticipantStatus.QUEUED -> target in setOf(
                ExecutionParticipantStatus.RUNNING,
                ExecutionParticipantStatus.STOPPED,
                ExecutionParticipantStatus.RETRYABLE,
                ExecutionParticipantStatus.FAILED,
            )
            ExecutionParticipantStatus.RUNNING -> target in setOf(
                ExecutionParticipantStatus.STREAMING,
                ExecutionParticipantStatus.SUCCEEDED,
                ExecutionParticipantStatus.FAILED,
                ExecutionParticipantStatus.TIMED_OUT,
                ExecutionParticipantStatus.STOPPED,
                ExecutionParticipantStatus.RETRYABLE,
            )
            ExecutionParticipantStatus.STREAMING -> target in setOf(
                ExecutionParticipantStatus.SUCCEEDED,
                ExecutionParticipantStatus.FAILED,
                ExecutionParticipantStatus.TIMED_OUT,
                ExecutionParticipantStatus.STOPPED,
                ExecutionParticipantStatus.RETRYABLE,
            )
            ExecutionParticipantStatus.FAILED,
            ExecutionParticipantStatus.TIMED_OUT,
            ExecutionParticipantStatus.STOPPED,
            ExecutionParticipantStatus.RETRYABLE,
            ExecutionParticipantStatus.SUCCEEDED -> false
        }
    }

    private fun RepositoryResult<ExecutionRuntimeSnapshot>.withIdempotentFlag():
        RepositoryResult<ExecutionRuntimeSnapshot> = when (this) {
        is RepositoryResult.Success -> copy(idempotent = true)
        is RepositoryResult.Failure -> this
    }
}
