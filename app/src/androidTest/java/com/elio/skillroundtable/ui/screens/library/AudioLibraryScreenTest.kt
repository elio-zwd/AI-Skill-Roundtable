package com.elio.skillroundtable.ui.screens.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.skillroundtable.audio.AudioSynthesisErrorCode
import com.elio.skillroundtable.audio.AudioSynthesisState
import com.elio.skillroundtable.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioLibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibrary_exposesStableRootAndEmptyState() {
        render(
            AudioLibraryUiState(
                audioMessages = emptyList(),
                currentPlayingId = null,
                synthesisTasks = emptyList(),
                allCharacters = emptyList(),
            ),
        )

        composeRule.onNodeWithTag(AudioLibraryTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(AudioLibraryTestTags.EMPTY_STATE).assertExists()
    }

    @Test
    fun synthesisFailure_exposesStableFailureTag() {
        render(
            AudioLibraryUiState(
                audioMessages = emptyList(),
                currentPlayingId = null,
                synthesisTasks = listOf(
                    AudioSynthesisTaskUiState(
                        messageId = 42L,
                        state = AudioSynthesisState.Failed(
                            code = AudioSynthesisErrorCode.NETWORK_ERROR,
                            displayMessage = "网络异常，请稍后重试",
                            retryable = true,
                        ),
                    ),
                ),
                allCharacters = emptyList(),
            ),
        )

        composeRule
            .onNodeWithTag(AudioLibraryTestTags.synthesis(42L, isFailure = true))
            .assertExists()
    }

    private fun render(uiState: AudioLibraryUiState) {
        composeRule.setContent {
            SkillRoundtableTheme {
                AudioLibraryScreen(
                    uiState = uiState,
                    onDismissSynthesisFailure = {},
                    onPlay = {},
                    onDelete = {},
                    onTranscode = {},
                )
            }
        }
    }
}
