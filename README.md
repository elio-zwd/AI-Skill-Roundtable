# 见域｜AI-Skill-Roundtable（当前仓库）

> 看见更多观点，打开认知边界。

本仓库包含“见域”Android 应用及其产品、设计和工程规格。见域当前的核心产品心智已经从旧 PR08 的“持续议题 + 阶段推进”调整为 **“对话 + Skill 角色”**：用户主要是在与一个或多个具有独立人格和思维方式的 AI Skill 角色持续交流。

当前定义入口：

- [`ADR-009 — “对话 + Skill 角色”产品心智模型与多角色独立性`](docs/decisions/adr-009-skill-role-conversation-product-model.md)
- [`见域术语契约`](docs/product/jianyu-terminology.md)
- [`见域产品模型`](docs/product/jianyu-product-model.md)
- [`见域 PRD`](docs/product/jianyu-prd.md)

PR08 历史 planning/decision 文档继续保留用于追溯；若其中“议题 / 推进议题 / 邀请 Skill / Skill 必须作为用户统一上位词”等旧定义与上述当前文档冲突，以 ADR-009 和 `docs/product/` 当前文档为准。

---

## 1. 当前可运行 Android 基线

当前工程使用以下技术标识：

```text
仓库：elio-zwd/AI-Skill-Roundtable
App 名称：见域
namespace / applicationId：com.elio.jianyu
Kotlin 包路径：app/src/main/java/com/elio/jianyu/
默认版本名：0.1.0
```

当前基线主要包括：

- 原生 Android、Kotlin、Jetpack Compose 和 Material 3；
- Room 本地会话和消息数据；
- 多人物型角色问答与多角色协作基线；
- Gemini REST、Interactions 和 Live WebSocket；
- 联网搜索、Markdown 渲染、停止、继续和失败重试；
- 用户自行导入的 BYOK Key 池；
- Android Keystore + AES-GCM 的 Key 本地保护；
- 流式音频、TTS 与本地音频管理；
- Compose Route / Screen / UiState 架构。

产品定义更新不代表这些能力已经全部按新 UI 和新上下文隔离契约完成迁移。特别是多角色独立回应、Top 1 对话页和用户术语仍需后续实现 PR 逐步对齐。

---

## 2. 当前目标产品：见域

> **见域是一款以 AI Skill 角色对话为核心的个人思考与行动应用。用户可以和一个或多个具有独立人格、思维方式与能力边界的 Skill 角色持续交流，在不同观点之间形成自己的判断，并把有价值的内容沉淀为资料、下一步和成果。**

### 2.1 核心产品关系

```text
对话是 Top 1
→ 会话是持续保存的对话容器
→ Skill 是内部能力载体
→ Skill 角色是用户实际交流的参与者
→ 单角色 / 多角色自然共存
→ 默认独立回应
→ 用户可 @角色 / 增加 Skill 角色 / 发起交叉讨论
→ 资料 / 下一步 / 成果
```

用户不需要先理解“议题”“阶段”“Run”“多智能体编排”才能使用见域。

### 2.2 Skill 与 Skill 角色

这是当前最重要的定义边界：

- **Skill**：内部能力包，可包含 persona、领域知识、思维框架、工具、工作流、来源、时效和安全边界；
- **Skill 角色**：用户真正看到并交流的 AI 角色，是 Skill 能力的角色化呈现。

只要一个对象会作为聊天参与者出现，用户侧统一把它当作 **Skill 角色**，而不是机械的 Skill 工具条目。

Skill 角色应具有：

- 清晰名称；
- 头像/肖像；
- 稳定身份与人格；
- 稳定思考方式；
- 稳定表达风格；
- 可理解的擅长范围和局限。

### 2.3 可以“像真人”，但不能冒充真人

角色设计允许高度拟人化。用户知道它由 AI 驱动，因此产品无需为了避免混淆而故意把角色设计成机械工具。

对于基于真实人物构建的 Skill 角色：

- 可以使用人物化姓名、肖像和表达风格；
- 可以使用自然第一人称角色表达；
- 应在角色详情、首次使用或其他合理位置说明其为 AI 模拟角色/视角；
- 不需要每条消息重复免责声明；
- 不得把 AI 生成内容伪装成真人本人当前真实发言、授权或背书。

推荐披露：

> AI 模拟角色，基于可获得资料构建，不代表本人，也不保证复现本人当前或完整观点。

### 2.4 多角色默认平级且独立

一个会话中的多个 Skill 角色：

- 没有默认主 Skill / 副 Skill；
- 不因加入顺序形成领导关系；
- 不默认设置正方 / 反方；
- 不因为 UI 排序而表示地位高低；
- 各自按自己的角色设定、知识、证据和推理形成判断。

用户动作正式写 **增加 Skill 角色**，不再使用“邀请 Skill”。

新增角色可以赞同、补充、反驳或认为证据不足，但这些立场必须来自它自己的判断，而不是系统预设职责。

### 2.5 默认独立回应

默认情况下，一个角色形成本轮判断时主要使用：

- 用户当前问题和必要用户消息；
- 用户选择带入的资料；
- 用户选择带入的个人背景；
- 必要的中性事实背景；
- 自己的 Skill 角色设定。

**其他角色刚生成的完整结论不应自动成为它必须继承的领导性上下文。**

这条规则同样约束实现层。即使使用 Interactions API、`previous_interaction_id` 或全历史 fallback，也不能因为技术复用而让后一个角色默认被前一个角色锚定。

### 2.6 @角色与交叉讨论

用户可以通过 `@角色` 或“本次回复角色”让某个角色回答当前请求。这只影响当前请求，不会让该角色变成主角色。

**交叉讨论**必须由用户显式触发。只有这时，角色才明确把其他角色的相关观点作为讨论输入。

交叉讨论不：

- 自动无限循环；
- 自动投票决定真理；
- 建立永久主从关系。

### 2.7 “议题 / 推进议题”的当前处理

旧 PR08 曾把“议题”定义为最高层持续容器，把“推进议题”冻结为正式阶段入口。该用户心智已经被 ADR-009 覆盖。

当前正式用户表达：

| 旧表达 | 当前表达 |
|---|---|
| 议题 | 会话 |
| 议题列表 | 会话记录 |
| 议题详情 / 工作区 | 对话 |
| 邀请 Skill | 增加 Skill 角色 |
| 当前 Skill 阵容 | 参与 Skill 角色 |
| 主 Skill / 副 Skill | 不使用 |
| 推进议题 | 继续深入 |
| 阶段 | 不作为一级用户概念；必要时二级可称“对话节点” |

工程内部可以暂时保留 `topic/stage/round` 等结构，但不能把过渡实现重新解释成当前用户产品定义。

---

## 3. Top 1 UI：对话

当前移动端 UI 重构基准：

```text
设备：Xiaomi 14 Ultra
平台：Android
方向：竖屏
画布：1440 × 3200 px
语言：简体中文
```

Top 1 对话页的优先级：

1. 消息内容；
2. 角色头像、名字和消息归属；
3. 当前参与 Skill 角色；
4. 输入、发送、@角色；
5. 增加 Skill 角色；
6. 联网、思考强度、资料、交叉讨论、保存成果等二级工具。

不要把 Run ID、阶段时间线、上下文确认、策略 Override、执行历史等工程/高级控制卡长期铺在聊天主屏。

---

## 4. 资料、成果与用户控制

本次定义调整不改变以下原则：

- **资料**是输入和依据；
- **成果**是用户明确确认保存的输出；
- 普通 AI 回复不自动成为成果；
- **个人背景**只有用户主动保存和选择后才进入长期复用；
- 系统可以推荐角色、资料和下一步，但关键动作由用户确认；
- 敏感资料不应默认发送给所有角色；
- 归档不等于删除；
- 导入、导出、恢复和删除等高影响数据操作继续遵循安全规格。

---

## 5. 当前内置人物型 Skill / Skill 角色基础

当前 Android 基线已经包含一批人物型能力资产，例如：

| 人物 | 资产目录 |
|---|---|
| 埃隆·马斯克 | `elon-musk-skill-main/` |
| 理查德·费曼 | `feynman-skill-main/` |
| 查理·芒格 | `munger-skill-main/` |
| 纳瓦尔 | `naval-skill-main/` |
| 史蒂夫·乔布斯 | `steve-jobs-skill-main/` |
| 纳西姆·塔勒布 | `taleb-skill-main/` |
| 张雪峰 | `zhangxuefeng-skill-main/` |
| 安德烈·卡帕斯 | `karpathy-skill/` |
| 张一鸣 | `zhang-yiming-skill/` |
| 保罗·格雷厄姆 | `paul-graham-skill/` |
| 伊利亚·苏茨克维尔 | `ilya-sutskever-skill/` |
| 唐纳德·特朗普 | `trump-skill/` |
| 吉米·唐纳森（MrBeast） | `mrbeast-skill/` |
| 孙宇晨 | `sun-yuchen-perspective/` |
| 西格蒙德·弗洛伊德 | `freud-skill/` |
| X 增长导师 | `x-mentor-skill/` |
| 峰哥亡命天涯 | `fengge-skill/` |
| 赵长鹏（CZ） | `cz-skill/` |
| 段永平 | `duan-yongping-skill/` |
| 蒂姆·库克 | `tim-cook-skill/` |

这些目录名和内部实现可以继续使用 Skill 术语；面向用户时应逐步迁移为具有清晰人物身份的 **Skill 角色**。

---

## 6. 环境要求

| 工具 | 要求 |
|---|---|
| OS | Windows 10 x64 或其他支持 Android 构建的系统 |
| Shell | PowerShell 7（推荐） |
| JDK | JDK 17 |
| Android SDK | Platform 35 与对应 Build Tools |
| Gradle | 使用仓库 Gradle Wrapper 8.14 |
| API Key | 用户在 Android 客户端运行时导入 Gemini API Key |

---

## 7. 安装与启动

### 一键运行

```powershell
.\run.ps1
```

脚本会检查 JDK 和 adb 环境，编译、安装并启动 `com.elio.jianyu/.MainActivity`，同时输出日志。

### 手动构建

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Debug APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

### 导入 Gemini API Key

1. 启动 App；
2. 打开 API Key 管理入口；
3. 输入单个或批量 Key；
4. Key 由 Android Keystore + AES-GCM 保护并保存；
5. 界面只显示掩码。

Android App 编译和运行时不读取根目录 `.env`。`.env` 仅供开发者手动运行本地辅助脚本。

---

## 8. 构建与测试

常用命令：

```powershell
.\gradlew.bat clean
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
pwsh.exe -NoProfile -File .\tools\check-app-identity.ps1
```

涉及新产品定义的后续实现 PR，除常规构建和测试外，还应增加以下行为验收：

- 用户可见文案不再把“议题 / 推进议题 / 邀请 Skill / 主 Skill”作为核心术语；
- 多个 Skill 角色视觉平级；
- 新增角色默认独立判断；
- `@角色` 只影响当前请求；
- 交叉讨论只有用户显式触发；
- 真实人物型角色有适当 AI 模拟身份说明；
- Interactions 会话链/历史 fallback 不破坏角色独立性。

---

## 9. 安全说明

- 仓库不应包含生产 Key；
- 不要提交 `.env`、Keystore、签名密码、证书或真实用户数据；
- Android App 只管理用户自行导入的 BYOK Key；
- 文档中的目标安全行为不代表当前实现已经通过完整安全审计；
- 不使用“绝对安全”“零风险”或“永不丢失”等未经验证的承诺；
- 人物型角色可以拟人化，但不得伪造真人当前发言、授权或背书；
- 医学、法律、金融等高风险内容继续要求来源、时效、适用条件和必要的专业复核边界。

---

## 10. 目录说明

```text
AI-Skill-Roundtable/
├── app/                  # 当前 Android 应用
├── docs/
│   ├── product/          # 当前产品定义、模型与术语
│   ├── decisions/        # ADR 决策记录
│   ├── architecture/     # 架构与迁移评估
│   ├── planning/         # 历史/当前 PR 计划、规格和交接
│   ├── testing/          # 回归清单与验收说明
│   ├── environment/      # 构建环境
│   ├── protocols/        # API 与协议
│   └── skills/           # Skill 能力和角色资料
├── tools/                # 运行时辅助工具
├── test/                 # 自动化交互工具链测试
└── workspace/            # 构建期资产处理辅助区
```

---

## 11. 产品定义优先级

当前需要理解产品时，按以下顺序阅读：

1. [ADR-009：对话 + Skill 角色](docs/decisions/adr-009-skill-role-conversation-product-model.md)
2. [见域术语契约](docs/product/jianyu-terminology.md)
3. [见域产品模型](docs/product/jianyu-product-model.md)
4. [见域 PRD](docs/product/jianyu-prd.md)

历史 PR08 文档继续用于：

- 理解以前为什么选择“议题/阶段”模型；
- 查阅尚未被 ADR-009 改动的数据安全、导入导出、成果等细节；
- 追踪工程迁移背景。

但历史文档中的“冻结”声明不能覆盖后续已 Accepted 的 ADR-009。

---

## 12. 后续实现重点

产品定义更新后，后续代码/设计 PR 建议按以下顺序迁移：

1. Top 1 页面从“议题/工作区”心智迁移为 **对话**；
2. 所有用户侧参与者文案从 Skill 迁移为 **Skill 角色**；
3. “邀请 Skill”迁移为 **增加 Skill 角色**；
4. 清理“主 Skill / 副 Skill / 默认反方”等层级和预设立场；
5. 审计多角色上下文，保证 **默认独立回应**；
6. 将交叉讨论保持为用户显式动作；
7. 将“推进议题/阶段时间线”降级为必要时的内部/二级结构；
8. 根据 Xiaomi 14 Ultra 1440 × 3200 基准完成新的 Compose UI。

定义层和实现层分开推进：本次产品文档变更不等于已经完成上述代码迁移。
