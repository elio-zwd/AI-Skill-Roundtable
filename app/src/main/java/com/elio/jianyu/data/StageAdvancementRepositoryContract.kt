package com.elio.jianyu.data

internal interface JianyuStageAdvancementRepository {
    suspend fun advanceIssue(
        command: AdvanceIssueCommand,
    ): RepositoryResult<AdvanceIssueResult>

    suspend fun getStageAdvancement(
        stageId: String,
    ): RepositoryResult<StageAdvancementSnapshot>

    suspend fun listStageAdvancements(
        issueId: String,
    ): RepositoryResult<List<StageAdvancementSnapshot>>
}

suspend fun JianyuRepository.advanceIssue(
    command: AdvanceIssueCommand,
): RepositoryResult<AdvanceIssueResult> =
    stageAdvancementCapability()?.advanceIssue(command)
        ?: RepositoryResult.Failure(
            RepositoryError.CompatibilityFailure(
                operation = "advance_issue",
                compatibilityCode = "stage_advancement_not_supported",
            ),
        )

suspend fun JianyuRepository.getStageAdvancement(
    stageId: String,
): RepositoryResult<StageAdvancementSnapshot> =
    stageAdvancementCapability()?.getStageAdvancement(stageId)
        ?: RepositoryResult.Failure(
            RepositoryError.CompatibilityFailure(
                operation = "get_stage_advancement",
                compatibilityCode = "stage_advancement_not_supported",
            ),
        )

suspend fun JianyuRepository.listStageAdvancements(
    issueId: String,
): RepositoryResult<List<StageAdvancementSnapshot>> =
    stageAdvancementCapability()?.listStageAdvancements(issueId)
        ?: RepositoryResult.Failure(
            RepositoryError.CompatibilityFailure(
                operation = "list_stage_advancements",
                compatibilityCode = "stage_advancement_not_supported",
            ),
        )

private fun JianyuRepository.stageAdvancementCapability(): JianyuStageAdvancementRepository? =
    this as? JianyuStageAdvancementRepository
