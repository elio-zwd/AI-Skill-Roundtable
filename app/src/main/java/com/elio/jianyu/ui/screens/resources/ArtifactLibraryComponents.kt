package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuStateCard
import dev.jeziellago.compose.markdowntext.MarkdownText

object ArtifactLibraryTestTags {
    const val LIBRARY = JianyuAutomationTags.Artifacts.LIBRARY
    const val EMPTY = JianyuAutomationTags.Artifacts.EMPTY
    const val FAILURE = JianyuAutomationTags.Artifacts.FAILURE
    const val SEARCH = JianyuAutomationTags.Artifacts.SEARCH
    const val TYPE_FILTER = JianyuAutomationTags.Artifacts.TYPE_FILTER
    const val HISTORY_FILTER = JianyuAutomationTags.Artifacts.HISTORY_FILTER
    const val DETAIL = JianyuAutomationTags.Artifacts.DETAIL
    const val SOURCES = JianyuAutomationTags.Artifacts.SOURCES
    const val OPEN_ISSUE = JianyuAutomationTags.Artifacts.OPEN_ISSUE

    fun item(artifactId: String): String = JianyuAutomationTags.Artifacts.item(artifactId)
    fun typeFilter(type: ArtifactType): String = "artifact_type_filter_${type.storageValue}"
}

@Composable
internal fun ArtifactLibraryContent(
    state: ArtifactLibraryUiState,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onTypesChange: (Set<ArtifactType>) -> Unit,
    onIncludeHistoryChange: (Boolean) -> Unit,
    onOpenArtifact: (String) -> Unit,
    onDismissArtifact: () -> Unit,
    onOpenIssue: (String, String) -> Unit,
    onCopyArtifact: (ArtifactLibraryItem) -> Unit = {},
) {
    when (state) {
        ArtifactLibraryUiState.Loading -> Column(
            modifier = Modifier.testTag(ArtifactLibraryTestTags.LIBRARY),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text("正在读取已确认成果")
            Text(
                "只读取本地已确认成果，不会调用模型或自动导出。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ArtifactLibraryUiState.Empty -> JianyuStateCard(
            title = "暂无正式成果",
            message = "阶段草稿只有在用户最终确认后才会出现在这里。",
            modifier = Modifier.testTag(ArtifactLibraryTestTags.EMPTY),
        )
        is ArtifactLibraryUiState.Failure -> JianyuStateCard(
            title = "成果库读取失败",
            message = "本地成果暂时无法读取，资料库不受影响。",
            actionLabel = "重试",
            onAction = onRetry,
            modifier = Modifier.testTag(ArtifactLibraryTestTags.FAILURE),
        )
        is ArtifactLibraryUiState.Content -> ArtifactLibraryBody(
            content = state,
            partialFailureCode = null,
            onQueryChange = onQueryChange,
            onTypesChange = onTypesChange,
            onIncludeHistoryChange = onIncludeHistoryChange,
            onOpenArtifact = onOpenArtifact,
            onDismissArtifact = onDismissArtifact,
            onOpenIssue = onOpenIssue,
            onCopyArtifact = onCopyArtifact,
        )
        is ArtifactLibraryUiState.PartialFailure -> ArtifactLibraryBody(
            content = state.content,
            partialFailureCode = state.errorCode,
            onQueryChange = onQueryChange,
            onTypesChange = onTypesChange,
            onIncludeHistoryChange = onIncludeHistoryChange,
            onOpenArtifact = onOpenArtifact,
            onDismissArtifact = onDismissArtifact,
            onOpenIssue = onOpenIssue,
            onCopyArtifact = onCopyArtifact,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtifactLibraryBody(
    content: ArtifactLibraryUiState.Content,
    partialFailureCode: String?,
    onQueryChange: (String) -> Unit,
    onTypesChange: (Set<ArtifactType>) -> Unit,
    onIncludeHistoryChange: (Boolean) -> Unit,
    onOpenArtifact: (String) -> Unit,
    onDismissArtifact: () -> Unit,
    onOpenIssue: (String, String) -> Unit,
    onCopyArtifact: (ArtifactLibraryItem) -> Unit,
) {
    var showFilters by remember { mutableStateOf(false) }
    var draftTypes by remember(content.selectedTypes) { mutableStateOf(content.selectedTypes) }
    var draftIncludeHistory by remember(content.includeHistory) {
        mutableStateOf(content.includeHistory)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ArtifactLibraryTestTags.LIBRARY),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        partialFailureCode?.let {
            JianyuStateCard(
                title = "部分成果未能读取",
                message = "已显示成功恢复的成果；其他会话或来源关系可稍后重试。",
                actionLabel = null,
            )
        }
        OutlinedTextField(
            value = content.query,
            onValueChange = onQueryChange,
            label = { Text("搜索成果标题或摘要") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ArtifactLibraryTestTags.SEARCH),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "已选 ${content.selectedTypes.size} 个类型 · " +
                    if (content.includeHistory) "含历史版本" else "仅最新版本",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    draftTypes = content.selectedTypes
                    draftIncludeHistory = content.includeHistory
                    showFilters = true
                },
            ) { Text("筛选") }
        }
        if (content.snapshot.revisionProblems.isNotEmpty()) {
            JianyuStateCard(
                title = "部分修订关系需要检查",
                message = "检测到孤儿、循环、跨阶段或分叉关系；历史仍保留，未将其误画为单链。",
            )
        }
        if (content.visibleItems.isEmpty()) {
            JianyuStateCard(
                title = "暂无匹配成果",
                message = "可调整搜索词、成果类型或显示历史版本。",
                modifier = Modifier.testTag(ArtifactLibraryTestTags.EMPTY),
            )
        } else {
            content.visibleItems.forEach { item ->
                ArtifactCard(item = item, onOpen = { onOpenArtifact(item.artifactId) })
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag(ArtifactLibraryTestTags.TYPE_FILTER),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("筛选成果", style = MaterialTheme.typography.titleLarge)
                Text("成果类型", style = MaterialTheme.typography.labelLarge)
                ArtifactType.entries.chunked(2).forEach { rowTypes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTypes.forEach { type ->
                            FilterChip(
                                selected = type in draftTypes,
                                onClick = {
                                    draftTypes = if (type in draftTypes) {
                                        draftTypes - type
                                    } else {
                                        draftTypes + type
                                    }
                                },
                                label = { Text(type.displayName) },
                                modifier = Modifier.testTag(ArtifactLibraryTestTags.typeFilter(type)),
                            )
                        }
                    }
                }
                FilterChip(
                    selected = draftIncludeHistory,
                    onClick = { draftIncludeHistory = !draftIncludeHistory },
                    label = { Text("显示历史版本") },
                    modifier = Modifier.testTag(ArtifactLibraryTestTags.HISTORY_FILTER),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = {
                            draftTypes = emptySet()
                            draftIncludeHistory = false
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("重置") }
                    TextButton(
                        onClick = { showFilters = false },
                        modifier = Modifier.weight(1f),
                    ) { Text("取消") }
                    Button(
                        onClick = {
                            onTypesChange(draftTypes)
                            onIncludeHistoryChange(draftIncludeHistory)
                            showFilters = false
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("应用") }
                }
            }
        }
    }

    content.selectedItem?.let { item ->
        ArtifactDetailDialog(
            item = item,
            onDismiss = onDismissArtifact,
            onOpenIssue = { onOpenIssue(item.issueId, item.stageId) },
            onCopy = { onCopyArtifact(item) },
        )
    }
}

@Composable
private fun ArtifactCard(
    item: ArtifactLibraryItem,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ArtifactLibraryTestTags.item(item.artifactId)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            JianyuMetadataRow("成果类型", item.artifactType?.displayName ?: item.rawArtifactType)
            JianyuMetadataRow("所属会话", item.issueTitle)
            JianyuMetadataRow("对话节点", item.stageTitle)
            JianyuMetadataRow("版本", "v${item.revisionNumber}${if (item.latest) " · 最新" else " · 历史"}")
            Text(
                item.contentSummary.ifBlank { "无可展示摘要" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpen) { Text("查看成果详情") }
        }
    }
}

@Composable
private fun ArtifactDetailDialog(
    item: ArtifactLibraryItem,
    onDismiss: () -> Unit,
    onOpenIssue: () -> Unit,
    onCopy: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag(ArtifactLibraryTestTags.DETAIL),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        "成果详情",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(item.title, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "已保存成果",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        JianyuMetadataRow("成果类型", item.artifactType?.displayName ?: item.rawArtifactType)
                        JianyuMetadataRow("所属会话", item.issueTitle)
                        JianyuMetadataRow("对话节点", item.stageTitle)
                        JianyuMetadataRow("保存时间", formatResourceTime(item.confirmedAt))
                        JianyuMetadataRow("版本", "v${item.revisionNumber}${if (item.latest) " · 最新" else " · 历史"}")
                        item.revisionOfArtifactId?.let {
                            JianyuMetadataRow("直接前序", it)
                        }
                    }
                }
                Text("正文", style = MaterialTheme.typography.titleMedium)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    MarkdownText(
                        markdown = item.content,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                JianyuStateCard(
                    title = "来自这段对话",
                    message = artifactSourceDescription(item),
                    modifier = Modifier.testTag(ArtifactLibraryTestTags.SOURCES),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                        Text("复制")
                    }
                    Button(
                        onClick = onOpenIssue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(ArtifactLibraryTestTags.OPEN_ISSUE),
                    ) {
                        Text("打开来源会话")
                    }
                }
            }
        }
    }
}

private fun artifactSourceDescription(item: ArtifactLibraryItem): String {
    if (!item.sourcesAvailable) {
        return "来源关系暂时无法读取；未根据成果正文或界面状态猜测来源。"
    }
    return buildString {
        append("保存时关联消息 ")
        append(item.sourceMessageIds.size)
        append(" 条；执行记录 ")
        append(item.sourceRunIds.size)
        append(" 个；草稿修订 ")
        append(item.sourceDraftRevisionIds.size)
        append(" 个；资料使用快照 ")
        append(item.sourceMaterialUsageSnapshotIds.size)
        append(" 个。")
        if (item.sourceRunIds.isNotEmpty()) {
            append("\n执行记录：")
            append(item.sourceRunIds.joinToString())
        }
        if (item.sourceDraftRevisionIds.isNotEmpty()) {
            append("\n草稿修订：")
            append(item.sourceDraftRevisionIds.joinToString())
        }
    }
}
