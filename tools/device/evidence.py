from __future__ import annotations

import hashlib
import json
import os
import tempfile
from pathlib import Path
from typing import Any

from .models import DeviceControlError


def resolve_output_directory(
    output: str | Path | None,
    repository_root: str | Path,
    *,
    prefix: str = "jianyu-device-control-",
) -> Path:
    repo = Path(repository_root).expanduser().resolve()
    if output is None:
        target = Path(tempfile.mkdtemp(prefix=prefix)).resolve()
    else:
        target = Path(output).expanduser().resolve()
        _assert_external(target, repo)
        target.mkdir(parents=True, exist_ok=True)
    _assert_external(target, repo)
    return target


def write_json(path: str | Path, payload: dict[str, Any]) -> Path:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True),
        encoding="utf-8",
    )
    return target.resolve()


def sha256_file(path: str | Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _assert_external(target: Path, repository_root: Path) -> None:
    target_norm = os.path.normcase(str(target))
    repo_norm = os.path.normcase(str(repository_root))
    try:
        common = os.path.commonpath([target_norm, repo_norm])
    except ValueError:
        return
    if common == repo_norm:
        raise DeviceControlError(
            f"设备控制证据目录必须位于仓库外：{target}",
            category="OUTPUT_INSIDE_REPOSITORY",
            exit_code=70,
        )
