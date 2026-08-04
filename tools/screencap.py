#!/usr/bin/env python3
import argparse
import os
import sys

from device.adb_client import AdbClient
from device.models import DeviceControlError


def main() -> int:
    parser = argparse.ArgumentParser(description="Silent ADB Screen Capturer API")
    parser.add_argument("-o", "--out", required=True, help="Destination path for saved PNG file")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    try:
        client = AdbClient()
        client.bind(args.device)
        output_path = os.path.abspath(args.out)
        os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
        with open(output_path, "wb") as stream:
            stream.write(client.screenshot_png())
        print(output_path)
        return 0
    except DeviceControlError as exc:
        sys.stderr.write(f"ERROR: {exc}\n")
        return exc.exit_code
    except OSError as exc:
        sys.stderr.write(f"ERROR: Failed to save screenshot. {exc}\n")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
