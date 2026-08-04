#!/usr/bin/env python3
import argparse
import shutil
import sys
import time

from device.adb_client import AdbClient
from device.models import DeviceControlError
from device.selectors import parse_ui_nodes


def main() -> int:
    parser = argparse.ArgumentParser(description="ADB verbose diagnostics utility")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    print("==================================================")
    print("      ADB Verbose System Diagnostics Utility      ")
    print("==================================================")

    adb_path = shutil.which("adb")
    print(f"Local ADB Executable Path: {adb_path or 'NOT FOUND IN PATH'}")
    if not adb_path:
        return 69

    try:
        client = AdbClient(adb_path=adb_path)
        records = client.list_devices()
        print("\nDetected devices:")
        for record in records:
            print(f"- {record.serial}: {record.state} {record.details}")
        serial = client.bind(args.device)
        print(f"\nSelected target device: {serial}")

        started = time.monotonic()
        screen = client.screen_info()
        foreground = client.foreground_info()
        xml_text = client.dump_ui_xml()
        nodes = parse_ui_nodes(xml_text)
        elapsed = (time.monotonic() - started) * 1000

        print("\nScreen:")
        print(f"- size: {screen.size}")
        print(f"- density: {screen.density}")
        print(f"- api: {screen.api}")
        print(f"- orientation: {screen.orientation}")
        print("\nForeground:")
        print(f"- package: {foreground.package or '<unknown>'}")
        print(f"- activity: {foreground.activity or '<unknown>'}")
        print("\nUI Automator:")
        print(f"- nodes: {len(nodes)}")
        print(f"- elapsedMs: {elapsed:.2f}")
        print("\n==================================================")
        print("          Diagnostics Scan Complete               ")
        print("==================================================")
        return 0
    except DeviceControlError as exc:
        sys.stderr.write(f"ERROR: {exc}\n")
        if exc.details:
            sys.stderr.write(f"DETAILS: {exc.details}\n")
        return exc.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
