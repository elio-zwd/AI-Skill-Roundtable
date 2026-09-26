package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.skill.knowledge.SkillKnowledgeDocumentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillKnowledgeUiStateTest {
    private val documents = listOf(
        SkillKnowledgeDocumentUiItem(
            documentId = "feynman-core",
            skillId = "richard_feynman",
            skillName = "理查德·费曼",
            relativePath = "SKILL.md",
            title = "费曼角色核心",
            type = SkillKnowledgeDocumentType.CORE,
            contentHash = "hash-1",
            content = "核心",
        ),
        SkillKnowledgeDocumentUiItem(
            documentId = "feynman-research",
            skillId = "richard_feynman",
            skillName = "理查德·费曼",
            relativePath = "references/research.md",
            title = "教学方法研究",
            type = SkillKnowledgeDocumentType.KNOWLEDGE,
            contentHash = "hash-2",
            content = "研究",
        ),
        SkillKnowledgeDocumentUiItem(
            documentId = "munger-readme",
            skillId = "charlie_munger",
            skillName = "查理·芒格",
            relativePath = "README.md",
            title = "使用说明",
            type = SkillKnowledgeDocumentType.SUPPORTING,
            contentHash = "hash-3",
            content = "说明",
        ),
    )

    @Test
    fun searchMatchesSkillNameDocumentTitleAndPath() {
        val bySkill = SkillKnowledgeUiState.Content(documents, query = "费曼")
        assertEquals(2, bySkill.visibleDocuments.size)

        val byTitle = SkillKnowledgeUiState.Content(documents, query = "教学")
        assertEquals(listOf("feynman-research"), byTitle.visibleDocuments.map { it.documentId })

        val byPath = SkillKnowledgeUiState.Content(documents, query = "README")
        assertEquals(listOf("munger-readme"), byPath.visibleDocuments.map { it.documentId })
    }

    @Test
    fun groupingKeepsRoleBoundaryAndSelectionCarriesSourceMetadata() {
        val state = SkillKnowledgeUiState.Content(
            documents = documents,
            selectedDocumentId = "feynman-research",
        )

        assertEquals(2, state.groupedDocuments.size)
        val selection = requireNotNull(state.selectedDocument).toSelection()
        assertEquals("richard_feynman", selection.skillId)
        assertEquals("references/research.md", selection.relativePath)
        assertEquals("hash-2", selection.contentHash)
        assertTrue(selection.content.isNotBlank())
    }
}
