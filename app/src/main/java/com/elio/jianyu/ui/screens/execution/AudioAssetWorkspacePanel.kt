package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.audio.assets.AudioAssetPlaybackState
import com.elio.jianyu.audio.assets.AudioAssetRecord
import com.elio.jianyu.audio.assets.AudioAssetSource
import com.elio.jianyu.audio.assets.AudioSourceReference
import com.elio.jianyu.data.AudioFileState
import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.Message
import com.elio.jianyu.ui.automation.JianyuAutomationTags

@Composable
fun AudioAssetWorkspacePanel(
    issueId: String,
    stageId: String,
    messages: List<Message>,
    artifacts: List<ConfirmedArtifactEntity>,
    state: AudioAssetWorkspaceState,
    onRefresh: () -> Unit,
    onReconcile: () -> Unit,
    onRequestGeneration: (AudioSourceReference) -> Unit,
    onRequestRetry: (String) -> Unit,
    onCancelGeneration: (String) -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmPendingAction: () -> Unit,
    onDismissPendingAction: () -> Unit,
    onPlay: (AudioAssetRecord) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.AudioAssets.PANEL),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("音频资产", style = MaterialTheme.typography.titleMedium)
            Text(
                "只从完成消息或已确认成果显式生成。恢复页面不会自动联网、重新排队或扫描文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !state.operationInProgress,
                    modifier = Modifier.testTag(JianyuAutomationTags.AudioAssets.REFRESH),
                ) { Text("刷新状态") }
                OutlinedButton(
                    onClick = onReconcile,
                    enabled = !state.operationInProgress,
                    modifier = Modifier.testTag(JianyuAutomationTags.AudioAssets.RECONCILE),
                ) { Text("检查缺失与孤儿") }
            }

            if (state.loading || state.operationInProgress) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(if (state.loading) "正在读取音频资产" else "正在执行受控音频操作")
                }
            }
            state.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.errorCode?.let {
                Text("音频操作失败：$it", color = MaterialTheme.colorScheme.error)
            }
            state.reconciliation?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("文件检查结果", style = MaterialTheme.typography.titleSmall)
                        Text("新标记缺失：${result.markedMissingAssetIds.size}")
                        Text("并发状态变化：${result.staleAssetIds.size}")
                        Text("孤儿文件：${result.orphanReport.files.size}")
                        Text(
                            "这里只报告孤儿文件，不会自动删除。物理清理由后续受控流程再次确认。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val completedMessages = messages.filterNot(Message::isPending)
            Text("完成消息", style = MaterialTheme.typography.titleSmall)
            if (completedMessages.isEmpty()) {
                Text("当前阶段没有可生成音频的完成消息。")
            } else {
                completedMessages.forEach { message ->
                    SourceGenerationCard(
                        label = "${message.senderName} · 消息 ${message.id}",
                        buttonTag = JianyuAutomationTags.AudioAssets.messageGenerate(message.id),
                        enabled = !state.operationInProgress,
                        onGenerate = {
                            onRequestGeneration(
                                AudioSourceReference.Message(issueId, stageId, message.id),
                            )
                        },
                    )
                }
            }

            Text("已确认成果", style = MaterialTheme.typography.titleSmall)
            if (artifacts.isEmpty()) {
                Text("当前阶段没有已确认成果。")
            } else {
                artifacts.forEach { artifact ->
                    SourceGenerationCard(
                        label = artifact.title,
                        buttonTag = JianyuAutomationTags.AudioAssets.artifactGenerate(artifact.id),
                        enabled = !state.operationInProgress,
                        onGenerate = {
                            onRequestGeneration(
                                AudioSourceReference.Artifact(issueId, stageId, artifact.id),
                            )
                        },
                    )
                }
            }

            Text("资产状态", style = MaterialTheme.typography.titleSmall)
            if (state.assets.isEmpty()) Text("当前阶段尚无独立音频资产。")
            state.assets.forEach { asset ->
                AudioAssetCard(
                    asset = asset,
                    playbackState = state.playbackState,
                    operationInProgress = state.operationInProgress,
                    onRetry = { onRequestRetry(asset.id) },
                    onCancel = { onCancelGeneration(asset.id) },
                    onDelete = { onRequestDelete(asset.id) },
                    onPlay = { onPlay(asset) },
                    onPause = onPause,
                    onResume = onResume,
                    onStop = onStop,
                )
            }
        }
    }

    state.pendingAction?.let { action ->
        AudioAssetConfirmationDialog(
            action = action,
            onConfirm = onConfirmPendingAction,
            onDismiss = onDismissPendingAction,
        )
    }
}

@Composable
private fun SourceGenerationCard(
    label: String,
    buttonTag: String,
    enabled: Boolean,
    onGenerate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, maxLines = 2)
            OutlinedButton(
                onClick = onGenerate,
                enabled = enabled,
                modifier = Modifier.testTag(buttonTag),
            ) { Text("生成音频") }
        }
    }
}

@Composable
private fun AudioAssetCard(
    asset: AudioAssetRecord,
    playbackState: AudioAssetPlaybackState,
    operationInProgress: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.AudioAssets.asset(asset.id)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(asset.source.displayLabel())
            Text(
                if (asset.purgeRequestedAt != null) {
                    "等待受控清理"
                } else {
                    "状态：${asset.fileState.storageValue} · ${asset.config.targetFormat.storageValue}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    asset.purgeRequestedAt != null -> Unit
                    asset.fileState == AudioFileState.PENDING -> {
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !operationInProgress,
                            modifier = Modifier.testTag(
                                JianyuAutomationTags.AudioAssets.assetCancel(asset.id),
                            ),
                        ) { Text("取消生成") }
                    }
                    asset.fileState == AudioFileState.AVAILABLE -> {
                        val playing = playbackState as? AudioAssetPlaybackState.Playing
                        val paused = playbackState as? AudioAssetPlaybackState.Paused
                        when {
                            playing?.audioAssetId == asset.id -> {
                                OutlinedButton(onClick = onPause) { Text("暂停") }
                                OutlinedButton(onClick = onStop) { Text("停止") }
                            }
                            paused?.audioAssetId == asset.id -> {
                                Button(onClick = onResume) { Text("继续") }
                                OutlinedButton(onClick = onStop) { Text("停止") }
                            }
                            else -> Button(
                                onClick = onPlay,
                                enabled = !operationInProgress,
                                modifier = Modifier.testTag(
                                    JianyuAutomationTags.AudioAssets.assetPlay(asset.id),
                                ),
                            ) { Text("播放") }
                        }
                    }
                    else -> Button(
                        onClick = onRetry,
                        enabled = !operationInProgress,
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.AudioAssets.assetRetry(asset.id),
                        ),
                    ) { Text("重试") }
                }
                if (asset.purgeRequestedAt == null) {
                    TextButton(
                        onClick = onDelete,
                        enabled = !operationInProgress,
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.AudioAssets.assetDelete(asset.id),
                        ),
                    ) { Text("请求删除") }
                }
            }
        }
    }
}

@Composable
private fun AudioAssetConfirmationDialog(
    action: AudioAssetPendingAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, message, confirmLabel) = when (action) {
        is AudioAssetPendingAction.Generate -> Triple(
            "确认生成音频",
            "确认后才会创建独立 AudioAsset、排入网络任务并使用你的 BYOK API Key。",
            "确认生成",
        )
        is AudioAssetPendingAction.Retry -> Triple(
            "确认重试音频",
            "将重新读取最新来源内容并显式替换后台任务，不会静默自动重试。",
            "确认重试",
        )
        is AudioAssetPendingAction.Delete -> Triple(
            "确认请求删除",
            "这里只记录受控删除请求并阻止迟到回调；物理文件由后续清理流程再次确认。",
            "确认请求",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(JianyuAutomationTags.AudioAssets.CONFIRMATION_DIALOG),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(JianyuAutomationTags.AudioAssets.CONFIRM),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(JianyuAutomationTags.AudioAssets.DISMISS),
            ) { Text("取消") }
        },
    )
}

private fun AudioAssetSource.displayLabel(): String = when (this) {
    is AudioAssetSource.CompletedMessage -> "消息音频 · $messageId"
    is AudioAssetSource.ConfirmedArtifact -> "成果音频 · $artifactId"
}
