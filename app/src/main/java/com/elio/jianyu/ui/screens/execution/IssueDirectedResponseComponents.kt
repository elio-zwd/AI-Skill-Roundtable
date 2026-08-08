package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuMetadataRow

@Composable
internal fun IssueCollaborationWorkspaceSection(
    state: IssueCollaborationUiState,
    contextConfirmed: Boolean,
    onInputChanged: (String) -> Unit,
    onOpenDirected: () -> Unit,
    onOpenCross: () -> Unit,
    onDismissDialog: () -> Unit,
    onToggleParticipant: (String) -> Unit,
    onToggleMessage: (Long) -> Unit,
    onOpenContext: () -> Unit,
    onConfirmDirected: () -> Unit,
    onConfirmCross: () -> Unit,
    onRetryDirected: (String) -> Unit,
    onRetryFailed: (String) -> Unit,
    onSynthesize: (String) -> Unit,
    onRetrySynthesis: (String) -> Unit,
    onStop: (String) -> Unit,
    showComposer: Boolean = true,
) {
    IssueCollaborationSection(
        state = state,
        contextConfirmed = contextConfirmed,
        onInputChanged = onInputChanged,
        onOpenDirected = onOpenDirected,
        onOpenCross = onOpenCross,
        onDismissDialog = onDismissDialog,
        onToggleParticipant = onToggleParticipant,
        onToggleMessage = onToggleMessage,
        onOpenContext = onOpenContext,
        onConfirmDirected = onConfirmDirected,
        onConfirmCross = onConfirmCross,
        onRetryFailed = onRetryFailed,
        onSynthesize = onSynthesize,
        onRetrySynthesis = onRetrySynthesis,
        onStop = onStop,
        showComposer = showComposer,
    )
    val content = state as? IssueCollaborationUiState.Content ?: return
    content.directedRuns.forEach { directed ->
        DirectedResponseStatusCard(
            directed = directed,
            operationInProgress = content.operationInProgress,
            onRetry = onRetryDirected,
        )
    }
}

@Composable
private fun DirectedResponseStatusCard(
    directed: DirectedResponseRunUi,
    operationInProgress: Boolean,
    onRetry: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (directed.canRetry || directed.status == ExecutionRunStatus.FAILED) {
                    JianyuAutomationTags.Collaboration.DIRECTED_FAILURE
                } else {
                    JianyuAutomationTags.Collaboration.directedParticipant(directed.skillId)
                },
            ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("点名回应 · ${directed.status.toDirectedLabel()}", style = MaterialTheme.typography.titleMedium)
            JianyuMetadataRow("点名成员", "${directed.displayName}（${directed.skillId}）")
            JianyuMetadataRow("本次问题", directed.question.ifBlank { "已保存的点名问题" })
            if (directed.hasIncompleteOutput) {
                Text(
                    "已保留不完整输出；重试仍使用原冻结成员与原消息使用快照。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (directed.canRetry) {
                Button(
                    onClick = { onRetry(directed.runId) },
                    enabled = !operationInProgress,
                    modifier = Modifier.testTag(JianyuAutomationTags.Collaboration.DIRECTED_RETRY),
                ) {
                    Text("仍由原成员重试")
                }
            }
        }
    }
}

private fun ExecutionRunStatus.toDirectedLabel(): String = when (this) {
    ExecutionRunStatus.NOT_STARTED -> "等待执行"
    ExecutionRunStatus.RUNNING -> "回应中"
    ExecutionRunStatus.PARTIAL_SUCCESS -> "部分完成"
    ExecutionRunStatus.SUCCEEDED,
    ExecutionRunStatus.COMPLETED -> "已完成"
    ExecutionRunStatus.STOPPED -> "已停止，可重试"
    ExecutionRunStatus.RETRYABLE -> "失败，可重试"
    ExecutionRunStatus.FAILED -> "失败，不可自动重试"
}
