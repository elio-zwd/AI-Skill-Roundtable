package com.elio.jianyu.ui.screens.resources

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactLibrarySnapshot
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.ui.navigation.ResourceTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArtifactLibraryComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateExposesStableSemanticTag() {
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
    }

    @Test
    fun failureStateExposesStableSemanticTag() {
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
    fun artifactStateRemainsVisibleWhenMaterialLibraryFails() {
        composeRule.setContent {
            MaterialTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.ARTIFACTS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Failure("material_load_failed"),
                    artifactState = ArtifactLibraryUiState.Empty,
                )
            }
        }

        composeRule.onNodeWithTag(ArtifactLibraryTestTags.EMPTY).assertIsDisplayed()
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
    fun filtersOpenInSheetAndCancelDoesNotApplyDraftChanges() {
        var typeApplyCount = 0
        var historyApplyCount = 0
        val item = artifact(content = "正文")
        composeRule.setContent {
            MaterialTheme {
                ArtifactLibraryContent(
                    state = ArtifactLibraryUiState.Content(
                        ArtifactLibrarySnapshot(listOf(item), emptyList()),
                    ),
                    onRetry = {},
                    onQueryChange = {},
                    onTypesChange = { typeApplyCount += 1 },
                    onIncludeHistoryChange = { historyApplyCount += 1 },
                    onOpenArtifact = {},
                    onDismissArtifact = {},
                    onOpenIssue = { _, _ -> },
                )
            }
        }

        composeRule.onNodeWithText("通用阶段总结").assertDoesNotExist()
        composeRule.onNodeWithText("筛选").performClick()
        composeRule.onNodeWithText("通用阶段总结").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("取消").performClick()
        composeRule.runOnIdle {
            assertEquals(0, typeApplyCount)
            assertEquals(0, historyApplyCount)
        }
    }

    @Test
    fun detailShowsFullContentAndReturnsStableIssueStageIds() {
        var openedIssue: Pair<String, String>? = null
        var copiedArtifactId: String? = null
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
                    onCopyArtifact = { copiedArtifactId = it.artifactId },
                )
            }
        }

        composeRule.onNodeWithTag(ArtifactLibraryTestTags.DETAIL).assertIsDisplayed()
        composeRule.onNodeWithText("成果详情").assertIsDisplayed()
        composeRule.onNodeWithText("已保存成果").assertIsDisplayed()
        composeRule.onNodeWithText("复制").performClick()
        composeRule.onNodeWithTag(ArtifactLibraryTestTags.OPEN_ISSUE).performClick()
        composeRule.runOnIdle {
            assertEquals("issue-1" to "stage-1", openedIssue)
            assertEquals("artifact-1", copiedArtifactId)
        }
    }

    private fun artifact(content: String) = ArtifactLibraryItem(
        artifactId = "artifact-1",
        issueId = "issue-1",
        issueTitle = "会话一",
        stageId = "stage-1",
        stageTitle = "节点一",
        title = "节点总结",
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
