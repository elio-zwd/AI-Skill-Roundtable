#!/usr/bin/env python3
"""根目录旧 ADB 小工具的显式设备集成测试。

普通 CI 不运行本文件。需要真实设备时必须设置：

    JIANYU_ADB_DEVICE=<adb-serial>

可选：

    JIANYU_FIND_TEXT=<当前页面可见文字>

这样不会在多设备场景误选目标，也不再依赖历史页面或固定坐标。
"""

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEVICE = os.environ.get("JIANYU_ADB_DEVICE", "").strip()
FIND_TEXT = os.environ.get("JIANYU_FIND_TEXT", "").strip()


@unittest.skipUnless(DEVICE, "Set JIANYU_ADB_DEVICE to run real-device integration tests")
class TestADBTools(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        result = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            raise unittest.SkipTest("adb devices failed")
        states = {}
        for line in result.stdout.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 2:
                states[parts[0]] = parts[1]
        if states.get(DEVICE) != "device":
            raise unittest.SkipTest(f"Requested device is not online: {DEVICE} state={states.get(DEVICE)}")

    def test_screencap_explicit_device(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "screen.png"
            result = subprocess.run(
                [
                    sys.executable,
                    str(REPOSITORY_ROOT / "tools" / "screencap.py"),
                    "-d",
                    DEVICE,
                    "-o",
                    str(output),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(str(output.resolve()), result.stdout.strip())
            self.assertTrue(output.is_file())
            self.assertGreater(output.stat().st_size, 1024)

    def test_uidump_explicit_device(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            output = Path(temp_dir) / "window.xml"
            result = subprocess.run(
                [
                    sys.executable,
                    str(REPOSITORY_ROOT / "tools" / "uidump.py"),
                    "-d",
                    DEVICE,
                    "-o",
                    str(output),
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(str(output.resolve()), result.stdout.strip())
            self.assertIn("<hierarchy", output.read_text(encoding="utf-8"))

    @unittest.skipUnless(FIND_TEXT, "Set JIANYU_FIND_TEXT to test current-page text lookup")
    def test_uidump_find_current_page_text(self):
        result = subprocess.run(
            [
                sys.executable,
                str(REPOSITORY_ROOT / "tools" / "uidump.py"),
                "-d",
                DEVICE,
                "--find",
                FIND_TEXT,
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        coordinates = result.stdout.strip().split()
        self.assertEqual(2, len(coordinates))
        self.assertGreaterEqual(int(coordinates[0]), 0)
        self.assertGreaterEqual(int(coordinates[1]), 0)


if __name__ == "__main__":
    unittest.main()
