package com.elio.jianyu.data

internal class MaterialContextRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
) {
    suspend fun createMaterial(command: CreateMaterialCommand): RepositoryResult<Material> =
        transactions.transaction("create_material") {
            require(command.id.isNotBlank() && command.issueId.isNotBlank())
            require(command.title.isNotBlank() && command.sourceType.isNotBlank())
            require(command.content.isNotBlank() && command.createdAt > 0L)
            if (getIssue(command.issueId) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", command.issueId),
                )
            }
            command.stageId?.let { stageId ->
                val stage = getStage(stageId)
                if (stage == null || stage.issueId != command.issueId) {
                    return@transaction RepositoryResult.Failure(
                        RepositoryError.NotFound("stage", stageId),
                    )
                }
            }
            val entity = MaterialReferenceEntity(
                id = command.id,
                issueId = command.issueId,
                stageId = command.stageId,
                title = command.title.trim(),
                sourceType = command.sourceType.trim(),
                sourceLocator = command.sourceLocator?.trim()?.takeIf(String::isNotBlank),
                content = ContextContentHasher.normalize(command.content),
                contentHash = ContextContentHasher.hash(command.content),
                sourcePublishedAt = command.sourcePublishedAt,
                sourceCapturedAt = command.sourceCapturedAt,
                createdAt = command.createdAt,
                updatedAt = command.createdAt,
                sensitive = command.sensitive,
            )
            val existing = getMaterialReference(command.id)
            if (existing != null) {
                return@transaction if (existing == entity) {
                    RepositoryResult.Success(existing.toDomain(), idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict("create_material", command.id),
                    )
                }
            }
            insertMaterialReference(entity)
            RepositoryResult.Success(entity.toDomain())
        }

    suspend fun updateMaterial(command: UpdateMaterialCommand): RepositoryResult<Material> =
        transactions.transaction("update_material") {
            require(command.title.isNotBlank() && command.sourceType.isNotBlank())
            require(command.content.isNotBlank() && command.updatedAt > command.expectedUpdatedAt)
            val existing = getMaterialReference(command.id)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("material", command.id),
                )
            if (existing.updatedAt != command.expectedUpdatedAt) {
                return@transaction stale("update_material")
            }
            if (existing.lifecycleState in setOf(
                    ContextSourceLifecycle.DELETED,
                    ContextSourceLifecycle.PURGE_REQUESTED,
                    ContextSourceLifecycle.PURGED,
                )
            ) {
                return@transaction invalidLifecycle("update_material", existing.lifecycleState)
            }
            val updated = existing.copy(
                title = command.title.trim(),
                sourceType = command.sourceType.trim(),
                sourceLocator = command.sourceLocator?.trim()?.takeIf(String::isNotBlank),
                content = ContextContentHasher.normalize(command.content),
                contentHash = ContextContentHasher.hash(command.content),
                sourcePublishedAt = command.sourcePublishedAt,
                sourceCapturedAt = command.sourceCapturedAt,
                sensitive = command.sensitive,
                updatedAt = command.updatedAt,
            )
            if (updateMaterialReference(updated) != 1) return@transaction stale("update_material")
            RepositoryResult.Success(updated.toDomain())
        }

    suspend fun getMaterial(id: String): RepositoryResult<Material> =
        transactions.transaction("get_material") {
            val entity = getMaterialReference(id)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("material", id),
                )
            RepositoryResult.Success(entity.toDomain())
        }

    suspend fun listMaterials(filter: MaterialFilter): RepositoryResult<List<Material>> =
        transactions.transaction("list_materials") {
            val query = filter.query.trim()
            val values = getAllMaterialReferences()
                .asSequence()
                .filter { it.lifecycleState in filter.lifecycles }
                .filter { filter.issueId == null || it.issueId == filter.issueId }
                .filter { filter.stageId == null || it.stageId == filter.stageId }
                .filter { filter.sourceType == null || it.sourceType == filter.sourceType }
                .filter {
                    query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                        it.sourceType.contains(query, ignoreCase = true)
                }
                .map(MaterialReferenceEntity::toDomain)
                .toList()
            RepositoryResult.Success(values)
        }

    suspend fun changeMaterialLifecycle(
        command: ChangeMaterialLifecycleCommand,
    ): RepositoryResult<Material> = transactions.transaction("change_material_lifecycle") {
        val existing = getMaterialReference(command.materialId)
            ?: return@transaction RepositoryResult.Failure(
                RepositoryError.NotFound("material", command.materialId),
            )
        if (existing.lifecycleState == command.target && existing.updatedAt == command.changedAt) {
            return@transaction RepositoryResult.Success(existing.toDomain(), idempotent = true)
        }
        if (existing.updatedAt != command.expectedUpdatedAt) {
            return@transaction stale("change_material_lifecycle")
        }
        val updated = transitionMaterial(existing, command.target, command.changedAt)
            ?: return@transaction invalidLifecycle(
                "change_material_lifecycle",
                existing.lifecycleState,
            )
        if (updated == existing) {
            return@transaction RepositoryResult.Success(existing.toDomain(), idempotent = true)
        }
        if (updateMaterialReference(updated) != 1) {
            return@transaction stale("change_material_lifecycle")
        }
        RepositoryResult.Success(updated.toDomain())
    }

    suspend fun getMaterialPurgeImpact(id: String): RepositoryResult<ContextPurgeImpact> =
        transactions.transaction("get_material_purge_impact") {
            if (getMaterialReference(id) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("material", id),
                )
            }
            val usages = getMaterialUsagesForSource(id)
            RepositoryResult.Success(
                ContextPurgeImpact(
                    sourceType = ContextSourceType.MATERIAL,
                    sourceId = id,
                    issueCount = usages.map { it.issueId }.distinct().size,
                    stageCount = usages.map { it.stageId }.distinct().size,
                    usageSnapshotCount = usages.size,
                    runCount = usages.mapNotNull { it.runId }.distinct().size,
                ),
            )
        }

    suspend fun purgeMaterial(command: PurgeMaterialCommand): RepositoryResult<Material> =
        transactions.transaction("purge_material") {
            require(command.confirmedAt > 0L)
            val existing = getMaterialReference(command.materialId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("material", command.materialId),
                )
            if (existing.lifecycleState == ContextSourceLifecycle.PURGED &&
                existing.purgedAt == command.confirmedAt
            ) {
                return@transaction RepositoryResult.Success(existing.toDomain(), idempotent = true)
            }
            if (existing.updatedAt != command.expectedUpdatedAt) {
                return@transaction stale("purge_material")
            }
            if (existing.lifecycleState != ContextSourceLifecycle.PURGE_REQUESTED) {
                return@transaction invalidLifecycle("purge_material", existing.lifecycleState)
            }
            val purged = existing.copy(
                title = "",
                sourceType = "",
                sourceLocator = null,
                content = "",
                contentHash = "",
                sourcePublishedAt = null,
                sourceCapturedAt = null,
                lifecycleState = ContextSourceLifecycle.PURGED,
                sensitive = false,
                updatedAt = command.confirmedAt,
                purgedAt = command.confirmedAt,
            )
            purgeMaterialUsageSnapshots(existing.id)
            if (updateMaterialReference(purged) != 1) return@transaction stale("purge_material")
            RepositoryResult.Success(purged.toDomain())
        }

    suspend fun createPersonalContext(
        command: CreatePersonalContextCommand,
    ): RepositoryResult<PersonalContext> = transactions.transaction("create_personal_context") {
        require(command.id.isNotBlank() && command.title.isNotBlank())
        require(command.content.isNotBlank() && command.createdAt > 0L)
        val entity = PersonalContextEntryEntity(
            id = command.id,
            title = command.title.trim(),
            content = ContextContentHasher.normalize(command.content),
            contentHash = ContextContentHasher.hash(command.content),
            sensitive = command.sensitive,
            createdAt = command.createdAt,
            updatedAt = command.createdAt,
        )
        val existing = getPersonalContextEntry(command.id)
        if (existing != null) {
            return@transaction if (existing == entity) {
                RepositoryResult.Success(existing.toDomain(), idempotent = true)
            } else {
                RepositoryResult.Failure(
                    RepositoryError.IdempotencyConflict("create_personal_context", command.id),
                )
            }
        }
        insertPersonalContextEntry(entity)
        RepositoryResult.Success(entity.toDomain())
    }

    suspend fun updatePersonalContext(
        command: UpdatePersonalContextCommand,
    ): RepositoryResult<PersonalContext> = transactions.transaction("update_personal_context") {
        require(command.title.isNotBlank() && command.content.isNotBlank())
        require(command.updatedAt > command.expectedUpdatedAt)
        val existing = getPersonalContextEntry(command.id)
            ?: return@transaction RepositoryResult.Failure(
                RepositoryError.NotFound("personal_context", command.id),
            )
        if (existing.updatedAt != command.expectedUpdatedAt) {
            return@transaction stale("update_personal_context")
        }
        if (existing.lifecycleState in setOf(
                ContextSourceLifecycle.DELETED,
                ContextSourceLifecycle.PURGE_REQUESTED,
                ContextSourceLifecycle.PURGED,
            )
        ) {
            return@transaction invalidLifecycle("update_personal_context", existing.lifecycleState)
        }
        val updated = existing.copy(
            title = command.title.trim(),
            content = ContextContentHasher.normalize(command.content),
            contentHash = ContextContentHasher.hash(command.content),
            sensitive = command.sensitive,
            updatedAt = command.updatedAt,
        )
        if (updatePersonalContextEntry(updated) != 1) {
            return@transaction stale("update_personal_context")
        }
        RepositoryResult.Success(updated.toDomain())
    }

    suspend fun getPersonalContext(id: String): RepositoryResult<PersonalContext> =
        transactions.transaction("get_personal_context") {
            val entity = getPersonalContextEntry(id)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("personal_context", id),
                )
            RepositoryResult.Success(entity.toDomain())
        }

    suspend fun listPersonalContexts(
        filter: PersonalContextFilter,
    ): RepositoryResult<List<PersonalContext>> = transactions.transaction("list_personal_contexts") {
        val query = filter.query.trim()
        val values = getAllPersonalContextEntries()
            .asSequence()
            .filter { it.lifecycleState in filter.lifecycles }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .map(PersonalContextEntryEntity::toDomain)
            .toList()
        RepositoryResult.Success(values)
    }

    suspend fun changePersonalContextLifecycle(
        command: ChangePersonalContextLifecycleCommand,
    ): RepositoryResult<PersonalContext> =
        transactions.transaction("change_personal_context_lifecycle") {
            val existing = getPersonalContextEntry(command.personalContextId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("personal_context", command.personalContextId),
                )
            if (existing.lifecycleState == command.target && existing.updatedAt == command.changedAt) {
                return@transaction RepositoryResult.Success(existing.toDomain(), idempotent = true)
            }
            if (existing.updatedAt != command.expectedUpdatedAt) {
                return@transaction stale("change_personal_context_lifecycle")
            }
            val updated = transitionPersonalContext(existing, command.target, command.changedAt)
                ?: return@transaction invalidLifecycle(
                    "change_personal_context_lifecycle",
                    existing.lifecycleState,
                )
            if (updated == existing) {
                return@transaction RepositoryResult.Success(existing.toDomain(), idempotent = true)
            }
            if (updatePersonalContextEntry(updated) != 1) {
                return@transaction stale("change_personal_context_lifecycle")
            }
            RepositoryResult.Success(updated.toDomain())
        }

    suspend fun getPersonalContextPurgeImpact(
        id: String,
    ): RepositoryResult<ContextPurgeImpact> =
        transactions.transaction("get_personal_context_purge_impact") {
            if (getPersonalContextEntry(id) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("personal_context", id),
                )
            }
            val usages = getPersonalContextUsagesForSource(id)
            RepositoryResult.Success(
                ContextPurgeImpact(
                    sourceType = ContextSourceType.PERSONAL_CONTEXT,
                    sourceId = id,
                    issueCount = usages.map { it.issueId }.distinct().size,
                    stageCount = usages.map { it.stageId }.distinct().size,
                    usageSnapshotCount = usages.size,
                    runCount = usages.mapNotNull { it.runId }.distinct().size,
                ),
            )
        }

    suspend fun purgePersonalContext(
        command: PurgePersonalContextCommand,
    ): RepositoryResult<PersonalContext> = transactions.transaction("purge_personal_context") {
        require(command.confirmedAt > 0L)
        val existing = getPersonalContextEntry(command.personalContextId)
            ?: return@transaction RepositoryResult.Failure(
                RepositoryError.NotFound("personal_context", command.personalContextId),
            )
        if (existing.lifecycleState == ContextSourceLifecycle.PURGED &&
            existing.purgedAt == command.confirmedAt
        ) {
            return@transaction RepositoryResult.Success(existing.toDomain(), idempotent = true)
        }
        if (existing.updatedAt != command.expectedUpdatedAt) {
            return@transaction stale("purge_personal_context")
        }
        if (existing.lifecycleState != ContextSourceLifecycle.PURGE_REQUESTED) {
            return@transaction invalidLifecycle("purge_personal_context", existing.lifecycleState)
        }
        val purged = existing.copy(
            title = "",
            content = "",
            contentHash = "",
            isEnabled = false,
            lifecycleState = ContextSourceLifecycle.PURGED,
            sensitive = false,
            updatedAt = command.confirmedAt,
            purgedAt = command.confirmedAt,
        )
        purgePersonalContextUsageSnapshots(existing.id)
        if (updatePersonalContextEntry(purged) != 1) {
            return@transaction stale("purge_personal_context")
        }
        RepositoryResult.Success(purged.toDomain())
    }

    suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand,
    ): RepositoryResult<PreparedExecutionContext> =
        transactions.transaction("prepare_execution_context") {
            require(command.preparedAt > 0L)
            val draft = command.draft
            if (!draft.confirmed) {
                return@transaction validationFailure(ContextValidationError.CONFIRMATION_REQUIRED)
            }
            val stage = getStage(draft.stageId)
            if (stage == null || stage.issueId != draft.issueId) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("stage", draft.stageId),
                )
            }
            val verified = mutableListOf<ConfirmedContextItem>()
            for (item in draft.items) {
                when (item.sourceType) {
                    ContextSourceType.MATERIAL -> {
                        val source = getMaterialReference(item.sourceId)
                            ?: return@transaction validationFailure(
                                ContextValidationError.SOURCE_NOT_FOUND,
                            )
                        lifecycleError(source.lifecycleState)?.let {
                            return@transaction validationFailure(it)
                        }
                        if (source.issueId != draft.issueId ||
                            (source.stageId != null && source.stageId != draft.stageId)
                        ) {
                            return@transaction validationFailure(
                                ContextValidationError.SOURCE_NOT_FOUND,
                            )
                        }
                        if (source.updatedAt != item.expectedSourceUpdatedAt ||
                            source.contentHash != item.expectedSourceHash
                        ) {
                            return@transaction validationFailure(ContextValidationError.SOURCE_STALE)
                        }
                        if (!ContextContentHasher.normalize(source.content)
                                .contains(ContextContentHasher.normalize(item.content))
                        ) {
                            return@transaction validationFailure(
                                ContextValidationError.CONTENT_HASH_MISMATCH,
                            )
                        }
                        verified += item.copy(
                            title = source.title,
                            sourceKind = source.sourceType,
                            sourceLocator = source.sourceLocator,
                            sourcePublishedAt = source.sourcePublishedAt,
                            sourceCapturedAt = source.sourceCapturedAt,
                            sensitive = source.sensitive,
                        )
                    }
                    ContextSourceType.PERSONAL_CONTEXT -> {
                        val source = getPersonalContextEntry(item.sourceId)
                            ?: return@transaction validationFailure(
                                ContextValidationError.SOURCE_NOT_FOUND,
                            )
                        lifecycleError(source.lifecycleState)?.let {
                            return@transaction validationFailure(it)
                        }
                        if (source.updatedAt != item.expectedSourceUpdatedAt ||
                            source.contentHash != item.expectedSourceHash
                        ) {
                            return@transaction validationFailure(ContextValidationError.SOURCE_STALE)
                        }
                        if (!ContextContentHasher.normalize(source.content)
                                .contains(ContextContentHasher.normalize(item.content))
                        ) {
                            return@transaction validationFailure(
                                ContextValidationError.CONTENT_HASH_MISMATCH,
                            )
                        }
                        verified += item.copy(title = source.title, sensitive = source.sensitive)
                    }
                }
            }
            when (val validated = ContextSelectionValidator.validate(
                baseContextCharacters = draft.baseContextCharacters,
                items = verified,
                maxContextCharacters = command.maxContextCharacters,
            )) {
                is ContextPreparationResult.Invalid -> {
                    return@transaction validationFailure(validated.errors.first())
                }
                is ContextPreparationResult.Ready -> {
                    val usage = createUsageWriteSet(draft, validated.items, command.preparedAt)
                    RepositoryResult.Success(PreparedExecutionContext(validated, usage))
                }
            }
        }

    suspend fun listRunContextUsage(
        runId: String,
    ): RepositoryResult<List<ContextUsageSnapshot>> =
        transactions.transaction("list_run_context_usage") {
            if (getExecutionRun(runId) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("execution_run", runId),
                )
            }
            val materials = getMaterialUsagesForRun(runId).map { usage ->
                ContextUsageSnapshot(
                    sourceType = ContextSourceType.MATERIAL,
                    sourceId = usage.materialReferenceId,
                    title = usage.titleSnapshot.takeIf(String::isNotBlank),
                    content = usage.contentSnapshot,
                    contentHash = usage.contentHash.takeIf(String::isNotBlank),
                    contentState = usage.contentState,
                    userConfirmedAt = usage.userConfirmedAt,
                    usedAt = usage.createdAt,
                    networkAllowed = usage.networkAllowed,
                    sensitive = usage.sensitive,
                )
            }
            val personal = getPersonalContextUsagesForRun(runId).map { usage ->
                ContextUsageSnapshot(
                    sourceType = ContextSourceType.PERSONAL_CONTEXT,
                    sourceId = usage.personalContextEntryId,
                    title = usage.titleSnapshot.takeIf(String::isNotBlank),
                    content = usage.contentSnapshot,
                    contentHash = usage.contentHash.takeIf(String::isNotBlank),
                    contentState = usage.contentState,
                    userConfirmedAt = usage.userConfirmedAt,
                    usedAt = usage.createdAt,
                    networkAllowed = usage.networkAllowed,
                    sensitive = usage.sensitive,
                )
            }
            RepositoryResult.Success(
                (materials + personal).sortedWith(
                    compareBy({ it.userConfirmedAt }, { it.sourceType.storageValue }, { it.sourceId }),
                ),
            )
        }

    private fun createUsageWriteSet(
        draft: ContextSelectionDraft,
        items: List<ConfirmedContextItem>,
        preparedAt: Long,
    ): ContextUsageWriteSet = ContextUsageWriteSet(
        materials = items.filter { it.sourceType == ContextSourceType.MATERIAL }.map { item ->
            MaterialUsageSnapshotEntity(
                id = "${draft.runId}:material:${item.sourceId}",
                issueId = draft.issueId,
                stageId = draft.stageId,
                runId = draft.runId,
                materialReferenceId = item.sourceId,
                titleSnapshot = item.title,
                sourceTypeSnapshot = item.sourceKind,
                sourceLocatorSnapshot = item.sourceLocator,
                contentSnapshot = ContextContentHasher.normalize(item.content),
                contentHash = ContextContentHasher.hash(item.content),
                sourcePublishedAtSnapshot = item.sourcePublishedAt,
                sourceCapturedAtSnapshot = item.sourceCapturedAt,
                userConfirmedAt = item.userConfirmedAt,
                createdAt = preparedAt,
                networkAllowed = item.networkAllowed,
                sensitive = item.sensitive,
            )
        },
        personalContexts = items
            .filter { it.sourceType == ContextSourceType.PERSONAL_CONTEXT }
            .map { item ->
                PersonalContextUsageSnapshotEntity(
                    id = "${draft.runId}:personal:${item.sourceId}",
                    issueId = draft.issueId,
                    stageId = draft.stageId,
                    runId = draft.runId,
                    personalContextEntryId = item.sourceId,
                    titleSnapshot = item.title,
                    contentSnapshot = ContextContentHasher.normalize(item.content),
                    contentHash = ContextContentHasher.hash(item.content),
                    userConfirmedAt = item.userConfirmedAt,
                    createdAt = preparedAt,
                    networkAllowed = item.networkAllowed,
                    sensitive = item.sensitive,
                )
            },
        sourceExpectations = items.map { item ->
            ContextSourceExpectation(
                sourceType = item.sourceType,
                sourceId = item.sourceId,
                expectedUpdatedAt = item.expectedSourceUpdatedAt,
                expectedContentHash = item.expectedSourceHash,
            )
        },
    ).sorted()

    private fun lifecycleError(state: ContextSourceLifecycle): ContextValidationError? = when (state) {
        ContextSourceLifecycle.ACTIVE -> null
        ContextSourceLifecycle.DISABLED -> ContextValidationError.SOURCE_DISABLED
        ContextSourceLifecycle.ARCHIVED -> ContextValidationError.SOURCE_ARCHIVED
        ContextSourceLifecycle.DELETED -> ContextValidationError.SOURCE_DELETED
        ContextSourceLifecycle.PURGE_REQUESTED,
        ContextSourceLifecycle.PURGED -> ContextValidationError.SOURCE_PURGED
    }

    private fun transitionMaterial(
        existing: MaterialReferenceEntity,
        target: ContextSourceLifecycle,
        changedAt: Long,
    ): MaterialReferenceEntity? {
        require(changedAt > 0L)
        if (existing.lifecycleState == target) return existing
        if (!transitionAllowed(existing.lifecycleState, target)) return null
        return existing.copy(
            lifecycleState = target,
            disabledAt = changedAt.takeIf { target == ContextSourceLifecycle.DISABLED },
            archivedAt = changedAt.takeIf { target == ContextSourceLifecycle.ARCHIVED },
            deletedAt = changedAt.takeIf { target == ContextSourceLifecycle.DELETED },
            purgeRequestedAt = changedAt.takeIf { target == ContextSourceLifecycle.PURGE_REQUESTED },
            updatedAt = changedAt,
        )
    }

    private fun transitionPersonalContext(
        existing: PersonalContextEntryEntity,
        target: ContextSourceLifecycle,
        changedAt: Long,
    ): PersonalContextEntryEntity? {
        require(changedAt > 0L)
        if (existing.lifecycleState == target) return existing
        if (!transitionAllowed(existing.lifecycleState, target)) return null
        return existing.copy(
            isEnabled = target == ContextSourceLifecycle.ACTIVE,
            lifecycleState = target,
            disabledAt = changedAt.takeIf { target == ContextSourceLifecycle.DISABLED },
            archivedAt = changedAt.takeIf { target == ContextSourceLifecycle.ARCHIVED },
            deletedAt = changedAt.takeIf { target == ContextSourceLifecycle.DELETED },
            purgeRequestedAt = changedAt.takeIf { target == ContextSourceLifecycle.PURGE_REQUESTED },
            updatedAt = changedAt,
        )
    }

    private fun transitionAllowed(
        current: ContextSourceLifecycle,
        target: ContextSourceLifecycle,
    ): Boolean = when (current) {
        ContextSourceLifecycle.ACTIVE -> target in setOf(
            ContextSourceLifecycle.DISABLED,
            ContextSourceLifecycle.ARCHIVED,
            ContextSourceLifecycle.DELETED,
        )
        ContextSourceLifecycle.DISABLED -> target in setOf(
            ContextSourceLifecycle.ACTIVE,
            ContextSourceLifecycle.ARCHIVED,
            ContextSourceLifecycle.DELETED,
        )
        ContextSourceLifecycle.ARCHIVED -> target in setOf(
            ContextSourceLifecycle.ACTIVE,
            ContextSourceLifecycle.DELETED,
        )
        ContextSourceLifecycle.DELETED -> target in setOf(
            ContextSourceLifecycle.ACTIVE,
            ContextSourceLifecycle.PURGE_REQUESTED,
        )
        ContextSourceLifecycle.PURGE_REQUESTED -> target == ContextSourceLifecycle.DELETED
        ContextSourceLifecycle.PURGED -> false
    }

    private fun invalidLifecycle(
        operation: String,
        state: ContextSourceLifecycle,
    ): RepositoryResult.Failure = RepositoryResult.Failure(
        RepositoryError.InvalidState(operation, "source_${state.storageValue}"),
    )

    private fun stale(operation: String): RepositoryResult.Failure = RepositoryResult.Failure(
        RepositoryError.InvalidState(operation, ContextValidationError.SOURCE_STALE.code),
    )

    private fun validationFailure(error: ContextValidationError): RepositoryResult.Failure =
        RepositoryResult.Failure(
            RepositoryError.ConstraintViolation("prepare_execution_context", error.code),
        )
}
