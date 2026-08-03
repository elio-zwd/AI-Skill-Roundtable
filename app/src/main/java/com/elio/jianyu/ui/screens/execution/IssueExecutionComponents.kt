package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.ui.components.JianyuMetadataRow

object IssueExecutionTestTags {
    const val SCREEN = "issue_execution_screen"
    const val LOADING = "issue_execution_loading"
    const val FAILURE = "issue_execution_failure"
    const val STATUS = "issue_execution_status"
    const val STOP = "issue_execution_stop"
    const val RETRY = "issue_execution_retry"
    const val RECOVER = "issue_execution_recover"

    fun participant(snapshotId: String): String = "issue_execution_participant_$snapshotId"
}

@Composable
internal fun ExecutionStatusCard(
    state: IssueExecutionUiState.Content,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IssueExecutionTestTags.STATUS),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = state.phase.toDisplayLabel(),
                style = MaterialTheme.typography.titleMedium,
            )
            JianyuMetadataRow("Run", state.runId ?: "尚未创建")
            JianyuMetadataRow("阶段", state.stageTitle ?: "尚无阶段")
            state.budget?.let { budget ->
                JianyuMetadataRow(
                    label = "调用预算",
                    value = "已用 ${budget.usedApiCalls} / ${budget.maxApiCalls}，剩余 ${budget.remainingApiCalls}",
                )
            }
            state.failureMessage?.takeIf(String::isNotBlank)?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!state.executionAvailable) {
                Text(
                    text = "官方 Skill 目录未能加载，当前工作区保持只读，不会调用模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun ExecutionParticipantCard(
    participant: IssueExecutionParticipantUi,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IssueExecutionTestTags.participant(participant.snapshotId)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${participant.position + 1}. ${participant.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = participant.status.toDisplayLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = participant.status.toStatusColor(),
                )
            }
            JianyuMetadataRow("尝试次数", participant.attemptCount.toString())
            participant.text?.takeIf(String::isNotBlank)?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (participant.isPending) {
                Text(
                    text = "正在流式生成…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (participant.hasIncompleteOutput) {
                Text(
                    text = "以上内容未完整生成，已保留用于恢复和审计。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
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
    ExecutionParticipantStatus.SUCCEEDED -> MaterialTheme.colorScheme.primary
    ExecutionParticipantStatus.RUNNING,
    ExecutionParticipantStatus.STREAMING -> MaterialTheme.colorScheme.tertiary
    ExecutionParticipantStatus.FAILED,
    ExecutionParticipantStatus.TIMED_OUT -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
