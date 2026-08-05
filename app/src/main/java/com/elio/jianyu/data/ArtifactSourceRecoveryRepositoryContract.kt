package com.elio.jianyu.data

/** 单个已确认成果的完整、只读来源关系。 */
data class ArtifactSourceRecoverySnapshot(
    val artifactId: String,
    val messages: List<ArtifactMessageSourceEntity>,
    val runs: List<ArtifactRunSourceEntity>,
    val draftRevisions: List<ArtifactDraftSourceEntity>,
    val materials: List<ArtifactMaterialSourceEntity>,
)

internal interface JianyuArtifactSourceRecoveryRepository {
    suspend fun listArtifactSourcesForIssue(
        issueId: String,
    ): RepositoryResult<List<ArtifactSourceRecoverySnapshot>>
}

/**
 * 读取已确认成果的持久化来源关系。
 *
 * 该能力只读取 Room 既有表，不调用网络、不创建 Run，也不修改任何执行或协作状态。
 */
suspend fun JianyuRepository.listArtifactSourcesForIssue(
    issueId: String,
): RepositoryResult<List<ArtifactSourceRecoverySnapshot>> =
    artifactSourceRecoveryCapability()?.listArtifactSourcesForIssue(issueId)
        ?: RepositoryResult.Failure(
            RepositoryError.CompatibilityFailure(
                operation = "list_artifact_sources_for_issue",
                compatibilityCode = "artifact_source_recovery_not_supported",
            ),
        )

private fun JianyuRepository.artifactSourceRecoveryCapability():
    JianyuArtifactSourceRecoveryRepository? =
    this as? JianyuArtifactSourceRecoveryRepository
