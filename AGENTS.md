# AGENTS.md — AI 智囊圆桌 (AI-Skill-Roundtable)

> AI 代理工作规范文件。每次进入本工作区，**必须先阅读本文件**，再开始任何修改。

---

## 1. 项目背景与当前阶段

### 1.1 项目简介
**AI 智囊圆桌**是一款原生 Android 聊天应用。用户提问后，7 个 GitHub Skills 角色（Elon Musk、Feynman、Munger、Naval、Steve Jobs、Taleb、张雪峰）依次轮流作答，每位角色回答后自动携带完整上下文，让下一位角色看到前人发言并进行评论、反驳或补充，形成真实的圆桌脑暴效果。

### 1.2 当前阶段
- **阶段**：MVP 开发期（核心功能已可编译运行，Skills 引擎需要升级）
- **本轮交付目标**：
  1. 修复 API 模型名错误（`gemini-3.5-flash` → 真实模型名）
  2. 将单 Key 升级为多 Key 轮询 + 熔断机制（参考 `D:\My_Elio\life-archive-app`）
  3. 7 个 GitHub Skills 的 `SKILL.md` 完整 systemPrompt 替换现有硬编码内容
  4. Skills 文件打包到 `app/src/main/assets/skills/` 目录

---

## 2. 技术栈与关键依赖

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material3 | BOM 最新 |
| 构建脚本 | Kotlin DSL (`.gradle.kts`) | — |
| 数据持久化 | Room Database | 当前 v1 |
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
│       │   │   ├── Character.kt
│       │   │   ├── ChatSession.kt
│       │   │   └── RoundtableDatabase.kt
│       │   ├── network/                  # Gemini API 客户端（待升级多 Key 机制）
│       │   │   └── GeminiApi.kt
│       │   ├── skill/                    # [待建] Skills 加载工具
│       │   │   └── SkillLoader.kt
│       │   └── viewmodel/
│       │       └── RoundtableViewModel.kt
│       ├── assets/skills/                # [待建] 打包的 SKILL.md 文件（7 个角色）
│       └── res/                          # Android 资源文件
├── docs/                                 # 项目文档（不含源代码）
│   ├── skills/                           # 7 个 GitHub Skills 仓库原始文件（参考源）
│   │   ├── elon-musk-skill-main/SKILL.md
│   │   ├── feynman-skill-main/SKILL.md
│   │   ├── munger-skill-main/SKILL.md
│   │   ├── naval-skill-main/SKILL.md
│   │   ├── steve-jobs-skill-main/SKILL.md
│   │   ├── taleb-skill-main/SKILL.md
│   │   └── zhangxuefeng-skill-main/SKILL.md
│   ├── architecture/                     # 架构与数据流说明
│   ├── decisions/                        # 关键技术决策记录
│   ├── issues/                           # Bug 与解决方案记录
│   ├── protocols/                        # API 协议与接口说明
│   ├── planning/                         # 任务计划与阶段目标
│   ├── environment/                      # 环境配置与编译说明
│   └── ai-guidance/                      # AI 工作结论与对话记录
├── workspace/
│   ├── tests/                            # 测试脚本
│   └── tools/                            # 开发辅助工具脚本
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
  - `feat: 新增多 Key 轮询熔断机制`
  - `fix: 修正 Gemini 模型名称错误`
  - `refactor: 将 systemPrompt 改为从 assets 动态加载`
  - `docs: 更新 AGENTS.md 项目规范`
- **包名**：`com.example.skillroundtable`
- **数据库版本**：每次添加字段必须升版本号并提供 Migration

---

## 6. 敏感信息处理规则

| 信息类型 | 存放位置 | 是否提交 Git |
|---------|---------|-------------|
| Gemini API Key（主 Key） | `.env` 文件 `GEMINI_API_KEY=` | ❌ 已在 `.gitignore` 排除 |
| 备用 Key 池（多 Key 轮询） | `network/ApiKeyPool.kt`（待建） | ⚠️ 评估是否放 `.env` 或代码内 |
| 密钥模板 | `.env.example`（无真实值） | ✅ 可提交 |

**禁止事项**：禁止将任何真实密钥写入 `README.md`、`AGENTS.md`、commit message 或任何可提交文件。

---

## 7. 测试与验收标准

1. `.\gradlew.bat assembleDebug` 编译零错误
2. 7 个 Skills 角色在"智囊大厅"界面正常显示
3. 用户提问后，各角色依次顺序作答（非一次性全部输出）
4. 每个角色的回答携带前序发言上下文
5. API 429 触发时，自动切换下一个可用 Key，不直接报错给用户
6. 角色回答风格与对应 `SKILL.md` 描述一致（如马斯克用极简宣言体）

---

## 8. 禁止事项

- ❌ 禁止提交 `.env` 文件到 Git
- ❌ 禁止在代码中 hardcode 真实 API Key（`ApiKeyPool.kt` 中的 Key 需评估）
- ❌ 禁止跳过 JDK 17 环境配置直接运行 Gradle（会报版本错误）
- ❌ 禁止在未确认的情况下删除 `RoundtableDatabase.kt` 中的初始角色数据
- ❌ 禁止使用 `powershell.exe`，只用 `pwsh.exe`

---

## 9. AI 工作原则

1. **先读后改**：每次任务开始，先阅读相关源文件，再动手修改
2. **验证结果**：代码修改后必须运行 `assembleDebug` 确认编译通过
3. **标注假设**：对不确定的内容明确注明"假设：..."，等待用户确认
4. **不重复询问**：已确认的结论沉淀到本文件，后续直接引用
5. **分段修改**：避免一次性替换大文件，优先使用精准的局部替换

---

## 10. 推荐 Skills（按任务场景）

| 场景 | 推荐 Skill | 使用条件 |
|------|----------|---------|
| 编写或重构 Compose UI 页面 | `@design-taste-frontend` / `@high-end-visual-design` | UI 相关任务自动加载 |
| 生成完整 Kotlin 文件（防省略） | `@full-output-enforcement` | 文件行数 > 100 行时强制启用 |
| Android SDK / ADB / 设备操作 | `@android-cli` | 需要 CLI 命令执行时 |
| Antigravity IDE 使用疑问 | `@antigravity-guide` | 工具链配置问题时 |

---

## 11. 核心文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 安卓编译指南 | [docs/environment/android-compilation-guide.md](docs/environment/android-compilation-guide.md) | JDK 17 离线编译完整方案 |
| Bug 记录 | [docs/issues/](docs/issues/) | 历史问题与解决方案 |
| API 协议说明 | [docs/protocols/gemini-api.md](docs/protocols/gemini-api.md) | Gemini REST API 使用规范 |
| 架构说明 | [docs/architecture/](docs/architecture/) | 模块划分与数据流 |
| 技术决策 | [docs/decisions/](docs/decisions/) | 关键决策记录 |

---

## 12. 已确认的技术决策

| 决策 | 结论 | 记录时间 |
|------|------|---------|
| Skills 加载方式 | SKILL.md 打包到 `app/src/main/assets/skills/`，运行时 AssetManager 读取 | 2026-07-12 |
| API 多 Key 机制 | 参考 `life-archive-app/composables/useGemini.js`，10 Key 池 + 429 熔断 24h | 2026-07-12 |
| 模型名称 | 主力 `gemini-2.0-flash`，备用 `gemini-1.5-flash`（修复现有 `gemini-3.5-flash` 错误） | 2026-07-12 |
| 数据库迁移策略 | 开发期使用 `fallbackToDestructiveMigration()`，发布前换为正式 Migration | 2026-07-12 |
