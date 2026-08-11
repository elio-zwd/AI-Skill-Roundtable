package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.execution.SearchMode
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.screens.context.ContextConfirmationDialog
import com.elio.jianyu.ui.screens.result.StageDraftResultPanel
import com.elio.jianyu.ui.screens.result.StageResultCallbacks
import com.elio.jianyu.ui.screens.result.StageResultUiState

@Composable
fun IssueExecutionScreen(
    state: IssueExecutionUiState,
    collaborationState: IssueCollaborationUiState = IssueCollaborationUiState.Loading,
    stageResultState: StageResultUiState? = null,
    stageResultCallbacks: StageResultCallbacks = StageResultCallbacks.Empty,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onRecoverInterrupted: () -> Unit,
    onSearchModeChanged: (SearchMode) -> Unit = {},
    onThinkingOverrideChanged: (IssueThinkingPolicy?) -> Unit = {},
    onIssueDefaultThinkingPolicyChanged: (IssueThinkingPolicy) -> Unit = {},
    onOpenRunHistoryDetail: (String) -> Unit = {},
    onDismissRunHistoryDetail: () -> Unit = {},
    onOpenContext: () -> Unit = {},
    onDismissContext: () -> Unit = {},
    onToggleContext: (ContextSourceType, String) -> Unit = { _, _ -> },
    onContextNetworkAllowed: (ContextSourceType, String, Boolean) -> Unit = { _, _, _ -> },
    onSensitiveContextConfirmed: (ContextSourceType, String, Boolean) -> Unit = { _, _, _ -> },
    onContextExcerptChanged: (ContextSourceType, String, String) -> Unit = { _, _, _ -> },
    onConfirmContext: () -> Unit = {},
    onCollaborationInputChanged: (String) -> Unit = {},
    onSubmitStandard: () -> Unit = {},
    onOpenDirected: () -> Unit = {},
    onOpenCross: () -> Unit = {},
    onDismissCollaborationDialog: () -> Unit = {},
    onToggleCollaborationParticipant: (String) -> Unit = {},
    onToggleCollaborationMessage: (Long) -> Unit = {},
    onConfirmDirected: () -> Unit = {},
    onConfirmCross: () -> Unit = {},
    onRetryDirected: (String) -> Unit = {},
    onRetryCrossFailed: (String) -> Unit = {},
    onSynthesizeCross: (String) -> Unit = {},
    onRetryCrossSynthesis: (String) -> Unit = {},
    onStopCross: (String) -> Unit = {},
) {
    val contentState = state as? IssueExecutionUiState.Content
    val collaborationContent = collaborationState as? IssueCollaborationUiState.Content

    Scaffold(
        modifier = Modifier.testTag(IssueExecutionTestTags.SCREEN),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            IssueExecutionTopBar(
                title = contentState?.issueTitle ?: "议题工作区",
                skillCount = contentState?.participants?.size ?: 0,
                onBack = onBack,
            )
        },
        bottomBar = {
            collaborationContent?.let { workspace ->
                WorkspaceComposer(
                    state = workspace,
                    onInputChanged = onCollaborationInputChanged,
                    onSubmitStandard = onSubmitStandard,
                    onOpenDirected = onOpenDirected,
                    onOpenCross = onOpenCross,
                    executionInProgress = contentState?.operationInProgress == true,
                )
            }
        },
    ) { paddingValues ->
        when (state) {
            IssueExecutionUiState.Loading -> LoadingExecutionContent(paddingValues)
            is IssueExecutionUiState.Failure -> FailureExecutionContent(
                state = state,
                paddingValues = paddingValues,
                onReload = onReload,
            )
            is IssueExecutionUiState.Content -> IssueExecutionContent(
                state = state,
                collaborationState = collaborationState,
                stageResultState = stageResultState,
                stageResultCallbacks = stageResultCallbacks,
                paddingValues = paddingValues,
                onStop = onStop,
                onRetry = onRetry,
                onRecoverInterrupted = onRecoverInterrupted,
                onSearchModeChanged = onSearchModeChanged,
                onThinkingOverrideChanged = onThinkingOverrideChanged,
                onIssueDefaultThinkingPolicyChanged = onIssueDefaultThinkingPolicyChanged,
                onOpenRunHistoryDetail = onOpenRunHistoryDetail,
                onDismissRunHistoryDetail = onDismissRunHistoryDetail,
                onOpenContext = onOpenContext,
                onCollaborationInputChanged = onCollaborationInputChanged,
                onOpenDirected = onOpenDirected,
                onOpenCross = onOpenCross,
                onDismissCollaborationDialog = onDismissCollaborationDialog,
                onToggleCollaborationParticipant = onToggleCollaborationParticipant,
                onToggleCollaborationMessage = onToggleCollaborationMessage,
                onConfirmDirected = onConfirmDirected,
                onConfirmCross = onConfirmCross,
                onRetryDirected = onRetryDirected,
                onRetryCrossFailed = onRetryCrossFailed,
                onSynthesizeCross = onSynthesizeCross,
                onRetryCrossSynthesis = onRetryCrossSynthesis,
                onStopCross = onStopCross,
            )
        }
    }

    val confirmation = contentState?.contextConfirmation
    if (confirmation != null) {
        ContextConfirmationDialog(
            state = confirmation,
            onDismiss = onDismissContext,
            onToggleSelected = onToggleContext,
            onNetworkAllowed = onContextNetworkAllowed,
            onSensitiveConfirmed = onSensitiveContextConfirmed,
            onExcerptChanged = onContextExcerptChanged,
            onConfirm = onConfirmContext,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun IssueExecutionTopBar(
    title: String,
    skillCount: Int,
    onBack: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回议题",
                        )
                    }
                },
                actions = {
                    if (skillCount > 0) {
                        Text(
                            text = "$skillCount 位 Skill",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun LoadingExecutionContent(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp)
            .testTag(IssueExecutionTestTags.LOADING),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("正在从数据库恢复议题、阶段与执行状态")
        Text(
            text = "恢复页面只读取持久化事实，不会自动调用模型。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailureExecutionContent(
    state: IssueExecutionUiState.Failure,
    paddingValues: PaddingValues,
    onReload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(paddingValues)
            .padding(16.dp),
    ) {
        JianyuStateCard(
            title = state.title,
            message = state.message,
            actionLabel = "重新读取",
            onAction = onReload,
            modifier = Modifier.testTag(IssueExecutionTestTags.FAILURE),
        )
    }
}

@Composable
private fun IssueExecutionContent(
    state: IssueExecutionUiState.Content,
    collaborationState: IssueCollaborationUiState,
    stageResultState: StageResultUiState?,
    stageResultCallbacks: StageResultCallbacks,
    paddingValues: PaddingValues,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onRecoverInterrupted: () -> Unit,
    onSearchModeChanged: (SearchMode) -> Unit,
    onThinkingOverrideChanged: (IssueThinkingPolicy?) -> Unit,
    onIssueDefaultThinkingPolicyChanged: (IssueThinkingPolicy) -> Unit,
    onOpenRunHistoryDetail: (String) -> Unit,
    onDismissRunHistoryDetail: () -> Unit,
    onOpenContext: () -> Unit,
    onCollaborationInputChanged: (String) -> Unit,
    onOpenDirected: () -> Unit,
    onOpenCross: () -> Unit,
    onDismissCollaborationDialog: () -> Unit,
    onToggleCollaborationParticipant: (String) -> Unit,
    onToggleCollaborationMessage: (Long) -> Unit,
    onConfirmDirected: () -> Unit,
    onConfirmCross: () -> Unit,
    onRetryDirected: (String) -> Unit,
    onRetryCrossFailed: (String) -> Unit,
    onSynthesizeCross: (String) -> Unit,
    onRetryCrossSynthesis: (String) -> Unit,
    onStopCross: (String) -> Unit,
) {
    val currentRunIsCollaboration = collaborationState.isCollaborationRun(state.runId)
    val canConfigureNextRun = !state.operationInProgress && state.runStatus !in setOf(
        ExecutionRunStatus.NOT_STARTED,
        ExecutionRunStatus.RUNNING,
        ExecutionRunStatus.PARTIAL_SUCCESS,
    )
    LazyColumn(
        modifier = Modifier
            .padding(paddingValues)
            .testTag(JianyuAutomationTags.Execution.CONTENT_LIST),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ExecutionStatusCard(state)
                if (state.canStop || state.canRetry || state.canRecoverInterrupted) {
                    ExecutionRunActions(
                        state = state,
                        onStop = onStop,
                        onRetry = onRetry,
                        onRecoverInterrupted = onRecoverInterrupted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            ExecutionRunConfigurationCard(
                searchMode = state.searchMode,
                defaultPolicy = state.issueDefaultThinkingPolicy,
                overridePolicy = state.thinkingOverride,
                canChangeDefault = state.canChangeIssueDefaultThinkingPolicy,
                canConfigureNextRun = canConfigureNextRun,
                onDefaultChanged = onIssueDefaultThinkingPolicyChanged,
                onOverrideChanged = onThinkingOverrideChanged,
                onSearchModeChanged = onSearchModeChanged,
            )
        }

        item { ContextSelectionSummaryCard(state = state, onOpenContext = onOpenContext) }

        if (state.participants.isNotEmpty() || state.runId != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("本轮输出", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "每位 Skill 的回应与执行状态会在这里保留。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Execution.PARTICIPANTS),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.participants.forEach { participant ->
                    ExecutionParticipantCard(participant)
                }
                if (state.participants.isEmpty() && state.runId != null) {
                    JianyuStateCard(
                        title = "没有参与者运行快照",
                        message = "当前 Run 无法安全执行，请返回后重新选择 Skill。",
                    )
                }
            }
        }

        if (state.runHistory.isNotEmpty()) {
            item {
                ExecutionRunHistorySection(
                    runs = state.runHistory,
                    detail = state.runDetail,
                    onOpenDetail = onOpenRunHistoryDetail,
                    onDismissDetail = onDismissRunHistoryDetail,
                )
            }
        }

        item {
            IssueCollaborationWorkspaceSection(
                state = collaborationState,
                contextConfirmed = state.contextConfirmation?.confirmedForStart == true,
                onInputChanged = onCollaborationInputChanged,
                onOpenDirected = onOpenDirected,
                onOpenCross = onOpenCross,
                onDismissDialog = onDismissCollaborationDialog,
                onToggleParticipant = onToggleCollaborationParticipant,
                onToggleMessage = onToggleCollaborationMessage,
                onOpenContext = onOpenContext,
                onConfirmDirected = onConfirmDirected,
                onConfirmCross = onConfirmCross,
                onRetryDirected = onRetryDirected,
                onRetryFailed = onRetryCrossFailed,
                onSynthesize = onSynthesizeCross,
                onRetrySynthesis = onRetryCrossSynthesis,
                onStop = onStopCross,
                showComposer = false,
            )
        }

        stageResultState?.let { resultState ->
            item {
                StageDraftResultPanel(
                    state = resultState,
                    callbacks = stageResultCallbacks,
                )
            }
        }

        if (state.runId == null) {
            item {
                JianyuStateCard(
                    title = "尚未开始执行",
                    message = "先确认资料与个人背景，再选择点名回应或交叉讨论。",
                )
            }
        }
        if (state.contextConfirmation?.confirmedForStart == true) {
            item {
                JianyuStateCard(
                    title = "上下文已确认",
                    message = "只有最终确认协作后才会创建 Run 和调用网络。",
                )
            }
        }
        if (state.canRecoverInterrupted && !currentRunIsCollaboration) {
            item {
                JianyuStateCard(
                    title = "运行可能被中断",
                    message = "恢复只收敛数据库状态，不会自动重发请求。",
                )
            }
        }
        if (state.operationInProgress) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("正在持久化操作结果")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ExecutionRunActions(
    state: IssueExecutionUiState.Content,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onRecoverInterrupted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.canStop) {
            TextButton(
                onClick = onStop,
                enabled = true,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(IssueExecutionTestTags.STOP),
            ) {
                Text("停止")
            }
        }
        if (state.canRecoverInterrupted) {
            TextButton(
                onClick = onRecoverInterrupted,
                enabled = !state.operationInProgress,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(IssueExecutionTestTags.RECOVER),
            ) {
                Text("收敛中断状态")
            }
        }
        if (state.canRetry) {
            TextButton(
                onClick = onRetry,
                enabled = !state.operationInProgress,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(IssueExecutionTestTags.RETRY),
            ) {
                Text("查看上下文并重试")
            }
        }
    }
}

private fun IssueCollaborationUiState.isCollaborationRun(runId: String?): Boolean {
    if (runId == null) return false
    val content = this as? IssueCollaborationUiState.Content ?: return false
    return content.directedRuns.any { it.runId == runId } ||
        content.sessions.any { session ->
            session.responseRunId == runId || session.synthesisRunId == runId
        }
}
