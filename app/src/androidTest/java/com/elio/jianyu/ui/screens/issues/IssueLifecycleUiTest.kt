package com.elio.jianyu.ui.screens.issues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.lifecycle.IssuePurgeImpactSnapshot
import com.elio.jianyu.lifecycle.PurgeFileImpact
import com.elio.jianyu.ui.automation.JianyuLifecycleAutomationTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IssueLifecycleUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun archivedActionsRemainVisibleAt360DpAndTwoHundredPercentFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        IssuesScreen(
                            state = IssuesUiState.Content(
                                active = emptyList(),
                                archived = listOf(issue(IssueLifecycleState.ARCHIVED)),
                                trashed = emptyList(),
                            ),
                            onRetry = {},
                            onOpenIssue = { _, _ -> },
                            onOpenSettings = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(JianyuLifecycleAutomationTags.Resume.BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(JianyuLifecycleAutomationTags.RelatedIssue.BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(
            JianyuLifecycleAutomationTags.IssueLifecycle.MOVE_TO_TRASH,
        ).assertIsDisplayed()
    }

    @Test
    fun trashActionsRemainVisibleAt360DpAndTwoHundredPercentFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 2f)) {
                MaterialTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        IssuesScreen(
                            state = IssuesUiState.Content(
                                active = emptyList(),
                                archived = emptyList(),
                                trashed = listOf(issue(IssueLifecycleState.TRASHED)),
                            ),
                            onRetry = {},
                            onOpenIssue = { _, _ -> },
                            onOpenSettings = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(
            JianyuLifecycleAutomationTags.IssueLifecycle.RESTORE_FROM_TRASH,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(JianyuLifecycleAutomationTags.Purge.BUTTON).assertIsDisplayed()
    }

    @Test
    fun purgeRequiresFirstAndFinalConfirmationWithoutPreselectedConsent() {
        val impact = IssuePurgeImpactSnapshot(
            issueId = "issue-1",
            databaseCounts = mapOf("messages" to 2L),
            formalFiles = listOf(PurgeFileImpact("committed/audio-1.mp3", 12L)),
            pendingWorkNames = emptyList(),
            missingAssetIds = emptyList(),
            orphanRelativePaths = listOf("temporary/orphan.part"),
            relatedIssueCount = 1,
            externalObjectCount = 0,
        )
        var firstConfirmations = 0
        var finalConfirmations = 0

        composeRule.setContent {
            MaterialTheme {
                IssueLifecycleDialogs(
                    state = IssueLifecycleUiState.PurgeImpactReady(impact),
                    onDismiss = {},
                    onWait = {},
                    onRefreshWaiting = {},
                    onStop = {},
                    onSummaryChange = {},
                    onArchiveConfirm = {},
                    onTrashConfirm = {},
                    onResumeChange = {},
                    onResumeNoChange = {},
                    onResumeConfirm = {},
                    onRelatedTitleChange = {},
                    onRelatedObjectiveChange = {},
                    onRelatedConfirm = {},
                    onPurgeFirstConfirm = { firstConfirmations += 1 },
                    onPurgeFinalConfirm = { finalConfirmations += 1 },
                    onPurgeRetry = {},
                    onPurgeCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag(JianyuLifecycleAutomationTags.Purge.IMPACT).assertIsDisplayed()
        composeRule.onNodeWithTag(JianyuLifecycleAutomationTags.Purge.FIRST_CONFIRM).performClick()
        assertEquals(1, firstConfirmations)
        assertEquals(0, finalConfirmations)

        composeRule.setContent {
            MaterialTheme {
                IssueLifecycleDialogs(
                    state = IssueLifecycleUiState.PurgeConfirming(
                        impact = impact,
                        firstConfirmationCompleted = true,
                    ),
                    onDismiss = {},
                    onWait = {},
                    onRefreshWaiting = {},
                    onStop = {},
                    onSummaryChange = {},
                    onArchiveConfirm = {},
                    onTrashConfirm = {},
                    onResumeChange = {},
                    onResumeNoChange = {},
                    onResumeConfirm = {},
                    onRelatedTitleChange = {},
                    onRelatedObjectiveChange = {},
                    onRelatedConfirm = {},
                    onPurgeFirstConfirm = {},
                    onPurgeFinalConfirm = { finalConfirmations += 1 },
                    onPurgeRetry = {},
                    onPurgeCancel = {},
                )
            }
        }
        composeRule.onNodeWithTag(JianyuLifecycleAutomationTags.Purge.FINAL_CONFIRM).assertIsDisplayed()
        composeRule.onNodeWithTag(JianyuLifecycleAutomationTags.Purge.FINAL_CONFIRM).performClick()
        assertEquals(1, finalConfirmations)
    }

    private fun issue(state: IssueLifecycleState): IssueNavigationUiItem = IssueNavigationUiItem(
        issueId = "issue-${state.storageValue}",
        title = "大字号生命周期测试议题",
        lifecycleState = state,
        currentStageId = "stage-1",
        currentStageTitle = "当前阶段",
        activeOrRecoverableRunCount = 0,
        updatedAt = 100L,
    )
}
