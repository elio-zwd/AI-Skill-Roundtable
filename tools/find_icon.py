#!/usr/bin/env python3
import sys
import os
import argparse
import subprocess

try:
    import cv2
    import numpy as np
except ImportError:
    sys.stderr.write("ERROR: OpenCV or NumPy is not installed.\n")
    sys.stderr.write("Please run 'pip install opencv-python numpy' to use visual matching features.\n")
    sys.exit(1)

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

def match_template_multiscale(screenshot_path, template_path, threshold=0.75):
    # 1. 读入图像
    img = cv2.imread(screenshot_path)
    template = cv2.imread(template_path)
    
    if img is None:
        raise ValueError(f"Failed to read screenshot: {screenshot_path}")
    if template is None:
        raise ValueError(f"Failed to read template: {template_path}")
        
    h_temp, w_temp = template.shape[:2]
    
    # 转灰色
    img_gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    template_gray = cv2.cvtColor(template, cv2.COLOR_BGR2GRAY)
    
    best_max_val = -1
    best_loc = None
    best_scale = 1.0
    
    # 2. 多尺度匹配，从 0.5 到 1.5 倍等比缩放搜索 (步长 0.05)
    for scale in np.linspace(0.5, 1.5, 21):
        resized_w = int(w_temp * scale)
        resized_h = int(h_temp * scale)
        
        # 边界检查
        if resized_w > img_gray.shape[1] or resized_h > img_gray.shape[0]:
            continue
            
        resized_template = cv2.resize(template_gray, (resized_w, resized_h), interpolation=cv2.INTER_AREA)
        
        res = cv2.matchTemplate(img_gray, resized_template, cv2.TM_CCOEFF_NORMED)
        _, max_val, _, max_loc = cv2.minMaxLoc(res)
        
        if max_val > best_max_val:
            best_max_val = max_val
            best_loc = max_loc
            best_scale = scale
            
    # 3. 结果验证
    if best_max_val >= threshold:
        best_w = int(w_temp * best_scale)
        best_h = int(h_temp * best_scale)
        center_x = best_loc[0] + best_w // 2
        center_y = best_loc[1] + best_h // 2
        return center_x, center_y, best_max_val
        
    return None

def main():
    parser = argparse.ArgumentParser(description="Multi-scale Image Template Matching for ADB Visual Positioning API")
    parser.add_argument("-t", "--template", required=True, help="Path to standard template PNG file")
    parser.add_argument("-s", "--screenshot", help="Local screenshot file path (if omitted, captures screen automatically)")
    parser.add_argument("--threshold", type=float, default=0.75, help="Matching score threshold (default: 0.75)")
    parser.add_argument("-d", "--device", help="Target ADB device ID")
    args = parser.parse_args()

    device = None
    temp_screenshot = None

    # 1. 自动截取屏幕
    if not args.screenshot:
        try:
            device = get_target_device(args.device)
            # 转储至本地临时位置
            import tempfile
            temp_fd, temp_screenshot = tempfile.mkstemp(suffix=".png")
            os.close(temp_fd)
            
            # 运行截图
            subprocess.check_call(["adb", "-s", device, "shell", "screencap", "-p", "/sdcard/match_temp.png"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.check_call(["adb", "-s", device, "pull", "/sdcard/match_temp.png", temp_screenshot], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            subprocess.check_call(["adb", "-s", device, "shell", "rm", "/sdcard/match_temp.png"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            
            screenshot_path = temp_screenshot
        except Exception as e:
            sys.stderr.write(f"ERROR: Automatic screenshot failed. {str(e)}\n")
            sys.exit(1)
    else:
        screenshot_path = args.screenshot

    # 2. 执行模板匹配
    try:
        result = match_template_multiscale(screenshot_path, args.template, args.threshold)
    except Exception as e:
        sys.stderr.write(f"ERROR: Image matching process encountered error. {str(e)}\n")
        if temp_screenshot and os.path.exists(temp_screenshot):
            os.remove(temp_screenshot)
        sys.exit(1)
        
    # 清理临时截图
    if temp_screenshot and os.path.exists(temp_screenshot):
        os.remove(temp_screenshot)

    # 3. 输出坐标
    if result:
        x, y, score = result
        # 仅输出中点坐标，以支持 click.py 直连
        print(f"{x} {y}")
    else:
        sys.stderr.write(f"ERROR: Template image not matched (maximum match score below {args.threshold}).\n")
        sys.exit(1)

if __name__ == "__main__":
    main()
