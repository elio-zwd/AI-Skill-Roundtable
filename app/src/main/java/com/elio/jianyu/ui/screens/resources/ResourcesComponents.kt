package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.ui.components.JianyuMetadataRow

object ResourcesTestTags {
    const val SCREEN = "resources_screen"
    const val MATERIALS_TAB = "resources_tab_materials"
    const val ARTIFACTS_TAB = "resources_tab_artifacts"
    const val MATERIAL_LIBRARY = "resources_material_library"
    const val PERSONAL_CONTEXT_LIBRARY = "resources_personal_context_library"
    const val SEARCH = "resources_search"
    const val ADD = "resources_add"
    const val EMPTY_STATE = "resources_empty_state"
    const val EDITOR = "resources_editor"
    const val PURGE_CONFIRMATION = "resources_purge_confirmation"

    fun material(id: String): String = "resources_material_$id"
    fun personalContext(id: String): String = "resources_personal_context_$id"
}

@Composable
internal fun ResourceLifecycleFilters(
    selected: Set<ContextSourceLifecycle>,
    onSelected: (Set<ContextSourceLifecycle>) -> Unit,
) {
    val choices = listOf(
        ContextSourceLifecycle.ACTIVE,
        ContextSourceLifecycle.DISABLED,
        ContextSourceLifecycle.ARCHIVED,
        ContextSourceLifecycle.DELETED,
        ContextSourceLifecycle.PURGE_REQUESTED,
        ContextSourceLifecycle.PURGED,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("显示状态", style = MaterialTheme.typography.labelLarge)
        choices.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { lifecycle ->
                    FilterChip(
                        selected = lifecycle in selected,
                        onClick = {
                            onSelected(
                                if (lifecycle in selected) selected - lifecycle
                                else selected + lifecycle,
                            )
                        },
                        label = { Text(lifecycle.label()) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun MaterialCard(
    item: MaterialUiItem,
    onEdit: () -> Unit,
    onLifecycle: (ContextSourceLifecycle) -> Unit,
    onRequestPurge: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResourcesTestTags.material(item.id)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            JianyuMetadataRow("状态", item.lifecycle.label())
            JianyuMetadataRow("来源类型", item.sourceType.ifBlank { "匿名占位" })
            JianyuMetadataRow("所属议题", item.issueId)
            JianyuMetadataRow("所属阶段", item.stageId ?: "整个议题")
            item.sourceLocator?.let { JianyuMetadataRow("来源定位", it) }
            JianyuMetadataRow("采集时间", item.sourceCapturedAt?.toString() ?: "未知")
            JianyuMetadataRow("来源日期", item.sourcePublishedAt?.toString() ?: "未知")
            Text(item.contentPreview, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ResourceActions(
                lifecycle = item.lifecycle,
                onEdit = onEdit,
                onLifecycle = onLifecycle,
                onRequestPurge = onRequestPurge,
            )
        }
    }
}

@Composable
internal fun PersonalContextCard(
    item: PersonalContextUiItem,
    onEdit: () -> Unit,
    onLifecycle: (ContextSourceLifecycle) -> Unit,
    onRequestPurge: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResourcesTestTags.personalContext(item.id)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            JianyuMetadataRow("状态", item.lifecycle.label())
            Text(item.contentPreview, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.sensitive && item.lifecycle != ContextSourceLifecycle.PURGED) {
                Text("敏感内容", color = MaterialTheme.colorScheme.error)
            }
            ResourceActions(
                lifecycle = item.lifecycle,
                onEdit = onEdit,
                onLifecycle = onLifecycle,
                onRequestPurge = onRequestPurge,
            )
        }
    }
}

@Composable
private fun ResourceActions(
    lifecycle: ContextSourceLifecycle,
    onEdit: () -> Unit,
    onLifecycle: (ContextSourceLifecycle) -> Unit,
    onRequestPurge: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lifecycle in setOf(
                ContextSourceLifecycle.ACTIVE,
                ContextSourceLifecycle.DISABLED,
                ContextSourceLifecycle.ARCHIVED,
            )
        ) {
            OutlinedButton(onClick = onEdit) { Text("编辑") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (lifecycle) {
                ContextSourceLifecycle.ACTIVE -> {
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.DISABLED) }) {
                        Text("停用")
                    }
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.ARCHIVED) }) {
                        Text("归档")
                    }
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.DELETED) }) {
                        Text("删除")
                    }
                }
                ContextSourceLifecycle.DISABLED -> {
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.ACTIVE) }) {
                        Text("启用")
                    }
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.ARCHIVED) }) {
                        Text("归档")
                    }
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.DELETED) }) {
                        Text("删除")
                    }
                }
                ContextSourceLifecycle.ARCHIVED -> {
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.ACTIVE) }) {
                        Text("恢复")
                    }
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.DELETED) }) {
                        Text("删除")
                    }
                }
                ContextSourceLifecycle.DELETED -> {
                    TextButton(onClick = { onLifecycle(ContextSourceLifecycle.ACTIVE) }) {
                        Text("恢复")
                    }
                    TextButton(onClick = onRequestPurge) { Text("彻底清除") }
                }
                ContextSourceLifecycle.PURGE_REQUESTED -> Text("等待清除确认")
                ContextSourceLifecycle.PURGED -> Text("匿名历史占位不可恢复")
            }
        }
    }
}

@Composable
internal fun ResourceEditorDialog(
    draft: ResourceEditorDraft,
    issues: List<ResourceIssueOption>,
    onChange: (ResourceEditorDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .testTag(ResourcesTestTags.EDITOR),
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (draft.sourceId == null) "新建${draft.sourceType.label()}"
                else "编辑${draft.sourceType.label()}",
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (draft.sourceType == ContextSourceType.MATERIAL) {
                    Text("所属议题", style = MaterialTheme.typography.labelLarge)
                    issues.forEach { issue ->
                        FilterChip(
                            selected = draft.issueId == issue.issueId,
                            onClick = {
                                onChange(
                                    draft.copy(
                                        issueId = issue.issueId,
                                        stageId = issue.stages.firstOrNull()?.stageId,
                                    ),
                                )
                            },
                            label = { Text(issue.title) },
                        )
                    }
                    issues.firstOrNull { it.issueId == draft.issueId }?.let { issue ->
                        Text("关联阶段（可选）", style = MaterialTheme.typography.labelLarge)
                        FilterChip(
                            selected = draft.stageId == null,
                            onClick = { onChange(draft.copy(stageId = null)) },
                            label = { Text("整个议题") },
                        )
                        issue.stages.forEach { stage ->
                            FilterChip(
                                selected = draft.stageId == stage.stageId,
                                onClick = { onChange(draft.copy(stageId = stage.stageId)) },
                                label = { Text(stage.title) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.sourceKind,
                        onValueChange = { onChange(draft.copy(sourceKind = it)) },
                        label = { Text("来源类型") },
                        supportingText = { Text("例如 note、url、excerpt") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.sourceLocator,
                        onValueChange = { onChange(draft.copy(sourceLocator = it)) },
                        label = { Text("来源定位（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onChange(draft.copy(title = it)) },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.content,
                    onValueChange = { onChange(draft.copy(content = it)) },
                    label = { Text("正文或确认摘录") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Checkbox(
                        checked = draft.sensitive,
                        onCheckedChange = { onChange(draft.copy(sensitive = it)) },
                    )
                    Text("标记为敏感内容", modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    "正文仅保存在本地；是否发送给模型将在每次执行前单独确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun PurgeConfirmationDialog(
    confirmation: ResourcePurgeConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(ResourcesTestTags.PURGE_CONFIRMATION),
        onDismissRequest = onDismiss,
        title = { Text("彻底清除“${confirmation.title}”") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("此操作不可恢复，历史回答保留，但相关正文会变成匿名占位。")
                JianyuMetadataRow("关联议题", confirmation.impact.issueCount.toString())
                JianyuMetadataRow("关联阶段", confirmation.impact.stageCount.toString())
                JianyuMetadataRow("使用快照", confirmation.impact.usageSnapshotCount.toString())
                JianyuMetadataRow("关联运行", confirmation.impact.runCount.toString())
                Text(
                    "匿名占位不会包含原始标题、来源类型、正文、Hash 或敏感类别。",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("确认彻底清除") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("返回删除状态") } },
    )
}

internal fun ContextSourceLifecycle.label(): String = when (this) {
    ContextSourceLifecycle.ACTIVE -> "活跃"
    ContextSourceLifecycle.DISABLED -> "已停用"
    ContextSourceLifecycle.ARCHIVED -> "已归档"
    ContextSourceLifecycle.DELETED -> "已删除"
    ContextSourceLifecycle.PURGE_REQUESTED -> "待清除"
    ContextSourceLifecycle.PURGED -> "已清除"
}

private fun ContextSourceType.label(): String = when (this) {
    ContextSourceType.MATERIAL -> "资料"
    ContextSourceType.PERSONAL_CONTEXT -> "个人背景"
}
