# PR 07：UI 基础架构重构多对话交接说明

> 总计划：`docs/planning/pr-07-ui-foundation-refactor-plan.md`
>
> 任务清单：`docs/planning/pr-07-ui-foundation-refactor-tasks.md`
>
> 仓库：`https://github.com/elio-zwd/AI-Skill-Roundtable`

本文件用于多个 ChatGPT / Codex 对话之间通过 GitHub 可靠交接。不同对话不共享可靠实时记忆，所有状态以 GitHub 的 PR、Commit、评论和 CI 为准。

---

## 1. 并行原则

1. 一个对话只负责一个任务、一个分支和一个 PR。
2. PR07-A、PR07-B、PR07-F 必须串行。
3. 只有 PR07-C、PR07-D、PR07-E 可以并行。
4. 并行对话不得修改对方独占目录。
5. 共享文件在 PR07-B 合并后冻结；确需修改时先报告，不自行抢改。
6. 开始开发前必须检查开放 PR 和最新 `main` SHA。
7. 每次写文件前重新读取目标分支最新版本。
8. 未经用户授权不得合并 PR、删除分支或强制更新分支。

---

## 2. 正确启动顺序

### 第一步：规划 PR

先合并只包含规划文档的 PR。规划 PR 不修改生产代码。

### 第二步：开启 PR07-A 对话

PR07-A 完成、CI 和本地验收通过、合并后，才开启 PR07-B。

### 第三步：开启 PR07-B 对话

PR07-B 完成、CI 和真机返回路径验收通过、合并后，记录新的 `main` SHA。

### 第四步：同时开启三个对话

从 PR07-B 合并后的同一个 `main` SHA 创建：

- PR07-C：圆桌页面；
- PR07-D：智囊页面；
- PR07-E：音频库与设置域。

三个对话可以并行，但必须保持目录边界。

### 第五步：全部合并后开启 PR07-F

PR07-F 负责最终架构审查、测试门禁、文档和稳定基线，不继续大规模重构页面。

---

## 3. 每个新对话的统一启动要求

将对应 Prompt 发给新对话后，执行 AI 必须先：

1. 检查 Superpowers 与 GitHub 是否可用；
2. 选择适用的 Superpowers 技能；
3. 读取 `README.md`、`AGENTS.md`、总计划、任务清单和本文件；
4. 检查开放 PR；
5. 确认目标 Base SHA；
6. 读取本任务所有相关源码、测试和 CI；
7. 输出计划、预计修改文件、行为冻结点和风险；
8. 创建独立分支后再写入；
9. 完成后使用 `verification-before-completion`；
10. 创建 Draft PR，不自动合并；
11. 输出本地只读验收 Prompt。

若 Superpowers 技能端点未暴露，应明确说明并人工执行等价流程，不得假装调用成功。

---

## 4. PR07-A 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR07-A：Compose UI 机械拆分与包结构重构。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
refactor/pr-07a-ui-file-extraction

Base：
请读取当前最新 main，并确认它包含 UI 基础重构规划文档。不要使用过期 SHA。

必须先读取：
1. README.md
2. AGENTS.md
3. docs/planning/pr-execution-master-plan.md
4. docs/planning/pr-07-ui-foundation-refactor-plan.md
5. docs/planning/pr-07-ui-foundation-refactor-tasks.md
6. docs/planning/pr-07-ui-foundation-parallel-handoff.md
7. MainActivity.kt、AudioLibraryScreen.kt、ApiKeyManagerScreen.kt
8. 相关测试、开放 PR 和 CI

工作流：
- 优先使用 Superpowers:writing-plans；
- 执行时使用 Superpowers:executing-plans；
- 完成前使用 Superpowers:verification-before-completion；
- 准备 PR 时使用 Superpowers:finishing-a-development-branch。

目标：
只做机械拆文件、package/import 调整和职责归位。保持现有页面布局、文案、颜色值、业务调用、导航方式和全部 testTag 不变。MainActivity.kt 最终只保留入口与顶层 App 调用。本 PR 不引入正式 NavHost，不重新设计 UI，不修改 ViewModel、Room、网络、TTS 或遥测业务。

要求：
- 开始前检查开放 PR，避免同域冲突；
- 从最新 main 创建目标分支；
- 每次写入前重读目标文件；
- 原子 Commit；
- 运行 compileDebugKotlin、testDebugUnitTest、lintDebug、assembleDebug、assembleRelease；
- 能运行时执行 connectedDebugAndroidTest；
- 创建 Draft PR，标题：refactor: 拆分 Compose UI 文件结构；
- 不合并；
- 输出给本地 AI 的只读验收 Prompt。

不确定且会改变产品行为时再向我确认；小范围文件命名按现有风格自行决定。
```

---

## 5. PR07-B 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR07-B：统一 Compose 主题、正式导航与顶层状态边界。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
refactor/pr-07b-ui-theme-navigation

前置条件：
PR07-A 已合并。请先读取 PR07-A、最新 main SHA、CI 和本地验收记录；若未合并，不要开始写代码。

必须先读取：
README.md、AGENTS.md、pr-execution-master-plan.md、PR07 三份规划文档、PR07-A PR Diff、当前 ui 目录、app/build.gradle.kts。

工作流：
- 使用 Superpowers:writing-plans；
- 使用 Superpowers:test-driven-development 设计导航状态测试；
- 使用 Superpowers:verification-before-completion；
- 使用 Superpowers:finishing-a-development-branch。

目标：
建立统一 ui/theme，原样迁移现有深色视觉值；建立 AppDestination 与 NavHost；圆桌、智囊、音频为顶层目标，API Key 与遥测为二级目标；明确底部导航、顶部返回和系统返回行为。不得重新设计 UI，不引入 Material 3 Adaptive，不修改业务、Room 或协议。

共享约束：
本 PR 合并后 ui/App.kt、navigation、theme 将成为 PR07-C/D/E 的冻结共享契约，因此必须提供清晰、稳定、最小的入口。

验证：
compileDebugKotlin、testDebugUnitTest、lintDebug、assembleDebug、assembleRelease、可用时 connectedDebugAndroidTest；真机验证顶层切换、API Key→遥测→返回、系统返回和进程重建。

创建 Draft PR，标题：refactor: 统一 Compose 主题与顶层导航。不合并，并输出本地只读验收 Prompt。
```

---

## 6. PR07-C 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR07-C：圆桌页面 UI 架构重构。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
refactor/pr-07c-roundtable-ui-architecture

前置条件：
PR07-B 已合并。必须从 PR07-B 合并后的最新 main SHA 创建分支。先检查 PR07-D、PR07-E 是否已开启，并确认目录边界。

独占写入目录：
app/src/main/java/com/elio/skillroundtable/ui/screens/roundtable/
以及本任务对应测试文件。

冻结共享文件：
ui/App.kt、ui/navigation/、ui/theme/、其他页面目录。确需修改时先报告，不直接写入。

必须先读取 PR07 三份规划文档、PR07-A/PR07-B PR、RoundtableViewModel、当前圆桌页面和相关测试。

使用 Superpowers:writing-plans、test-driven-development、verification-before-completion、finishing-a-development-branch。

目标：
建立 RoundtableRoute、RoundtableScreen、不可变 RoundtableUiState；拆分会话抽屉、顶栏、席位状态、对话列表、轮次、消息气泡、Typing、动作区、失败重试条和输入区；将 ChatItem/groupMessages 变成可单测纯逻辑。保持 SSE、Pending、停止、继续、重试、联网模式、导出和 TTS 行为以及全部 testTag 不变。

禁止修改 ViewModel 业务算法、网络、Room、TTS 协议、智囊/音频/设置页面。

创建 Draft PR，标题：refactor: 重构圆桌页面 UI 架构。不合并，并输出本地只读验收 Prompt。
```

---

## 7. PR07-D 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR07-D：智囊页面 UI 架构重构。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
refactor/pr-07d-character-ui-architecture

前置条件：
PR07-B 已合并。必须从 PR07-B 合并后的最新 main SHA 创建分支。

独占写入目录：
app/src/main/java/com/elio/skillroundtable/ui/screens/characters/
以及本任务对应测试文件。

冻结共享文件：
ui/App.kt、ui/navigation/、ui/theme/、其他页面目录。确需修改时先报告。

必须先读取 PR07 三份规划文档、PR07-A/PR07-B PR、角色分组/详情代码、RoundtableViewModel 对应接口和测试。

使用 Superpowers:writing-plans、test-driven-development、verification-before-completion、finishing-a-development-branch。

目标：
建立 CharacterHallRoute、CharacterHallScreen、不可变 CharacterHallUiState；拆分分组栏、角色条目、更多菜单、详情 BottomSheet、保存/删除分组 Dialog 和角色编辑 Dialog。保留角色启用、分组、详情加载、新增、编辑、删除、Toast、文案和视觉值。

禁止修改 Room Schema、Migration、Repository 业务语义、圆桌/音频/设置页面。

创建 Draft PR，标题：refactor: 重构智囊页面 UI 架构。不合并，并输出本地只读验收 Prompt。
```

---

## 8. PR07-E 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR07-E：音频库与设置域 UI 架构重构。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
refactor/pr-07e-library-settings-ui-architecture

前置条件：
PR07-B 已合并。必须从 PR07-B 合并后的最新 main SHA 创建分支。

独占写入目录：
app/src/main/java/com/elio/skillroundtable/ui/screens/library/
app/src/main/java/com/elio/skillroundtable/ui/screens/settings/
以及本任务对应测试文件。

冻结共享文件：
ui/App.kt、ui/navigation/、ui/theme/、其他页面目录。

必须先读取 PR07 三份规划文档、PR07-A/PR07-B PR、AudioLibraryScreen、ApiKeyManagerScreen、Telemetry 页面、音频状态、ApiKeyPool、TelemetryRepository 和测试。

使用 Superpowers:writing-plans、test-driven-development、verification-before-completion、finishing-a-development-branch。

目标：
建立 AudioLibraryRoute/Screen/Components、ApiKeyManagerRoute/Screen、TelemetryRoute/Screen；拆分状态卡、音频卡、Key 卡、指标、遥测事件和风险 Dialog。保留音频进度、失败、播放、删除、转码入口；保留 Key 导入、验证、启停、删除、清空；保留遥测级别、清理、正文调试和云端会话链确认。

禁止修复或新增 Seek、时间轴、AAC、WebSocket、Key 存储和遥测 Repository 行为，不修改圆桌或智囊页面。

创建 Draft PR，标题：refactor: 重构音频库与设置页面架构。不合并，并输出本地只读验收 Prompt。
```

---

## 9. PR07-F 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR07-F：UI 基础重构最终回归门禁与收口。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
test/pr-07f-ui-refactor-guardrails

前置条件：
PR07-C、PR07-D、PR07-E 已全部合并。请先检查全部 PR、最新 main SHA、CI、评论和未解决风险；任何一个未合并都不要开始写代码。

必须读取：
README.md、AGENTS.md、PR07 三份规划文档、PR07-A～E 的 PR Diff/描述/CI、最终 ui 目录、现有测试与工作流。

使用 Superpowers:requesting-code-review、verification-before-completion、finishing-a-development-branch。

目标：
审查最终目录和依赖方向；确认 MainActivity 只保留入口；清理重复主题和旧实现；增加必要 Compose UI 回归测试与纯逻辑测试；更新 AGENTS/架构文档；完成全量 CI、Instrumentation 和真机回归；给出 PR08 可用的稳定基线 SHA。

本 PR 不继续大规模拆页面，不重新设计 UI，不修复无关业务 Bug。

创建 Draft PR，标题：test: 增加 UI 重构回归门禁。不合并，并输出完整本地只读验收 Prompt。
```

---

## 10. 本地验收 AI 通用只读 Prompt 模板

每个远端开发 PR 完成后，将以下模板中的占位符替换为真实值：

```text
你负责对 GitHub PR #<PR_NUMBER> 进行本地只读验收。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

Base：<BASE_BRANCH>@<BASE_SHA>
开发分支：<HEAD_BRANCH>
Head：<HEAD_SHA>

要求：
1. 拉取远端最新代码并检出开发分支；
2. 只进行读取、构建、测试和验收；
3. 不修改文件、不格式化、不提交、不推送、不合并；
4. 开始和结束都执行 git status --short，确认工作区干净；
5. 记录 Windows、JDK、Gradle、Android SDK、设备和 Android 版本；
6. 执行 PR 描述列出的全部验证命令；
7. 按 PR07 行为冻结清单进行真机回归；
8. 记录每条命令、退出码、结果和关键日志；
9. 失败时提供第一条根因错误、复现步骤和可能原因；
10. 将报告反馈给远端开发对话，不修改代码。
```

---

## 11. 状态交接格式

每个对话结束时必须留下：

```text
仓库：
PR：
Base 分支与 SHA：
开发分支：
Head SHA：
Commit：
修改文件：
已验证：
未验证：
已知风险：
共享文件是否修改：
依赖的前置 PR：
阻塞的后续 PR：
本地验收 Prompt：
```

没有真实 PR、Commit、CI 或测试证据时，对应字段必须写“未创建”“未运行”或“无法确认”，不得猜测。
