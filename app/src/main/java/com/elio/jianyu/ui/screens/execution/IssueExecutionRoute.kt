package com.elio.jianyu.ui.screens.execution

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.result.StageResultService
import com.elio.jianyu.ui.screens.result.StageResultCallbacks
import com.elio.jianyu.ui.screens.result.StageResultViewModel
import com.elio.jianyu.ui.screens.result.stageResultViewModelFactory

@Composable
fun IssueExecutionRoute(
    repository: JianyuRepository,
    coordinator: ExecutionRunCoordinator?,
    collaborationCoordinator: IssueCollaborationCoordinator?,
    stageResultService: StageResultService,
    issueId: String?,
    stageId: String?,
    onBack: () -> Unit,
    viewModel: IssueExecutionViewModel = composeViewModel(
        factory = IssueExecutionViewModel.factory(repository, coordinator),
    ),
    collaborationViewModel: IssueCollaborationViewModel = composeViewModel(
        factory = IssueCollaborationViewModel.factory(collaborationCoordinator),
    ),
    stageResultViewModel: StageResultViewModel? = if (issueId != null && stageId != null) {
        composeViewModel(
            key = "stage-result-$issueId-$stageId",
            factory = stageResultViewModelFactory(stageResultService, issueId, stageId),
        )
    } else {
        null
    },
) {
    val state by viewModel.state.collectAsState()
    val collaborationState by collaborationViewModel.state.collectAsState()
    val stageResultState = stageResultViewModel?.state?.collectAsState()?.value
    LaunchedEffect(issueId, stageId) {
        viewModel.load(issueId, stageId)
        collaborationViewModel.load(issueId, stageId)
        stageResultViewModel?.load()
    }
    IssueExecutionScreen(
        state = state,
        collaborationState = collaborationState,
        stageResultState = stageResultState,
        stageResultCallbacks = stageResultViewModel?.let { resultViewModel ->
            StageResultCallbacks(
                onRetry = resultViewModel::load,
                onToggleMessage = resultViewModel::toggleMessage,
                onCreateGenericDraft = resultViewModel::createGenericDraft,
                onCreateDraftFromMessages = resultViewModel::createDraftFromSelectedMessages,
                onContentChange = resultViewModel::updateContentText,
                onSave = resultViewModel::saveNow,
                onReloadConflict = resultViewModel::reloadConflict,
                onRequestAbandon = resultViewModel::requestAbandon,
                onDismissAbandon = resultViewModel::dismissAbandon,
                onConfirmAbandon = resultViewModel::confirmAbandon,
                onRequestArtifactConfirmation = resultViewModel::requestArtifactConfirmation,
                onDismissArtifactConfirmation = resultViewModel::dismissArtifactConfirmation,
                onArtifactTitleChange = resultViewModel::updateArtifactTitle,
                onArtifactTypeChange = resultViewModel::updateArtifactType,
                onConfirmArtifact = resultViewModel::confirmArtifact,
                onCreateRevision = resultViewModel::createRevision,
            )
        } ?: StageResultCallbacks.Empty,
        onBack = onBack,
        onReload = {
            viewModel.load(issueId, stageId)
            collaborationViewModel.load(issueId, stageId)
            stageResultViewModel?.load()
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
        onRetryDirected = collaborationViewModel::retryDirected,
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
