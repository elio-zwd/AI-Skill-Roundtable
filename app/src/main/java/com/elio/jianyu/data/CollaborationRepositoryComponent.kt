package com.elio.jianyu.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class CollaborationRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
) {
    private val json = Json

    suspend fun createStandardInteraction(
        command: CreateStandardInteractionCommand,
    ): RepositoryResult<CollaborationStartResult> = transactions.collaborationTransaction(
        "create_standard_interaction",
    ) {
        validateUserMessage(command.userMessage, command.run)

        val existingRun = core.getExecutionRunByIdempotencyKey(command.run.idempotencyKey)
        if (existingRun != null) {
            val existing = loadIdempotentRuntime(
                command.run,
                command.participants,
                command.contextUsage,
            )
            val existingMessage = core.getMessage(command.userMessage.messageId)
            val usage = collaboration.getMessageUsageSnapshotsForRun(existingRun.id)
            if (
                existing != null &&
                existingMessage != null &&
                messageMatches(existingMessage, command.userMessage) &&
                usage.isEmpty()
            ) {
                return@collaborationTransaction RepositoryResult.Success(
                    CollaborationStartResult(existing),
                    idempotent = true,
                )
            }
            return@collaborationTransaction idempotencyConflict(
                "create_standard_interaction",
                command.run.idempotencyKey,
            )
        }

        validateStageAndActiveRun(command.run)?.let {
            return@collaborationTransaction RepositoryResult.Failure(it)
        }
        validateContextUsage(command.run, command.contextUsage)?.let {
            return@collaborationTransaction RepositoryResult.Failure(it)
        }

        insertUserMessage(command.userMessage)
        val runtime = insertRuntime(
            run = command.run,
            participants = command.participants,
            budgetRootRunId = command.run.id,
            budget = command.budget,
            requiredReserve = command.participants.size,
            contextUsage = command.contextUsage,
            messageUsage = emptyList(),
        )
        RepositoryResult.Success(CollaborationStartResult(runtime))
    }

    suspend fun createDirectedInteraction(
        command: CreateDirectedInteractionCommand,
    ): RepositoryResult<CollaborationStartResult> = transactions.collaborationTransaction(
        "create_directed_interaction",
    ) {
        validateUserMessage(command.userMessage, command.run)
        require(command.run.discussionId == null)
        require(command.run.parentRunId == null)
        require(command.run.retryOfRunId == null)
        require(command.run.historyScope == historyScopeFor(command.selectedMessageIds))

        loadIdempotentRuntime(command.run, listOf(command.participant), command.contextUsage)
            ?.let { existing ->
                val existingMessage = core.getMessage(command.userMessage.messageId)
                val usage = collaboration.getMessageUsageSnapshotsForRun(existing.run.id)
                if (
                    existingMessage != null &&
                    messageMatches(existingMessage, command.userMessage) &&
                    messageUsageMatches(usage, command.run, command.selectedMessageIds)
                ) {
                    return@collaborationTransaction RepositoryResult.Success(
                        CollaborationStartResult(existing, messageUsage = usage),
                        idempotent = true,
                    )
                }
                return@collaborationTransaction idempotencyConflict(
                    "create_directed_interaction",
                    command.run.idempotencyKey,
                )
            }

        validateStageAndActiveRun(command.run)?.let {
            return@collaborationTransaction RepositoryResult.Failure(it)
        }
        validateContextUsage(command.run, command.contextUsage)?.let {
            return@collaborationTransaction RepositoryResult.Failure(it)
        }

        insertUserMessage(command.userMessage)
        val messageUsage = buildMessageUsage(
            run = command.run,
            selectedMessageIds = command.selectedMessageIds,
            usedAt = command.run.createdAt,
        ) ?: return@collaborationTransaction invalidState(
            "create_directed_interaction",
            "message_usage_invalid",
        )
        val runtime = insertRuntime(
            run = command.run,
            participants = listOf(command.participant),
            budgetRootRunId = command.run.id,
            budget = command.budget,
            requiredReserve = 1,
            contextUsage = command.contextUsage,
            messageUsage = messageUsage,
        )
        RepositoryResult.Success(
            CollaborationStartResult(runtime, messageUsage = messageUsage),
        )
    }

    suspend fun createCrossDiscussionResponse(
        command: CreateCrossDiscussionResponseCommand,
    ): RepositoryResult<CollaborationStartResult> = transactions.collaborationTransaction(
        "create_cross_discussion_response",
    ) {
        validateUserMessage(command.userMessage, command.run)
        require(command.run.parentRunId == null)
        require(command.run.retryOfRunId == null)
        require(command.run.historyScope == historyScopeFor(command.selectedMessageIds))
        require(command.session.status == CrossDiscussionStatus.RESPONDING)
        require(command.session.synthesisRunId == null)
        require(command.session.integratorSkillId == "meeting-to-action")

        val existingSession = collaboration.getCrossDiscussionSessionByIdempotencyKey(
            command.session.idempotencyKey,
        )
        if (existingSession != null) {
            val existingRuntime = loadIdempotentRuntime(
                command.run,
                command.participants,
                command.contextUsage,
            )
            val existingMessage = core.getMessage(command.userMessage.messageId)
            val usage = collaboration.getMessageUsageSnapshotsForRun(command.run.id)
            if (
                existingRuntime != null &&
                existingSession == command.session &&
                existingMessage != null &&
                messageMatches(existingMessage, command.userMessage) &&
                messageUsageMatches(usage, command.run, command.selectedMessageIds)
            ) {
                return@collaborationTransaction RepositoryResult.Success(
                    CollaborationStartResult(existingRuntime, existingSession, usage),
                    idempotent = true,
                )
            }
            return@collaborationTransaction idempotencyConflict(
                "create_cross_discussion_response",
                command.session.idempotencyKey,
            )
        }

        validateStageAndActiveRun(command.run)?.let {
            return@collaborationTransaction RepositoryResult.Failure(it)
        }
        validateContextUsage(command.run, command.contextUsage)?.let {
            return@collaborationTransaction RepositoryResult.Failure(it)
        }

        insertUserMessage(command.userMessage)
        val messageUsage = buildMessageUsage(
            run = command.run,
            selectedMessageIds = command.selectedMessageIds,
            usedAt = command.run.createdAt,
        ) ?: return@collaborationTransaction invalidState(
            "create_cross_discussion_response",
            "message_usage_invalid",
        )
        val runtime = insertRuntime(
            run = command.run,
            participants = command.participants,
            budgetRootRunId = command.run.id,
            budget = command.budget,
            requiredReserve = command.participants.size + 1,
            contextUsage = command.contextUsage,
            messageUsage = messageUsage,
        )
        collaboration.insertCrossDiscussionSession(command.session)
        RepositoryResult.Success(
            CollaborationStartResult(runtime, command.session, messageUsage),
        )
    }

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

        core.getExecutionRunByIdempotencyKey(command.run.idempotencyKey)?.let { existingRun ->
            val existingRuntime = loadRuntime(existingRun, session.responseRunId)
            val usage = collaboration.getMessageUsageSnapshotsForRun(existingRun.id)
            if (
                sameRunCreationPayload(existingRun, command.run) &&
                core.getParticipantSnapshots(existingRun.id) == listOf(command.participant) &&
                contextUsageMatches(existingRun.id, command.contextUsage) &&
                session.synthesisRunId == existingRun.id
            ) {
                return@collaborationTransaction RepositoryResult.Success(
                    CollaborationStartResult(existingRuntime, session, usage),
                    idempotent = true,
                )
            }
            return@collaborationTransaction idempotencyConflict(
                "create_cross_discussion_synthesis",
                command.run.idempotencyKey,
            )
        }

        if (session.status !in setOf(
                CrossDiscussionStatus.AWAITING_SYNTHESIS,
                CrossDiscussionStatus.PARTIAL_SUCCESS,
                CrossDiscussionStatus.SYNTHESIS_RETRYABLE,
            )
        ) {
            return@collaborationTransaction invalidState(
                "create_cross_discussion_synthesis",
                "discussion_not_ready_for_synthesis",
            )
        }
        if (session.status == CrossDiscussionStatus.PARTIAL_SUCCESS && !command.userAcceptedPartial) {
            return@collaborationTransaction invalidState(
                "create_cross_discussion_synthesis",
                "partial_synthesis_confirmation_required",
            )
        }
        validateContextUsage(command.run, command.contextUsage)?.let {
            return@collaborationTransaction RepositoryResult.Failure(it)
        }

        val responseParticipants = core.getParticipantSnapshots(session.responseRunId)
        val responseStates = core.getParticipantStates(session.responseRunId)
            .associateBy { it.participantSnapshotId }
        val successfulParticipants = responseParticipants.filter { participant ->
            responseStates[participant.id]?.status == ExecutionParticipantStatus.SUCCEEDED
        }
        val failedParticipants = responseParticipants.filterNot { it in successfulParticipants }
        if (successfulParticipants.isEmpty()) {
            return@collaborationTransaction invalidState(
                "create_cross_discussion_synthesis",
                "no_successful_response",
            )
        }
        if (failedParticipants.isNotEmpty() && !command.userAcceptedPartial) {
            return@collaborationTransaction invalidState(
                "create_cross_discussion_synthesis",
                "partial_synthesis_confirmation_required",
            )
        }

        val sourceMessageIds = successfulParticipants.map { participant ->
            responseStates.getValue(participant.id).outputMessageId
                ?: return@collaborationTransaction invalidState(
                    "create_cross_discussion_synthesis",
                    "successful_participant_missing_output",
                )
        }
        val messageUsage = buildMessageUsage(
            run = command.run,
            selectedMessageIds = sourceMessageIds,
            usedAt = command.createdAt,
        ) ?: return@collaborationTransaction invalidState(
            "create_cross_discussion_synthesis",
            "response_message_usage_invalid",
        )

        val rootBudget = core.getRunBudget(session.responseRunId)
            ?: return@collaborationTransaction invalidState(
                "create_cross_discussion_synthesis",
                "response_budget_missing",
            )
        if (rootBudget.closed) {
            return@collaborationTransaction invalidState(
                "create_cross_discussion_synthesis",
                "response_budget_closed",
            )
        }
        val runtime = insertRuntime(
            run = command.run,
            participants = listOf(command.participant),
            budgetRootRunId = session.responseRunId,
            budget = ExecutionRuntimeBudgetConfig(
                maxApiCalls = rootBudget.maxApiCalls,
                maxCharacters = rootBudget.maxCharacters,
                maxSearchQueriesPerCharacter = rootBudget.maxSearchQueriesPerCharacter,
                maxOutputTokensPerAnswer = rootBudget.maxOutputTokensPerAnswer,
            ),
            requiredReserve = 1,
            contextUsage = command.contextUsage,
            messageUsage = messageUsage,
        )
        val successfulIds = successfulParticipants.map { it.id }
        val failedIds = failedParticipants.map { it.id }
        if (
            collaboration.compareAndSetCrossDiscussionSession(
                sessionId = session.id,
                expectedStatuses = listOf(session.status.storageValue),
                synthesisRunId = command.run.id,
                newStatus = CrossDiscussionStatus.SYNTHESIZING.storageValue,
                successfulParticipantIdsJson = json.encodeToString(successfulIds),
                failedParticipantIdsJson = json.encodeToString(failedIds),
                partialSynthesisConfirmedAt = if (failedIds.isEmpty()) {
                    session.partialSynthesisConfirmedAt
                } else {
                    command.createdAt
                },
                updatedAt = command.createdAt,
                failureCode = null,
            ) != 1
        ) {
            return@collaborationTransaction invalidState(
                "create_cross_discussion_synthesis",
                "discussion_state_changed",
            )
        }
        val updatedSession = requireNotNull(collaboration.getCrossDiscussionSession(session.id))
        RepositoryResult.Success(
            CollaborationStartResult(runtime, updatedSession, messageUsage),
        )
    }

    suspend fun transitionCrossDiscussion(
        command: TransitionCrossDiscussionCommand,
    ): RepositoryResult<CrossDiscussionSessionEntity> = transactions.collaborationTransaction(
        "transition_cross_discussion",
    ) {
        val current = collaboration.getCrossDiscussionSession(command.sessionId)
            ?: return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.NotFound("cross_discussion_session", command.sessionId),
            )
        val target = current.copy(
            synthesisRunId = command.synthesisRunId ?: current.synthesisRunId,
            status = command.newStatus,
            successfulParticipantIdsJson = json.encodeToString(command.successfulParticipantIds),
            failedParticipantIdsJson = json.encodeToString(command.failedParticipantIds),
            partialSynthesisConfirmedAt = command.partialSynthesisConfirmedAt
                ?: current.partialSynthesisConfirmedAt,
            updatedAt = command.updatedAt,
            failureCode = command.failureCode,
        )
        if (target == current) {
            return@collaborationTransaction RepositoryResult.Success(current, idempotent = true)
        }
        if (
            collaboration.compareAndSetCrossDiscussionSession(
                sessionId = command.sessionId,
                expectedStatuses = command.expectedStatuses.map { it.storageValue },
                synthesisRunId = target.synthesisRunId,
                newStatus = target.status.storageValue,
                successfulParticipantIdsJson = target.successfulParticipantIdsJson,
                failedParticipantIdsJson = target.failedParticipantIdsJson,
                partialSynthesisConfirmedAt = target.partialSynthesisConfirmedAt,
                updatedAt = target.updatedAt,
                failureCode = target.failureCode,
            ) != 1
        ) {
            return@collaborationTransaction invalidState(
                "transition_cross_discussion",
                "expected_state_mismatch",
            )
        }
        RepositoryResult.Success(requireNotNull(collaboration.getCrossDiscussionSession(command.sessionId)))
    }

    suspend fun getStageCollaboration(
        stageId: String,
    ): RepositoryResult<StageCollaborationSnapshot> = transactions.collaborationTransaction(
        "get_stage_collaboration",
    ) {
        if (core.getStage(stageId) == null) {
            return@collaborationTransaction RepositoryResult.Failure(
                RepositoryError.NotFound("stage", stageId),
            )
        }
        val discussions = collaboration.getCrossDiscussionSessionsForStage(stageId)
        val runIds = discussions.flatMap { session ->
            listOfNotNull(session.responseRunId, session.synthesisRunId)
        }.distinct()
        RepositoryResult.Success(
            StageCollaborationSnapshot(
                discussions = discussions,
                messageUsageByRun = runIds.associateWith {
                    collaboration.getMessageUsageSnapshotsForRun(it)
                },
            ),
        )
    }

    suspend fun listExecutionMessageUsage(
        runId: String,
    ): RepositoryResult<List<ExecutionMessageUsageSnapshotEntity>> =
        transactions.collaborationTransaction("list_execution_message_usage") {
            if (core.getExecutionRun(runId) == null) {
                return@collaborationTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("execution_run", runId),
                )
            }
            RepositoryResult.Success(collaboration.getMessageUsageSnapshotsForRun(runId))
        }

    private suspend fun CollaborationTransactionScope.loadIdempotentRuntime(
        requestedRun: ExecutionRunEntity,
        requestedParticipants: List<ExecutionParticipantSnapshotEntity>,
        contextUsage: ContextUsageWriteSet,
    ): ExecutionRuntimeSnapshot? {
        val existing = core.getExecutionRunByIdempotencyKey(requestedRun.idempotencyKey)
            ?: return null
        return if (
            sameRunCreationPayload(existing, requestedRun) &&
            core.getParticipantSnapshots(existing.id) == requestedParticipants.sortedBy { it.position } &&
            contextUsageMatches(existing.id, contextUsage)
        ) {
            loadRuntime(existing, resolveBudgetRoot(existing))
        } else {
            null
        }
    }

    private suspend fun CollaborationTransactionScope.validateStageAndActiveRun(
        run: ExecutionRunEntity,
    ): RepositoryError? {
        val stage = core.getStage(run.stageId)
        if (stage == null || stage.issueId != run.issueId) {
            return RepositoryError.NotFound("stage", run.stageId)
        }
        val active = core.getExecutionRunsForIssue(run.issueId).firstOrNull { existing ->
            existing.stageId == run.stageId &&
                existing.status in setOf(
                    ExecutionRunStatus.NOT_STARTED,
                    ExecutionRunStatus.RUNNING,
                    ExecutionRunStatus.PARTIAL_SUCCESS,
                )
        }
        return active?.let {
            RepositoryError.InvalidState("create_collaboration_runtime", "stage_has_active_run")
        }
    }

    private suspend fun CollaborationTransactionScope.insertUserMessage(
        command: AppendDomainMessageCommand,
    ) {
        val issue = requireNotNull(core.getIssue(command.issueId))
        val existing = core.getMessage(command.messageId)
        if (existing != null) {
            check(messageMatches(existing, command))
            return
        }
        val sessionId = ensureCompatibilitySession(issue, command)
        core.insertDomainMessage(
            Message(
                id = command.messageId,
                chatId = sessionId,
                senderId = command.senderId,
                senderName = command.senderName,
                avatar = command.avatar,
                text = command.text,
                timestamp = command.timestamp,
                isPending = false,
                roundIndex = command.roundIndex,
                issueId = command.issueId,
                stageId = command.stageId,
                executionRunId = null,
                participantSnapshotId = null,
            ),
        )
    }

    private suspend fun CollaborationTransactionScope.ensureCompatibilitySession(
        issue: IssueEntity,
        command: AppendDomainMessageCommand,
    ): Long {
        issue.legacyChatSessionId?.let { sessionId ->
            if (core.getCompatibilitySession(sessionId) == null) {
                throw RepositoryCompatibilityAbort("missing_legacy_chat_session")
            }
            return sessionId
        }
        val sessionId = core.insertCompatibilitySession(
            ChatSession(
                title = command.compatibilitySessionTitle.ifBlank { issue.title },
                createdAt = command.timestamp,
            ),
        )
        check(
            core.updateIssue(
                issue.copy(
                    updatedAt = maxOf(issue.updatedAt, command.timestamp),
                    legacyChatSessionId = sessionId,
                ),
            ) == 1,
        )
        return sessionId
    }

    private suspend fun CollaborationTransactionScope.buildMessageUsage(
        run: ExecutionRunEntity,
        selectedMessageIds: List<Long>,
        usedAt: Long,
    ): List<ExecutionMessageUsageSnapshotEntity>? {
        if (selectedMessageIds.isEmpty()) return emptyList()
        return selectedMessageIds.mapIndexed { index, messageId ->
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
    }

    private suspend fun CollaborationTransactionScope.insertRuntime(
        run: ExecutionRunEntity,
        participants: List<ExecutionParticipantSnapshotEntity>,
        budgetRootRunId: String,
        budget: ExecutionRuntimeBudgetConfig,
        requiredReserve: Int,
        contextUsage: ContextUsageWriteSet,
        messageUsage: List<ExecutionMessageUsageSnapshotEntity>,
    ): ExecutionRuntimeSnapshot {
        require(participants.isNotEmpty())
        require(validateParticipantPayload(participants))
        core.insertExecutionRun(run)
        val sortedParticipants = participants.sortedBy { it.position }
        core.insertParticipantSnapshots(sortedParticipants)
        core.insertParticipantStates(
            sortedParticipants.map { participant ->
                ExecutionParticipantStateEntity(
                    participantSnapshotId = participant.id,
                    runId = run.id,
                    updatedAt = run.createdAt,
                )
            },
        )
        if (budgetRootRunId == run.id) {
            core.insertRunBudget(
                ExecutionRunBudgetEntity(
                    rootRunId = run.id,
                    maxApiCalls = budget.maxApiCalls,
                    reservedRequiredCalls = requiredReserve,
                    maxCharacters = budget.maxCharacters,
                    maxSearchQueriesPerCharacter = budget.maxSearchQueriesPerCharacter,
                    maxOutputTokensPerAnswer = budget.maxOutputTokensPerAnswer,
                    updatedAt = run.createdAt,
                ),
            )
        } else {
            val root = core.getRunBudget(budgetRootRunId)
            require(root != null && !root.closed)
            check(
                core.setRequiredBudgetReserve(
                    budgetRootRunId,
                    requiredReserve,
                    run.createdAt,
                ) == 1,
            )
        }
        val sortedUsage = contextUsage.sorted()
        if (sortedUsage.materials.isNotEmpty()) core.insertMaterialUsages(sortedUsage.materials)
        if (sortedUsage.personalContexts.isNotEmpty()) {
            core.insertPersonalContextUsages(sortedUsage.personalContexts)
        }
        if (messageUsage.isNotEmpty()) collaboration.insertMessageUsageSnapshots(messageUsage)
        return loadRuntime(run, budgetRootRunId)
    }

    private suspend fun CollaborationTransactionScope.loadRuntime(
        run: ExecutionRunEntity,
        budgetRootRunId: String,
    ): ExecutionRuntimeSnapshot = ExecutionRuntimeSnapshot(
        run = run,
        participants = core.getParticipantSnapshots(run.id),
        participantStates = core.getParticipantStates(run.id),
        budget = requireNotNull(core.getRunBudget(budgetRootRunId)),
    )

    private suspend fun CollaborationTransactionScope.resolveBudgetRoot(
        run: ExecutionRunEntity,
    ): String {
        var current = run
        val visited = mutableSetOf<String>()
        while (true) {
            check(visited.add(current.id))
            val parentId = current.retryOfRunId ?: current.parentRunId ?: return current.id
            current = core.getExecutionRun(parentId)
                ?: throw RepositoryCompatibilityAbort("missing_execution_parent")
        }
    }

    private suspend fun CollaborationTransactionScope.contextUsageMatches(
        runId: String,
        requested: ContextUsageWriteSet,
    ): Boolean {
        val sorted = requested.sorted()
        return core.getMaterialUsagesForRun(runId) == sorted.materials &&
            core.getPersonalContextUsagesForRun(runId) == sorted.personalContexts
    }

    private fun validateContextUsage(
        run: ExecutionRunEntity,
        usage: ContextUsageWriteSet,
    ): RepositoryError? {
        val materialValid = usage.materials.all { item ->
            item.runId == run.id &&
                item.issueId == run.issueId &&
                item.stageId == run.stageId &&
                item.contentSnapshot?.isNotBlank() == true &&
                item.contentHash == ContextContentHasher.hash(requireNotNull(item.contentSnapshot)) &&
                item.networkAllowed &&
                item.userConfirmedAt > 0L
        }
        val personalValid = usage.personalContexts.all { item ->
            item.runId == run.id &&
                item.issueId == run.issueId &&
                item.stageId == run.stageId &&
                item.contentSnapshot?.isNotBlank() == true &&
                item.contentHash == ContextContentHasher.hash(requireNotNull(item.contentSnapshot)) &&
                item.networkAllowed &&
                item.userConfirmedAt > 0L
        }
        return if (materialValid && personalValid) null else {
            RepositoryError.ConstraintViolation(
                "create_collaboration_runtime",
                "context_usage_invalid",
            )
        }
    }

    private fun validateUserMessage(
        command: AppendDomainMessageCommand,
        run: ExecutionRunEntity,
    ) {
        require(command.messageId > 0L)
        require(command.issueId == run.issueId)
        require(command.stageId == run.stageId)
        require(command.executionRunId == null)
        require(command.participantSnapshotId == null)
        require(command.senderId == "user")
        require(!command.isPending)
        require(command.text.isNotBlank())
    }

    private fun historyScopeFor(selectedMessageIds: List<Long>): ExecutionHistoryScope =
        if (selectedMessageIds.isEmpty()) ExecutionHistoryScope.NO_HISTORY
        else ExecutionHistoryScope.EXPLICIT_MESSAGES

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

    private fun messageMatches(
        existing: Message,
        command: AppendDomainMessageCommand,
    ): Boolean = existing.id == command.messageId &&
        existing.issueId == command.issueId &&
        existing.stageId == command.stageId &&
        existing.executionRunId == null &&
        existing.participantSnapshotId == null &&
        existing.senderId == command.senderId &&
        existing.senderName == command.senderName &&
        existing.avatar == command.avatar &&
        existing.text == command.text &&
        existing.timestamp == command.timestamp &&
        !existing.isPending &&
        existing.roundIndex == command.roundIndex

    private fun messageUsageMatches(
        existing: List<ExecutionMessageUsageSnapshotEntity>,
        run: ExecutionRunEntity,
        requestedMessageIds: List<Long>,
    ): Boolean = existing.map { it.sourceMessageId } == requestedMessageIds &&
        existing.all { it.runId == run.id }

    private fun <T> idempotencyConflict(operation: String, key: String): RepositoryResult<T> =
        RepositoryResult.Failure(RepositoryError.IdempotencyConflict(operation, key))

    private fun <T> invalidState(operation: String, code: String): RepositoryResult<T> =
        RepositoryResult.Failure(RepositoryError.InvalidState(operation, code))
}
