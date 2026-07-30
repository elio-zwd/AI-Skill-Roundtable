package com.elio.skillroundtable.ui.screens.library

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.elio.skillroundtable.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test

class AudioLibraryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyLibrary_exposesStableRootAndEmptyState() {
        composeRule.setContent {
            SkillRoundtableTheme {
                AudioLibraryScreen(
                    uiState = AudioLibraryUiState(
                        audioMessages = emptyList(),
                        currentPlayingId = null,
                        synthesisTasks = emptyList(),
                        allCharacters = emptyList(),
                    ),
                    onDismissSynthesisFailure = {},
                    onPlay = {},
                    onDelete = {},
                    onTranscode = {},
                )
            }
        }

        composeRule.onNodeWithTag(AudioLibraryTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(AudioLibraryTestTags.EMPTY_STATE).assertExists()
    }
}
