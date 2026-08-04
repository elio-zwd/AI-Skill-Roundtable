package com.elio.jianyu.ui.screens.execution

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssueExecutionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingExplainsThatRecoveryDoesNotCallModel() {
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueExecutionScreen(
                    state = IssueExecutionUiState.Loading,
                    onBack = {},
                    onReload = {},
                    onStop = {},
                    onRetry = {},
                    onRecoverInterrupted = {},
                )
            }
        }

        composeRule.onNodeWithTag(IssueExecutionTestTags.LOADING).assertIsDisplayed()
        composeRule.onNodeWithText("恢复页面只读取持久化事实，不会自动调用模型。")
            .assertIsDisplayed()
    }

    @Test
    fun runningStateShowsParticipantBudgetAndStopActions() {
        var stopClicks = 0
        var recoverClicks = 0
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueExecutionScreen(
                    state = contentState(
                        phase = IssueExecutionPhase.RUNNING,
                        runStatus = ExecutionRunStatus.RUNNING,
                        canStop = true,
                        canRetry = false,
                        canRecover = true,
                    ),
                    onBack = {},
                    onReload = {},
                    onStop = { stopClicks++ },
                    onRetry = {},
                    onRecoverInterrupted = { recoverClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag(IssueExecutionTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.participant("participant-1"))
            .assertIsDisplayed()
        composeRule.onNodeWithText("已用 1 / 30，剩余 29").assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.STOP).performClick()
        composeRule.onNodeWithTag(IssueExecutionTestTags.RECOVER).performClick()

        assertEquals(1, stopClicks)
        assertEquals(1, recoverClicks)
    }

    @Test
    fun retryableStateShowsIncompleteOutputAndRetriesFailedMembers() {
        var retryClicks = 0
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueExecutionScreen(
                    state = contentState(
                        phase = IssueExecutionPhase.RETRYABLE,
                        runStatus = ExecutionRunStatus.RETRYABLE,
                        canStop = false,
                        canRetry = true,
                        canRecover = false,
                    ),
                    onBack = {},
                    onReload = {},
                    onStop = {},
                    onRetry = { retryClicks++ },
                    onRecoverInterrupted = {},
                )
            }
        }

        composeRule.onNodeWithText("存在可重试成员").assertIsDisplayed()
        composeRule.onNodeWithText("以上内容未完整生成，已保留用于恢复和审计。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.RETRY).performClick()

        assertEquals(1, retryClicks)
    }

    @Test
    fun catalogFailureLeavesWorkspaceReadOnly() {
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueExecutionScreen(
                    state = contentState(
                        phase = IssueExecutionPhase.READY,
                        runStatus = ExecutionRunStatus.NOT_STARTED,
                        canStop = false,
                        canRetry = false,
                        canRecover = false,
                        executionAvailable = false,
                    ),
                    onBack = {},
                    onReload = {},
                    onStop = {},
                    onRetry = {},
                    onRecoverInterrupted = {},
                )
            }
        }

        composeRule.onNodeWithText("官方 Skill 目录未能加载", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("不会调用模型", substring = true)
            .assertIsDisplayed()
    }

    private fun contentState(
        phase: IssueExecutionPhase,
        runStatus: ExecutionRunStatus,
        canStop: Boolean,
        canRetry: Boolean,
        canRecover: Boolean,
        executionAvailable: Boolean = true,
    ): IssueExecutionUiState.Content = IssueExecutionUiState.Content(
        issueId = "issue-1",
        issueTitle = "是否转向机器人行业",
        stageId = "stage-1",
        stageTitle = "评估路径",
        phase = phase,
        runId = "run-1",
        runStatus = runStatus,
        participants = listOf(
            IssueExecutionParticipantUi(
                snapshotId = "participant-1",
                displayName = "风险分析",
                position = 0,
                status = if (phase == IssueExecutionPhase.RUNNING) {
                    ExecutionParticipantStatus.STREAMING
                } else {
                    ExecutionParticipantStatus.RETRYABLE
                },
                attemptCount = 1,
                text = "已生成的部分分析",
                isPending = phase == IssueExecutionPhase.RUNNING,
                hasIncompleteOutput = phase == IssueExecutionPhase.RETRYABLE,
                errorCode = if (phase == IssueExecutionPhase.RETRYABLE) "offline" else null,
                errorMessage = if (phase == IssueExecutionPhase.RETRYABLE) {
                    "网络不可用，请检查连接后重试。"
                } else {
                    null
                },
            ),
        ),
        budget = IssueExecutionBudgetUi(
            maxApiCalls = 30,
            usedApiCalls = 1,
            reservedRequiredCalls = 1,
            closed = false,
        ),
        failureCode = null,
        failureMessage = null,
        executionAvailable = executionAvailable,
        canStop = canStop,
        canRetry = canRetry,
        canRecoverInterrupted = canRecover,
    )
}
