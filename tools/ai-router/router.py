#!/usr/bin/env python3
"""见域本地 AI Router。

这是一个只读 Drive watcher 与事件路由器：它不会修改 Drive 控制文件、生产代码或 Git
状态。外发给 ChatGPT 的消息只由控制文件的 ``to`` 字段和本地固定 URL 决定。
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping, Protocol
from urllib.parse import quote, urlsplit, urlunsplit


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
WORK_DIRECTORY = REPOSITORY_ROOT / "work" / "ai-router"
DEFAULT_CONFIG_PATH = WORK_DIRECTORY / "config.json"
CONFIG_EXAMPLE_PATH = Path(__file__).with_name("config.example.json")

GOOGLE_DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
GOOGLE_DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
ALLOWED_ROLES = frozenset({"PLANNER", "ENGINEER", "LOCAL_AI"})
ALLOWED_EVENT_STATUSES = frozenset({"PENDING", "CLAIMED", "SENT", "ACKED", "BLOCKED"})
EVENT_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
EVENT_TYPE_PATTERN = re.compile(r"^[A-Z][A-Z0-9_:-]{0,63}$")
LOCAL_REQUEST_MARKER = re.compile(r"<!--\s*AI_ROUTER_EVENT_ID:\s*([^\s]+)\s*-->")
MAX_EVENT_RECORDS = 200


class RouterError(Exception):
    """预期内的配置、协议或路由失败。"""

    def __init__(self, code: str, detail: str = "") -> None:
        self.code = code
        self.detail = detail
        super().__init__(f"{code}: {detail}" if detail else code)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def atomic_write_text(path: Path, content: str) -> None:
    """先写临时文件再替换，避免 watcher 中断时留下半截状态。"""

    path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    try:
        temporary_path.write_text(content, encoding="utf-8", newline="\n")
        os.replace(temporary_path, path)
    finally:
        if temporary_path.exists():
            temporary_path.unlink()


def atomic_write_json(path: Path, payload: Mapping[str, Any]) -> None:
    atomic_write_text(path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def require_text(raw: Mapping[str, Any], key: str) -> str:
    value = raw.get(key)
    if not isinstance(value, str) or not value.strip():
        raise RouterError("CONTROL_EVENT_INVALID", f"字段 {key} 必须是非空字符串")
    return value


def parse_rfc3339(value: str) -> None:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise RouterError("CONTROL_EVENT_INVALID", "created_at 必须是 ISO-8601 时间") from error
    if parsed.tzinfo is None:
        raise RouterError("CONTROL_EVENT_INVALID", "created_at 必须包含时区")


def validate_drive_url(value: str) -> None:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or parsed.hostname not in {"drive.google.com", "docs.google.com"}:
        raise RouterError("CONTROL_EVENT_INVALID", "drive_url 必须是 https Google Drive 链接")


def validate_fixed_conversation_url(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "https" or parsed.hostname != "chatgpt.com":
        raise RouterError("CHATGPT_CONVERSATION_URL_INVALID", "固定对话 URL 必须位于 https://chatgpt.com")
    normalized_path = parsed.path.rstrip("/")
    if not (normalized_path.startswith("/c/") or normalized_path.startswith("/g/")):
        raise RouterError("CHATGPT_CONVERSATION_URL_INVALID", "固定 URL 必须指向具体 ChatGPT 对话")
    return value


def validate_cdp_endpoint(value: str) -> str:
    parsed = urlsplit(value)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
        raise RouterError("BROWSER_CDP_ENDPOINT_INVALID", "CDP endpoint 只能是本机 loopback HTTP 地址")
    if parsed.port is None:
        raise RouterError("BROWSER_CDP_ENDPOINT_INVALID", "CDP endpoint 必须包含端口")
    return value.rstrip("/")


def canonical_conversation_url(value: str) -> str:
    parsed = urlsplit(value)
    return urlunsplit((parsed.scheme, parsed.netloc, parsed.path.rstrip("/"), "", ""))


@dataclass(frozen=True)
class ControlEvent:
    protocol_version: int
    event_id: str
    status: str
    source: str
    destination: str
    event_type: str
    message: str
    drive_url: str
    conversation_url: str | None
    created_at: str
    payload_sha256: str


@dataclass(frozen=True)
class DriveMetadata:
    file_id: str
    modified_time: str


@dataclass(frozen=True)
class RouteOutcome:
    state: str
    result: str
    detail: str = ""


@dataclass(frozen=True)
class RouterResult:
    code: str
    event: ControlEvent | None = None
    state: str | None = None
    detail: str = ""


def parse_control_event(raw: Any) -> ControlEvent:
    if not isinstance(raw, Mapping):
        raise RouterError("CONTROL_EVENT_INVALID", "控制文件根节点必须是 JSON object")

    protocol_version = raw.get("protocol_version")
    if isinstance(protocol_version, bool) or protocol_version != 1:
        raise RouterError("CONTROL_EVENT_INVALID", "仅支持 protocol_version=1")

    event_id = require_text(raw, "event_id")
    if not EVENT_ID_PATTERN.fullmatch(event_id):
        raise RouterError("CONTROL_EVENT_INVALID", "event_id 格式不合法")

    status = require_text(raw, "status")
    if status not in ALLOWED_EVENT_STATUSES:
        raise RouterError("CONTROL_EVENT_INVALID", "status 不在允许集合中")

    source = require_text(raw, "from")
    destination = require_text(raw, "to")
    if source not in ALLOWED_ROLES or destination not in ALLOWED_ROLES:
        raise RouterError("CONTROL_EVENT_INVALID", "from/to 必须是 PLANNER、ENGINEER 或 LOCAL_AI")

    event_type = require_text(raw, "type")
    if not EVENT_TYPE_PATTERN.fullmatch(event_type):
        raise RouterError("CONTROL_EVENT_INVALID", "type 格式不合法")

    message = require_text(raw, "message")
    if len(message) > 12_000:
        raise RouterError("CONTROL_EVENT_INVALID", "message 超过 12000 个字符")

    drive_url = require_text(raw, "drive_url")
    validate_drive_url(drive_url)

    conversation_url_raw = raw.get("conversation_url")
    if conversation_url_raw is not None and not isinstance(conversation_url_raw, str):
        raise RouterError("CONTROL_EVENT_INVALID", "conversation_url 必须是字符串或省略")

    created_at = require_text(raw, "created_at")
    parse_rfc3339(created_at)

    # 即使 conversation_url 不用于发送，也纳入摘要，防止同一 event_id 被静默改写。
    fingerprint_payload = {
        "protocol_version": protocol_version,
        "event_id": event_id,
        "status": status,
        "from": source,
        "to": destination,
        "type": event_type,
        "message": message,
        "drive_url": drive_url,
        "conversation_url": conversation_url_raw,
        "created_at": created_at,
    }
    fingerprint = sha256_text(json.dumps(fingerprint_payload, ensure_ascii=False, sort_keys=True))
    return ControlEvent(
        protocol_version=protocol_version,
        event_id=event_id,
        status=status,
        source=source,
        destination=destination,
        event_type=event_type,
        message=message,
        drive_url=drive_url,
        conversation_url=conversation_url_raw,
        created_at=created_at,
        payload_sha256=fingerprint,
    )


DEFAULT_SETTINGS: dict[str, Any] = {
    "GOOGLE_DRIVE_FILE_ID": "",
    "GOOGLE_CREDENTIALS_PATH": "",
    "GOOGLE_OAUTH_CLIENT_SECRETS_PATH": "",
    "PLANNER_CONVERSATION_URL": "",
    "ENGINEER_CONVERSATION_URL": "",
    "POLL_INTERVAL_SECONDS": 15,
    "CHATGPT_PROJECT_URL": "",
    "ENABLE_BROWSER_SEND": False,
    "BROWSER_CDP_ENDPOINT": "http://127.0.0.1:9222",
    "REQUEST_TIMEOUT_SECONDS": 20,
    "MAX_CONTROL_FILE_BYTES": 131_072,
    "CHATGPT_SEND_CONFIRM_TIMEOUT_SECONDS": 20,
}
SUPPORTED_SETTINGS = frozenset(DEFAULT_SETTINGS)


class Settings:
    """配置文件值可被同名环境变量覆盖，凭据值永远不写入日志。"""

    def __init__(self, values: Mapping[str, Any], config_path: Path) -> None:
        self.values = dict(values)
        self.config_path = config_path

    @classmethod
    def load(cls, config_path: Path) -> "Settings":
        if not config_path.exists():
            raise RouterError("CONFIG_NOT_FOUND", f"未找到配置文件：{config_path}")
        try:
            raw = json.loads(config_path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise RouterError("CONFIG_INVALID", "config.json 不是有效 JSON") from error
        if not isinstance(raw, Mapping):
            raise RouterError("CONFIG_INVALID", "config.json 根节点必须是 object")
        return cls.from_mapping(raw, config_path)

    @classmethod
    def from_mapping(cls, raw: Mapping[str, Any], config_path: Path) -> "Settings":
        unknown_keys = set(raw) - SUPPORTED_SETTINGS
        if unknown_keys:
            raise RouterError("CONFIG_INVALID", f"不支持的配置项：{', '.join(sorted(unknown_keys))}")
        values = copy.deepcopy(DEFAULT_SETTINGS)
        values.update(raw)
        for key in SUPPORTED_SETTINGS:
            if key in os.environ:
                values[key] = os.environ[key]
        return cls(values, config_path)

    def optional_text(self, key: str) -> str | None:
        value = self.values.get(key, "")
        if value is None:
            return None
        if not isinstance(value, str):
            raise RouterError("CONFIG_INVALID", f"{key} 必须是字符串")
        value = value.strip()
        return value or None

    def required_text(self, key: str) -> str:
        value = self.optional_text(key)
        if not value:
            raise RouterError("CONFIG_REQUIRED", f"请配置 {key}")
        return value

    def bool_value(self, key: str) -> bool:
        value = self.values.get(key)
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            normalized = value.strip().lower()
            if normalized in {"true", "1", "yes"}:
                return True
            if normalized in {"false", "0", "no"}:
                return False
        raise RouterError("CONFIG_INVALID", f"{key} 必须是 boolean")

    def int_value(self, key: str, minimum: int, maximum: int) -> int:
        value = self.values.get(key)
        if isinstance(value, bool):
            raise RouterError("CONFIG_INVALID", f"{key} 必须是整数")
        try:
            parsed = int(value)
        except (TypeError, ValueError) as error:
            raise RouterError("CONFIG_INVALID", f"{key} 必须是整数") from error
        if not minimum <= parsed <= maximum:
            raise RouterError("CONFIG_INVALID", f"{key} 必须在 {minimum} 到 {maximum} 之间")
        return parsed

    def path_value(self, key: str) -> Path:
        raw_path = Path(self.required_text(key))
        if raw_path.is_absolute():
            return raw_path
        return (self.config_path.parent / raw_path).resolve()


class HttpSession(Protocol):
    def get(self, url: str, **kwargs: Any) -> Any: ...


class DriveClient:
    def __init__(self, file_id: str, session: HttpSession, timeout_seconds: int, max_file_bytes: int) -> None:
        self.file_id = file_id
        self.session = session
        self.timeout_seconds = timeout_seconds
        self.max_file_bytes = max_file_bytes

    @property
    def file_url(self) -> str:
        return f"{GOOGLE_DRIVE_FILES_URL}/{quote(self.file_id, safe='')}"

    def _request(self, params: Mapping[str, str], *, stream: bool = False) -> Any:
        try:
            response = self.session.get(self.file_url, params=dict(params), timeout=self.timeout_seconds, stream=stream)
            response.raise_for_status()
            return response
        except RouterError:
            raise
        except Exception as error:  # requests 与 google-auth 都可能抛出不同的网络异常类型。
            response = getattr(error, "response", None)
            if response is not None and getattr(response, "status_code", None):
                raise RouterError("DRIVE_HTTP_ERROR", f"HTTP {response.status_code}") from error
            raise RouterError("DRIVE_NETWORK_ERROR", type(error).__name__) from error

    def get_metadata(self) -> DriveMetadata:
        # 轮询只读取两个字段，不下载控制文件内容。
        response = self._request(
            {"fields": "id,modifiedTime", "supportsAllDrives": "true"},
            stream=False,
        )
        try:
            payload = response.json()
            file_id = payload["id"]
            modified_time = payload["modifiedTime"]
        except (KeyError, TypeError, ValueError) as error:
            raise RouterError("DRIVE_METADATA_INVALID", "files.get 未返回 id/modifiedTime") from error
        if not isinstance(file_id, str) or not isinstance(modified_time, str):
            raise RouterError("DRIVE_METADATA_INVALID", "files.get 字段类型不合法")
        return DriveMetadata(file_id=file_id, modified_time=modified_time)

    def download_control_file(self) -> Mapping[str, Any]:
        response = self._request({"alt": "media", "supportsAllDrives": "true"}, stream=True)
        raw_length = response.headers.get("Content-Length")
        if raw_length:
            try:
                if int(raw_length) > self.max_file_bytes:
                    raise RouterError("CONTROL_FILE_TOO_LARGE", "控制文件超过配置的字节上限")
            except ValueError as error:
                raise RouterError("CONTROL_FILE_INVALID", "控制文件 Content-Length 不合法") from error

        chunks: list[bytes] = []
        received = 0
        try:
            for chunk in response.iter_content(chunk_size=8192):
                if not chunk:
                    continue
                received += len(chunk)
                if received > self.max_file_bytes:
                    raise RouterError("CONTROL_FILE_TOO_LARGE", "控制文件超过配置的字节上限")
                chunks.append(chunk)
            payload = json.loads(b"".join(chunks).decode("utf-8"))
        except UnicodeDecodeError as error:
            raise RouterError("CONTROL_FILE_INVALID", "控制文件不是 UTF-8 JSON") from error
        except json.JSONDecodeError as error:
            raise RouterError("CONTROL_FILE_INVALID", "控制文件不是有效 JSON") from error
        if not isinstance(payload, Mapping):
            raise RouterError("CONTROL_FILE_INVALID", "控制文件根节点必须是 object")
        return payload


def parse_node_expiry(raw: Any) -> datetime | None:
    """兼容 server-gdrive 保存的毫秒时间戳和标准 OAuth ISO 时间。

    google-auth 2.x 的 ``Credentials`` 内部用 UTC 无时区 datetime 比较 expiry，
    因而这里不能传入 aware datetime。
    """

    if raw is None:
        return None
    if isinstance(raw, (int, float)) and not isinstance(raw, bool):
        seconds = raw / 1000 if raw > 10_000_000_000 else raw
        return datetime.fromtimestamp(seconds, tz=timezone.utc).replace(tzinfo=None)
    if isinstance(raw, str):
        try:
            parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
        except ValueError:
            return None
        return parsed.astimezone(timezone.utc).replace(tzinfo=None) if parsed.tzinfo else parsed
    return None


def load_oauth_client_secrets(path: Path) -> tuple[str, str]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RouterError("OAUTH_CLIENT_SECRETS_INVALID", "无法读取 OAuth client secrets 文件") from error
    if not isinstance(raw, Mapping):
        raise RouterError("OAUTH_CLIENT_SECRETS_INVALID", "OAuth client secrets 不是 JSON object")
    client = raw.get("installed") or raw.get("web")
    if not isinstance(client, Mapping):
        raise RouterError("OAUTH_CLIENT_SECRETS_INVALID", "缺少 installed/web OAuth client 配置")
    client_id = client.get("client_id")
    client_secret = client.get("client_secret")
    if not isinstance(client_id, str) or not isinstance(client_secret, str):
        raise RouterError("OAUTH_CLIENT_SECRETS_INVALID", "OAuth client 缺少 client_id/client_secret")
    return client_id, client_secret


def build_drive_client(settings: Settings) -> DriveClient:
    """建立只读 OAuth session；不向 Drive 写入任何字节或元数据。"""

    credential_path = settings.path_value("GOOGLE_CREDENTIALS_PATH")
    if not credential_path.exists():
        raise RouterError("GOOGLE_CREDENTIALS_NOT_FOUND", "GOOGLE_CREDENTIALS_PATH 指向的文件不存在")
    try:
        credential_data = json.loads(credential_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RouterError("GOOGLE_CREDENTIALS_INVALID", "OAuth 凭据文件不是有效 JSON") from error
    if not isinstance(credential_data, Mapping):
        raise RouterError("GOOGLE_CREDENTIALS_INVALID", "OAuth 凭据根节点必须是 object")

    try:
        from google.oauth2.credentials import Credentials
        from google.auth.transport.requests import AuthorizedSession
    except ImportError as error:
        raise RouterError("GOOGLE_AUTH_DEPENDENCY_MISSING", "需要安装 google-auth 与 requests") from error

    try:
        if {"client_id", "client_secret", "refresh_token"}.issubset(credential_data):
            credentials = Credentials.from_authorized_user_info(
                dict(credential_data),
                scopes=[GOOGLE_DRIVE_SCOPE],
            )
        elif "access_token" in credential_data:
            client_secret_path = settings.optional_text("GOOGLE_OAUTH_CLIENT_SECRETS_PATH")
            if not client_secret_path:
                raise RouterError(
                    "GOOGLE_OAUTH_CLIENT_SECRETS_PATH_REQUIRED",
                    "gdrive token 格式需要 OAuth client secrets 才能自动刷新",
                )
            client_id, client_secret = load_oauth_client_secrets(settings.path_value("GOOGLE_OAUTH_CLIENT_SECRETS_PATH"))
            access_token = credential_data.get("access_token")
            refresh_token = credential_data.get("refresh_token")
            if not isinstance(access_token, str) or not isinstance(refresh_token, str):
                raise RouterError("GOOGLE_CREDENTIALS_INVALID", "gdrive 凭据缺少 access_token/refresh_token")
            credentials = Credentials(
                token=access_token,
                refresh_token=refresh_token,
                token_uri="https://oauth2.googleapis.com/token",
                client_id=client_id,
                client_secret=client_secret,
                scopes=[GOOGLE_DRIVE_SCOPE],
                expiry=parse_node_expiry(credential_data.get("expiry_date")),
            )
        else:
            raise RouterError("GOOGLE_CREDENTIALS_INVALID", "不支持的 OAuth 凭据格式")
    except RouterError:
        raise
    except (KeyError, TypeError, ValueError) as error:
        raise RouterError("GOOGLE_CREDENTIALS_INVALID", "无法建立 OAuth credentials") from error

    return DriveClient(
        file_id=settings.required_text("GOOGLE_DRIVE_FILE_ID"),
        session=AuthorizedSession(credentials),
        timeout_seconds=settings.int_value("REQUEST_TIMEOUT_SECONDS", 5, 120),
        max_file_bytes=settings.int_value("MAX_CONTROL_FILE_BYTES", 1024, 1_048_576),
    )


def default_state() -> dict[str, Any]:
    return {
        "protocol_version": 1,
        "last_event_id": None,
        "last_result": None,
        "last_processed_at": None,
        "last_seen_modified_time": None,
        "events": {},
    }


class StateStore:
    def __init__(self, path: Path) -> None:
        self.path = path

    def load(self) -> dict[str, Any]:
        if not self.path.exists():
            return default_state()
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise RouterError("CURSOR_INVALID", "cursor.json 不是有效 JSON") from error
        if not isinstance(raw, Mapping) or not isinstance(raw.get("events", {}), Mapping):
            raise RouterError("CURSOR_INVALID", "cursor.json 结构不合法")
        state = default_state()
        for key in state:
            if key in raw:
                state[key] = raw[key]
        state["events"] = dict(state["events"])
        return state

    def save(self, state: Mapping[str, Any]) -> None:
        normalized = dict(state)
        events = dict(normalized.get("events", {}))
        if len(events) > MAX_EVENT_RECORDS:
            # 已确认的最旧事件可以压缩；CLAIMED/BLOCKED 永远保留以阻止不确定重发。
            removable = sorted(
                (
                    (event_id, record)
                    for event_id, record in events.items()
                    if isinstance(record, Mapping) and record.get("state") in {"SENT", "ACKED"}
                ),
                key=lambda item: str(item[1].get("updated_at", "")),
            )
            while len(events) > MAX_EVENT_RECORDS and removable:
                event_id, _ = removable.pop(0)
                events.pop(event_id, None)
        normalized["events"] = events
        atomic_write_json(self.path, normalized)


class RouterLog:
    """仅记录可审计的摘要；绝不将 message、OAuth token 写入日志。"""

    def __init__(self, path: Path) -> None:
        self.path = path

    def record(self, code: str, event: ControlEvent | None = None, state: str | None = None) -> None:
        entry: dict[str, Any] = {"timestamp": utc_now(), "code": code}
        if state:
            entry["state"] = state
        if event:
            entry.update(
                {
                    "event_id": event.event_id,
                    "from": event.source,
                    "to": event.destination,
                    "type": event.event_type,
                    "payload_sha256": event.payload_sha256,
                }
            )
        self.path.parent.mkdir(parents=True, exist_ok=True)
        with self.path.open("a", encoding="utf-8", newline="\n") as handle:
            handle.write(json.dumps(entry, ensure_ascii=False, sort_keys=True) + "\n")


class BrowserSender(Protocol):
    def send(self, conversation_url: str, payload: str) -> None: ...


class ChatGPTBrowserSender:
    """通过本机 CDP 发送，不读取任何 ChatGPT 历史消息文本。"""

    EDITOR_SELECTORS = (
        "#prompt-textarea",
        "textarea[data-id='root']",
        "textarea",
        "[contenteditable='true'][role='textbox']",
    )
    SEND_SELECTORS = (
        "button[data-testid='send-button']",
        "button[aria-label='Send prompt']",
        "button[aria-label*='发送']",
    )
    STOP_SELECTORS = (
        "button[data-testid='stop-button']",
        "button[aria-label*='Stop']",
        "button[aria-label*='停止']",
    )

    def __init__(self, settings: Settings) -> None:
        self.endpoint = validate_cdp_endpoint(settings.required_text("BROWSER_CDP_ENDPOINT"))
        self.timeout_ms = settings.int_value("CHATGPT_SEND_CONFIRM_TIMEOUT_SECONDS", 5, 120) * 1000

    @staticmethod
    def _first_visible(page: Any, selectors: tuple[str, ...], timeout_ms: int) -> Any | None:
        deadline = time.monotonic() + timeout_ms / 1000
        while True:
            for selector in selectors:
                locator = page.locator(selector).first
                try:
                    if locator.count() and locator.is_visible(timeout=250):
                        return locator
                except Exception:
                    # 页面重绘时 locator 可能短暂失效；在本次确认窗口内重试即可。
                    pass
            if time.monotonic() >= deadline:
                return None
            page.wait_for_timeout(150)

    @staticmethod
    def _editor_is_empty(editor: Any) -> bool:
        # evaluate 仅返回布尔值，检查的是当前输入框而不是任何对话 turn。
        return bool(
            editor.evaluate(
                """node => ('value' in node
                    ? node.value === ''
                    : ((node.textContent || '').trim() === ''))"""
            )
        )

    def _wait_for_editor_empty(self, page: Any, editor: Any) -> bool:
        deadline = time.monotonic() + self.timeout_ms / 1000
        while time.monotonic() < deadline:
            try:
                if self._editor_is_empty(editor):
                    return True
            except Exception:
                return False
            page.wait_for_timeout(150)
        return False

    def send(self, conversation_url: str, payload: str) -> None:
        configured_url = validate_fixed_conversation_url(conversation_url)
        try:
            from playwright.sync_api import Error as PlaywrightError
            from playwright.sync_api import sync_playwright
        except ImportError as error:
            raise RouterError("PLAYWRIGHT_DEPENDENCY_MISSING", "需要安装 playwright Python 包") from error

        try:
            with sync_playwright() as playwright:
                browser = playwright.chromium.connect_over_cdp(self.endpoint, timeout=self.timeout_ms)
                if not browser.contexts:
                    raise RouterError("CHATGPT_BROWSER_CONTEXT_MISSING", "CDP 未暴露已登录的浏览器上下文")
                context = browser.contexts[0]

                # 只比较 URL 来复用固定页面，不读取候选页面的 DOM 或历史内容。
                target_url = canonical_conversation_url(configured_url)
                page = next(
                    (candidate for candidate in context.pages if canonical_conversation_url(candidate.url) == target_url),
                    None,
                )
                if page is None:
                    page = context.new_page()
                    page.goto(configured_url, wait_until="domcontentloaded", timeout=self.timeout_ms)

                final_url = urlsplit(page.url)
                if final_url.hostname != "chatgpt.com":
                    raise RouterError("CHATGPT_LOGIN_REQUIRED", "未保持 ChatGPT 已登录会话或被重定向")

                editor = self._first_visible(page, self.EDITOR_SELECTORS, self.timeout_ms)
                if editor is None:
                    raise RouterError("CHATGPT_INPUT_NOT_FOUND", "未找到 ChatGPT 消息输入框")
                if self._first_visible(page, self.STOP_SELECTORS, 0) is not None:
                    raise RouterError("CHATGPT_CONVERSATION_BUSY", "固定对话仍在生成，未发送新消息")

                editor.fill(payload)
                send_button = self._first_visible(page, self.SEND_SELECTORS, self.timeout_ms)
                if send_button is None:
                    raise RouterError("CHATGPT_SEND_BUTTON_NOT_FOUND", "未找到 ChatGPT 发送按钮")
                if send_button.is_disabled():
                    raise RouterError("CHATGPT_SEND_BUTTON_DISABLED", "ChatGPT 发送按钮不可用")
                send_button.click(timeout=self.timeout_ms)

                # 必须同时观察到输入框清空和生成停止按钮，才可将 event 标为 SENT。
                if not self._wait_for_editor_empty(page, editor):
                    raise RouterError("CHATGPT_SEND_NOT_ACCEPTED", "点击后输入框未清空")
                if self._first_visible(page, self.STOP_SELECTORS, self.timeout_ms) is None:
                    raise RouterError("CHATGPT_SEND_CONFIRMATION_AMBIGUOUS", "未观察到生成状态，拒绝标记 SENT")
                # 不调用 browser.close()；这是用户正在使用的已连接浏览器，而非 Router 启动的进程。
        except RouterError:
            raise
        except PlaywrightError as error:
            raise RouterError("CHATGPT_BROWSER_ERROR", type(error).__name__) from error


class LocalAIRequestWriter:
    def __init__(self, path: Path) -> None:
        self.path = path

    def write(self, event: ControlEvent) -> None:
        if self.path.exists():
            existing = self.path.read_text(encoding="utf-8")
            match = LOCAL_REQUEST_MARKER.search(existing)
            if match and match.group(1) == event.event_id:
                return
            raise RouterError(
                "LOCAL_AI_REQUEST_FILE_OCCUPIED",
                "已有未处理的 Local AI request；为防覆盖，本事件未写入",
            )

        content = (
            "# Local AI Router Request\n\n"
            f"<!-- AI_ROUTER_EVENT_ID: {event.event_id} -->\n\n"
            "状态：`LOCAL_AI_TRIGGER_NOT_IMPLEMENTED`\n\n"
            f"- Event: `{event.event_id}`\n"
            f"- From: `{event.source}`\n"
            f"- Type: `{event.event_type}`\n"
            f"- Created at: `{event.created_at}`\n"
            f"- Drive: {event.drive_url}\n\n"
            "## Message\n\n"
            f"{event.message}\n"
        )
        atomic_write_text(self.path, content)


def outbound_message(event: ControlEvent) -> str:
    return f"[EVENT:{event.event_id}]\n\n{event.message}\n\n{event.drive_url}"


class Router:
    def __init__(
        self,
        settings: Settings,
        drive: DriveClient,
        state_store: StateStore,
        router_log: RouterLog,
        browser_sender: BrowserSender | None = None,
        local_ai_writer: LocalAIRequestWriter | None = None,
    ) -> None:
        self.settings = settings
        self.drive = drive
        self.state_store = state_store
        self.router_log = router_log
        self.browser_sender = browser_sender or ChatGPTBrowserSender(settings)
        self.local_ai_writer = local_ai_writer or LocalAIRequestWriter(WORK_DIRECTORY / "local-ai-request.md")

    @staticmethod
    def _set_last_result(state: dict[str, Any], event: ControlEvent | None, result: str) -> None:
        state["last_event_id"] = event.event_id if event else None
        state["last_result"] = result
        state["last_processed_at"] = utc_now()

    def _finish_event(self, state: dict[str, Any], event: ControlEvent, outcome: RouteOutcome) -> None:
        record = state["events"][event.event_id]
        record.update(
            {
                "state": outcome.state,
                "result": outcome.result,
                "updated_at": utc_now(),
            }
        )
        if outcome.detail:
            record["detail"] = outcome.detail
        self._set_last_result(state, event, outcome.result)
        self.state_store.save(state)
        self.router_log.record(outcome.result, event, outcome.state)

    def _route(self, event: ControlEvent) -> RouteOutcome:
        if event.destination == "LOCAL_AI":
            self.local_ai_writer.write(event)
            return RouteOutcome("BLOCKED", "LOCAL_AI_TRIGGER_NOT_IMPLEMENTED")

        if not self.settings.bool_value("ENABLE_BROWSER_SEND"):
            raise RouterError("BROWSER_SEND_DISABLED", "ENABLE_BROWSER_SEND=false，未向 ChatGPT 发送")
        setting_key = "ENGINEER_CONVERSATION_URL" if event.destination == "ENGINEER" else "PLANNER_CONVERSATION_URL"
        # event.conversation_url 仅保留在协议中用于审计，绝不改变固定收件对话。
        conversation_url = validate_fixed_conversation_url(self.settings.required_text(setting_key))
        self.browser_sender.send(conversation_url, outbound_message(event))
        return RouteOutcome("SENT", "SENT")

    def run_once(self, *, execute: bool) -> RouterResult:
        metadata = self.drive.get_metadata()
        state = self.state_store.load()
        if state.get("last_seen_modified_time") == metadata.modified_time:
            return RouterResult("NOOP_MODIFIED_TIME_UNCHANGED")

        try:
            raw_event = self.drive.download_control_file()
            event = parse_control_event(raw_event)
        except RouterError as error:
            state["last_seen_modified_time"] = metadata.modified_time
            self._set_last_result(state, None, error.code)
            self.state_store.save(state)
            self.router_log.record(error.code)
            return RouterResult(error.code, detail=error.detail)

        if event.status != "PENDING":
            state["last_seen_modified_time"] = metadata.modified_time
            self._set_last_result(state, event, f"IGNORED_EVENT_STATUS_{event.status}")
            self.state_store.save(state)
            self.router_log.record(f"IGNORED_EVENT_STATUS_{event.status}", event)
            return RouterResult(f"IGNORED_EVENT_STATUS_{event.status}", event)

        existing = state["events"].get(event.event_id)
        if existing:
            state["last_seen_modified_time"] = metadata.modified_time
            if existing.get("payload_sha256") != event.payload_sha256:
                self._set_last_result(state, event, "BLOCKED_EVENT_ID_CONFLICT")
                self.state_store.save(state)
                self.router_log.record("BLOCKED_EVENT_ID_CONFLICT", event, "BLOCKED")
                return RouterResult("BLOCKED_EVENT_ID_CONFLICT", event, "BLOCKED")
            existing_state = str(existing.get("state", "UNKNOWN"))
            existing_result = str(existing.get("result", existing_state))
            self._set_last_result(state, event, existing_result)
            self.state_store.save(state)
            if existing_state == "SENT":
                return RouterResult("NOOP_EVENT_ALREADY_SENT", event, existing_state)
            if existing_state == "CLAIMED":
                return RouterResult("NOOP_EVENT_CLAIMED_REQUIRES_RECONCILIATION", event, existing_state)
            if existing_state == "BLOCKED":
                return RouterResult("NOOP_EVENT_BLOCKED_REQUIRES_RECONCILIATION", event, existing_state)
            return RouterResult("NOOP_EVENT_ALREADY_RECORDED", event, existing_state)

        if not execute:
            # observe 是 Phase 1 专用只读检查；不保存 cursor，后续 once 仍可安全处理该事件。
            return RouterResult("EVENT_DETECTED", event)

        # 在任何外部动作前落盘 CLAIMED。进程崩溃后宁可人工对账，也绝不自动重复发送。
        state["last_seen_modified_time"] = metadata.modified_time
        state["events"][event.event_id] = {
            "state": "CLAIMED",
            "result": "CLAIMED",
            "payload_sha256": event.payload_sha256,
            "claimed_at": utc_now(),
            "updated_at": utc_now(),
        }
        self._set_last_result(state, event, "CLAIMED")
        self.state_store.save(state)
        self.router_log.record("CLAIMED", event, "CLAIMED")

        try:
            outcome = self._route(event)
        except RouterError as error:
            outcome = RouteOutcome("BLOCKED", error.code, error.detail)
        except Exception as error:  # 不让未知异常诱发自动 retry。
            outcome = RouteOutcome("BLOCKED", "ROUTER_UNEXPECTED_ERROR", type(error).__name__)
        self._finish_event(state, event, outcome)
        return RouterResult(outcome.result, event, outcome.state, outcome.detail)


def runtime_paths() -> tuple[StateStore, RouterLog, LocalAIRequestWriter]:
    return (
        StateStore(WORK_DIRECTORY / "cursor.json"),
        RouterLog(WORK_DIRECTORY / "router.log"),
        LocalAIRequestWriter(WORK_DIRECTORY / "local-ai-request.md"),
    )


def doctor(settings: Settings) -> dict[str, Any]:
    result: dict[str, Any] = {
        "config_path": str(settings.config_path),
        "work_directory": str(WORK_DIRECTORY),
        "browser_send_enabled": settings.bool_value("ENABLE_BROWSER_SEND"),
        "checks": [],
    }
    credential_path = settings.path_value("GOOGLE_CREDENTIALS_PATH")
    result["checks"].append({"name": "google_credentials", "ok": credential_path.exists()})
    result["checks"].append({"name": "google_drive_file_id", "ok": bool(settings.optional_text("GOOGLE_DRIVE_FILE_ID"))})
    if settings.bool_value("ENABLE_BROWSER_SEND"):
        try:
            validate_fixed_conversation_url(settings.required_text("PLANNER_CONVERSATION_URL"))
            validate_fixed_conversation_url(settings.required_text("ENGINEER_CONVERSATION_URL"))
            validate_cdp_endpoint(settings.required_text("BROWSER_CDP_ENDPOINT"))
            result["checks"].append({"name": "browser_configuration", "ok": True})
        except RouterError as error:
            result["checks"].append({"name": "browser_configuration", "ok": False, "code": error.code})
    return result


def render_result(result: RouterResult) -> dict[str, Any]:
    rendered: dict[str, Any] = {"result": result.code}
    if result.state:
        rendered["state"] = result.state
    if result.detail:
        rendered["detail"] = result.detail
    if result.event:
        rendered.update({"event_id": result.event.event_id, "to": result.event.destination})
        if result.code == "EVENT_DETECTED":
            # Phase 1 明确要求输出四项；正常路由日志不保存 message。
            rendered.update({"message": result.event.message, "drive_url": result.event.drive_url})
    return rendered


def reconcile(state_store: StateStore, router_log: RouterLog, event_id: str, target_state: str, result: str) -> RouterResult:
    if target_state not in {"SENT", "ACKED", "BLOCKED"}:
        raise RouterError("RECONCILE_STATE_INVALID", "state 只能是 SENT、ACKED 或 BLOCKED")
    state = state_store.load()
    record = state["events"].get(event_id)
    if not isinstance(record, Mapping):
        raise RouterError("RECONCILE_EVENT_NOT_FOUND", "cursor 中没有该 event_id")
    updated_record = dict(record)
    updated_record.update({"state": target_state, "result": result, "updated_at": utc_now(), "manual_reconciliation": True})
    state["events"][event_id] = updated_record
    state["last_event_id"] = event_id
    state["last_result"] = result
    state["last_processed_at"] = utc_now()
    state_store.save(state)
    router_log.record("MANUAL_RECONCILIATION", None, target_state)
    return RouterResult("MANUAL_RECONCILIATION", state=target_state)


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="见域本地 Drive AI Router")
    subparsers = parser.add_subparsers(dest="command", required=True)

    init_parser = subparsers.add_parser("init-config", help="从示例创建本地 config.json，不覆盖已有文件")
    init_parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG_PATH)

    for name, help_text in (
        ("doctor", "检查本地配置和依赖，不访问 Drive"),
        ("observe", "只读执行一次 Phase 1 检查，不路由也不写 cursor"),
        ("once", "执行一次 watcher 与路由"),
        ("watch", "持续监控 modifiedTime"),
    ):
        command_parser = subparsers.add_parser(name, help=help_text)
        command_parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG_PATH)

    reconcile_parser = subparsers.add_parser("reconcile", help="人工对账不确定的已 CLAIMED/BLOCKED event")
    reconcile_parser.add_argument("--event-id", required=True)
    reconcile_parser.add_argument("--state", required=True)
    reconcile_parser.add_argument("--result", required=True)
    return parser


def print_json(payload: Mapping[str, Any]) -> None:
    print(json.dumps(payload, ensure_ascii=False, sort_keys=True))


def run_watch(router: Router, interval_seconds: int) -> int:
    error_count = 0
    while True:
        try:
            result = router.run_once(execute=True)
            error_count = 0
            if result.code != "NOOP_MODIFIED_TIME_UNCHANGED":
                print_json(render_result(result))
            delay = interval_seconds
        except KeyboardInterrupt:
            return 0
        except RouterError as error:
            error_count += 1
            router.router_log.record(error.code)
            print_json({"result": error.code, "detail": error.detail})
            # 网络/认证故障不进行高频重试，最大退避到五分钟。
            delay = min(interval_seconds * (2**min(error_count, 5)), 300)
        time.sleep(delay)


def main(argv: list[str] | None = None) -> int:
    args = create_parser().parse_args(argv)
    state_store, router_log, local_ai_writer = runtime_paths()

    try:
        if args.command == "init-config":
            target = args.config.resolve()
            if target.exists():
                raise RouterError("CONFIG_ALREADY_EXISTS", "为避免覆盖，未修改已有 config.json")
            atomic_write_text(target, CONFIG_EXAMPLE_PATH.read_text(encoding="utf-8"))
            print_json({"result": "CONFIG_CREATED", "config_path": str(target)})
            return 0

        if args.command == "status":  # 防御性分支；当前 parser 不创建该命令，供嵌入调用保留。
            print_json(state_store.load())
            return 0

        if args.command == "reconcile":
            result = reconcile(state_store, router_log, args.event_id, args.state, args.result)
            print_json(render_result(result))
            return 0

        settings = Settings.load(args.config.resolve())
        if args.command == "doctor":
            print_json(doctor(settings))
            return 0

        drive = build_drive_client(settings)
        router = Router(settings, drive, state_store, router_log, local_ai_writer=local_ai_writer)
        if args.command == "watch":
            return run_watch(router, settings.int_value("POLL_INTERVAL_SECONDS", 5, 3600))

        result = router.run_once(execute=args.command == "once")
        print_json(render_result(result))
        return 0 if result.state != "BLOCKED" else 2
    except RouterError as error:
        router_log.record(error.code)
        print_json({"result": error.code, "detail": error.detail})
        return 2


if __name__ == "__main__":
    sys.exit(main())
