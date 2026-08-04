package com.elio.jianyu.ui.screens.execution

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
class IssueExecutionStopAvailabilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun stopRemainsEnabledWhileExecutionOperationIsInProgress() {
        var stopClicks = 0
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueExecutionScreen(
                    state = IssueExecutionUiState.Content(
                        issueId = "issue-1",
                        issueTitle = "Issue",
                        stageId = "stage-1",
                        stageTitle = "Stage",
                        phase = IssueExecutionPhase.RUNNING,
                        runId = "run-1",
                        runStatus = ExecutionRunStatus.RUNNING,
                        participants = listOf(
                            IssueExecutionParticipantUi(
                                snapshotId = "participant-1",
                                displayName = "Skill A",
                                position = 0,
                                status = ExecutionParticipantStatus.STREAMING,
                                attemptCount = 1,
                                text = "partial",
                                isPending = true,
                                hasIncompleteOutput = false,
                                errorCode = null,
                                errorMessage = null,
                            ),
                        ),
                        budget = IssueExecutionBudgetUi(30, 1, 1, false),
                        failureCode = null,
                        failureMessage = null,
                        executionAvailable = true,
                        canStop = true,
                        canRetry = false,
                        canRecoverInterrupted = false,
                        operationInProgress = true,
                    ),
                    onBack = {},
                    onReload = {},
                    onStop = { stopClicks++ },
                    onRetry = {},
                    onRecoverInterrupted = {},
                )
            }
        }

        composeRule.onNodeWithTag(IssueExecutionTestTags.STOP)
            .assertIsEnabled()
            .performClick()

        assertEquals(1, stopClicks)
    }
}
