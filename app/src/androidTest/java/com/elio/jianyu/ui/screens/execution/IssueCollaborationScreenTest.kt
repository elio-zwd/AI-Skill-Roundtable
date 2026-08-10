package com.elio.jianyu.ui.screens.execution

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssueCollaborationScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun composerShowsRosterAndRequiresQuestionBeforeOpeningModes() {
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueCollaborationSection(
                    state = content(),
                    contextConfirmed = false,
                    onInputChanged = {},
                    onOpenDirected = {},
                    onOpenCross = {},
                    onDismissDialog = {},
                    onToggleParticipant = {},
                    onToggleMessage = {},
                    onOpenContext = {},
                    onConfirmDirected = {},
                    onConfirmCross = {},
                    onRetryFailed = {},
                    onSynthesize = {},
                    onRetrySynthesis = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.INPUT).assertIsDisplayed()
        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.ROSTER).assertIsDisplayed()
        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.DIRECTED_RESPONSE_BUTTON)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.CROSS_DISCUSSION_BUTTON)
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.STANDARD_SEND_BUTTON)
            .assertIsNotEnabled()
    }

    @Test
    fun standardFollowUpSendsNonBlankQuestionToCurrentRoster() {
        var submissions = 0
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueCollaborationSection(
                    state = content(input = "请继续检查这个方案的盲区"),
                    contextConfirmed = false,
                    onInputChanged = {},
                    onSubmitStandard = { submissions++ },
                    onOpenDirected = {},
                    onOpenCross = {},
                    onDismissDialog = {},
                    onToggleParticipant = {},
                    onToggleMessage = {},
                    onOpenContext = {},
                    onConfirmDirected = {},
                    onConfirmCross = {},
                    onRetryFailed = {},
                    onSynthesize = {},
                    onRetrySynthesis = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.STANDARD_SEND_BUTTON)
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, submissions) }
    }

    @Test
    fun historicalStageCannotSendStandardFollowUp() {
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueCollaborationSection(
                    state = content(
                        input = "这条消息不能写入旧阶段",
                        isCurrentStage = false,
                    ),
                    contextConfirmed = false,
                    onInputChanged = {},
                    onSubmitStandard = {},
                    onOpenDirected = {},
                    onOpenCross = {},
                    onDismissDialog = {},
                    onToggleParticipant = {},
                    onToggleMessage = {},
                    onOpenContext = {},
                    onConfirmDirected = {},
                    onConfirmCross = {},
                    onRetryFailed = {},
                    onSynthesize = {},
                    onRetrySynthesis = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.STANDARD_SEND_BUTTON)
            .assertIsNotEnabled()
    }

    @Test
    fun directedDialogRequiresExactlyOneVisibleRosterMember() {
        var toggled = ""
        var confirmed = 0
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueCollaborationSection(
                    state = content(
                        input = "请只评估学习路线",
                        dialogMode = CollaborationDialogMode.DIRECTED,
                        selectedSkillIds = setOf("study-planner"),
                    ),
                    contextConfirmed = true,
                    onInputChanged = {},
                    onOpenDirected = {},
                    onOpenCross = {},
                    onDismissDialog = {},
                    onToggleParticipant = { toggled = it },
                    onToggleMessage = {},
                    onOpenContext = {},
                    onConfirmDirected = { confirmed++ },
                    onConfirmCross = {},
                    onRetryFailed = {},
                    onSynthesize = {},
                    onRetrySynthesis = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.DIRECTED_DIALOG)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Collaboration.directedParticipant("research-fact-checker"),
        ).performClick()
        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.DIRECTED_CONFIRM)
            .assertIsEnabled()
            .performClick()

        assertEquals("research-fact-checker", toggled)
        assertEquals(1, confirmed)
    }

    @Test
    fun crossDialogShowsTransparentIntegratorOneRoundAndExpectedCalls() {
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueCollaborationSection(
                    state = content(
                        input = "比较两种转型路线",
                        dialogMode = CollaborationDialogMode.CROSS,
                        selectedSkillIds = setOf("study-planner", "research-fact-checker"),
                    ),
                    contextConfirmed = false,
                    onInputChanged = {},
                    onOpenDirected = {},
                    onOpenCross = {},
                    onDismissDialog = {},
                    onToggleParticipant = {},
                    onToggleMessage = {},
                    onOpenContext = {},
                    onConfirmDirected = {},
                    onConfirmCross = {},
                    onRetryFailed = {},
                    onSynthesize = {},
                    onRetrySynthesis = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.CROSS_DIALOG)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.CROSS_INTEGRATOR)
            .assertIsDisplayed()
        composeRule.onNodeWithText("会议行动助手（meeting-to-action）").assertIsDisplayed()
        composeRule.onNodeWithText("第一阶段成员相互不可见，只进行一轮。", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("确认开始 3 次必需调用").assertIsDisplayed()
    }

    @Test
    fun partialSuccessShowsRetryAndExplicitPartialSynthesisActions() {
        var retryClicks = 0
        var synthesisClicks = 0
        val session = CrossDiscussionSessionUi(
            sessionId = "discussion-12345678",
            status = CrossDiscussionStatus.PARTIAL_SUCCESS,
            focus = "比较方案",
            responseRunId = "response-1",
            synthesisRunId = null,
            integratorSkillId = "meeting-to-action",
            successfulSkillIds = listOf("study-planner"),
            failedSkillIds = listOf("research-fact-checker"),
        )
        composeRule.setContent {
            SkillRoundtableTheme {
                IssueCollaborationSection(
                    state = content(sessions = listOf(session)),
                    contextConfirmed = false,
                    onInputChanged = {},
                    onOpenDirected = {},
                    onOpenCross = {},
                    onDismissDialog = {},
                    onToggleParticipant = {},
                    onToggleMessage = {},
                    onOpenContext = {},
                    onConfirmDirected = {},
                    onConfirmCross = {},
                    onRetryFailed = { retryClicks++ },
                    onSynthesize = { synthesisClicks++ },
                    onRetrySynthesis = {},
                    onStop = {},
                )
            }
        }

        composeRule.onNodeWithTag(JianyuAutomationTags.Collaboration.CROSS_RETRY_FAILED)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(
            JianyuAutomationTags.Collaboration.CROSS_SYNTHESIZE_AVAILABLE,
        ).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("仅整合当前成功内容").assertIsDisplayed()

        assertEquals(1, retryClicks)
        assertEquals(1, synthesisClicks)
    }

    private fun content(
        input: String = "",
        dialogMode: CollaborationDialogMode? = null,
        selectedSkillIds: Set<String> = emptySet(),
        sessions: List<CrossDiscussionSessionUi> = emptyList(),
        isCurrentStage: Boolean = true,
    ): IssueCollaborationUiState.Content = IssueCollaborationUiState.Content(
        issueId = "issue-1",
        stageId = "stage-1",
        input = input,
        roster = listOf(
            participant("study-planner", "学习规划助手", 0, selectedSkillIds),
            participant("research-fact-checker", "研究事实核查助手", 1, selectedSkillIds),
        ),
        messages = listOf(
            CollaborationMessageUi(
                messageId = 101,
                senderName = "你",
                preview = "历史问题",
                selected = false,
            ),
        ),
        dialogMode = dialogMode,
        sessions = sessions,
        isCurrentStage = isCurrentStage,
    )

    private fun participant(
        id: String,
        name: String,
        position: Int,
        selected: Set<String>,
    ) = CollaborationParticipantUi(
        skillId = id,
        displayName = name,
        avatar = name.take(1),
        responsibility = "职责-$position",
        position = position,
        selected = id in selected,
    )
}
