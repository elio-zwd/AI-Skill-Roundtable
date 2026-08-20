package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionThinkingLevel
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.data.IssueThinkingPolicy
import com.elio.jianyu.execution.SearchMode
import com.elio.jianyu.ui.automation.JianyuAutomationTags
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
        composeRule.onNodeWithText("已发起 1 次 API 调用")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.CONTENT_LIST).performScrollToIndex(4)
        composeRule.onNodeWithTag(IssueExecutionTestTags.participant("participant-1"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.CONTENT_LIST).performScrollToIndex(0)
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
        composeRule.onNodeWithTag(IssueExecutionTestTags.CONTENT_LIST).performScrollToIndex(4)
        composeRule.onNodeWithText("内容未完整生成，已保留用于恢复和审计。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.CONTENT_LIST).performScrollToIndex(0)
        composeRule.onNodeWithTag(IssueExecutionTestTags.RETRY).performClick()

        assertEquals(1, retryClicks)
    }

    @Test
    fun nextRunSearchModeProvidesThreeChoicesAndEmitsSelection() {
        var selected: SearchMode? = null
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
                    onRetry = {},
                    onRecoverInterrupted = {},
                    onSearchModeChanged = { selected = it },
                )
            }
        }

        composeRule.onNodeWithTag(IssueExecutionTestTags.SEARCH_MODE).assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.searchMode(SearchMode.OFF))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.searchMode(SearchMode.AUTO))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.searchMode(SearchMode.ON))
            .performClick()

        assertEquals(SearchMode.ON, selected)
    }

    @Test
    fun runningStateLocksNextRunConfiguration() {
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueExecutionScreen(
                    state = contentState(
                        phase = IssueExecutionPhase.RUNNING,
                        runStatus = ExecutionRunStatus.RUNNING,
                        canStop = true,
                        canRetry = false,
                        canRecover = false,
                    ),
                    onBack = {},
                    onReload = {},
                    onStop = {},
                    onRetry = {},
                    onRecoverInterrupted = {},
                )
            }
        }

        composeRule.onNodeWithText("当前 Interaction 正在运行，以下本次选择已锁定。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(IssueExecutionTestTags.searchMode(SearchMode.ON))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(IssueExecutionTestTags.thinkingOverride(IssueThinkingPolicy.HIGH))
            .assertIsNotEnabled()
    }

    @Test
    fun runHistoryShowsFrozenSnapshotAndDetailWithoutChangingCurrentRun() {
        val run = IssueExecutionRunHistoryUi(
            runId = "run-history",
            runKind = ExecutionRunKind.DIRECTED_RESPONSE,
            status = ExecutionRunStatus.RETRYABLE,
            historyScope = ExecutionHistoryScope.EXPLICIT_MESSAGES,
            retryOfRunId = "run-original",
            parentRunId = null,
            failureMessage = "网络不可用，请重试。",
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.HIGH,
            thinkingLevelSource = ExecutionThinkingSource.ROUND_USER_OVERRIDE,
            isCurrent = false,
        )
        val persistedDetail = IssueExecutionRunDetailUiState.Content(
            run = run,
            participants = listOf(
                IssueExecutionParticipantUi(
                    snapshotId = "history-participant",
                    displayName = "事实核验",
                    position = 0,
                    status = ExecutionParticipantStatus.RETRYABLE,
                    attemptCount = 2,
                    text = "已保留的历史输出",
                    isPending = false,
                    hasIncompleteOutput = true,
                    errorCode = "offline",
                    errorMessage = "网络不可用，请重试。",
                ),
            ),
            budget = IssueExecutionBudgetUi(
                usedApiCalls = 3,
                closed = true,
            ),
        )
        var opened: String? = null
        composeRule.setContent {
            SkillRoundtableTheme {
                var visibleDetail by remember {
                    mutableStateOf<IssueExecutionRunDetailUiState?>(null)
                }
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExecutionRunHistorySection(
                        runs = listOf(run),
                        detail = visibleDetail,
                        onOpenDetail = { runId ->
                            opened = runId
                            visibleDetail = persistedDetail
                        },
                        onDismissDetail = { visibleDetail = null },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(JianyuAutomationTags.Execution.RUN_HISTORY)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Execution.runHistoryItem("run-history"),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("模型：gemini-3.6-flash").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("查看详情").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals("run-history", opened) }
        composeRule.onNodeWithTag(JianyuAutomationTags.Execution.RUN_HISTORY_DETAIL)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Execution.runHistoryParticipant(
                "run-history",
                "history-participant",
            ),
        ).performScrollTo().assertIsDisplayed()
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
            usedApiCalls = 1,
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
