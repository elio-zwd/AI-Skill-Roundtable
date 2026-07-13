# AGENTS.md — AI 智囊圆桌 (AI-Skill-Roundtable)

> AI 代理工作规范文件。每次进入本工作区，**必须先阅读本文件**，再开始任何修改。

---

## 1. 项目背景与当前阶段

### 1.1 项目简介
**AI 智囊圆桌**是一款原生 Android 聊天应用。用户提问后，7 个 GitHub Skills 角色（Elon Musk、Feynman、Munger、Naval、Steve Jobs、Taleb、张雪峰）依次轮流作答，每位角色回答后自动携带完整上下文，让下一位角色看到前人发言并进行评论、反驳或补充，形成真实的圆桌脑暴效果。

### 1.2 当前阶段
- **阶段**：并发回复交互、Markdown渲染与离线语音管理层（v2.0）全面交付，编译全量通过。
- **已交付目标**：
  1. **多角色并发回复**：使用并发协程组同时拉取所有角色的回答，配合多角色 typing 思考指示器。
  2. **反检测节奏策略**：配置 1~3s 首发错峰延迟与 2~6s 同 Key 内串行间隔，跨 Key 组间实现并发。
  3. **横向滑动轮次气泡**：采用 HorizontalPager 分轮滑动展示智囊团交锋气泡，底栏支持头像指示器快速定位。
  4. **Markdown 与一键导出**：AI 言论全面 Markdown 渲染，顶栏一键复制或免权限 MediaStore 保存 Markdown 文档至系统目录。
  5. **Gemini Live 极速 TTS**：连接 WebSocket 流式获取 PCM 并直存 44字节 WAV 无损头，实现首次秒播；预设 7 大智囊专属音色。
  6. **后台 MediaCodec 转码 AAC**：利用 CoroutineWorker 与原生 MediaCodec 异步打包 ADTS 头部压缩 WAV 为 AAC，节省 85%+ 空间。
  7. **🎵 音频管理 Tab**：底部增加第三 Tab 入口，具备体积统计、在线播放、全文折叠、手动转码、一键删除等功能。
  8. **API 熔断调试面板**：设置页提供强对比度 Debug 对话框，监控 10 个内置 Key 熔断倒计时，并保存最近 50 条请求日志。
  9. **人格组合预设与 SKILL.md 可视化**：大厅顶端配置 4 套组合预设卡片并支持自定义另存/长按删除；席位抽屉动态加载并 Markdown 渲染角色的 SKILL.md 思维模型。

---

## 2. 技术栈与关键依赖

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material3 | BOM 最新 |
| 构建脚本 | Kotlin DSL (`.gradle.kts`) | — |
| 数据持久化 | Room Database | 当前 v5 |
| 网络 | Retrofit2 + OkHttp + WebSocket | — |
| 序列化 | kotlinx.serialization | — |
| AI API | Google Gemini REST & Live WebSocket | v1beta / v1alpha |
| 后台异步任务 | WorkManager (Transcode) | — |
| 依赖库 | `com.github.jeziellago:compose-markdown` | 0.5.4 |
| JDK | JDK 17 | 17.0.19+10 |
| 编译工具 | Gradle 8.14-all | — |

**关键依赖路径**：
- JDK 17：`D:\My_Elio\dev-tools\jdk-17.0.19+10`
- Gradle 缓存：本地离线缓存（无需联网）
- Markdown 仓库源：在 `settings.gradle.kts` 中加入了 `https://jitpack.io`

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
│       │   ├── MainActivity.kt           # Compose UI 入口与路由分发
│       │   ├── AudioLibraryScreen.kt     # (新增) 离线音频库管理界面
│       │   ├── audio/                    # (新增) 播控与转码底层
│       │   │   ├── AudioPlaybackManager.kt # MediaPlayer 单例控制
│       │   │   └── AudioTranscodeWorker.kt # CoroutineWorker 后台 MediaCodec 音频转码
│       │   ├── data/                     # Room 实体 / DAO / 数据库 / Repository
│       │   │   ├── Character.kt          # 实体（新增 skillDescriptionVector, voiceConfig）
│       │   │   ├── ChatSession.kt        # 实体 (新增 roundIndex, audioFilePath, audioFormat, audioSizeBytes)
│       │   │   └── RoundtableDatabase.kt # 数据库版本升级到 5，集成 Migration 3->4, 4->5
│       │   ├── network/                  # 接口模块
│       │   │   ├── GeminiApi.kt          # REST 接口与 Broker 拼装
│       │   │   ├── LiveApiClient.kt      # (新增) WebSocket Gemini Live 接口 (WAV直存)
│       │   │   └── ApiKeyPool.kt         # Key 池轮询熔断与随机分组及日志遥测
│       │   ├── skill/                    # Skills 动态加载与 Broker 读取判定
│       │   │   └── SkillLoader.kt
│       │   └── viewmodel/
│       │       └── RoundtableViewModel.kt# 搭载 Cosine Similarity 余弦路由与双模型 Broker 拼装
│       ├── assets/skills/                # 动态打包的 7 个角色完整技能文件夹
│       ├── assets/skills_config.json     # 动态 Seeding 及配置总表 (含 voiceConfig 预设)
│       └── res/                          # Android 资源文件
├── docs/                                 # 项目文档（不含源代码）
│   ├── planning/                         # 任务计划 (新增 v2_0_implementation_plan.md / v2_0_walkthrough.md)
│   ├── architecture/                     # 架构与数据流说明
│   ├── decisions/                        # 关键技术决策记录
│   ├── issues/                           # Bug 与解决方案记录
│   ├── protocols/                        # API 协议与接口说明
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
2. 7 个 Skills 角色在大厅与圆桌中能基于 `skills_config.json` 一站式扩充展示，点击可弹出 BottomSheet Markdown 渲染其画像
3. 问答支持 HorizontalPager 分轮横向滑动，指示器头像高亮与点击跳转工作正常
4. 气泡和导出文档 Markdown 解析及 MediaStore 写入正确，无权限申请
5. Debug 弹窗可精确显示 Key 频控状态和遥测日志（含 Prompt）
6. 点击 🔊 首次产生 WAV 离线文件并立即发声，二次播放走本地缓存
7. 后台转码 AAC 工作正常，原 WAV 被删除，大小显示为 AAC 比例且能正常播放
8. 音频库界面支持全文展开/收起、手动转码、一键清空等功能

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
| API 协议说明 | [docs/protocols/gemini-api.md](docs/protocols/gemini-api.md) | Gemini API (含 Embedding、Broker、Live WebSocket) 规范 |
| 架构说明 | [docs/architecture/system-architecture.md](docs/architecture/system-architecture.md) | 双模型 Broker 与语义路由系统数据流 |
| 音频引擎架构 | [docs/architecture/audio-engine-architecture.md](docs/architecture/audio-engine-architecture.md) | (新增) Gemini Live WebSocket 收流与后台压缩转码详细设计 |
| 新增角色指南 | [docs/skills/how-to-add-new-character.md](docs/skills/how-to-add-new-character.md) | 一键动态扩增全新智囊角色的操作指南 |
| 三模型级联与联网接地 ADR | [docs/decisions/adr-005-three-model-cascade-with-google-search-grounding.md](docs/decisions/adr-005-three-model-cascade-with-google-search-grounding.md) | 记录三模型级联联网搜索接地设计决策 |

---

## 12. 已确认的技术决策

| 决策 | 结论 | 记录时间 |
|------|------|---------|
| 数据库 Seeding 模式 | Scheme B：由 `extract_skills_metadata.py` 物理除二进制大文件，并在 `DatabaseCallback` 通过解析 assets/skills_config.json 全动态入库 | 2026-07-12 |
| 会话级 Key 绑定 | 通过 `SharedPreferences` 让每个 `sessionId` 绑定唯一的 API Key，仅在 429 报错时换绑重试，最大化命中隐式前缀缓存 | 2026-07-12 |
| 双模型级联 Broker 路由 | 使用 `gemini-3.1-flash-lite-preview` 快速选出相关少样本文件，加载拼装后送主力 `gemini-3.5-flash` (high thinking) 回答 | 2026-07-12 |
| 语义自适应路由 | 通过 `text-embedding-004` 将角色介绍转为 768 维向量存库。提问时计算问题余弦相似度，降序对席位重排以实现“专家先发”模式 | 2026-07-12 |
| 极速语音直存 (WAV) | 使用 Gemini Live WebSocket 接口实时提取 PCM base64 数据包，流结束时追加 44 字节 WAV 头部信息直存为 `.wav` 文件（无延迟播放） | 2026-07-13 |
| 后台 MediaCodec 转码 (AAC) | WorkManager 后台任务配合原生 MediaCodec 硬件压缩 PCM 帧，前置 ADTS 头部（7字节）输出高保真 AAC 覆盖原 WAV | 2026-07-13 |
| 反检测请求节奏限制 | 并发组随机分组，组内串行，各组错峰 1-3s 延迟启动，同一组角色间强制 2-6s 随机请求间隔以降低 API 屏蔽风险 | 2026-07-13 |
| 三模型级联联网搜索配合 | 由 3.1lite 决策本地资料及提取多 searchQuery，2.5flash 使用 google_search 工具多路联网检索接地并做空 query 兜底，最终由 3.5flash (high thinking) 整合输出高保真作答 | 2026-07-13 |
| 多档联网模式调节 | UI 增加 SMART（自适应）、FORCE（强制，使用提问兜底）、OFF（禁用）三档胶囊切换控制，控制是否进行联网及搜索数目分配 | 2026-07-13 |
