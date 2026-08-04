from __future__ import annotations

import re
import time
import xml.etree.ElementTree as ET
from collections.abc import Callable, Sequence

from .models import DeviceControlError, Selector, UiNode

_BOUNDS_PATTERN = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
_ALLOWED_SELECTORS = {
    "tag",
    "resource-id",
    "content-desc",
    "text-exact",
    "text-contains",
}


def parse_ui_nodes(xml_text: str) -> list[UiNode]:
    if not xml_text.strip():
        raise DeviceControlError(
            "UI XML 为空。",
            category="UI_XML_EMPTY",
            exit_code=1,
        )
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as exc:
        raise DeviceControlError(
            "UI XML 无法解析。",
            category="UI_XML_INVALID",
            exit_code=1,
        ) from exc

    nodes: list[UiNode] = []
    for element in root.iter("node"):
        bounds = parse_bounds(element.attrib.get("bounds", ""))
        if bounds is None:
            continue
        nodes.append(
            UiNode(
                resource_id=element.attrib.get("resource-id", ""),
                text=element.attrib.get("text", ""),
                content_description=element.attrib.get("content-desc", ""),
                class_name=element.attrib.get("class", ""),
                clickable=_parse_bool(element.attrib.get("clickable")),
                enabled=_parse_bool(element.attrib.get("enabled"), default=True),
                selected=_parse_bool(element.attrib.get("selected")),
                checked=_parse_bool(element.attrib.get("checked")),
                bounds=bounds,
            )
        )
    if not nodes:
        raise DeviceControlError(
            "UI XML 中没有可定位节点。",
            category="UI_NODES_EMPTY",
            exit_code=1,
        )
    return nodes


def parse_bounds(value: str) -> tuple[int, int, int, int] | None:
    match = _BOUNDS_PATTERN.fullmatch(value.strip())
    if not match:
        return None
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        return None
    return left, top, right, bottom


def find_nodes(nodes: Sequence[UiNode], selector: Selector) -> list[UiNode]:
    by = selector.normalized_by()
    if by not in _ALLOWED_SELECTORS - {"tag"}:
        raise DeviceControlError(
            f"不支持的选择器：{selector.by}",
            category="SELECTOR_INVALID",
            exit_code=64,
        )
    value = selector.value.strip()
    if not value:
        raise DeviceControlError(
            "选择器值不能为空。",
            category="SELECTOR_INVALID",
            exit_code=64,
        )

    if by == "resource-id":
        return [node for node in nodes if _resource_id_matches(node.resource_id, value)]
    if by == "content-desc":
        return [node for node in nodes if node.content_description == value]
    if by == "text-exact":
        return [node for node in nodes if node.text == value]
    if by == "text-contains":
        folded = value.casefold()
        return [node for node in nodes if folded in node.text.casefold()]
    return []


def select_unique_node(nodes: Sequence[UiNode], selector: Selector) -> UiNode:
    matches = find_nodes(nodes, selector)
    if not matches:
        raise DeviceControlError(
            f"未找到控件：{selector.by}={selector.value}",
            category="SELECTOR_NOT_FOUND",
            exit_code=1,
        )
    if selector.index is not None:
        if selector.index < 0 or selector.index >= len(matches):
            raise DeviceControlError(
                f"选择器 index 越界：{selector.index}，候选数={len(matches)}",
                category="SELECTOR_INDEX_INVALID",
                exit_code=64,
            )
        return matches[selector.index]
    if len(matches) > 1:
        raise DeviceControlError(
            f"选择器匹配到多个控件：{selector.by}={selector.value}",
            category="SELECTOR_AMBIGUOUS",
            exit_code=1,
            details={
                "matchCount": len(matches),
                "candidates": [node.to_dict() for node in matches[:5]],
            },
        )
    return matches[0]


def wait_for_selector(
    xml_supplier: Callable[[], str],
    selector: Selector,
    *,
    timeout_seconds: float,
    interval_seconds: float = 0.25,
    sleeper: Callable[[float], None] = time.sleep,
) -> UiNode:
    deadline = time.monotonic() + timeout_seconds
    last_error: DeviceControlError | None = None
    while True:
        try:
            return select_unique_node(parse_ui_nodes(xml_supplier()), selector)
        except DeviceControlError as exc:
            if exc.category not in {
                "SELECTOR_NOT_FOUND",
                "UI_XML_EMPTY",
                "UI_XML_INVALID",
                "UI_NODES_EMPTY",
            }:
                raise
            last_error = exc
        if time.monotonic() >= deadline:
            break
        sleeper(interval_seconds)
    raise DeviceControlError(
        f"等待控件超时：{selector.by}={selector.value}",
        category="SELECTOR_TIMEOUT",
        exit_code=1,
        details={"lastError": str(last_error) if last_error else None},
    )


def visible_texts(nodes: Sequence[UiNode], limit: int = 40) -> tuple[str, ...]:
    result: list[str] = []
    seen: set[str] = set()
    for node in nodes:
        text = node.text.strip()
        if not text or text in seen:
            continue
        seen.add(text)
        result.append(text[:160])
        if len(result) >= limit:
            break
    return tuple(result)


def _resource_id_matches(resource_id: str, value: str) -> bool:
    if resource_id == value:
        return True
    return resource_id.endswith(f"/{value}") or resource_id.endswith(f":id/{value}")


def _parse_bool(value: str | None, default: bool = False) -> bool:
    if value is None or value == "":
        return default
    return value.lower() == "true"
