package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.result.StageResultService
import com.elio.jianyu.ui.screens.result.StageDraftSaveStatus
import com.elio.jianyu.ui.screens.result.StageResultCallbacks
import com.elio.jianyu.ui.screens.result.StageResultUiState
import com.elio.jianyu.ui.screens.result.StageResultViewModel
import com.elio.jianyu.ui.screens.result.stageResultViewModelFactory
import kotlinx.coroutines.flow.collectLatest

@Composable
fun IssueExecutionRoute(
    repository: JianyuRepository,
    coordinator: ExecutionRunCoordinator?,
    collaborationCoordinator: IssueCollaborationCoordinator?,
    stageResultService: StageResultService,
    issueId: String?,
    stageId: String?,
    onBack: () -> Unit,
    onOpenStage: (String, String) -> Unit = { _, _ -> },
    viewModel: IssueExecutionViewModel = composeViewModel(
        factory = IssueExecutionViewModel.factory(repository, coordinator),
    ),
    collaborationViewModel: IssueCollaborationViewModel = composeViewModel(
        factory = IssueCollaborationViewModel.factory(collaborationCoordinator),
    ),
    advanceIssueViewModel: AdvanceIssueViewModel = composeViewModel(
        key = "advance-issue-${issueId.orEmpty()}",
        factory = AdvanceIssueViewModel.factory(repository),
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
    val advanceIssueState by advanceIssueViewModel.state.collectAsState()
    val stageResultState = stageResultViewModel?.state?.collectAsState()?.value
    var unsavedChoiceVisible by remember { mutableStateOf(false) }
    var pendingDraftAction by remember { mutableStateOf<PendingDraftAction?>(null) }

    LaunchedEffect(issueId, stageId) {
        viewModel.load(issueId, stageId)
        collaborationViewModel.load(issueId, stageId)
        advanceIssueViewModel.load(issueId, stageId)
        stageResultViewModel?.load()
    }
    LaunchedEffect(advanceIssueViewModel) {
        advanceIssueViewModel.events.collectLatest { event ->
            when (event) {
                is AdvanceIssueEvent.NavigateToStage -> {
                    onOpenStage(event.issueId, event.stageId)
                }
                AdvanceIssueEvent.RequestStopCurrentRun -> viewModel.stop()
            }
        }
    }
    LaunchedEffect(advanceIssueState, state) {
        val stopping = advanceIssueState is AdvanceIssueUiState.StoppingCurrentRun
        val execution = state as? IssueExecutionUiState.Content
        val terminal = execution?.runStatus !in setOf(
            ExecutionRunStatus.NOT_STARTED,
            ExecutionRunStatus.RUNNING,
        )
        if (stopping && execution != null && terminal && !execution.operationInProgress) {
            advanceIssueViewModel.onStopFinished()
        }
    }
    LaunchedEffect(stageResultState, pendingDraftAction) {
        val content = stageResultState as? StageResultUiState.Content ?: return@LaunchedEffect
        when (pendingDraftAction) {
            PendingDraftAction.SAVE -> {
                if (
                    content.editorContent == content.persistedContent &&
                    content.saveStatus is StageDraftSaveStatus.Saved
                ) {
                    pendingDraftAction = null
                    advanceIssueViewModel.open()
                }
            }
            PendingDraftAction.DISCARD -> {
                if (content.editorContent == content.persistedContent) {
                    pendingDraftAction = null
                    advanceIssueViewModel.open()
                }
            }
            null -> Unit
        }
    }

    val stageResultCallbacks = stageResultViewModel?.let { resultViewModel ->
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
    } ?: StageResultCallbacks.Empty

    val callbacks = AdvanceIssueCallbacks(
        onCancel = advanceIssueViewModel::close,
        onToggleDirection = advanceIssueViewModel::toggleDirection,
        onContinueFromDirection = advanceIssueViewModel::continueFromDirection,
        onBackToDirection = advanceIssueViewModel::backToDirection,
        onToggleMeasure = advanceIssueViewModel::toggleMeasure,
        onObjectiveChanged = advanceIssueViewModel::updateObjective,
        onExpectedOutputChanged = advanceIssueViewModel::updateExpectedOutput,
        onToggleMaterial = advanceIssueViewModel::toggleMaterial,
        onToggleArtifact = advanceIssueViewModel::toggleArtifact,
        onContinueToSummary = advanceIssueViewModel::continueToSummary,
        onBackToMeasures = advanceIssueViewModel::backToMeasures,
        onConfirm = advanceIssueViewModel::confirm,
        onWaitForRun = advanceIssueViewModel::waitForRun,
        onStopCurrentRun = advanceIssueViewModel::requestStopCurrentRun,
        onDismissUndo = advanceIssueViewModel::dismissUndo,
        onConfirmUndo = advanceIssueViewModel::confirmUndo,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        StageTimeline(
            candidates = advanceIssueState.candidatesOrNull(),
            onOpenStage = onOpenStage,
            onAdvanceIssue = {
                val content = stageResultState as? StageResultUiState.Content
                if (content != null && content.editorContent != content.persistedContent) {
                    unsavedChoiceVisible = true
                } else {
                    advanceIssueViewModel.open()
                }
            },
            onUndoStage = advanceIssueViewModel::requestUndo,
        )
        IssueExecutionScreen(
            state = state,
            collaborationState = collaborationState,
            stageResultState = stageResultState,
            stageResultCallbacks = stageResultCallbacks,
            onBack = onBack,
            onReload = {
                viewModel.load(issueId, stageId)
                collaborationViewModel.load(issueId, stageId)
                advanceIssueViewModel.load(issueId, stageId)
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
            modifier = Modifier.weight(1f),
        )
    }

    AdvanceIssueFlow(advanceIssueState, callbacks)

    if (unsavedChoiceVisible) {
        AlertDialog(
            onDismissRequest = { unsavedChoiceVisible = false },
            title = { Text("存在未保存的草稿修改") },
            text = { Text("推进不会静默丢失编辑器正文，也不会自动确认草稿。") },
            confirmButton = {
                Button(
                    onClick = {
                        unsavedChoiceVisible = false
                        pendingDraftAction = PendingDraftAction.SAVE
                        stageResultViewModel?.saveNow()
                    },
                ) { Text("保存草稿后继续") }
            },
            dismissButton = {
                Column {
                    TextButton(
                        onClick = {
                            unsavedChoiceVisible = false
                            pendingDraftAction = PendingDraftAction.DISCARD
                            stageResultViewModel?.load()
                        },
                    ) { Text("放弃未保存修改后继续") }
                    TextButton(
                        onClick = { unsavedChoiceVisible = false },
                    ) { Text("返回编辑") }
                }
            },
        )
    }
}

private enum class PendingDraftAction {
    SAVE,
    DISCARD,
}

private fun AdvanceIssueUiState.candidatesOrNull(): AdvanceIssueCandidates? = when (this) {
    is AdvanceIssueUiState.DirectionStep -> candidates
    is AdvanceIssueUiState.MeasureStep -> candidates
    is AdvanceIssueUiState.SummaryStep -> candidates
    is AdvanceIssueUiState.WaitingForRun -> candidates
    is AdvanceIssueUiState.StoppingCurrentRun -> candidates
    is AdvanceIssueUiState.CreatingStage -> candidates
    is AdvanceIssueUiState.CreateFailure -> candidates
    is AdvanceIssueUiState.IdempotencyConflict -> candidates
    is AdvanceIssueUiState.UndoAvailable -> candidates
    is AdvanceIssueUiState.Undoing -> candidates
    is AdvanceIssueUiState.UndoFailure -> candidates
    is AdvanceIssueUiState.RestoredDraft -> candidates
    else -> null
}
