package com.elio.jianyu.ui.screens.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

object IssuesTestTags {
    const val SCREEN = "issues_screen"
    const val LOADING = "issues_loading"
    const val EMPTY = "issues_empty"
    const val FAILURE = "issues_failure"
    const val ACTIVE_SECTION = "issues_active_section"
    const val ARCHIVED_SECTION = "issues_archived_section"
    const val TRASHED_SECTION = "issues_trashed_section"
    const val RECOVERY_SCREEN = "issue_recovery_screen"
    const val RECOVERY_FAILURE = "issue_recovery_failure"

    fun issue(issueId: String): String = "issue_navigation_$issueId"
}

@Composable
fun IssuesRoute(
    onOpenIssue: (issueId: String, stageId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: IssuesViewModel = viewModel(
        factory = IssuesViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.issuesState.collectAsState()
    IssuesScreen(
        state = state,
        onRetry = viewModel::refresh,
        onOpenIssue = onOpenIssue,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
fun IssuesScreen(
    state: IssuesUiState,
    onRetry: () -> Unit,
    onOpenIssue: (issueId: String, stageId: String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    JianyuPageShell(
        title = "议题",
        subtitle = "活跃、归档与恢复",
        onOpenSettings = onOpenSettings,
        modifier = Modifier.testTag(IssuesTestTags.SCREEN),
    ) {
        when (state) {
            IssuesUiState.Loading -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(IssuesTestTags.LOADING),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("正在读取议题")
                Text(
                    text = "当前只读取导航摘要，不创建议题、阶段或运行。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IssuesUiState.Empty -> JianyuStateCard(
                title = "还没有议题",
                message = "议题是持续承载背景、阶段、资料与成果的容器。当前页面不会在加载时自动创建内容。",
                modifier = Modifier.testTag(IssuesTestTags.EMPTY),
            )

            is IssuesUiState.Failure -> JianyuStateCard(
                title = "议题读取失败",
                message = state.message,
                actionLabel = "重试",
                onAction = onRetry,
                modifier = Modifier.testTag(IssuesTestTags.FAILURE),
            )

            is IssuesUiState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    IssueSection(
                        title = "活跃议题",
                        items = state.active,
                        emptyMessage = "暂无活跃议题",
                        testTag = IssuesTestTags.ACTIVE_SECTION,
                        onOpenIssue = onOpenIssue,
                    )
                }
                item {
                    IssueSection(
                        title = "已归档",
                        items = state.archived,
                        emptyMessage = "暂无归档议题",
                        testTag = IssuesTestTags.ARCHIVED_SECTION,
                        onOpenIssue = onOpenIssue,
                    )
                }
                item {
                    IssueSection(
                        title = "回收站",
                        items = state.trashed,
                        emptyMessage = "回收站为空",
                        testTag = IssuesTestTags.TRASHED_SECTION,
                        onOpenIssue = onOpenIssue,
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueSection(
    title: String,
    items: List<IssueNavigationUiItem>,
    emptyMessage: String,
    testTag: String,
    onOpenIssue: (issueId: String, stageId: String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        if (items.isEmpty()) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.forEach { item ->
                IssueNavigationCard(
                    item = item,
                    onClick = {
                        onOpenIssue(item.issueId, item.currentStageId)
                    },
                )
            }
        }
    }
}

@Composable
private fun IssueNavigationCard(
    item: IssueNavigationUiItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "打开议题 ${item.title}",
                onClick = onClick,
            )
            .testTag(IssuesTestTags.issue(item.issueId)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
            )
            JianyuMetadataRow(
                label = "状态",
                value = item.lifecycleState.toDisplayLabel(),
            )
            JianyuMetadataRow(
                label = "当前阶段",
                value = item.currentStageTitle ?: "尚无阶段",
            )
            JianyuMetadataRow(
                label = "可恢复运行",
                value = item.activeOrRecoverableRunCount.toString(),
            )
        }
    }
}

@Composable
fun IssueRecoveryRoute(
    issueId: String?,
    stageId: String?,
    onBack: () -> Unit,
    viewModel: IssueRecoveryViewModel = viewModel(
        factory = IssueRecoveryViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.recoveryState.collectAsState()
    LaunchedEffect(issueId, stageId) {
        viewModel.recover(issueId, stageId)
    }
    IssueRecoveryScreen(
        state = state,
        onBack = onBack,
    )
}

@Composable
fun IssueRecoveryScreen(
    state: IssueRecoveryUiState,
    onBack: () -> Unit,
) {
    JianyuPageShell(
        title = "议题定位",
        subtitle = "按稳定 ID 恢复",
        onBack = onBack,
        modifier = Modifier.testTag(IssuesTestTags.RECOVERY_SCREEN),
    ) {
        when (state) {
            IssueRecoveryUiState.Loading -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("正在恢复议题位置")
            }

            is IssueRecoveryUiState.Failure -> JianyuStateCard(
                title = "无法定位议题",
                message = state.message,
                modifier = Modifier.testTag(IssuesTestTags.RECOVERY_FAILURE),
            )

            is IssueRecoveryUiState.Content -> {
                JianyuStateCard(
                    title = state.title,
                    message = "已恢复到现有议题位置。本页面不会创建新的议题、阶段或运行。",
                )
                JianyuMetadataRow(
                    label = "生命周期",
                    value = state.lifecycleState.toDisplayLabel(),
                )
                JianyuMetadataRow(
                    label = "阶段",
                    value = state.selectedStageTitle ?: "尚无阶段",
                )
                JianyuMetadataRow(
                    label = "阶段数量",
                    value = state.stageCount.toString(),
                )
                JianyuMetadataRow(
                    label = "可恢复运行",
                    value = state.activeOrRecoverableRunCount.toString(),
                )
            }
        }
    }
}

private fun IssueLifecycleState.toDisplayLabel(): String = when (this) {
    IssueLifecycleState.ACTIVE -> "活跃"
    IssueLifecycleState.ARCHIVED -> "已归档"
    IssueLifecycleState.TRASHED -> "回收站"
}
