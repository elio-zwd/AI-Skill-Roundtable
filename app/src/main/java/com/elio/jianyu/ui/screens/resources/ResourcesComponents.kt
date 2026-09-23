package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
    const val MATERIAL_DETAIL = "resources_material_detail"
    const val ADD_SHEET = "resources_add_sheet"

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
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onLifecycle: (ContextSourceLifecycle) -> Unit,
    onRequestPurge: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
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
            JianyuMetadataRow("所属会话", item.issueTitle ?: "会话信息暂不可用")
            JianyuMetadataRow("对话节点", item.stageTitle ?: if (item.stageId == null) "整个会话" else "节点信息暂不可用")
            item.sourceLocator?.let { JianyuMetadataRow("来源定位", it) }
            JianyuMetadataRow("采集时间", item.sourceCapturedAt?.toString() ?: "未知")
            JianyuMetadataRow("来源日期", item.sourcePublishedAt?.toString() ?: "未知")
            Text(
                if (item.sensitive) "敏感内容已隐藏" else item.contentPreview,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ResourceActions(
                lifecycle = item.lifecycle,
                onEdit = onEdit,
                onLifecycle = onLifecycle,
                onRequestPurge = onRequestPurge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddMaterialSheet(
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ResourcesTestTags.ADD_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("新增资料", style = MaterialTheme.typography.titleLarge)
            Text(
                "选择资料来源，添加后仍由你决定是否带入对话。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AddMaterialChoice("上传文件", "选择 PDF、Word 或文本；仅可提取正文的格式能够保存") { onChoose("file") }
            AddMaterialChoice("添加链接", "保存网页地址与来源信息") { onChoose("url") }
            AddMaterialChoice("粘贴文本", "添加摘录、笔记或一段参考内容") { onChoose("excerpt") }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("资料是对话的输入与依据，不会自动成为成果。")
                    Text(
                        "敏感资料需要你在使用时再次确认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun AddMaterialChoice(title: String, description: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun MaterialDetailDialog(
    item: MaterialUiItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onLifecycle: (ContextSourceLifecycle) -> Unit,
    onRequestPurge: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(ResourcesTestTags.MATERIAL_DETAIL),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text("资料详情", style = MaterialTheme.typography.titleLarge)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        JianyuMetadataRow("类型", materialKindLabel(item.sourceType))
                        JianyuMetadataRow("状态", item.lifecycle.label())
                        JianyuMetadataRow("所属会话", item.issueTitle ?: "会话信息暂不可用")
                        item.sourceLocator?.takeIf(String::isNotBlank)?.let {
                            JianyuMetadataRow("来源", it)
                        }
                        JianyuMetadataRow("更新时间", formatResourceTime(item.updatedAt))
                        if (item.sensitive) {
                            Text("敏感资料：正文仅在你明确选择后才可用于对话。", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text("内容预览", style = MaterialTheme.typography.titleMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        if (item.sensitive) "敏感内容已隐藏，点击编辑后查看。" else item.contentPreview,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ResourceActions(
                    lifecycle = item.lifecycle,
                    onEdit = onEdit,
                    onLifecycle = onLifecycle,
                    onRequestPurge = onRequestPurge,
                )
                Text(
                    "资料只是对话的输入与依据，不会自动发送给任何 Skill 角色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    message: String?,
    saving: Boolean,
    onChange: (ResourceEditorDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val isMaterial = draft.sourceType == ContextSourceType.MATERIAL
    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(ResourcesTestTags.EDITOR),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, enabled = !saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "取消")
                    }
                    Text(
                        resourceEditorTitle(draft),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    message?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    if (isMaterial) {
                        Text(
                            "${materialKindLabel(draft.sourceKind)}只保存在本地，保存后不会自动带入对话。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("所属会话", style = MaterialTheme.typography.labelLarge)
                        if (issues.isEmpty()) {
                            Text(
                                "当前没有可用会话。请先返回【对话】开始一个会话，再添加资料。",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
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
                            Text("关联对话节点（可选）", style = MaterialTheme.typography.labelLarge)
                            FilterChip(
                                selected = draft.stageId == null,
                                onClick = { onChange(draft.copy(stageId = null)) },
                                label = { Text("整个会话") },
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
                            value = draft.sourceLocator,
                            onValueChange = { onChange(draft.copy(sourceLocator = it)) },
                            label = {
                                Text(
                                    when (draft.sourceKind) {
                                        "url" -> "链接地址"
                                        "file" -> "本地文件"
                                        else -> "来源说明（可选）"
                                    },
                                )
                            },
                            enabled = draft.sourceKind != "file",
                            supportingText = if (draft.sourceKind == "url") {
                                { Text("这里只保存地址，不会联网抓取网页。") }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        draft.importedFileSummary?.let { summary ->
                            Text(
                                summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
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
                        label = {
                            Text(if (draft.sourceKind == "url") "备注或确认摘录" else "正文或确认摘录")
                        },
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = onSave,
                        enabled = !saving && (!isMaterial || draft.issueId.isNotBlank()),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (saving) "保存中…" else "保存")
                    }
                }
            }
        }
    }
}

private fun resourceEditorTitle(draft: ResourceEditorDraft): String {
    if (draft.sourceId != null) return "编辑${draft.sourceType.label()}"
    if (draft.sourceType == ContextSourceType.PERSONAL_CONTEXT) return "新建个人背景"
    return when (draft.sourceKind) {
        "file" -> "确认文件资料"
        "url" -> "添加链接"
        "excerpt" -> "粘贴文本"
        else -> "新建资料"
    }
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
                JianyuMetadataRow("关联会话", confirmation.impact.issueCount.toString())
                JianyuMetadataRow("关联对话节点", confirmation.impact.stageCount.toString())
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
