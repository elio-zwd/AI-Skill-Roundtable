package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionThinkingLevel
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.execution.SearchMode
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
    const val SEARCH_MODE = "issue_execution_search_mode"
    const val THINKING_DEFAULT = "issue_execution_thinking_default"
    const val THINKING_OVERRIDE = "issue_execution_thinking_override"

    fun searchMode(mode: SearchMode): String = "issue_execution_search_mode_${mode.name.lowercase()}"

    fun thinkingDefault(policy: IssueThinkingPolicy): String =
        "issue_execution_thinking_default_${policy.storageValue}"

    fun thinkingOverride(policy: IssueThinkingPolicy?): String =
        "issue_execution_thinking_override_${policy?.storageValue ?: "follow_default"}"

    fun participant(snapshotId: String): String = "issue_execution_participant_$snapshotId"
}

@Composable
internal fun ExecutionThinkingPolicyCard(
    defaultPolicy: IssueThinkingPolicy,
    overridePolicy: IssueThinkingPolicy?,
    canChangeDefault: Boolean,
    operationInProgress: Boolean,
    onDefaultChanged: (IssueThinkingPolicy) -> Unit,
    onOverrideChanged: (IssueThinkingPolicy?) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("议题默认思考策略", style = MaterialTheme.typography.titleSmall)
            Text(
                text = if (canChangeDefault) {
                    "仅影响之后创建的 Run；历史与当前 Run 不回写。"
                } else {
                    "有进行中的 Run 时不可修改默认策略。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .testTag(IssueExecutionTestTags.THINKING_DEFAULT)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IssueThinkingPolicy.entries.forEach { candidate ->
                    FilterChip(
                        selected = candidate == defaultPolicy,
                        onClick = { onDefaultChanged(candidate) },
                        enabled = canChangeDefault && !operationInProgress,
                        label = { Text(candidate.displayLabel) },
                        modifier = Modifier.testTag(IssueExecutionTestTags.thinkingDefault(candidate)),
                    )
                }
            }

            Text("本次思考策略", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "仅影响下一次创建或重试的 Run，不会改写议题默认策略。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .testTag(IssueExecutionTestTags.THINKING_OVERRIDE)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = overridePolicy == null,
                    onClick = { onOverrideChanged(null) },
                    enabled = !operationInProgress,
                    label = { Text("跟随议题默认") },
                    modifier = Modifier.testTag(IssueExecutionTestTags.thinkingOverride(null)),
                )
                IssueThinkingPolicy.entries
                    .filterNot { it == IssueThinkingPolicy.AUTO }
                    .forEach { candidate ->
                        FilterChip(
                            selected = candidate == overridePolicy,
                            onClick = { onOverrideChanged(candidate) },
                            enabled = !operationInProgress,
                            label = { Text(candidate.displayLabel) },
                            modifier = Modifier.testTag(
                                IssueExecutionTestTags.thinkingOverride(candidate),
                            ),
                        )
                    }
            }
        }
    }
}

private val IssueThinkingPolicy.displayLabel: String
    get() = when (this) {
        IssueThinkingPolicy.AUTO -> "自动"
        IssueThinkingPolicy.MINIMAL -> "minimal"
        IssueThinkingPolicy.LOW -> "low"
        IssueThinkingPolicy.MEDIUM -> "medium"
        IssueThinkingPolicy.HIGH -> "high"
    }

@Composable
internal fun ExecutionSearchModeCard(
    mode: SearchMode,
    enabled: Boolean,
    onModeChanged: (SearchMode) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(IssueExecutionTestTags.SEARCH_MODE),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("联网搜索", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "仅影响下一次 Interaction。创建后的当前 Interaction 不可更改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchMode.entries.forEach { candidate ->
                    FilterChip(
                        selected = candidate == mode,
                        onClick = { onModeChanged(candidate) },
                        enabled = enabled,
                        label = { Text(candidate.displayLabel) },
                        modifier = Modifier.testTag(IssueExecutionTestTags.searchMode(candidate)),
                    )
                }
            }
        }
    }
}

private val SearchMode.displayLabel: String
    get() = when (this) {
        SearchMode.OFF -> "关闭"
        SearchMode.AUTO -> "自动"
        SearchMode.ON -> "开启"
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
            state.actualModelId?.let { model ->
                Text(
                    text = "实际模型：$model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.actualThinkingLevel?.let { level ->
                Text(
                    text = "实际思考强度：${level.displayLabel}（${state.thinkingLevelSource?.displayLabel.orEmpty()}）",
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

private val ExecutionThinkingLevel.displayLabel: String
    get() = storageValue

private val ExecutionThinkingSource.displayLabel: String
    get() = when (this) {
        ExecutionThinkingSource.ROUND_USER_OVERRIDE -> "本轮用户覆盖"
        ExecutionThinkingSource.ISSUE_USER_DEFAULT -> "议题用户默认"
        ExecutionThinkingSource.AUTO_ROUTED -> "自动路由"
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
