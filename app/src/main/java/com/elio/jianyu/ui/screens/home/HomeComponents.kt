package com.elio.jianyu.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
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
import com.elio.jianyu.home.HomeExecutionConsentSnapshot
import com.elio.jianyu.home.HomeRecommendation
import com.elio.jianyu.home.RecommendationMode
import com.elio.jianyu.home.RecommendedSkill
import com.elio.jianyu.home.ValueDirection
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.theme.skillRoundtableColors

@Composable
internal fun HomePageIntroduction(
    eyebrow: String,
    title: String,
    copy: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = copy,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
        Text("这次希望获得什么？", style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeDirectionCard(
                selected = ValueDirection.REALITY_SUPPORT in selected,
                title = "现实支持",
                description = "明确下一步、计划、阻碍与检查点",
                accent = MaterialTheme.skillRoundtableColors.practicalDirection,
                onClick = { onToggle(ValueDirection.REALITY_SUPPORT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(JianyuAutomationTags.Home.DIRECTION_REALITY_SUPPORT),
            )
            HomeDirectionCard(
                selected = ValueDirection.THINKING_EXPANSION in selected,
                title = "思维拓展",
                description = "反方、假设、遗漏视角与立场比较",
                accent = MaterialTheme.skillRoundtableColors.perspectiveDirection,
                onClick = { onToggle(ValueDirection.THINKING_EXPANSION) },
                modifier = Modifier
                    .weight(1f)
                    .testTag(JianyuAutomationTags.Home.DIRECTION_THINKING_EXPANSION),
            )
        }
    }
}

@Composable
private fun HomeDirectionCard(
    selected: Boolean,
    title: String,
    description: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (selected) "已选择" else "点击选择",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    executionConsent: HomeExecutionConsentSnapshot,
    consentIssues: List<String>,
    onNetworkAuthorized: (Boolean) -> Unit,
    onHighStakesConfirmed: (Boolean) -> Unit,
    onPersonDisclaimerConfirmed: (Boolean) -> Unit,
    onStart: () -> Unit,
    startEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val selected = recommendation.selectedSkills
    val needsNetwork = selected.any(RecommendedSkill::requiresNetworkAuthorization)
    val needsHighStakes = selected.any(RecommendedSkill::requiresHighStakesConfirmation)
    val needsPersonDisclaimer = selected.any(RecommendedSkill::isPersonPerspective)
    val blocksRestrictedMaterial = selected.any(RecommendedSkill::prohibitsExternalMaterial) &&
        executionConsent.restrictedMaterialPresent &&
        executionConsent.materialMayLeaveDevice

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
            JianyuMetadataRow("价值方向", directions.toDisplayText())
            JianyuMetadataRow(
                "模式",
                if (recommendation.mode == RecommendationMode.SINGLE) "单 Skill" else "多 Skill",
            )
            selected.forEach { skill ->
                JianyuMetadataRow(skill.displayName, skill.responsibility)
                if (skill.isPersonPerspective || skill.requiresHighStakesConfirmation) {
                    Text(
                        skill.riskDisclosure,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            JianyuMetadataRow("资料与个人背景", "已确认 $selectedContextCount 项")
            JianyuMetadataRow("预期输出", recommendation.expectedOutput)

            if (needsNetwork) {
                HomeConsentCheckbox(
                    checked = executionConsent.networkAuthorized,
                    label = "我同意本次为核验当前事实使用联网能力；没有真实来源时不得声称已检索。",
                    tag = HomeTestTags.NETWORK_AUTHORIZATION,
                    onCheckedChange = onNetworkAuthorized,
                )
            }
            if (needsHighStakes) {
                HomeConsentCheckbox(
                    checked = executionConsent.highStakesConfirmed,
                    label = "我已阅读高后果边界，结果不替代现实专业复核或紧急帮助。",
                    tag = HomeTestTags.HIGH_STAKES_CONFIRMATION,
                    onCheckedChange = onHighStakesConfirmed,
                )
            }
            if (needsPersonDisclaimer) {
                HomeConsentCheckbox(
                    checked = executionConsent.personDisclaimerConfirmed,
                    label = "我理解人物 Skill 只是 AI 模拟公开思考框架，不代表本人。",
                    tag = HomeTestTags.PERSON_DISCLAIMER_CONFIRMATION,
                    onCheckedChange = onPersonDisclaimerConfirmed,
                )
            }
            if (blocksRestrictedMaterial) {
                Text(
                    "所选资料包含禁止外传的敏感正文，当前外部模型运行被阻止。请取消该资料，改用自行脱敏摘要或仅查看本地通用清单。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(HomeTestTags.RESTRICTED_MATERIAL_BLOCK),
                )
            }
            if (consentIssues.isNotEmpty() && !blocksRestrictedMaterial) {
                Text(
                    "仍需完成：${consentIssues.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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

@Composable
private fun HomeConsentCheckbox(
    checked: Boolean,
    label: String,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { role = Role.Checkbox },
        )
        Text(label, modifier = Modifier.weight(1f))
    }
}

internal fun Set<ValueDirection>.toDisplayText(): String = when {
    isEmpty() -> "未指定"
    size == 2 -> "现实支持 + 思维拓展"
    ValueDirection.REALITY_SUPPORT in this -> "现实支持"
    else -> "思维拓展"
}
