package com.elio.jianyu.ui.screens.execution

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.execution.ExecutionRunCoordinator

@Composable
fun IssueExecutionRoute(
    repository: JianyuRepository,
    coordinator: ExecutionRunCoordinator?,
    collaborationCoordinator: IssueCollaborationCoordinator?,
    issueId: String?,
    stageId: String?,
    onBack: () -> Unit,
    viewModel: IssueExecutionViewModel = viewModel(
        factory = IssueExecutionViewModel.factory(repository, coordinator),
    ),
    collaborationViewModel: IssueCollaborationViewModel = viewModel(
        factory = IssueCollaborationViewModel.factory(collaborationCoordinator),
    ),
) {
    val state by viewModel.state.collectAsState()
    val collaborationState by collaborationViewModel.state.collectAsState()
    LaunchedEffect(issueId, stageId) {
        viewModel.load(issueId, stageId)
        collaborationViewModel.load(issueId, stageId)
    }
    IssueExecutionScreen(
        state = state,
        collaborationState = collaborationState,
        onBack = onBack,
        onReload = {
            viewModel.load(issueId, stageId)
            collaborationViewModel.load(issueId, stageId)
        },
        onStop = viewModel::stop,
        onRetry = viewModel::retryFailedParticipants,
        onRecoverInterrupted = viewModel::recoverInterrupted,
        onOpenContext = { viewModel.openContextSelection(retryMode = false) },
        onDismissContext = viewModel::dismissContextSelection,
        onToggleContext = viewModel::toggleContextCandidate,
        onContextNetworkAllowed = viewModel::setContextNetworkAllowed,
        onSensitiveContextConfirmed = viewModel::setSensitiveContextConfirmed,
        onContextExcerptChanged = viewModel::updateContextExcerpt,
        onConfirmContext = viewModel::confirmContextSelection,
        onCollaborationInputChanged = collaborationViewModel::updateInput,
        onOpenDirected = collaborationViewModel::openDirected,
        onOpenCross = collaborationViewModel::openCross,
        onDismissCollaborationDialog = collaborationViewModel::dismissDialog,
        onToggleCollaborationParticipant = collaborationViewModel::toggleParticipant,
        onToggleCollaborationMessage = collaborationViewModel::toggleMessage,
        onConfirmDirected = {
            collaborationViewModel.confirmDirected(viewModel.peekPreparedContextForStart())
        },
        onConfirmCross = {
            collaborationViewModel.confirmCross(viewModel.peekPreparedContextForStart())
        },
        onRetryCrossFailed = collaborationViewModel::retryFailed,
        onSynthesizeCross = { sessionId ->
            collaborationViewModel.synthesize(
                sessionId,
                viewModel.peekPreparedContextForStart(),
            )
        },
        onRetryCrossSynthesis = collaborationViewModel::retrySynthesis,
        onStopCross = collaborationViewModel::stop,
    )
}
