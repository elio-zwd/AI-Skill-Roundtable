# 见域（AI-Skill-Roundtable）

> 看见更多观点，打开认知边界。

见域是一款面向个人的多智能体思考与行动工作台。用户围绕持续议题，调用不同专业视角与工作流能力，通过自由追问和分阶段推进，沉淀判断、行动方案与知识成果。

---

## 产品定位

见域提供两类可以在同一议题中自然切换的价值：

- **现实支持**：处理生活、学习和工作中的具体事情，例如任务拆解、规划、沟通、研究、写作、决策和成果交付；
- **思维拓展**：引入不同人物、领域、立场、反方意见和思维模型，帮助用户重新理解问题、发现盲区并拓展认知边界。

产品支持两种并列使用模式：

- **单 Skill**：用于明确问题、垂直任务或持续咨询；
- **多 Skill**：用于复杂议题、重大决策、长期成长和跨领域协作。

“圆桌”是多 Skill 的主要协作形式，但不等于整个产品。人物视角只是 Skill 的一种类型；专业顾问、任务助手和工作流能力与其并列。

### 核心产品关系

```text
问题是入口
→ Skill 是能力载体
→ 单 Skill / 多 Skill 是使用模式
→ 议题持续承载背景与上下文
→ 自由追问 / 阶段推进 / 专业工作流
→ 判断 / 行动方案 / 知识成果
```

---

## 当前实现与目标产品的关系

当前 Android 生产代码仍建立在原“AI 智囊圆桌”多角色应用基线上，已经具备多角色回答、Room 本地会话、Gemini 接入、联网搜索、Markdown、BYOK Key 池、遥测和音频管理等能力。

仓库正在进行“见域”产品迁移前的规格收敛：

```text
PR01～PR07：现有 Android 工程、业务与 UI 基线
→ PR08：产品定义、信息架构、交互、Skill、品牌视觉与技术迁移规格
→ PR08-F：整合并冻结最终规格
→ PR09：依据冻结规格修改生产代码
```

重要边界：

- **PR08 只做文档、研究、设计和迁移评估，不修改 Android 生产代码。**
- **PR09 才实施导航、页面、Room、资源、配置和兼容迁移。**
- 当前 README 使用“见域”描述目标产品；旧代码、包名和部分界面仍可能保留“AI 智囊圆桌”名称，这是待 PR09 处理的兼容基线，不代表产品定位仍以 20 位名人为中心。

---

## 当前 Android 基线能力

| 能力 | 当前说明 |
|---|---|
| 多角色回答与圆桌协作 | 当前代码支持多个内置 Skill 参与回答，后续将按见域规格扩展为单／多 Skill 与持续议题模型 |
| 用户 BYOK Key 池 | 支持在客户端导入和管理用户自己的 Gemini API Key，密钥使用 Android Keystore 保护 |
| 联网搜索与 Markdown | 支持联网信息获取与 Markdown 内容展示 |
| 本地会话 | 使用 Room 保存当前会话、消息和相关状态 |
| 音频与 TTS | 保留现有音频生成、播放和管理能力，未来在信息架构中的位置待 PR08 规格确认 |
| Skill 资产 | 当前包含 20 个以人物视角为主的内置 Skill；第一版见域仍只提供官方内置 Skill，但不再把人物型 Skill 作为唯一中心 |
| Compose UI 基线 | PR07 已建立 Route / Screen / Component / UiState 等结构和回归门禁，目标视觉与产品交互将在 PR08 设计、PR09 实现 |

---

## 当前内置人物型 Skills

以下目录描述的是**当前 Android 基线中已有的 20 个内置人物型 Skill**，不是见域未来全部 Skill 类型，也不代表相关真实人物本人参与、认可或提供意见。

| 角色 | 核心标签 / 决策 DNA | 分配音色 | Skill 源文件 |
|---|---|---|---|
| 埃隆·马斯克 | 第一性原理 · 五步工作法 · 白痴指数 | **Fenrir** | `elon-musk-skill-main/SKILL.md` |
| 理查德·费曼 | 反术语 · 货物崇拜 · 六年级测试 | **Sadaltager** | `feynman-skill-main/SKILL.md` |
| 查理·芒格 | 多元思维模型 · 逆向思考 · 太难筐 | **Gacrux** | `munger-skill-main/SKILL.md` |
| 纳瓦尔 | 特定知识 · 无需许可的杠杆 · 无限游戏 | **Charon** | `naval-skill-main/SKILL.md` |
| 史蒂夫·乔布斯 | 极简 · 端到端控制 · 死亡过滤器 | **Kore** | `steve-jobs-skill-main/SKILL.md` |
| 纳西姆·塔勒布 | 反脆弱 · 切肤之痛 · 杠铃策略 | **Algenib** | `taleb-skill-main/SKILL.md` |
| 张雪峰 | 就业倒推 · 家庭背景分流 · 社会筛子论 | **Orus** | `zhangxuefeng-skill-main/SKILL.md` |
| 安德烈·卡帕斯 | 深度学习 · 代码即算法 · 神经网络本质 | **Achird** | `karpathy-skill/SKILL.md` |
| 张一鸣 | 延迟满足感 · 空间复杂度与认知 · 务实 | **Schedar** | `zhang-yiming-skill/SKILL.md` |
| 保罗·格雷厄姆 | 创投 · 做出人们需要的东西 · 独立思考 | **Rasalgethi** | `paul-graham-skill/SKILL.md` |
| 伊利亚·苏茨克维尔 | 人工智能安全 · 技术趋势 · 探索真理 | **Achernar** | `ilya-sutskever-skill/SKILL.md` |
| 唐纳德·特朗普 | 交易 · 对抗节奏 · 赢家思维 | **Pulcherrima** | `trump-skill/SKILL.md` |
| 吉米·唐纳森（MrBeast） | 注意力 · 极限测试 · 内容增长 | **Sadachbia** | `mrbeast-skill/SKILL.md` |
| 孙宇晨 | Web3 · 营销 · 认知套利 | **Laomedeia** | `sun-yuchen-perspective/SKILL.md` |
| 西格蒙德·弗洛伊德 | 精神分析 · 冰山模型 · 潜意识 | **Vindemiatrix** | `freud-skill/SKILL.md` |
| X 增长导师 | 海外内容 · 社交平台 · 增长 | **Zubenelgenubi** | `x-mentor-skill/SKILL.md` |
| 峰哥亡命天涯 | 纪实旅行 · 平民视角 · 黑色幽默 | **Umbriel** | `fengge-skill/SKILL.md` |
| 赵长鹏（CZ） | 去中心化 · 系统效率 · 加密行业 | **Algieba** | `cz-skill/SKILL.md` |
| 段永平 | 平常心 · 本分 · 价值判断 | **Sulafat** | `duan-yongping-skill/SKILL.md` |
| 蒂姆·库克 | 供应链 · 平稳管理 · 商业运营 | **Despina** | `tim-cook-skill/SKILL.md` |

> 人物型 Skill 是 AI 根据公开材料构建的模拟视角，不代表真实人物本人观点。医疗、法律、金融等高风险事项不得仅依赖人物模拟或 AI 建议作出决定。

---

## PR08 当前文档入口

PR08 处于“规格冻结前”的产品收敛阶段，相关 Draft PR 采用堆叠方式维护：

- PR #20：见域产品定义与体验设计总规划；
- PR #21：议题阶段推进结构；
- PR #22：产品规格收敛审阅稿。

关键文档：

- [PR08 产品定义工作笔记](docs/planning/pr-08-product-definition-working-notes.md)
- [PR08 总计划](docs/planning/pr-08-jianyu-product-redesign-plan.md)
- [PR08 任务清单](docs/planning/pr-08-jianyu-product-redesign-tasks.md)
- [PR08 多对话交接](docs/planning/pr-08-jianyu-parallel-handoff.md)
- [议题推进结构](docs/planning/pr-08-jianyu-issue-advancement-planning.md)
- [产品规格收敛审阅稿](docs/planning/pr-08-jianyu-product-spec-review-draft.md)

在这些文档完成审核和规格冻结前，不启动 PR09 生产实现。

---

## 环境要求

| 工具 | 要求 |
|---|---|
| OS | Windows 10 x64，或其他支持 Android 构建的系统 |
| Shell | PowerShell 7（`pwsh.exe`） |
| JDK | JDK 17 |
| Android SDK | Android SDK Platform 35 及对应 Build Tools |
| Gradle | 仓库自带 Wrapper 8.14 `-bin` |
| API Key | 用户在 Android 客户端运行时手动导入的 Google Gemini API Key |

---

## 安装与启动

### 一键部署与日志追踪

项目内置 `run.ps1`，用于检测 JDK 和 adb 环境、构建、安装并启动应用：

```powershell
.\run.ps1
```

终端中按 `Ctrl + C` 可退出日志追踪。

### 手动构建与安装

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Debug APK 默认生成于：

```text
app\build\outputs\apk\debug\app-debug.apk
```

### 运行时导入 Gemini API Key

1. 启动应用并进入 API Key 管理入口；
2. 输入一个或多个用户自有 Gemini API Key；
3. Key 由 Android Keystore 加密后保存在应用私有目录；
4. 界面只显示掩码，不应在日志、源码或文档中回显完整 Key。

`.env` 只供开发者手动运行本地辅助脚本，Android App 编译和运行时均不读取根目录 `.env`。

---

## 构建、测试与调试

```powershell
# 清理
.\gradlew.bat clean

# 编译
.\gradlew.bat compileDebugKotlin

# 单元测试
.\gradlew.bat testDebugUnitTest

# Lint
.\gradlew.bat lintDebug

# 构建 Debug APK
.\gradlew.bat assembleDebug

# 查看任务
.\gradlew.bat tasks
```

具体执行范围以 [AGENTS.md](AGENTS.md) 和当前任务文档为准。纯文档 PR 不需要为了形式运行 Android 构建，但必须如实记录未执行项。

---

## 目录说明

```text
AI-Skill-Roundtable/
├── app/                     # Android 应用模块
│   └── src/main/
│       ├── java/            # Kotlin 源代码
│       └── assets/skills/   # 当前内置 Skill 资产
├── docs/
│   ├── product/             # 见域产品模型、术语与正式 PRD
│   ├── design/              # UX、品牌、视觉与页面规格
│   ├── skills/              # Skill 分类、研究与扩展说明
│   ├── architecture/        # 系统架构、稳定接口和迁移评估
│   ├── decisions/           # ADR 技术决策记录
│   ├── protocols/           # Gemini 等协议说明
│   └── planning/            # 计划、任务、交接与交付报告
├── tools/                   # 运行时 ADB 和辅助工具
├── test/                    # 自动化交互工具链测试
├── workspace/               # 构建期辅助区
├── .env.example             # 本地辅助脚本密钥模板
├── AGENTS.md                # AI 代理工作规范
└── README.md                # 本文件
```

---

## 常用命令速查

```powershell
# 一键编译安装、启动并输出日志
.\run.ps1

# 仅构建，不安装或跟踪日志
.\run.ps1 -SkipInstall -NoLogcat

# 环境初始化
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 编译与安装
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug

# 清理
.\gradlew.bat clean
```

---

## 其他文档入口

- [AI 代理规范](AGENTS.md)
- [Android 编译指南](docs/environment/android-compilation-guide.md)
- [Gemini API 协议](docs/protocols/gemini-api.md)
- [系统架构](docs/architecture/system-architecture.md)
- [PR07 UI 回归清单](docs/testing/pr-07-ui-regression-checklist.md)
- [历史 Bug 记录](docs/bugs/)
- [早期重构总控计划](docs/planning/pr-execution-master-plan.md)

---

## 当前限制与待完成事项

1. 当前生产 UI 和数据模型仍是旧版多角色应用结构，尚未完成见域迁移；
2. 议题、阶段推进、成果、个人背景、本地加密备份和恢复快照仍处于规格阶段；
3. 第一版见域只计划提供官方内置 Skill，不包含用户自定义、导入或公开市场；
4. “见域”的商标、应用商店名称、域名和重名情况尚未完成正式核验；
5. PR08-A～E、PR08-F 和 PR09 均须按最新规划、用户授权和真实 GitHub 状态推进；
6. 未经用户明确授权，不合并相关 Draft PR，也不提前修改生产代码。
