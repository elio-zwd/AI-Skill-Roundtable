package com.elio.jianyu.data

import com.elio.jianyu.lifecycle.ResumeChangeNotePolicy

class RoomIssueLifecycleV12Repository(
    database: RoundtableDatabase,
) : IssueLifecycleV12Repository {
    private val transactions = JianyuRepositoryTransactions(database)

    override suspend fun archiveIssueWithEvent(
        command: ArchiveIssueWithEventCommand,
    ): RepositoryResult<ArchivedIssueResult> {
        val normalized = try {
            IssueLifecycleV12CommandPolicy.normalize(command)
        } catch (_: IllegalArgumentException) {
            return invalid("archive_issue", "archive_payload_invalid")
        }
        val payloadHash = IssueLifecycleV12PayloadHasher.hash(normalized)
        return transactions.databaseTransaction("archive_issue") {
            val core = jianyuRepositoryDao()
            val lifecycleEvents = issueLifecycleV12Dao()
            val existing = lifecycleEvents.getArchiveEventByOperation(normalized.operationId)
            if (existing != null) {
                if (existing.payloadHash != payloadHash) {
                    return@databaseTransaction conflict("archive_issue", normalized.operationId)
                }
                val lifecycle = core.getIssueLifecycle(existing.issueId)
                    ?: return@databaseTransaction compatibility("archive_issue", "missing_issue_lifecycle")
                return@databaseTransaction RepositoryResult.Success(
                    ArchivedIssueResult(lifecycle, existing),
                    idempotent = true,
                )
            }

            if (core.getIssue(normalized.issueId) == null) {
                return@databaseTransaction notFound("issue", normalized.issueId)
            }
            val lifecycle = core.getIssueLifecycle(normalized.issueId)
                ?: return@databaseTransaction compatibility("archive_issue", "missing_issue_lifecycle")
            if (lifecycle.state != IssueLifecycleState.ACTIVE || lifecycle.purgeRequestedAt != null) {
                return@databaseTransaction invalid("archive_issue", "archive_state_changed")
            }
            if (lifecycleEvents.getPurgeOperationForIssue(normalized.issueId) != null) {
                return@databaseTransaction invalid("archive_issue", "purge_operation_exists")
            }

            val stages = core.getStagesForIssue(normalized.issueId)
            val runs = core.getExecutionRunsForIssue(normalized.issueId)
            val messages = core.getMessagesForIssue(normalized.issueId)
            val drafts = core.getDraftsForIssue(normalized.issueId)
            val artifacts = core.getArtifactsForIssue(normalized.issueId)
            val audioAssets = core.getAudioAssetsForIssue(normalized.issueId)
            val activeRun = runs.any { it.status == ExecutionRunStatus.NOT_STARTED || it.status == ExecutionRunStatus.RUNNING }
            val activeCross = stages.asSequence()
                .flatMap { collaborationDao().getCrossDiscussionSessionsForStage(it.id).asSequence() }
                .any { it.status == CrossDiscussionStatus.RESPONDING || it.status == CrossDiscussionStatus.SYNTHESIZING }
            val pendingMessage = messages.any(Message::isPending)
            val pendingAudio = audioAssets.any {
                it.fileState == AudioFileState.PENDING && it.purgeRequestedAt == null && it.deletedAt == null
            }
            if (activeRun || activeCross || pendingMessage || pendingAudio) {
                return@databaseTransaction invalid("archive_issue", "archive_active_work")
            }

            val currentStageId = stages.lastOrNull()?.id
            val snapshotMatches = normalized.currentStageIdSnapshot == currentStageId &&
                normalized.stageCountSnapshot == stages.size &&
                normalized.runCountSnapshot == runs.size &&
                normalized.draftCountSnapshot == drafts.size &&
                normalized.artifactCountSnapshot == artifacts.size &&
                normalized.audioAssetCountSnapshot == audioAssets.size
            if (!snapshotMatches) {
                return@databaseTransaction invalid("archive_issue", "archive_state_changed")
            }

            val event = IssueArchiveEventEntity(
                id = normalized.eventId,
                issueId = normalized.issueId,
                archiveOperationId = normalized.operationId,
                payloadHash = payloadHash,
                summaryMarkdown = normalized.summaryMarkdown,
                currentStageIdSnapshot = normalized.currentStageIdSnapshot,
                stageCountSnapshot = normalized.stageCountSnapshot,
                runCountSnapshot = normalized.runCountSnapshot,
                draftCountSnapshot = normalized.draftCountSnapshot,
                artifactCountSnapshot = normalized.artifactCountSnapshot,
                audioAssetCountSnapshot = normalized.audioAssetCountSnapshot,
                archivedAt = normalized.archivedAt,
                createdAt = normalized.archivedAt,
            )
            val archivedLifecycle = resolveLifecycleTransition(
                lifecycle,
                LifecycleAction.ARCHIVE,
                normalized.archivedAt,
            )
            lifecycleEvents.insertArchiveEvent(event)
            if (core.updateIssueLifecycle(archivedLifecycle) != 1) {
                throw IllegalStateException("Archive lifecycle update failed")
            }
            RepositoryResult.Success(ArchivedIssueResult(archivedLifecycle, event))
        }
    }

    override suspend fun resumeArchivedIssue(
        command: ResumeArchivedIssueCommand,
    ): RepositoryResult<ResumedIssueResult> {
        val normalized = try {
            IssueLifecycleV12CommandPolicy.normalize(command)
        } catch (_: IllegalArgumentException) {
            return invalid("resume_issue", "resume_note_required")
        }
        val payloadHash = IssueLifecycleV12PayloadHasher.hash(normalized)
        return transactions.databaseTransaction("resume_issue") {
            val core = jianyuRepositoryDao()
            val lifecycleEvents = issueLifecycleV12Dao()
            val existing = lifecycleEvents.getResumeEventByOperation(normalized.operationId)
            if (existing != null) {
                if (existing.payloadHash != payloadHash) {
                    return@databaseTransaction conflict("resume_issue", normalized.operationId)
                }
                val lifecycle = core.getIssueLifecycle(existing.issueId)
                    ?: return@databaseTransaction compatibility("resume_issue", "missing_issue_lifecycle")
                return@databaseTransaction RepositoryResult.Success(
                    ResumedIssueResult(lifecycle, existing),
                    idempotent = true,
                )
            }

            val lifecycle = core.getIssueLifecycle(normalized.issueId)
                ?: return@databaseTransaction notFound("issue", normalized.issueId)
            if (lifecycle.state != IssueLifecycleState.ARCHIVED || lifecycle.purgeRequestedAt != null) {
                return@databaseTransaction invalid("resume_issue", "resume_state_changed")
            }
            val archive = lifecycleEvents.getArchiveEvent(normalized.archiveEventId)
                ?: return@databaseTransaction notFound("archive_event", normalized.archiveEventId)
            if (archive.issueId != normalized.issueId) {
                return@databaseTransaction invalid("resume_issue", "archive_event_issue_mismatch")
            }

            val event = IssueResumeEventEntity(
                id = normalized.eventId,
                issueId = normalized.issueId,
                archiveEventId = normalized.archiveEventId,
                resumeOperationId = normalized.operationId,
                payloadHash = payloadHash,
                changeNote = ResumeChangeNotePolicy.normalized(
                    normalized.changeNote,
                    normalized.noChangeConfirmed,
                ),
                resumedAt = normalized.resumedAt,
                createdAt = normalized.resumedAt,
            )
            val restored = resolveLifecycleTransition(
                lifecycle,
                LifecycleAction.RESTORE,
                normalized.resumedAt,
            )
            lifecycleEvents.insertResumeEvent(event)
            if (core.updateIssueLifecycle(restored) != 1) {
                throw IllegalStateException("Resume lifecycle update failed")
            }
            RepositoryResult.Success(ResumedIssueResult(restored, event))
        }
    }

    override suspend fun createRelatedIssue(
        command: CreateRelatedIssueCommand,
    ): RepositoryResult<RelatedIssueResult> {
        val normalized = try {
            IssueLifecycleV12CommandPolicy.normalize(command)
        } catch (_: IllegalArgumentException) {
            return invalid("create_related_issue", "related_issue_payload_invalid")
        }
        val payloadHash = IssueLifecycleV12PayloadHasher.hash(normalized)
        return transactions.databaseTransaction("create_related_issue") {
            val core = jianyuRepositoryDao()
            val lifecycleEvents = issueLifecycleV12Dao()
            val existing = lifecycleEvents.getIssueRelationByOperation(normalized.operationId)
            if (existing != null) {
                if (existing.payloadHash != payloadHash) {
                    return@databaseTransaction conflict("create_related_issue", normalized.operationId)
                }
                val issue = core.getIssue(existing.targetIssueId)
                    ?: return@databaseTransaction compatibility("create_related_issue", "missing_related_issue")
                val stage = core.getStagesForIssue(existing.targetIssueId).singleOrNull()
                    ?: return@databaseTransaction compatibility("create_related_issue", "invalid_related_stage")
                val lifecycle = core.getIssueLifecycle(existing.targetIssueId)
                    ?: return@databaseTransaction compatibility("create_related_issue", "missing_related_lifecycle")
                return@databaseTransaction RepositoryResult.Success(
                    RelatedIssueResult(issue, stage, lifecycle, existing),
                    idempotent = true,
                )
            }

            val sourceLifecycle = core.getIssueLifecycle(normalized.sourceIssueId)
                ?: return@databaseTransaction notFound("issue", normalized.sourceIssueId)
            if (sourceLifecycle.state != IssueLifecycleState.ARCHIVED ||
                sourceLifecycle.purgeRequestedAt != null
            ) {
                return@databaseTransaction invalid("create_related_issue", "related_issue_source_not_archived")
            }
            val archive = lifecycleEvents.getArchiveEvent(normalized.sourceArchiveEventId)
                ?: return@databaseTransaction notFound("archive_event", normalized.sourceArchiveEventId)
            if (archive.issueId != normalized.sourceIssueId) {
                return@databaseTransaction invalid("create_related_issue", "archive_event_issue_mismatch")
            }
            if (core.getIssue(normalized.targetIssueId) != null ||
                core.getStage(normalized.targetStageId) != null
            ) {
                return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.AlreadyExists("related_issue", normalized.targetIssueId),
                )
            }

            val issue = IssueEntity(
                id = normalized.targetIssueId,
                title = normalized.targetIssueTitle,
                createdAt = normalized.createdAt,
                updatedAt = normalized.createdAt,
            )
            val stage = StageEntity(
                id = normalized.targetStageId,
                issueId = normalized.targetIssueId,
                sequenceIndex = 0,
                title = normalized.targetStageTitle,
                objective = normalized.targetObjective,
                createdAt = normalized.createdAt,
                updatedAt = normalized.createdAt,
            )
            val lifecycle = IssueLifecycleEntity(
                issueId = normalized.targetIssueId,
                state = IssueLifecycleState.ACTIVE,
                stateChangedAt = normalized.createdAt,
                updatedAt = normalized.createdAt,
            )
            val relation = IssueRelationEntity(
                id = normalized.relationId,
                sourceIssueId = normalized.sourceIssueId,
                targetIssueId = normalized.targetIssueId,
                sourceArchiveEventId = normalized.sourceArchiveEventId,
                operationId = normalized.operationId,
                payloadHash = payloadHash,
                relationType = IssueRelationType.CONTINUATION,
                createdAt = normalized.createdAt,
            )
            core.insertIssue(issue)
            core.insertStage(stage)
            core.insertIssueLifecycle(lifecycle)
            lifecycleEvents.insertIssueRelation(relation)
            RepositoryResult.Success(RelatedIssueResult(issue, stage, lifecycle, relation))
        }
    }

    override suspend fun listArchiveEvents(
        issueId: String,
    ): RepositoryResult<List<IssueArchiveEventEntity>> = transactions.execute("list_archive_events") {
        require(issueId.isNotBlank())
        RepositoryResult.Success(transactions.databaseRead { issueLifecycleV12Dao().listArchiveEvents(issueId) })
    }

    override suspend fun listResumeEvents(
        issueId: String,
    ): RepositoryResult<List<IssueResumeEventEntity>> = transactions.execute("list_resume_events") {
        require(issueId.isNotBlank())
        RepositoryResult.Success(transactions.databaseRead { issueLifecycleV12Dao().listResumeEvents(issueId) })
    }

    override suspend fun listIssueRelations(
        issueId: String,
    ): RepositoryResult<List<IssueRelationEntity>> = transactions.execute("list_issue_relations") {
        require(issueId.isNotBlank())
        val relations = transactions.databaseRead {
            val dao = issueLifecycleV12Dao()
            (dao.listRelationsFromIssue(issueId) + dao.listRelationsToIssue(issueId))
                .distinctBy(IssueRelationEntity::id)
                .sortedWith(compareBy(IssueRelationEntity::createdAt, IssueRelationEntity::id))
        }
        RepositoryResult.Success(relations)
    }

    override suspend fun requestIssuePurgeOperation(
        command: RequestIssuePurgeOperationCommand,
    ): RepositoryResult<IssuePurgeOperationEntity> {
        val normalized = try {
            IssueLifecycleV12CommandPolicy.normalize(command)
        } catch (_: IllegalArgumentException) {
            return invalid("request_issue_purge", "purge_confirmation_required")
        }
        val payloadHash = IssueLifecycleV12PayloadHasher.hash(normalized)
        return transactions.databaseTransaction("request_issue_purge") {
            val core = jianyuRepositoryDao()
            val lifecycleEvents = issueLifecycleV12Dao()
            val byOperation = lifecycleEvents.getPurgeOperationByIdempotencyKey(normalized.operationId)
            if (byOperation != null) {
                return@databaseTransaction if (byOperation.payloadHash == payloadHash) {
                    RepositoryResult.Success(byOperation, idempotent = true)
                } else {
                    conflict("request_issue_purge", normalized.operationId)
                }
            }
            val existingForIssue = lifecycleEvents.getPurgeOperationForIssue(normalized.issueId)
            if (existingForIssue != null) {
                return@databaseTransaction invalid("request_issue_purge", "purge_operation_exists")
            }
            val lifecycle = core.getIssueLifecycle(normalized.issueId)
                ?: return@databaseTransaction notFound("issue", normalized.issueId)
            if (lifecycle.state != IssueLifecycleState.TRASHED || lifecycle.purgeRequestedAt != null) {
                return@databaseTransaction invalid("request_issue_purge", "purge_requires_trashed")
            }

            val operation = IssuePurgeOperationEntity(
                id = normalized.id,
                issueId = normalized.issueId,
                operationId = normalized.operationId,
                payloadHash = payloadHash,
                impactHash = normalized.impactHash,
                state = IssuePurgeState.REQUESTED,
                requestedAt = normalized.requestedAt,
                updatedAt = normalized.requestedAt,
            )
            val updatedLifecycle = resolveLifecycleTransition(
                lifecycle,
                LifecycleAction.REQUEST_PURGE,
                normalized.requestedAt,
            )
            lifecycleEvents.insertPurgeOperation(operation)
            if (core.updateIssueLifecycle(updatedLifecycle) != 1) {
                throw IllegalStateException("Purge lifecycle update failed")
            }
            RepositoryResult.Success(operation)
        }
    }

    override suspend fun transitionIssuePurgeOperation(
        command: TransitionIssuePurgeOperationCommand,
    ): RepositoryResult<IssuePurgeOperationEntity> {
        if (command.operationId.isBlank() || command.expectedStates.isEmpty() || command.updatedAt <= 0L) {
            return invalid("transition_issue_purge", "purge_transition_invalid")
        }
        return transactions.databaseTransaction("transition_issue_purge") {
            val dao = issueLifecycleV12Dao()
            val current = dao.getPurgeOperation(command.operationId)
                ?: return@databaseTransaction notFound("purge_operation", command.operationId)
            if (current.state !in command.expectedStates) {
                return@databaseTransaction invalid("transition_issue_purge", "purge_state_changed")
            }
            if (!IssuePurgeTransitionPolicy.canTransition(current.state, command.targetState)) {
                return@databaseTransaction invalid("transition_issue_purge", "purge_transition_forbidden")
            }
            if (command.targetState == IssuePurgeState.FAILED_RETRYABLE &&
                (command.failureCode.isNullOrBlank() || command.failurePhase == null)
            ) {
                return@databaseTransaction invalid("transition_issue_purge", "purge_failure_details_required")
            }
            val target = current.copy(
                state = command.targetState,
                startedAt = current.startedAt ?: command.updatedAt,
                updatedAt = command.updatedAt,
                failedAt = if (command.targetState == IssuePurgeState.FAILED_RETRYABLE) {
                    command.updatedAt
                } else {
                    null
                },
                failureCode = command.failureCode,
                failurePhase = command.failurePhase,
                retryCount = if (command.targetState == IssuePurgeState.FAILED_RETRYABLE) {
                    current.retryCount + 1
                } else {
                    current.retryCount
                },
            )
            if (dao.updatePurgeOperation(target) != 1) {
                throw IllegalStateException("Purge operation update failed")
            }
            RepositoryResult.Success(target)
        }
    }

    override suspend fun getIssuePurgeOperation(
        operationId: String,
    ): RepositoryResult<IssuePurgeOperationEntity> = transactions.execute("get_issue_purge") {
        require(operationId.isNotBlank())
        val operation = transactions.databaseRead {
            issueLifecycleV12Dao().getPurgeOperation(operationId)
        } ?: return@execute notFound("purge_operation", operationId)
        RepositoryResult.Success(operation)
    }

    override suspend fun listRecoverableIssuePurgeOperations(): RepositoryResult<List<IssuePurgeOperationEntity>> =
        transactions.execute("list_recoverable_issue_purge") {
            RepositoryResult.Success(
                transactions.databaseRead {
                    issueLifecycleV12Dao().listRecoverablePurgeOperations()
                },
            )
        }
}

internal object IssuePurgeTransitionPolicy {
    private val allowed = mapOf(
        IssuePurgeState.REQUESTED to setOf(
            IssuePurgeState.WAITING_FOR_TASKS,
            IssuePurgeState.CANCELING_TASKS,
            IssuePurgeState.FAILED_RETRYABLE,
        ),
        IssuePurgeState.WAITING_FOR_TASKS to setOf(
            IssuePurgeState.CANCELING_TASKS,
            IssuePurgeState.FAILED_RETRYABLE,
        ),
        IssuePurgeState.CANCELING_TASKS to setOf(
            IssuePurgeState.DELETING_FILES,
            IssuePurgeState.FAILED_RETRYABLE,
        ),
        IssuePurgeState.DELETING_FILES to setOf(
            IssuePurgeState.READY_FOR_DATABASE_PURGE,
            IssuePurgeState.FAILED_RETRYABLE,
        ),
        IssuePurgeState.READY_FOR_DATABASE_PURGE to setOf(
            IssuePurgeState.DATABASE_PURGING,
            IssuePurgeState.FAILED_RETRYABLE,
        ),
        IssuePurgeState.DATABASE_PURGING to setOf(IssuePurgeState.FAILED_RETRYABLE),
        IssuePurgeState.FAILED_RETRYABLE to setOf(
            IssuePurgeState.WAITING_FOR_TASKS,
            IssuePurgeState.CANCELING_TASKS,
            IssuePurgeState.DELETING_FILES,
            IssuePurgeState.READY_FOR_DATABASE_PURGE,
            IssuePurgeState.DATABASE_PURGING,
        ),
        IssuePurgeState.COMPLETED to emptySet(),
    )

    fun canTransition(from: IssuePurgeState, to: IssuePurgeState): Boolean = to in allowed.getValue(from)
}

private fun <T> invalid(operation: String, code: String): RepositoryResult<T> =
    RepositoryResult.Failure(RepositoryError.InvalidState(operation, code))

private fun <T> conflict(operation: String, id: String): RepositoryResult<T> =
    RepositoryResult.Failure(RepositoryError.IdempotencyConflict(operation, id))

private fun <T> notFound(resource: String, id: String): RepositoryResult<T> =
    RepositoryResult.Failure(RepositoryError.NotFound(resource, id))

private fun <T> compatibility(operation: String, code: String): RepositoryResult<T> =
    RepositoryResult.Failure(RepositoryError.CompatibilityFailure(operation, code))
