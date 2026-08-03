package com.elio.jianyu.ui.screens.issues

import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueNavigationItem
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.ui.navigation.JianyuNavigationRoutes

internal interface IssueNavigationReader {
    suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState>,
    ): RepositoryResult<List<IssueNavigationItem>>

    suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot>
}

internal class JianyuIssueNavigationReader(
    private val repository: JianyuRepository,
) : IssueNavigationReader {
    override suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState>,
    ): RepositoryResult<List<IssueNavigationItem>> =
        repository.listIssueNavigation(states)

    override suspend fun recoverIssue(
        issueId: String,
    ): RepositoryResult<IssueRecoverySnapshot> =
        repository.recoverIssue(issueId)
}

internal class IssuesNavigationLoader(
    private val reader: IssueNavigationReader,
) {
    suspend fun load(): IssuesUiState = when (
        val result = reader.listIssueNavigation(IssueLifecycleState.entries.toSet())
    ) {
        is RepositoryResult.Success -> mapIssueNavigation(result.value)
        is RepositoryResult.Failure -> IssuesUiState.Failure(
            repositoryErrorMessage(result.error),
        )
    }

    suspend fun recover(
        issueId: String?,
        stageId: String?,
    ): IssueRecoveryUiState {
        if (!JianyuNavigationRoutes.isStableId(issueId)) {
            return IssueRecoveryUiState.Failure("无效的议题 ID")
        }
        if (stageId != null && !JianyuNavigationRoutes.isStableId(stageId)) {
            return IssueRecoveryUiState.Failure("无效的阶段 ID")
        }

        return when (val result = reader.recoverIssue(issueId.orEmpty())) {
            is RepositoryResult.Success -> mapIssueRecovery(result.value, stageId)
            is RepositoryResult.Failure -> IssueRecoveryUiState.Failure(
                repositoryErrorMessage(result.error),
            )
        }
    }
}
