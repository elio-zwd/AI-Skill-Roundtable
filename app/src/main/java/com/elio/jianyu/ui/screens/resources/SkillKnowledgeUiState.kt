package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.skill.knowledge.SkillKnowledgeDocumentType
import com.elio.jianyu.skill.knowledge.SkillKnowledgeSelection

data class SkillKnowledgeDocumentUiItem(
    val documentId: String,
    val skillId: String,
    val skillName: String,
    val relativePath: String,
    val title: String,
    val type: SkillKnowledgeDocumentType,
    val contentHash: String,
    val content: String,
) {
    fun toSelection(): SkillKnowledgeSelection = SkillKnowledgeSelection(
        skillId = skillId,
        documentId = documentId,
        title = title,
        relativePath = relativePath,
        content = content,
        contentHash = contentHash,
    )
}

sealed interface SkillKnowledgeUiState {
    data object Loading : SkillKnowledgeUiState

    data class Content(
        val documents: List<SkillKnowledgeDocumentUiItem>,
        val query: String = "",
        val selectedDocumentId: String? = null,
        val message: String? = null,
    ) : SkillKnowledgeUiState {
        val visibleDocuments: List<SkillKnowledgeDocumentUiItem>
            get() {
                val needle = query.trim()
                return documents.filter { item ->
                    needle.isBlank() ||
                        item.skillName.contains(needle, ignoreCase = true) ||
                        item.title.contains(needle, ignoreCase = true) ||
                        item.relativePath.contains(needle, ignoreCase = true)
                }
            }

        val groupedDocuments: List<Pair<String, List<SkillKnowledgeDocumentUiItem>>>
            get() = visibleDocuments
                .groupBy { it.skillId }
                .entries
                .sortedBy { (_, items) -> items.firstOrNull()?.skillName.orEmpty() }
                .map { it.key to it.value.sortedBy(SkillKnowledgeDocumentUiItem::relativePath) }

        val selectedDocument: SkillKnowledgeDocumentUiItem?
            get() = documents.firstOrNull { it.documentId == selectedDocumentId }
    }

    data class Failure(
        val message: String,
    ) : SkillKnowledgeUiState
}

internal fun skillKnowledgeTypeLabel(type: SkillKnowledgeDocumentType): String = when (type) {
    SkillKnowledgeDocumentType.CORE -> "角色核心"
    SkillKnowledgeDocumentType.KNOWLEDGE -> "参考知识"
    SkillKnowledgeDocumentType.SUPPORTING -> "说明文档"
}
