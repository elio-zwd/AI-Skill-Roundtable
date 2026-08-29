# AGENTS.md — 见域（当前仓库：AI-Skill-Roundtable）

> AI 代理工作规范。进入仓库后先阅读本文件，再阅读距离目标文件最近的 `AGENTS.md`、当前产品定义、当前规划和任务施工单。更具体目录中的规则优先。

---

## 1. 当前工程事实与目标产品

### 1.1 当前可运行 Android 基线

当前仓库和 Android 生产代码已经使用见域应用身份：

```text
仓库：elio-zwd/AI-Skill-Roundtable
App 用户可见名称：见域
namespace / applicationId：com.elio.jianyu
Kotlin 包路径：app/src/main/java/com/elio/jianyu/
```

当前应用是一套可运行的原生 Android 多角色聊天基线，包含 Room 本地会话、Gemini REST / Interactions / Live WebSocket、联网搜索、Markdown、用户自带 Key 池、遥测和音频管理。

以上是**当前实现事实**。产品定义已经更新为“对话 + Skill 角色”，但现有路由、数据模型、历史用户文案和编排上下文不代表已经全部完成迁移。

### 1.2 当前目标产品

目标产品名称为 **见域**，口号为：

> 看见更多观点，打开认知边界

当前正式定义：

> **见域是一款以 AI Skill 角色对话为核心的个人思考与行动应用。用户可以和一个或多个具有独立人格、思维方式与能力边界的 Skill 角色持续交流，在不同观点之间形成自己的判断，并把有价值的内容沉淀为资料、下一步和成果。**

当前产品定义由以下文件共同约束，按顺序优先：

1. `docs/decisions/adr-009-skill-role-conversation-product-model.md`
2. `docs/product/jianyu-terminology.md`
3. `docs/product/jianyu-product-model.md`
4. `docs/product/jianyu-prd.md`

PR08 历史 planning/decision 文档继续保留用于追溯；当其中“议题 / 推进议题 / 邀请 Skill / Skill 必须作为用户统一上位词”等旧冻结定义与 ADR-009 冲突时，**ADR-009 和当前 `docs/product/` 文档优先**。

### 1.3 当前产品心智模型

```text
对话是 Top 1
会话是持续保存的用户容器
Skill 是内部能力载体
Skill 角色是用户实际交流的参与者
多个 Skill 角色默认平级、独立判断
增加 Skill 角色不会产生主从或默认正反方
@角色只影响本次回复
交叉讨论必须由用户显式触发
资料与成果保持区分
```

用户不需要理解“议题”“阶段”“Run”“响应批次”“Agent 编排”等工程术语才能使用产品。

### 1.4 旧 App 数据边界

当前 App 尚未正式发布，应用身份已从 `com.elio.skillroundtable` 迁移到 `com.elio.jianyu`，并冻结以下边界：

- 不保留旧 App 作为独立产品；
- 不迁移旧包 `com.elio.skillroundtable` 的 Room 数据；
- 不迁移旧 Keystore、偏好设置、私有文件或本地会话；
- 新包使用独立 Android UID 和私有沙箱；
- 用户需要在新包中重新配置 API Key；
- 不设计跨包数据桥接；
- 不自动卸载或清空旧包。

不得在代码或文档中暗示旧数据会自动迁移。

---

## 2. 当前阶段与执行顺序

历史顺序：

```text
PR #20～#22：PR08 产品定义阶段
→ PR08-A～F：历史产品规格整合
→ PR09：生产代码、数据模型、品牌和技术标识迁移
→ ADR-009（2026-08-29）：产品心智重组为“对话 + Skill 角色”，覆盖冲突的 PR08 冻结术语
→ 后续 PR：按 ADR-009 逐步迁移 UI、文案、编排上下文与数据语义
```

执行规则：

- 历史 PR08 文档不得被当作不可覆盖的“永远冻结”规则；后续 Accepted ADR 可以明确变更产品契约；
- 每个实现任务使用独立分支、Commit 和 PR；
- 当前文档中的目标行为只有在对应代码、测试和验收完成后才能写成已实现；
- 未经用户明确授权不得合并、关闭 PR 或删除分支；
- 规格变更 PR 与生产代码迁移 PR 应尽量分离，除非任务明确要求一起实施；
- 遇到历史文档冲突时先判断它是“历史依据”还是“当前规范”，不要为了满足旧措辞逆向修改当前产品定义。

---

## 3. 当前见域产品契约

### 3.1 对话与会话

正式用户术语：

- **对话**：Top 1 核心页面；
- **会话**：可持续保存、搜索、继续和归档的对话容器；
- **会话记录**：历史会话入口。

旧“议题 / 议题列表 / 议题详情”不再是当前核心用户术语。

工程内部可以暂时保留 `topic/stage/round` 等过渡结构，但不得因为当前实现存在这些结构，就重新把“议题”写回正式用户心智。

### 3.2 Skill 与 Skill 角色

必须区分：

- **Skill**：内部能力包/能力载体；
- **Skill 角色**：用户实际看见、选择和交流的 AI 角色。

只要对象作为聊天参与者出现，用户侧统一使用 **Skill 角色** 或具体角色名。

Skill 角色允许并鼓励具有：

- 人物化头像/肖像；
- 稳定名称；
- 清晰身份；
- 稳定人格；
- 稳定思考方式；
- 稳定表达风格。

不能因为“不能冒充真人”而故意把角色设计成无人格工具。

### 3.3 真实人物型 Skill 角色

真实人物型角色可以高度人物化，但必须保持身份真实性。

推荐在角色详情、首次使用或合适位置说明：

> AI 模拟角色，基于可获得资料构建，不代表本人，也不保证复现本人当前或完整观点。

不要求每条消息重复免责声明，但不得把 AI 生成内容表述为真人本人当前真实发言、授权、背书或批准。

### 3.4 多角色平级

一个会话中的多个 Skill 角色默认：

- 平级；
- 独立；
- 无主 Skill / 副 Skill；
- 无默认领导者；
- 无默认正方 / 反方；
- 不因加入顺序改变身份地位；
- 不因 UI 排序表示权力。

用户增加参与者时正式写 **增加 Skill 角色**，不用“邀请 Skill”。

### 3.5 默认独立回应

多角色默认独立回应是**强产品契约**，不是只用于文案的描述。

角色形成本轮首次判断时主要基于：

- 用户当前问题和必要用户消息；
- 用户明确选择的资料；
- 用户明确选择的个人背景；
- 必要的中性事实背景；
- 该角色自己的 Skill/persona/能力。

默认不得把前一个角色刚生成的完整回答作为后一个角色必须继承的领导性上下文。

因此修改以下实现时必须审计角色独立性：

- Interactions `previous_interaction_id`；
- fallback 全量历史拼接；
- 会话摘要；
- RoundtableOrchestrator / 调度器；
- Prompt 组装；
- 上下文缓存与召回。

技术优化不能让角色 B 因为角色 A 先说话而系统性复读或从众。

### 3.6 @角色 / 本次回复角色

用户可以通过 `@角色` 或选择器让某个 Skill 角色回答当前请求。

这只影响当前请求：

- 不提升为主角色；
- 不移除其他角色；
- 不改变长期角色关系；
- 不让其他角色以后自动服从它。

### 3.7 交叉讨论

**交叉讨论必须由用户显式触发。**

只有此时，参与角色才把其他角色的相关观点作为明确讨论输入。

交叉讨论：

- 不无限自动循环；
- 不通过多数票裁决真理；
- 不建立永久主从关系；
- 完成后默认恢复独立回应。

### 3.8 继续深入

旧“推进议题”不再是冻结正式入口。

当前自然用户动作是 **继续深入**，可根据上下文提供：

- 深入问题；
- 检查假设；
- 增加 Skill 角色；
- 换视角；
- 交叉讨论；
- 核查资料；
- 整理下一步；
- 保存成果。

“阶段”可作为内部/局部工作流结构，必要时用户侧二级表达为“对话节点”，不得在 Top 1 聊天页常驻成主视觉。

### 3.9 资料与成果

- 资料是输入和依据；
- 草稿不是正式成果；
- 成果只有用户明确确认保存后成立；
- 个人背景只有用户主动保存和选择后长期复用；
- 敏感资料不应未经用户允许发送给所有角色；
- 系统可以给出推荐及理由，用户保留最终决定权。

---

## 4. 当前可信工程事实

开始实现任务前必须以当前分支代码和最近迁移文档再次核对版本，不得仅凭本文件的历史数字做修改决策。

当前稳定身份事实：

| 项目 | 当前值 |
|---|---|
| 应用名称 | 见域 |
| applicationId / namespace | `com.elio.jianyu` |
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| JDK | 17 |
| Gradle | 使用仓库 Wrapper |
| 网络 | Retrofit / OkHttp / WebSocket / Gemini APIs |
| API Key | 用户自行导入的 BYOK Key 池 |
| Key 存储 | Android Keystore + AES-GCM 本地保护 |

### API Key 不可变事实

- 仓库不包含内置、备用或只读硬编码生产 Key；
- `ApiKeyPool` 只管理用户在 App 内自行导入的 Key；
- Android App 编译和运行时都不读取根目录 `.env`；
- `.env` 仅供开发者手动运行本地辅助脚本；
- 历史文档中的“内置 10 个 Key”“w1-w10 内置密钥”等属于旧架构残留，不得重新实现为当前事实。

---

## 5. 开始任务前必须执行

1. 阅读根目录 `AGENTS.md`；
2. 阅读目标目录下最近的 `AGENTS.md`；
3. 产品/UI 任务先阅读 ADR-009 和 `docs/product/` 当前三份规范；
4. 阅读 `docs/planning/pr-execution-master-plan.md` 和当前任务施工单（若存在）；
5. 执行：

```powershell
git status --short
git branch --show-current
git log -5 --oneline
```

6. 检查开放 PR、目标 Base SHA、相关 CI、评论、Review 和审查线程；
7. 读取调用链、关联测试、配置和文档，不得只读取单个目标文件；
8. 修改前列出预计文件、行为冻结点、验证命令和主要风险；
9. 从最新目标基线创建独立分支，不直接修改 `main`；
10. 检查是否与其他对话或开放 PR 修改相同文件；
11. 每次写入前重新读取目标分支上的最新文件；
12. 若 Superpowers 技能不可用，明确使用等价人工流程，不得假装调用成功。

---

## 6. 当前目录与职责

当前 Android 代码目录：

```text
app/src/main/java/com/elio/jianyu/
├── MainActivity.kt
├── audio/
├── data/
├── network/
├── roundtable/
├── skill/
├── telemetry/
├── viewmodel/
└── ui/
    ├── AGENTS.md
    ├── App.kt
    ├── LegacyUiTokens.kt
    ├── navigation/
    ├── theme/
    ├── components/
    └── screens/
```

当前测试目录：

```text
app/src/test/java/com/elio/jianyu/
app/src/androidTest/java/com/elio/jianyu/
```

当前文档目录：

```text
docs/
├── product/
├── decisions/
├── architecture/
├── planning/
├── testing/
├── environment/
├── protocols/
└── skills/
```

未来规划中的新目录只有在实际创建后，才可描述为当前工程事实。

---

## 7. UI 架构规则

目标文件位于 `ui/` 时，还必须遵守 `app/src/main/java/com/elio/jianyu/ui/AGENTS.md`。

- `MainActivity.kt` 只保留 Activity 入口；
- `ui/App.kt` 只负责顶层导航和页面 Route 组装；
- `Route` 收集 Flow、调用 ViewModel / Repository、处理副作用；
- `Screen` 只接收不可变 `UiState` 与事件回调；
- `Components` 只负责展示和局部交互；
- 页面域不得跨包引用其他页面域内部组件；
- `navigation/` 与 `theme/` 不得包含页面专属逻辑；
- 全局主题值只在 `ui/theme/` 定义；
- `LegacyUiTokens.kt` 只能做兼容别名；
- 不保留同一页面的新旧两套入口、导航或重复 Composable；
- 新抽象必须有真实调用方和测试；
- 已存在的 `testTag` 属于稳定测试契约，除非对应产品迁移任务明确更新测试契约。

### 7.1 Top 1 对话 UI 额外规则

当前移动 UI 设计基准：Xiaomi 14 Ultra，Android 竖屏，1440 × 3200。

- 聊天内容是绝对主体；
- Skill 角色头像、名称和消息归属清晰；
- 多角色视觉默认平级；
- “增加 Skill 角色”是正式用户动作；
- `@角色`/本次回复角色不能被实现成长期主角色；
- 高级控制项默认收进二级入口或 Bottom Sheet；
- 不把 Run、Interaction ID、阶段时间线、上下文确认、策略 Override 等工程内容长期铺在主屏；
- 不采用 iPhone Dynamic Island、iOS Home Indicator 等 iOS 专属视觉作为 Android 基准。

---

## 8. 修改范围纪律

- 只修改完成当前需求所必需的文件；
- 不顺手升级 Kotlin、AGP、Compose、Navigation、Room 或 Retrofit；
- 不以删除功能代替修复，不吞异常，不降低断言；
- 不保留互相冲突的新旧实现；
- 修改数据库实体时同步版本、Migration、Schema 和测试；
- 修改包名、Activity 或 `applicationId` 时同步脚本、Manifest、CI 和文档；
- 历史 ADR 和 planning 文档可保留背景，但必须明确历史状态；
- 无关 Bug 另开 PR，不在规格、设计或迁移评估 PR 中顺带修复；
- 不强制更新、删除他人分支、合并或关闭 PR，除非用户明确授权。

---

## 9. 敏感信息与数据安全

| 信息类型 | 正确位置 | 是否提交 Git |
|---|---|---|
| Android App BYOK Key | App 内导入；Keystore 加密后本地保存 | 否 |
| 本地辅助脚本 Key | 根目录 `.env` | 否 |
| 模板占位符 | `.env.example` | 是 |
| 签名文件、私钥、证书 | 本机或安全 CI Secret | 否 |

禁止将真实 Key 写入源码、Markdown、提交信息、日志样例、测试夹具、`BuildConfig`、资源或 assets。交付报告不得回显完整 Key。

目标安全行为必须写成“待实现规格”，不得使用“绝对安全”“不会丢失”“零风险”等未经实现和验证的承诺。

---

## 10. 构建、测试与真实性

Windows 基础环境：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat --version
```

生产代码基础修改至少执行：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

UI、资源、Manifest、Gradle 或完整集成修改按需要执行：

```powershell
.\gradlew.bat clean
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat connectedDebugAndroidTest
pwsh.exe -File .\tools\check-secrets.ps1 -IncludeHistory
git diff --check
git status --short
```

纯文档 PR 至少检查：

- 文件回读和相对链接；
- 当前术语与 ADR-009 一致；
- 历史规格和当前规格的覆盖关系明确；
- 净差异只包含授权路径；
- `git diff --check`；
- PR 当前 Head 的 CI（如配置文档门禁）。

交付报告必须区分：

- 已实际执行并通过；
- GitHub CI 已通过；
- 仅完成静态检查；
- 因环境限制未执行；
- 等待真机或本地验收。

不得虚构命令、测试、Commit、分支、CI 或 PR 状态。

---

## 11. GitHub Actions 查询纪律

1. 先读取 PR 元数据并锁定 Base、Head 和 Draft 状态；
2. 每个新 Head SHA 查询关联 Workflow Run；
3. 全部成功后停止，不下载无关日志或 Artifact；
4. 只有失败或取消时读取 Job；
5. 先读失败步骤摘要，必要时再读完整日志；
6. 只在验收需要 APK、测试报告或 Schema 时读取 Artifact；
7. 遇到权限、速率限制或服务错误后停止连续试探并记录；
8. 只有 Runner、网络或服务端瞬时故障才直接重跑；
9. 代码或文档问题必须修复并产生新 Head；
10. 不为触发 CI 创建空提交。

---

## 12. Commit、PR 与多对话交接

- Commit 和 PR 标题使用“英文类型: 中文描述”；
- Commit 保持原子性，不自动添加 `Co-Authored-By`；
- PR 描述至少包含背景、实现、修改文件、验证、风险、本地验收和回滚建议；
- 一个对话只负责一个任务、一个分支和一个 PR；
- 多对话通过 Issue、分支、Commit、PR 描述和评论交接；
- 开始前检查开放 PR，避免修改相同文件；
- 不假设其他对话共享实时记忆；
- 未经授权不合并、不关闭、不删除分支；
- 完成后提供本地 AI 只读验收 Prompt（如果任务需要本地验收）。

---

## 13. 核心文档

### 当前产品定义

| 文档 | 路径 |
|---|---|
| ADR-009：对话 + Skill 角色 | `docs/decisions/adr-009-skill-role-conversation-product-model.md` |
| 当前术语契约 | `docs/product/jianyu-terminology.md` |
| 当前产品模型 | `docs/product/jianyu-product-model.md` |
| 当前 PRD | `docs/product/jianyu-prd.md` |

### 工程与执行

| 文档 | 路径 |
|---|---|
| 总控计划 | `docs/planning/pr-execution-master-plan.md` |
| 系统架构 | `docs/architecture/system-architecture.md` |
| 当前 UI 稳定接口 | `docs/architecture/pr-08-ui-design-stable-interfaces.md` |
| UI 回归清单 | `docs/testing/pr-07-ui-regression-checklist.md` |
| Android 编译指南 | `docs/environment/android-compilation-guide.md` |
| Gemini API 协议 | `docs/protocols/gemini-api.md` |
| 新增角色指南 | `docs/skills/how-to-add-new-character.md` |

### 历史产品规格（追溯用）

以下文档仍可用于理解历史决定，但产品语义发生冲突时不得覆盖 ADR-009：

- `docs/planning/pr-08-product-definition-working-notes.md`
- `docs/planning/pr-08-jianyu-product-redesign-plan.md`
- `docs/planning/pr-08-jianyu-product-redesign-tasks.md`
- `docs/planning/pr-08-jianyu-parallel-handoff.md`
- `docs/planning/pr-08-jianyu-issue-advancement-planning.md`
- `docs/planning/pr-08-jianyu-product-spec-review-draft.md`
- `docs/planning/pr-08-jianyu-product-spec-decision-index.md`
- `docs/planning/pr-08-jianyu-product-spec-supplement-56-61.md`
- `docs/planning/pr-08-jianyu-product-spec-supplement-62.md`

如果后续任务要继续使用这些历史规格中的非冲突部分，PR 描述必须说明哪些条款仍被沿用、哪些已由 ADR-009 覆盖。
