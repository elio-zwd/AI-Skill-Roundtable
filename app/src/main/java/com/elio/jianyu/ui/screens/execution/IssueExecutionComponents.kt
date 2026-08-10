package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.data.ExecutionParticipantStatus
import dev.jeziellago.compose.markdowntext.MarkdownText

object IssueExecutionTestTags {
    const val SCREEN = "issue_execution_screen"
    const val LOADING = "issue_execution_loading"
    const val FAILURE = "issue_execution_failure"
    const val STATUS = "issue_execution_status"
    const val STOP = "issue_execution_stop"
    const val RETRY = "issue_execution_retry"
    const val RECOVER = "issue_execution_recover"
    const val CONTEXT = "issue_execution_context"

    fun participant(snapshotId: String): String = "issue_execution_participant_$snapshotId"
}

@Composable
internal fun ExecutionStatusCard(
    state: IssueExecutionUiState.Content,
) {
    val statusColor = state.phase.toStatusColor()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IssueExecutionTestTags.STATUS),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = statusColor.copy(alpha = 0.12f),
                contentColor = statusColor,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = state.phase.toDisplayLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            state.budget?.let { budget ->
                Text(
                    text = "调用额度：已用 ${budget.usedApiCalls} / ${budget.maxApiCalls}，剩余 ${budget.remainingApiCalls}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.failureMessage?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!state.executionAvailable) {
                Text(
                    text = "官方 Skill 目录未能加载，当前工作区保持只读，不会调用模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun ContextSelectionSummaryCard(
    state: IssueExecutionUiState.Content,
    onOpenContext: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IssueExecutionTestTags.CONTEXT),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "执行上下文", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "资料和个人背景默认不发送。执行或重试前可查看精确摘录与授权范围。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.contextConfirmation?.let { confirmation ->
                Text(
                    text = "已选 ${confirmation.selectedItems.size} 项 · 预计 ${confirmation.totalCharacters} / 24000 字符",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onOpenContext,
                enabled = !state.operationInProgress,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("添加或查看上下文")
            }
        }
    }
}

@Composable
internal fun ExecutionParticipantCard(
    participant: IssueExecutionParticipantUi,
) {
    val statusColor = participant.status.toStatusColor()
    val avatarLabel = participant.displayName.trim().take(1).ifBlank {
        "${participant.position + 1}"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IssueExecutionTestTags.participant(participant.snapshotId))
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    text = avatarLabel,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = participant.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Skill 回应 · 第 ${participant.attemptCount} 次尝试",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        contentColor = statusColor,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = participant.status.toDisplayLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        participant.text?.takeIf(String::isNotBlank)?.let { text ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                MarkdownText(
                    markdown = text,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                )
            }
        }
        if (participant.isPending) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "正在流式生成",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (participant.hasIncompleteOutput) {
            Text(
                text = "内容未完整生成，已保留用于恢复和审计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        participant.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun IssueExecutionPhase.toDisplayLabel(): String = when (this) {
    IssueExecutionPhase.IDLE -> "尚未开始"
    IssueExecutionPhase.READY -> "准备就绪"
    IssueExecutionPhase.RUNNING -> "执行中"
    IssueExecutionPhase.PARTIAL_SUCCESS -> "已有部分结果，继续执行中"
    IssueExecutionPhase.SUCCEEDED -> "执行完成"
    IssueExecutionPhase.RETRYABLE -> "存在可重试成员"
    IssueExecutionPhase.STOPPED -> "已停止"
    IssueExecutionPhase.FAILED -> "执行失败"
    IssueExecutionPhase.RECOVERING -> "正在恢复"
    IssueExecutionPhase.NO_API_KEY -> "未配置 API Key"
    IssueExecutionPhase.OFFLINE -> "网络不可用"
    IssueExecutionPhase.RATE_LIMITED -> "请求受限"
    IssueExecutionPhase.BUDGET_EXHAUSTED -> "调用预算已用完"
}

@Composable
private fun IssueExecutionPhase.toStatusColor() = when (this) {
    IssueExecutionPhase.SUCCEEDED -> MaterialTheme.colorScheme.secondary
    IssueExecutionPhase.RUNNING,
    IssueExecutionPhase.PARTIAL_SUCCESS,
    IssueExecutionPhase.RECOVERING -> MaterialTheme.colorScheme.primary
    IssueExecutionPhase.RETRYABLE,
    IssueExecutionPhase.NO_API_KEY,
    IssueExecutionPhase.OFFLINE,
    IssueExecutionPhase.RATE_LIMITED,
    IssueExecutionPhase.BUDGET_EXHAUSTED -> MaterialTheme.colorScheme.tertiary
    IssueExecutionPhase.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun ExecutionParticipantStatus.toDisplayLabel(): String = when (this) {
    ExecutionParticipantStatus.QUEUED -> "排队中"
    ExecutionParticipantStatus.RUNNING -> "准备请求"
    ExecutionParticipantStatus.STREAMING -> "生成中"
    ExecutionParticipantStatus.SUCCEEDED -> "已完成"
    ExecutionParticipantStatus.FAILED -> "失败"
    ExecutionParticipantStatus.TIMED_OUT -> "超时"
    ExecutionParticipantStatus.STOPPED -> "已停止"
    ExecutionParticipantStatus.RETRYABLE -> "可重试"
}

@Composable
private fun ExecutionParticipantStatus.toStatusColor() = when (this) {
    ExecutionParticipantStatus.SUCCEEDED -> MaterialTheme.colorScheme.secondary
    ExecutionParticipantStatus.RUNNING,
    ExecutionParticipantStatus.STREAMING -> MaterialTheme.colorScheme.primary
    ExecutionParticipantStatus.FAILED,
    ExecutionParticipantStatus.TIMED_OUT -> MaterialTheme.colorScheme.error
    ExecutionParticipantStatus.RETRYABLE -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
