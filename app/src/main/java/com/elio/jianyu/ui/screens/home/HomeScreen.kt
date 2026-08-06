package com.elio.jianyu.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.home.HomeWorkflow
import com.elio.jianyu.home.HomeWorkflowStep
import com.elio.jianyu.home.RecommendationMode
import com.elio.jianyu.home.ValueDirection
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onQuestionChanged: (String) -> Unit,
    onClearQuestion: () -> Unit,
    onToggleDirection: (ValueDirection) -> Unit,
    onUseExample: (HomeExampleQuestion) -> Unit,
    onRequestRecommendation: () -> Unit,
    onSaveIssueOnly: () -> Unit,
    onToggleSkill: (String) -> Unit,
    onResponsibilityChanged: (String, String) -> Unit,
    onMoveSkill: (String, Int) -> Unit,
    onModeChanged: (RecommendationMode) -> Unit,
    onConfirmRecommendation: () -> Unit,
    onOpenContextConfirmation: () -> Unit,
    onNetworkAuthorized: (Boolean) -> Unit,
    onHighStakesConfirmed: (Boolean) -> Unit,
    onPersonDisclaimerConfirmed: (Boolean) -> Unit,
    onBrowseSkills: () -> Unit,
    onStartIssue: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    examples: List<HomeExampleQuestion> = defaultHomeExampleQuestions,
) {
    val workflow = uiState.workflow
    JianyuPageShell(
        title = "见域",
        subtitle = "从问题开始，再决定需要哪些视角与能力",
        onOpenSettings = onOpenSettings,
        contentScrollable = true,
        modifier = modifier.testTag(HomeTestTags.SCREEN),
    ) {
        if (workflow.restored) {
            JianyuStateCard(
                title = "已恢复未完成草稿",
                message = "问题、方向和选择已恢复；系统没有自动推荐、保存或开始运行。",
                modifier = Modifier.testTag(JianyuAutomationTags.Home.DRAFT_RECOVERY),
            )
        }

        OutlinedTextField(
            value = uiState.question,
            onValueChange = onQuestionChanged,
            label = { Text("你现在想解决什么问题？") },
            supportingText = { Text("可以是疑问、目标、选择或需要完成的任务") },
            minLines = 4,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HomeTestTags.QUESTION_INPUT),
        )
        if (uiState.question.isNotEmpty()) {
            TextButton(
                onClick = onClearQuestion,
                modifier = Modifier.testTag(JianyuAutomationTags.Home.QUESTION_CLEAR_BUTTON),
            ) {
                Text("清空问题")
            }
        }

        HomeDirectionSelector(
            selected = workflow.draft.directions,
            onToggle = onToggleDirection,
        )

        HomeExampleQuestions(
            examples = examples,
            onUseExample = onUseExample,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onSaveIssueOnly,
                enabled = uiState.canSaveIssueOnly,
                modifier = Modifier
                    .weight(1f)
                    .testTag(JianyuAutomationTags.Home.SAVE_ISSUE_ONLY_BUTTON),
            ) {
                Text("仅保存议题")
            }
            Button(
                onClick = onRequestRecommendation,
                enabled = uiState.canRequestRecommendation,
                modifier = Modifier
                    .weight(1f)
                    .testTag(HomeTestTags.RECOMMENDATION_REQUEST_BUTTON),
            ) {
                Text("获取建议")
            }
        }

        uiState.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiState.recommendationVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTestTags.RECOMMENDATION_RESULT),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (workflow.step) {
                    HomeWorkflowStep.RECOMMENDATION_LOADING -> Row(
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.Home.RECOMMENDATION_LOADING,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("正在根据官方 Skill Catalog 生成本地建议…")
                    }
                    HomeWorkflowStep.RECOMMENDATION_FAILURE -> JianyuStateCard(
                        title = "推荐失败",
                        message = "问题草稿已保留。可以重试、修改问题、浏览 Skill 或仅保存议题。",
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.Home.RECOMMENDATION_FAILURE,
                        ),
                        actionLabel = "重试",
                        onAction = onRequestRecommendation,
                    )
                    HomeWorkflowStep.NO_SUITABLE_SKILL -> JianyuStateCard(
                        title = "没有合适的 Skill",
                        message = "当前官方目录没有足够匹配的候选，不会强行推荐人物视角。",
                        modifier = Modifier.testTag(
                            JianyuAutomationTags.Home.RECOMMENDATION_FAILURE,
                        ),
                        actionLabel = "浏览 Skill",
                        onAction = onBrowseSkills,
                    )
                    else -> Unit
                }

                workflow.recommendation?.let { recommendation ->
                    HomeRecommendationCard(
                        recommendation = recommendation,
                        onToggleSkill = onToggleSkill,
                        onResponsibilityChanged = onResponsibilityChanged,
                        onMoveSkill = onMoveSkill,
                        onModeChanged = onModeChanged,
                    )
                    if (!workflow.recommendationConfirmed) {
                        Button(
                            onClick = onConfirmRecommendation,
                            enabled = recommendation.selectedSkills.isNotEmpty() &&
                                recommendation.selectedSkills.all { it.executable } &&
                                !workflow.operationInFlight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(HomeTestTags.RECOMMENDATION_CONFIRM_BUTTON),
                        ) {
                            Text("确认阵容并选择上下文")
                        }
                    } else if (!workflow.contextSelection.confirmed) {
                        Button(
                            onClick = onOpenContextConfirmation,
                            enabled = !workflow.operationInFlight,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(HomeTestTags.CONTEXT_CONFIRMATION_BUTTON),
                        ) {
                            Text("选择并确认资料与个人背景")
                        }
                    }
                }
            }
        }

        if (workflow.contextSelection.confirmed) {
            JianyuStateCard(
                title = "上下文已确认",
                message = "已选择 ${workflow.contextSelection.items.count { it.selected }} 项；个人背景没有默认勾选。",
                modifier = Modifier.testTag(HomeTestTags.CONTEXT_CONFIRMED_SUMMARY),
                actionLabel = "重新检查上下文",
                onAction = onOpenContextConfirmation,
            )
        }

        if (uiState.finalReviewVisible) {
            workflow.recommendation?.let { recommendation ->
                HomeFinalReviewCard(
                    recommendation = recommendation,
                    directions = workflow.draft.directions,
                    selectedContextCount = workflow.contextSelection.items.count { it.selected },
                    executionConsent = workflow.executionConsent,
                    consentIssues = HomeWorkflow.executionConsentIssues(workflow),
                    onNetworkAuthorized = onNetworkAuthorized,
                    onHighStakesConfirmed = onHighStakesConfirmed,
                    onPersonDisclaimerConfirmed = onPersonDisclaimerConfirmed,
                    onStart = onStartIssue,
                    startEnabled = workflow.finalConfirmationReady && !workflow.operationInFlight,
                )
            }
        }

        if (workflow.step == HomeWorkflowStep.SAVED_NOT_STARTED) {
            JianyuStateCard(
                title = "议题已保存，尚未开始",
                message = "可以修正上下文、执行资格或环境条件后，使用同一议题继续。",
                actionLabel = "重新检查上下文",
                onAction = onOpenContextConfirmation,
            )
        }

        if (workflow.operationInFlight) {
            Text(
                text = when (workflow.step) {
                    HomeWorkflowStep.SAVING_ISSUE -> "正在保存议题…"
                    HomeWorkflowStep.STARTING_EXECUTION -> "正在创建议题并启动执行…"
                    else -> "正在处理…"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
