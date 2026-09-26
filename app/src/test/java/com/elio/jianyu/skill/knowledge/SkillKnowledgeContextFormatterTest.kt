package com.elio.jianyu.skill.knowledge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillKnowledgeContextFormatterTest {
    @Test
    fun formatterSeparatesKnowledgeMapFromRetrievedSources() {
        val formatted = SkillKnowledgeContextFormatter.format(
            SkillKnowledgeRetrievalResult.Available(
                knowledgeMap = "- 费曼研究 [KNOWLEDGE]\n- README [SUPPORTING]",
                hits = listOf(
                    SkillKnowledgeHit(
                        skillId = "richard_feynman",
                        documentId = "doc",
                        relativePath = "references/research.md",
                        title = "费曼研究",
                        headingPath = "教学 > 类比",
                        content = "从可验证的例子开始。",
                        score = 0.91f,
                        retrievalOrder = 0,
                    ),
                ),
            ),
        )

        assertTrue(formatted.contains("=== Skill Knowledge Map ==="))
        assertTrue(formatted.contains("- 费曼研究 [KNOWLEDGE]"))
        assertTrue(formatted.contains("=== Retrieved Skill Knowledge ==="))
        assertTrue(formatted.contains("[source: references/research.md#教学 > 类比]"))
        assertTrue(formatted.contains("从可验证的例子开始。"))
        assertFalse(formatted.contains("README 正文"))
    }
}
