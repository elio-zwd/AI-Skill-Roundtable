package com.elio.jianyu.ui.screens.context

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContextConfirmationDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun networkPermissionBlocksConfirmationWithoutSilentlyDroppingSource() {
        val candidate = ContextCandidateUi(
            sourceType = ContextSourceType.MATERIAL,
            sourceId = "material-1",
            title = "资料",
            sourceKind = "note",
            sourceLocator = null,
            sourcePublishedAt = null,
            sourceCapturedAt = 1L,
            originalContent = "正文",
            selectedContent = "正文",
            sourceHash = "hash",
            sourceUpdatedAt = 1L,
            sensitive = false,
            selected = true,
            networkAllowed = false,
        )
        composeRule.setContent {
            SkillRoundtableTheme {
                ContextConfirmationDialog(
                    state = ContextConfirmationUiState(
                        visible = true,
                        runId = "run-1",
                        issueId = "issue-1",
                        stageId = "stage-1",
                        currentUserInput = "问题",
                        baseContextCharacters = 10,
                        candidates = listOf(candidate),
                    ),
                    onDismiss = {},
                    onToggleSelected = { _, _ -> },
                    onNetworkAllowed = { _, _, _ -> },
                    onSensitiveConfirmed = { _, _, _ -> },
                    onExcerptChanged = { _, _, _ -> },
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText("已选来源中存在未允许本次发送到模型服务的正文。")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ContextConfirmationTestTags.CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(
            ContextConfirmationTestTags.candidate(ContextSourceType.MATERIAL, "material-1"),
        ).assertIsDisplayed()
    }
}
