#!/usr/bin/env python3
import sys
import subprocess
import time
import shutil

def run_cmd_verbose(args_list):
    print(f"\n>> Executing command: {' '.join(args_list)}")
    start = time.time()
    try:
        res = subprocess.run(args_list, text=True, capture_output=True, check=True)
        elapsed = (time.time() - start) * 1000
        print(f"Status: SUCCESS (took {elapsed:.2f}ms)")
        if res.stdout:
            print("--- Standard Output ---")
            print(res.stdout.strip())
        if res.stderr:
            print("--- Standard Error ---")
            print(res.stderr.strip())
        return res.stdout.strip()
    except subprocess.CalledProcessError as e:
        elapsed = (time.time() - start) * 1000
        print(f"Status: FAILED with exit code {e.returncode} (took {elapsed:.2f}ms)")
        print("--- Standard Output ---")
        print(e.stdout.strip() if e.stdout else "<empty>")
        print("--- Standard Error ---")
        print(e.stderr.strip() if e.stderr else "<empty>")
        return None

def main():
    print("==================================================")
    print("      ADB Verbose System Diagnostics Utility      ")
    print("==================================================")

    # 1. 寻找本地 adb 可执行程序
    adb_path = shutil.whoami = shutil.who_is = shutil.which("adb")
    print(f"Local ADB Executable Path: {adb_path or 'NOT FOUND IN PATH'}")
    if not adb_path:
        print("\nERROR: Please install ADB and add it to your system Environment Variables (PATH).")
        sys.exit(1)

    # 2. 获取 ADB 版本
    run_cmd_verbose(["adb", "version"])

    # 3. 罗列设备
    devices_out = run_cmd_verbose(["adb", "devices", "-l"])
    if not devices_out:
        print("\nERROR: Failed to run 'adb devices'.")
        sys.exit(1)
        
    lines = devices_out.split("\n")[1:]
    active_devices = [line.split()[0] for line in lines if line.strip() and "device" in line]
    if not active_devices:
        print("\nWARNING: No active Android devices/emulators online. Please start your emulator.")
        sys.exit(0)
        
    target_device = active_devices[0]
    print(f"\nAuto-selected target device: {target_device}")

    # 4. 打印屏幕参数
    run_cmd_verbose(["adb", "-s", target_device, "shell", "wm", "size"])
    run_cmd_verbose(["adb", "-s", target_device, "shell", "wm", "density"])

    # 5. 打印顶层 Activity
    print("\nDetecting top screen activity...")
    run_cmd_verbose(["adb", "-s", target_device, "shell", "dumpsys", "window", "displays", "|", "grep", "-E", "'mCurrentFocus|mFocusedApp'"])

    # 6. 进行 UI Dump 测试，输出耗时
    print("\nRunning test UI Automator hierarchy dump...")
    run_cmd_verbose(["adb", "-s", target_device, "shell", "uiautomator", "dump", "/sdcard/diagnose_temp.xml"])
    run_cmd_verbose(["adb", "-s", target_device, "pull", "/sdcard/diagnose_temp.xml", "diagnose_temp.xml"])
    run_cmd_verbose(["adb", "-s", target_device, "shell", "rm", "/sdcard/diagnose_temp.xml"])
    
    import os
    if os.path.exists("diagnose_temp.xml"):
        size = os.path.getsize("diagnose_temp.xml")
        print(f"SUCCESS: Temp XML size retrieved locally: {size} bytes")
        os.remove("diagnose_temp.xml")
    else:
        print("FAILED: Local temp XML file was not found post pull.")

    print("\n==================================================")
    print("          Diagnostics Scan Complete               ")
    print("==================================================")

if __name__ == "__main__":
    main()
