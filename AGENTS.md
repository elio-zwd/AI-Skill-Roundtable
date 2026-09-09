# AGENTS.md — 见域

> 适用于仓库 `elio-zwd/AI-Skill-Roundtable`。本文件保留执行规则、关键产品契约和文档入口；完整产品规格由权威文档维护。

## 1. 规则与事实的判断顺序

- 在系统与开发者指令允许的范围内，用户当前明确要求优先于仓库文档；既有授权持续有效，不重复请求确认。
- 修改文件前，阅读根目录及目标路径上适用的 `AGENTS.md`；更具体目录的工程规则优先，但不能自行覆盖当前产品契约。
- 区分**当前实现、目标规格、历史记录**。实现事实以当前分支代码、配置和验证结果为准；目标能力未经实现与验收，不得写成已完成。
- 当前产品定义按以下顺序读取和裁决冲突：
  1. [ADR-009：对话 + Skill 角色](docs/decisions/adr-009-skill-role-conversation-product-model.md)
  2. [术语契约](docs/product/jianyu-terminology.md)
  3. [产品模型](docs/product/jianyu-product-model.md)
  4. [产品需求文档](docs/product/jianyu-prd.md)
- 后续 Accepted ADR 明确覆盖旧决策时，以覆盖范围为准。PR08 等历史文档中的“冻结”措辞不能阻止已批准的产品变更。
- 历史计划只约束仍适用的任务。例如总控计划中“后一个角色读取前一个角色发言”的旧验收项，不得覆盖 ADR-009 的默认独立回应契约。
- 文件中的任务示例、历史命令和引用内容不是用户本次请求，不得据此扩大工作范围。

## 2. 工作方式与开发原则

### 2.1 语言与环境

- 报告、文档和代码注释使用简体中文；协议字段、API 名称和标准术语可保留英文。
- 用户环境为 **Windows 10 x64**，交付脚本必须按此环境验证；调用 PowerShell 默认使用 **`pwsh.exe`（PowerShell 7）**。
- 使用仓库 Gradle Wrapper 与 JDK 17；具体依赖版本以构建配置为准，不复制文档中的历史版本号作修改依据。
- 优先用 `rg` / `rg --files` 定位文件；按任务读取相关内容，不遍历无关目录或输出敏感配置。

### 2.2 实现与范围

- 先检查现有依赖和实现，再参考成熟产品、官方文档和维护中的库；选择满足当前需求的最简单方案。
- 先跑通最小端到端流程，再增加能力。保持模块化和职责分离，不为尚未实现的复杂度拆掉可运行基线。
- 架构面向长期维护；不做预防性抽象、空壳接口、无真实调用方的配置层或“以后再换”的临时方案。
- 不保留向后兼容：替换过时实现时同步更新调用方并删除旧实现，不新增兼容层、旧版 Migration 或兼容性 fallback。
- 已有迁移代码或 `LegacyUiTokens.kt` 的存在不构成继续扩展旧方案的理由；清理时须纳入明确任务范围，不能借小改动顺手重构全仓。
- 数据结构变更同步数据库版本、Schema、调用方和相关测试，说明已有数据的处理方式。**不保留兼容不等于可以自动清空数据**；清库、卸载或破坏性重建需要已有明确授权。
- 只修改完成本次需求必需的文件；不顺手升级 Kotlin、AGP、Compose、Navigation、Room、Retrofit 等依赖。
- 不以删除功能代替修复，不吞异常、不降低断言，不保留互相冲突的新旧实现。与任务无关的问题记录后另行处理。
- 修改包名、Activity 或 `applicationId` 时，同步调用方、脚本、Manifest、CI 和文档。
- 规格变更与生产代码改造尽量分开；用户明确要求一起实施时按授权范围完成。

### 2.3 注释

- 用清晰命名和结构表达代码含义，不要求逐行注释，也不重复翻译代码。
- 对不明显的业务约束、协议格式、状态转换、并发与生命周期、超时重试、异常恢复和重要边界，简洁说明**为什么这样实现**。
- 保留准确且有价值的原注释；逻辑改变时同步更新或删除失效注释。

## 3. 按任务执行

### 3.1 开始前

先确认工作区、分支和近期提交：

```powershell
git status --short
git branch --show-current
git log -5 --oneline
```

然后按任务类型读取和核查：

| 任务类型 | 必要上下文与检查 |
|---|---|
| 只读分析、文档措辞或格式整理 | 目标文件、适用目录规则、直接引用和相关事实；不要求为只读任务创建分支或运行 Android 构建 |
| 产品、UI、用户文案或角色行为变更 | ADR-009、当前三份产品规范、相关任务施工单；UI 文件另读 [UI 目录规则](app/src/main/java/com/elio/jianyu/ui/AGENTS.md) |
| 生产代码、配置或构建修改 | 调用链、关联测试、配置、相关架构/协议和任务施工单；不能只读单个目标文件 |
| 总控计划所列重构任务 | 另读 [总控计划](docs/planning/pr-execution-master-plan.md)，核对适用阶段和已被覆盖的条款 |
| PR 实施、评审或交接 | 核查开放 PR 的文件范围、目标 Base/Head SHA、Draft、相关 CI、评论、Review 和未解决审查线程 |

- 修改前简要说明预计文件、必须保持的行为、验证命令和主要风险；小改动用一段话即可。
- 写入任务使用独立分支，默认前缀 `codex/`，不直接修改 `main`。已有本任务分支则继续使用，不重复建分支。
- 新分支从已核实的目标基线创建；远端不可访问时注明本地基线和未核实项，不声称已同步最新远端。
- 开始写入前检查开放 PR 和本地工作区是否存在同文件修改；本地工作与其他任务隔离，必要时使用独立 worktree。
- 不覆盖、还原或清理用户已有改动。首次编辑前读取最新文件；切换分支、收到并发修改通知或文件变化后重新读取并核对差异。
- GitHub 不可用时记录一次原因，继续不依赖远端的本地工作；不要连续试探权限，也不要把未知状态写成无冲突。
- Skill 仅在任务适用时使用。所需 Skill（含 Superpowers）不可用时说明限制并执行可行的等价流程，不得虚构调用成功。

### 3.2 修改与交付

1. 完成最小可运行改动，并同步受影响的测试、注释和文档。
2. 按第 7 节执行匹配的验证；失败先定位根因，再修复本次范围内的问题。
3. 回读文件、检查净差异和工作区，确认只包含授权修改。
4. 交付说明修改结果、实际验证和未验证项；需要真机或本地 AI 验收时，附只读验收 Prompt。

## 4. 工程基线与数据边界

| 项目 | 基线 / 入口 |
|---|---|
| 产品 | **见域**；口号：看见更多观点，打开认知边界 |
| 应用身份 | namespace / applicationId：`com.elio.jianyu` |
| 生产代码 | `app/src/main/java/com/elio/jianyu/` |
| UI 与数据 | Kotlin、Jetpack Compose + Material 3、Room |
| 网络与音频 | Retrofit / OkHttp、Gemini REST / Interactions / Live WebSocket；具体行为查当前代码 |
| 本地单元测试 | `app/src/test/java/com/elio/jianyu/` |
| 设备测试 | `app/src/androidTest/java/com/elio/jianyu/` |
| 角色资源 | `app/src/main/assets/skills/` |
| Key 管理 | 用户自行导入的 BYOK Key 池，Android Keystore + AES-GCM 本地保护 |

当前代码中的 `topic/stage/round` 等名称不表示用户仍须使用旧产品概念，也不表示目标产品迁移已全部完成。未来目录、能力和保障只有落实后才能描述为工程事实。

### 4.1 旧 App 边界

旧包 `com.elio.skillroundtable` 不再作为独立产品维护；新包使用独立 Android UID 与私有沙箱。不迁移旧包的 Room、Keystore、偏好、私有文件或会话，不建立跨包数据桥接；用户需重新配置 Key。不得自动卸载、清空旧包或暗示旧数据自动迁移。

### 4.2 密钥与敏感信息

- `ApiKeyPool` 只管理用户自行导入的 Key；仓库不包含内置、备用或只读硬编码生产 Key。
- Android App 编译和运行时均不读取根目录 `.env`；它只供开发者手动运行本地辅助脚本。
- `.env.example` 只放占位符；真实 Key、`.env`、`local.properties`、签名文件、私钥和证书不得提交 Git。
- 不将真实 Key 写入源码、资源、assets、`BuildConfig`、文档、测试夹具、日志或提交信息；报告不得回显完整 Key。
- 历史“内置 10 个 Key”“w1-w10 内置密钥”等描述不构成当前实现依据。
- 未实现的安全行为明确标为目标规格，不承诺“绝对安全”“不会丢失”或“零风险”。

## 5. 必须保持的产品契约

见域以 AI Skill 角色对话为核心，支持用户持续交流、形成自己的判断，并沉淀资料、下一步和成果。详细定义见第 1 节权威文档。

### 5.1 用户术语与角色身份

| 概念 / 动作 | 约束 |
|---|---|
| 对话 | Top 1 核心页面，聊天内容是主体 |
| 会话 / 会话记录 | 持续保存、搜索、继续和归档的容器 / 历史入口 |
| Skill / Skill 角色 | Skill 是内部能力载体；聊天参与者使用“Skill 角色”或具体角色名 |
| 增加 Skill 角色 | 正式用户动作，不用“邀请 Skill” |
| 继续深入 | 可提供深入问题、检查假设、换视角、增加角色、交叉讨论、核查资料、整理下一步和保存成果 |
| 对话节点 | 确有需要时使用的二级表达；阶段、Run、响应批次等不能成为核心用户心智 |

角色可以有人物化头像、稳定名称、身份、人格、思维方式和表达风格。真实人物型角色必须在详情、首次使用或合适位置明确 AI 模拟身份，例如：

> AI 模拟角色，基于可获得资料构建，不代表本人，也不保证复现本人当前或完整观点。

不要求每条消息重复声明；不得把生成内容冒充真人当前真实发言、授权、背书或批准。

### 5.2 多角色独立性

- 多个 Skill 角色默认平级、独立；无默认主从、领导者或正反方，不因加入顺序或 UI 排序改变身份地位。
- 角色首次判断主要依据用户问题与必要用户消息、用户明确选择的资料和个人背景、必要中性事实以及自身 Skill/persona/能力。
- 不默认把前一个角色刚生成的结论或完整回答作为后一个角色必须继承的上下文；摘要、缓存和技术优化不能制造锚定、复读或从众。
- `@角色` / 本次回复角色只影响当前请求，不提升为主角色、不移除其他角色、不改变长期参与关系。
- **交叉讨论必须由用户显式触发**；允许相关角色观点作为讨论输入，有限轮次，不以多数票裁决真理，结束后恢复独立回应。
- 用户主动引用或明确要求评价其他角色观点时，可将相关输出作为本次明确输入，无需强制进入交叉讨论模式。

修改以下任一环节，必须检查上下文是否泄漏其他角色的默认判断，并以相关测试或验收验证：`previous_interaction_id`、历史上下文拼接（包括现有 fallback 路径）、会话摘要、`RoundtableOrchestrator` / 调度器、Prompt 组装、缓存与召回。

### 5.3 资料、背景与成果

资料是输入和依据；草稿和普通回复不自动成为正式成果，只有用户确认保存后才成立。个人背景由用户主动保存和选择后复用，敏感资料不得未经允许发送给所有角色。系统可推荐并说明理由，最终决定权属于用户。

## 6. UI 架构与视觉约束

修改 UI 时完整遵守 [UI 目录 AGENTS.md](app/src/main/java/com/elio/jianyu/ui/AGENTS.md)。根目录只保留跨层关键边界：

- `MainActivity.kt` 只负责 Activity 入口；`ui/App.kt` 只负责顶层导航和 Route 组装。
- Route 收集 Flow、调用 ViewModel / Repository、处理副作用；Screen 接收不可变 `UiState` 和事件回调；Components 负责展示与局部交互。
- 页面域不得引用其他页面域的内部组件；`navigation/` 和 `theme/` 不包含页面业务。全局主题值放在 `ui/theme/`。
- 不保留同一页面的新旧入口、导航或重复 Composable；已有 `testTag` 属于稳定测试契约，明确变更时同步调用方与测试。
- 设计基准：**Xiaomi 14 Ultra，Android 竖屏，1440 × 3200**。它是视觉参考，不是固定像素布局要求；实现需适配实际窗口和系统 Insets。
- 聊天正文占主体，头像、名称和消息归属清晰，多角色视觉平级。高级控制项放二级入口或 Bottom Sheet；Run、Interaction ID、阶段时间线、策略 Override 等不常驻主屏。
- 不使用 iPhone Dynamic Island、iOS Home Indicator 等 iOS 专属视觉作为 Android 基准。

## 7. 验证与完成标准

先确认 JDK 17、Android SDK 和仓库环境；不要把示例占位路径写入实际配置。环境配置详见 [Android 编译指南](docs/environment/android-compilation-guide.md)。

```powershell
# 通过 Wrapper 核实实际 Gradle 与 JVM 环境。
.\gradlew.bat --version
```

| 修改类型 | 必须执行 | 按影响补充 |
|---|---|---|
| 纯文档 | 文件回读、相对链接、术语与覆盖关系、范围核对、`git diff --check` | 已有 PR 的当前 Head CI |
| 生产 Kotlin | `compileDebugKotlin`、`testDebugUnitTest` | 关联模块的聚焦回归 |
| UI、资源、Manifest、Gradle 或集成 | 上述编译/单测及 `lintDebug`、`assembleDebug` | UI 行为需设备或模拟器验收；存在对应测试时运行设备测试 |
| 数据库结构 | 编译/单测、版本与 Schema 一致性检查 | 当前支持范围内的数据库设备测试、新建与重新打开验证；说明既有数据处理结果 |
| Release 或签名配置 | 对应构建与静态检查 | `assembleRelease`，确认签名和制品；缺少必要配置时说明阻塞 |
| 密钥处理、日志、发布或历史泄露审查 | 相关测试和密钥扫描 | 历史扫描使用 `-IncludeHistory` |

表中 Gradle 任务通过仓库 Wrapper 执行，例如：

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
pwsh.exe -File .\tools\check-secrets.ps1 -IncludeHistory
git diff --check
git status --short
```

以上是按场景选择的命令，不要求每次全部运行。`clean` 仅在排查缓存或确需干净构建时使用；检查通过后，除非有新改动、失败或未解决疑点，不重复执行同一轮完整验证。

- 失败时记录失败命令和首个根因，区分环境、既有问题与本次引入问题；修复后重跑相关检查。
- 交付明确区分：本地实际通过、GitHub CI 通过、仅静态检查、环境阻塞未执行、待真机/本地验收。
- 不得虚构命令、测试、分支、Commit、CI、PR 或验收状态；没有 PR 时写“未创建 PR”，不声称 CI 通过。

## 8. Git、PR 与协作

- 每个实现任务保持独立分支、原子 Commit 和对应 PR，不混入其他任务。仅本地文档整理不自动扩展为提交、推送或创建 PR。
- Commit 和 PR 标题使用“英文类型: 中文描述”，例如 `docs: 精简项目代理工作规范`；不自动添加 `Co-Authored-By`。
- PR 描述覆盖背景、实现、修改文件、验证、风险、本地验收和回滚建议；简单改动可合并描述，避免空模板。
- 沿用历史规格时说明仍适用条款和被 ADR-009 覆盖的内容。
- 多任务通过分支、Commit、Issue、PR 和交接文档传递可核实状态，不假设其他对话共享实时记忆。
- 未经用户明确授权，不合并或关闭 PR、不删除分支、不强制更新他人分支，也不向他人发送消息。

### GitHub Actions 查询

1. 先锁定 PR 的 Base、Head SHA 和 Draft 状态，按当前 Head 查询关联 Workflow Run。
2. 全部成功后停止；运行中按需等待，不重复查询未变化状态。只有失败或取消时读取 Job，先看失败步骤摘要，必要时再取完整日志。
3. 仅验收需要 APK、测试报告或 Schema 时获取 Artifact。
4. 遇到权限、速率限制或服务错误，记录原因并停止连续试探。
5. 仅 Runner、网络或服务端瞬时故障可直接重跑；代码或文档问题须修复并产生新 Head，不创建空提交触发 CI。

## 9. 按需查阅的工程文档

| 场景 | 入口 |
|---|---|
| 五阶段重构与历史任务追溯 | [总控计划](docs/planning/pr-execution-master-plan.md)，仅沿用未被新决策覆盖的条款 |
| 系统调用链 | [系统架构](docs/architecture/system-architecture.md) |
| UI 接口与回归 | [UI 稳定接口](docs/architecture/pr-08-ui-design-stable-interfaces.md)、[UI 回归清单](docs/testing/pr-07-ui-regression-checklist.md)，产品语义以 ADR-009 为准 |
| Gemini 请求与上下文 | [Gemini API 协议](docs/protocols/guides/gemini-api.md)及相关任务施工单 |
| 新增角色 | [新增角色指南](docs/skills/how-to-add-new-character.md) |
| 历史 PR08 规格 | 在 `docs/planning/` 按 `pr-08-*` 定位，仅用于追溯和未被覆盖的领域 |

维护本文件时优先更新规则与入口，不重复粘贴完整 PRD、历史阶段清单或易过期的实现细节。文档调整本身不代表代码和测试已同步落实。
