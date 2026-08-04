from __future__ import annotations

from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any


class DeviceControlError(RuntimeError):
    """可向 CLI 映射退出码和有限详情的设备控制错误。"""

    def __init__(
        self,
        message: str,
        *,
        category: str = "DEVICE_CONTROL",
        exit_code: int = 1,
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.category = category
        self.exit_code = exit_code
        self.details = details or {}

    def to_dict(self) -> dict[str, Any]:
        return {
            "status": "FAIL",
            "category": self.category,
            "message": str(self),
            "details": self.details,
        }


@dataclass(frozen=True)
class DeviceRecord:
    serial: str
    state: str
    details: dict[str, str] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class DeviceProfile:
    name: str
    serial: str | None = None
    package: str | None = None
    expected_size: str | None = None
    expected_density: int | None = None
    expected_api: int | None = None
    expected_orientation: str | None = None
    source_path: str | None = None

    @classmethod
    def from_dict(cls, data: dict[str, Any], source_path: Path | None = None) -> "DeviceProfile":
        expected = data.get("expected") or {}
        return cls(
            name=str(data.get("name") or "unnamed"),
            serial=_optional_string(data.get("serial")),
            package=_optional_string(data.get("package")),
            expected_size=_optional_string(expected.get("size")),
            expected_density=_optional_int(expected.get("density")),
            expected_api=_optional_int(expected.get("api")),
            expected_orientation=_optional_string(expected.get("orientation")),
            source_path=str(source_path.resolve()) if source_path else None,
        )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class ScreenInfo:
    width: int
    height: int
    density: int
    api: int
    orientation: str

    @property
    def size(self) -> str:
        return f"{self.width}x{self.height}"

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["size"] = self.size
        return payload


@dataclass(frozen=True)
class ForegroundInfo:
    package: str | None
    activity: str | None

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class Selector:
    by: str
    value: str
    index: int | None = None

    def normalized_by(self) -> str:
        return "resource-id" if self.by == "tag" else self.by

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(frozen=True)
class UiNode:
    resource_id: str
    text: str
    content_description: str
    class_name: str
    clickable: bool
    enabled: bool
    selected: bool
    checked: bool
    bounds: tuple[int, int, int, int]

    @property
    def center(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return ((left + right) // 2, (top + bottom) // 2)

    def to_dict(self) -> dict[str, Any]:
        return {
            "resourceId": self.resource_id,
            "text": self.text,
            "contentDescription": self.content_description,
            "className": self.class_name,
            "clickable": self.clickable,
            "enabled": self.enabled,
            "selected": self.selected,
            "checked": self.checked,
            "bounds": list(self.bounds),
            "center": list(self.center),
        }


@dataclass(frozen=True)
class Observation:
    serial: str
    foreground: ForegroundInfo
    screen: ScreenInfo
    screenshot_path: str
    screenshot_sha256: str
    xml_path: str
    node_count: int
    visible_texts: tuple[str, ...]
    json_path: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "schemaVersion": 1,
            "serial": self.serial,
            "foregroundPackage": self.foreground.package,
            "activity": self.foreground.activity,
            "screen": self.screen.to_dict(),
            "screenshotPath": self.screenshot_path,
            "screenshotSha256": self.screenshot_sha256,
            "xmlPath": self.xml_path,
            "nodeCount": self.node_count,
            "visibleTexts": list(self.visible_texts),
            "jsonPath": self.json_path,
        }


def _optional_string(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _optional_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    return int(value)
