#!/usr/bin/env python3
import sys
import os
import subprocess
import argparse
import tempfile
import re
import xml.etree.ElementTree as ET

def get_target_device(device_arg=None):
    if device_arg:
        return device_arg
    try:
        output = subprocess.check_output(["adb", "devices"], text=True)
    except Exception:
        sys.stderr.write("ERROR: ADB executable not found in system PATH.\n")
        sys.exit(1)
        
    lines = output.strip().split("\n")[1:]
    devices = []
    for line in lines:
        if not line.strip():
            continue
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
            
    if not devices:
        sys.stderr.write("ERROR: No active Android devices/emulators detected via ADB.\n")
        sys.exit(1)
    return devices[0]

def parse_bounds(bounds_str):
    pattern = r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]"
    match = re.match(pattern, bounds_str)
    if not match:
        return None
    x1, y1, x2, y2 = map(int, match.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2

def find_node_by_text(node, search_text):
    text = node.get("text", "")
    desc = node.get("content-desc", "")
    
    # 模糊包含匹配 (大小写不敏感)
    if search_text.lower() in text.lower() or search_text.lower() in desc.lower():
        bounds = node.get("bounds", "")
        coords = parse_bounds(bounds)
        if coords:
            return coords, text or desc

    for child in node:
        result = find_node_by_text(child, search_text)
        if result:
            return result
    return None

def main():
    parser = argparse.ArgumentParser(description="Silent ADB UI Automator Tree Dumper & Node Locator API")
    parser.add_argument("-o", "--out", help="Destination path for XML hierarchy tree")
    parser.add_argument("-f", "--find", help="Text/content-desc of the widget to find. Returns middle coordinate 'x y'")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    # 1. 寻找设备
    try:
        device = get_target_device(args.device)
    except SystemExit:
        sys.exit(1)

    # 2. 导出 XML 到手机，并拉取到本地
    temp_xml_fd, temp_xml_path = tempfile.mkstemp(suffix=".xml")
    os.close(temp_xml_fd)
    
    local_xml = args.out if args.out else temp_xml_path
    
    try:
        # 执行转储
        subprocess.check_call(["adb", "-s", device, "shell", "uiautomator", "dump", "/sdcard/window_dump.xml"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # 拉取文件
        subprocess.check_call(["adb", "-s", device, "pull", "/sdcard/window_dump.xml", local_xml], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # 清除手机临时文件
        subprocess.check_call(["adb", "-s", device, "shell", "rm", "/sdcard/window_dump.xml"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except subprocess.CalledProcessError as e:
        sys.stderr.write(f"ERROR: ADB uiautomator dump command failed. {str(e)}\n")
        # 清理临时文件
        if os.path.exists(temp_xml_path):
            os.remove(temp_xml_path)
        sys.exit(1)

    # 3. 如果是查找模式，在本地解析 XML 并提取 bounds 中点
    if args.find:
        try:
            tree = ET.parse(local_xml)
            root = tree.getroot()
            match_res = find_node_by_text(root, args.find)
            
            # 清理临时文件
            if not args.out and os.path.exists(temp_xml_path):
                os.remove(temp_xml_path)
                
            if match_res:
                coords, matched_val = match_res
                # 仅打印 X Y，方便 AI 直接输入 click
                print(f"{coords[0]} {coords[1]}")
            else:
                sys.stderr.write(f"ERROR: Widget with text/desc containing '{args.find}' not found on screen.\n")
                sys.exit(1)
        except Exception as e:
            sys.stderr.write(f"ERROR: Failed to parse XML or search node. {str(e)}\n")
            if not args.out and os.path.exists(temp_xml_path):
                os.remove(temp_xml_path)
            sys.exit(1)
    else:
        # 非查找模式仅打印保存路径
        print(os.path.abspath(local_xml))

if __name__ == "__main__":
    main()
