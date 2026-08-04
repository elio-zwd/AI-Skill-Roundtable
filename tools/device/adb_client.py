from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path
from typing import Any, Callable, Sequence

from .models import DeviceControlError, DeviceProfile, DeviceRecord, ForegroundInfo, ScreenInfo

Runner = Callable[..., subprocess.CompletedProcess[Any]]


class AdbClient:
    """绑定单一 Serial 的 ADB 客户端。"""

    def __init__(
        self,
        *,
        adb_path: str = "adb",
        serial: str | None = None,
        timeout_seconds: float = 15.0,
        runner: Runner = subprocess.run,
    ) -> None:
        self.adb_path = adb_path
        self.serial = serial
        self.timeout_seconds = timeout_seconds
        self._runner = runner

    def list_devices(self) -> list[DeviceRecord]:
        result = self._run(["devices", "-l"], use_serial=False)
        return parse_adb_devices(result.stdout)

    def bind(self, explicit_serial: str | None = None, profile_serial: str | None = None) -> str:
        serial = resolve_device_serial(self.list_devices(), explicit_serial, profile_serial)
        self.serial = serial
        return serial

    def shell(self, *arguments: str, timeout_seconds: float | None = None) -> str:
        return self._run(
            ["shell", *arguments],
            use_serial=True,
            timeout_seconds=timeout_seconds,
        ).stdout.strip()

    def exec_out(self, *arguments: str, timeout_seconds: float | None = None) -> bytes:
        return self._run(
            ["exec-out", *arguments],
            use_serial=True,
            binary=True,
            timeout_seconds=timeout_seconds,
        ).stdout

    def tap(self, x: int, y: int) -> None:
        self.shell("input", "tap", str(x), str(y))

    def force_stop(self, package: str) -> None:
        self.shell("am", "force-stop", package)

    def launch(self, package: str, activity: str | None = None) -> str:
        if activity:
            component = f"{package}/{activity}"
            return self.shell("am", "start", "-W", "-n", component, timeout_seconds=30)
        return self.shell(
            "monkey",
            "-p",
            package,
            "-c",
            "android.intent.category.LAUNCHER",
            "1",
            timeout_seconds=30,
        )

    def screen_info(self) -> ScreenInfo:
        width, height = parse_wm_size(self.shell("wm", "size"))
        density = parse_wm_density(self.shell("wm", "density"))
        api = int(self.shell("getprop", "ro.build.version.sdk"))
        orientation = self._orientation(width, height)
        return ScreenInfo(
            width=width,
            height=height,
            density=density,
            api=api,
            orientation=orientation,
        )

    def foreground_info(self) -> ForegroundInfo:
        candidates = [
            self.shell("dumpsys", "activity", "activities"),
            self.shell("dumpsys", "window", "windows"),
        ]
        for text in candidates:
            parsed = parse_foreground_component(text)
            if parsed.package:
                return parsed
        return ForegroundInfo(package=None, activity=None)

    def dump_ui_xml(self) -> str:
        remote_path = "/sdcard/jianyu-window-dump.xml"
        try:
            self.shell("uiautomator", "dump", remote_path, timeout_seconds=20)
            xml_bytes = self.exec_out("cat", remote_path, timeout_seconds=20)
            text = xml_bytes.decode("utf-8", errors="replace").strip()
            if not text:
                raise DeviceControlError(
                    "UI Automator 返回空 XML。",
                    category="UI_DUMP_EMPTY",
                    exit_code=1,
                )
            return text
        finally:
            try:
                self.shell("rm", "-f", remote_path)
            except DeviceControlError:
                pass

    def screenshot_png(self) -> bytes:
        data = self.exec_out("screencap", "-p", timeout_seconds=20)
        if not data.startswith(b"\x89PNG"):
            raise DeviceControlError(
                "ADB 截图未返回有效 PNG。",
                category="SCREENSHOT_INVALID",
                exit_code=1,
            )
        return data

    def _orientation(self, width: int, height: int) -> str:
        try:
            output = self.shell("dumpsys", "input")
            match = re.search(r"SurfaceOrientation:\s*(\d+)", output)
            if match:
                return "landscape" if int(match.group(1)) % 2 else "portrait"
        except DeviceControlError:
            pass
        return "portrait" if height >= width else "landscape"

    def _run(
        self,
        arguments: Sequence[str],
        *,
        use_serial: bool,
        binary: bool = False,
        timeout_seconds: float | None = None,
    ) -> subprocess.CompletedProcess[Any]:
        command = [self.adb_path]
        if use_serial:
            if not self.serial:
                raise DeviceControlError(
                    "ADB 操作尚未绑定目标设备。",
                    category="DEVICE_NOT_BOUND",
                    exit_code=69,
                )
            command.extend(["-s", self.serial])
        command.extend(arguments)

        try:
            result = self._runner(
                command,
                capture_output=True,
                text=not binary,
                timeout=timeout_seconds or self.timeout_seconds,
                check=False,
            )
        except FileNotFoundError as exc:
            raise DeviceControlError(
                f"未找到 ADB：{self.adb_path}",
                category="ADB_NOT_FOUND",
                exit_code=69,
            ) from exc
        except subprocess.TimeoutExpired as exc:
            raise DeviceControlError(
                f"ADB 命令超时：{' '.join(command[:6])}",
                category="ADB_TIMEOUT",
                exit_code=1,
            ) from exc

        if result.returncode != 0:
            stderr = _bounded_text(result.stderr, 1200)
            stdout = _bounded_text(result.stdout, 600)
            raise DeviceControlError(
                f"ADB 命令失败，退出码 {result.returncode}。",
                category="ADB_COMMAND_FAILED",
                exit_code=1,
                details={"stderr": stderr, "stdout": stdout},
            )
        return result


def load_device_profile(path: str | Path | None) -> DeviceProfile | None:
    if path is None:
        return None
    profile_path = Path(path).expanduser()
    try:
        data = json.loads(profile_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise DeviceControlError(
            f"无法读取设备 Profile：{profile_path}",
            category="PROFILE_INVALID",
            exit_code=64,
        ) from exc
    if not isinstance(data, dict):
        raise DeviceControlError(
            "设备 Profile 顶层必须是 JSON 对象。",
            category="PROFILE_INVALID",
            exit_code=64,
        )
    return DeviceProfile.from_dict(data, profile_path)


def parse_adb_devices(output: str) -> list[DeviceRecord]:
    records: list[DeviceRecord] = []
    for raw_line in output.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("List of devices") or line.startswith("*"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        details: dict[str, str] = {}
        for token in parts[2:]:
            if ":" in token:
                key, value = token.split(":", 1)
                details[key] = value
        records.append(DeviceRecord(serial=serial, state=state, details=details))
    return records


def resolve_device_serial(
    records: Sequence[DeviceRecord],
    explicit_serial: str | None,
    profile_serial: str | None,
) -> str:
    requested = (explicit_serial or profile_serial or "").strip() or None
    by_serial = {record.serial: record for record in records}

    if requested:
        record = by_serial.get(requested)
        if record is None:
            raise DeviceControlError(
                f"指定设备不存在：{requested}",
                category="DEVICE_NOT_FOUND",
                exit_code=69,
                details={"available": sorted(by_serial)},
            )
        if record.state != "device":
            raise DeviceControlError(
                f"指定设备不可用：{requested} state={record.state}",
                category="DEVICE_NOT_READY",
                exit_code=69,
            )
        return requested

    online = [record.serial for record in records if record.state == "device"]
    if not online:
        states = {record.serial: record.state for record in records}
        raise DeviceControlError(
            "没有在线 Android 设备。",
            category="NO_ONLINE_DEVICE",
            exit_code=69,
            details={"states": states},
        )
    if len(online) > 1:
        raise DeviceControlError(
            "检测到多台在线设备，必须显式传入 --device 或 --profile。",
            category="AMBIGUOUS_DEVICE",
            exit_code=69,
            details={"online": sorted(online)},
        )
    return online[0]


def parse_wm_size(output: str) -> tuple[int, int]:
    matches = re.findall(r"(?:Override|Physical) size:\s*(\d+)x(\d+)", output)
    if not matches:
        raise DeviceControlError("无法解析 wm size。", category="SCREEN_INFO_INVALID")
    width, height = matches[-1]
    return int(width), int(height)


def parse_wm_density(output: str) -> int:
    matches = re.findall(r"(?:Override|Physical) density:\s*(\d+)", output)
    if not matches:
        raise DeviceControlError("无法解析 wm density。", category="SCREEN_INFO_INVALID")
    return int(matches[-1])


def parse_foreground_component(output: str) -> ForegroundInfo:
    patterns = [
        r"mResumedActivity[^\n]*?\s([A-Za-z0-9_.$]+)/(\.?[A-Za-z0-9_.$]+)",
        r"mCurrentFocus[^\n]*?\s([A-Za-z0-9_.$]+)/(\.?[A-Za-z0-9_.$]+)",
        r"mFocusedApp[^\n]*?\s([A-Za-z0-9_.$]+)/(\.?[A-Za-z0-9_.$]+)",
    ]
    for pattern in patterns:
        match = re.search(pattern, output)
        if match:
            package, activity = match.groups()
            if activity.startswith("."):
                activity = package + activity
            return ForegroundInfo(package=package, activity=activity)
    return ForegroundInfo(package=None, activity=None)


def profile_mismatches(profile: DeviceProfile | None, screen: ScreenInfo) -> list[str]:
    if profile is None:
        return []
    mismatches: list[str] = []
    if profile.expected_size and profile.expected_size != screen.size:
        mismatches.append(f"size expected={profile.expected_size} actual={screen.size}")
    if profile.expected_density is not None and profile.expected_density != screen.density:
        mismatches.append(
            f"density expected={profile.expected_density} actual={screen.density}"
        )
    if profile.expected_api is not None and profile.expected_api != screen.api:
        mismatches.append(f"api expected={profile.expected_api} actual={screen.api}")
    if profile.expected_orientation and profile.expected_orientation != screen.orientation:
        mismatches.append(
            "orientation "
            f"expected={profile.expected_orientation} actual={screen.orientation}"
        )
    return mismatches


def _bounded_text(value: Any, max_chars: int) -> str:
    if value is None:
        return ""
    if isinstance(value, bytes):
        text = value.decode("utf-8", errors="replace")
    else:
        text = str(value)
    text = text.strip()
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 20] + "...[truncated]"
