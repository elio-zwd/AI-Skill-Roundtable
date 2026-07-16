# -*- coding: utf-8 -*-
import os
import sys
import json
import time
import urllib.request
import urllib.error

# 设置字符编码
sys.stdout.reconfigure(encoding='utf-8')

# API 配置
API_KEY = "REDACTED_GEMINI_API_KEY"
API_URL = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key={API_KEY}"

PROMPT_TEMPLATE = """你是一个专业的文档知识提取与主旨提炼助手。
请阅读以下 Markdown 文件的内容，为它生成一段严格控制在 120 到 150 字之间的中文核心主旨摘要。

要求：
1. 摘要必须精准提炼出本篇文档中角色所运用的核心心智模型、决策启发式或关键内容范畴，便于后续做脑暴路由挑选。
2. 直接输出提炼后的主旨内容，千万不要包含“本篇文档介绍了”、“摘要：”等废话和引导句，字数必须严格在 120-150 字之间。

文件内容：
{文件内容}"""

def call_gemini_api(content):
    payload = {
        "contents": [
            {
                "parts": [
                    {"text": PROMPT_TEMPLATE.format(文件内容=content)}
                ]
            }
        ]
    }
    
    req_data = json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    
    # 针对 429 频控重试做最大 6 次指数退避，针对其他错误做 3 次常规重试
    attempt_429 = 0
    attempt_other = 0
    
    while True:
        try:
            req = urllib.request.Request(API_URL, data=req_data, headers=headers, method="POST")
            with urllib.request.urlopen(req, timeout=60) as response:
                res_data = json.loads(response.read().decode("utf-8"))
                summary = res_data["candidates"][0]["content"]["parts"][0]["text"].strip()
                # 去除前缀
                for prefix in ["摘要：", "摘要:", "摘要 ", "本文", "本篇文档"]:
                    if summary.startswith(prefix):
                        summary = summary[len(prefix):].strip()
                return summary
        except urllib.error.HTTPError as e:
            if e.code == 429:
                attempt_429 += 1
                if attempt_429 <= 6:
                    backoff_time = (2 ** (attempt_429 - 1)) * 10 + 2  # 12s, 22s, 42s, 82s...
                    print(f"  [Rate Limit] 触发 429 频控 (HTTP 429)。第 {attempt_429}/6 次退避，等待 {backoff_time} 秒...")
                    time.sleep(backoff_time)
                    continue
                else:
                    print("  [Warning] 429 频控重试次数用尽，将使用前 200 字进行物理截断兜底。")
                    return content[:200].strip()
            else:
                # 其他 HTTP 错误
                attempt_other += 1
                if attempt_other <= 3:
                    print(f"  [Error] HTTP 错误 {e.code}。第 {attempt_other}/3 次重试，等待 3 秒...")
                    time.sleep(3)
                    continue
                else:
                    print(f"  [Warning] HTTP 错误 {e.code} 重试用尽，使用物理截断兜底。")
                    return content[:200].strip()
        except Exception as e:
            # 其它非 HTTP 错误 (例如网络连接断开)
            attempt_other += 1
            if attempt_other <= 3:
                print(f"  [Error] 请求异常: {e}。第 {attempt_other}/3 次重试，等待 3 秒...")
                time.sleep(3)
                continue
            else:
                print(f"  [Warning] 异常重试用尽，使用物理截断兜底。")
                return content[:200].strip()

def main():
    skills_dir = r"d:\My_Elio\AI-Skill-Roundtable\app\src\main\assets\skills"
    output_file = r"d:\My_Elio\AI-Skill-Roundtable\app\src\main\assets\skills_summaries.json"
    
    if not os.path.exists(skills_dir):
        print(f"错误: 技能目录 {skills_dir} 不存在。")
        sys.exit(1)
        
    result_data = {}
    processed_count = 0
    
    # 遍历技能目录
    character_dirs = sorted(os.listdir(skills_dir))
    for char_dir in character_dirs:
        char_path = os.path.join(skills_dir, char_dir)
        if not os.path.isdir(char_path):
            continue
            
        print(f"正在处理角色目录: {char_dir}")
        char_data = {}
        
        for folder in ["examples", "references"]:
            folder_path = os.path.join(char_path, folder)
            if not os.path.isdir(folder_path):
                continue
                
            files_data = {}
            for file_name in sorted(os.listdir(folder_path)):
                if file_name.endswith(".md") and file_name.lower() != "skill.md":
                    file_path = os.path.join(folder_path, file_name)
                    print(f"  处理文件: {folder}/{file_name}")
                    
                    try:
                        with open(file_path, "r", encoding="utf-8") as f:
                            content = f.read()
                        
                        # 字符长度控制
                        if len(content) > 10000:
                            content = content[:10000]
                            
                        # 调用 API 生成摘要
                        summary = call_gemini_api(content)
                        files_data[file_name] = summary
                        processed_count += 1
                        
                        # 请求间隔 2.0s 防止频控
                        time.sleep(2.0)
                    except Exception as e:
                        print(f"  [Fatal Error] 读取文件或处理失败: {file_path}. {e}")
                        # 兜底物理截断
                        files_data[file_name] = content[:200].strip() if 'content' in locals() else ""
            
            if files_data:
                char_data[folder] = files_data
                
        if char_data:
            result_data[char_dir] = char_data

    # 保存为 JSON 文件
    print(f"正在保存最终摘要总表至: {output_file}")
    try:
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(result_data, f, ensure_ascii=False, indent=2)
        print("保存成功！")
        
        # 自动物理删除自身
        try:
            script_path = os.path.abspath(__file__)
            print(f"安全规范：开始物理删除本脚本 {script_path}")
            os.remove(script_path)
            print("脚本已成功删除，敏感信息已清理。")
        except Exception as delete_error:
            print(f"删除自身脚本失败: {delete_error}")
            
    except Exception as save_error:
        print(f"保存 JSON 文件失败: {save_error}")
        
    print(f"任务完成！共成功处理文件数: {processed_count}")

if __name__ == "__main__":
    main()
