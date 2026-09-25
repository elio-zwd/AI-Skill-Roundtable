package com.elio.jianyu.ui.screens.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.elio.jianyu.ui.screens.dialog.components.AnswerPager
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class AnswerPagerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun switchingRolesKeepsEachAnswersExpandedState() {
        val roleA = SkillRoleUiModel("a", "角色甲", "")
        val roleB = SkillRoleUiModel("b", "角色乙", "")
        val longText = "这是一段长回复。".repeat(80)
        val replies = listOf(
            DialogMessageItem.SkillMessage("a1", roleA, longText, "10:00"),
            DialogMessageItem.SkillMessage("b1", roleB, longText, "10:01"),
        )
        var selected by mutableStateOf("a1")
        var expanded by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            SkillRoundtableTheme {
                AnswerPager(
                    group = DialogTimelineItem.Answers("q1", replies),
                    selectedAnswerId = selected,
                    expandedAnswerIds = expanded,
                    readAnswerIds = emptySet(),
                    onEvent = { event ->
                        when (event) {
                            is DialogEvent.SelectAnswer -> selected = event.answerId
                            is DialogEvent.ToggleAnswerExpanded -> expanded = expanded + event.answerId
                            else -> Unit
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("answer_expand_a1").performClick()
        composeRule.runOnIdle { assertTrue("a1" in expanded) }
        composeRule.onNodeWithText("›").performClick()
        composeRule.onNodeWithTag("answer_expand_b1").assertExists()
        composeRule.onNodeWithText("‹").performClick()
        composeRule.runOnIdle { assertTrue("a1" in expanded) }
    }
}
