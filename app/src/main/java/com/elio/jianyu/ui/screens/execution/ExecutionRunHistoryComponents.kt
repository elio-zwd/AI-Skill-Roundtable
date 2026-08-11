package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.ui.automation.JianyuAutomationTags

/**
 * 阶段内 Run 的只读记录区。详情来自已持久化快照，不会触发模型调用或恢复操作。
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ExecutionRunHistorySection(
    runs: List<IssueExecutionRunHistoryUi>,
    detail: IssueExecutionRunDetailUiState?,
    onOpenDetail: (String) -> Unit,
    onDismissDetail: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.Execution.RUN_HISTORY),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("本阶段运行记录", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "每条记录固定展示实际模型、思考强度与来源；查看详情不会修改历史 Run。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            runs.forEach { run ->
                ExecutionRunHistoryCard(run = run, onOpenDetail = onOpenDetail)
            }
            detail?.let { currentDetail ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExecutionRunHistoryDetail(
                    detail = currentDetail,
                    onDismiss = onDismissDetail,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ExecutionRunHistoryCard(
    run: IssueExecutionRunHistoryUi,
    onOpenDetail: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.Execution.runHistoryItem(run.runId)),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(run.runKind.displayLabel, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = run.historyScope.displayLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RunRecordPill("状态", run.status.displayLabel)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RunRecordPill("模型", run.actualModelId)
                RunRecordPill(
                    "思考",
                    "${run.actualThinkingLevel.storageValue} · ${run.thinkingLevelSource.displayLabel}",
                )
                if (run.isCurrent) RunRecordPill("位置", "当前 Run")
            }
            run.relationshipDescription?.let { relationship ->
                Text(
                    text = relationship,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            run.failureMessage?.takeIf(String::isNotBlank)?.let { failure ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = failure,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = { onOpenDetail(run.runId) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("查看详情")
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ExecutionRunHistoryDetail(
    detail: IssueExecutionRunDetailUiState,
    onDismiss: () -> Unit,
) {
    when (detail) {
        is IssueExecutionRunDetailUiState.Loading -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Text("正在读取 Run 详情")
            }
        }
        is IssueExecutionRunDetailUiState.Failure -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("无法读取 Run 详情", style = MaterialTheme.typography.titleSmall)
                    Text(detail.message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
        is IssueExecutionRunDetailUiState.Content -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Execution.RUN_HISTORY_DETAIL),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("运行详情", style = MaterialTheme.typography.titleSmall)
                            Text(
                                detail.run.runKind.displayLabel,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RunRecordPill("模型", detail.run.actualModelId)
                        RunRecordPill(
                            "思考",
                            "${detail.run.actualThinkingLevel.storageValue} · " +
                                detail.run.thinkingLevelSource.displayLabel,
                        )
                        RunRecordPill(
                            "额度",
                            "已用 ${detail.budget.usedApiCalls} / ${detail.budget.maxApiCalls}",
                        )
                    }
                    detail.run.relationshipDescription?.let { Text(it) }
                    detail.run.failureMessage?.takeIf(String::isNotBlank)?.let { failure ->
                        Text(
                            text = failure,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text("参与者与输出", style = MaterialTheme.typography.titleSmall)
                    if (detail.participants.isEmpty()) {
                        Text(
                            text = "此 Run 没有可读取的参与者快照。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        detail.participants.forEach { participant ->
                            ExecutionParticipantCard(
                                participant = participant,
                                testTag = JianyuAutomationTags.Execution.runHistoryParticipant(
                                    detail.run.runId,
                                    participant.snapshotId,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunRecordPill(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "$label：$value",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

private val IssueExecutionRunHistoryUi.relationshipDescription: String?
    get() = when {
        retryOfRunId != null -> "这是一次重试；原 Run 的快照保持不变。"
        parentRunId != null -> "该 Run 继承同一交叉讨论的主文本。"
        else -> null
    }

private val ExecutionRunKind.displayLabel: String
    get() = when (this) {
        ExecutionRunKind.STANDARD -> "标准执行"
        ExecutionRunKind.DIRECTED_RESPONSE -> "定向回应"
        ExecutionRunKind.CROSS_DISCUSSION_RESPONSE -> "交叉讨论回应"
        ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS -> "交叉讨论综合"
    }

private val ExecutionRunStatus.displayLabel: String
    get() = when (this) {
        ExecutionRunStatus.NOT_STARTED -> "尚未开始"
        ExecutionRunStatus.RUNNING -> "执行中"
        ExecutionRunStatus.PARTIAL_SUCCESS -> "部分完成"
        ExecutionRunStatus.SUCCEEDED,
        ExecutionRunStatus.COMPLETED -> "已完成"
        ExecutionRunStatus.RETRYABLE -> "可重试"
        ExecutionRunStatus.STOPPED -> "已停止"
        ExecutionRunStatus.FAILED -> "失败"
    }

private val ExecutionHistoryScope.displayLabel: String
    get() = when (this) {
        ExecutionHistoryScope.FULL_STAGE -> "上下文：当前阶段完整历史"
        ExecutionHistoryScope.EXPLICIT_MESSAGES -> "上下文：用户明确选择的消息"
        ExecutionHistoryScope.NO_HISTORY -> "上下文：不附带历史"
    }

private val ExecutionThinkingSource.displayLabel: String
    get() = when (this) {
        ExecutionThinkingSource.ROUND_USER_OVERRIDE -> "本轮用户覆盖"
        ExecutionThinkingSource.ISSUE_USER_DEFAULT -> "议题用户默认"
        ExecutionThinkingSource.AUTO_ROUTED -> "自动路由"
    }
