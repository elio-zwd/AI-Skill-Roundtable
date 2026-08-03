package com.elio.jianyu.data

internal class IssueExecutionRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions
) {
    suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<SavedIssue> {
        return transactions.transaction("save_issue") {
            require(command.issueId.isNotBlank() && command.initialStageId.isNotBlank())
            require(command.title.isNotBlank() && command.createdAt > 0L)

            val requestedIssue = IssueEntity(
                id = command.issueId,
                title = command.title,
                createdAt = command.createdAt,
                updatedAt = command.createdAt,
                legacyChatSessionId = null
            )
            val requestedStage = StageEntity(
                id = command.initialStageId,
                issueId = command.issueId,
                sequenceIndex = 0,
                title = command.initialStageTitle,
                objective = command.initialObjective,
                createdAt = command.createdAt,
                updatedAt = command.createdAt
            )
            val requestedLifecycle = IssueLifecycleEntity(
                issueId = command.issueId,
                state = IssueLifecycleState.ACTIVE,
                stateChangedAt = command.createdAt,
                updatedAt = command.createdAt
            )

            val existingIssue = getIssue(command.issueId)
            if (existingIssue != null) {
                val existingStage = getStage(command.initialStageId)
                val existingLifecycle = getIssueLifecycle(command.issueId)
                    ?: return@transaction RepositoryResult.Failure(
                        RepositoryError.CompatibilityFailure(
                            "save_issue",
                            "missing_issue_lifecycle"
                        )
                    )
                val sameIssuePayload = existingIssue.id == requestedIssue.id &&
                    existingIssue.title == requestedIssue.title &&
                    existingIssue.createdAt == requestedIssue.createdAt
                val sameStagePayload = existingStage?.id == requestedStage.id &&
                    existingStage.issueId == requestedStage.issueId &&
                    existingStage.sequenceIndex == 0 &&
                    existingStage.title == requestedStage.title &&
                    existingStage.objective == requestedStage.objective &&
                    existingStage.createdAt == requestedStage.createdAt
                if (sameIssuePayload && sameStagePayload) {
                    return@transaction RepositoryResult.Success(
                        SavedIssue(existingIssue, requireNotNull(existingStage), existingLifecycle),
                        idempotent = true
                    )
                }
                return@transaction RepositoryResult.Failure(
                    RepositoryError.IdempotencyConflict("save_issue", command.issueId)
                )
            }
            if (getStage(command.initialStageId) != null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.AlreadyExists("stage", command.initialStageId)
                )
            }

            insertIssue(requestedIssue)
            insertStage(requestedStage)
            insertIssueLifecycle(requestedLifecycle)
            RepositoryResult.Success(
                SavedIssue(requestedIssue, requestedStage, requestedLifecycle)
            )
        }
    }

    suspend fun createStage(command: CreateStageCommand): RepositoryResult<StageEntity> {
        return transactions.transaction("create_stage") {
            require(command.stageId.isNotBlank() && command.issueId.isNotBlank())
            require(command.title.isNotBlank() && command.createdAt > 0L)

            val existing = getStage(command.stageId)
            if (existing != null) {
                val samePayload = existing.issueId == command.issueId &&
                    existing.title == command.title &&
                    existing.objective == command.objective &&
                    existing.createdAt == command.createdAt
                return@transaction if (samePayload) {
                    RepositoryResult.Success(existing, idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict("create_stage", command.stageId)
                    )
                }
            }
            if (getIssue(command.issueId) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", command.issueId)
                )
            }

            val stage = StageEntity(
                id = command.stageId,
                issueId = command.issueId,
                sequenceIndex = (getMaxStageSequence(command.issueId) ?: -1) + 1,
                title = command.title,
                objective = command.objective,
                createdAt = command.createdAt,
                updatedAt = command.createdAt
            )
            insertStage(stage)
            RepositoryResult.Success(stage)
        }
    }

    suspend fun undoLatestUnrunStage(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> {
        return transactions.transaction("undo_latest_stage") {
            val stage = getStage(stageId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", stageId)
                )
            if (stage.issueId != issueId || stage.sequenceIndex == 0) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("undo_latest_stage", "not_undoable")
                )
            }
            if (getStagesForIssue(issueId).lastOrNull()?.id != stageId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("undo_latest_stage", "not_latest")
                )
            }

            val dependencyCount = countRunsForStage(issueId, stageId) +
                countMessagesForStage(issueId, stageId) +
                countDraftsForStage(issueId, stageId) +
                countDraftRevisionsForStage(issueId, stageId) +
                countArtifactsForStage(issueId, stageId) +
                countMaterialReferencesForStage(issueId, stageId) +
                countMaterialUsagesForStage(issueId, stageId) +
                countPersonalContextUsagesForStage(issueId, stageId) +
                countAudioAssetsForStage(issueId, stageId)
            if (dependencyCount != 0) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "undo_latest_stage",
                        "stage_has_dependencies"
                    )
                )
            }
            if (deleteStage(issueId, stageId) != 1) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.StorageFailure("undo_latest_stage", retryable = true)
                )
            }
            RepositoryResult.Success(Unit)
        }
    }

    suspend fun createExecutionRun(
        command: CreateExecutionRunCommand
    ): RepositoryResult<ExecutionRunSnapshot> {
        return transactions.transaction("create_execution_run") {
            require(command.run.id.isNotBlank() && command.run.idempotencyKey.isNotBlank())
            require(command.run.status == ExecutionRunStatus.NOT_STARTED)
            require(validateParticipantPayload(command.participants))
            require(command.participants.all { it.runId == command.run.id })

            val existing = getExecutionRunByIdempotencyKey(command.run.idempotencyKey)
            if (existing != null) {
                val storedParticipants = getParticipantSnapshots(existing.id)
                val requestedParticipants = command.participants.sortedBy { it.position }
                return@transaction if (
                    sameRunCreationPayload(existing, command.run) &&
                    storedParticipants == requestedParticipants
                ) {
                    RepositoryResult.Success(
                        ExecutionRunSnapshot(existing, storedParticipants),
                        idempotent = true
                    )
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict(
                            "create_execution_run",
                            command.run.idempotencyKey
                        )
                    )
                }
            }

            val stage = getStage(command.run.stageId)
            if (stage == null || stage.issueId != command.run.issueId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", command.run.stageId)
                )
            }
            insertExecutionRun(command.run)
            val participants = command.participants.sortedBy { it.position }
            if (participants.isNotEmpty()) insertParticipantSnapshots(participants)
            RepositoryResult.Success(ExecutionRunSnapshot(command.run, participants))
        }
    }

    suspend fun appendDomainMessage(
        command: AppendDomainMessageCommand
    ): RepositoryResult<Message> {
        return transactions.transaction("append_domain_message") {
            require(command.messageId > 0L && command.roundIndex >= 0)
            require(command.issueId.isNotBlank() && command.stageId.isNotBlank())
            require(command.senderId.isNotBlank() && command.timestamp > 0L)

            val existing = getMessage(command.messageId)
            if (existing != null) {
                return@transaction if (messageMatches(existing, command)) {
                    RepositoryResult.Success(existing, idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict(
                            "append_domain_message",
                            command.messageId.toString()
                        )
                    )
                }
            }

            val issue = getIssue(command.issueId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", command.issueId)
                )
            val stage = getStage(command.stageId)
            if (stage == null || stage.issueId != command.issueId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", command.stageId)
                )
            }
            val relationError = validateMessageRelations(command)
            if (relationError != null) {
                return@transaction RepositoryResult.Failure(relationError)
            }

            val sessionId = ensureCompatibilitySession(issue, command)
            val message = Message(
                id = command.messageId,
                chatId = sessionId,
                senderId = command.senderId,
                senderName = command.senderName,
                avatar = command.avatar,
                text = command.text,
                timestamp = command.timestamp,
                isPending = command.isPending,
                roundIndex = command.roundIndex,
                issueId = command.issueId,
                stageId = command.stageId,
                executionRunId = command.executionRunId,
                participantSnapshotId = command.participantSnapshotId
            )
            insertDomainMessage(message)
            RepositoryResult.Success(message)
        }
    }

    suspend fun transitionRun(
        command: TransitionRunCommand
    ): RepositoryResult<ExecutionRunEntity> {
        return transactions.transaction("transition_run") {
            require(command.expectedStatuses.isNotEmpty() && command.updatedAt > 0L)
            val existing = getExecutionRun(command.runId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("execution_run", command.runId)
                )
            val target = existing.copy(
                status = command.newStatus,
                updatedAt = command.updatedAt,
                startedAt = command.startedAt ?: existing.startedAt,
                finishedAt = command.finishedAt ?: existing.finishedAt,
                stoppedAt = command.stoppedAt ?: existing.stoppedAt,
                failureCode = command.failureCode,
                failureMessage = command.failureMessage
            )
            if (existing == target) {
                return@transaction RepositoryResult.Success(existing, idempotent = true)
            }

            val changed = compareAndSetRunStatus(
                runId = command.runId,
                expectedStatuses = command.expectedStatuses.map { it.storageValue },
                newStatus = command.newStatus.storageValue,
                updatedAt = target.updatedAt,
                startedAt = target.startedAt,
                finishedAt = target.finishedAt,
                stoppedAt = target.stoppedAt,
                failureCode = target.failureCode,
                failureMessage = target.failureMessage
            )
            if (changed != 1) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "transition_run",
                        "expected_state_mismatch"
                    )
                )
            }
            RepositoryResult.Success(
                getExecutionRun(command.runId)
                    ?: throw IllegalStateException("Run update disappeared")
            )
        }
    }

    private suspend fun JianyuRepositoryDao.validateMessageRelations(
        command: AppendDomainMessageCommand
    ): RepositoryError? {
        if (command.executionRunId == null) {
            return if (command.participantSnapshotId != null || command.senderId != "user") {
                RepositoryError.InvalidState(
                    "append_domain_message",
                    "user_message_relation_invalid"
                )
            } else {
                null
            }
        }

        val run = getExecutionRun(command.executionRunId)
        if (
            run == null || run.issueId != command.issueId ||
            run.stageId != command.stageId
        ) {
            return RepositoryError.InvalidState(
                "append_domain_message",
                "run_relation_invalid"
            )
        }
        if (command.participantSnapshotId != null) {
            val participant = getParticipantSnapshots(run.id)
                .firstOrNull { it.id == command.participantSnapshotId }
            if (participant == null || participant.sourceId != command.senderId) {
                return RepositoryError.InvalidState(
                    "append_domain_message",
                    "participant_relation_invalid"
                )
            }
        } else if (command.senderId != "user") {
            return RepositoryError.InvalidState(
                "append_domain_message",
                "participant_required"
            )
        }
        return null
    }

    private suspend fun JianyuRepositoryDao.ensureCompatibilitySession(
        issue: IssueEntity,
        command: AppendDomainMessageCommand
    ): Long {
        val existingId = issue.legacyChatSessionId
        if (existingId != null) {
            if (getCompatibilitySession(existingId) == null) {
                throw RepositoryCompatibilityAbort("missing_legacy_chat_session")
            }
            return existingId
        }
        val sessionId = insertCompatibilitySession(
            ChatSession(
                title = command.compatibilitySessionTitle.ifBlank { issue.title },
                createdAt = command.timestamp
            )
        )
        if (
            updateIssue(
                issue.copy(
                    updatedAt = maxOf(issue.updatedAt, command.timestamp),
                    legacyChatSessionId = sessionId
                )
            ) != 1
        ) {
            throw IllegalStateException("Issue compatibility update failed")
        }
        return sessionId
    }

    private fun sameRunCreationPayload(
        existing: ExecutionRunEntity,
        requested: ExecutionRunEntity
    ): Boolean {
        return existing.id == requested.id &&
            existing.issueId == requested.issueId &&
            existing.stageId == requested.stageId &&
            existing.triggerMessageId == requested.triggerMessageId &&
            existing.idempotencyKey == requested.idempotencyKey &&
            existing.retryOfRunId == requested.retryOfRunId &&
            existing.createdAt == requested.createdAt
    }

    private fun messageMatches(
        existing: Message,
        command: AppendDomainMessageCommand
    ): Boolean {
        return existing.id == command.messageId &&
            existing.issueId == command.issueId &&
            existing.stageId == command.stageId &&
            existing.executionRunId == command.executionRunId &&
            existing.participantSnapshotId == command.participantSnapshotId &&
            existing.senderId == command.senderId &&
            existing.senderName == command.senderName &&
            existing.avatar == command.avatar &&
            existing.text == command.text &&
            existing.timestamp == command.timestamp &&
            existing.isPending == command.isPending &&
            existing.roundIndex == command.roundIndex
    }
}
