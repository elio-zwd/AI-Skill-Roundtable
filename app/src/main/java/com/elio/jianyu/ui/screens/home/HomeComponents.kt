package com.elio.jianyu.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elio.jianyu.home.HomeRecommendation
import com.elio.jianyu.home.RecommendationMode
import com.elio.jianyu.home.RecommendedSkill
import com.elio.jianyu.home.ValueDirection
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuMetadataRow

@Composable
internal fun HomeDirectionSelector(
    selected: Set<ValueDirection>,
    onToggle: (ValueDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("价值方向（可跳过或同时选择）", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = ValueDirection.REALITY_SUPPORT in selected,
                onClick = { onToggle(ValueDirection.REALITY_SUPPORT) },
                label = { Text("现实支持") },
                modifier = Modifier
                    .weight(1f)
                    .testTag(JianyuAutomationTags.Home.DIRECTION_REALITY_SUPPORT),
            )
            FilterChip(
                selected = ValueDirection.THINKING_EXPANSION in selected,
                onClick = { onToggle(ValueDirection.THINKING_EXPANSION) },
                label = { Text("思维拓展") },
                modifier = Modifier
                    .weight(1f)
                    .testTag(JianyuAutomationTags.Home.DIRECTION_THINKING_EXPANSION),
            )
        }
    }
}

@Composable
internal fun HomeExampleQuestions(
    examples: List<HomeExampleQuestion>,
    onUseExample: (HomeExampleQuestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("示例问题", style = MaterialTheme.typography.titleSmall)
        examples.forEach { example ->
            OutlinedButton(
                onClick = { onUseExample(example) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Home.exampleQuestion(example.stableId)),
            ) {
                Text(example.text)
            }
        }
    }
}

@Composable
internal fun HomeRecommendationCard(
    recommendation: HomeRecommendation,
    onToggleSkill: (String) -> Unit,
    onResponsibilityChanged: (String, String) -> Unit,
    onMoveSkill: (String, Int) -> Unit,
    onModeChanged: (RecommendationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("推荐结果", style = MaterialTheme.typography.titleMedium)
            Text(recommendation.modeReason, style = MaterialTheme.typography.bodyMedium)
            Text(
                "来源：本地官方 Skill Catalog（不是模型分析）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = recommendation.mode == RecommendationMode.SINGLE,
                    onClick = { onModeChanged(RecommendationMode.SINGLE) },
                    label = { Text("单 Skill") },
                )
                FilterChip(
                    selected = recommendation.mode == RecommendationMode.MULTI,
                    onClick = { onModeChanged(RecommendationMode.MULTI) },
                    label = { Text("多 Skill") },
                )
            }
            recommendation.skills.sortedBy(RecommendedSkill::position).forEach { skill ->
                HomeRecommendedSkillCard(
                    skill = skill,
                    onToggle = { onToggleSkill(skill.skillId) },
                    onResponsibilityChanged = { onResponsibilityChanged(skill.skillId, it) },
                    onMoveUp = { onMoveSkill(skill.skillId, -1) },
                    onMoveDown = { onMoveSkill(skill.skillId, 1) },
                )
            }
            JianyuMetadataRow("预期输出", recommendation.expectedOutput)
        }
    }
}

@Composable
private fun HomeRecommendedSkillCard(
    skill: RecommendedSkill,
    onToggle: () -> Unit,
    onResponsibilityChanged: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.Home.recommendationSkill(skill.skillId)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = skill.selected,
                    enabled = skill.executable,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { role = Role.Checkbox },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(skill.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (skill.executable) "可执行" else "当前不可执行",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(skill.reason, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = skill.responsibility,
                onValueChange = onResponsibilityChanged,
                label = { Text("本次职责") },
                enabled = skill.selected,
                modifier = Modifier.fillMaxWidth(),
            )
            JianyuMetadataRow("风险与边界", skill.riskDisclosure)
            JianyuMetadataRow("时效", skill.freshnessDisclosure)
            JianyuMetadataRow("联网", skill.networkRequirement)
            JianyuMetadataRow("资料", skill.materialRequirement)
            JianyuMetadataRow("输出", skill.expectedOutput)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMoveUp) { Text("上移") }
                OutlinedButton(onClick = onMoveDown) { Text("下移") }
            }
        }
    }
}

@Composable
internal fun HomeFinalReviewCard(
    recommendation: HomeRecommendation,
    directions: Set<ValueDirection>,
    selectedContextCount: Int,
    onStart: () -> Unit,
    startEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.Home.FINAL_REVIEW),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("最终确认", style = MaterialTheme.typography.titleMedium)
            Text(recommendation.questionSummary)
            JianyuMetadataRow(
                "价值方向",
                directions.toDisplayText(),
            )
            JianyuMetadataRow(
                "模式",
                if (recommendation.mode == RecommendationMode.SINGLE) "单 Skill" else "多 Skill",
            )
            recommendation.selectedSkills.forEach { skill ->
                JianyuMetadataRow(skill.displayName, skill.responsibility)
            }
            JianyuMetadataRow("资料与个人背景", "已确认 $selectedContextCount 项")
            JianyuMetadataRow("预期输出", recommendation.expectedOutput)
            Text(
                "确认后将创建一个新议题和初始阶段，并开始模型调用。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onStart,
                enabled = startEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Home.START_ISSUE_BUTTON),
            ) {
                Text("确认并开始运行")
            }
        }
    }
}

internal fun Set<ValueDirection>.toDisplayText(): String = when {
    isEmpty() -> "未指定"
    size == 2 -> "现实支持 + 思维拓展"
    ValueDirection.REALITY_SUPPORT in this -> "现实支持"
    else -> "思维拓展"
}
