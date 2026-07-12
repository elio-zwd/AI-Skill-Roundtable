# -*- coding: utf-8 -*-
import os
import json
import shutil

# UI 相关的展示属性映射
UI_MAPPING = {
    "elon-musk-skill-main": {
        "id": "elon_musk",
        "name": "埃隆·马斯克",
        "avatar": "🪐",
        "tagline": "SpaceX与特斯拉CEO，用第一性原理与五步工作法重塑现实的硬核科技狂人",
        "order": 2
    },
    "feynman-skill-main": {
        "id": "richard_feynman",
        "name": "理查德·费曼",
        "avatar": "🥁",
        "tagline": "诺贝尔物理学奖得主，拒绝虚荣术语，主张用极简大白话解释一切的科学顽童",
        "order": 3
    },
    "munger-skill-main": {
        "id": "charlie_munger",
        "name": "查理·芒格",
        "avatar": "👴",
        "tagline": "用多元思维模型与逆向思考避开愚蠢的终身学习者",
        "order": 4
    },
    "naval-skill-main": {
        "id": "naval_ravikant",
        "name": "纳瓦尔",
        "avatar": "🧘",
        "tagline": "硅谷投资人与现代智者，用“特定知识”与“无需许可的杠杆”追求财富与快乐",
        "order": 5
    },
    "steve-jobs-skill-main": {
        "id": "steve_jobs",
        "name": "史蒂夫·乔布斯",
        "avatar": "🍎",
        "tagline": "苹果公司联合创始人，站在科技与人文的交汇处的完美主义者",
        "order": 6
    },
    "taleb-skill-main": {
        "id": "nassim_taleb",
        "name": "纳西姆·塔勒布",
        "avatar": "🏋️",
        "tagline": "《黑天鹅》《反脆弱》作者，关注尾部风险与切肤之痛的风险工程师",
        "order": 7
    },
    "zhangxuefeng-skill-main": {
        "id": "zhang_xuefeng",
        "name": "张雪峰",
        "avatar": "👨‍🏫",
        "tagline": "升学志愿与职业规划导师，刺破理想幻泡的实用主义者",
        "order": 1
    }
}

def parse_frontmatter(file_path):
    """
    解析 Markdown 文件头部的 YAML frontmatter
    """
    if not os.path.exists(file_path):
        return {"name": "", "description": ""}
        
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    if not content.startswith('---'):
        return {"name": "", "description": ""}
        
    end_fm = content.find('---', 3)
    if end_fm == -1:
        return {"name": "", "description": ""}
        
    fm_content = content[3:end_fm].strip()
    
    name = ""
    description_lines = []
    lines = fm_content.splitlines()
    in_desc = False
    
    for line in lines:
        if line.startswith('name:'):
            name = line.split('name:', 1)[1].strip()
            in_desc = False
        elif line.startswith('description:'):
            in_desc = True
            rest = line.split('description:', 1)[1].strip()
            if rest and rest != '|':
                description_lines.append(rest)
        elif in_desc:
            description_lines.append(line)
            
    desc_content = "\n".join(description_lines).strip()
    return {
        "name": name,
        "description": desc_content
    }

def main():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    docs_skills_dir = os.path.join(base_dir, "docs", "skills")
    assets_skills_dir = os.path.join(base_dir, "app", "src", "main", "assets", "skills")
    assets_config_path = os.path.join(base_dir, "app", "src", "main", "assets", "skills_config.json")

    # 1. 确保 assets/skills 目录存在
    if not os.path.exists(assets_skills_dir):
        os.makedirs(assets_skills_dir)
    else:
        # 清空现有的平铺 .md 文件，以保留全新的文件夹结构
        for item in os.listdir(assets_skills_dir):
            item_path = os.path.join(assets_skills_dir, item)
            if os.path.isfile(item_path) and item.endswith('.md'):
                os.remove(item_path)

    characters = []

    # 2. 拷贝技能文件夹并解析元数据
    for folder_name, ui_attrs in UI_MAPPING.items():
        src_folder = os.path.join(docs_skills_dir, folder_name)
        dst_folder = os.path.join(assets_skills_dir, folder_name)

        if not os.path.exists(src_folder):
            print(f"警告：源目录不存在 {src_folder}")
            continue

        # 拷贝文件夹，过滤掉图片、视频等二进制大文件
        print(f"正在拷贝 {folder_name} -> {dst_folder}")
        if os.path.exists(dst_folder):
            shutil.rmtree(dst_folder)

        def ignore_patterns(path, names):
            ignored = []
            for name in names:
                ext = os.path.splitext(name)[1].lower()
                if ext in ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.mp4', '.zip', '.tar', '.gz']:
                    ignored.append(name)
            return ignored

        shutil.copytree(src_folder, dst_folder, ignore=ignore_patterns)

        # 解析 SKILL.md 中的元数据
        skill_md_path = os.path.join(dst_folder, "SKILL.md")
        fm = parse_frontmatter(skill_md_path)

        # 合成最终的 Character 数据结构
        relative_asset_path = f"skills/{folder_name}/SKILL.md"
        char_data = {
            "id": ui_attrs["id"],
            "name": ui_attrs["name"],
            "avatar": ui_attrs["avatar"],
            "tagline": ui_attrs["tagline"],
            "systemPrompt": "", # 运行时动态载入
            "skillAssetPath": relative_asset_path,
            "order": ui_attrs["order"],
            "isActive": True,
            "skillName": fm["name"],
            "skillDescription": fm["description"]
        }
        characters.append(char_data)

    # 3. 按 order 升序排列
    characters.sort(key=lambda x: x["order"])

    # 4. 写入 json 文件
    print(f"正在生成 {assets_config_path}")
    with open(assets_config_path, 'w', encoding='utf-8') as f:
        json.dump(characters, f, ensure_ascii=False, indent=2)

    print("技能元数据抽取及资产拷贝已全部成功完成！")

if __name__ == "__main__":
    main()
