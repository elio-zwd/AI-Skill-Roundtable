from __future__ import annotations

import argparse
import tempfile
import unittest
from pathlib import Path
import sys

TOOLS_ROOT = Path(__file__).resolve().parents[2]
if str(TOOLS_ROOT) not in sys.path:
    sys.path.insert(0, str(TOOLS_ROOT))

from device.cli import NOT_VERIFIED, _execute_tap
from device.models import DeviceControlError, ForegroundInfo, ScreenInfo


CLICKABLE_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="设置" resource-id="settings_entry" class="android.view.View" content-desc="设置" clickable="true" enabled="true" selected="false" checked="false" bounds="[900,80][1040,220]" />
  <node index="1" text="设置页" resource-id="settings_screen" class="android.view.View" content-desc="设置页" clickable="false" enabled="true" selected="false" checked="false" bounds="[0,0][1080,2400]" />
</hierarchy>
"""

NON_CLICKABLE_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="" resource-id="com.android.launcher3:id/workspace" class="android.view.View" content-desc="" clickable="false" enabled="true" selected="false" checked="false" bounds="[0,0][1080,2400]" />
</hierarchy>
"""


class FakeClient:
    def __init__(self, xmls: list[str]) -> None:
        self.serial = "emulator-5554"
        self.xmls = list(xmls)
        self.taps: list[tuple[int, int]] = []

    def screenshot_png(self) -> bytes:
        return b"\x89PNG\r\n\x1a\nFAKE"

    def dump_ui_xml(self) -> str:
        if len(self.xmls) > 1:
            return self.xmls.pop(0)
        return self.xmls[0]

    def foreground_info(self) -> ForegroundInfo:
        return ForegroundInfo("com.elio.jianyu", "com.elio.jianyu.MainActivity")

    def screen_info(self) -> ScreenInfo:
        return ScreenInfo(1080, 2400, 420, 28, "portrait")

    def tap(self, x: int, y: int) -> None:
        self.taps.append((x, y))


def _args(
    *,
    by: str,
    value: str,
    expect_by: str | None = None,
    expect_value: str | None = None,
) -> argparse.Namespace:
    return argparse.Namespace(
        by=by,
        value=value,
        index=None,
        expect_by=expect_by,
        expect_value=expect_value,
        expect_index=None,
        timeout=100,
        interval=1,
    )


class TapVerificationSafetyTests(unittest.TestCase):
    def test_non_clickable_target_is_rejected_before_sending_tap(self) -> None:
        client = FakeClient([NON_CLICKABLE_XML])
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaises(DeviceControlError) as context:
                _execute_tap(
                    client,
                    _args(
                        by="resource-id",
                        value="com.android.launcher3:id/workspace",
                    ),
                    Path(temp_dir),
                )
        self.assertEqual("TARGET_NOT_CLICKABLE", context.exception.category)
        self.assertEqual([], client.taps)

    def test_expected_state_already_present_is_not_verified_and_tap_is_not_sent(self) -> None:
        client = FakeClient([CLICKABLE_XML])
        with tempfile.TemporaryDirectory() as temp_dir:
            result, exit_code = _execute_tap(
                client,
                _args(
                    by="resource-id",
                    value="settings_entry",
                    expect_by="resource-id",
                    expect_value="settings_screen",
                ),
                Path(temp_dir),
            )
        self.assertEqual(NOT_VERIFIED, exit_code)
        self.assertEqual("NOT_VERIFIED", result["status"])
        self.assertFalse(result["tapSent"])
        self.assertTrue(result["expectedPresentBefore"])
        self.assertEqual([], client.taps)


if __name__ == "__main__":
    unittest.main()
