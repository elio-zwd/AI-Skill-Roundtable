from __future__ import annotations

import argparse
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

TOOLS_ROOT = Path(__file__).resolve().parents[2]
if str(TOOLS_ROOT) not in sys.path:
    sys.path.insert(0, str(TOOLS_ROOT))

from device.adb_client import (
    AdbClient,
    load_device_profile,
    parse_adb_devices,
    parse_foreground_component,
    parse_wm_density,
    parse_wm_size,
    profile_mismatches,
    resolve_device_serial,
)
from device.cli import NOT_VERIFIED, _execute_tap
from device.evidence import resolve_output_directory
from device.models import DeviceControlError, DeviceRecord, ForegroundInfo, ScreenInfo, Selector
from device.observer import capture_observation
from device.selectors import (
    find_nodes,
    parse_bounds,
    parse_ui_nodes,
    select_unique_node,
    visible_texts,
    wait_for_selector,
)


SAMPLE_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="" resource-id="" class="android.widget.FrameLayout" content-desc="" clickable="false" enabled="true" selected="false" checked="false" bounds="[0,0][1080,2400]">
    <node index="0" text="首页" resource-id="com.elio.jianyu:id/home_tab" class="android.view.View" content-desc="首页" clickable="true" enabled="true" selected="true" checked="false" bounds="[0,2200][270,2400]" />
    <node index="1" text="议题" resource-id="com.elio.jianyu:id/issues_tab" class="android.view.View" content-desc="议题" clickable="true" enabled="true" selected="false" checked="false" bounds="[270,2200][540,2400]" />
    <node index="2" text="设置" resource-id="settings_entry" class="android.view.View" content-desc="设置" clickable="true" enabled="true" selected="false" checked="false" bounds="[936,84][1032,180]" />
    <node index="3" text="重复" resource-id="duplicate_a" class="android.view.View" content-desc="" clickable="true" enabled="true" selected="false" checked="false" bounds="[10,10][110,110]" />
    <node index="4" text="重复" resource-id="duplicate_b" class="android.view.View" content-desc="" clickable="true" enabled="true" selected="false" checked="false" bounds="[120,10][220,110]" />
  </node>
</hierarchy>
"""

EXPECTED_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="设置页" resource-id="settings_screen" class="android.view.View" content-desc="设置页" clickable="false" enabled="true" selected="false" checked="false" bounds="[0,0][1080,2400]" />
</hierarchy>
"""


class DeviceResolutionTests(unittest.TestCase):
    def test_parse_adb_devices_keeps_state_and_details(self) -> None:
        output = """List of devices attached
emulator-5554 device product:sdk model:LDPlayer transport_id:1
phone-1 unauthorized usb:1-2
phone-2 offline transport_id:2
"""
        records = parse_adb_devices(output)
        self.assertEqual(3, len(records))
        self.assertEqual("device", records[0].state)
        self.assertEqual("LDPlayer", records[0].details["model"])
        self.assertEqual("unauthorized", records[1].state)

    def test_unique_online_device_can_be_inferred(self) -> None:
        records = [
            DeviceRecord("emulator-5554", "device"),
            DeviceRecord("phone-1", "offline"),
        ]
        self.assertEqual("emulator-5554", resolve_device_serial(records, None, None))

    def test_multiple_online_devices_require_explicit_serial(self) -> None:
        records = [
            DeviceRecord("emulator-5554", "device"),
            DeviceRecord("phone-1", "device"),
        ]
        with self.assertRaises(DeviceControlError) as context:
            resolve_device_serial(records, None, None)
        self.assertEqual("AMBIGUOUS_DEVICE", context.exception.category)

    def test_explicit_serial_must_exist_and_be_online(self) -> None:
        records = [DeviceRecord("emulator-5554", "offline")]
        with self.assertRaises(DeviceControlError) as context:
            resolve_device_serial(records, "emulator-5554", None)
        self.assertEqual("DEVICE_NOT_READY", context.exception.category)

    def test_profile_serial_is_used_when_cli_serial_missing(self) -> None:
        records = [DeviceRecord("profile-device", "device")]
        self.assertEqual(
            "profile-device",
            resolve_device_serial(records, None, "profile-device"),
        )


class ParsingTests(unittest.TestCase):
    def test_wm_size_prefers_override(self) -> None:
        self.assertEqual(
            (1080, 2400),
            parse_wm_size("Physical size: 1440x3200\nOverride size: 1080x2400"),
        )

    def test_wm_density_prefers_override(self) -> None:
        self.assertEqual(
            420,
            parse_wm_density("Physical density: 560\nOverride density: 420"),
        )

    def test_foreground_component_normalizes_relative_activity(self) -> None:
        info = parse_foreground_component(
            "mResumedActivity: ActivityRecord{abc u0 com.elio.jianyu/.MainActivity t1}"
        )
        self.assertEqual("com.elio.jianyu", info.package)
        self.assertEqual("com.elio.jianyu.MainActivity", info.activity)

    def test_profile_load_and_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "profile.json"
            path.write_text(
                json.dumps(
                    {
                        "name": "ldplayer",
                        "serial": "emulator-5554",
                        "expected": {
                            "size": "1080x2400",
                            "density": 420,
                            "api": 28,
                            "orientation": "portrait",
                        },
                    }
                ),
                encoding="utf-8",
            )
            profile = load_device_profile(path)
        self.assertIsNotNone(profile)
        self.assertEqual("emulator-5554", profile.serial)
        mismatches = profile_mismatches(
            profile,
            ScreenInfo(1080, 2400, 440, 28, "portrait"),
        )
        self.assertEqual(["density expected=420 actual=440"], mismatches)


class SelectorTests(unittest.TestCase):
    def setUp(self) -> None:
        self.nodes = parse_ui_nodes(SAMPLE_XML)

    def test_bounds_and_node_count(self) -> None:
        self.assertEqual((1, 2, 3, 4), parse_bounds("[1,2][3,4]"))
        self.assertIsNone(parse_bounds("bad"))
        self.assertEqual(6, len(self.nodes))

    def test_tag_matches_exact_resource_id(self) -> None:
        node = select_unique_node(self.nodes, Selector("tag", "settings_entry"))
        self.assertEqual((984, 132), node.center)
        self.assertTrue(node.clickable)

    def test_resource_id_can_match_android_id_suffix(self) -> None:
        node = select_unique_node(self.nodes, Selector("resource-id", "issues_tab"))
        self.assertEqual("议题", node.text)

    def test_content_desc_and_text_selectors(self) -> None:
        self.assertEqual(
            1,
            len(find_nodes(self.nodes, Selector("content-desc", "设置"))),
        )
        self.assertEqual(
            1,
            len(find_nodes(self.nodes, Selector("text-contains", "首"))),
        )

    def test_ambiguous_match_requires_explicit_index(self) -> None:
        with self.assertRaises(DeviceControlError) as context:
            select_unique_node(self.nodes, Selector("text-exact", "重复"))
        self.assertEqual("SELECTOR_AMBIGUOUS", context.exception.category)
        selected = select_unique_node(self.nodes, Selector("text-exact", "重复", 1))
        self.assertEqual("duplicate_b", selected.resource_id)

    def test_visible_texts_are_unique_and_bounded(self) -> None:
        texts = visible_texts(self.nodes, limit=3)
        self.assertEqual(("首页", "议题", "设置"), texts)

    def test_wait_retries_until_selector_appears(self) -> None:
        payloads = iter([SAMPLE_XML.replace("settings_entry", "missing"), SAMPLE_XML])
        with patch("device.selectors.time.monotonic", side_effect=[0.0, 0.0, 0.1]):
            node = wait_for_selector(
                lambda: next(payloads),
                Selector("tag", "settings_entry"),
                timeout_seconds=1,
                interval_seconds=0,
                sleeper=lambda _: None,
            )
        self.assertEqual("settings_entry", node.resource_id)


class EvidenceTests(unittest.TestCase):
    def test_repository_inside_output_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as repo_dir:
            inside = Path(repo_dir) / "evidence"
            with self.assertRaises(DeviceControlError) as context:
                resolve_output_directory(inside, repo_dir)
        self.assertEqual("OUTPUT_INSIDE_REPOSITORY", context.exception.category)

    def test_external_output_is_created(self) -> None:
        with tempfile.TemporaryDirectory() as repo_dir, tempfile.TemporaryDirectory() as outer:
            target = Path(outer) / "evidence"
            resolved = resolve_output_directory(target, repo_dir)
            self.assertTrue(resolved.is_dir())


class FakeClient:
    def __init__(self, xmls: list[str] | None = None) -> None:
        self.serial = "emulator-5554"
        self.xmls = list(xmls or [SAMPLE_XML])
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


class ObservationAndTapTests(unittest.TestCase):
    def test_observation_writes_compact_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            observation = capture_observation(FakeClient(), temp_dir)
            payload = json.loads(Path(observation.json_path).read_text(encoding="utf-8"))
            self.assertEqual("emulator-5554", payload["serial"])
            self.assertEqual(6, payload["nodeCount"])
            self.assertNotIn("xml", payload)
            self.assertTrue(Path(observation.screenshot_path).is_file())
            self.assertTrue(Path(observation.xml_path).is_file())

    def test_tap_without_expected_state_is_not_verified(self) -> None:
        client = FakeClient([SAMPLE_XML, SAMPLE_XML])
        args = argparse.Namespace(
            by="tag",
            value="settings_entry",
            index=None,
            expect_by=None,
            expect_value=None,
            expect_index=None,
            timeout=100,
            interval=1,
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            result, exit_code = _execute_tap(client, args, Path(temp_dir))
        self.assertEqual(NOT_VERIFIED, exit_code)
        self.assertEqual("NOT_VERIFIED", result["status"])
        self.assertEqual([(984, 132)], client.taps)

    def test_tap_with_expected_state_is_pass(self) -> None:
        client = FakeClient([SAMPLE_XML, EXPECTED_XML, EXPECTED_XML])
        args = argparse.Namespace(
            by="tag",
            value="settings_entry",
            index=None,
            expect_by="tag",
            expect_value="settings_screen",
            expect_index=None,
            timeout=1000,
            interval=1,
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            result, exit_code = _execute_tap(client, args, Path(temp_dir))
        self.assertEqual(0, exit_code)
        self.assertEqual("PASS", result["status"])
        self.assertEqual("settings_screen", result["expectedNode"]["resourceId"])


class AdbClientBindingTests(unittest.TestCase):
    def test_client_keeps_same_bound_serial(self) -> None:
        calls: list[list[str]] = []

        def runner(command, **kwargs):
            calls.append(command)
            if command[-2:] == ["devices", "-l"]:
                return _completed(command, "List of devices attached\nemulator-5554 device\n")
            return _completed(command, "28\n")

        client = AdbClient(runner=runner)
        client.bind()
        client.shell("getprop", "ro.build.version.sdk")
        self.assertEqual("emulator-5554", client.serial)
        self.assertEqual(["adb", "-s", "emulator-5554"], calls[1][:3])


def _completed(command, stdout="", stderr="", returncode=0):
    from subprocess import CompletedProcess

    return CompletedProcess(command, returncode, stdout=stdout, stderr=stderr)


if __name__ == "__main__":
    unittest.main()
