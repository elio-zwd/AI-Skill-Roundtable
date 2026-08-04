from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

TOOLS_ROOT = Path(__file__).resolve().parents[2]
if str(TOOLS_ROOT) not in sys.path:
    sys.path.insert(0, str(TOOLS_ROOT))

from device.cli import _execute_tap
from device.models import DeviceControlError, ForegroundInfo, ScreenInfo


BEFORE_XML = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node text="设置" resource-id="settings_entry" class="android.view.View"
        content-desc="设置" clickable="true" enabled="true" selected="false"
        checked="false" bounds="[900,80][1040,200]" />
</hierarchy>
"""

MISSING_EXPECTED_XML = """<?xml version='1.0' encoding='UTF-8'?>
<hierarchy rotation="0">
  <node text="仍在首页" resource-id="home_screen" class="android.view.View"
        content-desc="首页" clickable="false" enabled="true" selected="false"
        checked="false" bounds="[0,0][1080,2400]" />
</hierarchy>
"""


class FakeClient:
    def __init__(self) -> None:
        self.serial = "emulator-5554"
        self.dumps = [BEFORE_XML, MISSING_EXPECTED_XML, MISSING_EXPECTED_XML]

    def screenshot_png(self) -> bytes:
        return b"\x89PNG\r\n\x1a\nFAKE"

    def dump_ui_xml(self) -> str:
        if len(self.dumps) > 1:
            return self.dumps.pop(0)
        return self.dumps[0]

    def foreground_info(self) -> ForegroundInfo:
        return ForegroundInfo("com.elio.jianyu", "com.elio.jianyu.MainActivity")

    def screen_info(self) -> ScreenInfo:
        return ScreenInfo(1080, 2400, 420, 28, "portrait")

    def tap(self, x: int, y: int) -> None:
        self.last_tap = (x, y)


class CliContractTests(unittest.TestCase):
    def test_help_survives_non_utf8_child_environment(self) -> None:
        cli_path = TOOLS_ROOT / "device" / "cli.py"
        environment = os.environ.copy()
        environment["PYTHONIOENCODING"] = "cp1252"
        result = subprocess.run(
            [sys.executable, str(cli_path), "--help"],
            capture_output=True,
            text=False,
            env=environment,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr.decode("utf-8", errors="replace"))
        output = result.stdout.decode("utf-8")
        self.assertIn("见域本地 AI Android 设备语义控制层", output)

    def test_tap_timeout_preserves_failure_evidence(self) -> None:
        arguments = argparse.Namespace(
            by="tag",
            value="settings_entry",
            index=None,
            expect_by="tag",
            expect_value="settings_screen",
            expect_index=None,
            timeout=0,
            interval=1,
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(DeviceControlError) as context:
                _execute_tap(FakeClient(), arguments, Path(temp_dir))
            evidence = Path(context.exception.details["evidence"])
            self.assertTrue(evidence.is_file())
            self.assertTrue((Path(temp_dir) / "before.json").is_file())
            self.assertTrue((Path(temp_dir) / "after.json").is_file())
            self.assertEqual("SELECTOR_TIMEOUT", context.exception.category)


if __name__ == "__main__":
    unittest.main()
