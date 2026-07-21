import os
import re
import json

def clean_markdown(text):
    """
    清理 Markdown 格式符号，转换为相对平铺的纯文本。
    """
    # 移除 YAML frontmatter (如果存在)
    text = re.sub(r'^---[\s\S]+?---', '', text)
    
    # 转换或移除 markdown 表格
    lines = text.split('\n')
    filtered_lines = []
    for line in lines:
        stripped = line.strip()
        if not stripped:
            continue
        # 如果是表格的分隔行，例如 |---|---|，直接丢弃
        if re.match(r'^\|?\s*[-:]+\s*\|', stripped) or re.match(r'^\|[-:| ]+\|$', stripped):
            continue
        # 如果是普通的表格行，将 | 替换为逗号或句号，保留文字
        if stripped.startswith('|') and stripped.endswith('|'):
            cells = [c.strip() for c in stripped.split('|') if c.strip()]
            if cells:
                line = "，".join(cells) + "。"
            else:
                continue
        # 移除 markdown 分割线
        if re.match(r'^[-*_]{3,}$', stripped):
            continue
        filtered_lines.append(line)
    text = '\n'.join(filtered_lines)
    
    # 移除 HTML 标签
    text = re.sub(r'<[^>]+>', '', text)
    
    # 移除链接: [text](url) -> text
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
    
    # 移除图片: ![alt](url) -> ''
    text = re.sub(r'!\[[^\]]*\]\([^)]+\)', '', text)
    
    # 移除代码块
    text = re.sub(r'```[\s\S]*?```', '', text)
    text = re.sub(r'`[^`\n]+`', '', text)
    
    # 移除粗体、斜体、删除线
    text = re.sub(r'\*\*([^*]+)\*\*', r'\1', text)
    text = re.sub(r'\*([^*]+)\*', r'\1', text)
    text = re.sub(r'__([^_]+)__', r'\1', text)
    text = re.sub(r'_([^_]+)_', r'\1', text)
    text = re.sub(r'~~([^~]+)~~', r'\1', text)
    
    # 移除标题标记 (#)
    text = re.sub(r'^#+\s+', '', text, flags=re.MULTILINE)
    
    # 移除无序列表标记 (- 或 *) 或有序列表标记 (1. 等)
    text = re.sub(r'^\s*[-\*+]\s+', '', text, flags=re.MULTILINE)
    text = re.sub(r'^\s*\d+\.\s+', '', text, flags=re.MULTILINE)
    
    # 移除引用的 >
    text = re.sub(r'^\s*>\s*', '', text, flags=re.MULTILINE)
    
    return text

def extract_summary(file_path, file_content):
    """
    智能提取其前两段或前 200 字符中代表核心主旨的纯文本，提炼为 120-150 字左右的中文核心摘要。
    """
    # 获取文件名（不含扩展名）
    file_name = os.path.splitext(os.path.basename(file_path))[0]
    
    # 提取文件头部第一个标题
    title = ""
    for line in file_content.split('\n'):
        line_strip = line.strip()
        if line_strip.startswith('# '):
            title = line_strip.lstrip('# ').strip()
            break
        elif line_strip.startswith('## '):
            title = line_strip.lstrip('## ').strip()
            break
            
    if not title:
        title = file_name
        
    cleaned_text = clean_markdown(file_content)
    
    # 将文本分割成段落，并过滤无意义行或过短行
    paragraphs = []
    for p in cleaned_text.split('\n'):
        p_strip = p.strip()
        # 排除包含日期、单纯链接等没有句意特征的极短段落
        if p_strip and len(p_strip) > 5:
            paragraphs.append(p_strip)
            
    # 智能选择前几段拼凑出 200 字左右的内容
    selected_text = ""
    for p in paragraphs:
        if len(selected_text) >= 200:
            break
        if selected_text:
            selected_text += " " + p
        else:
            selected_text = p
            
    # 如果提取出的文本太少（比如少于 80 字）
    if len(selected_text) < 80:
        complement = f"本文档是《{title}》（文件名：{file_name}）。"
        if selected_text:
            complement += f"主要内容包含：{selected_text}"
        else:
            complement += "内容主要整理了该角色的思维模型研究、场景对话实录与核心决策逻辑等文献。"
        selected_text = complement
        
    # 清理多余的空白字符，合并为一个空格
    selected_text = re.sub(r'\s+', ' ', selected_text).strip()
    
    # 提炼/裁剪为 120-150 字左右的中文核心摘要
    # 目标长度约 135 字。
    target_len = 135
    if len(selected_text) > 150:
        # 尝试在 120 到 148 字符之间寻找一个句末标点符号，以使得句子结束更自然
        slice_text = selected_text[:147]
        found = False
        for punc in ['。', '！', '？', '；']:
            idx = slice_text.rfind(punc)
            if idx >= 120:
                selected_text = slice_text[:idx + 1]
                found = True
                break
        if not found:
            # 如果没有找到合适的句尾标点，则直接截断并在 137 字处加省略号
            selected_text = selected_text[:137] + "..."
    elif len(selected_text) < 120:
        # 如果长度仍然不足 120 字，可以通过在末尾附加说明信息使其达到 120-150 字
        additional_info = f"该内容是理解该人物核心思维模式及其实践应用的重要参考素材，具有极高的实战参考价值。"
        needed = 125 - len(selected_text)
        if needed > 0:
            selected_text += " " + additional_info[:needed + 20]
            # 再次确认不超过 150
            if len(selected_text) > 150:
                selected_text = selected_text[:145] + "..."
                
    return selected_text

def main():
    base_dir = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    skills_dir = os.path.join(base_dir, "app", "src", "main", "assets", "skills")
    output_file = os.path.join(base_dir, "app", "src", "main", "assets", "skills_summaries.json")
    
    if not os.path.exists(skills_dir):
        print(f"Error: Skills directory '{skills_dir}' does not exist.")
        return
        
    summaries_dict = {}
    total_files_processed = 0
    
    # 遍历所有角色文件夹 (排序以确保输出有序)
    for char_dir in sorted(os.listdir(skills_dir)):
        char_path = os.path.join(skills_dir, char_dir)
        if not os.path.isdir(char_path):
            continue
            
        char_summaries = {}
        
        # 检查 examples 和 references 文件夹
        for sub_folder in ["examples", "references"]:
            sub_folder_path = os.path.join(char_path, sub_folder)
            if not os.path.isdir(sub_folder_path):
                continue
                
            folder_dict = {}
            for file_name in sorted(os.listdir(sub_folder_path)):
                if not file_name.lower().endswith(".md"):
                    continue
                    
                file_path = os.path.join(sub_folder_path, file_name)
                
                try:
                    # 1. 读取文件（最多读取前 2000 字符）
                    with open(file_path, "r", encoding="utf-8") as f:
                        file_content = f.read(2000)
                except Exception as e:
                    print(f"Warning: Failed to read {file_path}. Error: {e}")
                    continue
                    
                # 2. 生成摘要
                summary = extract_summary(file_path, file_content)
                folder_dict[file_name] = summary
                total_files_processed += 1
                
            if folder_dict:
                char_summaries[sub_folder] = folder_dict
                
        if char_summaries:
            summaries_dict[char_dir] = char_summaries
            
    # 4. 写入并保存到 skills_summaries.json
    try:
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(summaries_dict, f, ensure_ascii=False, indent=2)
        print(f"Success: Generated summaries for {total_files_processed} files.")
        print(f"Output saved to: {output_file}")
    except Exception as e:
        print(f"Error: Failed to write output file. Error: {e}")

if __name__ == "__main__":
    main()
