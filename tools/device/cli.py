#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from device.adb_client import AdbClient, load_device_profile, profile_mismatches
    from device.evidence import resolve_output_directory, write_json
    from device.models import DeviceControlError, Selector
    from device.observer import capture_observation
    from device.selectors import parse_ui_nodes, select_unique_node, wait_for_selector
else:
    from .adb_client import AdbClient, load_device_profile, profile_mismatches
    from .evidence import resolve_output_directory, write_json
    from .models import DeviceControlError, Selector
    from .observer import capture_observation
    from .selectors import parse_ui_nodes, select_unique_node, wait_for_selector

DEFAULT_PACKAGE = "com.elio.jianyu"
PASS = 0
FAIL = 1
NOT_VERIFIED = 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="见域本地 AI Android 设备语义控制层")
    subparsers = parser.add_subparsers(dest="command", required=True)

    doctor = subparsers.add_parser("doctor", help="检查设备连接与 Profile 门禁")
    _add_common_arguments(doctor, output=False)

    observe = subparsers.add_parser("observe", help="生成截图、UI XML 与紧凑观察证据")
    _add_common_arguments(observe, output=True)

    find = subparsers.add_parser("find", help="按语义选择器定位唯一控件")
    _add_common_arguments(find, output=True)
    _add_selector_arguments(find)

    wait = subparsers.add_parser("wait", help="等待语义选择器出现")
    _add_common_arguments(wait, output=True)
    _add_selector_arguments(wait)
    _add_wait_arguments(wait)

    assertion = subparsers.add_parser("assert", help="立即断言语义选择器存在")
    _add_common_arguments(assertion, output=True)
    _add_selector_arguments(assertion)

    tap = subparsers.add_parser("tap", help="点击语义控件并验证预期状态")
    _add_common_arguments(tap, output=True)
    _add_selector_arguments(tap)
    tap.add_argument("--expect-by", choices=_selector_choices())
    tap.add_argument("--expect-value")
    tap.add_argument("--expect-index", type=int)
    _add_wait_arguments(tap)

    launch = subparsers.add_parser("launch", help="启动 App 并验证前台状态")
    _add_common_arguments(launch, output=True)
    launch.add_argument("--package", help=f"包名，默认 Profile 或 {DEFAULT_PACKAGE}")
    launch.add_argument("--activity", help="可选 Activity，全限定名或 .MainActivity")
    launch.add_argument("--mode", choices=["warm", "force-stop"], default="warm")
    launch.add_argument("--expect-by", choices=_selector_choices())
    launch.add_argument("--expect-value")
    launch.add_argument("--expect-index", type=int)
    _add_wait_arguments(launch)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        result, exit_code = execute(args)
        _emit_result(result, args.json)
        return exit_code
    except DeviceControlError as exc:
        payload = exc.to_dict()
        _emit_result(payload, getattr(args, "json", False), is_error=True)
        return exc.exit_code
    except KeyboardInterrupt:
        _emit_result(
            {"status": "FAIL", "category": "INTERRUPTED", "message": "操作已中断。"},
            getattr(args, "json", False),
            is_error=True,
        )
        return 130


def execute(args: argparse.Namespace) -> tuple[dict[str, Any], int]:
    profile = load_device_profile(args.profile)
    client = AdbClient(adb_path=args.adb, timeout_seconds=args.adb_timeout)
    serial = client.bind(args.device, profile.serial if profile else None)

    if args.command == "doctor":
        screen = client.screen_info()
        foreground = client.foreground_info()
        mismatches = profile_mismatches(profile, screen)
        status = "PASS" if not mismatches else "FAIL"
        result = {
            "status": status,
            "serial": serial,
            "profile": profile.to_dict() if profile else None,
            "screen": screen.to_dict(),
            "foreground": foreground.to_dict(),
            "mismatches": mismatches,
        }
        return result, PASS if status == "PASS" else FAIL

    output = resolve_output_directory(args.output, args.repository_root)

    if args.command == "observe":
        observation = capture_observation(client, output)
        return {
            "status": "PASS",
            "action": "observe",
            **observation.to_dict(),
        }, PASS

    if args.command == "find":
        observation = capture_observation(client, output, prefix="find")
        selector = _selector_from_args(args)
        xml_text = Path(observation.xml_path).read_text(encoding="utf-8")
        node = select_unique_node(parse_ui_nodes(xml_text), selector)
        result = {
            "status": "PASS",
            "action": "find",
            "serial": serial,
            "selector": selector.to_dict(),
            "node": node.to_dict(),
            "observation": observation.json_path,
        }
        evidence = write_json(output / "find-result.json", result)
        result["evidence"] = str(evidence)
        return result, PASS

    if args.command == "assert":
        selector = _selector_from_args(args)
        xml_text = client.dump_ui_xml()
        node = select_unique_node(parse_ui_nodes(xml_text), selector)
        result = {
            "status": "PASS",
            "action": "assert",
            "serial": serial,
            "selector": selector.to_dict(),
            "node": node.to_dict(),
        }
        evidence = write_json(output / "assert-result.json", result)
        result["evidence"] = str(evidence)
        return result, PASS

    if args.command == "wait":
        selector = _selector_from_args(args)
        node = wait_for_selector(
            client.dump_ui_xml,
            selector,
            timeout_seconds=args.timeout / 1000.0,
            interval_seconds=args.interval / 1000.0,
        )
        result = {
            "status": "PASS",
            "action": "wait",
            "serial": serial,
            "selector": selector.to_dict(),
            "node": node.to_dict(),
            "timeoutMilliseconds": args.timeout,
        }
        evidence = write_json(output / "wait-result.json", result)
        result["evidence"] = str(evidence)
        return result, PASS

    if args.command == "tap":
        return _execute_tap(client, args, output)

    if args.command == "launch":
        return _execute_launch(client, args, profile, output)

    raise DeviceControlError(
        f"未知命令：{args.command}",
        category="COMMAND_INVALID",
        exit_code=64,
    )


def _execute_tap(
    client: AdbClient,
    args: argparse.Namespace,
    output: Path,
) -> tuple[dict[str, Any], int]:
    started = time.monotonic()
    selector = _selector_from_args(args)
    before = capture_observation(client, output, prefix="before")
    xml_text = Path(before.xml_path).read_text(encoding="utf-8")
    node = select_unique_node(parse_ui_nodes(xml_text), selector)
    if not node.enabled:
        raise DeviceControlError(
            "目标控件未启用，拒绝点击。",
            category="TARGET_DISABLED",
            details={"node": node.to_dict()},
        )

    client.tap(*node.center)
    expected = _expected_selector_from_args(args)
    expected_node = None
    status = "NOT_VERIFIED"
    exit_code = NOT_VERIFIED
    if expected is not None:
        expected_node = wait_for_selector(
            client.dump_ui_xml,
            expected,
            timeout_seconds=args.timeout / 1000.0,
            interval_seconds=args.interval / 1000.0,
        )
        status = "PASS"
        exit_code = PASS

    after = capture_observation(client, output, prefix="after")
    result = {
        "schemaVersion": 1,
        "status": status,
        "action": "tap",
        "serial": client.serial,
        "selector": selector.to_dict(),
        "matchedNode": node.to_dict(),
        "expected": expected.to_dict() if expected else None,
        "expectedNode": expected_node.to_dict() if expected_node else None,
        "durationMilliseconds": int((time.monotonic() - started) * 1000),
        "beforeObservation": before.json_path,
        "afterObservation": after.json_path,
        "beforeScreenshotSha256": before.screenshot_sha256,
        "afterScreenshotSha256": after.screenshot_sha256,
        "warning": None if expected else "未提供预期状态，动作不能判定为 UI PASS。",
    }
    evidence = write_json(output / "tap-result.json", result)
    result["evidence"] = str(evidence)
    return result, exit_code


def _execute_launch(
    client: AdbClient,
    args: argparse.Namespace,
    profile: Any,
    output: Path,
) -> tuple[dict[str, Any], int]:
    started = time.monotonic()
    package = args.package or (profile.package if profile else None) or DEFAULT_PACKAGE
    if args.mode == "force-stop":
        client.force_stop(package)
    client.launch(package, args.activity)

    expected = _expected_selector_from_args(args)
    expected_node = None
    if expected is not None:
        expected_node = wait_for_selector(
            client.dump_ui_xml,
            expected,
            timeout_seconds=args.timeout / 1000.0,
            interval_seconds=args.interval / 1000.0,
        )
    else:
        _wait_for_foreground_package(
            client,
            package,
            timeout_seconds=args.timeout / 1000.0,
            interval_seconds=args.interval / 1000.0,
        )

    observation = capture_observation(client, output, prefix="launch")
    result = {
        "schemaVersion": 1,
        "status": "PASS",
        "action": "launch",
        "serial": client.serial,
        "package": package,
        "activity": args.activity,
        "mode": args.mode,
        "expected": expected.to_dict() if expected else None,
        "expectedNode": expected_node.to_dict() if expected_node else None,
        "durationMilliseconds": int((time.monotonic() - started) * 1000),
        "observation": observation.json_path,
    }
    evidence = write_json(output / "launch-result.json", result)
    result["evidence"] = str(evidence)
    return result, PASS


def _wait_for_foreground_package(
    client: AdbClient,
    package: str,
    *,
    timeout_seconds: float,
    interval_seconds: float,
) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_package: str | None = None
    while True:
        foreground = client.foreground_info()
        last_package = foreground.package
        if last_package == package:
            return
        if time.monotonic() >= deadline:
            raise DeviceControlError(
                f"等待 App 前台超时：{package}",
                category="FOREGROUND_TIMEOUT",
                details={"lastPackage": last_package},
            )
        time.sleep(interval_seconds)


def _selector_from_args(args: argparse.Namespace) -> Selector:
    return Selector(by=args.by, value=args.value, index=args.index)


def _expected_selector_from_args(args: argparse.Namespace) -> Selector | None:
    by = getattr(args, "expect_by", None)
    value = getattr(args, "expect_value", None)
    index = getattr(args, "expect_index", None)
    if by is None and value is None:
        return None
    if not by or not value:
        raise DeviceControlError(
            "--expect-by 与 --expect-value 必须同时提供。",
            category="SELECTOR_INVALID",
            exit_code=64,
        )
    return Selector(by=by, value=value, index=index)


def _add_common_arguments(parser: argparse.ArgumentParser, *, output: bool) -> None:
    parser.add_argument("--device", help="目标 ADB Serial")
    parser.add_argument("--profile", help="设备 Profile JSON 路径")
    parser.add_argument("--adb", default="adb", help="ADB 可执行文件路径")
    parser.add_argument("--adb-timeout", type=float, default=15.0, help="单条 ADB 超时秒数")
    parser.add_argument(
        "--repository-root",
        default=str(Path(__file__).resolve().parents[2]),
        help="用于拒绝仓库内证据输出的仓库根目录",
    )
    if output:
        parser.add_argument("--output", help="仓库外证据目录；省略时使用系统临时目录")
    parser.add_argument("--json", action="store_true", help="输出单行紧凑 JSON")


def _add_selector_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--by", required=True, choices=_selector_choices())
    parser.add_argument("--value", required=True)
    parser.add_argument("--index", type=int)


def _add_wait_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--timeout", type=int, default=5000, help="等待超时毫秒")
    parser.add_argument("--interval", type=int, default=250, help="轮询间隔毫秒")


def _selector_choices() -> list[str]:
    return ["tag", "resource-id", "content-desc", "text-exact", "text-contains"]


def _emit_result(payload: dict[str, Any], as_json: bool, is_error: bool = False) -> None:
    stream = sys.stderr if is_error else sys.stdout
    if as_json:
        print(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), file=stream)
        return

    status = payload.get("status", "FAIL")
    action = payload.get("action") or payload.get("category") or "device-control"
    serial = payload.get("serial")
    summary = f"[{status}] {action}"
    if serial:
        summary += f" | device={serial}"
    if payload.get("message"):
        summary += f" | {payload['message']}"
    print(summary, file=stream)

    evidence = payload.get("evidence") or payload.get("jsonPath") or payload.get("observation")
    if evidence:
        print(f"evidence={evidence}", file=stream)

    if status == "PASS" and payload.get("node"):
        node = payload["node"]
        print(f"node={node.get('center')} bounds={node.get('bounds')}", file=stream)
    elif status == "FAIL" and payload.get("details"):
        details = json.dumps(payload["details"], ensure_ascii=False, separators=(",", ":"))
        print(f"details={details[:1200]}", file=stream)


if __name__ == "__main__":
    raise SystemExit(main())
