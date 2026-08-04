package com.elio.jianyu.ui.screens.execution

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.execution.ExecutionRunCoordinator

@Composable
fun IssueExecutionRoute(
    repository: JianyuRepository,
    coordinator: ExecutionRunCoordinator?,
    issueId: String?,
    stageId: String?,
    onBack: () -> Unit,
    viewModel: IssueExecutionViewModel = viewModel(
        factory = IssueExecutionViewModel.factory(repository, coordinator),
    ),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(issueId, stageId) {
        viewModel.load(issueId, stageId)
    }
    IssueExecutionScreen(
        state = state,
        onBack = onBack,
        onReload = { viewModel.load(issueId, stageId) },
        onStop = viewModel::stop,
        onRetry = viewModel::retryFailedParticipants,
        onRecoverInterrupted = viewModel::recoverInterrupted,
    )
}
