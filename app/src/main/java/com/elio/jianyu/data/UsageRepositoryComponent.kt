package com.elio.jianyu.data

/**
 * 资料与个人背景使用快照写入组件。
 *
 * 只接受用户已确认的不可变快照，并校验 Issue / Stage / Run 与资料引用关系。
 */
internal class UsageRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions
) {
    suspend fun recordMaterialUsage(
        entity: MaterialUsageSnapshotEntity
    ): RepositoryResult<MaterialUsageSnapshotEntity> {
        return transactions.transaction("record_material_usage") {
            require(entity.userConfirmedAt > 0L)
            val existing = getMaterialUsage(entity.id)
            if (existing != null) {
                return@transaction if (existing == entity) {
                    RepositoryResult.Success(existing, idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.IdempotencyConflict(
                            "record_material_usage",
                            entity.id
                        )
                    )
                }
            }

            val relationError = validateUsageRelations(
                issueId = entity.issueId,
                stageId = entity.stageId,
                runId = entity.runId
            ) ?: validateMaterialReferenceRelation(entity)
            if (relationError != null) {
                return@transaction RepositoryResult.Failure(relationError)
            }

            insertMaterialUsage(entity)
            RepositoryResult.Success(entity)
        }
    }

    suspend fun recordPersonalContextUsage(
        entity: PersonalContextUsageSnapshotEntity
    ): RepositoryResult<PersonalContextUsageSnapshotEntity> {
        return transactions.transaction("record_personal_context_usage") {
            require(entity.userConfirmedAt > 0L)
            val existing = getPersonalContextUsage(entity.id)
            if (existing != null) {
                return@transaction if (existing == entity) {
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
                issueId = entity.issueId,
                stageId = entity.stageId,
                runId = entity.runId
            )
            if (relationError != null) {
                return@transaction RepositoryResult.Failure(relationError)
            }

            insertPersonalContextUsage(entity)
            RepositoryResult.Success(entity)
        }
    }

    private suspend fun JianyuRepositoryDao.validateUsageRelations(
        issueId: String,
        stageId: String,
        runId: String?
    ): RepositoryError? {
        val stage = getStage(stageId)
        if (stage == null || stage.issueId != issueId) {
            return RepositoryError.ConstraintViolation("record_usage", "stage_mismatch")
        }
        if (runId != null) {
            val run = getExecutionRun(runId)
            if (run == null || run.issueId != issueId || run.stageId != stageId) {
                return RepositoryError.ConstraintViolation("record_usage", "run_mismatch")
            }
        }
        return null
    }

    private suspend fun JianyuRepositoryDao.validateMaterialReferenceRelation(
        entity: MaterialUsageSnapshotEntity
    ): RepositoryError? {
        val referenceId = entity.materialReferenceId ?: return null
        val reference = getMaterialReference(referenceId)
            ?: return RepositoryError.ConstraintViolation(
                "record_material_usage",
                "material_reference_missing"
            )
        if (
            reference.issueId != entity.issueId ||
            (reference.stageId != null && reference.stageId != entity.stageId)
        ) {
            return RepositoryError.ConstraintViolation(
                "record_material_usage",
                "material_reference_mismatch"
            )
        }
        return null
    }
}
