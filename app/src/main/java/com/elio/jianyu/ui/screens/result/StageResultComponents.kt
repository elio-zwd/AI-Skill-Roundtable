package com.elio.jianyu.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.Message
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.result.StageMessageSourceMetadata
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuStateCard

object StageResultTestTags {
    const val PANEL = JianyuAutomationTags.StageResult.PANEL
    const val DRAFT_EMPTY = JianyuAutomationTags.StageResult.DRAFT_EMPTY
    const val DRAFT_CREATE = JianyuAutomationTags.StageResult.DRAFT_CREATE
    const val DRAFT_CREATE_FROM_MESSAGES =
        JianyuAutomationTags.StageResult.DRAFT_CREATE_FROM_MESSAGES
    const val DRAFT_EDITOR = JianyuAutomationTags.StageResult.DRAFT_EDITOR
    const val DRAFT_SAVE = JianyuAutomationTags.StageResult.DRAFT_SAVE
    const val DRAFT_SAVING = JianyuAutomationTags.StageResult.DRAFT_SAVING
    const val DRAFT_SAVED = JianyuAutomationTags.StageResult.DRAFT_SAVED
    const val DRAFT_SAVE_FAILURE = JianyuAutomationTags.StageResult.DRAFT_SAVE_FAILURE
    const val DRAFT_CONFLICT = JianyuAutomationTags.StageResult.DRAFT_CONFLICT
    const val DRAFT_ABANDON = JianyuAutomationTags.StageResult.DRAFT_ABANDON
    const val DRAFT_ABANDON_CONFIRMATION =
        JianyuAutomationTags.StageResult.DRAFT_ABANDON_CONFIRMATION
    const val ARTIFACT_CONFIRM = JianyuAutomationTags.StageResult.ARTIFACT_CONFIRM
    const val ARTIFACT_CONFIRMATION_DIALOG =
        JianyuAutomationTags.StageResult.ARTIFACT_CONFIRMATION_DIALOG
    const val ARTIFACT_CONFIRMATION_CONFIRM =
        JianyuAutomationTags.StageResult.ARTIFACT_CONFIRMATION_CONFIRM
    const val ARTIFACT_CONFIRMATION_CANCEL =
        JianyuAutomationTags.StageResult.ARTIFACT_CONFIRMATION_CANCEL

    fun message(messageId: Long): String = JianyuAutomationTags.StageResult.message(messageId)

    fun artifact(artifactId: String): String = JianyuAutomationTags.StageResult.artifact(artifactId)
}

@Composable
fun StageDraftResultPanel(
    state: StageResultUiState,
    callbacks: StageResultCallbacks,
    modifier: Modifier = Modifier,
) {
    when (state) {
        StageResultUiState.Loading -> Column(
            modifier = modifier.testTag(StageResultTestTags.PANEL),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("正在恢复阶段草稿与成果")
        }
        is StageResultUiState.Failure -> JianyuStateCard(
            title = "阶段草稿与成果读取失败",
            message = "本地持久化状态暂时不可用。",
            actionLabel = "重试",
            onAction = callbacks.onRetry,
            modifier = modifier.testTag(StageResultTestTags.PANEL),
        )
        is StageResultUiState.Content -> StageResultContent(
            state = state,
            callbacks = callbacks,
            modifier = modifier,
        )
    }
}

@Composable
private fun StageResultContent(
    state: StageResultUiState.Content,
    callbacks: StageResultCallbacks,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(StageResultTestTags.PANEL),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("阶段草稿与成果", style = MaterialTheme.typography.titleMedium)
        Text(
            "草稿只在本地保存；只有最终确认后才会成为正式成果。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MessageSourceSelector(
            messages = state.workspace.selectableMessages,
            metadataByMessage = state.workspace.messageSourceMetadata,
            selectedIds = state.selectedMessageIds,
            onToggle = callbacks.onToggleMessage,
        )
        if (!state.hasDraft) {
            JianyuStateCard(
                title = "尚未创建阶段草稿",
                message = "可以从通用结构开始，也可以先选择当前阶段的已完成消息。",
                modifier = Modifier.testTag(StageResultTestTags.DRAFT_EMPTY),
            )
            Button(
                onClick = callbacks.onCreateGenericDraft,
                modifier = Modifier.testTag(StageResultTestTags.DRAFT_CREATE),
            ) {
                Text("创建阶段总结草稿")
            }
            if (state.selectedMessageIds.isNotEmpty()) {
                Button(
                    onClick = callbacks.onCreateDraftFromMessages,
                    modifier = Modifier.testTag(StageResultTestTags.DRAFT_CREATE_FROM_MESSAGES),
                ) {
                    Text("从选定消息创建草稿")
                }
            }
        } else {
            OutlinedTextField(
                value = state.editorContent,
                onValueChange = callbacks.onContentChange,
                label = { Text("阶段草稿") },
                minLines = 12,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(StageResultTestTags.DRAFT_EDITOR),
            )
            DraftSaveStatusCard(state.saveStatus)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = callbacks.onSave,
                    enabled = state.saveStatus !is StageDraftSaveStatus.Saving,
                    modifier = Modifier.testTag(StageResultTestTags.DRAFT_SAVE),
                ) {
                    Text("保存")
                }
                TextButton(
                    onClick = callbacks.onRequestAbandon,
                    modifier = Modifier.testTag(StageResultTestTags.DRAFT_ABANDON),
                ) {
                    Text("放弃当前草稿")
                }
            }
            if (state.canConfirmArtifact) {
                Button(
                    onClick = callbacks.onRequestArtifactConfirmation,
                    modifier = Modifier.testTag(StageResultTestTags.ARTIFACT_CONFIRM),
                ) {
                    Text("确认正式成果")
                }
            } else {
                Text(
                    "确认成果前必须先完成最新草稿保存。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StageArtifactList(
            artifacts = state.workspace.artifacts,
            latestIds = state.workspace.artifactRevisionResolution.latestArtifactIds,
            onCreateRevision = callbacks.onCreateRevision,
            onOpenArtifact = callbacks.onOpenArtifact,
        )
    }

    if (state.showAbandonConfirmation) {
        AlertDialog(
            modifier = Modifier.testTag(StageResultTestTags.DRAFT_ABANDON_CONFIRMATION),
            onDismissRequest = callbacks.onDismissAbandon,
            title = { Text("放弃当前草稿？") },
            text = {
                Text("当前可编辑草稿会被移除；历史草稿 Revision 和正式成果不会删除。")
            },
            confirmButton = {
                Button(onClick = callbacks.onConfirmAbandon) { Text("确认放弃") }
            },
            dismissButton = {
                TextButton(onClick = callbacks.onDismissAbandon) { Text("取消") }
            },
        )
    }

    if (state.showArtifactConfirmation) {
        ArtifactConfirmationDialog(
            state = state,
            callbacks = callbacks,
        )
    }
}

@Composable
private fun MessageSourceSelector(
    messages: List<Message>,
    metadataByMessage: Map<Long, StageMessageSourceMetadata>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    if (messages.isEmpty()) return
    Text("可选消息来源", style = MaterialTheme.typography.labelLarge)
    Text(
        "默认不选择任何消息；这里只显示当前阶段绑定真实 Run 的非 Pending 参与者输出。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    messages.forEach { message ->
        val metadata = metadataByMessage[message.id]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(StageResultTestTags.message(message.id)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = message.id in selectedIds,
                onCheckedChange = { onToggle(message.id) },
            )
            Column {
                Text(message.senderName, style = MaterialTheme.typography.labelLarge)
                metadata?.let {
                    Text(
                        buildString {
                            append(it.runKind.displayName())
                            append(" · ")
                            append(it.historyScope.displayName())
                            append(" · 实际消息上下文 ")
                            append(it.actualMessageUsageCount)
                            append(" 条")
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!it.completeRun) {
                        Text(
                            "该 Run 未完整成功；仅作为原始输出候选，不代表完整结论。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    message.text.lineSequence().firstOrNull().orEmpty().take(120),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DraftSaveStatusCard(status: StageDraftSaveStatus) {
    when (status) {
        StageDraftSaveStatus.Idle -> Text("尚未保存")
        StageDraftSaveStatus.Dirty -> Text("有未保存修改")
        StageDraftSaveStatus.Saving -> JianyuStateCard(
            title = "正在保存",
            message = "正在创建新的草稿 Revision。",
            modifier = Modifier.testTag(StageResultTestTags.DRAFT_SAVING),
        )
        is StageDraftSaveStatus.Saved -> JianyuStateCard(
            title = "已保存",
            message = "当前为 Revision ${status.revision}。",
            modifier = Modifier.testTag(StageResultTestTags.DRAFT_SAVED),
        )
        is StageDraftSaveStatus.Failure -> JianyuStateCard(
            title = "保存失败",
            message = "编辑内容仍保留在页面中，可以再次保存。",
            modifier = Modifier.testTag(StageResultTestTags.DRAFT_SAVE_FAILURE),
        )
        StageDraftSaveStatus.Conflict -> JianyuStateCard(
            title = "草稿已有更新",
            message = "其他页面已保存较新 Revision；重新加载前不会覆盖。",
            actionLabel = "重新加载",
            modifier = Modifier.testTag(StageResultTestTags.DRAFT_CONFLICT),
        )
    }
}

@Composable
private fun StageArtifactList(
    artifacts: List<ConfirmedArtifactEntity>,
    latestIds: Set<String>,
    onCreateRevision: (String) -> Unit,
    onOpenArtifact: (String) -> Unit,
) {
    Text("当前阶段正式成果", style = MaterialTheme.typography.titleMedium)
    if (artifacts.isEmpty()) {
        Text(
            "当前阶段可以没有正式成果；这不会阻止后续推进。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    artifacts.sortedWith(
        compareByDescending<ConfirmedArtifactEntity> { it.confirmedAt }.thenBy { it.id },
    ).forEach { artifact ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(StageResultTestTags.artifact(artifact.id)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(artifact.title, style = MaterialTheme.typography.titleSmall)
                JianyuMetadataRow(
                    "类型",
                    ArtifactType.fromStorageValue(artifact.artifactType)?.displayName
                        ?: artifact.artifactType,
                )
                JianyuMetadataRow(
                    "版本",
                    if (artifact.id in latestIds) "最新版本" else "历史版本",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpenArtifact(artifact.id) }) { Text("查看") }
                    if (artifact.id in latestIds) {
                        TextButton(onClick = { onCreateRevision(artifact.id) }) {
                            Text("创建修订")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactConfirmationDialog(
    state: StageResultUiState.Content,
    callbacks: StageResultCallbacks,
) {
    AlertDialog(
        modifier = Modifier.testTag(StageResultTestTags.ARTIFACT_CONFIRMATION_DIALOG),
        onDismissRequest = callbacks.onDismissArtifactConfirmation,
        title = { Text("确认正式成果") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("最终确认前不会创建成果，也不会推进阶段。")
                OutlinedTextField(
                    value = state.artifactTitle,
                    onValueChange = callbacks.onArtifactTitleChange,
                    label = { Text("成果标题") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("成果类型", style = MaterialTheme.typography.labelLarge)
                ArtifactType.entries.forEach { type ->
                    FilterChip(
                        selected = state.artifactType == type,
                        onClick = { callbacks.onArtifactTypeChange(type) },
                        label = { Text(type.displayName) },
                    )
                }
                val selectedUsageCount = state.selectedMessageIds.sumOf { messageId ->
                    state.workspace.messageSourceMetadata[messageId]
                        ?.actualMessageUsageCount
                        ?: 0
                }
                JianyuStateCard(
                    title = "来源预览",
                    message = buildString {
                        append("草稿 Revision ")
                        append(state.currentRevision)
                        append("；选定参与者输出 ")
                        append(state.selectedMessageIds.size)
                        append(" 条；其 Run 实际使用消息上下文共 ")
                        append(selectedUsageCount)
                        append(" 条。对应 Run 与真实资料使用快照将在原子确认时加入。")
                    },
                )
                when (val status = state.artifactStatus) {
                    StageArtifactConfirmationStatus.Idle -> Unit
                    StageArtifactConfirmationStatus.Confirming -> Text("正在确认正式成果")
                    is StageArtifactConfirmationStatus.Confirmed -> Text("成果已确认")
                    is StageArtifactConfirmationStatus.Failure -> JianyuStateCard(
                        title = "成果确认失败",
                        message = "草稿仍保留，可以修正来源或稍后重试。",
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = callbacks.onConfirmArtifact,
                enabled = state.artifactTitle.isNotBlank() &&
                    state.artifactStatus !is StageArtifactConfirmationStatus.Confirming,
                modifier = Modifier.testTag(StageResultTestTags.ARTIFACT_CONFIRMATION_CONFIRM),
            ) {
                Text("最终确认")
            }
        },
        dismissButton = {
            TextButton(
                onClick = callbacks.onDismissArtifactConfirmation,
                modifier = Modifier.testTag(StageResultTestTags.ARTIFACT_CONFIRMATION_CANCEL),
            ) {
                Text("取消")
            }
        },
    )
}

private fun ExecutionRunKind.displayName(): String = when (this) {
    ExecutionRunKind.STANDARD -> "标准执行"
    ExecutionRunKind.DIRECTED_RESPONSE -> "点名回应"
    ExecutionRunKind.CROSS_DISCUSSION_RESPONSE -> "交叉讨论回应"
    ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS -> "交叉讨论整合"
}

private fun ExecutionHistoryScope.displayName(): String = when (this) {
    ExecutionHistoryScope.FULL_STAGE -> "完整阶段历史"
    ExecutionHistoryScope.EXPLICIT_MESSAGES -> "显式选定消息"
    ExecutionHistoryScope.NO_HISTORY -> "不使用历史消息"
}
