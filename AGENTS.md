# AGENTS.md — AI 智囊圆桌 (AI-Skill-Roundtable)

> AI 代理工作规范文件。每次进入本工作区，**必须先阅读本文件**，再开始任何修改。

---

## 1. 项目背景与当前阶段

### 1.1 项目简介
**AI 智囊圆桌**是一款原生 Android 聊天应用。用户提问后，7 个 GitHub Skills 角色（Elon Musk、Feynman、Munger、Naval、Steve Jobs、Taleb、张雪峰）依次轮流作答，每位角色回答后自动携带完整上下文，让下一位角色看到前人发言并进行评论、反驳或补充，形成真实的圆桌脑暴效果。

### 1.2 当前阶段
- **阶段**：引擎重构与双模型流水线完成（编译已完全通过，核心功能极度稳健）
- **已交付目标**：
  1. 修复 API 模型名错误（`gemini-3.5-flash` 为主力，`gemini-3.1-flash-lite-preview` 为 Broker 决策者）
  2. 会话级 API Key 轮询绑定与熔断保护机制（命中 Gemini 隐式前缀缓存）
  3. 全动态 Seeding 方案（Scheme B），完全脱离代码硬编码配置
  4. 向量语义路由 (Vector Semantic Routing) — 开启“专家先发”自适应排序
  5. 1M 上下文 Broker 双模型级联动态挑选Few-shot与辅助知识拼装

---

## 2. 技术栈与关键依赖

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material3 | BOM 最新 |
| 构建脚本 | Kotlin DSL (`.gradle.kts`) | — |
| 数据持久化 | Room Database | 当前 v3 |
| 网络 | Retrofit2 + OkHttp | — |
| 序列化 | kotlinx.serialization | — |
| AI API | Google Gemini REST API | v1beta |
| JDK | JDK 17 | 17.0.19+10 |
| 编译工具 | Gradle 8.14-all | — |

**关键依赖路径**：
- JDK 17：`D:\My_Elio\dev-tools\jdk-17.0.19+10`
- Gradle 缓存：本地离线缓存（无需联网）

---

## 3. 安装、运行与编译命令

```powershell
# 设置 JDK 17 环境（每次新开 shell 必须执行）
$env:JAVA_HOME = "D:\My_Elio\dev-tools\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 验证 Java 版本
java -version

# 编译 Debug 包
.\gradlew.bat assembleDebug

# 安装到已连接设备
.\gradlew.bat installDebug

# 清理构建产物
.\gradlew.bat clean
```

> **注意**：始终使用 `pwsh.exe`（PowerShell 7），禁止使用 `powershell.exe`。

---

## 4. 目录结构说明

```
AI-Skill-Roundtable/
├── app/                                  # Android 主应用模块
│   └── src/main/
│       ├── java/com/example/skillroundtable/
│       │   ├── MainActivity.kt           # Compose UI 入口（圆桌脑暴 + 角色大厅）
│       │   ├── data/                     # Room 实体 / DAO / 数据库 / Repository
│       │   │   ├── Character.kt          # 实体（新增 skillDescriptionVector）
│       │   │   ├── ChatSession.kt
│       │   │   └── RoundtableDatabase.kt # 数据库版本升级到 3
│       │   ├── network/                  # Gemini API 客户端（多 Key 会话绑定与 429 熔断）
│       │   │   └── GeminiApi.kt          # 增加 Embed 接口与 Broker 接口封装
│       │   ├── skill/                    # Skills 动态加载与 Broker 读取判定
│       │   │   └── SkillLoader.kt
│       │   └── viewmodel/
│       │       └── RoundtableViewModel.kt# 搭载 Cosine Similarity 余弦路由与双模型 Broker 拼装
│       ├── assets/skills/                # 动态打包的 7 个角色完整技能文件夹（物理过滤了二进制）
│       ├── assets/skills_config.json     # 动态 Seeding 及 UI 映射配置总表（含 Embedding 描述向量）
│       └── res/                          # Android 资源文件
├── docs/                                 # 项目文档（不含源代码）
│   ├── skills/                           # 7 个 GitHub Skills 仓库原始文件（参考源）
│   ├── architecture/                     # 架构与数据流说明
│   ├── decisions/                        # 关键技术决策记录
│   ├── issues/                           # Bug 与解决方案记录
│   ├── protocols/                        # API 协议与接口说明
│   ├── planning/                         # 任务计划与阶段目标
│   ├── environment/                      # 环境配置与编译说明
│   └── ai-guidance/                      # AI 工作结论与对话记录（新增架构升级报告）
├── workspace/
│   ├── tests/                            # 测试脚本
│   └── tools/                            # 开发辅助工具脚本
│       └── extract_skills_metadata.py    # 自动化元数据与 Embedding 生成工具（带 Mock Fallback 保护）
├── .env                                  # API 密钥（不提交 Git，见 .gitignore）
├── .env.example                          # 密钥模板（可提交）
├── AGENTS.md                             # 本文件
├── README.md
└── .gitignore
```

---

## 5. 代码规范与命名规范

- **注释语言**：中文
- **Git 提交格式**：`type: 中文说明`，例如：
  - `feat: 实现会话级 API Key 绑定机制`
  - `feat: 引入双模型 Broker 路由器决策流水线`
  - `refactor: 数据库 Seeding 升级至 Scheme B 全动态 JSON`
  - `docs: 更新 AGENTS.md 项目规范`
- **包名**：`com.example.skillroundtable`
- **数据库版本**：每次添加字段必须升版本号并提供 Migration（目前由于快速迭代，采用 Destination Fallback）

---

## 6. 敏感信息处理规则

| 信息类型 | 存放位置 | 是否提交 Git |
|---------|---------|-------------|
| Gemini API Key（主 Key） | `.env` 文件 `GEMINI_API_KEY=` | ❌ 已在 `.gitignore` 排除 |
| 备用 Key 池（多 Key 轮询） | `network/ApiKeyPool.kt` | ⚠️ 10个 Key 存入只读代码，安全脱敏 |
| 密钥模板 | `.env.example`（无真实值） | ✅ 可提交 |

**禁止事项**：禁止将任何真实密钥写入 `README.md`、`AGENTS.md`、commit message 或任何可提交文件。

---

## 7. 测试与验收标准

1. `.\gradlew.bat compileDebugKotlin` 编译零错误
2. 7 个 Skills 角色在“智囊大厅”界面正常显示，且可以由 `skills_config.json` 一站式扩增
3. 用户提问后，当开启“专家先发”时，自动利用余弦相似度计算，对智囊席位排序并作答
4. Lite 模型作为 Broker 自动选出需要载入的 references/ 或 examples/ 资料文件拼装入 Prompt
5. 同一会话只用一个 Key 发起请求，以最大化命中隐式前缀缓存

---

## 8. 禁止事项

- ❌ 禁止提交 `.env` 文件到 Git
- ❌ 禁止在未确认的情况下删除 `RoundtableDatabase.kt` 中的初始角色数据
- ❌ 禁止使用 `powershell.exe`，只用 `pwsh.exe`

---

## 9. AI 工作原则

1. **先读后改**：每次任务开始，先阅读相关源文件，再动手修改
2. **验证结果**：代码修改后必须运行 `assembleDebug` 或 `compileDebugKotlin` 确认编译通过
3. **分段修改**：避免一次性替换大文件，优先使用精准的局部替换或多段替换 `multi_replace_file_content`

---

## 10. 推荐 Skills（按任务场景）

| 场景 | 推荐 Skill | 使用条件 |
|------|----------|---------|
| 编写或重构 Compose UI 页面 | `@design-taste-frontend` / `@high-end-visual-design` | UI 相关任务自动加载 |
| 生成完整 Kotlin 文件（防省略） | `@full-output-enforcement` | 文件行数 > 100 行时强制启用 |
| Android SDK / ADB / 设备操作 | `@android-cli` | 需要 CLI 命令执行时 |

---

## 11. 核心文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 安卓编译指南 | [docs/environment/android-compilation-guide.md](docs/environment/android-compilation-guide.md) | JDK 17 离线编译完整方案 |
| 架构升级说明 | [docs/ai-guidance/2026-07-12-upgrades.md](docs/ai-guidance/2026-07-12-upgrades.md) | 2026-07-12 重大技术重构全景报告 |
| API 协议说明 | [docs/protocols/gemini-api.md](docs/protocols/gemini-api.md) | Gemini API 包含 Embedding、Broker 的协议规范 |
| 架构说明 | [docs/architecture/system-architecture.md](docs/architecture/system-architecture.md) | 双模型 Broker 与语义路由系统数据流 |
| 新增角色指南 | [docs/skills/how-to-add-new-character.md](docs/skills/how-to-add-new-character.md) | 一键动态扩增全新智囊角色的操作指南 |

---

## 12. 已确认的技术决策

| 决策 | 结论 | 记录时间 |
|------|------|---------|
| 数据库 Seeding 模式 | Scheme B：由 `extract_skills_metadata.py` 物理除二进制大文件，并在 `DatabaseCallback` 通过解析 assets/skills_config.json 全动态入库 | 2026-07-12 |
| 会话级 Key 绑定 | 通过 `SharedPreferences` 让每个 `sessionId` 绑定唯一的 API Key，仅在 429 报错时换绑重试，最大化命中隐式前缀缓存 | 2026-07-12 |
| 双模型级联 Broker 路由 | 使用 `gemini-3.1-flash-lite-preview` 快速选出相关少样本文件，加载拼装后送主力 `gemini-3.5-flash` (high thinking) 回答 | 2026-07-12 |
| 语义自适应路由 | 通过 `text-embedding-004` 将角色介绍转为 768 维向量存库。提问时计算问题余弦相似度，降序对席位重排以实现“专家先发”模式 | 2026-07-12 |
