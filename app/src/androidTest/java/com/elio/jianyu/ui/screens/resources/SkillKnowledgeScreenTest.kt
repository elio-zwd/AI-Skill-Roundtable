package com.elio.jianyu.ui.screens.resources

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.skill.knowledge.SkillKnowledgeDocumentType
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillKnowledgeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roleDocumentsAreCollapsedByDefaultAndExpandIndependently() {
        val first = document(
            documentId = "feynman-core",
            skillId = "feynman",
            skillName = "费曼",
            title = "角色核心",
        )
        val second = document(
            documentId = "feynman-research",
            skillId = "feynman",
            skillName = "费曼",
            title = "教学研究",
        )
        val otherRole = document(
            documentId = "munger-core",
            skillId = "munger",
            skillName = "芒格",
            title = "角色核心",
        )

        setContent(documents = listOf(first, second, otherRole))

        composeRule.onNodeWithText("2 份资料").assertIsDisplayed()
        composeRule.onNodeWithTag(SkillKnowledgeTestTags.document(first.documentId))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(SkillKnowledgeTestTags.document(second.documentId))
            .assertDoesNotExist()
        composeRule.onNodeWithTag(SkillKnowledgeTestTags.document(otherRole.documentId))
            .assertDoesNotExist()

        composeRule.onNodeWithTag(SkillKnowledgeTestTags.skill(first.skillId)).performClick()

        composeRule.onNodeWithTag(SkillKnowledgeTestTags.document(first.documentId))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SkillKnowledgeTestTags.document(second.documentId))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SkillKnowledgeTestTags.document(otherRole.documentId))
            .assertDoesNotExist()

        composeRule.onNodeWithTag(SkillKnowledgeTestTags.skill(first.skillId)).performClick()
        composeRule.onNodeWithTag(SkillKnowledgeTestTags.document(first.documentId))
            .assertDoesNotExist()
    }

    @Test
    fun detailRendersMarkdownInsteadOfShowingSourceMarkers() {
        val selected = document(
            documentId = "markdown-document",
            skillId = "feynman",
            skillName = "费曼",
            title = "Markdown 示例",
            content = "# Markdown 标题\n\n- 列表项\n\n**重点内容**",
        )

        setContent(documents = listOf(selected), selectedDocumentId = selected.documentId)

        composeRule.onNodeWithTag(SkillKnowledgeTestTags.MARKDOWN_CONTENT).assertIsDisplayed()
        composeRule.onNodeWithText(selected.content).assertDoesNotExist()
    }

    private fun setContent(
        documents: List<SkillKnowledgeDocumentUiItem>,
        selectedDocumentId: String? = null,
    ) {
        composeRule.setContent {
            SkillRoundtableTheme {
                SkillKnowledgeScreen(
                    state = SkillKnowledgeUiState.Content(
                        documents = documents,
                        selectedDocumentId = selectedDocumentId,
                    ),
                    onBack = {},
                    onRetry = {},
                    onQueryChange = {},
                    onOpenDocument = {},
                    onDismissDocument = {},
                    onUseInConversation = {},
                )
            }
        }
    }

    private fun document(
        documentId: String,
        skillId: String,
        skillName: String,
        title: String,
        content: String = "正文",
    ) = SkillKnowledgeDocumentUiItem(
        documentId = documentId,
        skillId = skillId,
        skillName = skillName,
        relativePath = "$documentId.md",
        title = title,
        type = SkillKnowledgeDocumentType.CORE,
        contentHash = "hash-$documentId",
        content = content,
    )
}
