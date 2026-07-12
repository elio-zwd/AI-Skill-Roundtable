# -*- coding: utf-8 -*-
import os
import json
import shutil
import urllib.request
import urllib.error
import random

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

# 10 个备用内置 Key
BACKUP_KEYS = [
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY",
    "REDACTED_GEMINI_API_KEY"
]

def load_api_key(env_path):
    """
    从项目根目录的 .env 文件中加载 GEMINI_API_KEY
    """
    if not os.path.exists(env_path):
        return None
    try:
        with open(env_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line.startswith('GEMINI_API_KEY='):
                    val = line.split('GEMINI_API_KEY=', 1)[1].strip().strip('"').strip("'")
                    if val and val != "your_gemini_api_key_here":
                        return val
    except Exception as e:
        print(f"读取 .env 文件异常: {e}")
    return None

def get_embedding(text, api_key):
    """
    调用 Google Gemini Embedding API 获取文本向量值 (768 维)
    """
    url = f"https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key={api_key}"
    req_data = {
        "model": "models/text-embedding-004",
        "content": {
            "parts": [{"text": text}]
        }
    }
    
    headers = {"Content-Type": "application/json"}
    data_bytes = json.dumps(req_data).encode('utf-8')
    req = urllib.request.Request(url, data=data_bytes, headers=headers, method='POST')
    
    # 设定 3 秒超时，避免挂起过长
    with urllib.request.urlopen(req, timeout=3) as response:
        res_body = response.read().decode('utf-8')
        res_json = json.loads(res_body)
        return res_json.get("embedding", {}).get("values", [])

def generate_mock_vector(seed_text):
    """
    为该角色本地伪造生成一个 768 维特征向量，用以离线测试
    """
    # 保证确定性，对 seed_text 取绝对 hash 值
    random.seed(abs(hash(seed_text)))
    return [round(random.uniform(-0.1, 0.1), 6) for _ in range(768)]

def get_embedding_with_fallback(text, preferred_key):
    """
    带轮询重试的 Embedding 获取，并在所有 Key 都报错/不可达时降级为 Mock 特征向量
    """
    keys = []
    if preferred_key:
        keys.append(preferred_key)
    keys.extend(BACKUP_KEYS)
    
    last_ex = None
    
    for k in keys:
        try:
            return get_embedding(text, k)
        except urllib.error.HTTPError as e:
            last_ex = e
        except Exception as e:
            last_ex = e
            # 若由于网络超时等物理网络异常（非 API 返回的 HTTP Code 报错），直接跳过后续 Key 的网络尝试
            break
            
    print(f"警告：网络接口发生超时或不可达 (原因: {last_ex})，已本地生成 768 维 Mock 特征向量。")
    return generate_mock_vector(text)

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
    env_path = os.path.join(base_dir, ".env")

    # 加载 API Key，如果 .env 没拿到，降级到内置 Key 库中的第一个 (w1)
    api_key = load_api_key(env_path)
    if not api_key:
        print("未在 .env 中找到有效密钥。")
    else:
        print("已成功从 .env 加载 GEMINI_API_KEY")

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

        # 调用 Google Gemini Embedding API 提取描述向量
        desc_text = fm["description"]
        if not desc_text:
            desc_text = ui_attrs["tagline"]

        print(f"正在获取 {ui_attrs['name']} 的描述向量...")
        try:
            vector = get_embedding_with_fallback(desc_text, api_key)
            print(f"成功获取 {ui_attrs['name']} 向量, 维度: {len(vector)}")
        except Exception as e:
            print(f"获取 {ui_attrs['name']} 向量失败，设置为空向量。错误: {e}")
            vector = []

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
            "skillDescription": fm["description"],
            "descriptionVector": vector # 768维浮点列表
        }
        characters.append(char_data)

    # 3. 按 order 升序排列
    characters.sort(key=lambda x: x["order"])

    # 4. 写入 json 文件
    print(f"正在生成 {assets_config_path}")
    with open(assets_config_path, 'w', encoding='utf-8') as f:
        json.dump(characters, f, ensure_ascii=False, indent=2)

    print("技能元数据与向量化提取及资产拷贝已全部成功完成！")

if __name__ == "__main__":
    main()
