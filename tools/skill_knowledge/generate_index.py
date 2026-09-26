#!/usr/bin/env python3
"""Generate the packaged Skill Knowledge manifest and Gemini Embedding 2 index."""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import json
import math
import os
import random
import re
import struct
import sys
import time
from collections import deque
import urllib.error
import urllib.request
from pathlib import Path
from typing import Iterable

MODEL = "gemini-embedding-2"
DIMENSION = 768
SCHEMA_VERSION = 1
MAX_CHARS = 900
OVERLAP_CHARS = 200


@dataclasses.dataclass(frozen=True)
class Chunk:
    chunk_id: str
    document_id: str
    heading_path: str
    start_character: int
    end_character: int
    text: str


def normalize_text(text: str) -> str:
    return text.replace("\r\n", "\n").replace("\r", "\n")


def sha256_text(text: str) -> str:
    return hashlib.sha256(normalize_text(text).encode("utf-8")).hexdigest()


def stable_id(*parts: str) -> str:
    raw = "::".join(parts)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def build_document_id(skill_id: str, relative_path: str) -> str:
    return stable_id(skill_id, relative_path.replace("\\", "/"))


def classify_markdown(relative_path: str) -> str:
    path = relative_path.replace("\\", "/").strip("/")
    lower = path.lower()
    if lower == "skill.md":
        return "CORE"
    if Path(lower).name.startswith("readme"):
        return "SUPPORTING"
    if (
        lower.startswith("references/")
        or lower.startswith("research/")
        or lower.startswith("examples/")
    ):
        return "KNOWLEDGE"
    return "SUPPORTING"


def markdown_title(text: str, fallback: str) -> str:
    match = re.search(r"(?m)^#\s+(.+?)\s*$", normalize_text(text))
    if match:
        return match.group(1).strip()
    return Path(fallback).stem.replace("-", " ").replace("_", " ").strip()


def _trim_region(text: str, start: int, end: int) -> tuple[int, int]:
    while start < end and text[start].isspace():
        start += 1
    while end > start and text[end - 1].isspace():
        end -= 1
    return start, end


def _heading_sections(text: str) -> list[tuple[int, int, str]]:
    heading_re = re.compile(r"(?m)^(#{1,6})[ \t]+(.+?)[ \t]*$")
    matches = list(heading_re.finditer(text))
    sections: list[tuple[int, int, str]] = []
    heading_stack: list[str] = []

    if not matches:
        start, end = _trim_region(text, 0, len(text))
        return [(start, end, "")] if start < end else []

    first_start, first_end = _trim_region(text, 0, matches[0].start())
    if first_start < first_end:
        sections.append((first_start, first_end, ""))

    for index, match in enumerate(matches):
        level = len(match.group(1))
        title = match.group(2).strip()
        heading_stack = heading_stack[: level - 1]
        while len(heading_stack) < level - 1:
            heading_stack.append("")
        if len(heading_stack) == level - 1:
            heading_stack.append(title)
        else:
            heading_stack[level - 1] = title
        heading_stack = heading_stack[:level]
        heading_path = " > ".join(part for part in heading_stack if part)

        body_start = match.end()
        if body_start < len(text) and text[body_start] == "\n":
            body_start += 1
        body_end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        body_start, body_end = _trim_region(text, body_start, body_end)
        if body_start < body_end:
            sections.append((body_start, body_end, heading_path))

    return sections


def _split_region(
    text: str,
    start: int,
    end: int,
    max_chars: int,
    overlap_chars: int,
) -> list[tuple[int, int]]:
    regions: list[tuple[int, int]] = []
    cursor = start
    while cursor < end:
        hard_end = min(cursor + max_chars, end)
        candidate_end = hard_end
        if hard_end < end:
            boundary = text.rfind("\n\n", cursor + max_chars // 2, hard_end)
            if boundary > cursor:
                candidate_end = boundary
        chunk_start, chunk_end = _trim_region(text, cursor, candidate_end)
        if chunk_start < chunk_end:
            regions.append((chunk_start, chunk_end))
        if candidate_end >= end:
            break
        next_cursor = max(candidate_end - overlap_chars, cursor + 1)
        if next_cursor <= cursor:
            next_cursor = candidate_end
        cursor = next_cursor
    return regions


def chunk_markdown(
    document_id: str,
    text: str,
    max_chars: int = MAX_CHARS,
    overlap_chars: int = OVERLAP_CHARS,
) -> list[Chunk]:
    if max_chars <= 0:
        raise ValueError("max_chars must be positive")
    if overlap_chars < 0 or overlap_chars >= max_chars:
        raise ValueError("overlap_chars must be >= 0 and < max_chars")

    normalized = normalize_text(text)
    chunks: list[Chunk] = []
    for section_start, section_end, heading_path in _heading_sections(normalized):
        for start, end in _split_region(
            normalized,
            section_start,
            section_end,
            max_chars=max_chars,
            overlap_chars=overlap_chars,
        ):
            chunk_text = normalized[start:end]
            chunk_index = len(chunks)
            chunk_id = stable_id(
                document_id,
                str(chunk_index),
                str(start),
                str(end),
                sha256_text(chunk_text),
            )
            chunks.append(
                Chunk(
                    chunk_id=chunk_id,
                    document_id=document_id,
                    heading_path=heading_path,
                    start_character=start,
                    end_character=end,
                    text=chunk_text,
                )
            )
    return chunks


def format_document_for_embedding(title: str, heading_path: str, chunk_text: str) -> str:
    heading = heading_path.strip() or title.strip()
    return f"title: {title.strip()} | text: {heading}\n{chunk_text.strip()}"


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _load_catalog(repo_root: Path) -> dict:
    return _load_json(
        repo_root / "app/src/main/assets/official_skill_catalog_v1.json"
    )


def _load_execution_manifest(repo_root: Path) -> dict:
    return _load_json(
        repo_root / "app/src/main/assets/official_skill_execution_manifest_v2.json"
    )


def _skill_sources(repo_root: Path) -> list[dict]:
    catalog = _load_catalog(repo_root)
    publication = _load_execution_manifest(repo_root)
    base_skills = catalog.get("skills", [])
    published_skills = publication.get("skills", [])
    base_ids = [skill["id"] for skill in base_skills]
    published_ids = [skill["id"] for skill in published_skills]

    if (
        len(published_ids) != len(base_ids)
        or len(set(published_ids)) != len(published_ids)
        or set(published_ids) != set(base_ids)
    ):
        raise ValueError(
            "Execution manifest does not match official catalog: "
            f"catalog={len(base_ids)}, publication={len(published_ids)}"
        )

    published_by_id = {item["id"]: item for item in published_skills}
    sources: list[dict] = []
    for skill in base_skills:
        published = published_by_id[skill["id"]]
        core_asset_path = str(published.get("assetPath") or "").strip()
        if not core_asset_path:
            raise ValueError(f"Published Skill assetPath missing: {skill['id']}")

        availability = skill.get("availability") or {}
        historical_asset_path = (
            str(skill["assetPath"])
            if availability.get("hasAsset") and skill.get("assetPath")
            else None
        )
        sources.append(
            {
                "skillId": skill["id"],
                "skillName": skill.get("nameZh") or skill["id"],
                "coreAssetPath": core_asset_path,
                "historicalAssetPath": historical_asset_path,
            }
        )
    return sources


def _collect_documents(repo_root: Path) -> list[dict]:
    assets_root = repo_root / "app/src/main/assets"
    skills: list[dict] = []

    for source in _skill_sources(repo_root):
        skill_id = source["skillId"]
        core_asset_path = source["coreAssetPath"]
        core_file = assets_root / core_asset_path
        if not core_file.is_file():
            raise FileNotFoundError(f"Published Skill asset missing: {core_asset_path}")

        official_root = core_file.parent
        documents: list[dict] = []
        seen_relative_paths: set[str] = set()

        def append_document(file_path: Path, relative_path: str) -> None:
            normalized_relative_path = relative_path.replace("\\", "/")
            key = normalized_relative_path.lower()
            if key in seen_relative_paths:
                return
            content = normalize_text(file_path.read_text(encoding="utf-8"))
            document_type = classify_markdown(normalized_relative_path)
            document_id = build_document_id(skill_id, normalized_relative_path)
            chunks = (
                chunk_markdown(document_id, content)
                if document_type == "KNOWLEDGE"
                else []
            )
            documents.append(
                {
                    "documentId": document_id,
                    "skillId": skill_id,
                    "assetPath": file_path.relative_to(assets_root).as_posix(),
                    "relativePath": normalized_relative_path,
                    "title": markdown_title(content, normalized_relative_path),
                    "type": document_type,
                    "contentHash": sha256_text(content),
                    "retrievalEligible": document_type == "KNOWLEDGE",
                    "_content": content,
                    "_chunks": chunks,
                }
            )
            seen_relative_paths.add(key)

        append_document(core_file, "SKILL.md")

        for file_path in sorted(
            official_root.rglob("*.md"),
            key=lambda p: p.as_posix().lower(),
        ):
            relative_path = file_path.relative_to(official_root).as_posix()
            if relative_path.lower() != "skill.md":
                append_document(file_path, relative_path)

        historical_asset_path = source["historicalAssetPath"]
        if historical_asset_path:
            historical_root = (assets_root / historical_asset_path).parent
            if historical_root != official_root:
                if not historical_root.is_dir():
                    raise FileNotFoundError(
                        "Historical Skill knowledge root missing: "
                        f"{historical_root.relative_to(assets_root).as_posix()}"
                    )
                for file_path in sorted(
                    historical_root.rglob("*.md"),
                    key=lambda p: p.as_posix().lower(),
                ):
                    relative_path = file_path.relative_to(historical_root).as_posix()
                    if relative_path.lower() != "skill.md":
                        append_document(file_path, relative_path)

        skills.append(
            {
                "skillId": skill_id,
                "skillName": source["skillName"],
                "assetRoot": official_root.relative_to(assets_root).as_posix(),
                "documents": documents,
            }
        )
    return skills


def embedding_failure_context(
    request_index: int,
    request_total: int,
    skill_id: str,
    asset_path: str,
    chunk_id: str,
    embedding_text: str,
) -> str:
    payload = embedding_text.encode("utf-8")
    return (
        f"request={request_index}/{request_total}, "
        f"skillId={skill_id}, "
        f"assetPath={asset_path}, "
        f"chunkId={chunk_id}, "
        f"chars={len(embedding_text)}, "
        f"utf8Bytes={len(payload)}, "
        f"inputSha256={hashlib.sha256(payload).hexdigest()}"
    )


def safe_gemini_error_detail(raw_body: bytes, api_key: str) -> str:
    try:
        payload = json.loads(raw_body.decode("utf-8", errors="replace"))
        error = payload.get("error") or {}
        status = str(error.get("status") or "").strip()
        message = str(error.get("message") or "").strip()
    except (json.JSONDecodeError, UnicodeDecodeError, AttributeError, TypeError):
        status = ""
        message = ""

    detail = ": ".join(part for part in (status, message) if part)
    if not detail:
        detail = "No structured Gemini error detail"

    if api_key:
        detail = detail.replace(api_key, "<redacted>")
    detail = re.sub(r"AIza[0-9A-Za-z_-]{16,}", "<redacted>", detail)
    detail = re.sub(r"\s+", " ", detail).strip()
    return detail[:500]


def _parse_batch_embeddings(raw_body: bytes, expected_count: int, dimension: int) -> list[list[float]]:
    embeddings = json.loads(raw_body)["embeddings"]
    if len(embeddings) != expected_count:
        raise ValueError(f"Embedding count mismatch: expected {expected_count}, got {len(embeddings)}")
    vectors = []
    for embedding in embeddings:
        values = embedding["values"]
        if len(values) != dimension:
            raise ValueError(f"Embedding dimension mismatch: expected {dimension}, got {len(values)}")
        vector = [float(value) for value in values]
        if not all(math.isfinite(value) for value in vector):
            raise ValueError("Embedding contains a non-finite value")
        vectors.append(vector)
    return vectors


class EmbeddingCache:
    """Only successful vectors are checkpointed; input text and API keys are never stored."""

    def __init__(self, root: Path):
        self.root = root

    def _path(self, model: str, dimension: int, text: str) -> Path:
        identity = f"{model}\n{dimension}\n{sha256_text(text)}"
        return self.root / (hashlib.sha256(identity.encode("utf-8")).hexdigest() + ".bin")

    def get(self, model: str, dimension: int, text: str) -> list[float] | None:
        path = self._path(model, dimension, text)
        if not path.is_file():
            return None
        raw = path.read_bytes()
        if len(raw) != dimension * 4:
            raise ValueError(f"Corrupt embedding checkpoint: {path.name}")
        values = list(struct.unpack(f"<{dimension}f", raw))
        if not all(math.isfinite(value) for value in values):
            raise ValueError(f"Invalid embedding checkpoint: {path.name}")
        return values

    def put(self, model: str, dimension: int, text: str, vector: list[float]) -> None:
        if len(vector) != dimension or not all(math.isfinite(value) for value in vector):
            raise ValueError("Cannot checkpoint invalid embedding")
        path = self._path(model, dimension, text)
        path.parent.mkdir(parents=True, exist_ok=True)
        pending = path.with_suffix(".tmp")
        pending.write_bytes(struct.pack(f"<{dimension}f", *vector))
        pending.replace(path)


class SlidingWindowRateLimiter:
    def __init__(
        self,
        max_requests_per_minute: int,
        max_input_tokens_per_minute: int,
        clock=time.monotonic,
        sleep=time.sleep,
    ):
        if max_requests_per_minute < 1 or max_input_tokens_per_minute < 1:
            raise ValueError("Rate limits must be positive")
        self.max_requests = max_requests_per_minute
        self.max_tokens = max_input_tokens_per_minute
        self.clock = clock
        self.sleep = sleep
        self.events: deque[tuple[float, int]] = deque()

    def acquire(self, estimated_tokens: int) -> None:
        if estimated_tokens > self.max_tokens:
            raise ValueError("A batch exceeds the configured input TPM budget")
        while True:
            now = self.clock()
            while self.events and now - self.events[0][0] >= 60:
                self.events.popleft()
            used_tokens = sum(tokens for _, tokens in self.events)
            if len(self.events) < self.max_requests and used_tokens + estimated_tokens <= self.max_tokens:
                self.events.append((now, estimated_tokens))
                return
            self.sleep(max(0.01, 60 - (now - self.events[0][0])))


def _estimated_input_tokens(text: str) -> int:
    # Chinese characters can approach one token each; use a conservative byte estimate.
    return max(1, math.ceil(len(text.encode("utf-8")) / 3))


def _embed_batch(
    api_key: str,
    texts: list[str],
    model: str,
    dimension: int,
    limiter: SlidingWindowRateLimiter | None = None,
) -> list[list[float]]:
    if not texts:
        raise ValueError("Embedding batch must not be empty")
    payload = json.dumps(
        {"requests": [
            {
                "model": f"models/{model}",
                "content": {"parts": [{"text": value}]},
                "output_dimensionality": dimension,
            }
            for value in texts
        ]},
        ensure_ascii=False,
    ).encode("utf-8")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:batchEmbedContents"
    for attempt in range(12):
        if limiter is not None:
            limiter.acquire(sum(_estimated_input_tokens(value) for value in texts))
        request = urllib.request.Request(
            url=url,
            data=payload,
            method="POST",
            headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        )
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                return _parse_batch_embeddings(response.read(), len(texts), dimension)
        except urllib.error.HTTPError as error:
            raw = error.read()
            retryable = error.code in {429, 500, 502, 503, 504} or (error.code == 400 and not raw.strip())
            last_attempt = 11 if error.code == 429 else 3
            if not retryable or attempt >= last_attempt:
                raise RuntimeError(
                    f"Gemini batch embedding failed with HTTP {error.code}: "
                    f"{safe_gemini_error_detail(raw, api_key)}"
                ) from error
            retry_after = error.headers.get("Retry-After") if error.headers else None
            delay = float(retry_after) if retry_after and retry_after.isdecimal() else 0.0
            if error.code == 429:
                match = re.search(rb"Please retry in\s+([0-9]+(?:\.[0-9]+)?)s", raw)
                if match:
                    delay = max(delay, min(float(match.group(1)), 300.0))
                else:
                    delay = max(delay, 30.0)
        except (urllib.error.URLError, OSError) as error:
            if attempt >= 3:
                raise RuntimeError(
                    "Gemini batch embedding failed after transient network retries: "
                    f"{type(error).__name__}"
                ) from error
            delay = 0.0
        time.sleep(max(delay, 2 ** attempt + random.uniform(0, 0.5)))
    raise AssertionError("Unreachable embedding retry state")


def _embed_text(api_key: str, text: str, model: str, dimension: int) -> list[float]:
    payload = json.dumps(
        {
            "content": {"parts": [{"text": text}]},
            "output_dimensionality": dimension,
        },
        ensure_ascii=False,
    ).encode("utf-8")
    url = (
        "https://generativelanguage.googleapis.com/v1beta/models/"
        f"{model}:embedContent"
    )
    request = urllib.request.Request(
        url=url,
        data=payload,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "x-goog-api-key": api_key,
        },
    )

    last_error: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                raw = response.read().decode("utf-8")
            values = json.loads(raw)["embedding"]["values"]
            if len(values) != dimension:
                raise ValueError(
                    f"Embedding dimension mismatch: expected {dimension}, got {len(values)}"
                )
            return [float(value) for value in values]
        except urllib.error.HTTPError as error:
            last_error = error
            raw_error = error.read()
            detail = safe_gemini_error_detail(raw_error, api_key)
            if error.code not in {429, 500, 502, 503, 504} or attempt == 3:
                raise RuntimeError(
                    f"Gemini embedding request failed with HTTP {error.code}: {detail}"
                ) from error
        except (urllib.error.URLError, OSError) as error:
            last_error = error
            if attempt == 3:
                raise RuntimeError(
                    "Gemini embedding request failed after transient network retries: "
                    f"{type(error).__name__}"
                ) from error
        time.sleep(2 ** attempt)

    raise RuntimeError("Gemini embedding request failed") from last_error


def _build_manifest_and_index(
    repo_root: Path,
    model: str,
    dimension: int,
    api_key: str,
    cache_root: Path | None = None,
    batch_size: int = 8,
    max_rpm: int = 24,
    max_tpm: int = 800,
) -> tuple[dict, bytes]:
    skills = _collect_documents(repo_root)
    if batch_size < 1:
        raise ValueError("Batch size must be positive")
    cache = EmbeddingCache(cache_root or repo_root / "build/tmp/skill_knowledge/embeddings")
    limiter = SlidingWindowRateLimiter(max_rpm, max_tpm)
    work: list[tuple[dict, dict, Chunk, str]] = []
    vectors_by_chunk: dict[str, list[float]] = {}
    for skill in skills:
        for document in skill["documents"]:
            for chunk in document["_chunks"]:
                embedding_text = format_document_for_embedding(
                    document["title"], chunk.heading_path, chunk.text
                )
                cached = cache.get(model, dimension, embedding_text)
                if cached is None:
                    work.append((skill, document, chunk, embedding_text))
                else:
                    vectors_by_chunk[chunk.chunk_id] = cached

    print(f"Embedding cache: {len(vectors_by_chunk)} hits, {len(work)} pending", flush=True)
    cursor = 0
    while cursor < len(work):
        batch: list[tuple[dict, dict, Chunk, str]] = []
        estimated_tokens = 0
        while cursor < len(work) and len(batch) < batch_size:
            item = work[cursor]
            item_tokens = _estimated_input_tokens(item[3])
            if item_tokens > max_tpm:
                raise ValueError(
                    f"Embedding input exceeds configured TPM budget at "
                    f"{item[0]['skillId']}/{item[2].chunk_id}: {item_tokens}"
                )
            if batch and estimated_tokens + item_tokens > max_tpm:
                break
            batch.append(item)
            estimated_tokens += item_tokens
            cursor += 1
        try:
            vectors = _embed_batch(
                api_key, [item[3] for item in batch], model, dimension, limiter
            )
        except Exception as error:
            first = batch[0]
            context = embedding_failure_context(
                request_index=cursor - len(batch) + 1,
                request_total=len(work),
                skill_id=first[0]["skillId"],
                asset_path=first[1]["assetPath"],
                chunk_id=first[2].chunk_id,
                embedding_text=first[3],
            )
            raise RuntimeError(f"Gemini embedding failed at {context}: {error}") from error
        for item, vector in zip(batch, vectors, strict=True):
            cache.put(model, dimension, item[3], vector)
            vectors_by_chunk[item[2].chunk_id] = vector
        if cursor % 50 < len(batch) or cursor == len(work):
            print(f"Embedding progress: {cursor}/{len(work)} pending chunks complete", flush=True)

    index_bytes = bytearray()
    public_skills: list[dict] = []
    request_total = sum(
        len(document["_chunks"])
        for skill in skills
        for document in skill["documents"]
    )
    request_index = 0

    for skill in skills:
        public_documents: list[dict] = []
        for document in skill["documents"]:
            public_chunks: list[dict] = []
            for chunk in document["_chunks"]:
                embedding_text = format_document_for_embedding(
                    title=document["title"],
                    heading_path=chunk.heading_path,
                    chunk_text=chunk.text,
                )
                request_index += 1
                values = vectors_by_chunk[chunk.chunk_id]
                vector_offset = len(index_bytes)
                index_bytes.extend(struct.pack(f"<{dimension}f", *values))
                public_chunks.append(
                    {
                        "chunkId": chunk.chunk_id,
                        "documentId": chunk.document_id,
                        "headingPath": chunk.heading_path,
                        "startCharacter": chunk.start_character,
                        "endCharacter": chunk.end_character,
                        "vectorOffsetBytes": vector_offset,
                        "vectorLength": dimension,
                        "embeddingTextHash": sha256_text(embedding_text),
                    }
                )

            public_documents.append(
                {
                    key: value
                    for key, value in document.items()
                    if not key.startswith("_")
                }
                | {"chunks": public_chunks}
            )

        public_skills.append(
            {
                "skillId": skill["skillId"],
                "skillName": skill["skillName"],
                "assetRoot": skill["assetRoot"],
                "documents": public_documents,
            }
        )

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "model": model,
        "vectorDimension": dimension,
        "vectorEncoding": "float32-le",
        "skills": public_skills,
    }
    return manifest, bytes(index_bytes)


def _validate(repo_root: Path, manifest_path: Path, index_path: Path) -> None:
    if not manifest_path.is_file() or not index_path.is_file():
        raise FileNotFoundError("Generated Skill Knowledge assets are missing")

    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        raise ValueError("Unsupported manifest schemaVersion")
    if manifest.get("model") != MODEL:
        raise ValueError("Unexpected embedding model")
    dimension = int(manifest.get("vectorDimension", 0))
    if dimension != DIMENSION:
        raise ValueError("Unexpected embedding dimension")

    if manifest.get("vectorEncoding") != "float32-le":
        raise ValueError("Unexpected vector encoding")
    expected_skills = _collect_documents(repo_root)
    actual_skills = manifest.get("skills", [])
    if len(actual_skills) != len(expected_skills):
        raise ValueError("Manifest Skill count mismatch")
    index_size = index_path.stat().st_size
    expected_vector_bytes = dimension * 4
    next_offset = 0
    document_ids: set[str] = set()
    chunk_ids: set[str] = set()
    for skill, expected_skill in zip(actual_skills, expected_skills, strict=True):
        if skill["skillId"] != expected_skill["skillId"]:
            raise ValueError("Manifest Skill ownership mismatch")
        expected_documents = expected_skill["documents"]
        documents = skill.get("documents", [])
        if len(documents) != len(expected_documents):
            raise ValueError(f"Manifest document count mismatch: {skill['skillId']}")
        for document, expected_document in zip(documents, expected_documents, strict=True):
            for field in (
                "documentId", "skillId", "assetPath", "relativePath", "title",
                "type", "contentHash", "retrievalEligible",
            ):
                if document.get(field) != expected_document[field]:
                    raise ValueError(f"Manifest document {field} mismatch: {skill['skillId']}")
            document_id = document["documentId"]
            if document_id in document_ids:
                raise ValueError("Duplicate documentId")
            document_ids.add(document_id)
            expected_chunks = expected_document["_chunks"]
            chunks = document.get("chunks", [])
            if len(chunks) != len(expected_chunks):
                raise ValueError(f"Manifest chunk count mismatch: {document_id}")
            for chunk, expected_chunk in zip(chunks, expected_chunks, strict=True):
                if chunk["chunkId"] in chunk_ids:
                    raise ValueError("Duplicate chunkId")
                chunk_ids.add(chunk["chunkId"])
                for field, expected_value in (
                    ("chunkId", expected_chunk.chunk_id),
                    ("documentId", document_id),
                    ("headingPath", expected_chunk.heading_path),
                    ("startCharacter", expected_chunk.start_character),
                    ("endCharacter", expected_chunk.end_character),
                    ("embeddingTextHash", sha256_text(format_document_for_embedding(
                        document["title"], expected_chunk.heading_path, expected_chunk.text,
                    ))),
                ):
                    if chunk.get(field) != expected_value:
                        raise ValueError(f"Manifest chunk {field} mismatch: {document_id}")
                if chunk["vectorLength"] != dimension:
                    raise ValueError("Chunk vector length mismatch")
                if chunk["vectorOffsetBytes"] != next_offset:
                    raise ValueError("Chunk vector offset is not contiguous")
                next_offset += expected_vector_bytes
    if next_offset != index_size:
        raise ValueError(
            f"Index size mismatch: manifest uses {next_offset} bytes, file has {index_size}"
        )


def _write_generated_assets(repo_root: Path, manifest: dict, index_bytes: bytes) -> None:
    output_dir = repo_root / "app/src/main/assets/skill_knowledge"
    output_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = output_dir / "manifest.json"
    index_path = output_dir / "index-v1.bin"
    pending_manifest = manifest_path.with_suffix(".json.tmp")
    pending_index = index_path.with_suffix(".bin.tmp")
    pending_manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    pending_index.write_bytes(index_bytes)
    _validate(repo_root, pending_manifest, pending_index)
    pending_index.replace(index_path)
    pending_manifest.replace(manifest_path)


def parse_args(argv: Iterable[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--dimension", type=int, default=DIMENSION)
    parser.add_argument("--validate-only", action="store_true")
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-rpm", type=int, default=24)
    parser.add_argument("--max-tpm", type=int, default=800)
    return parser.parse_args(list(argv))


def main(argv: Iterable[str] = ()) -> int:
    args = parse_args(argv or sys.argv[1:])
    repo_root = Path(args.repo_root).resolve()
    output_dir = repo_root / "app/src/main/assets/skill_knowledge"
    manifest_path = output_dir / "manifest.json"
    index_path = output_dir / "index-v1.bin"

    if args.validate_only:
        _validate(repo_root, manifest_path, index_path)
        print("Skill Knowledge assets: VALID")
        return 0

    if args.model != MODEL or args.dimension != DIMENSION:
        raise ValueError(
            f"This repository contract requires {MODEL} with {DIMENSION} dimensions"
        )
    api_key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("GEMINI_API_KEY is required to generate embeddings")

    manifest, index_bytes = _build_manifest_and_index(
        repo_root=repo_root,
        model=args.model,
        dimension=args.dimension,
        api_key=api_key,
        batch_size=args.batch_size,
        max_rpm=args.max_rpm,
        max_tpm=args.max_tpm,
    )
    _write_generated_assets(repo_root, manifest, index_bytes)
    _validate(repo_root, manifest_path, index_path)
    print(
        "Generated Skill Knowledge assets: "
        f"{sum(len(s['documents']) for s in manifest['skills'])} documents, "
        f"{sum(len(d['chunks']) for s in manifest['skills'] for d in s['documents'])} chunks"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
