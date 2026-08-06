package com.elio.jianyu.data

internal class StageAdvancementRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
    private val officialSkillIdValidator: OfficialSkillIdValidator,
) {
    suspend fun advanceIssue(
        command: AdvanceIssueCommand,
    ): RepositoryResult<AdvanceIssueResult> {
        val normalized = try {
            StageAdvancementPolicy.normalize(command)
        } catch (_: IllegalArgumentException) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation("advance_issue", "invalid_command"),
            )
        }
        val payloadHash = StageAdvancementPayloadHasher.hash(normalized)
        return transactions.stageAdvancementTransaction("advance_issue") {
            val existing = getAdvancementByOperationId(normalized.operationId)
            if (existing != null) {
                return@stageAdvancementTransaction if (existing.payloadHash == payloadHash) {
                    RepositoryResult.Success(
                        AdvanceIssueResult(requireSnapshot(existing.stageId)),
                        idempotent = true,
                    )
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict(
                            operation = "advance_issue",
                            stableId = normalized.operationId,
                        ),
                    )
                }
            }

            if (getIssue(normalized.issueId) == null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", normalized.issueId),
                )
            }
            val sourceStage = getStage(normalized.sourceStageId)
            if (sourceStage == null || sourceStage.issueId != normalized.issueId) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", normalized.sourceStageId),
                )
            }
            if (getLatestStage(normalized.issueId)?.id != normalized.sourceStageId) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("advance_issue", "source_stage_not_current"),
                )
            }
            if (getStage(normalized.newStageId) != null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.AlreadyExists("stage", normalized.newStageId),
                )
            }
            if (
                countBlockingRuns(normalized.issueId, normalized.sourceStageId) > 0 ||
                countBlockingDiscussions(normalized.issueId, normalized.sourceStageId) > 0 ||
                countPendingMessages(normalized.issueId, normalized.sourceStageId) > 0
            ) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("advance_issue", "source_stage_still_running"),
                )
            }
            if (normalized.roster.isEmpty()) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.ConstraintViolation("advance_issue", "roster_required"),
                )
            }
            val rosterError = validateRoster(normalized)
            if (rosterError != null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(rosterError)
            }
            val materialError = validateMaterials(normalized)
            if (materialError != null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(materialError)
            }
            val artifactError = validateArtifacts(normalized)
            if (artifactError != null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(artifactError)
            }

            val now = normalized.confirmedAt
            val stage = StageEntity(
                id = normalized.newStageId,
                issueId = normalized.issueId,
                sequenceIndex = (getMaxStageSequence(normalized.issueId) ?: -1) + 1,
                title = normalized.newStageTitle,
                objective = normalized.objective,
                createdAt = now,
                updatedAt = now,
            )
            val advancement = StageAdvancementEntity(
                stageId = stage.id,
                issueId = stage.issueId,
                sourceStageId = normalized.sourceStageId,
                operationId = normalized.operationId,
                payloadHash = payloadHash,
                realitySupport = normalized.realitySupport,
                thinkingExpansion = normalized.thinkingExpansion,
                objective = normalized.objective,
                expectedOutput = normalized.expectedOutput,
                confirmedAt = now,
                createdAt = now,
            )
            val measures = normalized.measures.mapIndexed { index, measure ->
                StageAdvancementMeasureEntity(
                    stageId = stage.id,
                    issueId = stage.issueId,
                    measure = measure,
                    position = index,
                )
            }
            val roster = normalized.roster.map { member ->
                StageAdvancementSkillMemberEntity(
                    stageId = stage.id,
                    issueId = stage.issueId,
                    officialSkillId = member.officialSkillId,
                    position = member.position,
                    responsibility = member.responsibility,
                    sourceRunId = member.sourceRunId,
                    sourceParticipantSnapshotId = member.sourceParticipantSnapshotId,
                    catalogVersionBasis = member.catalogVersionBasis,
                    confirmedAt = now,
                )
            }
            val materials = normalized.inheritedMaterialIds.mapIndexed { index, materialId ->
                StageAdvancementMaterialEntity(
                    stageId = stage.id,
                    issueId = stage.issueId,
                    materialReferenceId = materialId,
                    position = index,
                    inheritedAt = now,
                )
            }
            val artifacts = normalized.inheritedArtifactIds.mapIndexed { index, artifactId ->
                StageAdvancementArtifactEntity(
                    stageId = stage.id,
                    issueId = stage.issueId,
                    artifactId = artifactId,
                    position = index,
                    inheritedAt = now,
                )
            }

            insertStage(stage)
            insertAdvancement(advancement)
            if (measures.isNotEmpty()) insertMeasures(measures)
            insertSkillMembers(roster)
            if (materials.isNotEmpty()) insertMaterials(materials)
            if (artifacts.isNotEmpty()) insertArtifacts(artifacts)

            RepositoryResult.Success(
                AdvanceIssueResult(
                    StageAdvancementSnapshot(
                        stage = stage,
                        advancement = advancement,
                        measures = measures,
                        roster = roster,
                        materials = materials,
                        artifacts = artifacts,
                    ),
                ),
            )
        }
    }

    suspend fun getStageAdvancement(
        stageId: String,
    ): RepositoryResult<StageAdvancementSnapshot> =
        transactions.stageAdvancementTransaction("get_stage_advancement") {
            val root = getAdvancement(stageId)
                ?: return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage_advancement", stageId),
                )
            RepositoryResult.Success(requireSnapshot(root.stageId))
        }

    suspend fun listStageAdvancements(
        issueId: String,
    ): RepositoryResult<List<StageAdvancementSnapshot>> =
        transactions.stageAdvancementTransaction("list_stage_advancements") {
            if (getIssue(issueId) == null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", issueId),
                )
            }
            RepositoryResult.Success(
                getAdvancementsForIssue(issueId).map { requireSnapshot(it.stageId) },
            )
        }

    suspend fun undoLatestUnrunStage(
        issueId: String,
        stageId: String,
    ): RepositoryResult<Unit> =
        transactions.stageAdvancementTransaction("undo_latest_stage") {
            if (getIssue(issueId) == null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", issueId),
                )
            }
            val stage = getStage(stageId)
            if (stage == null) {
                return@stageAdvancementTransaction RepositoryResult.Success(
                    Unit,
                    idempotent = true,
                )
            }
            if (stage.issueId != issueId || stage.sequenceIndex <= 0) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("undo_latest_stage", "not_undoable"),
                )
            }
            if (getLatestStage(issueId)?.id != stageId) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("undo_latest_stage", "not_latest"),
                )
            }
            if (getAdvancement(stageId) == null) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("undo_latest_stage", "missing_advancement"),
                )
            }
            val dependencyCount = countRuns(issueId, stageId) +
                countMessages(issueId, stageId) +
                countDrafts(issueId, stageId) +
                countDraftRevisions(issueId, stageId) +
                countArtifacts(issueId, stageId) +
                countMaterialReferences(issueId, stageId) +
                countMaterialUsages(issueId, stageId) +
                countPersonalContextUsages(issueId, stageId) +
                countAudioAssets(issueId, stageId) +
                countDiscussions(issueId, stageId) +
                countMessageUsages(issueId, stageId)
            if (dependencyCount != 0) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("undo_latest_stage", "stage_has_dependencies"),
                )
            }

            deleteMeasures(stageId)
            deleteSkillMembers(stageId)
            deleteMaterials(stageId)
            deleteArtifacts(stageId)
            if (deleteAdvancement(issueId, stageId) != 1 || deleteStage(issueId, stageId) != 1) {
                return@stageAdvancementTransaction RepositoryResult.Failure(
                    RepositoryError.StorageFailure("undo_latest_stage", retryable = true),
                )
            }
            RepositoryResult.Success(Unit)
        }

    private suspend fun StageAdvancementDao.validateRoster(
        command: AdvanceIssueCommand,
    ): RepositoryError? {
        for (member in command.roster) {
            if (!officialSkillIdValidator.isValid(member.officialSkillId)) {
                return RepositoryError.ConstraintViolation(
                    "advance_issue",
                    "official_skill_not_available",
                )
            }
            val sourceRunId = member.sourceRunId
            val sourceSnapshotId = member.sourceParticipantSnapshotId
            if ((sourceRunId == null) != (sourceSnapshotId == null)) {
                return RepositoryError.ConstraintViolation(
                    "advance_issue",
                    "roster_source_incomplete",
                )
            }
            if (sourceRunId != null && sourceSnapshotId != null) {
                val sourceRun = getRun(sourceRunId)
                val sourceSnapshot = getParticipantSnapshot(sourceSnapshotId)
                if (
                    sourceRun == null ||
                    sourceRun.issueId != command.issueId ||
                    sourceRun.runKind != ExecutionRunKind.STANDARD ||
                    sourceRun.retryOfRunId != null ||
                    sourceRun.parentRunId != null ||
                    sourceSnapshot == null ||
                    sourceSnapshot.runId != sourceRunId ||
                    sourceSnapshot.sourceId != member.officialSkillId
                ) {
                    return RepositoryError.ConstraintViolation(
                        "advance_issue",
                        "roster_source_invalid",
                    )
                }
            } else if (member.catalogVersionBasis.isNullOrBlank()) {
                return RepositoryError.ConstraintViolation(
                    "advance_issue",
                    "roster_catalog_basis_required",
                )
            }
        }
        return null
    }

    private suspend fun StageAdvancementDao.validateMaterials(
        command: AdvanceIssueCommand,
    ): RepositoryError? {
        for (materialId in command.inheritedMaterialIds) {
            val material = getMaterial(materialId)
            if (
                material == null ||
                material.issueId != command.issueId ||
                material.lifecycleState != ContextSourceLifecycle.ACTIVE ||
                material.purgedAt != null
            ) {
                return RepositoryError.ConstraintViolation(
                    "advance_issue",
                    "inherited_material_invalid",
                )
            }
        }
        return null
    }

    private suspend fun StageAdvancementDao.validateArtifacts(
        command: AdvanceIssueCommand,
    ): RepositoryError? {
        for (artifactId in command.inheritedArtifactIds) {
            val artifact = getArtifact(artifactId)
            if (artifact == null || artifact.issueId != command.issueId) {
                return RepositoryError.ConstraintViolation(
                    "advance_issue",
                    "inherited_artifact_invalid",
                )
            }
        }
        return null
    }

    private suspend fun StageAdvancementDao.requireSnapshot(
        stageId: String,
    ): StageAdvancementSnapshot {
        val stage = requireNotNull(getStage(stageId))
        val root = requireNotNull(getAdvancement(stageId))
        return StageAdvancementSnapshot(
            stage = stage,
            advancement = root,
            measures = getMeasures(stageId),
            roster = getSkillMembers(stageId),
            materials = getMaterials(stageId),
            artifacts = getArtifacts(stageId),
        )
    }
}
