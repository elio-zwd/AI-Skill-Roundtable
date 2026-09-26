import json
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from tools.skill_knowledge.generate_index import (
    Chunk,
    build_document_id,
    chunk_markdown,
    classify_markdown,
    format_document_for_embedding,
    _collect_documents,
    _embed_text,
    embedding_failure_context,
    safe_gemini_error_detail,
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

    def test_collects_published_core_and_historical_repository_knowledge(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            assets = root / "app/src/main/assets"
            (assets / "skills/official/example").mkdir(parents=True)
            (assets / "skills/legacy-example/references").mkdir(parents=True)
            (assets / "official_skill_catalog_v1.json").write_text(
                '{"skills":[{"id":"example","nameZh":"","assetPath":"skills/legacy-example/SKILL.md","availability":{"hasAsset":true}}]}',
                encoding="utf-8",
            )
            (assets / "official_skill_execution_manifest_v2.json").write_text(
                '{"skills":[{"id":"example","assetPath":"skills/official/example/SKILL.md"}]}',
                encoding="utf-8",
            )
            (assets / "skills/official/example/SKILL.md").write_text(
                "# Current Core\n\ncurrent",
                encoding="utf-8",
            )
            (assets / "skills/legacy-example/SKILL.md").write_text(
                "# Historical Core\n\nhistorical",
                encoding="utf-8",
            )
            (assets / "skills/legacy-example/references/research.md").write_text(
                "# Research\n\nknowledge",
                encoding="utf-8",
            )

            skill = _collect_documents(root)[0]
            core = next(item for item in skill["documents"] if item["type"] == "CORE")
            knowledge = next(
                item for item in skill["documents"] if item["type"] == "KNOWLEDGE"
            )

            self.assertEqual("skills/official/example", skill["assetRoot"])
            self.assertEqual("skills/official/example/SKILL.md", core["assetPath"])
            self.assertEqual("# Current Core\n\ncurrent", core["_content"])
            self.assertEqual(
                "skills/legacy-example/references/research.md",
                knowledge["assetPath"],
            )
            self.assertEqual("references/research.md", knowledge["relativePath"])
            self.assertNotIn(
                "# Historical Core\n\nhistorical",
                [item["_content"] for item in skill["documents"]],
            )

    def test_safe_gemini_error_detail_keeps_status_and_redacts_key(self):
        key = "AIzaSyExampleSecretKey1234567890"
        body = json.dumps(
            {
                "error": {
                    "code": 400,
                    "status": "INVALID_ARGUMENT",
                    "message": f"API key {key} is invalid",
                }
            }
        ).encode("utf-8")

        detail = safe_gemini_error_detail(body, key)

        self.assertIn("INVALID_ARGUMENT", detail)
        self.assertIn("API key", detail)
        self.assertNotIn(key, detail)
        self.assertIn("<redacted>", detail)

    def test_embedding_retries_connection_reset(self):
        class Response:
            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return False

            def read(self):
                return json.dumps(
                    {"embedding": {"values": [0.25] * 768}}
                ).encode("utf-8")

        with patch(
            "tools.skill_knowledge.generate_index.urllib.request.urlopen",
            side_effect=[ConnectionResetError(10054, "connection reset"), Response()],
        ) as urlopen, patch(
            "tools.skill_knowledge.generate_index.time.sleep"
        ) as sleep:
            values = _embed_text(
                api_key="test-key",
                text="smoke",
                model="gemini-embedding-2",
                dimension=768,
            )

        self.assertEqual(768, len(values))
        self.assertEqual(2, urlopen.call_count)
        sleep.assert_called_once_with(1)

    def test_embedding_failure_context_has_metadata_without_text(self):
        detail = embedding_failure_context(
            request_index=7,
            request_total=42,
            skill_id="richard_feynman",
            asset_path="skills/feynman-skill-main/references/research.md",
            chunk_id="chunk-7",
            embedding_text="secret body",
        )

        self.assertIn("request=7/42", detail)
        self.assertIn("skillId=richard_feynman", detail)
        self.assertIn("assetPath=skills/feynman-skill-main/references/research.md", detail)
        self.assertIn("chunkId=chunk-7", detail)
        self.assertIn("chars=11", detail)
        self.assertIn("utf8Bytes=11", detail)
        self.assertIn("inputSha256=", detail)
        self.assertNotIn("secret body", detail)

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
