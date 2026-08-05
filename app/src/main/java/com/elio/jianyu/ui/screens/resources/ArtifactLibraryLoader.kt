package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.data.ArtifactSourceRecoverySnapshot
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.listArtifactSourcesForIssue
import com.elio.jianyu.result.ArtifactLibraryAggregator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ArtifactLibraryLoader(
    private val repository: JianyuRepository,
) {
    suspend fun load(): ArtifactLibraryUiState = withContext(Dispatchers.IO) {
        val navigation = repository.listIssueNavigation(
            setOf(IssueLifecycleState.ACTIVE, IssueLifecycleState.ARCHIVED),
        )
        if (navigation is RepositoryResult.Failure) {
            return@withContext ArtifactLibraryUiState.Failure(ARTIFACT_LOAD_FAILED)
        }

        navigation as RepositoryResult.Success
        if (navigation.value.isEmpty()) {
            return@withContext ArtifactLibraryUiState.Empty
        }

        val snapshots = mutableListOf<IssueRecoverySnapshot>()
        val sourcesByIssue = mutableMapOf<String, List<ArtifactSourceRecoverySnapshot>>()
        var recoveryFailureCount = 0
        var sourceFailureCount = 0
        navigation.value.forEach { item ->
            when (val recovered = repository.recoverIssue(item.issue.id)) {
                is RepositoryResult.Success -> {
                    snapshots += recovered.value
                    if (recovered.value.resources.artifacts.isNotEmpty()) {
                        when (val sources = repository.listArtifactSourcesForIssue(item.issue.id)) {
                            is RepositoryResult.Success -> sourcesByIssue[item.issue.id] = sources.value
                            is RepositoryResult.Failure -> {
                                if (!sources.error.isUnsupportedSourceRecovery()) {
                                    sourceFailureCount += 1
                                }
                            }
                        }
                    }
                }
                is RepositoryResult.Failure -> recoveryFailureCount += 1
            }
        }

        if (snapshots.isEmpty() && recoveryFailureCount > 0) {
            return@withContext ArtifactLibraryUiState.Failure(ARTIFACT_LOAD_FAILED)
        }

        val aggregated = ArtifactLibraryAggregator.aggregate(
            snapshots = snapshots,
            sourcesByIssue = sourcesByIssue,
        )
        if (aggregated.items.isEmpty()) {
            return@withContext if (recoveryFailureCount > 0) {
                ArtifactLibraryUiState.Failure(ARTIFACT_LOAD_FAILED)
            } else {
                ArtifactLibraryUiState.Empty
            }
        }

        val content = ArtifactLibraryUiState.Content(aggregated)
        if (recoveryFailureCount > 0 || sourceFailureCount > 0) {
            ArtifactLibraryUiState.PartialFailure(
                content = content,
                errorCode = ARTIFACT_PARTIAL_LOAD_FAILED,
            )
        } else {
            content
        }
    }

    private fun RepositoryError.isUnsupportedSourceRecovery(): Boolean =
        this is RepositoryError.CompatibilityFailure &&
            compatibilityCode == "artifact_source_recovery_not_supported"

    companion object {
        const val ARTIFACT_LOAD_FAILED = "artifact_load_failed"
        const val ARTIFACT_PARTIAL_LOAD_FAILED = "artifact_partial_load_failed"
    }
}
