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
        "avatar": "avatars/elon_musk.jpg",
        "tagline": "SpaceX与特斯拉CEO，用第一性原理与五步工作法重塑现实的硬核科技狂人",
        "order": 2
    },
    "feynman-skill-main": {
        "id": "richard_feynman",
        "name": "理查德·费曼",
        "avatar": "avatars/richard_feynman.png",
        "tagline": "诺贝尔物理学奖得主，拒绝虚荣术语，主张用极简大白话解释一切的科学顽童",
        "order": 3
    },
    "munger-skill-main": {
        "id": "charlie_munger",
        "name": "查理·芒格",
        "avatar": "avatars/charlie_munger.png",
        "tagline": "用多元思维模型与逆向思考避开愚蠢的终身学习者",
        "order": 4
    },
    "naval-skill-main": {
        "id": "naval_ravikant",
        "name": "纳瓦尔",
        "avatar": "avatars/naval_ravikant.png",
        "tagline": "硅谷投资人与现代智者，用“特定知识”与“无需许可的杠杆”追求财富与快乐",
        "order": 5
    },
    "steve-jobs-skill-main": {
        "id": "steve_jobs",
        "name": "史蒂夫·乔布斯",
        "avatar": "avatars/steve_jobs.png",
        "tagline": "苹果公司联合创始人，站在科技与人文的交汇处的完美主义者",
        "order": 6
    },
    "taleb-skill-main": {
        "id": "nassim_taleb",
        "name": "纳西姆·塔勒布",
        "avatar": "avatars/nassim_taleb.png",
        "tagline": "《黑天鹅》《反脆弱》作者，关注尾部风险与切肤之痛的风险工程师",
        "order": 7
    },
    "zhangxuefeng-skill-main": {
        "id": "zhang_xuefeng",
        "name": "张雪峰",
        "avatar": "avatars/zhang_xuefeng.png",
        "tagline": "升学志愿与职业规划导师，刺破理想幻泡的实用主义者",
        "order": 1
    },
    "karpathy-skill": {
        "id": "andrej_karpathy",
        "name": "安德烈·卡帕斯",
        "avatar": "avatars/andrej_karpathy.png",
        "tagline": "前特斯拉AI主管与OpenAI创始成员，主张用极简逻辑和手写纯Python代码理解AI的硬核极客",
        "order": 8
    },
    "zhang-yiming-skill": {
        "id": "zhang_yiming",
        "name": "张一鸣",
        "avatar": "avatars/zhang_yiming.png",
        "tagline": "字节跳动创始人，信奉“延迟满足感”、坦诚清晰、大力出奇迹的理性机器与逻辑操盘手",
        "order": 9
    },
    "paul-graham-skill": {
        "id": "paul_graham",
        "name": "保罗·格雷厄姆",
        "avatar": "avatars/paul_graham.png",
        "tagline": "Y Combinator联合创始人，初创企业与黑客思维的创投教父",
        "order": 10
    },
    "ilya-sutskever-skill": {
        "id": "ilya_sutskever",
        "name": "伊利亚·苏茨克维尔",
        "avatar": "avatars/ilya_sutskever.png",
        "tagline": "前OpenAI首席科学家，对AGI安全深信不疑，探寻大模型涌现机制的技术先知",
        "order": 11
    },
    "trump-skill": {
        "id": "donald_trump",
        "name": "唐纳德·特朗普",
        "avatar": "avatars/donald_trump.png",
        "tagline": "前美国总统，擅长极限施压、博弈与注意力争夺的“交易艺术”谈判家",
        "order": 12
    },
    "mrbeast-skill": {
        "id": "mr_beast",
        "name": "吉米·唐纳森 (MrBeast)",
        "avatar": "avatars/mr_beast.png",
        "tagline": "全球顶级YouTube创作者，解构爆款流量、传播学与受众心理的超级策划人",
        "order": 13
    },
    "sun-yuchen-perspective": {
        "id": "justin_sun",
        "name": "孙宇晨",
        "avatar": "avatars/justin_sun.png",
        "tagline": "波场创始人，擅长注意力经济、极限营销与事件公关的币圈弄潮儿",
        "order": 14
    },
    "freud-skill": {
        "id": "sigmund_freud",
        "name": "西格蒙德·弗洛伊德",
        "avatar": "avatars/sigmund_freud.png",
        "tagline": "精神分析学之父，擅长剖析人类与AI潜意识、梦境与本能的心理学大师",
        "order": 15
    },
    "x-mentor-skill": {
        "id": "x_mentor",
        "name": "X 增长导师",
        "avatar": "avatars/x_mentor.png",
        "tagline": "聚合顶级海外博主增长方法论，手握选题与内容增长密钥的社交媒体专家",
        "order": 16
    },
    "fengge-skill": {
        "id": "feng_ge",
        "name": "峰哥亡命天涯",
        "avatar": "avatars/feng_ge.png",
        "tagline": "纪实旅行自媒体，用反煽情、黑色幽默与平民视角冷眼看世界的“峰哥”",
        "order": 17
    },
    "cz-skill": {
        "id": "changpeng_zhao",
        "name": "赵长鹏 (CZ)",
        "avatar": "avatars/changpeng_zhao.png",
        "tagline": "币安创始人，奉行效率第一、力行去中心化与实用加密精神的Web3拓荒者",
        "order": 18
    },
    "duan-yongping-skill": {
        "id": "duan_yongping",
        "name": "段永平",
        "avatar": "avatars/duan_yongping.png",
        "tagline": "步步高与小天才幕后推手，倡导“本分”、“平常心”与做对的事的投资大师",
        "order": 19
    },
    "tim-cook-skill": {
        "id": "tim_cook",
        "name": "蒂姆·库克",
        "avatar": "avatars/tim_cook.png",
        "tagline": "苹果公司CEO，将供应链效率提升至极致的商业操盘手与平稳过渡大师",
        "order": 20
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

NETWORK_AVAILABLE = True

def check_network():
    """
    检查是否能够连通 Google API 服务。设定极短的超时，如果失败则直接判定网络不可用。
    """
    import socket
    try:
        # 尝试解析并建立 TCP 连接，超时设为 0.8 秒
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(0.8)
        s.connect(("generativelanguage.googleapis.com", 443))
        s.close()
        return True
    except Exception:
        return False

def get_embedding_with_fallback(text, preferred_key):
    """
    带轮询重试的 Embedding 获取，并在所有 Key 都报错/不可达时降级为 Mock 特征向量
    """
    global NETWORK_AVAILABLE
    if not NETWORK_AVAILABLE or not preferred_key:
        return generate_mock_vector(text)
        
    keys = [preferred_key]
    keys.extend(BACKUP_KEYS)
    
    last_ex = None
    
    for k in keys:
        try:
            return get_embedding(text, k)
        except urllib.error.HTTPError as e:
            last_ex = e
            # 如果是 400 (Bad Request) 或 403 (Forbidden)，表明 API Key 无效或过期，直接熔断，不再尝试其他备份 Key
            if e.code in [400, 403]:
                print(f"密钥 {k[:8]}... 触发 400/403 熔断: {e}")
                break
        except Exception as e:
            last_ex = e
            # 物理网络错误（如超时、无路由、连接被拒绝），直接标记网络不可用，后续角色全部熔断
            print(f"物理网络连接异常: {e}。后续角色将直接本地降级，不再尝试网络请求。")
            NETWORK_AVAILABLE = False
            break
            
    print(f"警告：网络接口发生超时或不可达 (原因: {last_ex})，已本地生成 768 维 Mock 特征向量。")
    return generate_mock_vector(text)


def parse_frontmatter(file_path):
    """
    解析 Markdown 文件头部的 YAML frontmatter，具有极强的健壮性和容错能力
    """
    if not os.path.exists(file_path):
        return {"name": "", "description": ""}
        
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"读取文件 {file_path} 异常: {e}")
        return {"name": "", "description": ""}

    # 处理 BOM 头和前导空白
    content = content.lstrip('\ufeff').lstrip()
    
    if not content.startswith('---'):
        # 兼容性处理：如果没以 --- 开头，但文件前部有 ---，则截取第一个 --- 之后的内容
        idx = content.find('---')
        if idx != -1:
            content = content[idx:]
        else:
            return {"name": "", "description": ""}
        
    end_fm = content.find('---', 3)
    if end_fm == -1:
        return {"name": "", "description": ""}
        
    fm_content = content[3:end_fm].strip()
    
    name = ""
    description_lines = []
    
    lines = fm_content.splitlines()
    in_desc = False
    desc_indent = None
    
    import re
    for line in lines:
        # 如果是空行且我们在读取 description，保留空行
        if not line.strip():
            if in_desc:
                description_lines.append("")
            continue
            
        # 检查是否是新的键值对
        key_match = re.match(r'^([a-zA-Z0-9_\-\u4e00-\u9fa5]+):\s*(.*)', line)
        
        if key_match:
            key = key_match.group(1).strip()
            val = key_match.group(2).strip()
            
            if key == 'name':
                name = val.strip('"').strip("'")
                in_desc = False
            elif key == 'description':
                in_desc = True
                if val and val not in ['|', '|-', '>']:
                    description_lines.append(val.strip('"').strip("'"))
                desc_indent = None
            else:
                in_desc = False
        else:
            if in_desc:
                if desc_indent is None:
                    desc_indent = len(line) - len(line.lstrip())
                cleaned_line = line[desc_indent:] if len(line) - len(line.lstrip()) >= desc_indent else line.lstrip()
                description_lines.append(cleaned_line)
                
    while description_lines and not description_lines[-1]:
        description_lines.pop()
        
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

    # 加载 API Key
    api_key = load_api_key(env_path)
    if not api_key:
        print("未在 .env 中找到有效密钥，自动进入本地离线 Mock 模式。")
        NETWORK_AVAILABLE = False
    else:
        print("已成功从 .env 加载 GEMINI_API_KEY。")
        print("正在检测 Gemini API 网络连通性...")
        if not check_network():
            print("检测到当前网络无法直连 Gemini API 服务，已自动进入离线 Mock 模式。")
            NETWORK_AVAILABLE = False
        else:
            print("Gemini API 网络连接正常，将尝试在线获取 Embedding。")

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
                elif name in ['.git', '.github', '.idea', '.gitignore']:
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
