package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuStateCard

@Composable
internal fun IssueCollaborationSection(
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
    onRetryFailed: (String) -> Unit,
    onSynthesize: (String) -> Unit,
    onRetrySynthesis: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    when (state) {
        IssueCollaborationUiState.Loading -> Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("正在从 Room 恢复正式阵容与协作状态")
            }
        }
        is IssueCollaborationUiState.Failure -> JianyuStateCard(
            title = if (state.catalogUnavailable) "协作入口不可用" else "协作状态读取失败",
            message = state.message,
        )
        is IssueCollaborationUiState.Content -> {
            CollaborationComposer(
                state = state,
                onInputChanged = onInputChanged,
                onOpenDirected = onOpenDirected,
                onOpenCross = onOpenCross,
            )
            state.sessions.forEach { session ->
                CrossDiscussionStatusCard(
                    session = session,
                    operationInProgress = state.operationInProgress,
                    onRetryFailed = onRetryFailed,
                    onSynthesize = onSynthesize,
                    onRetrySynthesis = onRetrySynthesis,
                    onStop = onStop,
                )
            }
            when (state.dialogMode) {
                CollaborationDialogMode.DIRECTED -> DirectedResponseDialog(
                    state = state,
                    contextConfirmed = contextConfirmed,
                    onDismiss = onDismissDialog,
                    onToggleParticipant = onToggleParticipant,
                    onToggleMessage = onToggleMessage,
                    onOpenContext = onOpenContext,
                    onConfirm = onConfirmDirected,
                )
                CollaborationDialogMode.CROSS -> CrossDiscussionDialog(
                    state = state,
                    contextConfirmed = contextConfirmed,
                    onDismiss = onDismissDialog,
                    onToggleParticipant = onToggleParticipant,
                    onToggleMessage = onToggleMessage,
                    onOpenContext = onOpenContext,
                    onConfirm = onConfirmCross,
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun CollaborationComposer(
    state: IssueCollaborationUiState.Content,
    onInputChanged: (String) -> Unit,
    onOpenDirected: () -> Unit,
    onOpenCross: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("本次协作", style = MaterialTheme.typography.titleMedium)
            Text(
                "点名只影响本次请求；交叉讨论只进行一轮，并由透明的会议行动助手整合。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Collaboration.INPUT),
                label = { Text("本次问题或讨论焦点") },
                minLines = 3,
                enabled = !state.operationInProgress,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Collaboration.ROSTER),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("当前正式阵容", style = MaterialTheme.typography.labelLarge)
                if (state.roster.isEmpty()) {
                    Text(
                        "当前阶段尚无 STANDARD 根 Run 的正式阵容，点名和交叉讨论不可用。",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    state.roster.sortedBy { it.position }.forEach { participant ->
                        Text("${participant.position + 1}. ${participant.displayName} · ${participant.responsibility}")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onOpenDirected,
                    enabled = state.canOpenDirected,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(JianyuAutomationTags.Collaboration.DIRECTED_RESPONSE_BUTTON),
                ) {
                    Text("点名回应")
                }
                Button(
                    onClick = onOpenCross,
                    enabled = state.canOpenCross,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(JianyuAutomationTags.Collaboration.CROSS_DISCUSSION_BUTTON),
                ) {
                    Text("交叉讨论")
                }
            }
            state.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(
                        if (state.dialogMode == CollaborationDialogMode.DIRECTED) {
                            JianyuAutomationTags.Collaboration.DIRECTED_FAILURE
                        } else {
                            JianyuAutomationTags.Collaboration.CROSS_FAILURE
                        },
                    ),
                )
            }
            if (state.operationInProgress) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在持久化协作事实并执行")
                }
            }
        }
    }
}

@Composable
private fun DirectedResponseDialog(
    state: IssueCollaborationUiState.Content,
    contextConfirmed: Boolean,
    onDismiss: () -> Unit,
    onToggleParticipant: (String) -> Unit,
    onToggleMessage: (Long) -> Unit,
    onOpenContext: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(JianyuAutomationTags.Collaboration.DIRECTED_DIALOG),
        title = { Text("临时点名回应") },
        text = {
            CollaborationDialogBody(
                state = state,
                directed = true,
                contextConfirmed = contextConfirmed,
                onToggleParticipant = onToggleParticipant,
                onToggleMessage = onToggleMessage,
                onOpenContext = onOpenContext,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = state.canConfirmDirected,
                modifier = Modifier.testTag(JianyuAutomationTags.Collaboration.DIRECTED_CONFIRM),
            ) {
                Text("确认，仅由该 Skill 回应")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CrossDiscussionDialog(
    state: IssueCollaborationUiState.Content,
    contextConfirmed: Boolean,
    onDismiss: () -> Unit,
    onToggleParticipant: (String) -> Unit,
    onToggleMessage: (Long) -> Unit,
    onOpenContext: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(JianyuAutomationTags.Collaboration.CROSS_DIALOG),
        title = { Text("一轮交叉讨论") },
        text = {
            CollaborationDialogBody(
                state = state,
                directed = false,
                contextConfirmed = contextConfirmed,
                onToggleParticipant = onToggleParticipant,
                onToggleMessage = onToggleMessage,
                onOpenContext = onOpenContext,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = state.canConfirmCross,
                modifier = Modifier.testTag(JianyuAutomationTags.Collaboration.CROSS_CONFIRM),
            ) {
                Text("确认开始 ${state.estimatedCrossCalls} 次必需调用")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CollaborationDialogBody(
    state: IssueCollaborationUiState.Content,
    directed: Boolean,
    contextConfirmed: Boolean,
    onToggleParticipant: (String) -> Unit,
    onToggleMessage: (Long) -> Unit,
    onOpenContext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            state.input,
            modifier = if (directed) Modifier else Modifier.testTag(
                JianyuAutomationTags.Collaboration.CROSS_FOCUS_INPUT,
            ),
        )
        Text(
            if (directed) {
                "只影响本次请求，不改变当前阵容；必须精确选择一位成员。"
            } else {
                "至少选择两位成员。第一阶段成员相互不可见，只进行一轮。"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.roster.sortedBy { it.position }.forEach { participant ->
            SelectionRow(
                selected = participant.selected,
                title = participant.displayName,
                subtitle = participant.responsibility,
                tag = if (directed) {
                    JianyuAutomationTags.Collaboration.directedParticipant(participant.skillId)
                } else {
                    JianyuAutomationTags.Collaboration.crossParticipant(participant.skillId)
                },
                onClick = { onToggleParticipant(participant.skillId) },
            )
        }
        if (!directed) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Collaboration.CROSS_INTEGRATOR),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("透明整合者", style = MaterialTheme.typography.labelLarge)
                    Text(state.integratorDisplayName)
                    Text("整合调用计入预算；保留分歧，不使用多数票裁决。")
                }
            }
        }
        Text("可选历史消息", style = MaterialTheme.typography.labelLarge)
        if (state.messages.isEmpty()) {
            Text("当前阶段没有可选择的已完成消息。")
        } else {
            state.messages.forEach { message ->
                SelectionRow(
                    selected = message.selected,
                    title = message.senderName,
                    subtitle = message.preview,
                    tag = JianyuAutomationTags.Collaboration.message(message.messageId),
                    onClick = { onToggleMessage(message.messageId) },
                )
            }
        }
        OutlinedButton(onClick = onOpenContext, enabled = !state.operationInProgress) {
            Text(if (contextConfirmed) "资料与个人背景已确认，可重新查看" else "选择资料与个人背景")
        }
        Text(
            "只有明确选择的历史消息、资料和个人背景会发送；空选择不会回退为整个阶段历史。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = title }
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CrossDiscussionStatusCard(
    session: CrossDiscussionSessionUi,
    operationInProgress: Boolean,
    onRetryFailed: (String) -> Unit,
    onSynthesize: (String) -> Unit,
    onRetrySynthesis: (String) -> Unit,
    onStop: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.Collaboration.session(session.sessionId)),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .testTag(JianyuAutomationTags.Collaboration.CROSS_STATUS),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("交叉讨论 · ${session.status.toDisplayLabel()}", style = MaterialTheme.typography.titleMedium)
            Text(session.focus)
            JianyuMetadataRow("整合者", "会议行动助手（${session.integratorSkillId}）")
            JianyuMetadataRow(
                "已成功成员",
                session.successfulSkillIds.joinToString().ifBlank { "暂无" },
            )
            JianyuMetadataRow(
                "未成功成员",
                session.failedSkillIds.joinToString().ifBlank { "暂无" },
            )
            if (session.status == CrossDiscussionStatus.AWAITING_SYNTHESIS) {
                Text("成员回应已完成，等待继续整合。")
            }
            if (session.status == CrossDiscussionStatus.PARTIAL_SUCCESS) {
                Text("不会自动忽略失败成员；仅整合当前成功内容需要用户明确确认。")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session.canRetryFailed) {
                    OutlinedButton(
                        onClick = { onRetryFailed(session.sessionId) },
                        enabled = !operationInProgress,
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.Collaboration.CROSS_RETRY_FAILED,
                        ),
                    ) { Text("重试失败成员") }
                }
                if (session.canSynthesize) {
                    Button(
                        onClick = { onSynthesize(session.sessionId) },
                        enabled = !operationInProgress,
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.Collaboration.CROSS_SYNTHESIZE_AVAILABLE,
                        ),
                    ) {
                        Text(
                            if (session.status == CrossDiscussionStatus.PARTIAL_SUCCESS) {
                                "仅整合当前成功内容"
                            } else {
                                "继续整合"
                            },
                        )
                    }
                }
                if (session.canRetrySynthesis) {
                    Button(
                        onClick = { onRetrySynthesis(session.sessionId) },
                        enabled = !operationInProgress,
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.Collaboration.CROSS_RESUME_SYNTHESIS,
                        ),
                    ) { Text("仅重试整合") }
                }
                if (session.status in setOf(
                        CrossDiscussionStatus.RESPONDING,
                        CrossDiscussionStatus.SYNTHESIZING,
                        CrossDiscussionStatus.PARTIAL_SUCCESS,
                        CrossDiscussionStatus.AWAITING_SYNTHESIS,
                    )
                ) {
                    TextButton(
                        onClick = { onStop(session.sessionId) },
                        enabled = !operationInProgress,
                    ) { Text("停止讨论") }
                }
            }
        }
    }
}

private fun CrossDiscussionStatus.toDisplayLabel(): String = when (this) {
    CrossDiscussionStatus.RESPONDING -> "成员独立回应中"
    CrossDiscussionStatus.PARTIAL_SUCCESS -> "部分成员成功"
    CrossDiscussionStatus.AWAITING_SYNTHESIS -> "等待整合"
    CrossDiscussionStatus.SYNTHESIZING -> "透明整合中"
    CrossDiscussionStatus.SYNTHESIS_RETRYABLE -> "整合可重试"
    CrossDiscussionStatus.SUCCEEDED -> "整合完成"
    CrossDiscussionStatus.STOPPED -> "已停止"
    CrossDiscussionStatus.FAILED -> "讨论失败"
}
