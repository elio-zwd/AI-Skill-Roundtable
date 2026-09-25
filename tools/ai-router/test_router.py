"""Router 的离线回归测试；不访问真实 Drive、ChatGPT 或 Git。"""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any


MODULE_PATH = Path(__file__).with_name("router.py")
SPEC = importlib.util.spec_from_file_location("ai_router", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
router = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = router
SPEC.loader.exec_module(router)


def event_payload(**overrides: Any) -> dict[str, Any]:
    event = {
        "protocol_version": 1,
        "event_id": "TEST-0001",
        "status": "PENDING",
        "from": "PLANNER",
        "to": "ENGINEER",
        "type": "TEST",
        "message": "中控连接测试 TEST-0001",
        "drive_url": "https://drive.google.com/",
        "conversation_url": "USE_CONFIG",
        "created_at": "2026-09-13T00:00:00Z",
    }
    event.update(overrides)
    return event


class FakeDrive:
    def __init__(self, payload: dict[str, Any], modified_time: str = "2026-09-13T00:00:00Z") -> None:
        self.payload = payload
        self.modified_time = modified_time
        self.metadata_calls = 0
        self.download_calls = 0

    def get_metadata(self) -> Any:
        self.metadata_calls += 1
        return router.DriveMetadata("test-drive-file", self.modified_time)

    def download_control_file(self) -> dict[str, Any]:
        self.download_calls += 1
        return dict(self.payload)


class RecordingBrowser:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []

    def send(self, conversation_url: str, payload: str) -> None:
        self.calls.append((conversation_url, payload))


class FailingBrowser:
    def __init__(self) -> None:
        self.calls = 0

    def send(self, conversation_url: str, payload: str) -> None:
        self.calls += 1
        raise router.RouterError("CHATGPT_SEND_CONFIRMATION_AMBIGUOUS")


class FakeResponse:
    def __init__(self, payload: dict[str, Any]) -> None:
        self.payload = payload
        self.headers: dict[str, str] = {}

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, Any]:
        return self.payload


class FakeSession:
    def __init__(self) -> None:
        self.calls: list[dict[str, Any]] = []

    def get(self, url: str, **kwargs: Any) -> FakeResponse:
        self.calls.append({"url": url, **kwargs})
        return FakeResponse({"id": "file-1", "modifiedTime": "2026-09-13T00:00:00Z"})


class RouterTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name)
        self.settings = router.Settings.from_mapping(
            {
                "GOOGLE_DRIVE_FILE_ID": "test-drive-file",
                "GOOGLE_CREDENTIALS_PATH": "credentials.json",
                "GOOGLE_OAUTH_CLIENT_SECRETS_PATH": "client.json",
                "PLANNER_CONVERSATION_URL": "https://chatgpt.com/c/planner-test",
                "ENGINEER_CONVERSATION_URL": "https://chatgpt.com/c/engineer-test",
                "ENABLE_BROWSER_SEND": True,
            },
            self.root / "config.json",
        )
        self.store = router.StateStore(self.root / "cursor.json")
        self.log = router.RouterLog(self.root / "router.log")
        self.local_request = router.LocalAIRequestWriter(self.root / "local-ai-request.md")

    def tearDown(self) -> None:
        self.temporary_directory.cleanup()

    def make_router(self, drive: FakeDrive, browser: Any) -> Any:
        return router.Router(
            self.settings,
            drive,
            self.store,
            self.log,
            browser_sender=browser,
            local_ai_writer=self.local_request,
        )

    def test_observe_prints_event_without_cursor_or_send(self) -> None:
        drive = FakeDrive(event_payload())
        browser = RecordingBrowser()
        result = self.make_router(drive, browser).run_once(execute=False)

        self.assertEqual("EVENT_DETECTED", result.code)
        self.assertEqual("TEST-0001", result.event.event_id)
        self.assertFalse(self.store.path.exists())
        self.assertEqual([], browser.calls)

    def test_sent_event_is_not_sent_again_after_modified_time_changes(self) -> None:
        drive = FakeDrive(event_payload())
        browser = RecordingBrowser()
        subject = self.make_router(drive, browser)

        first = subject.run_once(execute=True)
        self.assertEqual("SENT", first.code)
        self.assertEqual(1, len(browser.calls))
        self.assertIn("[EVENT:TEST-0001]", browser.calls[0][1])

        drive.modified_time = "2026-09-13T00:01:00Z"
        duplicate = subject.run_once(execute=True)
        self.assertEqual("NOOP_EVENT_ALREADY_SENT", duplicate.code)
        self.assertEqual(1, len(browser.calls))

    def test_uncertain_send_is_blocked_and_never_automatically_retried(self) -> None:
        drive = FakeDrive(event_payload())
        browser = FailingBrowser()
        subject = self.make_router(drive, browser)

        first = subject.run_once(execute=True)
        self.assertEqual("CHATGPT_SEND_CONFIRMATION_AMBIGUOUS", first.code)
        self.assertEqual("BLOCKED", first.state)
        self.assertEqual(1, browser.calls)

        drive.modified_time = "2026-09-13T00:01:00Z"
        duplicate = subject.run_once(execute=True)
        self.assertEqual("NOOP_EVENT_BLOCKED_REQUIRES_RECONCILIATION", duplicate.code)
        self.assertEqual(1, browser.calls)

    def test_same_event_id_with_changed_payload_is_blocked(self) -> None:
        drive = FakeDrive(event_payload())
        browser = RecordingBrowser()
        subject = self.make_router(drive, browser)
        self.assertEqual("SENT", subject.run_once(execute=True).code)

        drive.payload = event_payload(message="同一个 ID 的不同内容")
        drive.modified_time = "2026-09-13T00:01:00Z"
        result = subject.run_once(execute=True)
        self.assertEqual("BLOCKED_EVENT_ID_CONFLICT", result.code)
        self.assertEqual(1, len(browser.calls))

    def test_local_ai_writes_request_but_never_claims_execution(self) -> None:
        drive = FakeDrive(event_payload(to="LOCAL_AI"))
        result = self.make_router(drive, RecordingBrowser()).run_once(execute=True)

        self.assertEqual("LOCAL_AI_TRIGGER_NOT_IMPLEMENTED", result.code)
        self.assertEqual("BLOCKED", result.state)
        request = self.local_request.path.read_text(encoding="utf-8")
        self.assertIn("AI_ROUTER_EVENT_ID: TEST-0001", request)
        state = self.store.load()
        self.assertEqual("BLOCKED", state["events"]["TEST-0001"]["state"])

    def test_metadata_poll_uses_only_id_and_modified_time_fields(self) -> None:
        session = FakeSession()
        client = router.DriveClient("file-1", session, timeout_seconds=10, max_file_bytes=1024)
        metadata = client.get_metadata()

        self.assertEqual("file-1", metadata.file_id)
        self.assertEqual("id,modifiedTime", session.calls[0]["params"]["fields"])
        self.assertFalse(session.calls[0]["stream"])

    def test_gdrive_node_expiry_is_compatible_with_google_auth(self) -> None:
        expiry = router.parse_node_expiry(1_789_000_000_000)
        self.assertIsNotNone(expiry)
        self.assertIsNone(expiry.tzinfo)


if __name__ == "__main__":
    unittest.main(verbosity=2)
