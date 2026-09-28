package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.skill.knowledge.SkillKnowledgeSelection
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuSettingsAction
import com.elio.jianyu.ui.components.JianyuTopBarLevel
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.components.MarkdownRender

object SkillKnowledgeTestTags {
    const val SCREEN = "skill_knowledge_screen"
    const val SEARCH = "skill_knowledge_search"
    const val DETAIL = "skill_knowledge_detail"
    const val MARKDOWN_CONTENT = "skill_knowledge_markdown_content"
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
    var expandedSkillIds by rememberSaveable { mutableStateOf(emptyList<String>()) }

    if (selected != null) {
        JianyuPageShell(
            title = selected.title,
            level = JianyuTopBarLevel.SECONDARY,
            subtitle = selected.skillName,
            onBack = onDismissDocument,
            actions = { JianyuSettingsAction(onOpenSettings) },
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
            MarkdownRender(
                text = selected.content,
                modifier = Modifier.testTag(SkillKnowledgeTestTags.MARKDOWN_CONTENT),
            )
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
        level = JianyuTopBarLevel.SECONDARY,
        subtitle = "Skill 角色自带的知识与参考来源",
        onBack = onBack,
        actions = { JianyuSettingsAction(onOpenSettings) },
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
                        val expanded = skillId in expandedSkillIds
                        SkillKnowledgeGroup(
                            skillId = skillId,
                            skillName = skillName,
                            documents = documents,
                            expanded = expanded,
                            onToggle = {
                                expandedSkillIds = if (expanded) {
                                    expandedSkillIds - skillId
                                } else {
                                    expandedSkillIds + skillId
                                }
                            },
                            onOpenDocument = onOpenDocument,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillKnowledgeGroup(
    skillId: String,
    skillName: String,
    documents: List<SkillKnowledgeDocumentUiItem>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { role = Role.Button }
                .clickable(onClick = onToggle)
                .testTag(SkillKnowledgeTestTags.skill(skillId)),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        skillName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${documents.size} 份资料",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) {
                        "收起 $skillName 的资料"
                    } else {
                        "展开 $skillName 的资料"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (expanded) {
            documents.forEach { item ->
                SkillKnowledgeDocumentCard(item = item, onOpenDocument = onOpenDocument)
            }
        }
    }
}

@Composable
private fun SkillKnowledgeDocumentCard(
    item: SkillKnowledgeDocumentUiItem,
    onOpenDocument: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDocument(item.documentId) }
            .testTag(SkillKnowledgeTestTags.document(item.documentId)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
