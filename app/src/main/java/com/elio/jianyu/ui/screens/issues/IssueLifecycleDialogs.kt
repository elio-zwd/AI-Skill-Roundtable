package com.elio.jianyu.ui.screens.issues

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.IssuePurgeFailurePhase
import com.elio.jianyu.ui.automation.JianyuLifecycleAutomationTags

@Composable
fun IssueLifecycleDialogs(
    state: IssueLifecycleUiState?,
    onDismiss: () -> Unit,
    onWait: () -> Unit,
    onRefreshWaiting: () -> Unit,
    onStop: () -> Unit,
    onSummaryChange: (String) -> Unit,
    onArchiveConfirm: () -> Unit,
    onTrashConfirm: () -> Unit,
    onResumeChange: (String) -> Unit,
    onResumeNoChange: () -> Unit,
    onResumeConfirm: () -> Unit,
    onRelatedTitleChange: (String) -> Unit,
    onRelatedObjectiveChange: (String) -> Unit,
    onRelatedConfirm: () -> Unit,
    onPurgeFirstConfirm: () -> Unit,
    onPurgeFinalConfirm: () -> Unit,
    onPurgeRetry: (String) -> Unit,
    onPurgeCancel: (String) -> Unit,
) {
    when (state) {
        null -> Unit
        IssueLifecycleUiState.ArchiveImpactLoading,
        IssueLifecycleUiState.RestoringFromTrash,
        IssueLifecycleUiState.RelatedIssueCreating,
        -> ProgressDialog("正在处理", onDismiss = null)

        is IssueLifecycleUiState.ArchiveNeedsTaskDecision -> AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.DIALOG),
            title = { Text("仍有活动任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请选择等待任务自然结束，或停止相关运行、协作和音频任务。系统不会默认选择。")
                    Text("活动任务：${state.preparation.impact.activeWorkCount}")
                    Text("Pending Message：${state.preparation.impact.pendingMessageCount}")
                    Text("Pending Audio：${state.preparation.impact.audioPendingCount}")
                }
            },
            confirmButton = {
                Button(
                    onClick = onWait,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.WAIT),
                ) { Text("等待") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onStop,
                        modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.STOP),
                    ) { Text("停止任务") }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.CANCEL),
                    ) { Text("取消") }
                }
            },
        )

        is IssueLifecycleUiState.ArchiveWaiting -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("等待活动任务") },
            text = { Text("当前不会停止任务，也不会自动归档或删除。任务完成后请重新检查。") },
            confirmButton = {
                Button(
                    onClick = onRefreshWaiting,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.WAIT),
                ) { Text("重新检查") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is IssueLifecycleUiState.ArchiveStopping -> ProgressDialog("正在停止相关任务", onDismiss = null)

        is IssueLifecycleUiState.ArchiveEditingSummary -> AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.DIALOG),
            title = { Text("确认归档") },
            text = {
                OutlinedTextField(
                    value = state.summaryMarkdown,
                    onValueChange = onSummaryChange,
                    label = { Text("归档简报") },
                    minLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .testTag(JianyuLifecycleAutomationTags.Archive.SUMMARY),
                )
            },
            confirmButton = {
                Button(
                    onClick = onArchiveConfirm,
                    enabled = state.summaryMarkdown.isNotBlank(),
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.CONFIRM),
                ) { Text("确认归档") }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Archive.CANCEL),
                ) { Text("取消") }
            },
        )

        is IssueLifecycleUiState.Archiving -> ProgressDialog("正在保存归档简报", onDismiss = null)
        is IssueLifecycleUiState.Archived -> ResultDialog("归档完成", "历史、成果和音频均已保留。", onDismiss)
        is IssueLifecycleUiState.ArchiveFailure -> ErrorDialog("归档失败", state.code, onDismiss)

        is IssueLifecycleUiState.ResumeEditingChanges -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("继续原议题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("归档简报")
                    Text(
                        state.archiveEvent.summaryMarkdown,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = state.changeNote,
                        onValueChange = onResumeChange,
                        label = { Text("现在有什么变化") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(JianyuLifecycleAutomationTags.Resume.CHANGE_NOTE),
                    )
                    TextButton(
                        onClick = onResumeNoChange,
                        modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Resume.NO_CHANGE),
                    ) { Text(if (state.noChangeConfirmed) "已选择：暂无变化" else "选择暂无变化") }
                }
            },
            confirmButton = {
                Button(
                    onClick = onResumeConfirm,
                    enabled = state.changeNote.isNotBlank() || state.noChangeConfirmed,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Resume.CONFIRM),
                ) { Text("确认继续") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is IssueLifecycleUiState.Resuming -> ProgressDialog("正在恢复议题", onDismiss = null)
        is IssueLifecycleUiState.Resumed -> ResultDialog("已恢复", "议题已返回原当前 Stage。", onDismiss)
        is IssueLifecycleUiState.ResumeFailure -> ErrorDialog("恢复失败", state.code, onDismiss)

        is IssueLifecycleUiState.RelatedIssueEditing -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("创建关联新议题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("这是关联的新议题，不是原议题的新 Stage。不会复制全部消息、运行、草稿、成果或资料正文。")
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onRelatedTitleChange,
                        label = { Text("新议题标题") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(JianyuLifecycleAutomationTags.RelatedIssue.TITLE),
                    )
                    OutlinedTextField(
                        value = state.objective,
                        onValueChange = onRelatedObjectiveChange,
                        label = { Text("初始目标") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(JianyuLifecycleAutomationTags.RelatedIssue.OBJECTIVE),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onRelatedConfirm,
                    enabled = state.title.isNotBlank() && state.objective.isNotBlank(),
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.RelatedIssue.CONFIRM),
                ) { Text("创建独立议题") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is IssueLifecycleUiState.RelatedIssueCreated -> ResultDialog(
            "关联议题已创建",
            "新议题拥有独立主线，并保留来源关系。",
            onDismiss,
        )
        is IssueLifecycleUiState.RelatedIssueFailure -> ErrorDialog("创建失败", state.code, onDismiss)

        is IssueLifecycleUiState.TrashImpact -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("移入回收站") },
            text = {
                Text("议题会保留恢复能力，不会自动过期、自动清空或删除文件。")
            },
            confirmButton = {
                Button(
                    onClick = onTrashConfirm,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.IssueLifecycle.TRASH_CONFIRM),
                ) { Text("移入回收站") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is IssueLifecycleUiState.MovingToTrash -> ProgressDialog("正在移入回收站", onDismiss = null)
        is IssueLifecycleUiState.Trashed -> ResultDialog("已移入回收站", "不会自动过期或清空。", onDismiss)
        is IssueLifecycleUiState.TrashFailure -> ErrorDialog("回收站操作失败", state.code, onDismiss)
        is IssueLifecycleUiState.TrashRestored -> ResultDialog("恢复完成", "已恢复到进入回收站前的状态。", onDismiss)

        IssueLifecycleUiState.PurgeImpactLoading -> ProgressDialog("正在计算真实影响范围", onDismiss = null)
        is IssueLifecycleUiState.PurgeImpactReady -> AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Purge.IMPACT),
            title = { Text("彻底清除影响范围") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("数据库对象：${state.impact.databaseObjectCount}")
                    Text("正式文件：${state.impact.formalFileCount}")
                    Text("文件大小：${state.impact.formalFileBytes} B")
                    Text("待执行任务：${state.impact.pendingWorkNames.size}")
                    Text("缺失文件：${state.impact.missingAssetIds.size}")
                    Text("Orphan 报告：${state.impact.orphanRelativePaths.size}（不会自动删除）")
                    Text("关联新议题：${state.impact.relatedIssueCount}（目标议题保留）")
                    Text("外部或不可删除对象：${state.impact.externalObjectCount}")
                    Text("此操作不可恢复。下一步仍需第二次明确确认。")
                }
            },
            confirmButton = {
                Button(
                    onClick = onPurgeFirstConfirm,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Purge.FIRST_CONFIRM),
                ) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )

        is IssueLifecycleUiState.PurgeConfirming -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("最终确认") },
            text = { Text("彻底清除后无法恢复。确认永久删除该测试或目标议题的全部受控数据？") },
            confirmButton = {
                Button(
                    onClick = onPurgeFinalConfirm,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Purge.FINAL_CONFIRM),
                ) { Text("永久清除") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("返回") } },
        )

        is IssueLifecycleUiState.PurgeRequested -> PurgeProgressDialog(
            title = "已请求彻底清除",
            message = "正在等待后台任务进入安全终态。",
            operationId = state.operation.id,
            cancelAllowed = true,
            onCancel = onPurgeCancel,
        )
        is IssueLifecycleUiState.PurgeCancelingTasks -> PurgeProgressDialog(
            title = "正在停止后台任务",
            message = "文件尚未开始删除，可尝试安全取消。",
            operationId = state.operation.id,
            cancelAllowed = true,
            onCancel = onPurgeCancel,
        )
        is IssueLifecycleUiState.PurgeDeletingFiles -> PurgeProgressDialog(
            title = "正在删除受控文件",
            message = "文件删除已经开始，不能伪装为可完整取消。",
            operationId = state.operation.id,
            cancelAllowed = false,
            onCancel = onPurgeCancel,
        )
        is IssueLifecycleUiState.PurgeDatabaseCleanup -> PurgeProgressDialog(
            title = "正在完成数据库清理",
            message = "全部数据库事实将在单一事务中删除。",
            operationId = state.operation.id,
            cancelAllowed = false,
            onCancel = onPurgeCancel,
        )
        is IssueLifecycleUiState.PurgeRetryableFailure -> AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Purge.FAILURE),
            title = { Text("清理失败，可重试") },
            text = {
                Text(
                    if (state.operation.failurePhase == IssuePurgeFailurePhase.DATABASE_PURGE) {
                        "文件已清理，数据库收尾失败。重试不会恢复或重复生成文件。"
                    } else {
                        "失败阶段：${state.operation.failurePhase?.storageValue ?: "unknown"}；错误码：${state.operation.failureCode ?: "unknown"}"
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = { onPurgeRetry(state.operation.id) },
                    enabled = state.operation.failurePhase != IssuePurgeFailurePhase.IMPACT,
                    modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Purge.RETRY),
                ) { Text("重试") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        )
        is IssueLifecycleUiState.PurgeCompleted -> ResultDialog("彻底清除完成", "议题已从导航移除。", onDismiss)
        is IssueLifecycleUiState.PurgeStorageFailure -> ErrorDialog("清理不可用", state.code, onDismiss)
        is IssueLifecycleUiState.LifecycleContent,
        IssueLifecycleUiState.LifecycleLoading,
        -> Unit
    }
}

@Composable
private fun ProgressDialog(title: String, onDismiss: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Purge.PROGRESS),
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("请根据持久化状态继续，不会自动创建模型或音频任务。")
            }
        },
        confirmButton = {
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun PurgeProgressDialog(
    title: String,
    message: String,
    operationId: String,
    cancelAllowed: Boolean,
    onCancel: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag(JianyuLifecycleAutomationTags.Purge.PROGRESS),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(message)
            }
        },
        confirmButton = {
            if (cancelAllowed) {
                TextButton(onClick = { onCancel(operationId) }) { Text("安全取消") }
            }
        },
    )
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun ErrorDialog(title: String, code: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("错误码：$code")
                Text("未执行未确认的后续写入。")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("关闭") } },
    )
}
