# AGENTS.md — 见域（当前仓库：AI-Skill-Roundtable）

> AI 代理工作规范。进入仓库后先阅读本文件，再阅读距离目标文件最近的 `AGENTS.md`、当前规划和任务施工单。更具体目录中的规则优先。

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

以上是**当前实现事实**。应用名称和技术身份已经迁移，但持续议题、阶段、资料成果、完整品牌视觉和后续数据模型仍由 PR09 后续任务实施。

### 1.2 目标产品

目标产品名称为 **见域**，口号为：

> 看见更多观点，打开认知边界

正式定义：

> 见域是一款面向个人的多智能体思考与行动工作台。用户围绕持续议题，调用人物视角、专业顾问、任务助手和工作流能力，通过自由追问与分阶段推进，沉淀判断、行动方案和知识成果。

当前已完成的技术标识：

```text
App 名称：见域
applicationId / namespace：com.elio.jianyu
```

仍属于未来目标：

```text
目标仓库名：jianyu-workbench
目标官网：jianyu.my-elio.online
```

不得把仓库名、官网、完整产品模型或完整品牌视觉描述为已经完成。

### 1.3 旧 App 数据边界

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

当前可信顺序：

```text
PR #20：已合并，冻结产品定义、品牌、PR08 总计划与协作规则
→ PR #21：已合并，冻结“推进议题”与阶段推进契约
→ PR #22：已合并，收敛第 1～62 题并更新仓库规则
→ PR08-A～E：已完成
→ PR08-F：已完成并获得用户批准
→ PR09：正在按独立任务实施生产代码、数据模型、品牌和技术标识迁移
```

当前门禁：

- PR08 不修改 Android 生产代码、测试、Room、资源、Manifest、Gradle、CI 或仓库设置；
- PR09 每个任务使用独立分支、Commit 和 Draft PR 阶段；
- 当前文档中的目标行为只有在对应代码、测试和验收完成后才能写成已实现；
- 未经用户明确授权不得标记 Ready、合并、关闭 PR 或删除分支。

任何历史文档中“PR08 直接逐屏修改 Compose”“PR08-F 前即可开始 PR09”或“`com.elio.jianyu` 尚未迁移”的表述均已失效。

---

## 3. 已冻结的见域产品契约

### 3.1 产品对象

```text
问题是入口
Skill 是能力载体
议题是持续容器
阶段是议题内推进节点
圆桌是多 Skill 协作形式
成果是判断、行动方案、知识笔记或可交付内容
```

“轮次”只可描述模型或成员的一次响应批次，不是最高层产品对象。

### 3.2 两类核心价值

正式名称仅为：

- **现实支持**
- **思维拓展**

规则：

- 可跳过、单独选择或组合；
- 可在同一议题中反复切换；
- 不是永久标签；
- 不自动拆成两条议题主线；
- “生活与工作”“思维与视角”“现实行动”不得作为正式分类名称。

### 3.3 单 Skill 与多 Skill

- 单 Skill 与多 Skill 并列；
- 单 Skill 可邀请其他 Skill，升级为多 Skill；
- 多 Skill 中点名某个 Skill 只产生临时定向回答；
- 临时定向回答不自动退出多 Skill；
- Skill 增删不删除历史回答、资料或成果；
- 系统可以推荐 Skill，但必须说明理由并由用户确认。

### 3.4 推进议题

正式入口名称为 **推进议题**，不是暂定名称。

- 入口始终可用，阶段成熟时可增强提示；
- 不强迫用户必须完成当前阶段；
- 自由追问用于继续当前阶段；
- 推进议题用于建立新的明确阶段目标；
- “下一轮”只作为历史术语；
- 推进议题不等于让全部角色重复发言。

三步确认：

```text
第一步：选择推进方向
第二步：选择具体措施或自定义目标
第三步：确认下一阶段摘要
```

只有第三步最终确认后才创建新阶段。

正式方向和措施：

**思维拓展**

- 引入反方意见；
- 查找遗漏视角；
- 检查关键假设；
- 比较不同立场；
- 深入某个问题；
- 自定义目标。

**现实支持**

- 明确下一步；
- 形成执行计划；
- 分析执行阻碍；
- 生成成果交付；
- 设置检查节点；
- 自定义目标。

默认继承：

- 议题背景；
- 已确认资料；
- 已保存成果；
- 当前 Skill 阵容；
- 已形成判断；
- 主要分歧；
- 当前行动项。

用户可调整：

- 下一阶段目标；
- Skill；
- 资料；
- 输出形式。

阶段关系：

- 新阶段仍属于原议题；
- 旧阶段完整保存；
- 不覆盖原阶段；
- 一个议题保持单一主线；
- 不自动创建分支议题；
- 不自动复制互相冲突的历史副本。

### 3.5 圆桌结果

圆桌不投票裁决真理。收束结果应包含：

- 共识；
- 分歧；
- 适用条件；
- 明确建议；
- 下一步。

系统可以给出推荐及理由，用户保留最终决定权。

---

## 4. 当前可信工程事实

| 项目 | 当前值 |
|---|---|
| 应用名称 | 见域 |
| applicationId / namespace | `com.elio.jianyu` |
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose 2.8.4 |
| 数据库 | Room v5 |
| JDK | JDK 17 |
| Gradle | Wrapper 8.14 `-bin` |
| Compile / Target SDK | 35 |
| Min SDK | 26 |
| 网络 | Retrofit、OkHttp、WebSocket |
| API Key | 用户自行导入的 BYOK Key 池，最多 50 个 |
| Key 存储 | Android Keystore + AES-GCM，密文位于 `noBackupFilesDir` |

### API Key 不可变事实

- 仓库不包含内置、备用或只读硬编码生产 Key；
- `ApiKeyPool` 只管理用户在 App 内自行导入的 Key；
- Android App 编译和运行时都不读取根目录 `.env`；
- `.env` 仅供开发者手动运行本地辅助脚本；
- 文档中的“内置 10 个 Key”“w1-w10 内置密钥”等属于旧架构残留。

---

## 5. 开始任务前必须执行

1. 阅读根目录 `AGENTS.md`；
2. 阅读目标目录下最近的 `AGENTS.md`；
3. 阅读 `docs/planning/pr-execution-master-plan.md` 和当前任务施工单；
4. 执行：

```powershell
git status --short
git branch --show-current
git log -5 --oneline
```

5. 检查开放 PR、目标 Base SHA、相关 CI、评论、Review 和审查线程；
6. 读取调用链、关联测试、配置和文档，不得只读取单个目标文件；
7. 修改前列出预计文件、行为冻结点、验证命令和主要风险；
8. 从最新目标基线创建独立分支，不直接修改 `main`；
9. 检查是否与其他对话或开放 PR 修改相同文件；
10. 每次写入前重新读取目标分支上的最新文件；
11. 若任务属于 PR08，确认写入范围仅包含授权文档或设计资产；
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
- 已存在的 `testTag` 属于稳定测试契约。

这些规则描述当前工程基线。PR09 实施时必须继续遵守，除非最终迁移规格明确更新。

---

## 8. 修改范围纪律

- 只修改完成当前需求所必需的文件；
- 不顺手升级 Kotlin、AGP、Compose、Navigation、Room 或 Retrofit；
- 不以删除功能代替修复，不吞异常，不降低断言；
- 不保留互相冲突的新旧实现；
- 修改数据库实体时同步版本、Migration、Schema 和测试；
- 修改包名、Activity 或 `applicationId` 时同步脚本、Manifest、CI 和文档；
- 历史 ADR 可保留背景，但必须明确历史状态；
- 无关 Bug 另开 PR，不在规格、设计或迁移评估 PR 中顺带修复；
- 不强制更新、删除他人分支、合并或关闭 PR，除非用户明确授权。

---

## 9. 敏感信息与数据安全

| 信息类型 | 正确位置 | 是否提交 Git |
|---|---|---|
| Android App BYOK Key | App 内导入；Keystore 加密后存入 `noBackupFilesDir` | 否 |
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
- 术语、状态和阶段边界一致性；
- 净差异只包含授权路径；
- `git diff --check`；
- PR 当前 Head 的 CI。

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
- 未经授权不标记 Ready、不合并、不关闭、不删除分支；
- 完成后提供本地 AI 只读验收 Prompt。

---

## 13. 核心文档

| 文档 | 路径 |
|---|---|
| 总控计划 | `docs/planning/pr-execution-master-plan.md` |
| PR08 产品定义工作笔记 | `docs/planning/pr-08-product-definition-working-notes.md` |
| PR08 总计划 | `docs/planning/pr-08-jianyu-product-redesign-plan.md` |
| PR08 任务清单 | `docs/planning/pr-08-jianyu-product-redesign-tasks.md` |
| PR08 多对话交接 | `docs/planning/pr-08-jianyu-parallel-handoff.md` |
| 议题推进契约 | `docs/planning/pr-08-jianyu-issue-advancement-planning.md` |
| 产品规格审阅稿 | `docs/planning/pr-08-jianyu-product-spec-review-draft.md` |
| 第 1～62 题决策索引 | `docs/planning/pr-08-jianyu-product-spec-decision-index.md` |
| 第 56～61 题补充 | `docs/planning/pr-08-jianyu-product-spec-supplement-56-61.md` |
| 第 62 题补充 | `docs/planning/pr-08-jianyu-product-spec-supplement-62.md` |
| PR09-01 应用身份迁移计划 | `docs/planning/pr-09-01-jianyu-app-identity-plan.md` |
| 系统架构 | `docs/architecture/system-architecture.md` |
| 当前 UI 稳定接口 | `docs/architecture/pr-08-ui-design-stable-interfaces.md` |
| UI 回归清单 | `docs/testing/pr-07-ui-regression-checklist.md` |
| Android 编译指南 | `docs/environment/android-compilation-guide.md` |
| Gemini API 协议 | `docs/protocols/gemini-api.md` |
| 新增角色指南 | `docs/skills/how-to-add-new-character.md` |
