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
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

@Composable
fun IssueExecutionScreen(
    state: IssueExecutionUiState,
    onBack: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onRecoverInterrupted: () -> Unit,
) {
    JianyuPageShell(
        title = when (state) {
            is IssueExecutionUiState.Content -> state.issueTitle
            else -> "议题工作区"
        },
        subtitle = "执行运行与恢复",
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
                if (state.runId == null) {
                    JianyuStateCard(
                        title = "尚未开始执行",
                        message = "打开工作区不会创建 Run。后续由已确认的 Skill 选择入口传入稳定启动命令。",
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
                            enabled = !state.operationInProgress,
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
                            Text("重试失败成员")
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
}
