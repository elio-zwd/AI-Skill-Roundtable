#!/usr/bin/env python3
import unittest
import subprocess
import os

class TestADBTools(unittest.TestCase):
    
    @classmethod
    def setUpClass(cls):
        # 确保 adb devices 在线，否则跳过所有测试
        try:
            output = subprocess.check_output(["adb", "devices"], text=True)
            lines = output.strip().split("\n")[1:]
            cls.has_device = any(line.strip() and "device" in line for line in lines)
        except Exception:
            cls.has_device = False

    def test_screencap_silent(self):
        if not self.has_device:
            self.skipTest("No ADB device online. Skipping.")
            
        out_png = os.path.abspath("tmp_debug_media/test_cap.png")
        # 确保没有遗留文件
        if os.path.exists(out_png):
            os.remove(out_png)
            
        cmd = ["python", "tools/screencap.py", "-o", out_png]
        res = subprocess.run(cmd, capture_output=True, text=True)
        
        # 检查退出码
        self.assertEqual(res.returncode, 0, f"Screencap failed with error: {res.stderr}")
        # 检查 stdout 仅输出一行文件路径
        self.assertEqual(res.stdout.strip(), out_png)
        # 检查物理文件存在且大小合理
        self.assertTrue(os.path.exists(out_png))
        self.assertGreater(os.path.getsize(out_png), 1024) # 大于 1KB
        
        # 清理
        os.remove(out_png)

    def test_uidump_silent(self):
        if not self.has_device:
            self.skipTest("No ADB device online. Skipping.")
            
        out_xml = os.path.abspath("tmp_debug_media/test_dump.xml")
        if os.path.exists(out_xml):
            os.remove(out_xml)
            
        cmd = ["python", "tools/uidump.py", "-o", out_xml]
        res = subprocess.run(cmd, capture_output=True, text=True)
        
        self.assertEqual(res.returncode, 0, f"Uidump failed with error: {res.stderr}")
        self.assertEqual(res.stdout.strip(), out_xml)
        self.assertTrue(os.path.exists(out_xml))
        
        os.remove(out_xml)

    def test_uidump_find_text(self):
        if not self.has_device:
            self.skipTest("No ADB device online. Skipping.")
            
        cmd = ["python", "tools/uidump.py", "--find", "智囊"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        
        if res.returncode == 0:
            coords = res.stdout.strip().split()
            self.assertEqual(len(coords), 2, f"Coordinate output should be two ints: {res.stdout}")
            x, y = int(coords[0]), int(coords[1])
            self.assertGreaterEqual(x, 0)
            self.assertGreaterEqual(y, 0)
            self.assertLessEqual(x, 2000)
            self.assertLessEqual(y, 3000)
        else:
            self.assertIn("ERROR: Widget with text/desc containing", res.stderr)

    def test_uidump_synonyms(self):
        if not self.has_device:
            self.skipTest("No ADB device online. Skipping.")
            
        # 搜别名同义词，联想匹配齿轮图标 content-desc "设置"
        cmd = ["python", "tools/uidump.py", "--find", "配置"]
        res = subprocess.run(cmd, capture_output=True, text=True)
        
        if res.returncode == 0:
            coords = res.stdout.strip().split()
            self.assertEqual(len(coords), 2)
            x, y = int(coords[0]), int(coords[1])
            self.assertGreaterEqual(x, 0)
            self.assertLessEqual(x, 1080)
            self.assertGreaterEqual(y, 0)
            self.assertLessEqual(y, 2400)

    def test_find_icon_image(self):
        if not self.has_device:
            self.skipTest("No ADB device online. Skipping.")
            
        # 测试在屏幕上模板匹配寻找设置齿轮图标
        template_png = os.path.abspath("tools/templates/setting.png")
        self.assertTrue(os.path.exists(template_png), f"Template image {template_png} does not exist.")
        
        cmd = ["python", "tools/find_icon.py", "-t", template_png]
        res = subprocess.run(cmd, capture_output=True, text=True)
        
        self.assertEqual(res.returncode, 0, f"find_icon.py failed with: {res.stderr}")
        coords = res.stdout.strip().split()
        self.assertEqual(len(coords), 2)
        x, y = int(coords[0]), int(coords[1])
        # 齿轮中点大致位置在 975 142 附近 (误差在 10px 内)
        self.assertTrue(965 <= x <= 985, f"X coordinate {x} out of range.")
        self.assertTrue(132 <= y <= 152, f"Y coordinate {y} out of range.")

if __name__ == "__main__":
    unittest.main()
