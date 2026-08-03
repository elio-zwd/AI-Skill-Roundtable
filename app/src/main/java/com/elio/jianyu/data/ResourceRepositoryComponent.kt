package com.elio.jianyu.data

/**
 * 草稿、成果及官方 Skill 组合组件。
 *
 * 资料和个人背景使用快照由 [UsageRepositoryComponent] 独占处理，避免内部出现第二写入口。
 */
internal class ResourceRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
    private val officialSkillIdValidator: OfficialSkillIdValidator
) {
    suspend fun saveStageDraft(
        command: SaveStageDraftCommand
    ): RepositoryResult<StageSummaryDraftEntity> {
        return transactions.transaction("save_stage_draft") {
            val draft = command.draft
            val revision = command.revision
            require(
                draft.id == revision.draftIdSnapshot &&
                    draft.issueId == revision.issueId &&
                    draft.stageId == revision.stageId &&
                    draft.revisionNumber == revision.revisionNumber &&
                    draft.revisionNumber > 0
            )

            val stage = getStage(draft.stageId)
            if (stage == null || stage.issueId != draft.issueId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", draft.stageId)
                )
            }
            val existingRevision = getDraftRevision(revision.id)
            val current = getDraft(draft.issueId, draft.stageId)
            if (existingRevision != null) {
                return@transaction if (existingRevision == revision && current == draft) {
                    RepositoryResult.Success(draft, idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict("save_stage_draft", revision.id)
                    )
                }
            }
            val expectedRevision = (current?.revisionNumber ?: 0) + 1
            if (revision.revisionNumber != expectedRevision) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "save_stage_draft",
                        "revision_not_contiguous"
                    )
                )
            }
            if (current != null && current.id != draft.id) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.IdempotencyConflict("save_stage_draft", draft.id)
                )
            }

            insertDraftRevision(revision)
            if (current == null) {
                insertDraft(draft)
            } else if (updateDraft(draft) != 1) {
                throw IllegalStateException("Draft update failed")
            }
            RepositoryResult.Success(draft)
        }
    }

    suspend fun abandonStageDraft(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> {
        return transactions.transaction("abandon_stage_draft") {
            val stage = getStage(stageId)
            if (stage == null || stage.issueId != issueId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", stageId)
                )
            }
            val deleted = deleteDraft(issueId, stageId)
            RepositoryResult.Success(Unit, idempotent = deleted == 0)
        }
    }

    suspend fun confirmArtifact(
        command: ConfirmArtifactCommand
    ): RepositoryResult<ConfirmedArtifactEntity> {
        return transactions.transaction("confirm_artifact") {
            val artifact = command.artifact
            require(artifact.confirmedAt > 0L)
            validateArtifactRevision(artifact.id, artifact.revisionOfArtifactId)
            validateArtifactSources(artifact, command.sources)

            val existing = getArtifact(artifact.id)
            if (existing != null) {
                return@transaction if (
                    existing == artifact &&
                    storedArtifactSources(artifact.id) == normalizeSources(command.sources)
                ) {
                    RepositoryResult.Success(existing, idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict("confirm_artifact", artifact.id)
                    )
                }
            }
            val stage = getStage(artifact.stageId)
            if (stage == null || stage.issueId != artifact.issueId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", artifact.stageId)
                )
            }
            val relationError = validateArtifactRelations(artifact, command.sources)
            if (relationError != null) {
                return@transaction RepositoryResult.Failure(relationError)
            }

            insertArtifact(artifact)
            if (command.sources.messages.isNotEmpty()) {
                insertArtifactMessageSources(command.sources.messages)
            }
            if (command.sources.runs.isNotEmpty()) {
                insertArtifactRunSources(command.sources.runs)
            }
            if (command.sources.draftRevisions.isNotEmpty()) {
                insertArtifactDraftSources(command.sources.draftRevisions)
            }
            if (command.sources.materials.isNotEmpty()) {
                insertArtifactMaterialSources(command.sources.materials)
            }
            RepositoryResult.Success(artifact)
        }
    }

    suspend fun saveOfficialSkillCombination(
        command: SaveOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationSnapshot> {
        return transactions.execute("save_official_skill_combination") {
            validateOfficialCombinationMembers(command.members)
            require(command.members.all { it.combinationId == command.combination.id })
            val invalidId = command.members.firstOrNull {
                !officialSkillIdValidator.isValid(it.officialSkillId)
            }?.officialSkillId
            if (invalidId != null) {
                return@execute RepositoryResult.Failure(
                    RepositoryError.ConstraintViolation(
                        "save_official_skill_combination",
                        "unknown_official_skill_id"
                    )
                )
            }

            transactions.transactionRaw {
                val existing = getOfficialSkillCombination(command.combination.id)
                val requestedMembers = command.members.sortedBy { it.position }
                if (existing == null) {
                    if (command.expectedUpdatedAt != null) {
                        return@transactionRaw RepositoryResult.Failure(
                            RepositoryError.NotFound(
                                "official_skill_combination",
                                command.combination.id
                            )
                        )
                    }
                    insertOfficialSkillCombination(command.combination)
                    if (requestedMembers.isNotEmpty()) {
                        insertOfficialSkillCombinationMembers(requestedMembers)
                    }
                    return@transactionRaw RepositoryResult.Success(
                        OfficialSkillCombinationSnapshot(
                            command.combination,
                            requestedMembers
                        )
                    )
                }

                val storedMembers = getOfficialSkillCombinationMembers(existing.id)
                if (existing == command.combination && storedMembers == requestedMembers) {
                    return@transactionRaw RepositoryResult.Success(
                        OfficialSkillCombinationSnapshot(existing, storedMembers),
                        idempotent = true
                    )
                }
                if (command.expectedUpdatedAt == null) {
                    return@transactionRaw RepositoryResult.Failure(
                        RepositoryError.AlreadyExists(
                            "official_skill_combination",
                            existing.id
                        )
                    )
                }
                if (existing.updatedAt != command.expectedUpdatedAt) {
                    return@transactionRaw RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "save_official_skill_combination",
                            "stale_combination"
                        )
                    )
                }
                if (updateOfficialSkillCombination(command.combination) != 1) {
                    throw IllegalStateException("Combination update failed")
                }
                deleteOfficialSkillCombinationMembers(existing.id)
                if (requestedMembers.isNotEmpty()) {
                    insertOfficialSkillCombinationMembers(requestedMembers)
                }
                RepositoryResult.Success(
                    OfficialSkillCombinationSnapshot(
                        command.combination,
                        requestedMembers
                    )
                )
            }
        }
    }

    suspend fun deleteOfficialSkillCombination(
        command: DeleteOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationEntity> {
        return transactions.transaction("delete_official_skill_combination") {
            require(command.deletedAt > 0L)
            val existing = getOfficialSkillCombination(command.combinationId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound(
                        "official_skill_combination",
                        command.combinationId
                    )
                )
            if (existing.deletedAt != null) {
                return@transaction RepositoryResult.Success(existing, idempotent = true)
            }
            if (existing.updatedAt != command.expectedUpdatedAt) {
                return@transaction RepositoryResult.Failure(
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
            if (updateOfficialSkillCombination(deleted) != 1) {
                throw IllegalStateException("Combination delete failed")
            }
            RepositoryResult.Success(deleted)
        }
    }

    suspend fun getOfficialSkillCombination(
        combinationId: String
    ): RepositoryResult<OfficialSkillCombinationSnapshot> {
        return transactions.transaction("get_official_skill_combination") {
            val combination = getOfficialSkillCombination(combinationId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound(
                        "official_skill_combination",
                        combinationId
                    )
                )
            RepositoryResult.Success(
                OfficialSkillCombinationSnapshot(
                    combination,
                    getOfficialSkillCombinationMembers(combinationId)
                )
            )
        }
    }

    suspend fun listOfficialSkillCombinations(): RepositoryResult<List<OfficialSkillCombinationSnapshot>> {
        return transactions.transaction("list_official_skill_combinations") {
            RepositoryResult.Success(
                getActiveOfficialSkillCombinations().map { combination ->
                    OfficialSkillCombinationSnapshot(
                        combination,
                        getOfficialSkillCombinationMembers(combination.id)
                    )
                }
            )
        }
    }

    private suspend fun JianyuRepositoryDao.validateArtifactRelations(
        artifact: ConfirmedArtifactEntity,
        sources: ArtifactSources
    ): RepositoryError? {
        for (source in sources.messages) {
            val message = getMessage(source.messageId)
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
            val run = getExecutionRun(source.runId)
            if (run == null || run.issueId != artifact.issueId || run.stageId != artifact.stageId) {
                return RepositoryError.ConstraintViolation(
                    "confirm_artifact",
                    "run_source_mismatch"
                )
            }
        }
        for (source in sources.draftRevisions) {
            val revision = getDraftRevision(source.draftRevisionId)
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
            val usage = getMaterialUsage(source.materialUsageSnapshotId)
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

    private suspend fun JianyuRepositoryDao.storedArtifactSources(
        artifactId: String
    ): ArtifactSources {
        return ArtifactSources(
            messages = getArtifactMessageSources(artifactId),
            runs = getArtifactRunSources(artifactId),
            draftRevisions = getArtifactDraftSources(artifactId),
            materials = getArtifactMaterialSources(artifactId)
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
}
