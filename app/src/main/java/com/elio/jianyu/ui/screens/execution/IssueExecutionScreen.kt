package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.screens.context.ContextConfirmationDialog

@Composable
fun IssueExecutionScreen(
    state: IssueExecutionUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onRecoverInterrupted: () -> Unit,
    onOpenContext: () -> Unit = {},
    onDismissContext: () -> Unit = {},
    onToggleContext: (ContextSourceType, String) -> Unit = { _, _ -> },
    onContextNetworkAllowed: (ContextSourceType, String, Boolean) -> Unit = { _, _, _ -> },
    onSensitiveContextConfirmed: (ContextSourceType, String, Boolean) -> Unit = { _, _, _ -> },
    onContextExcerptChanged: (ContextSourceType, String, String) -> Unit = { _, _, _ -> },
    onConfirmContext: () -> Unit = {},
) {
    JianyuPageShell(
        title = when (state) {
            is IssueExecutionUiState.Content -> state.issueTitle
            else -> "议题工作区"
        },
        subtitle = "执行运行、上下文确认与恢复",
        onBack = onBack,
        contentScrollable = true,
        modifier = Modifier.testTag(IssueExecutionTestTags.SCREEN),
    ) {
        when (state) {
            IssueExecutionUiState.Loading -> Column(
                modifier = Modifier.testTag(IssueExecutionTestTags.LOADING),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text("正在从数据库恢复议题、阶段与执行状态")
                Text(
                    text = "恢复页面只读取持久化事实，不会自动调用模型。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is IssueExecutionUiState.Failure -> JianyuStateCard(
                title = state.title,
                message = state.message,
                actionLabel = "重新读取",
                onAction = onReload,
                modifier = Modifier.testTag(IssueExecutionTestTags.FAILURE),
            )

            is IssueExecutionUiState.Content -> {
                ExecutionStatusCard(state)
                ContextSelectionSummaryCard(
                    state = state,
                    onOpenContext = onOpenContext,
                )
                if (state.runId == null) {
                    JianyuStateCard(
                        title = "尚未开始执行",
                        message = "打开工作区不会创建 Run。可以先确认资料与个人背景，随后由 Skill 选择流程消费已确认上下文。",
                    )
                }
                if (state.contextConfirmation?.confirmedForStart == true) {
                    JianyuStateCard(
                        title = "上下文已确认",
                        message = "当前只生成不可变 Contribution 与 Usage 写入载荷；尚未选择 Skill 时不会创建 Run、Pending、预算或网络请求。",
                    )
                }
                if (state.canRecoverInterrupted) {
                    JianyuStateCard(
                        title = "运行可能被中断",
                        message = "仅在确认原网络调用已经停止后执行恢复。恢复只收敛数据库状态，不会自动重发请求。",
                    )
                }
                state.participants.forEach { participant ->
                    ExecutionParticipantCard(participant)
                }
                if (state.participants.isEmpty() && state.runId != null) {
                    JianyuStateCard(
                        title = "没有参与者运行快照",
                        message = "当前 Run 无法安全执行，请返回后重新选择 Skill。",
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.canStop) {
                        OutlinedButton(
                            onClick = onStop,
                            enabled = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(IssueExecutionTestTags.STOP),
                        ) {
                            Text("停止")
                        }
                    }
                    if (state.canRecoverInterrupted) {
                        OutlinedButton(
                            onClick = onRecoverInterrupted,
                            enabled = !state.operationInProgress,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(IssueExecutionTestTags.RECOVER),
                        ) {
                            Text("收敛中断状态")
                        }
                    }
                    if (state.canRetry) {
                        Button(
                            onClick = onRetry,
                            enabled = !state.operationInProgress,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(IssueExecutionTestTags.RETRY),
                        ) {
                            Text("确认上下文并重试")
                        }
                    }
                }
                if (state.operationInProgress) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("正在持久化操作结果")
                    }
                }
            }
        }
    }

    val confirmation = (state as? IssueExecutionUiState.Content)?.contextConfirmation
    if (confirmation != null) {
        ContextConfirmationDialog(
            state = confirmation,
            onDismiss = onDismissContext,
            onToggleSelected = onToggleContext,
            onNetworkAllowed = onContextNetworkAllowed,
            onSensitiveConfirmed = onSensitiveContextConfirmed,
            onExcerptChanged = onContextExcerptChanged,
            onConfirm = onConfirmContext,
        )
    }
}
