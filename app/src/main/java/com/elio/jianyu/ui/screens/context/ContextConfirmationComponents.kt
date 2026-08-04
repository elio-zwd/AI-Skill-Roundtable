package com.elio.jianyu.ui.screens.context

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.SnapshotContentState
import com.elio.jianyu.ui.components.JianyuMetadataRow

object ContextConfirmationTestTags {
    const val DIALOG = "context_confirmation_dialog"
    const val TOTAL = "context_confirmation_total"
    const val CONFIRM = "context_confirmation_confirm"
    const val CANCEL = "context_confirmation_cancel"

    fun candidate(sourceType: ContextSourceType, sourceId: String): String =
        "context_candidate_${sourceType.storageValue}_$sourceId"
}

@Composable
fun ContextConfirmationDialog(
    state: ContextConfirmationUiState,
    onDismiss: () -> Unit,
    onToggleSelected: (ContextSourceType, String) -> Unit,
    onNetworkAllowed: (ContextSourceType, String, Boolean) -> Unit,
    onSensitiveConfirmed: (ContextSourceType, String, Boolean) -> Unit,
    onExcerptChanged: (ContextSourceType, String, String) -> Unit,
    onConfirm: () -> Unit,
) {
    if (!state.visible) return
    AlertDialog(
        modifier = Modifier.testTag(ContextConfirmationTestTags.DIALOG),
        onDismissRequest = onDismiss,
        title = {
            Text(if (state.retryMode) "确认本次重试上下文" else "确认执行上下文")
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "所有资料和个人背景默认不发送。勾选、查看摘录并确认本次联网发送后，才会构造执行上下文。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.previousUsage.isNotEmpty()) {
                    Text("上次实际使用", style = MaterialTheme.typography.titleSmall)
                    state.previousUsage.forEach { usage ->
                        val label = if (usage.contentState == SnapshotContentState.PURGED) {
                            "内容已清除"
                        } else {
                            usage.title ?: "匿名来源"
                        }
                        Text("• $label（默认不继承）")
                    }
                }
                JianyuMetadataRow("固定上下文预估", state.baseContextCharacters.toString())
                JianyuMetadataRow("已选正文", state.selectedCharacters.toString())
                JianyuMetadataRow(
                    "总字符",
                    "${state.totalCharacters} / 24000",
                    modifier = Modifier.testTag(ContextConfirmationTestTags.TOTAL),
                )
                JianyuMetadataRow("剩余额度", state.remainingCharacters.coerceAtLeast(0).toString())
                if (state.tooLarge) {
                    Text(
                        "上下文超出 24,000 字符。请主动缩短摘录或移除来源；系统不会静默截断。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.networkPermissionMissing) {
                    Text(
                        "已选来源中存在未允许本次发送到模型服务的正文。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (state.sensitiveConfirmationMissing) {
                    Text(
                        "敏感来源需要额外确认；敏感标记不会自动禁止使用。",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (state.candidates.isEmpty()) {
                    Text("当前没有可选的活跃资料或个人背景。")
                } else {
                    state.candidates.forEach { candidate ->
                        ContextCandidateCard(
                            candidate = candidate,
                            onToggleSelected = {
                                onToggleSelected(candidate.sourceType, candidate.sourceId)
                            },
                            onNetworkAllowed = {
                                onNetworkAllowed(candidate.sourceType, candidate.sourceId, it)
                            },
                            onSensitiveConfirmed = {
                                onSensitiveConfirmed(candidate.sourceType, candidate.sourceId, it)
                            },
                            onExcerptChanged = {
                                onExcerptChanged(candidate.sourceType, candidate.sourceId, it)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.tooLarge &&
                    !state.networkPermissionMissing &&
                    !state.sensitiveConfirmationMissing,
                modifier = Modifier.testTag(ContextConfirmationTestTags.CONFIRM),
            ) {
                Text(if (state.retryMode) "确认并重试" else "确认选择")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(ContextConfirmationTestTags.CANCEL),
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ContextCandidateCard(
    candidate: ContextCandidateUi,
    onToggleSelected: () -> Unit,
    onNetworkAllowed: (Boolean) -> Unit,
    onSensitiveConfirmed: (Boolean) -> Unit,
    onExcerptChanged: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ContextConfirmationTestTags.candidate(candidate.sourceType, candidate.sourceId)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = candidate.selected, onCheckedChange = { onToggleSelected() })
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(candidate.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (candidate.sourceType == ContextSourceType.MATERIAL) "资料"
                        else "个人背景",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (candidate.sourceType == ContextSourceType.MATERIAL) {
                JianyuMetadataRow("来源类型", candidate.sourceKind.ifBlank { "未标记" })
                candidate.sourceLocator?.let { JianyuMetadataRow("来源定位", it) }
                JianyuMetadataRow(
                    "来源日期",
                    candidate.sourcePublishedAt?.toString() ?: "未知",
                )
                JianyuMetadataRow(
                    "采集时间",
                    candidate.sourceCapturedAt?.toString() ?: "未知",
                )
            }
            if (!candidate.selected) {
                Text(
                    if (candidate.sensitive) "敏感正文已隐藏，选中后查看并确认摘录。"
                    else candidate.originalContent.lineSequence().firstOrNull().orEmpty().take(120),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = candidate.selectedContent,
                    onValueChange = onExcerptChanged,
                    label = { Text("本次发送的精确正文或摘录") },
                    supportingText = { Text("${candidate.characterCount} 字符；Hash 将对应此文本") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Checkbox(
                        checked = candidate.networkAllowed,
                        onCheckedChange = onNetworkAllowed,
                    )
                    Text(
                        "允许本次发送给模型服务",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (candidate.sensitive) {
                    Row {
                        Checkbox(
                            checked = candidate.sensitiveConfirmed,
                            onCheckedChange = onSensitiveConfirmed,
                        )
                        Text(
                            "我已查看并确认发送敏感内容",
                            modifier = Modifier.padding(top = 12.dp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
