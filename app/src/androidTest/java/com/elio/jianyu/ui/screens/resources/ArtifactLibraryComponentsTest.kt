package com.elio.jianyu.ui.screens.resources

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactLibrarySnapshot
import com.elio.jianyu.result.ArtifactType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArtifactLibraryComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyAndFailureStatesExposeStableSemanticTags() {
        composeRule.setContent {
            MaterialTheme {
                ArtifactLibraryContent(
                    state = ArtifactLibraryUiState.Empty,
                    onRetry = {},
                    onQueryChange = {},
                    onTypesChange = {},
                    onIncludeHistoryChange = {},
                    onOpenArtifact = {},
                    onDismissArtifact = {},
                    onOpenIssue = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag(ArtifactLibraryTestTags.EMPTY).assertIsDisplayed()

        composeRule.setContent {
            MaterialTheme {
                ArtifactLibraryContent(
                    state = ArtifactLibraryUiState.Failure("artifact_load_failed"),
                    onRetry = {},
                    onQueryChange = {},
                    onTypesChange = {},
                    onIncludeHistoryChange = {},
                    onOpenArtifact = {},
                    onDismissArtifact = {},
                    onOpenIssue = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag(ArtifactLibraryTestTags.FAILURE).assertIsDisplayed()
    }

    @Test
    fun contentShowsLatestArtifactCardWithoutExpandingFullBody() {
        val item = artifact(content = "完整正文不应直接出现在列表")
        composeRule.setContent {
            MaterialTheme {
                ArtifactLibraryContent(
                    state = ArtifactLibraryUiState.Content(
                        ArtifactLibrarySnapshot(listOf(item), emptyList()),
                    ),
                    onRetry = {},
                    onQueryChange = {},
                    onTypesChange = {},
                    onIncludeHistoryChange = {},
                    onOpenArtifact = {},
                    onDismissArtifact = {},
                    onOpenIssue = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithTag(ArtifactLibraryTestTags.LIBRARY).assertIsDisplayed()
        composeRule.onNodeWithTag(ArtifactLibraryTestTags.item(item.artifactId)).assertIsDisplayed()
        composeRule.onNodeWithTag(ArtifactLibraryTestTags.DETAIL).assertDoesNotExist()
    }

    @Test
    fun detailShowsFullContentAndReturnsStableIssueStageIds() {
        var openedIssue: Pair<String, String>? = null
        val item = artifact(content = "完整正文只在用户打开详情后展示")
        composeRule.setContent {
            MaterialTheme {
                ArtifactLibraryContent(
                    state = ArtifactLibraryUiState.Content(
                        snapshot = ArtifactLibrarySnapshot(listOf(item), emptyList()),
                        selectedArtifactId = item.artifactId,
                    ),
                    onRetry = {},
                    onQueryChange = {},
                    onTypesChange = {},
                    onIncludeHistoryChange = {},
                    onOpenArtifact = {},
                    onDismissArtifact = {},
                    onOpenIssue = { issueId, stageId -> openedIssue = issueId to stageId },
                )
            }
        }

        composeRule.onNodeWithTag(ArtifactLibraryTestTags.DETAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(ArtifactLibraryTestTags.OPEN_ISSUE).performClick()
        composeRule.runOnIdle {
            assertEquals("issue-1" to "stage-1", openedIssue)
        }
    }

    private fun artifact(content: String) = ArtifactLibraryItem(
        artifactId = "artifact-1",
        issueId = "issue-1",
        issueTitle = "议题一",
        stageId = "stage-1",
        stageTitle = "阶段一",
        title = "阶段总结",
        contentSummary = "摘要",
        content = content,
        artifactType = ArtifactType.GENERAL_SUMMARY,
        rawArtifactType = ArtifactType.GENERAL_SUMMARY.storageValue,
        confirmedAt = 1,
        revisionOfArtifactId = null,
        revisionNumber = 1,
        latest = true,
    )
}
