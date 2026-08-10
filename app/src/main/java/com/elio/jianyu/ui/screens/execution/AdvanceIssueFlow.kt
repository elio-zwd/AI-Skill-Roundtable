package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.StageAdvancementMeasure
import com.elio.jianyu.ui.automation.JianyuAutomationTags

@Composable
fun AdvanceIssueFlow(
    state: AdvanceIssueUiState,
    callbacks: AdvanceIssueCallbacks,
) {
    when (state) {
        AdvanceIssueUiState.Idle -> Unit
        AdvanceIssueUiState.LoadingCandidates -> LoadingDialog(callbacks.onCancel)
        is AdvanceIssueUiState.DirectionStep -> DirectionDialog(state, callbacks)
        is AdvanceIssueUiState.MeasureStep -> MeasureDialog(state, callbacks)
        is AdvanceIssueUiState.SummaryStep -> SummaryDialog(state, callbacks)
        is AdvanceIssueUiState.WaitingForRun -> WaitingForRunDialog(callbacks)
        is AdvanceIssueUiState.StoppingCurrentRun -> ProgressDialog(
            title = "正在停止当前运行",
            message = "等待所有运行终态持久化后，需要再次确认下一阶段摘要。",
        )
        is AdvanceIssueUiState.CreatingStage -> ProgressDialog(
            title = "正在创建新阶段",
            message = "仅写入阶段与继承关系，不会自动运行模型。",
        )
        is AdvanceIssueUiState.Created -> Unit
        is AdvanceIssueUiState.CreateFailure -> FailureDialog(
            message = state.message,
            onBack = callbacks.onBackToMeasures,
            onCancel = callbacks.onCancel,
        )
        is AdvanceIssueUiState.IdempotencyConflict -> FailureDialog(
            message = "相同操作标识已经用于不同内容，请关闭后重新发起推进。",
            onBack = callbacks.onBackToMeasures,
            onCancel = callbacks.onCancel,
        )
        is AdvanceIssueUiState.UndoAvailable -> UndoDialog(callbacks)
        is AdvanceIssueUiState.Undoing -> ProgressDialog(
            title = "正在撤销新阶段",
            message = "只删除尚未运行的最新阶段及其推进关系。",
        )
        is AdvanceIssueUiState.UndoFailure -> FailureDialog(
            message = state.message,
            onBack = callbacks.onDismissUndo,
            onCancel = callbacks.onDismissUndo,
        )
        is AdvanceIssueUiState.RestoredDraft -> DirectionDialog(
            AdvanceIssueUiState.DirectionStep(state.candidates, state.draft, restored = true),
            callbacks,
        )
        is AdvanceIssueUiState.StorageFailure -> FailureDialog(
            message = state.message,
            onBack = callbacks.onCancel,
            onCancel = callbacks.onCancel,
        )
    }
}

@Composable
private fun LoadingDialog(onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.DIALOG),
        title = { Text("准备推进议题") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(
                    "正在读取当前阶段、阵容、资料与成果。",
                    Modifier.padding(start = 16.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.CANCEL),
            ) { Text("取消") }
        },
    )
}

@Composable
private fun DirectionDialog(
    state: AdvanceIssueUiState.DirectionStep,
    callbacks: AdvanceIssueCallbacks,
) {
    AlertDialog(
        onDismissRequest = callbacks.onCancel,
        modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.DIALOG),
        title = { Text("推进议题 · 1/3") },
        text = {
            Column(
                modifier = Modifier
                    .testTag(JianyuAutomationTags.AdvanceIssue.DIRECTION_STEP)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择下一阶段的推进方向，两个方向可以同时选择。")
                if (state.restored) {
                    Text("已恢复未确认的推进草稿；恢复不会创建新阶段。")
                }
                ChoiceRow(
                    checked = state.draft.realitySupport,
                    text = "现实支持",
                    tag = JianyuAutomationTags.AdvanceIssue.DIRECTION_REALITY_SUPPORT,
                    onClick = {
                        callbacks.onToggleDirection(AdvanceIssueDirection.REALITY_SUPPORT)
                    },
                )
                ChoiceRow(
                    checked = state.draft.thinkingExpansion,
                    text = "思维拓展",
                    tag = JianyuAutomationTags.AdvanceIssue.DIRECTION_THINKING_EXPANSION,
                    onClick = {
                        callbacks.onToggleDirection(AdvanceIssueDirection.THINKING_EXPANSION)
                    },
                )
                if (!state.draft.hasDirection) {
                    Text("至少选择一个方向。", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = callbacks.onContinueFromDirection,
                enabled = state.draft.hasDirection,
            ) { Text("下一步") }
        },
        dismissButton = {
            TextButton(
                onClick = callbacks.onCancel,
                modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.CANCEL),
            ) { Text("取消") }
        },
    )
}

@Composable
private fun MeasureDialog(
    state: AdvanceIssueUiState.MeasureStep,
    callbacks: AdvanceIssueCallbacks,
) {
    val visibleMeasures = orderedAvailableMeasures(state.draft)
    AlertDialog(
        onDismissRequest = callbacks.onCancel,
        modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.DIALOG),
        title = { Text("推进议题 · 2/3") },
        text = {
            Column(
                modifier = Modifier
                    .testTag(JianyuAutomationTags.AdvanceIssue.MEASURE_STEP)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("选择具体措施，可多选；内部顺序按产品契约稳定保存。")
                visibleMeasures.forEach { measure ->
                    ChoiceRow(
                        checked = measure in state.draft.measures,
                        text = measure.label,
                        tag = null,
                        onClick = { callbacks.onToggleMeasure(measure) },
                    )
                }
                OutlinedTextField(
                    value = state.draft.objective,
                    onValueChange = callbacks.onObjectiveChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(JianyuAutomationTags.AdvanceIssue.CUSTOM_OBJECTIVE),
                    label = { Text("新阶段目标") },
                    supportingText = { Text("目标必须非空；编辑后旧摘要确认立即失效。") },
                    minLines = 2,
                )
                OutlinedTextField(
                    value = state.draft.expectedOutput,
                    onValueChange = callbacks.onExpectedOutputChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(JianyuAutomationTags.AdvanceIssue.EXPECTED_OUTPUT),
                    label = { Text("预期输出") },
                )
                RosterSelection(state, callbacks)
                InheritanceSelection(state, callbacks)
            }
        },
        confirmButton = {
            Button(
                onClick = callbacks.onContinueToSummary,
                enabled = state.draft.canEnterSummary,
            ) { Text("查看摘要") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = callbacks.onBackToDirection) { Text("上一步") }
                TextButton(
                    onClick = callbacks.onCancel,
                    modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.CANCEL),
                ) { Text("取消") }
            }
        },
    )
}

@Composable
private fun RosterSelection(
    state: AdvanceIssueUiState.MeasureStep,
    callbacks: AdvanceIssueCallbacks,
) {
    HorizontalDivider()
    Text("调整本阶段 Skill 阵容", style = MaterialTheme.typography.titleSmall)
    Column(modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.ROSTER)) {
        if (state.candidates.roster.isEmpty()) {
            Text(
                "当前阶段没有可继承阵容，需先完成一个 STANDARD 根运行。",
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            state.candidates.roster.forEach { member ->
                ChoiceRow(
                    checked = state.draft.roster.any {
                        it.officialSkillId == member.officialSkillId
                    },
                    text = "${member.officialSkillId}：${member.responsibility}",
                    tag = JianyuAutomationTags.AdvanceIssue.rosterMember(member.officialSkillId),
                    onClick = { callbacks.onToggleRosterMember(member.officialSkillId) },
                )
            }
            if (state.draft.roster.isEmpty()) {
                Text("至少保留一个 Skill。", color = MaterialTheme.colorScheme.error)
            }
            Text("执行前仍会重新检查官方 Skill 的当前执行资格。")
        }
    }
}

@Composable
private fun InheritanceSelection(
    state: AdvanceIssueUiState.MeasureStep,
    callbacks: AdvanceIssueCallbacks,
) {
    HorizontalDivider()
    Text("重点资料", style = MaterialTheme.typography.titleSmall)
    Column(modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.INHERITED_MATERIALS)) {
        if (state.candidates.materials.isEmpty()) {
            Text("没有可继承资料；仍可推进。")
        } else {
            state.candidates.materials.forEach { material ->
                ChoiceRow(
                    checked = material.id in state.draft.selectedMaterialIds,
                    text = material.title,
                    tag = JianyuAutomationTags.AdvanceIssue.material(material.id),
                    onClick = { callbacks.onToggleMaterial(material.id) },
                )
            }
        }
    }
    Text("继承成果", style = MaterialTheme.typography.titleSmall)
    Column(modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.INHERITED_ARTIFACTS)) {
        if (state.candidates.artifacts.isEmpty()) {
            Text("没有正式成果；仍可推进，草稿不会自动确认。")
        } else {
            state.candidates.artifacts.forEach { artifact ->
                ChoiceRow(
                    checked = artifact.id in state.draft.selectedArtifactIds,
                    text = artifact.title,
                    tag = JianyuAutomationTags.AdvanceIssue.artifact(artifact.id),
                    onClick = { callbacks.onToggleArtifact(artifact.id) },
                )
            }
        }
    }
}

@Composable
private fun SummaryDialog(
    state: AdvanceIssueUiState.SummaryStep,
    callbacks: AdvanceIssueCallbacks,
) {
    AlertDialog(
        onDismissRequest = callbacks.onCancel,
        modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.DIALOG),
        title = { Text("推进议题 · 3/3") },
        text = {
            Column(
                modifier = Modifier
                    .testTag(JianyuAutomationTags.AdvanceIssue.SUMMARY_STEP)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryItem("新阶段目标", state.draft.objective)
                SummaryItem(
                    "推进方向",
                    listOfNotNull(
                        "现实支持".takeIf { state.draft.realitySupport },
                        "思维拓展".takeIf { state.draft.thinkingExpansion },
                    ).joinToString("、"),
                )
                SummaryItem(
                    "具体措施",
                    orderedSelectedMeasures(state.draft).joinToString("、") { it.label },
                )
                SummaryItem(
                    "默认继承内容",
                    "议题背景、已确认资料、正式成果与计划阵容；不继承网络授权、敏感确认或个人背景选择。",
                )
                Column(modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.ROSTER)) {
                    Text("调整后的 Skill 阵容", style = MaterialTheme.typography.titleSmall)
                    state.draft.roster.sortedBy { it.position }.forEachIndexed { index, member ->
                        Text(
                            "${index + 1}. ${member.officialSkillId}：${member.responsibility}",
                            modifier = Modifier.testTag(
                                JianyuAutomationTags.AdvanceIssue.rosterMember(
                                    member.officialSkillId,
                                ),
                            ),
                        )
                    }
                }
                SummaryItem("重点资料", "${state.draft.selectedMaterialIds.size} 项稳定引用")
                SummaryItem("继承成果", "${state.draft.selectedArtifactIds.size} 项正式成果引用")
                SummaryItem("预期输出", state.draft.expectedOutput)
                if (state.candidates.currentStageHasDraft) {
                    Text("旧阶段草稿会保留，不会自动确认或复制。")
                }
                if (state.candidates.hasUnfinishedDiscussion) {
                    Text("旧阶段仍有待整合或部分成功的讨论；推进不会自动整合。")
                }
                if (state.candidates.hasBlockingRun) {
                    Text("当前仍有运行，确认后需选择等待或明确停止。")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = callbacks.onConfirm,
                enabled = state.draft.canEnterSummary && state.draft.summaryIsCurrent,
                modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.CONFIRM),
            ) { Text("确认创建新阶段") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = callbacks.onBackToMeasures) { Text("上一步") }
                TextButton(
                    onClick = callbacks.onCancel,
                    modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.CANCEL),
                ) { Text("取消") }
            }
        },
    )
}

@Composable
private fun WaitingForRunDialog(callbacks: AdvanceIssueCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onCancel,
        modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.DIALOG),
        title = { Text("当前阶段仍在运行") },
        text = {
            Text("不会自动停止任何运行。停止成功并持久化终态后，需要重新检查并再次确认摘要。")
        },
        confirmButton = {
            Button(
                onClick = callbacks.onStopCurrentRun,
                modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.STOP_CURRENT_RUN),
            ) { Text("明确停止当前运行后推进") }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    onClick = callbacks.onWaitForRun,
                    modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.WAIT_FOR_RUN),
                ) { Text("等待当前运行完成") }
                TextButton(onClick = callbacks.onCancel) { Text("取消推进") }
            }
        },
    )
}

@Composable
private fun UndoDialog(callbacks: AdvanceIssueCallbacks) {
    AlertDialog(
        onDismissRequest = callbacks.onDismissUndo,
        modifier = Modifier.testTag(JianyuAutomationTags.StageTimeline.UNDO_CONFIRMATION),
        title = { Text("撤销新阶段") },
        text = {
            Text("仅当这是最新阶段且从未产生 Run、消息、草稿、成果、使用快照、音频或讨论时才会删除。")
        },
        confirmButton = {
            Button(onClick = callbacks.onConfirmUndo) { Text("确认撤销") }
        },
        dismissButton = {
            TextButton(onClick = callbacks.onDismissUndo) { Text("取消") }
        },
    )
}

@Composable
private fun FailureDialog(
    message: String,
    onBack: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = Modifier
            .testTag(JianyuAutomationTags.AdvanceIssue.DIALOG)
            .semantics { contentDescription = "推进议题失败：$message" },
        title = { Text("无法完成推进") },
        text = {
            Text(
                message,
                modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.FAILURE),
            )
        },
        confirmButton = {
            Button(onClick = onBack) { Text("返回检查") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("关闭") }
        },
    )
}

@Composable
private fun ProgressDialog(title: String, message: String) {
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.DIALOG),
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(message, Modifier.padding(start = 16.dp))
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SummaryItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(value)
    }
}

@Composable
private fun ChoiceRow(
    checked: Boolean,
    text: String,
    tag: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .semantics { contentDescription = text },
        shape = MaterialTheme.shapes.small,
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = if (checked) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
            TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                Text(text, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

data class AdvanceIssueCallbacks(
    val onCancel: () -> Unit,
    val onToggleDirection: (AdvanceIssueDirection) -> Unit,
    val onContinueFromDirection: () -> Unit,
    val onBackToDirection: () -> Unit,
    val onToggleMeasure: (StageAdvancementMeasure) -> Unit,
    val onObjectiveChanged: (String) -> Unit,
    val onExpectedOutputChanged: (String) -> Unit,
    val onToggleRosterMember: (String) -> Unit,
    val onToggleMaterial: (String) -> Unit,
    val onToggleArtifact: (String) -> Unit,
    val onContinueToSummary: () -> Unit,
    val onBackToMeasures: () -> Unit,
    val onConfirm: () -> Unit,
    val onWaitForRun: () -> Unit,
    val onStopCurrentRun: () -> Unit,
    val onDismissUndo: () -> Unit,
    val onConfirmUndo: () -> Unit,
)

private val REALITY_MEASURES = listOf(
    StageAdvancementMeasure.CLARIFY_NEXT_STEP,
    StageAdvancementMeasure.FORM_EXECUTION_PLAN,
    StageAdvancementMeasure.ANALYZE_EXECUTION_OBSTACLES,
    StageAdvancementMeasure.GENERATE_DELIVERABLE,
    StageAdvancementMeasure.SET_CHECKPOINTS,
)
private val THINKING_MEASURES = listOf(
    StageAdvancementMeasure.INTRODUCE_COUNTERARGUMENT,
    StageAdvancementMeasure.FIND_MISSING_PERSPECTIVES,
    StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS,
    StageAdvancementMeasure.COMPARE_POSITIONS,
    StageAdvancementMeasure.DEEPEN_QUESTION,
)

private fun orderedAvailableMeasures(draft: AdvanceIssueDraft): List<StageAdvancementMeasure> =
    buildList {
        if (draft.realitySupport) addAll(REALITY_MEASURES)
        if (draft.thinkingExpansion) addAll(THINKING_MEASURES)
        add(StageAdvancementMeasure.CUSTOM_OBJECTIVE)
    }

private fun orderedSelectedMeasures(draft: AdvanceIssueDraft): List<StageAdvancementMeasure> =
    orderedAvailableMeasures(draft).filter(draft.measures::contains)

private val StageAdvancementMeasure.label: String
    get() = when (this) {
        StageAdvancementMeasure.CLARIFY_NEXT_STEP -> "明确下一步"
        StageAdvancementMeasure.FORM_EXECUTION_PLAN -> "形成执行计划"
        StageAdvancementMeasure.ANALYZE_EXECUTION_OBSTACLES -> "分析执行阻碍"
        StageAdvancementMeasure.GENERATE_DELIVERABLE -> "生成成果交付"
        StageAdvancementMeasure.SET_CHECKPOINTS -> "设置检查节点"
        StageAdvancementMeasure.INTRODUCE_COUNTERARGUMENT -> "引入反方意见"
        StageAdvancementMeasure.FIND_MISSING_PERSPECTIVES -> "查找遗漏视角"
        StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS -> "检查关键假设"
        StageAdvancementMeasure.COMPARE_POSITIONS -> "比较不同立场"
        StageAdvancementMeasure.DEEPEN_QUESTION -> "深入某个问题"
        StageAdvancementMeasure.CUSTOM_OBJECTIVE -> "自定义目标"
    }
