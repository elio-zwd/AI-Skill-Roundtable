# -*- coding: utf-8 -*-
import os
import sys
import re
import hashlib
from PIL import Image, ImageDraw, ImageFont

# 将当前目录加入 python path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
try:
    from extract_skills_metadata import UI_MAPPING
except ImportError:
    print("无法导入 UI_MAPPING，请确认 extract_skills_metadata.py 是否在相同目录下。")
    sys.exit(1)

# 15 个温和的莫兰迪双色渐变色盘
MORANDI_PALETTES = [
    ((180, 195, 188), (155, 170, 175)), # 1. 豆绿-灰蓝
    ((210, 185, 185), (185, 170, 185)), # 2. 脏粉-浅紫
    ((215, 205, 195), (190, 185, 180)), # 3. 燕麦-暖灰
    ((170, 180, 165), (145, 155, 145)), # 4. 灰绿-鼠灰
    ((165, 185, 200), (140, 155, 170)), # 5. 雾霾蓝-深灰蓝
    ((205, 190, 175), (180, 165, 155)), # 6. 驼色-卡其灰
    ((190, 180, 195), (165, 165, 180)), # 7. 薰衣草灰-浅青灰
    ((185, 190, 170), (160, 165, 150)), # 8. 抹茶绿-浅灰绿
    ((200, 175, 170), (175, 155, 150)), # 9. 砖红灰-粉褐
    ((180, 185, 195), (155, 160, 170)), # 10. 浅灰蓝-石板灰
    ((220, 210, 200), (195, 185, 175)), # 11. 暖黄灰-浅褐
    ((175, 190, 195), (150, 165, 170)), # 12. 浅水蓝-灰蓝
    ((200, 180, 180), (175, 155, 155)), # 13. 暗玫瑰粉-浅红褐
    ((190, 195, 180), (165, 170, 155)), # 14. 芥末绿灰-橄榄绿灰
    ((200, 200, 190), (175, 175, 165)), # 15. 砂岩灰-浅石炭灰
]

def clean_and_get_last_char(name):
    # 去除括号及其中内容，如 (MrBeast), (CZ)
    name = re.sub(r'\(.*?\)', '', name).strip()
    # 提取所有汉字
    chinese_chars = re.findall(r'[\u4e00-\u9fa5]', name)
    if chinese_chars:
        return chinese_chars[-1]
    return name[-1] if name else "智"

def get_palette_by_id(char_id):
    # 根据 id 的 md5 确定性选择色盘，保证结果稳定
    md5 = hashlib.md5(char_id.encode('utf-8')).hexdigest()
    idx = int(md5, 16) % len(MORANDI_PALETTES)
    return MORANDI_PALETTES[idx]

def create_avatar(text, output_path, palette, size=256):
    # 2 倍超采样，提高文字和圆形边缘的抗锯齿效果
    scale = 2
    large_size = size * scale
    
    # 1. 创建渐变背景图 (RGBA)
    gradient = Image.new("RGBA", (large_size, large_size))
    c1, c2 = palette # (R, G, B)
    # 对角线渐变 (从左上到右下)
    for y in range(large_size):
        for x in range(large_size):
            t = (x + y) / (2.0 * large_size)
            r = int(c1[0] + (c2[0] - c1[0]) * t)
            g = int(c1[1] + (c2[1] - c1[1]) * t)
            b = int(c1[2] + (c2[2] - c1[2]) * t)
            gradient.putpixel((x, y), (r, g, b, 255))
            
    # 2. 绘制圆形遮罩
    mask = Image.new("L", (large_size, large_size), 0)
    m_draw = ImageDraw.Draw(mask)
    m_draw.ellipse([0, 0, large_size, large_size], fill=255)
    
    # 3. 将渐变图应用遮罩，生成透明圆形背景
    avatar_large = Image.new("RGBA", (large_size, large_size), (0, 0, 0, 0))
    avatar_large.paste(gradient, (0, 0), mask=mask)
    
    # 4. 居中绘制单字
    font_path = r"C:\Windows\Fonts\msyh.ttc" # 微软雅黑
    font_size = int(large_size * 0.45)
    try:
        font = ImageFont.truetype(font_path, font_size)
    except IOError:
        print("警告：未找到微软雅黑字体，使用默认字体。")
        font = ImageFont.load_default()
        
    draw_text = ImageDraw.Draw(avatar_large)
    bbox = draw_text.textbbox((0, 0), text, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    
    # 居中对齐公式
    x_pos = (large_size - text_width) // 2 - bbox[0]
    y_pos = (large_size - text_height) // 2 - bbox[1]
    
    # 使用暖白色 (255, 255, 255, 240) 提升质感
    text_color = (255, 255, 255, 240)
    draw_text.text((x_pos, y_pos), text, font=font, fill=text_color)
    
    # 5. 缩放到最终大小并保存 (LANCZOS)
    if hasattr(Image, "Resampling"):
        resample = Image.Resampling.LANCZOS
    else:
        resample = Image.ANTIALIAS
    avatar = avatar_large.resize((size, size), resample=resample)
    
    avatar.save(output_path, "PNG")

def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    avatars_dir = os.path.join(base_dir, "app", "src", "main", "assets", "avatars")
    
    if not os.path.exists(avatars_dir):
        os.makedirs(avatars_dir)
        print(f"创建头像目录: {avatars_dir}")
        
    generated_count = 0
    for folder, attrs in UI_MAPPING.items():
        char_id = attrs["id"]
        char_name = attrs["name"]
        
        # 排除马斯克
        if char_id == "elon_musk":
            print("埃隆·马斯克头像已由用户提供，跳过。")
            continue
            
        last_char = clean_and_get_last_char(char_name)
        filename = f"{char_id}.png"
        output_path = os.path.join(avatars_dir, filename)
        
        palette = get_palette_by_id(char_id)
        
        print(f"生成角色 [{char_name}] 头像 -> 字: {last_char}, 文件名: {filename}")
        create_avatar(last_char, output_path, palette)
        generated_count += 1
        
    print(f"批量头像生成完成！共生成了 {generated_count} 张头像。")

if __name__ == "__main__":
    main()
