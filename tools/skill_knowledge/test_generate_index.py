import json
import io
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
    _parse_batch_embeddings,
    EmbeddingCache,
    SlidingWindowRateLimiter,
    _embed_batch,
    _build_manifest_and_index,
    _validate,
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

    def test_default_chunk_budget_stays_below_free_tier_input_window(self):
        chunks = chunk_markdown("doc", "# 标题\n\n" + "中文内容" * 1000)
        self.assertTrue(all(len(chunk.text) <= 900 for chunk in chunks))

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
        key = "example-secret-key-for-test"
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

    def test_batch_response_requires_count_order_and_dimension(self):
        payload = json.dumps({"embeddings": [{"values": [0.1] * 768}, {"values": [0.2] * 768}]}).encode()
        vectors = _parse_batch_embeddings(payload, expected_count=2, dimension=768)
        self.assertEqual([0.1, 0.2], [vector[0] for vector in vectors])
        with self.assertRaises(ValueError):
            _parse_batch_embeddings(payload, expected_count=3, dimension=768)
        with self.assertRaises(ValueError):
            _parse_batch_embeddings(json.dumps({"embeddings": [{"values": [0.1] * 767}]}).encode(), 1, 768)

    def test_checkpoint_reuses_matching_input_and_invalidates_changed_contract(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            cache = EmbeddingCache(Path(temp_dir))
            vector = [0.25] * 768
            cache.put("gemini-embedding-2", 768, "input A", vector)
            self.assertEqual(vector, cache.get("gemini-embedding-2", 768, "input A"))
            self.assertIsNone(cache.get("gemini-embedding-2", 768, "input B"))
            self.assertIsNone(cache.get("other-model", 768, "input A"))
            self.assertIsNone(cache.get("gemini-embedding-2", 512, "input A"))

    def test_limiter_obeys_request_and_estimated_token_windows(self):
        now = [0.0]
        sleeps = []
        def sleep(seconds):
            sleeps.append(seconds)
            now[0] += seconds
        limiter = SlidingWindowRateLimiter(max_requests_per_minute=2, max_input_tokens_per_minute=10, clock=lambda: now[0], sleep=sleep)
        limiter.acquire(4)
        limiter.acquire(4)
        limiter.acquire(4)
        self.assertEqual([60.0], sleeps)

    def test_batch_retries_only_empty_body_400_and_transient_errors(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self, exc_type, exc, tb): return False
            def read(self): return json.dumps({"embeddings": [{"values": [0.25] * 768}]}).encode()
        error = __import__("urllib.error", fromlist=["HTTPError"]).HTTPError("url", 400, "bad request", {}, __import__("io").BytesIO(b""))
        with patch("tools.skill_knowledge.generate_index.urllib.request.urlopen", side_effect=[error, Response()]) as urlopen, patch("tools.skill_knowledge.generate_index.time.sleep") as sleep:
            values = _embed_batch("test-key", ["input"], "gemini-embedding-2", 768)
        self.assertEqual(768, len(values[0]))
        self.assertEqual(2, urlopen.call_count)
        sleep.assert_called_once()
        error.close()

    def test_batch_honors_structured_429_retry_delay(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self, exc_type, exc, tb): return False
            def read(self): return json.dumps({"embeddings": [{"values": [0.25] * 768}]}).encode()
        from urllib.error import HTTPError
        error_body = json.dumps({"error": {"status": "RESOURCE_EXHAUSTED", "message": "Please retry in 30.5s."}}).encode()
        error = HTTPError("url", 429, "quota", {}, io.BytesIO(error_body))
        with patch("tools.skill_knowledge.generate_index.urllib.request.urlopen", side_effect=[error, Response()]), patch("tools.skill_knowledge.generate_index.time.sleep") as sleep:
            _embed_batch("test-key", ["input"], "gemini-embedding-2", 768)
        self.assertGreaterEqual(sleep.call_args.args[0], 30.5)
        error.close()

    def test_batch_can_recover_after_four_rate_limit_responses(self):
        class Response:
            def __enter__(self): return self
            def __exit__(self, exc_type, exc, tb): return False
            def read(self): return json.dumps({"embeddings": [{"values": [0.25] * 768}]}).encode()
        from urllib.error import HTTPError
        errors = [HTTPError("url", 429, "quota", {}, io.BytesIO(b'{"error":{"status":"RESOURCE_EXHAUSTED","message":"Please retry in 1s."}}')) for _ in range(4)]
        with patch("tools.skill_knowledge.generate_index.urllib.request.urlopen", side_effect=[*errors, Response()]) as urlopen, patch("tools.skill_knowledge.generate_index.time.sleep"):
            vectors = _embed_batch("test-key", ["input"], "gemini-embedding-2", 768)
        self.assertEqual(5, urlopen.call_count)
        self.assertEqual(768, len(vectors[0]))
        for error in errors: error.close()

    def test_batch_recovers_after_four_consecutive_tls_eofs(self):
        from urllib.error import URLError

        class Response:
            def __enter__(self): return self
            def __exit__(self, exc_type, exc, tb): return False
            def read(self): return json.dumps({"embeddings": [{"values": [0.25] * 768}]}).encode()

        errors = [URLError(OSError("TLS handshake EOF")) for _ in range(4)]
        with patch(
            "tools.skill_knowledge.generate_index.urllib.request.urlopen",
            side_effect=[*errors, Response()],
        ) as urlopen, patch("tools.skill_knowledge.generate_index.time.sleep") as sleep:
            vectors = _embed_batch("test-key", ["input"], "gemini-embedding-2", 768)

        self.assertEqual(5, urlopen.call_count)
        self.assertEqual(4, sleep.call_count)
        self.assertEqual(768, len(vectors[0]))

    def test_batch_tls_eof_retries_remain_bounded(self):
        from urllib.error import URLError

        errors = [URLError(OSError("TLS handshake EOF")) for _ in range(8)]
        with patch(
            "tools.skill_knowledge.generate_index.urllib.request.urlopen",
            side_effect=errors,
        ) as urlopen, patch("tools.skill_knowledge.generate_index.time.sleep"):
            with self.assertRaisesRegex(RuntimeError, "transient network retries"):
                _embed_batch("test-key", ["input"], "gemini-embedding-2", 768)

        self.assertEqual(8, urlopen.call_count)

    def test_generation_resumes_from_checkpoint_without_partial_assets(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            assets = root / "app/src/main/assets"
            (assets / "skills/official/example/references").mkdir(parents=True)
            (assets / "official_skill_catalog_v1.json").write_text(
                '{"skills":[{"id":"example","assetPath":"skills/official/example/SKILL.md","availability":{"hasAsset":true}}]}',
                encoding="utf-8",
            )
            (assets / "official_skill_execution_manifest_v2.json").write_text(
                '{"skills":[{"id":"example","assetPath":"skills/official/example/SKILL.md"}]}',
                encoding="utf-8",
            )
            (assets / "skills/official/example/SKILL.md").write_text("# Current Core", encoding="utf-8")
            knowledge = assets / "skills/official/example/references/a.md"
            knowledge.write_text("# A\n\nFirst version\n\n## B\n\nSecond section", encoding="utf-8")
            with patch("tools.skill_knowledge.generate_index._embed_batch", return_value=[[0.25] * 768, [0.5] * 768]) as embed:
                first, first_bytes = _build_manifest_and_index(root, "gemini-embedding-2", 768, "test-key")
                second, second_bytes = _build_manifest_and_index(root, "gemini-embedding-2", 768, "test-key")
            self.assertEqual(1, embed.call_count)
            self.assertEqual(first, second)
            self.assertEqual(first_bytes, second_bytes)
            self.assertFalse((assets / "skill_knowledge/manifest.json").exists())
            output = assets / "skill_knowledge"
            output.mkdir()
            manifest_path = output / "manifest.json"
            index_path = output / "index-v1.bin"
            manifest_path.write_text(json.dumps(first), encoding="utf-8")
            index_path.write_bytes(first_bytes)
            _validate(root, manifest_path, index_path)
            first["skills"][0]["documents"][1]["chunks"][0]["vectorOffsetBytes"] = 4
            manifest_path.write_text(json.dumps(first), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "offset"):
                _validate(root, manifest_path, index_path)
            knowledge.write_text("# A\n\nChanged version", encoding="utf-8")
            with patch("tools.skill_knowledge.generate_index._embed_batch", return_value=[[0.5] * 768]) as changed_embed:
                _build_manifest_and_index(root, "gemini-embedding-2", 768, "test-key")
            self.assertEqual(1, changed_embed.call_count)


if __name__ == "__main__":
    unittest.main()
