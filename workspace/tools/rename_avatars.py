# -*- coding: utf-8 -*-
import os

AVATAR_MAPPING = {
    0: "richard_feynman",
    1: "charlie_munger",
    2: "naval_ravikant",
    3: "steve_jobs",
    4: "nassim_taleb",
    5: "zhang_xuefeng",
    6: "andrej_karpathy",
    7: "zhang_yiming",
    8: "paul_graham",
    9: "ilya_sutskever",
    10: "donald_trump",
    11: "mr_beast",
    12: "justin_sun",
    13: "sigmund_freud",
    14: "x_mentor",
    15: "feng_ge",
    16: "changpeng_zhao",
    17: "duan_yongping",
    18: "tim_cook"
}

def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    avatars_dir = os.path.join(base_dir, "app", "src", "main", "assets", "avatars")
    
    print(f"头像目录: {avatars_dir}")
    
    # 1. 遍历重命名并删除旧的 .png 文件
    for index, char_id in AVATAR_MAPPING.items():
        src_name = f"Generated Image July 13, 2026 - 8_59PM ({index}).jpg"
        src_path = os.path.join(avatars_dir, src_name)
        dst_name = f"{char_id}.jpg"
        dst_path = os.path.join(avatars_dir, dst_name)
        
        # 重命名
        if os.path.exists(src_path):
            # 如果目标路径已存在，先删除（防 Windows 重命名报错）
            if os.path.exists(dst_path):
                os.remove(dst_path)
            os.rename(src_path, dst_path)
            print(f"重命名成功: {src_name} -> {dst_name}")
        else:
            print(f"警告：未找到源文件 {src_name}")
            
        # 删除同名旧 PNG
        png_name = f"{char_id}.png"
        png_path = os.path.join(avatars_dir, png_name)
        if os.path.exists(png_path):
            os.remove(png_path)
            print(f"已删除旧占位文件: {png_name}")

if __name__ == "__main__":
    main()
