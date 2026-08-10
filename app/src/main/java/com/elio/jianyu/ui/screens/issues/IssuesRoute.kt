package com.elio.jianyu.ui.screens.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.lifecycle.JianyuLifecycleRuntime
import com.elio.jianyu.ui.automation.JianyuLifecycleAutomationTags
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

private enum class IssueListTab {
    ACTIVE,
    ARCHIVED,
    TRASHED,
}

@Composable
fun IssuesRoute(
    repository: JianyuRepository,
    lifecycleRuntime: JianyuLifecycleRuntime,
    onOpenIssue: (issueId: String, stageId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: IssuesViewModel = viewModel(
        factory = IssuesViewModel.factory(repository),
    ),
    lifecycleViewModel: IssueLifecycleViewModel = viewModel(
        factory = IssueLifecycleViewModel.factory(lifecycleRuntime),
    ),
) {
    val state by viewModel.issuesState.collectAsState()
    val lifecycleState by lifecycleViewModel.state.collectAsState()

    LaunchedEffect(lifecycleViewModel, onOpenIssue) {
        lifecycleViewModel.events.collect { event ->
            when (event) {
                is IssueLifecycleUiEvent.NavigateToIssue -> onOpenIssue(event.issueId, null)
                is IssueLifecycleUiEvent.NavigateToRelatedIssue -> onOpenIssue(event.issueId, null)
                is IssueLifecycleUiEvent.ShowStableError -> Unit
            }
            viewModel.refresh()
        }
    }
    LaunchedEffect(lifecycleState) {
        when (lifecycleState) {
            is IssueLifecycleUiState.Archived,
            is IssueLifecycleUiState.RelatedIssueCreated,
            is IssueLifecycleUiState.Resumed,
            is IssueLifecycleUiState.Trashed,
            is IssueLifecycleUiState.TrashRestored,
            is IssueLifecycleUiState.PurgeCompleted,
            -> viewModel.refresh()
            else -> Unit
        }
    }

    IssuesScreen(
        state = state,
        onRetry = viewModel::refresh,
        onOpenIssue = onOpenIssue,
        onOpenSettings = onOpenSettings,
        onArchive = lifecycleViewModel::beginArchive,
        onResume = lifecycleViewModel::beginResume,
        onRelatedIssue = lifecycleViewModel::beginRelatedIssue,
        onMoveToTrash = lifecycleViewModel::beginMoveToTrash,
        onRestoreFromTrash = lifecycleViewModel::restoreFromTrash,
        onPurge = lifecycleViewModel::beginPurge,
    )
    IssueLifecycleDialogs(
        state = lifecycleState,
        onDismiss = lifecycleViewModel::dismiss,
        onWait = lifecycleViewModel::waitForTasks,
        onRefreshWaiting = lifecycleViewModel::refreshWaiting,
        onStop = lifecycleViewModel::stopTasks,
        onSummaryChange = lifecycleViewModel::updateArchiveSummary,
        onArchiveConfirm = lifecycleViewModel::confirmArchive,
        onTrashConfirm = lifecycleViewModel::confirmMoveToTrash,
        onResumeChange = lifecycleViewModel::updateResumeChangeNote,
        onResumeNoChange = lifecycleViewModel::selectNoChange,
        onResumeConfirm = lifecycleViewModel::confirmResume,
        onRelatedTitleChange = lifecycleViewModel::updateRelatedTitle,
        onRelatedObjectiveChange = lifecycleViewModel::updateRelatedObjective,
        onRelatedConfirm = lifecycleViewModel::confirmRelatedIssue,
        onPurgeFirstConfirm = lifecycleViewModel::confirmPurgeImpact,
        onPurgeFinalConfirm = lifecycleViewModel::confirmPurgeFinal,
        onPurgeRetry = lifecycleViewModel::retryPurge,
        onPurgeCancel = lifecycleViewModel::cancelPurge,
    )
}

@Composable
fun IssuesScreen(
    state: IssuesUiState,
    onRetry: () -> Unit,
    onOpenIssue: (issueId: String, stageId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onArchive: (String) -> Unit = {},
    onResume: (String) -> Unit = {},
    onRelatedIssue: (String) -> Unit = {},
    onMoveToTrash: (String) -> Unit = {},
    onRestoreFromTrash: (String) -> Unit = {},
    onPurge: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(IssueListTab.ACTIVE) }
    JianyuPageShell(
        title = "议题",
        subtitle = null,
        onOpenSettings = onOpenSettings,
        contentScrollable = true,
        modifier = Modifier.testTag(IssuesTestTags.SCREEN),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("你的议题", style = MaterialTheme.typography.headlineMedium)
            Text(
                "按状态查看，并从原来的阶段继续。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == IssueListTab.ACTIVE,
                onClick = { selectedTab = IssueListTab.ACTIVE },
                text = { Text("进行中") },
            )
            Tab(
                selected = selectedTab == IssueListTab.ARCHIVED,
                onClick = { selectedTab = IssueListTab.ARCHIVED },
                text = { Text("已归档") },
            )
            Tab(
                selected = selectedTab == IssueListTab.TRASHED,
                onClick = { selectedTab = IssueListTab.TRASHED },
                text = { Text("回收站") },
            )
        }
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

            is IssuesUiState.Content -> {
                val section = when (selectedTab) {
                    IssueListTab.ACTIVE -> IssueSectionSpec(
                        title = "进行中的议题",
                        items = state.active,
                        emptyMessage = "暂无进行中的议题",
                        testTag = IssuesTestTags.ACTIVE_SECTION,
                    )
                    IssueListTab.ARCHIVED -> IssueSectionSpec(
                        title = "已归档",
                        items = state.archived,
                        emptyMessage = "暂无归档议题；归档后仍可恢复继续。",
                        testTag = IssuesTestTags.ARCHIVED_SECTION,
                    )
                    IssueListTab.TRASHED -> IssueSectionSpec(
                        title = "回收站",
                        items = state.trashed,
                        emptyMessage = "回收站为空，不会自动过期或自动清空。",
                        testTag = IssuesTestTags.TRASHED_SECTION,
                    )
                }
                IssueSection(
                    title = section.title,
                    items = section.items,
                    emptyMessage = section.emptyMessage,
                    testTag = section.testTag,
                    onOpenIssue = onOpenIssue,
                    onArchive = onArchive,
                    onResume = onResume,
                    onRelatedIssue = onRelatedIssue,
                    onMoveToTrash = onMoveToTrash,
                    onRestoreFromTrash = onRestoreFromTrash,
                    onPurge = onPurge,
                )
            }
        }
    }
}

private data class IssueSectionSpec(
    val title: String,
    val items: List<IssueNavigationUiItem>,
    val emptyMessage: String,
    val testTag: String,
)

@Composable
private fun IssueSection(
    title: String,
    items: List<IssueNavigationUiItem>,
    emptyMessage: String,
    testTag: String,
    onOpenIssue: (issueId: String, stageId: String?) -> Unit,
    onArchive: (String) -> Unit,
    onResume: (String) -> Unit,
    onRelatedIssue: (String) -> Unit,
    onMoveToTrash: (String) -> Unit,
    onRestoreFromTrash: (String) -> Unit,
    onPurge: (String) -> Unit,
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
                    onClick = { onOpenIssue(item.issueId, item.currentStageId) },
                    onArchive = { onArchive(item.issueId) },
                    onResume = { onResume(item.issueId) },
                    onRelatedIssue = { onRelatedIssue(item.issueId) },
                    onMoveToTrash = { onMoveToTrash(item.issueId) },
                    onRestoreFromTrash = { onRestoreFromTrash(item.issueId) },
                    onPurge = { onPurge(item.issueId) },
                )
            }
        }
    }
}

@Composable
private fun IssueNavigationCard(
    item: IssueNavigationUiItem,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onResume: () -> Unit,
    onRelatedIssue: () -> Unit,
    onMoveToTrash: () -> Unit,
    onRestoreFromTrash: () -> Unit,
    onPurge: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.lifecycleState.toDisplayLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (item.lifecycleState) {
                        IssueLifecycleState.ACTIVE -> MaterialTheme.colorScheme.secondary
                        IssueLifecycleState.ARCHIVED -> MaterialTheme.colorScheme.onSurfaceVariant
                        IssueLifecycleState.TRASHED -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = buildString {
                    append(item.currentStageTitle ?: "尚无阶段")
                    if (item.activeOrRecoverableRunCount > 0) {
                        append(" · ")
                        append(item.activeOrRecoverableRunCount)
                        append(" 项可恢复运行")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                when (item.lifecycleState) {
                    IssueLifecycleState.ACTIVE -> {
                        TextButton(
                            onClick = onArchive,
                            modifier = Modifier.testTag(
                                JianyuLifecycleAutomationTags.IssueLifecycle.ARCHIVE_BUTTON,
                            ),
                        ) { Text("归档") }
                        TextButton(
                            onClick = onMoveToTrash,
                            modifier = Modifier.testTag(
                                JianyuLifecycleAutomationTags.IssueLifecycle.MOVE_TO_TRASH,
                            ),
                        ) { Text("删除") }
                    }
                    IssueLifecycleState.ARCHIVED -> {
                        TextButton(
                            onClick = onResume,
                            modifier = Modifier.testTag(
                                JianyuLifecycleAutomationTags.Resume.BUTTON,
                            ),
                        ) { Text("继续") }
                        TextButton(
                            onClick = onRelatedIssue,
                            modifier = Modifier.testTag(
                                JianyuLifecycleAutomationTags.RelatedIssue.BUTTON,
                            ),
                        ) { Text("关联新议题") }
                        TextButton(
                            onClick = onMoveToTrash,
                            modifier = Modifier.testTag(
                                JianyuLifecycleAutomationTags.IssueLifecycle.MOVE_TO_TRASH,
                            ),
                        ) { Text("删除") }
                    }
                    IssueLifecycleState.TRASHED -> {
                        TextButton(
                            onClick = onRestoreFromTrash,
                            modifier = Modifier.testTag(
                                JianyuLifecycleAutomationTags.IssueLifecycle.RESTORE_FROM_TRASH,
                            ),
                        ) { Text("恢复") }
                        TextButton(
                            onClick = onPurge,
                            modifier = Modifier.testTag(
                                JianyuLifecycleAutomationTags.Purge.BUTTON,
                            ),
                        ) { Text("彻底清除") }
                    }
                }
            }
        }
    }
}

@Composable
fun IssueRecoveryRoute(
    repository: JianyuRepository,
    issueId: String?,
    stageId: String?,
    onBack: () -> Unit,
    viewModel: IssueRecoveryViewModel = viewModel(
        factory = IssueRecoveryViewModel.factory(repository),
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
        contentScrollable = true,
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
