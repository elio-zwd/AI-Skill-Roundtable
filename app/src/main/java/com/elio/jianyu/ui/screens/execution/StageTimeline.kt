package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.automation.JianyuAutomationTags

@Composable
fun StageTimeline(
    candidates: AdvanceIssueCandidates?,
    onOpenStage: (String, String) -> Unit,
    onAdvanceIssue: () -> Unit,
    onUndoStage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(JianyuAutomationTags.StageTimeline.TIMELINE),
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            candidates?.stages.orEmpty().forEach { stage ->
                val current = stage.id == candidates?.currentStage?.id
                AssistChip(
                    onClick = { onOpenStage(stage.issueId, stage.id) },
                    label = {
                        Text(
                            if (current) "${stage.title} · 当前" else stage.title,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    modifier = Modifier
                        .testTag(JianyuAutomationTags.StageTimeline.item(stage.id))
                        .then(
                            if (current) {
                                Modifier
                                    .testTag(JianyuAutomationTags.StageTimeline.CURRENT)
                                    .semantics { contentDescription = "当前阶段：${stage.sequenceIndex + 1}" }
                            } else {
                                Modifier.semantics {
                                    contentDescription = "历史阶段：${stage.sequenceIndex + 1}"
                                }
                            },
                        ),
                )
            }
            Button(
                onClick = onAdvanceIssue,
                enabled = candidates != null,
                modifier = Modifier.testTag(JianyuAutomationTags.AdvanceIssue.BUTTON),
            ) {
                Text("推进议题")
            }
            if (candidates?.undoAvailable == true) {
                OutlinedButton(
                    onClick = onUndoStage,
                    modifier = Modifier.testTag(JianyuAutomationTags.StageTimeline.UNDO_BUTTON),
                ) {
                    Text("撤销新阶段")
                }
            }
        }
    }
}
