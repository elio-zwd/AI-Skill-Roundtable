# AI 智囊圆桌 (AI-Skill-Roundtable)

> 多角色轮询式 AI 聊天 Android 应用 — 用 20 位思想巨匠的视角，对你的问题展开一场真实的脑暴圆桌。

---

## 项目简介

**AI 智囊圆桌**是一款原生 Android 聊天 App。用户提交一个问题，20 个 AI 技能角色（Skills）就像圆桌上的与会者一样，**逐一顺序作答**：每位角色回答完毕后，下一位看到所有前序发言，并在此基础上评论、补充或反驳，形成真正的多智囊脑暴。

### 🎨 全员统一莫兰迪 AI 肖像 (v2.1)
项目已剔除 Emoji 头像，全员 20 位智囊已全部升级为统一美学的 **“低饱和度莫兰迪极简插画风” AI 高画质肖像**，具备 Editorial 杂志级的高级感。

### 📋 20 位智囊清单

| 角色 | 核心标签 / 决策 DNA | 分配音色 (音色特点) | 技能 (Skill) 物理源文件 |
|------|--------------------|-------------------|-----------------------|
| 埃隆·马斯克 | 第一性原理 · 五步工作法 · 白痴指数 | **Fenrir** (Excitable 亢奋极客) | `elon-musk-skill-main/SKILL.md` |
| 理查德·费曼 | 反术语 · 货物崇拜 · 六年级测试 | **Sadaltager** (Knowledgeable 博学大师) | `feynman-skill-main/SKILL.md` |
| 查理·芒格 | 多元思维模型 · 逆向思考 · 太难筐 | **Gacrux** (Mature 沉稳长者) | `munger-skill-main/SKILL.md` |
| 纳瓦尔 | 特定知识 · 无需许可的杠杆 · 无限游戏 | **Charon** (Informative 冷静思考者) | `naval-skill-main/SKILL.md` |
| 史蒂夫·乔布斯 | 极简 · 端到端控制 · 死亡过滤器 | **Kore** (Firm 坚定而极具煽动性) | `steve-jobs-skill-main/SKILL.md` |
| 纳西姆·塔勒布 | 反脆弱 · 切肤之痛 · 杠铃策略 | **Algenib** (Gravelly 粗粝反叛) | `taleb-skill-main/SKILL.md` |
| 张雪峰 | 就业倒推 · 家庭背景分流 · 社会筛子论 | **Orus** (Firm 洪亮坚定且极接地气) | `zhangxuefeng-skill-main/SKILL.md` |
| 安德烈·卡帕斯 | 深度学习 · 代码即算法 · 神经网络本质 | **Achird** (Friendly 亲切而有条理) | `karpathy-skill/SKILL.md` |
| 张一鸣 | 延迟满足感 · 空间复杂度与认知 · 务实 | **Schedar** (Even 极度克制与平静) | `zhang-yiming-skill/SKILL.md` |
| 保罗·格雷厄姆 | 创投教父 · 做出人们需要的东西 · 独立思考 | **Rasalgethi** (Informative 逻辑启发式) | `paul-graham-skill/SKILL.md` |
| 伊利亚·苏茨克维尔 | 技术先知 · 人工智能安全 · 无限逼近真理 | **Achernar** (Soft 深邃而谦逊温和) | `ilya-sutskever-skill/SKILL.md` |
| 唐纳德·特朗普 | 交易的艺术 · 强对抗节奏 · 赢家通吃 | **Pulcherrima** (Forward 直白且极富攻击性) | `trump-skill/SKILL.md` |
| 吉米·唐纳森 (MrBeast) | 注意力引擎 · 极限测试 · 极致流量曝光 | **Sadachbia** (Lively 活泼充满戏剧张力) | `mrbeast-skill/SKILL.md` |
| 孙宇晨 | Web3 杠杆 · 顶级营销术 · 认知套利 | **Laomedeia** (Upbeat 亢奋高昂的营销风) | `sun-yuchen-perspective/SKILL.md` |
| 西格蒙德·弗洛伊德 | 精神分析学 · 冰山模型 · 潜意识映射 | **Vindemiatrix** (Gentle 温和细致的心理流) | `freud-skill/SKILL.md` |
| X 增长导师 | 海外流量选题 · 社交媒体算法 · 快速增长密钥 | **Zubenelgenubi** (Casual 随性自如的播主风) | `x-mentor-skill/SKILL.md` |
| 峰哥亡命天涯 | 纪实旅行自媒体 · 平民视角 · 黑色幽默冷眼旁白 | **Umbriel** (Easy-going 慵懒随性的冷幽默) | `fengge-skill/SKILL.md` |
| 赵长鹏 (CZ) | 去中心化精神 · 极高系统效率 · 实用加密精神 | **Algieba** (Smooth 自信圆融的极客腔) | `cz-skill/SKILL.md` |
| 段永平 | 平常心投资法 · 本分价值观 · 避开不对的事 | **Sulafat** (Warm 温暖朴实的本分大叔) | `duan-yongping-skill/SKILL.md` |
| 蒂姆·库克 | 极致供应链管理 · 平稳过渡艺术 · 商业操盘手 | **Despina** (Smooth 温润平稳的中庸管理腔) | `tim-cook-skill/SKILL.md` |

---

## 当前状态

| 功能模块 | 交付状态 | 核心实现细节 |
|------|------|-------------|
| **多智囊脑暴调度** | ✅ 完成 | 自动拼装前序上下文，组内错峰串行间隔，跨组并发调用调度 |
| **API 备用池与熔断保护** | ✅ 完成 | 内置 10 个 API Key 随机负载均衡，在 429 发生时自动启动倒计时熔断隔离 |
| **流式 PCM 音频与 TTS 极速秒播** | ✅ 完成 | Live WebSocket 直接下发 PCM 帧追加 44 字节 WAV 头秒播，后台 ADTS MediaCodec 转码 AAC 压缩 |
| **音频大厅与库管理面板** | ✅ 完成 | 离线音频批量查看、在线播控、一键全量清理与体积压缩监测 |
| **智囊大厅预置/自定义分组** | ✅ 完成 | Room v4 注入四大官方预设组，右上角星标一键将激活角色另存为自定义分组 |
| **画册风 ModalBottomSheet 画像详情** | ✅ 完成 | 点击大厅卡片拉出抽屉，流式读取 SKILL.md 大纲并基于 MarkdownRender 极简渲染其决策 DNA |
| **莫兰迪极简 AI 高画质肖像** | ✅ 完成 | 全员 20 人头像全 JPG 化物理入库，Assets 本地零依赖安全流式加载，支持 Monogram 汉字降级 |


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
