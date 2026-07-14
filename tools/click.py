#!/usr/bin/env python3
import sys
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
    parser = argparse.ArgumentParser(description="Silent ADB Input/Click API")
    parser.add_argument("coords", nargs="*", type=int, help="Click coordinates (x y)")
    parser.add_argument("-l", "--long-press", type=int, help="Long press duration in milliseconds")
    parser.add_argument("-s", "--swipe", nargs="+", type=int, help="Swipe coords: x1 y1 x2 y2 [duration]")
    parser.add_argument("-k", "--key", type=int, help="Send hardware keyevent code (e.g. 4 for BACK)")
    parser.add_argument("-t", "--text", help="Send text input to currently focused field")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    # 1. 寻找设备
    try:
        device = get_target_device(args.device)
    except SystemExit:
        sys.exit(1)

    # 2. 执行操作
    try:
        if args.coords:
            if len(args.coords) != 2:
                sys.stderr.write("ERROR: Click coordinates must be exactly 2 integers: x y\n")
                sys.exit(1)
            x, y = args.coords[0], args.coords[1]
            if args.long_press:
                # 用 swipe 模拟长按
                subprocess.check_call(["adb", "-s", device, "shell", "input", "swipe", str(x), str(y), str(x), str(y), str(args.long_press)])
                print(f"OK: Long-pressed ({x}, {y}) for {args.long_press}ms")
            else:
                subprocess.check_call(["adb", "-s", device, "shell", "input", "tap", str(x), str(y)])
                print(f"OK: Tapped ({x}, {y})")

        elif args.swipe:
            if len(args.swipe) < 4 or len(args.swipe) > 5:
                sys.stderr.write("ERROR: Swipe parameters must be: x1 y1 x2 y2 [duration]\n")
                sys.exit(1)
            cmd = ["adb", "-s", device, "shell", "input", "swipe"] + [str(p) for p in args.swipe]
            subprocess.check_call(cmd)
            duration_str = f" for {args.swipe[4]}ms" if len(args.swipe) == 5 else ""
            print(f"OK: Swiped from ({args.swipe[0]}, {args.swipe[1]}) to ({args.swipe[2]}, {args.swipe[3]}){duration_str}")

        elif args.key is not None:
            subprocess.check_call(["adb", "-s", device, "shell", "input", "keyevent", str(args.key)])
            print(f"OK: Keyevent {args.key} sent")

        elif args.text is not None:
            # 替换空格为 %s 并在 adb shell input text 中发送以支持空格输入
            safe_text = args.text.replace(" ", "%s")
            subprocess.check_call(["adb", "-s", device, "shell", "input", "text", safe_text])
            print("OK: Text input sent")
        else:
            sys.stderr.write("ERROR: No action specified. Must provide coords, --swipe, --key, or --text.\n")
            sys.exit(1)
            
    except subprocess.CalledProcessError as e:
        sys.stderr.write(f"ERROR: ADB input command failed. {str(e)}\n")
        sys.exit(1)

if __name__ == "__main__":
    main()
