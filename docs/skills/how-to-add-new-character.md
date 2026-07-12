# 新增 Skill 角色操作指南 (how-to-add-new-character.md)

由于圆桌应用在重构中采用了 **Scheme B 全动态配置 Seeding 方案**，开发者在添加新的 Skills 智囊角色时，**无需修改任何 Kotlin 业务代码**。只需按照以下 4 步规范操作，即可实现一键扩增智囊。

---

## 1. 准备新角色技能文件夹
1. 将新角色的 GitHub 技能定义仓库下载并保存到本地：
   `docs/skills/<new-character-skill-folder>/` (例如 `docs/skills/sam-altman-skill-main/`)
2. 确保此目录下必须包含：
   - **`SKILL.md`**：以 `---` 包裹的 YAML Frontmatter 开始，定义 `name` 和 `description`，其余正文为系统设定提示词。
   - （可选）**`examples/`**：包含少样本对话范例 `.md` 文件。
   - （可选）**`references/`**：包含各种文献、语录、著作摘录等 `.md` 文件。

---

## 2. 配置 Python 脚本映射表
打开 [extract_skills_metadata.py](file:///d:/My_Elio/AI-Skill-Roundtable/workspace/tools/extract_skills_metadata.py) 脚本，在顶部的 `UI_MAPPING` 字典中，新增该角色文件夹与 UI 展示属性的映射：

```python
UI_MAPPING = {
    # ... 已有的 7 个角色 ...
    "sam-altman-skill-main": {          # 对应的物理文件夹名称
        "id": "sam_altman",             # 唯一字符ID（必须与数据库和 Room 表格对应）
        "name": "萨姆·奥特曼",           # 手机 UI 显示的姓名
        "avatar": "🤖",                 # 席位图及气泡显示的 Emoji 头像
        "tagline": "OpenAI 创始人，引领通用人工智能与大模型商业化浪潮的硅谷操盘手", # 大厅一句话标签
        "order": 8                      # 物理列表排序顺序
    }
}
```

---

## 3. 执行自动化打包与向量提取
在开发机控制台（pwsh）中运行元数据抽取脚本：

```powershell
python workspace/tools/extract_skills_metadata.py
```

### 自动化执行链说明
此脚本执行时会自动：
1. 清洗并递归拷贝新技能文件夹到 `app/src/main/assets/skills/`，并**自动过滤排斥** `.jpg` / `.gif` / `.mp4` 等大体积二进制文件。
2. 读取根目录的 `.env` Key，调用 Google `text-embedding-004` 向量接口计算新角色 `description` 向量（若超时或离线则自动 Mock 生成 768 维兼容向量）。
3. 自动重新生成 [skills_config.json](file:///d:/My_Elio/AI-Skill-Roundtable/app/src/main/assets/skills_config.json) 资产配置文件。

---

## 4. 编译与设备部署
在命令行中执行编译安装：

```powershell
$env:JAVA_HOME = "D:\My_Elio\dev-tools\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat installDebug
```

### 数据库自动更新机制
* 由于 Room 数据库的 `RoundtableDatabase.kt` 启用了 `.fallbackToDestructiveMigration()`。
* 当 App 在手机上重新安装启动时，Room 会判定版本，清空旧表，并在 `DatabaseCallback` 中**自动反序列化最新的 `skills_config.json` 进行全新 Seeding 入库**。
* 打开应用，新智囊角色（如“萨姆·奥特曼”）即刻在大厅及圆桌中渲染就绪，无缝支持向量语义排序和双模型 Broker 路由！
