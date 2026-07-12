# AI 智囊圆桌 (AI-Skill-Roundtable)

> 多角色轮询式 AI 聊天 Android 应用 — 用 7 位思想巨匠的视角，对你的问题展开一场真实的脑暴圆桌。

---

## 项目简介

**AI 智囊圆桌**是一款原生 Android 聊天 App。用户提交一个问题，7 个 AI 技能角色（Skills）就像圆桌上的与会者一样，**逐一顺序作答**：每位角色回答完毕后，下一位看到所有前序发言，并在此基础上评论、补充或反驳，形成真正的多智囊脑暴。

### 7 位智囊
| 角色 | 标签 | Skill 文件 |
|------|------|-----------|
| 🪐 埃隆·马斯克 | 第一性原理 · 五步工作法 · 白痴指数 | `elon-musk-skill-main/SKILL.md` |
| 🥁 理查德·费曼 | 反术语 · 货物崇拜 · 六年级测试 | `feynman-skill-main/SKILL.md` |
| 👴 查理·芒格 | 多元思维模型 · 逆向思考 · 太难筐 | `munger-skill-main/SKILL.md` |
| 🧘 纳瓦尔 | 特定知识 · 无需许可的杠杆 · 无限游戏 | `naval-skill-main/SKILL.md` |
| 🍎 史蒂夫·乔布斯 | 极简 · 端到端控制 · 死亡过滤器 | `steve-jobs-skill-main/SKILL.md` |
| 🏋️ 纳西姆·塔勒布 | 反脆弱 · 切肤之痛 · 杠铃策略 | `taleb-skill-main/SKILL.md` |
| 👨‍🏫 张雪峰 | 就业倒推 · 家庭背景分流 · 社会筛子论 | `zhangxuefeng-skill-main/SKILL.md` |

---

## 当前状态

| 功能 | 状态 |
|------|------|
| Compose 圆桌 UI（席位图、消息气泡、打字指示器） | ✅ 完成 |
| Room 数据库（角色 / 会话 / 消息三张表） | ✅ 完成 |
| 顺序答复调度（携带上下文轮询） | ✅ 完成 |
| 7 个 GitHub Skills 完整 systemPrompt 加载 | 🚧 开发中 |
| 多 Key 轮询 + 429 熔断机制 | 🚧 开发中 |
| API 模型名错误修复 | 🚧 开发中 |

---

## 环境要求

| 工具 | 要求 |
|------|------|
| OS | Windows 10 x64 |
| Shell | PowerShell 7（`pwsh.exe`） |
| JDK | JDK 17（路径：`D:\My_Elio\dev-tools\jdk-17.0.19+10`） |
| Android SDK | 已通过 Gradle 离线缓存配置 |
| Gradle | 8.14-all（离线模式，无需联网） |
| API Key | Google Gemini API Key（填入 `.env`） |

---

## 安装与启动

### 1. 配置 API 密钥

复制密钥模板并填入真实值：

```powershell
Copy-Item .env.example .env
# 编辑 .env，填入 GEMINI_API_KEY=你的Key
```

### 2. 配置 JDK 环境（每次新开终端都需要）

```powershell
$env:JAVA_HOME = "D:\My_Elio\dev-tools\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
java -version   # 应输出 openjdk 17
```

### 3. 编译 Debug 包

```powershell
.\gradlew.bat assembleDebug
```

生成的 APK 路径：`app\build\outputs\apk\debug\app-debug.apk`

### 4. 安装到设备

```powershell
.\gradlew.bat installDebug
```

---

## 构建、测试与调试

```powershell
# 清理旧产物
.\gradlew.bat clean

# 仅检查编译是否通过（不生成 APK）
.\gradlew.bat compileDebugKotlin

# 运行单元测试
.\gradlew.bat test

# 查看 Gradle 任务列表
.\gradlew.bat tasks
```

---

## 目录说明

```
AI-Skill-Roundtable/
├── app/                     # Android 应用模块
│   └── src/main/
│       ├── java/            # Kotlin 源代码
│       └── assets/skills/   # [待建] 7 个 SKILL.md 文件（打包进 APK）
├── docs/                    # 项目文档
│   ├── skills/              # GitHub Skills 原始文件（参考源）
│   ├── architecture/        # 架构与模块说明
│   ├── decisions/           # 技术决策记录
│   ├── issues/              # Bug 记录
│   ├── protocols/           # API 接口说明
│   ├── planning/            # 任务计划
│   ├── environment/         # 环境配置说明
│   └── ai-guidance/         # AI 工作结论
├── workspace/
│   ├── tests/               # 测试脚本
│   └── tools/               # 开发辅助工具
├── .env                     # API 密钥（不提交）
├── .env.example             # 密钥模板
├── AGENTS.md                # AI 代理工作规范
└── README.md                # 本文件
```

---

## 常用命令速查

```powershell
# 环境初始化
$env:JAVA_HOME = "D:\My_Elio\dev-tools\jdk-17.0.19+10"; $env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 编译
.\gradlew.bat assembleDebug

# 安装
.\gradlew.bat installDebug

# 清理
.\gradlew.bat clean
```

---

## 文档入口

- [AI 代理规范](AGENTS.md) — AI 工作规范、命名规约、技术决策
- [安卓编译指南](docs/environment/android-compilation-guide.md) — JDK 17 离线编译完整方案
- [API 协议说明](docs/protocols/gemini-api.md) — Gemini REST API 使用说明
- [架构文档](docs/architecture/) — 模块边界与数据流说明
- [历史 Bug 记录](docs/issues/) — 已知问题与解决方案

---

## 已知限制与待解决问题

1. **API 模型名错误**：`GeminiApi.kt` 中 `gemini-3.5-flash` 不存在，导致所有请求失败
2. **单 Key 无熔断**：429 错误时直接报错给用户，无 Key 轮换机制
3. **Skills systemPrompt 简化**：现有 hardcode 内容比 GitHub 上的 SKILL.md 精简很多，角色表现有差距
4. **数据库 v1**：添加新字段需要 Migration，开发期先用 `fallbackToDestructiveMigration()`
