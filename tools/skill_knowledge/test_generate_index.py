import tempfile
import unittest
from pathlib import Path

from tools.skill_knowledge.generate_index import (
    Chunk,
    build_document_id,
    chunk_markdown,
    classify_markdown,
    format_document_for_embedding,
)


class SkillKnowledgeIndexGeneratorTest(unittest.TestCase):
    def test_classifies_markdown_by_role(self):
        self.assertEqual("CORE", classify_markdown("SKILL.md"))
        self.assertEqual("KNOWLEDGE", classify_markdown("references/a.md"))
        self.assertEqual("KNOWLEDGE", classify_markdown("references/research/b.md"))
        self.assertEqual("KNOWLEDGE", classify_markdown("research/c.md"))
        self.assertEqual("KNOWLEDGE", classify_markdown("examples/demo.md"))
        self.assertEqual("SUPPORTING", classify_markdown("README.md"))
        self.assertEqual("SUPPORTING", classify_markdown("references/README.md"))
        self.assertEqual("SUPPORTING", classify_markdown("notes.md"))

    def test_chunker_preserves_heading_path_and_size_bound(self):
        body = "# 第一章\n\n" + ("甲" * 1200) + "\n\n## 第二节\n\n" + ("乙" * 2200)
        chunks = chunk_markdown(
            document_id="doc",
            text=body,
            max_chars=1800,
            overlap_chars=200,
        )

        self.assertGreaterEqual(len(chunks), 3)
        self.assertTrue(all(chunk.text for chunk in chunks))
        self.assertTrue(all(len(chunk.text) <= 1800 for chunk in chunks))
        self.assertEqual("第一章", chunks[0].heading_path)
        self.assertTrue(any("第二节" in chunk.heading_path for chunk in chunks))
        self.assertTrue(all(chunk.start_character < chunk.end_character for chunk in chunks))

    def test_ids_and_chunk_order_are_deterministic(self):
        document_id = build_document_id("richard_feynman", "references/research.md")
        first = chunk_markdown(document_id, "# A\n\n" + ("x" * 2400))
        second = chunk_markdown(document_id, "# A\n\n" + ("x" * 2400))

        self.assertEqual(
            [(c.chunk_id, c.start_character, c.end_character) for c in first],
            [(c.chunk_id, c.start_character, c.end_character) for c in second],
        )

    def test_document_embedding_format_is_stable(self):
        self.assertEqual(
            "title: 费曼表达风格 | text: 表达 > 类比\n用生活语言解释复杂概念",
            format_document_for_embedding(
                title="费曼表达风格",
                heading_path="表达 > 类比",
                chunk_text="用生活语言解释复杂概念",
            ),
        )


if __name__ == "__main__":
    unittest.main()
