#!/usr/bin/env python3
import sys
import os
import subprocess
import argparse

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

def main():
    parser = argparse.ArgumentParser(description="Silent ADB Screen Capturer API")
    parser.add_argument("-o", "--out", required=True, help="Absolute destination path for saved PNG file")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    # 1. 寻找设备
    try:
        device = get_target_device(args.device)
    except SystemExit:
        sys.exit(1)
        
    # 2. 执行截图
    try:
        # 执行手机内截图
        subprocess.check_call(["adb", "-s", device, "shell", "screencap", "-p", "/sdcard/screen.png"])
        # 拉取到本地
        subprocess.check_call(["adb", "-s", device, "pull", "/sdcard/screen.png", args.out], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # 清理手机端临时文件
        subprocess.check_call(["adb", "-s", device, "shell", "rm", "/sdcard/screen.png"])
    except subprocess.CalledProcessError as e:
        sys.stderr.write(f"ERROR: ADB screenshot command failed. {str(e)}\n")
        sys.exit(1)

    # 3. 成功后只打印一行绝对路径
    print(os.path.abspath(args.out))

if __name__ == "__main__":
    main()
