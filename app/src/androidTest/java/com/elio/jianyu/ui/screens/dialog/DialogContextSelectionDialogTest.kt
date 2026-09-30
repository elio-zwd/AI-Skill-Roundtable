package com.elio.jianyu.ui.screens.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test

class DialogContextSelectionDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentStartsCollapsedAndServicePermissionCheckboxIsRemoved() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    DialogContextState(
                        candidates = listOf(
                            DialogContextCandidate(
                                sourceType = ContextSourceType.MATERIAL,
                                sourceId = "material-1",
                                title = "产品资料",
                                content = "需要展开后才显示的完整资料正文",
                                expectedSourceHash = "hash",
                                expectedSourceUpdatedAt = 1L,
                                sensitive = false,
                            ),
                        ),
                    ),
                )
            }
            SkillRoundtableTheme {
                DialogContextSelectionDialog(
                    state = state,
                    showSensitiveReminder = true,
                    onDismiss = {},
                    onChange = { candidate ->
                        state = updateDialogContextCandidate(state, candidate)
                    },
                    onConfirm = {},
                )
            }
        }

        composeRule.onNodeWithText("允许本次发送给模型服务").assertDoesNotExist()
        composeRule.onNodeWithText("本次发送的正文或摘录").assertDoesNotExist()
        composeRule.onNodeWithText("点击展开内容").performClick()
        composeRule.onNodeWithText("本次发送的正文或摘录").assertIsDisplayed()
    }
}
