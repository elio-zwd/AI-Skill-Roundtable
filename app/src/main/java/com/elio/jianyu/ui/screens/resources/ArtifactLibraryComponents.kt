package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuStateCard

object ArtifactLibraryTestTags {
    const val LIBRARY = "artifact_library"
    const val EMPTY = "artifact_library_empty"
    const val FAILURE = "artifact_library_failure"
    const val SEARCH = "artifact_search"
    const val TYPE_FILTER = "artifact_type_filter"
    const val HISTORY_FILTER = "artifact_revision_history"
    const val DETAIL = "artifact_detail"
    const val SOURCES = "artifact_sources"
    const val OPEN_ISSUE = "artifact_open_issue"

    fun item(artifactId: String): String = "artifact_item_${stableTagPart(artifactId)}"

    private fun stableTagPart(value: String): String = value
        .lowercase()
        .map { character ->
            if (character.isLetterOrDigit() || character == '_' || character == '-') {
                character
            } else {
                '_'
            }
        }
        .joinToString(separator = "")
        .trim('_')
        .ifBlank { "unknown" }
        .take(80)
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
        )
    }
}

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
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ArtifactLibraryTestTags.LIBRARY),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        partialFailureCode?.let {
            JianyuStateCard(
                title = "部分成果未能读取",
                message = "已显示成功恢复的成果；其他议题可稍后重试。",
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
        Text("成果类型", style = MaterialTheme.typography.labelLarge)
        ArtifactType.entries.chunked(2).forEach { rowTypes ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ArtifactLibraryTestTags.TYPE_FILTER),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTypes.forEach { type ->
                    FilterChip(
                        selected = type in content.selectedTypes,
                        onClick = {
                            onTypesChange(
                                if (type in content.selectedTypes) {
                                    content.selectedTypes - type
                                } else {
                                    content.selectedTypes + type
                                },
                            )
                        },
                        label = { Text(type.displayName) },
                    )
                }
            }
        }
        FilterChip(
            selected = content.includeHistory,
            onClick = { onIncludeHistoryChange(!content.includeHistory) },
            label = { Text("显示历史版本") },
            modifier = Modifier.testTag(ArtifactLibraryTestTags.HISTORY_FILTER),
        )
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

    content.selectedItem?.let { item ->
        ArtifactDetailDialog(
            item = item,
            onDismiss = onDismissArtifact,
            onOpenIssue = { onOpenIssue(item.issueId, item.stageId) },
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
            JianyuMetadataRow("所属议题", item.issueTitle)
            JianyuMetadataRow("所属阶段", item.stageTitle)
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
) {
    AlertDialog(
        modifier = Modifier
            .widthIn(max = 680.dp)
            .testTag(ArtifactLibraryTestTags.DETAIL),
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                JianyuMetadataRow("成果类型", item.artifactType?.displayName ?: item.rawArtifactType)
                JianyuMetadataRow("所属议题", item.issueTitle)
                JianyuMetadataRow("所属阶段", item.stageTitle)
                JianyuMetadataRow("版本", "v${item.revisionNumber}")
                item.revisionOfArtifactId?.let {
                    JianyuMetadataRow("直接前序", it)
                }
                Text("正文", style = MaterialTheme.typography.labelLarge)
                Text(item.content)
                JianyuStateCard(
                    title = "来源追溯",
                    message = "成果来源关系已在 Room 中保留；阶段 A 公共恢复接口尚未提供关联行详情，未在此猜测来源。",
                    modifier = Modifier.testTag(ArtifactLibraryTestTags.SOURCES),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenIssue,
                modifier = Modifier.testTag(ArtifactLibraryTestTags.OPEN_ISSUE),
            ) {
                Text("返回对应议题")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
