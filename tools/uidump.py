#!/usr/bin/env python3
import argparse
import os
import re
import sys
import tempfile
import xml.etree.ElementTree as ET

from device.adb_client import AdbClient
from device.models import DeviceControlError


SYNONYMS = {
    "菜单": ["menu", "抽屉", "drawer", "navigation", "nav"],
    "设置": ["setting", "setup", "配置", "config", "齿轮"],
    "添加": ["add", "plus", "加号", "新建"],
    "保存": ["save", "存储", "确认"],
}


def parse_bounds(bounds_str):
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if not match:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    if x2 <= x1 or y2 <= y1:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_node_by_text(node, search_text):
    candidates = [search_text.casefold()]
    for key, aliases in SYNONYMS.items():
        if search_text.casefold() in key.casefold() or key.casefold() in search_text.casefold():
            candidates.extend(alias.casefold() for alias in aliases)

    def is_match(value):
        folded = (value or "").casefold()
        return any(candidate in folded for candidate in candidates)

    text = node.get("text", "")
    description = node.get("content-desc", "")
    if is_match(text) or is_match(description):
        coordinates = parse_bounds(node.get("bounds", ""))
        if coordinates:
            return coordinates, text or description

    for child in node:
        result = find_node_by_text(child, search_text)
        if result:
            return result
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description="Silent ADB UI Automator Tree Dumper & Node Locator API")
    parser.add_argument("-o", "--out", help="Destination path for XML hierarchy tree")
    parser.add_argument("-f", "--find", help="Text/content-desc to find; returns 'x y'")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    temporary_path = None
    try:
        client = AdbClient()
        client.bind(args.device)
        xml_text = client.dump_ui_xml()

        if args.out:
            local_xml = os.path.abspath(args.out)
        elif args.find:
            local_xml = None
        else:
            file_descriptor, temporary_path = tempfile.mkstemp(suffix=".xml")
            os.close(file_descriptor)
            local_xml = temporary_path

        if local_xml:
            os.makedirs(os.path.dirname(local_xml) or ".", exist_ok=True)
            with open(local_xml, "w", encoding="utf-8") as stream:
                stream.write(xml_text)

        if args.find:
            root = ET.fromstring(xml_text)
            match = find_node_by_text(root, args.find)
            if not match:
                sys.stderr.write(
                    f"ERROR: Widget with text/desc containing '{args.find}' not found on screen.\n"
                )
                return 1
            coordinates, _ = match
            print(f"{coordinates[0]} {coordinates[1]}")
            return 0

        print(os.path.abspath(local_xml))
        return 0
    except DeviceControlError as exc:
        sys.stderr.write(f"ERROR: {exc}\n")
        return exc.exit_code
    except (OSError, ET.ParseError) as exc:
        sys.stderr.write(f"ERROR: Failed to save or parse UI XML. {exc}\n")
        return 1
    finally:
        if temporary_path and not os.path.exists(temporary_path):
            try:
                os.remove(temporary_path)
            except OSError:
                pass


if __name__ == "__main__":
    raise SystemExit(main())
