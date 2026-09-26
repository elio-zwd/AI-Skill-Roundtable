package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.skill.knowledge.SkillKnowledgeSelection
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

object SkillKnowledgeTestTags {
    const val SCREEN = "skill_knowledge_screen"
    const val SEARCH = "skill_knowledge_search"
    const val DETAIL = "skill_knowledge_detail"
    const val USE_IN_CONVERSATION = "skill_knowledge_use_in_conversation"

    fun skill(skillId: String) = "skill_knowledge_skill_$skillId"
    fun document(documentId: String) = "skill_knowledge_document_$documentId"
}

@Composable
fun SkillKnowledgeScreen(
    state: SkillKnowledgeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onDismissDocument: () -> Unit,
    onUseInConversation: (SkillKnowledgeSelection) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val content = state as? SkillKnowledgeUiState.Content
    val selected = content?.selectedDocument

    if (selected != null) {
        JianyuPageShell(
            title = selected.title,
            subtitle = selected.skillName,
            onBack = onDismissDocument,
            onOpenSettings = onOpenSettings,
            contentScrollable = true,
            modifier = Modifier.testTag(SkillKnowledgeTestTags.DETAIL),
        ) {
            Text(
                "Skill 角色：${selected.skillName}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "类型：${skillKnowledgeTypeLabel(selected.type)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "路径：${selected.relativePath}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onUseInConversation(selected.toSelection()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SkillKnowledgeTestTags.USE_IN_CONVERSATION),
            ) {
                Text("带入当前会话")
            }
            Text(
                "正文",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(selected.content, style = MaterialTheme.typography.bodyMedium)
            content.message?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }

    JianyuPageShell(
        title = "Skill 资料",
        subtitle = "Skill 角色自带的知识与参考来源",
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        contentScrollable = true,
        modifier = Modifier.testTag(SkillKnowledgeTestTags.SCREEN),
    ) {
        when (state) {
            SkillKnowledgeUiState.Loading -> Text("正在读取 Skill 资料…")
            is SkillKnowledgeUiState.Failure -> JianyuStateCard(
                title = "Skill 资料读取失败",
                message = state.message,
                actionLabel = "重试",
                onAction = onRetry,
            )
            is SkillKnowledgeUiState.Content -> {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("搜索角色、文档标题或路径") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SkillKnowledgeTestTags.SEARCH),
                    singleLine = true,
                )
                state.message?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (state.groupedDocuments.isEmpty()) {
                    JianyuStateCard(
                        title = "没有匹配的 Skill 资料",
                        message = "可更换角色名、文档标题或路径关键词。",
                    )
                } else {
                    state.groupedDocuments.forEach { (skillId, documents) ->
                        val skillName = documents.first().skillName
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(SkillKnowledgeTestTags.skill(skillId)),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                skillName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            documents.forEach { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenDocument(item.documentId) }
                                        .testTag(SkillKnowledgeTestTags.document(item.documentId)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            item.title,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            "${skillKnowledgeTypeLabel(item.type)} · ${item.relativePath}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
