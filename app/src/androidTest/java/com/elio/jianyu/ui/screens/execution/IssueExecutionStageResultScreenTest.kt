package com.elio.jianyu.ui.screens.execution

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.result.ArtifactRevisionResolver
import com.elio.jianyu.result.StageResultWorkspace
import com.elio.jianyu.ui.screens.result.StageResultCallbacks
import com.elio.jianyu.ui.screens.result.StageResultTestTags
import com.elio.jianyu.ui.screens.result.StageResultUiState
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssueExecutionStageResultScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedWorkspaceShowsStageResultPanelWithoutReplacingExecutionStatus() {
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueExecutionScreen(
                    state = IssueExecutionUiState.Content(
                        issueId = "issue-1",
                        issueTitle = "议题",
                        stageId = "stage-1",
                        stageTitle = "阶段",
                        phase = IssueExecutionPhase.SUCCEEDED,
                        runId = "run-1",
                        runStatus = ExecutionRunStatus.SUCCEEDED,
                        participants = emptyList(),
                        budget = null,
                        failureCode = null,
                        failureMessage = null,
                        executionAvailable = true,
                        canStop = false,
                        canRetry = false,
                        canRecoverInterrupted = false,
                    ),
                    stageResultState = StageResultUiState.Content(
                        workspace = StageResultWorkspace(
                            issueId = "issue-1",
                            stageId = "stage-1",
                            draft = null,
                            draftRevisions = emptyList(),
                            artifacts = emptyList(),
                            selectableMessages = emptyList(),
                            materialUsages = emptyList(),
                            artifactRevisionResolution = ArtifactRevisionResolver.resolve(emptyList()),
                        ),
                        draftId = null,
                        editorContent = "",
                        persistedContent = "",
                        currentRevision = 0,
                        lastSavedAt = null,
                        saveStatus = com.elio.jianyu.ui.screens.result.StageDraftSaveStatus.Idle,
                        selectedMessageIds = emptySet(),
                        artifactTitle = "",
                        artifactType = com.elio.jianyu.result.ArtifactType.GENERAL_SUMMARY,
                        revisionOfArtifactId = null,
                        showAbandonConfirmation = false,
                        showArtifactConfirmation = false,
                        artifactStatus = com.elio.jianyu.ui.screens.result.StageArtifactConfirmationStatus.Idle,
                    ),
                    stageResultCallbacks = StageResultCallbacks.Empty,
                    onBack = {},
                    onReload = {},
                    onStop = {},
                    onRetry = {},
                    onRecoverInterrupted = {},
                )
            }
        }

        composeRule.onNodeWithTag(IssueExecutionTestTags.STATUS).assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_CREATE).assertIsDisplayed()
    }
}
