package com.elio.skillroundtable.ui.screens.characters

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.skillroundtable.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharacterHallScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_keepsRootAddActionAndEmptyMarker() {
        val events = mutableListOf<CharacterHallEvent>()
        composeRule.setContent {
            SkillRoundtableTheme {
                CharacterHallScreen(
                    uiState = CharacterHallUiState(
                        isLoading = false,
                        isEmpty = true,
                    ),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithTag(CharacterHallTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(CharacterHallTestTags.GROUP_ROW).assertExists()
        composeRule.onNodeWithTag(CharacterHallTestTags.EMPTY_STATE).assertExists()
        composeRule.onNodeWithTag(CharacterHallTestTags.ADD_BUTTON).assertExists().performClick()
        composeRule.runOnIdle {
            assertTrue(events.contains(CharacterHallEvent.AddCharacter))
        }
    }

    @Test
    fun loadingState_isExplicit() {
        composeRule.setContent {
            SkillRoundtableTheme {
                CharacterHallScreen(
                    uiState = CharacterHallUiState(isLoading = true),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithTag(CharacterHallTestTags.LOADING_STATE).assertExists()
    }
}
