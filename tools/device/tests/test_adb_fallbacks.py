from __future__ import annotations

import subprocess
import sys
import unittest
from pathlib import Path

TOOLS_ROOT = Path(__file__).resolve().parents[2]
if str(TOOLS_ROOT) not in sys.path:
    sys.path.insert(0, str(TOOLS_ROOT))

from device.adb_client import AdbClient


class AdbFallbackTests(unittest.TestCase):
    def test_foreground_query_falls_back_to_window_dumpsys(self) -> None:
        calls: list[list[str]] = []

        def runner(command, **kwargs):
            calls.append(command)
            if command[-2:] == ["devices", "-l"]:
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout="List of devices attached\nemulator-5554 device\n",
                    stderr="",
                )
            if command[-4:] == ["shell", "dumpsys", "activity", "activities"]:
                return subprocess.CompletedProcess(
                    command,
                    1,
                    stdout="",
                    stderr="activity service unavailable",
                )
            if command[-3:] == ["dumpsys", "window", "windows"]:
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout=(
                        "mCurrentFocus=Window{abc u0 "
                        "com.elio.jianyu/com.elio.jianyu.MainActivity}"
                    ),
                    stderr="",
                )
            return subprocess.CompletedProcess(command, 0, stdout="", stderr="")

        client = AdbClient(runner=runner)
        client.bind()
        foreground = client.foreground_info()

        self.assertEqual("com.elio.jianyu", foreground.package)
        self.assertEqual("com.elio.jianyu.MainActivity", foreground.activity)
        self.assertTrue(
            any(command[-4:] == ["shell", "dumpsys", "activity", "activities"] for command in calls)
        )
        self.assertTrue(
            any(command[-3:] == ["dumpsys", "window", "windows"] for command in calls)
        )

    def test_foreground_query_returns_unknown_when_both_sources_fail(self) -> None:
        def runner(command, **kwargs):
            if command[-2:] == ["devices", "-l"]:
                return subprocess.CompletedProcess(
                    command,
                    0,
                    stdout="List of devices attached\nemulator-5554 device\n",
                    stderr="",
                )
            return subprocess.CompletedProcess(
                command,
                1,
                stdout="",
                stderr="unsupported",
            )

        client = AdbClient(runner=runner)
        client.bind()
        foreground = client.foreground_info()

        self.assertIsNone(foreground.package)
        self.assertIsNone(foreground.activity)


if __name__ == "__main__":
    unittest.main()
