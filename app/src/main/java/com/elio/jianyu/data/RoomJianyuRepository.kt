package com.elio.jianyu.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import androidx.room.withTransaction

class RoomJianyuRepository(
    private val database: RoundtableDatabase,
    private val officialSkillIdValidator: OfficialSkillIdValidator = RejectingOfficialSkillIdValidator
) : JianyuRepository {
    private val dao: JianyuRepositoryDao
        get() = database.jianyuRepositoryDao()

    override suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<SavedIssue> {
        return safely("save_issue") {
            require(command.issueId.isNotBlank() && command.initialStageId.isNotBlank())
            require(command.title.isNotBlank() && command.createdAt > 0L)
            database.withTransaction {
                val issue = IssueEntity(
                    id = command.issueId,
                    title = command.title,
                    createdAt = command.createdAt,
                    updatedAt = command.createdAt,
                    legacyChatSessionId = null
                )
                val stage = StageEntity(
                    id = command.initialStageId,
                    issueId = command.issueId,
                    sequenceIndex = 0,
                    title = command.initialStageTitle,
                    objective = command.initialObjective,
                    createdAt = command.createdAt,
                    updatedAt = command.createdAt
                )
                val lifecycle = IssueLifecycleEntity(
                    issueId = command.issueId,
                    state = IssueLifecycleState.ACTIVE,
                    stateChangedAt = command.createdAt,
                    updatedAt = command.createdAt
                )
                val existingIssue = dao.getIssue(command.issueId)
                if (existingIssue != null) {
                    val existingStage = dao.getStage(command.initialStageId)
                    val existingLifecycle = dao.getIssueLifecycle(command.issueId)
                    if (existingIssue == issue && existingStage == stage && existingLifecycle == lifecycle) {
                        return@withTransaction RepositoryResult.Success(
                            SavedIssue(existingIssue, existingStage, existingLifecycle),
                            idempotent = true
                        )
                    }
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict("save_issue", command.issueId)
                    )
                }
                if (dao.getStage(command.initialStageId) != null) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.AlreadyExists("stage", command.initialStageId)
                    )
                }
                dao.insertIssue(issue)
                dao.insertStage(stage)
                dao.insertIssueLifecycle(lifecycle)
                RepositoryResult.Success(SavedIssue(issue, stage, lifecycle))
            }
        }
    }

    override suspend fun createStage(command: CreateStageCommand): RepositoryResult<StageEntity> {
        return safely("create_stage") {
            require(command.stageId.isNotBlank() && command.issueId.isNotBlank())
            require(command.title.isNotBlank() && command.createdAt > 0L)
            database.withTransaction {
                val existing = dao.getStage(command.stageId)
                if (existing != null) {
                    val matches = existing.issueId == command.issueId &&
                        existing.title == command.title &&
                        existing.objective == command.objective &&
                        existing.createdAt == command.createdAt
                    return@withTransaction if (matches) {
                        RepositoryResult.Success(existing, idempotent = true)
                    } else {
                        RepositoryResult.Failure(
                            RepositoryError.IdempotencyConflict("create_stage", command.stageId)
                        )
                    }
                }
                if (dao.getIssue(command.issueId) == null) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("issue", command.issueId)
                    )
                }
                val stage = StageEntity(
                    id = command.stageId,
                    issueId = command.issueId,
                    sequenceIndex = (dao.getMaxStageSequence(command.issueId) ?: -1) + 1,
                    title = command.title,
                    objective = command.objective,
                    createdAt = command.createdAt,
                    updatedAt = command.createdAt
                )
                dao.insertStage(stage)
                RepositoryResult.Success(stage)
            }
        }
    }

    override suspend fun undoLatestUnrunStage(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> {
        return safely("undo_latest_stage") {
            database.withTransaction {
                val stage = dao.getStage(stageId)
                    ?: return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("stage", stageId)
                    )
                if (stage.issueId != issueId || stage.sequenceIndex == 0) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState("undo_latest_stage", "not_undoable")
                    )
                }
                val latest = dao.getStagesForIssue(issueId).lastOrNull()
                if (latest?.id != stageId) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState("undo_latest_stage", "not_latest")
                    )
                }
                val dependencyCount = dao.countRunsForStage(issueId, stageId) +
                    dao.countMessagesForStage(issueId, stageId) +
                    dao.countDraftsForStage(issueId, stageId) +
                    dao.countDraftRevisionsForStage(issueId, stageId) +
                    dao.countArtifactsForStage(issueId, stageId) +
                    dao.countMaterialReferencesForStage(issueId, stageId) +
                    dao.countMaterialUsagesForStage(issueId, stageId) +
                    dao.countPersonalContextUsagesForStage(issueId, stageId) +
                    dao.countAudioAssetsForStage(issueId, stageId)
                if (dependencyCount != 0) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState("undo_latest_stage", "stage_has_dependencies")
                    )
                }
                if (dao.deleteStage(issueId, stageId) != 1) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.StorageFailure("undo_latest_stage", retryable = true)
                    )
                }
                RepositoryResult.Success(Unit)
            }
        }
    }

    override suspend fun createExecutionRun(
        command: CreateExecutionRunCommand
    ): RepositoryResult<ExecutionRunSnapshot> {
        return safely("create_execution_run") {
            require(command.run.id.isNotBlank() && command.run.idempotencyKey.isNotBlank())
            require(command.run.status == ExecutionRunStatus.NOT_STARTED)
            require(validateParticipantPayload(command.participants))
            require(command.participants.all { it.runId == command.run.id })
            database.withTransaction {
                val existing = dao.getExecutionRunByIdempotencyKey(command.run.idempotencyKey)
                if (existing != null) {
                    val storedParticipants = dao.getParticipantSnapshots(existing.id)
                    val requestedParticipants = command.participants.sortedBy { it.position }
                    return@withTransaction if (
                        existing == command.run && storedParticipants == requestedParticipants
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
                val stage = dao.getStage(command.run.stageId)
                if (stage == null || stage.issueId != command.run.issueId) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("stage", command.run.stageId)
                    )
                }
                dao.insertExecutionRun(command.run)
                if (command.participants.isNotEmpty()) {
                    dao.insertParticipantSnapshots(command.participants.sortedBy { it.position })
                }
                RepositoryResult.Success(
                    ExecutionRunSnapshot(command.run, command.participants.sortedBy { it.position })
                )
            }
        }
    }

    override suspend fun appendDomainMessage(
        command: AppendDomainMessageCommand
    ): RepositoryResult<Message> {
        return safely("append_domain_message") {
            require(command.messageId > 0L && command.roundIndex >= 0)
            require(command.issueId.isNotBlank() && command.stageId.isNotBlank())
            require(command.senderId.isNotBlank() && command.timestamp > 0L)
            database.withTransaction {
                val existing = dao.getMessage(command.messageId)
                if (existing != null) {
                    return@withTransaction if (messageMatches(existing, command)) {
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
                val issue = dao.getIssue(command.issueId)
                    ?: return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("issue", command.issueId)
                    )
                val stage = dao.getStage(command.stageId)
                if (stage == null || stage.issueId != command.issueId) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("stage", command.stageId)
                    )
                }
                if (command.executionRunId == null) {
                    if (command.participantSnapshotId != null || command.senderId != "user") {
                        return@withTransaction RepositoryResult.Failure(
                            RepositoryError.InvalidState(
                                "append_domain_message",
                                "user_message_relation_invalid"
                            )
                        )
                    }
                } else {
                    val run = dao.getExecutionRun(command.executionRunId)
                    if (
                        run == null || run.issueId != command.issueId ||
                        run.stageId != command.stageId
                    ) {
                        return@withTransaction RepositoryResult.Failure(
                            RepositoryError.InvalidState(
                                "append_domain_message",
                                "run_relation_invalid"
                            )
                        )
                    }
                    if (command.participantSnapshotId != null) {
                        val participant = dao.getParticipantSnapshots(run.id)
                            .firstOrNull { it.id == command.participantSnapshotId }
                        if (participant == null || participant.sourceId != command.senderId) {
                            return@withTransaction RepositoryResult.Failure(
                                RepositoryError.InvalidState(
                                    "append_domain_message",
                                    "participant_relation_invalid"
                                )
                            )
                        }
                    } else if (command.senderId != "user") {
                        return@withTransaction RepositoryResult.Failure(
                            RepositoryError.InvalidState(
                                "append_domain_message",
                                "participant_required"
                            )
                        )
                    }
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
                dao.insertDomainMessage(message)
                RepositoryResult.Success(message)
            }
        }
    }

    override suspend fun transitionRun(
        command: TransitionRunCommand
    ): RepositoryResult<ExecutionRunEntity> {
        return safely("transition_run") {
            require(command.expectedStatuses.isNotEmpty() && command.updatedAt > 0L)
            database.withTransaction {
                val existing = dao.getExecutionRun(command.runId)
                    ?: return@withTransaction RepositoryResult.Failure(
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
                    return@withTransaction RepositoryResult.Success(existing, idempotent = true)
                }
                val changed = dao.compareAndSetRunStatus(
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
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "transition_run",
                            "expected_state_mismatch"
                        )
                    )
                }
                RepositoryResult.Success(
                    dao.getExecutionRun(command.runId)
                        ?: throw IllegalStateException("Run update disappeared")
                )
            }
        }
    }

    override suspend fun saveStageDraft(
        command: SaveStageDraftCommand
    ): RepositoryResult<StageSummaryDraftEntity> {
        return safely("save_stage_draft") {
            val draft = command.draft
            val revision = command.revision
            require(
                draft.id == revision.draftIdSnapshot &&
                    draft.issueId == revision.issueId &&
                    draft.stageId == revision.stageId &&
                    draft.revisionNumber == revision.revisionNumber &&
                    draft.revisionNumber > 0
            )
            database.withTransaction {
                val stage = dao.getStage(draft.stageId)
                if (stage == null || stage.issueId != draft.issueId) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("stage", draft.stageId)
                    )
                }
                val existingRevision = dao.getDraftRevision(revision.id)
                val current = dao.getDraft(draft.issueId, draft.stageId)
                if (existingRevision != null) {
                    return@withTransaction if (existingRevision == revision && current == draft) {
                        RepositoryResult.Success(draft, idempotent = true)
                    } else {
                        RepositoryResult.Failure(
                            RepositoryError.IdempotencyConflict("save_stage_draft", revision.id)
                        )
                    }
                }
                val expectedRevision = (current?.revisionNumber ?: 0) + 1
                if (revision.revisionNumber != expectedRevision) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState("save_stage_draft", "revision_not_contiguous")
                    )
                }
                if (current != null && current.id != draft.id) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict("save_stage_draft", draft.id)
                    )
                }
                dao.insertDraftRevision(revision)
                if (current == null) {
                    dao.insertDraft(draft)
                } else if (dao.updateDraft(draft) != 1) {
                    throw IllegalStateException("Draft update failed")
                }
                RepositoryResult.Success(draft)
            }
        }
    }

    override suspend fun abandonStageDraft(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> {
        return safely("abandon_stage_draft") {
            database.withTransaction {
                val stage = dao.getStage(stageId)
                if (stage == null || stage.issueId != issueId) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("stage", stageId)
                    )
                }
                val deleted = dao.deleteDraft(issueId, stageId)
                RepositoryResult.Success(Unit, idempotent = deleted == 0)
            }
        }
    }

    override suspend fun confirmArtifact(
        command: ConfirmArtifactCommand
    ): RepositoryResult<ConfirmedArtifactEntity> {
        return safely("confirm_artifact") {
            val artifact = command.artifact
            require(artifact.confirmedAt > 0L)
            validateArtifactRevision(artifact.id, artifact.revisionOfArtifactId)
            validateArtifactSources(artifact, command.sources)
            database.withTransaction {
                val existing = dao.getArtifact(artifact.id)
                if (existing != null) {
                    return@withTransaction if (
                        existing == artifact && storedArtifactSources(artifact.id) ==
                        normalizeSources(command.sources)
                    ) {
                        RepositoryResult.Success(existing, idempotent = true)
                    } else {
                        RepositoryResult.Failure(
                            RepositoryError.IdempotencyConflict("confirm_artifact", artifact.id)
                        )
                    }
                }
                val stage = dao.getStage(artifact.stageId)
                if (stage == null || stage.issueId != artifact.issueId) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("stage", artifact.stageId)
                    )
                }
                val relationError = validateArtifactRelations(artifact, command.sources)
                if (relationError != null) {
                    return@withTransaction RepositoryResult.Failure(relationError)
                }
                dao.insertArtifact(artifact)
                if (command.sources.messages.isNotEmpty()) {
                    dao.insertArtifactMessageSources(command.sources.messages)
                }
                if (command.sources.runs.isNotEmpty()) {
                    dao.insertArtifactRunSources(command.sources.runs)
                }
                if (command.sources.draftRevisions.isNotEmpty()) {
                    dao.insertArtifactDraftSources(command.sources.draftRevisions)
                }
                if (command.sources.materials.isNotEmpty()) {
                    dao.insertArtifactMaterialSources(command.sources.materials)
                }
                RepositoryResult.Success(artifact)
            }
        }
    }

    override suspend fun recordMaterialUsage(
        entity: MaterialUsageSnapshotEntity
    ): RepositoryResult<MaterialUsageSnapshotEntity> {
        return safely("record_material_usage") {
            require(entity.userConfirmedAt > 0L)
            database.withTransaction {
                val existing = dao.getMaterialUsage(entity.id)
                if (existing != null) {
                    return@withTransaction if (existing == entity) {
                        RepositoryResult.Success(existing, idempotent = true)
                    } else {
                        RepositoryResult.Failure(
                            RepositoryError.IdempotencyConflict("record_material_usage", entity.id)
                        )
                    }
                }
                val relationError = validateUsageRelations(
                    entity.issueId,
                    entity.stageId,
                    entity.runId
                )
                if (relationError != null) {
                    return@withTransaction RepositoryResult.Failure(relationError)
                }
                dao.insertMaterialUsage(entity)
                RepositoryResult.Success(entity)
            }
        }
    }

    override suspend fun recordPersonalContextUsage(
        entity: PersonalContextUsageSnapshotEntity
    ): RepositoryResult<PersonalContextUsageSnapshotEntity> {
        return safely("record_personal_context_usage") {
            require(entity.userConfirmedAt > 0L)
            database.withTransaction {
                val existing = dao.getPersonalContextUsage(entity.id)
                if (existing != null) {
                    return@withTransaction if (existing == entity) {
                        RepositoryResult.Success(existing, idempotent = true)
                    } else {
                        RepositoryResult.Failure(
                            RepositoryError.IdempotencyConflict(
                                "record_personal_context_usage",
                                entity.id
                            )
                        )
                    }
                }
                val relationError = validateUsageRelations(
                    entity.issueId,
                    entity.stageId,
                    entity.runId
                )
                if (relationError != null) {
                    return@withTransaction RepositoryResult.Failure(relationError)
                }
                dao.insertPersonalContextUsage(entity)
                RepositoryResult.Success(entity)
            }
        }
    }

    override suspend fun saveOfficialSkillCombination(
        command: SaveOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationSnapshot> {
        return safely("save_official_skill_combination") {
            validateOfficialCombinationMembers(command.members)
            require(command.members.all { it.combinationId == command.combination.id })
            val invalidId = command.members.firstOrNull {
                !officialSkillIdValidator.isValid(it.officialSkillId)
            }?.officialSkillId
            if (invalidId != null) {
                return@safely RepositoryResult.Failure(
                    RepositoryError.ConstraintViolation(
                        "save_official_skill_combination",
                        "unknown_official_skill_id"
                    )
                )
            }
            database.withTransaction {
                val existing = dao.getOfficialSkillCombination(command.combination.id)
                val requestedMembers = command.members.sortedBy { it.position }
                if (existing == null) {
                    if (command.expectedUpdatedAt != null) {
                        return@withTransaction RepositoryResult.Failure(
                            RepositoryError.NotFound("official_skill_combination", command.combination.id)
                        )
                    }
                    dao.insertOfficialSkillCombination(command.combination)
                    if (requestedMembers.isNotEmpty()) {
                        dao.insertOfficialSkillCombinationMembers(requestedMembers)
                    }
                    return@withTransaction RepositoryResult.Success(
                        OfficialSkillCombinationSnapshot(command.combination, requestedMembers)
                    )
                }
                val storedMembers = dao.getOfficialSkillCombinationMembers(existing.id)
                if (existing == command.combination && storedMembers == requestedMembers) {
                    return@withTransaction RepositoryResult.Success(
                        OfficialSkillCombinationSnapshot(existing, storedMembers),
                        idempotent = true
                    )
                }
                if (command.expectedUpdatedAt == null) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.AlreadyExists("official_skill_combination", existing.id)
                    )
                }
                if (existing.updatedAt != command.expectedUpdatedAt) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "save_official_skill_combination",
                            "stale_combination"
                        )
                    )
                }
                if (dao.updateOfficialSkillCombination(command.combination) != 1) {
                    throw IllegalStateException("Combination update failed")
                }
                dao.deleteOfficialSkillCombinationMembers(existing.id)
                if (requestedMembers.isNotEmpty()) {
                    dao.insertOfficialSkillCombinationMembers(requestedMembers)
                }
                RepositoryResult.Success(
                    OfficialSkillCombinationSnapshot(command.combination, requestedMembers)
                )
            }
        }
    }

    override suspend fun deleteOfficialSkillCombination(
        command: DeleteOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationEntity> {
        return safely("delete_official_skill_combination") {
            require(command.deletedAt > 0L)
            database.withTransaction {
                val existing = dao.getOfficialSkillCombination(command.combinationId)
                    ?: return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("official_skill_combination", command.combinationId)
                    )
                if (existing.deletedAt != null) {
                    return@withTransaction RepositoryResult.Success(existing, idempotent = true)
                }
                if (existing.updatedAt != command.expectedUpdatedAt) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "delete_official_skill_combination",
                            "stale_combination"
                        )
                    )
                }
                val deleted = existing.copy(
                    updatedAt = command.deletedAt,
                    deletedAt = command.deletedAt,
                    isEnabled = false
                )
                if (dao.updateOfficialSkillCombination(deleted) != 1) {
                    throw IllegalStateException("Combination delete failed")
                }
                RepositoryResult.Success(deleted)
            }
        }
    }

    override suspend fun getOfficialSkillCombination(
        combinationId: String
    ): RepositoryResult<OfficialSkillCombinationSnapshot> {
        return safely("get_official_skill_combination") {
            val combination = dao.getOfficialSkillCombination(combinationId)
                ?: return@safely RepositoryResult.Failure(
                    RepositoryError.NotFound("official_skill_combination", combinationId)
                )
            RepositoryResult.Success(
                OfficialSkillCombinationSnapshot(
                    combination,
                    dao.getOfficialSkillCombinationMembers(combinationId)
                )
            )
        }
    }

    override suspend fun listOfficialSkillCombinations(): RepositoryResult<List<OfficialSkillCombinationSnapshot>> {
        return safely("list_official_skill_combinations") {
            RepositoryResult.Success(
                dao.getActiveOfficialSkillCombinations().map { combination ->
                    OfficialSkillCombinationSnapshot(
                        combination,
                        dao.getOfficialSkillCombinationMembers(combination.id)
                    )
                }
            )
        }
    }

    override suspend fun archiveIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.ARCHIVE, changedAt)
    }

    override suspend fun restoreIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.RESTORE, changedAt)
    }

    override suspend fun moveIssueToTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.MOVE_TO_TRASH, changedAt)
    }

    override suspend fun restoreIssueFromTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.RESTORE_FROM_TRASH, changedAt)
    }

    override suspend fun requestIssuePurge(
        issueId: String,
        requestedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.REQUEST_PURGE, requestedAt)
    }

    override suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot> {
        return safely("recover_issue") {
            database.withTransaction {
                val issue = dao.getIssue(issueId)
                    ?: return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("issue", issueId)
                    )
                val lifecycle = dao.getIssueLifecycle(issueId)
                    ?: return@withTransaction RepositoryResult.Failure(
                        RepositoryError.CompatibilityFailure(
                            "recover_issue",
                            "missing_issue_lifecycle"
                        )
                    )
                val stages = dao.getStagesForIssue(issueId)
                val runs = dao.getExecutionRunsForIssue(issueId)
                val participants = dao.getParticipantSnapshotsForIssue(issueId)
                val messages = dao.getMessagesForIssue(issueId)
                val activeStatuses = setOf(
                    ExecutionRunStatus.NOT_STARTED,
                    ExecutionRunStatus.RUNNING,
                    ExecutionRunStatus.PARTIAL_SUCCESS,
                    ExecutionRunStatus.RETRYABLE
                )
                RepositoryResult.Success(
                    IssueRecoverySnapshot(
                        core = IssueRecoveryCore(
                            issue = issue,
                            lifecycle = lifecycle,
                            stages = stages,
                            currentStage = stages.lastOrNull(),
                            runs = runs,
                            activeOrRecoverableRuns = runs.filter { it.status in activeStatuses },
                            participants = participants,
                            messages = messages,
                            pendingMessages = messages.filter { it.isPending }
                        ),
                        resources = IssueRecoveryResources(
                            drafts = dao.getDraftsForIssue(issueId),
                            draftRevisions = dao.getDraftRevisionsForIssue(issueId),
                            artifacts = dao.getArtifactsForIssue(issueId),
                            materialUsages = dao.getMaterialUsagesForIssue(issueId),
                            personalContextUsages = dao.getPersonalContextUsagesForIssue(issueId),
                            audioAssets = dao.getAudioAssetsForIssue(issueId)
                        )
                    )
                )
            }
        }
    }

    override suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState>
    ): RepositoryResult<List<IssueNavigationItem>> {
        return safely("list_issue_navigation") {
            database.withTransaction {
                val lifecycleByIssue = dao.getAllIssueLifecycles().associateBy { it.issueId }
                val items = dao.getAllIssues().mapNotNull { issue ->
                    val lifecycle = lifecycleByIssue[issue.id] ?: return@mapNotNull null
                    if (lifecycle.state !in states) return@mapNotNull null
                    val stages = dao.getStagesForIssue(issue.id)
                    val runs = dao.getExecutionRunsForIssue(issue.id)
                    IssueNavigationItem(
                        issue = issue,
                        lifecycle = lifecycle,
                        currentStage = stages.lastOrNull(),
                        activeRunCount = runs.count {
                            it.status in setOf(
                                ExecutionRunStatus.NOT_STARTED,
                                ExecutionRunStatus.RUNNING,
                                ExecutionRunStatus.PARTIAL_SUCCESS,
                                ExecutionRunStatus.RETRYABLE
                            )
                        }
                    )
                }
                RepositoryResult.Success(items)
            }
        }
    }

    private suspend fun transitionLifecycle(
        issueId: String,
        action: LifecycleAction,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return safely("transition_issue_lifecycle") {
            database.withTransaction {
                if (dao.getIssue(issueId) == null) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.NotFound("issue", issueId)
                    )
                }
                val current = dao.getIssueLifecycle(issueId)
                    ?: return@withTransaction RepositoryResult.Failure(
                        RepositoryError.CompatibilityFailure(
                            "transition_issue_lifecycle",
                            "missing_issue_lifecycle"
                        )
                    )
                val target = try {
                    resolveLifecycleTransition(current, action, changedAt)
                } catch (error: IllegalArgumentException) {
                    return@withTransaction RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "transition_issue_lifecycle",
                            "illegal_transition"
                        )
                    )
                }
                if (target == current) {
                    return@withTransaction RepositoryResult.Success(current, idempotent = true)
                }
                if (dao.updateIssueLifecycle(target) != 1) {
                    throw IllegalStateException("Lifecycle update failed")
                }
                RepositoryResult.Success(target)
            }
        }
    }

    private suspend fun ensureCompatibilitySession(
        issue: IssueEntity,
        command: AppendDomainMessageCommand
    ): Long {
        val existingId = issue.legacyChatSessionId
        if (existingId != null) {
            if (dao.getCompatibilitySession(existingId) == null) {
                throw CompatibilityAbort("missing_legacy_chat_session")
            }
            return existingId
        }
        val sessionId = dao.insertCompatibilitySession(
            ChatSession(
                title = command.compatibilitySessionTitle.ifBlank { issue.title },
                createdAt = command.timestamp
            )
        )
        if (
            dao.updateIssue(
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

    private fun messageMatches(existing: Message, command: AppendDomainMessageCommand): Boolean {
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

    private suspend fun validateArtifactRelations(
        artifact: ConfirmedArtifactEntity,
        sources: ArtifactSources
    ): RepositoryError? {
        for (source in sources.messages) {
            val message = dao.getMessage(source.messageId)
            if (
                message == null || message.issueId != artifact.issueId ||
                message.stageId != artifact.stageId
            ) {
                return RepositoryError.ConstraintViolation(
                    "confirm_artifact",
                    "message_source_mismatch"
                )
            }
        }
        for (source in sources.runs) {
            val run = dao.getExecutionRun(source.runId)
            if (run == null || run.issueId != artifact.issueId || run.stageId != artifact.stageId) {
                return RepositoryError.ConstraintViolation("confirm_artifact", "run_source_mismatch")
            }
        }
        for (source in sources.draftRevisions) {
            val revision = dao.getDraftRevision(source.draftRevisionId)
            if (
                revision == null || revision.issueId != artifact.issueId ||
                revision.stageId != artifact.stageId
            ) {
                return RepositoryError.ConstraintViolation(
                    "confirm_artifact",
                    "draft_source_mismatch"
                )
            }
        }
        for (source in sources.materials) {
            val usage = dao.getMaterialUsage(source.materialUsageSnapshotId)
            if (
                usage == null || usage.issueId != artifact.issueId ||
                usage.stageId != artifact.stageId
            ) {
                return RepositoryError.ConstraintViolation(
                    "confirm_artifact",
                    "material_source_mismatch"
                )
            }
        }
        return null
    }

    private suspend fun validateUsageRelations(
        issueId: String,
        stageId: String,
        runId: String?
    ): RepositoryError? {
        val stage = dao.getStage(stageId)
        if (stage == null || stage.issueId != issueId) {
            return RepositoryError.ConstraintViolation("record_usage", "stage_mismatch")
        }
        if (runId != null) {
            val run = dao.getExecutionRun(runId)
            if (run == null || run.issueId != issueId || run.stageId != stageId) {
                return RepositoryError.ConstraintViolation("record_usage", "run_mismatch")
            }
        }
        return null
    }

    private suspend fun storedArtifactSources(artifactId: String): ArtifactSources {
        return ArtifactSources(
            messages = dao.getArtifactMessageSources(artifactId),
            runs = dao.getArtifactRunSources(artifactId),
            draftRevisions = dao.getArtifactDraftSources(artifactId),
            materials = dao.getArtifactMaterialSources(artifactId)
        )
    }

    private fun normalizeSources(sources: ArtifactSources): ArtifactSources {
        return ArtifactSources(
            messages = sources.messages.sortedBy { it.messageId },
            runs = sources.runs.sortedBy { it.runId },
            draftRevisions = sources.draftRevisions.sortedBy { it.draftRevisionId },
            materials = sources.materials.sortedBy { it.materialUsageSnapshotId }
        )
    }

    private suspend fun <T> safely(
        operation: String,
        block: suspend () -> RepositoryResult<T>
    ): RepositoryResult<T> {
        return try {
            block()
        } catch (error: CompatibilityAbort) {
            RepositoryResult.Failure(
                RepositoryError.CompatibilityFailure(operation, error.code)
            )
        } catch (error: SQLiteConstraintException) {
            RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(operation, "sqlite_constraint")
            )
        } catch (error: SQLiteException) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true)
            )
        } catch (error: IllegalArgumentException) {
            RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(operation, "invalid_argument")
            )
        } catch (error: IllegalStateException) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true)
            )
        } catch (error: Exception) {
            RepositoryResult.Failure(
                RepositoryError.StorageFailure(operation, retryable = true)
            )
        }
    }

    private class CompatibilityAbort(val code: String) : RuntimeException()
}
