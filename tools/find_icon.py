#!/usr/bin/env python3
import argparse
import os
import sys
import tempfile

try:
    import cv2
    import numpy as np
except ImportError:
    sys.stderr.write("ERROR: OpenCV or NumPy is not installed.\n")
    sys.stderr.write("Please run 'pip install opencv-python numpy' to use visual matching features.\n")
    raise SystemExit(1)

from device.adb_client import AdbClient
from device.models import DeviceControlError


def match_template_multiscale(screenshot_path, template_path, threshold=0.75):
    image = cv2.imread(screenshot_path)
    template = cv2.imread(template_path)
    if image is None:
        raise ValueError(f"Failed to read screenshot: {screenshot_path}")
    if template is None:
        raise ValueError(f"Failed to read template: {template_path}")

    template_height, template_width = template.shape[:2]
    image_gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    template_gray = cv2.cvtColor(template, cv2.COLOR_BGR2GRAY)

    best_score = -1.0
    best_location = None
    best_scale = 1.0
    for scale in np.linspace(0.5, 1.5, 21):
        width = int(template_width * scale)
        height = int(template_height * scale)
        if width <= 0 or height <= 0:
            continue
        if width > image_gray.shape[1] or height > image_gray.shape[0]:
            continue
        resized = cv2.resize(template_gray, (width, height), interpolation=cv2.INTER_AREA)
        result = cv2.matchTemplate(image_gray, resized, cv2.TM_CCOEFF_NORMED)
        _, score, _, location = cv2.minMaxLoc(result)
        if score > best_score:
            best_score = float(score)
            best_location = location
            best_scale = float(scale)

    if best_location is None or best_score < threshold:
        return None
    width = int(template_width * best_scale)
    height = int(template_height * best_scale)
    return (
        best_location[0] + width // 2,
        best_location[1] + height // 2,
        best_score,
        best_scale,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Multi-scale Image Template Matching for ADB")
    parser.add_argument("-t", "--template", required=True, help="Path to template PNG")
    parser.add_argument("-s", "--screenshot", help="Existing local screenshot")
    parser.add_argument("--threshold", type=float, default=0.75)
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    parser.add_argument("--verbose-result", action="store_true", help="Also print score and scale")
    args = parser.parse_args()

    temporary_screenshot = None
    try:
        screenshot_path = args.screenshot
        if not screenshot_path:
            client = AdbClient()
            client.bind(args.device)
            file_descriptor, temporary_screenshot = tempfile.mkstemp(suffix=".png")
            os.close(file_descriptor)
            with open(temporary_screenshot, "wb") as stream:
                stream.write(client.screenshot_png())
            screenshot_path = temporary_screenshot

        result = match_template_multiscale(
            screenshot_path,
            args.template,
            args.threshold,
        )
        if result is None:
            sys.stderr.write(
                f"ERROR: Template image not matched (score below {args.threshold}).\n"
            )
            return 1
        x, y, score, scale = result
        if args.verbose_result:
            print(f"{x} {y} score={score:.4f} scale={scale:.2f}")
        else:
            print(f"{x} {y}")
        return 0
    except (DeviceControlError, ValueError, OSError) as exc:
        sys.stderr.write(f"ERROR: {exc}\n")
        return exc.exit_code if isinstance(exc, DeviceControlError) else 1
    finally:
        if temporary_screenshot and os.path.exists(temporary_screenshot):
            os.remove(temporary_screenshot)


if __name__ == "__main__":
    raise SystemExit(main())
