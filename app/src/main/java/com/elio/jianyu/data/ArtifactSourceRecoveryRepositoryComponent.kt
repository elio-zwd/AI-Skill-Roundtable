package com.elio.jianyu.data

/** 复用既有来源表的只读恢复组件，不引入新的持久化模型。 */
internal class ArtifactSourceRecoveryRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions,
) {
    suspend fun listArtifactSourcesForIssue(
        issueId: String,
    ): RepositoryResult<List<ArtifactSourceRecoverySnapshot>> {
        return transactions.transaction("list_artifact_sources_for_issue") {
            if (getIssue(issueId) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", issueId),
                )
            }
            val snapshots = getArtifactsForIssue(issueId).map { artifact ->
                ArtifactSourceRecoverySnapshot(
                    artifactId = artifact.id,
                    messages = getArtifactMessageSources(artifact.id),
                    runs = getArtifactRunSources(artifact.id),
                    draftRevisions = getArtifactDraftSources(artifact.id),
                    materials = getArtifactMaterialSources(artifact.id),
                )
            }
            RepositoryResult.Success(snapshots)
        }
    }
}
