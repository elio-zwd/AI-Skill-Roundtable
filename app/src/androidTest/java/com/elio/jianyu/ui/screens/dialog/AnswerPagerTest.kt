package com.elio.jianyu.ui.screens.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.screens.dialog.components.AnswerPager
import com.elio.jianyu.ui.screens.dialog.components.SkillMessageCard
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

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
        var selected by mutableStateOf("a")
        var expanded by mutableStateOf(emptySet<String>())
        composeRule.setContent {
            SkillRoundtableTheme {
                AnswerPager(
                    group = DialogTimelineItem.Answers("q1", replies),
                    selectedRoleId = selected,
                    expandedAnswerIds = expanded,
                    readAnswerIds = emptySet(),
                    onEvent = { event ->
                        when (event) {
                            is DialogEvent.SelectAnswer -> selected = event.roleId
                            is DialogEvent.ToggleAnswerExpanded -> expanded = expanded + event.answerId
                            else -> Unit
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithTag("answer_expand_a1").performClick()
        composeRule.runOnIdle { assertTrue("a1" in expanded) }
        composeRule.onNodeWithTag("answer_next_q1").performClick()
        composeRule.onNodeWithTag("answer_expand_b1").assertExists()
        composeRule.onNodeWithTag("answer_previous_q1").performClick()
        composeRule.runOnIdle { assertTrue("a1" in expanded) }
    }

    @Test
    fun previewFitsConversationViewportAndShortAnswerStaysWhole() {
        val role = SkillRoleUiModel("a", "角色甲", "")
        var longAnswer by mutableStateOf(true)
        var expanded by mutableStateOf(false)
        composeRule.setContent {
            SkillRoundtableTheme {
                SkillMessageCard(
                    message = DialogMessageItem.SkillMessage(
                        "a1", role,
                        if (longAnswer) "一段较长的回答。".repeat(120) else "简短回答。",
                        "10:00",
                    ),
                    onEvent = {},
                    expanded = expanded,
                    onToggleExpanded = { expanded = !expanded },
                    previewViewportHeight = 240.dp,
                )
            }
        }

        composeRule.onNodeWithTag("answer_expand_a1").assertExists()
        val bounds = composeRule.onNodeWithTag("answer_body_a1").getUnclippedBoundsInRoot()
        val previewHeight = bounds.bottom - bounds.top
        assertTrue("预览高度 $previewHeight 应接近消息视口的 45%", previewHeight <= 109.dp)

        composeRule.onNodeWithTag("answer_expand_a1").performClick()
        composeRule.runOnIdle { assertTrue(expanded) }
        composeRule.runOnIdle { longAnswer = false; expanded = false }
        composeRule.onNodeWithTag("answer_expand_a1").assertDoesNotExist()
    }

    @Test
    fun failedRoleCanRetryWithoutChangingOtherAnswer() {
        val roleA = SkillRoleUiModel("a", "角色甲", "")
        val roleB = SkillRoleUiModel("b", "角色乙", "")
        val replies = listOf(
            DialogMessageItem.SkillMessage(
                "a1", roleA, "回答失败", "10:00",
                questionId = "q1", answerStatus = DialogAnswerStatus.FAILED,
            ),
            DialogMessageItem.SkillMessage("b1", roleB, "已完成", "10:01"),
        )
        var selected by mutableStateOf("a")
        var retriedRole: String? = null
        composeRule.setContent {
            SkillRoundtableTheme {
                AnswerPager(
                    group = DialogTimelineItem.Answers("q1", replies),
                    selectedRoleId = selected,
                    expandedAnswerIds = emptySet(),
                    readAnswerIds = emptySet(),
                    onEvent = { event ->
                        when (event) {
                            is DialogEvent.RetryAnswer -> retriedRole = event.roleId
                            is DialogEvent.SelectAnswer -> selected = event.roleId
                            else -> Unit
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("answer_retry_a1").performClick()
        composeRule.runOnIdle { assertEquals("a", retriedRole) }
        composeRule.onNodeWithTag("answer_next_q1").performClick()
        composeRule.onNodeWithTag("answer_retry_a1").assertDoesNotExist()
    }

    @Test
    fun longAnswerKeepsPagingButtonsAvailableWhileReadingMiddle() {
        val roleA = SkillRoleUiModel("a", "角色甲", "")
        val roleB = SkillRoleUiModel("b", "角色乙", "")
        val group = DialogTimelineItem.Answers(
            "q1",
            listOf(
                DialogMessageItem.SkillMessage("a1", roleA, "很长的正文。".repeat(500), "10:00"),
                DialogMessageItem.SkillMessage("b1", roleB, "短回复", "10:01"),
            ),
        )
        var selectedRoles by mutableStateOf(emptyMap<String, String>())
        composeRule.setContent {
            SkillRoundtableTheme {
                DialogScreen(
                    uiState = DialogUiState(
                        session = DialogSessionInfo(id = "1", title = "测试会话", roleCount = 2),
                        visibleMessages = listOf(group),
                        roleStripExpanded = false,
                        expandedAnswerIds = setOf("a1"),
                        selectedRoleIds = selectedRoles,
                        restoredSessionId = "1",
                    ),
                    onEvent = { event ->
                        if (event is DialogEvent.SelectAnswer) {
                            selectedRoles = selectedRoles + (event.questionId to event.roleId)
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithTag("dialog_message_list").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("answer_next_sticky_q1").performClick()
        composeRule.runOnIdle { assertEquals("b", selectedRoles["q1"]) }
    }
}
