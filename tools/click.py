#!/usr/bin/env python3
import argparse
import sys

from device.adb_client import AdbClient
from device.models import DeviceControlError


def main() -> int:
    parser = argparse.ArgumentParser(description="Silent ADB Input/Click API")
    parser.add_argument("coords", nargs="*", type=int, help="Click coordinates (x y)")
    parser.add_argument("-l", "--long-press", type=int, help="Long press duration in milliseconds")
    parser.add_argument("-s", "--swipe", nargs="+", type=int, help="Swipe coords: x1 y1 x2 y2 [duration]")
    parser.add_argument("-k", "--key", type=int, help="Send hardware keyevent code (e.g. 4 for BACK)")
    parser.add_argument("-t", "--text", help="Send text input to currently focused field")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    try:
        client = AdbClient()
        client.bind(args.device)

        if args.coords:
            if len(args.coords) != 2:
                parser.error("Click coordinates must be exactly 2 integers: x y")
            x, y = args.coords
            if args.long_press:
                client.shell(
                    "input",
                    "swipe",
                    str(x),
                    str(y),
                    str(x),
                    str(y),
                    str(args.long_press),
                )
                print(f"OK: Long-pressed ({x}, {y}) for {args.long_press}ms")
            else:
                client.tap(x, y)
                print(f"OK: Tapped ({x}, {y})")
            return 0

        if args.swipe:
            if len(args.swipe) not in {4, 5}:
                parser.error("Swipe parameters must be: x1 y1 x2 y2 [duration]")
            client.shell("input", "swipe", *[str(value) for value in args.swipe])
            duration = f" for {args.swipe[4]}ms" if len(args.swipe) == 5 else ""
            print(
                f"OK: Swiped from ({args.swipe[0]}, {args.swipe[1]}) "
                f"to ({args.swipe[2]}, {args.swipe[3]}){duration}"
            )
            return 0

        if args.key is not None:
            client.shell("input", "keyevent", str(args.key))
            print(f"OK: Keyevent {args.key} sent")
            return 0

        if args.text is not None:
            safe_text = args.text.replace(" ", "%s")
            client.shell("input", "text", safe_text)
            print("OK: Text input sent")
            return 0

        parser.error("No action specified. Must provide coords, --swipe, --key, or --text.")
        return 64
    except DeviceControlError as exc:
        sys.stderr.write(f"ERROR: {exc}\n")
        return exc.exit_code


if __name__ == "__main__":
    raise SystemExit(main())
