package com.elio.skillroundtable.ui.screens.roundtable

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.skillroundtable.data.ChatSession
import com.elio.skillroundtable.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoundtableScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_exposesNewSessionAction() {
        val events = mutableListOf<RoundtableEvent>()
        render(RoundtableUiState(), events::add)

        composeRule.onNodeWithTag(RoundtableTestTags.NEW_SESSION_BUTTON).assertExists().performClick()
        composeRule.runOnIdle {
            assertTrue(events.contains(RoundtableEvent.CreateFirstSession))
        }
    }

    @Test
    fun runningSession_exposesInputAndStopAction() {
        render(
            RoundtableUiState(
                currentSessionId = 1L,
                currentSession = ChatSession(id = 1L, title = "测试会议"),
                isRoundtableRunning = true,
            ),
        )

        composeRule.onNodeWithTag(RoundtableTestTags.CHAT_INPUT).assertExists()
        composeRule.onNodeWithTag(RoundtableTestTags.STOP_BUTTON).assertExists()
    }

    @Test
    fun failedCharacters_exposeRetryAndDismissActions() {
        val events = mutableListOf<RoundtableEvent>()
        render(
            RoundtableUiState(
                currentSessionId = 1L,
                currentSession = ChatSession(id = 1L, title = "测试会议"),
                retryableSessionId = 1L,
                retryableCharacterIds = listOf("a", "b"),
            ),
            events::add,
        )

        composeRule
            .onNodeWithTag(RoundtableTestTags.RETRY_FAILED_CHARACTERS_BUTTON)
            .assertExists()
            .performClick()
        composeRule
            .onNodeWithTag(RoundtableTestTags.DISMISS_FAILED_CHARACTERS_BUTTON)
            .assertExists()
            .performClick()
        composeRule.onNodeWithTag(RoundtableTestTags.SEND_BUTTON).assertExists()

        composeRule.runOnIdle {
            assertTrue(events.contains(RoundtableEvent.RetryFailedCharacters))
            assertTrue(events.contains(RoundtableEvent.DismissRetryableState))
        }
    }

    private fun render(
        uiState: RoundtableUiState,
        onEvent: (RoundtableEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            SkillRoundtableTheme {
                RoundtableScreen(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
        }
    }
}
