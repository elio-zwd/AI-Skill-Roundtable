package com.elio.jianyu.ui.screens.result

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.elio.jianyu.result.ArtifactRevisionResolver
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.result.StageResultWorkspace
import org.junit.Rule
import org.junit.Test

class StageResultComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyWorkspaceShowsCreateEntryWithoutCreatingArtifact() {
        composeRule.setContent {
            MaterialTheme {
                StageDraftResultPanel(
                    state = contentState(),
                    callbacks = StageResultCallbacks.Empty,
                )
            }
        }

        composeRule.onNodeWithTag(StageResultTestTags.PANEL).assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_CREATE).assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.ARTIFACT_CONFIRM).assertDoesNotExist()
    }

    @Test
    fun savedDraftShowsEditorPersistentSavedStateAndConfirmationEntry() {
        composeRule.setContent {
            MaterialTheme {
                StageDraftResultPanel(
                    state = contentState(
                        draftId = "draft-1",
                        editorContent = "已保存正文",
                        persistedContent = "已保存正文",
                        currentRevision = 2,
                        saveStatus = StageDraftSaveStatus.Saved(revision = 2, savedAt = 100),
                    ),
                    callbacks = StageResultCallbacks.Empty,
                )
            }
        }

        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_EDITOR).assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_SAVED).assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.ARTIFACT_CONFIRM).assertIsDisplayed()
    }

    @Test
    fun saveFailureAndConflictRemainVisibleInStableState() {
        composeRule.setContent {
            MaterialTheme {
                StageDraftResultPanel(
                    state = contentState(
                        draftId = "draft-1",
                        editorContent = "未保存正文",
                        persistedContent = "旧正文",
                        currentRevision = 1,
                        saveStatus = StageDraftSaveStatus.Failure("draft_save_failed"),
                    ),
                    callbacks = StageResultCallbacks.Empty,
                )
            }
        }
        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_SAVE_FAILURE).assertIsDisplayed()

        composeRule.setContent {
            MaterialTheme {
                StageDraftResultPanel(
                    state = contentState(
                        draftId = "draft-1",
                        editorContent = "冲突正文",
                        persistedContent = "旧正文",
                        currentRevision = 1,
                        saveStatus = StageDraftSaveStatus.Conflict,
                    ),
                    callbacks = StageResultCallbacks.Empty,
                )
            }
        }
        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_CONFLICT).assertIsDisplayed()
    }

    @Test
    fun abandonAndArtifactConfirmationDialogsUseSeparateExplicitConfirmation() {
        composeRule.setContent {
            MaterialTheme {
                StageDraftResultPanel(
                    state = contentState(
                        draftId = "draft-1",
                        editorContent = "已保存正文",
                        persistedContent = "已保存正文",
                        currentRevision = 1,
                        saveStatus = StageDraftSaveStatus.Saved(1, 100),
                        showAbandonConfirmation = true,
                        showArtifactConfirmation = true,
                        artifactTitle = "阶段总结",
                        artifactType = ArtifactType.GENERAL_SUMMARY,
                    ),
                    callbacks = StageResultCallbacks.Empty,
                )
            }
        }

        composeRule.onNodeWithTag(StageResultTestTags.DRAFT_ABANDON_CONFIRMATION)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(StageResultTestTags.ARTIFACT_CONFIRMATION_DIALOG)
            .assertIsDisplayed()
    }

    private fun contentState(
        draftId: String? = null,
        editorContent: String = "",
        persistedContent: String = "",
        currentRevision: Int = 0,
        saveStatus: StageDraftSaveStatus = StageDraftSaveStatus.Idle,
        showAbandonConfirmation: Boolean = false,
        showArtifactConfirmation: Boolean = false,
        artifactTitle: String = "",
        artifactType: ArtifactType = ArtifactType.GENERAL_SUMMARY,
    ) = StageResultUiState.Content(
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
        draftId = draftId,
        editorContent = editorContent,
        persistedContent = persistedContent,
        currentRevision = currentRevision,
        lastSavedAt = (saveStatus as? StageDraftSaveStatus.Saved)?.savedAt,
        saveStatus = saveStatus,
        selectedMessageIds = emptySet(),
        artifactTitle = artifactTitle,
        artifactType = artifactType,
        revisionOfArtifactId = null,
        showAbandonConfirmation = showAbandonConfirmation,
        showArtifactConfirmation = showArtifactConfirmation,
        artifactStatus = StageArtifactConfirmationStatus.Idle,
    )
}
