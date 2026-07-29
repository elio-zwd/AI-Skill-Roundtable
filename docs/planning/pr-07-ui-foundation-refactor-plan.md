# PR 07：UI 基础架构重构总计划

> 仓库：`https://github.com/elio-zwd/AI-Skill-Roundtable`
>
> 规划基线：`main@743027f29dcc144341c2ac77ec4600796aa4cdcb`
>
> 目标：在不重新设计视觉、不改变核心业务语义的前提下，先完成 Compose UI 代码结构、主题、导航与页面边界重构，为后续 UI/UX 重设计建立稳定底座。

---

## 1. 结论

本阶段不直接重新设计 UI。

当前最优顺序是：

```text
PR07-A 机械拆分与包结构
→ PR07-B 主题、导航与顶层状态边界
→ PR07-C / PR07-D / PR07-E 三个页面域并行重构
→ PR07-F 回归门禁、文档与最终收口
→ PR08 UI/UX 视觉重设计
```

多对话并行只从 PR07-B 合并后开始。PR07-A 与 PR07-B 都会修改顶层入口和共享契约，必须串行完成。

---

## 2. 当前问题与依据

### 2.1 `MainActivity.kt` 职责严重过载

当前 `app/src/main/java/com/elio/skillroundtable/MainActivity.kt` 同时包含：

- `MainActivity` 与 `MaterialTheme` 初始化；
- 顶层页面切换与底部导航；
- 会话历史抽屉；
- 圆桌聊天页面；
- 消息分组、气泡、角色发言状态；
- 智囊大厅、角色详情、角色编辑弹窗；
- 遥测与诊断页面；
- Markdown 导出与本地保存工具；
- 公共头像、动画和 Markdown 渲染组件。

这会导致：

- 任意页面重构都容易修改同一个超大文件；
- 多对话开发冲突率高；
- UI 状态、业务调用和视觉组件边界不清；
- 后续重新设计 UI 时难以逐屏迭代和回滚。

### 2.2 主题颜色重复定义

以下文件分别维护相同或近似的 Slate 深色调色板：

- `MainActivity.kt`
- `AudioLibraryScreen.kt`
- `ApiKeyManagerScreen.kt`

页面直接依赖 `SlateBg`、`CardBg`、`PrimaryAccent`、`GoldAccent` 等实现色，而不是通过统一主题和语义色访问。

### 2.3 顶层导航由多个局部布尔状态维护

当前顶层使用：

- `selectedTab`
- `showApiKeyManagerScreen`
- `showTelemetryScreen`
- `showDrawer`
- 多个 Dialog 布尔状态

组合控制页面显示。此方式在增加设置页、资料库页、横屏布局或系统返回行为时容易产生状态组合问题。

### 2.4 页面和 ViewModel 耦合较重

多个页面直接读取 `RoundtableViewModel` 的大量 Flow，并在页面内部直接调用业务方法。后续应逐步形成：

```text
Route：收集 ViewModel 状态、连接事件
Screen：只接收不可变 UI State 与事件回调
Component：仅负责展示和局部交互
```

本阶段不重写业务编排器，只清理 UI 边界。

---

## 3. 阶段目标

### 3.1 必须完成

1. `MainActivity.kt` 只保留 Android Activity 入口和顶层 App 调用。
2. 建立统一 `ui/theme`，移除页面内重复调色板。
3. 建立明确的顶层导航目标与返回栈。
4. 按业务域拆分圆桌、智囊、音频、API Key、遥测页面。
5. 形成 Route / Screen / Component 基础分层。
6. 保留现有核心交互、文案、`testTag` 和 ViewModel 行为。
7. 为纯 UI 逻辑增加必要单元测试，并增加关键 Compose 回归测试。
8. 完成编译、单元测试、Lint、Debug/Release 构建、现有 Instrumentation Test 与真机基础回归。

### 3.2 明确不做

- 不重新设计页面布局、品牌色、字体或动画风格；
- 不升级 Kotlin、AGP、Compose、Room、Retrofit；
- 不修改 Room Schema、Migration 或实体；
- 不修改 Gemini、SSE、TTS、API Key、遥测的业务语义；
- 不新增功能；
- 不改变角色选择、圆桌顺序、失败重试、音频生成流程；
- 不顺带修复与重构无关的产品问题；
- 不在本阶段引入 Material 3 Adaptive。

发现现有 Bug 时，只记录复现和建议，另开独立 PR。

---

## 4. 目标目录结构

目标结构允许小范围调整，但职责不得重新混入单一大文件。

```text
app/src/main/java/com/elio/skillroundtable/
├── MainActivity.kt
├── ui/
│   ├── App.kt
│   ├── navigation/
│   │   ├── AppDestination.kt
│   │   └── AppNavHost.kt
│   ├── theme/
│   │   ├── Color.kt
│   │   ├── Theme.kt
│   │   ├── Type.kt
│   │   ├── Shape.kt
│   │   └── Spacing.kt
│   ├── components/
│   │   ├── CharacterAvatar.kt
│   │   ├── PressFeedback.kt
│   │   ├── MarkdownContent.kt
│   │   └── AppFeedback.kt
│   └── screens/
│       ├── roundtable/
│       │   ├── RoundtableRoute.kt
│       │   ├── RoundtableScreen.kt
│       │   ├── RoundtableUiState.kt
│       │   ├── SessionDrawer.kt
│       │   ├── ConversationList.kt
│       │   ├── MessageComponents.kt
│       │   └── ConversationExport.kt
│       ├── characters/
│       │   ├── CharacterHallRoute.kt
│       │   ├── CharacterHallScreen.kt
│       │   ├── CharacterHallUiState.kt
│       │   ├── CharacterComponents.kt
│       │   └── CharacterDialogs.kt
│       ├── library/
│       │   ├── AudioLibraryRoute.kt
│       │   ├── AudioLibraryScreen.kt
│       │   └── AudioLibraryComponents.kt
│       └── settings/
│           ├── ApiKeyManagerRoute.kt
│           ├── ApiKeyManagerScreen.kt
│           ├── TelemetryRoute.kt
│           ├── TelemetryScreen.kt
│           └── SettingsComponents.kt
```

不要求为了目录完整而创建空文件。只有存在明确调用方时才新增抽象。

---

## 5. PR 拆分与依赖

## PR07-A：机械拆分与包结构

分支：`refactor/pr-07a-ui-file-extraction`

基线：最新 `main`

目标：只移动代码和调整 import，不改变页面状态模型、导航方式和视觉效果。

主要工作：

- 将 `MainActivity.kt` 中的圆桌、智囊、遥测、弹窗、公共组件和导出工具移动到独立文件；
- 将现有 `AudioLibraryScreen.kt`、`ApiKeyManagerScreen.kt` 纳入目标包结构，或先提供清晰的迁移边界；
- 保留现有函数参数和调用方式；
- 保留所有现有 `testTag` 字符串；
- `MainActivity.kt` 收缩为入口文件，但本 PR 不强制引入 NavHost；
- 不修改颜色值、布局参数、文案和业务调用。

这是后续所有 PR 的前置依赖，禁止并行。

## PR07-B：统一主题、正式导航与顶层状态边界

分支：`refactor/pr-07b-ui-theme-navigation`

基线：PR07-A 合并后的 `main`

目标：统一主题和顶层导航，清除重复调色板与多布尔页面切换。

主要工作：

- 建立 `ui/theme`；
- 将现有深色视觉值原样迁入统一主题；
- 页面改用 `MaterialTheme` 或统一语义 Token；
- 建立 `AppDestination` 与 `NavHost`；
- 将圆桌、智囊、音频设为顶层目标；
- 将 API Key、遥测设为二级目标；
- 明确系统返回行为与底部导航显示规则；
- 将 Dialog、Drawer 等局部状态保留在所属页面域，不放入全局导航。

这是页面域并行重构的前置依赖，禁止与 PR07-C/D/E 并行。

## PR07-C：圆桌页面架构重构

分支：`refactor/pr-07c-roundtable-ui-architecture`

基线：PR07-B 合并后的 `main`

允许与 PR07-D、PR07-E 并行。

主要工作：

- 拆分 `RoundtableRoute`、`RoundtableScreen` 和组件；
- 引入不可变 `RoundtableUiState`，仅聚合现有 ViewModel 状态；
- 将会话抽屉、消息列表、轮次 Pager、输入区、重试条、继续本轮操作区拆成独立组件；
- 将 `groupMessages` 等纯逻辑移动到可单测位置；
- 保留 SSE 流式消息、Pending、取消、失败角色重试和 TTS 调用行为；
- 保留相关 `testTag`。

禁止修改角色大厅、音频库、设置页面和 ViewModel 业务算法。

## PR07-D：智囊页面架构重构

分支：`refactor/pr-07d-character-ui-architecture`

基线：PR07-B 合并后的 `main`

允许与 PR07-C、PR07-E 并行。

主要工作：

- 拆分 `CharacterHallRoute`、`CharacterHallScreen`、组件和 Dialog；
- 引入不可变 `CharacterHallUiState`；
- 将分组栏、角色列表项、角色详情 BottomSheet、保存/删除分组 Dialog、角色编辑 Dialog 分离；
- 保留角色启用、编辑、删除、分组应用和详情加载行为；
- 保留现有文案和视觉参数。

禁止修改圆桌页面、音频库、API Key、遥测和数据库。

## PR07-E：音频库与设置域架构重构

分支：`refactor/pr-07e-library-settings-ui-architecture`

基线：PR07-B 合并后的 `main`

允许与 PR07-C、PR07-D 并行。

主要工作：

- 将音频库拆成 Route / Screen / Components；
- 将 API Key 管理和遥测页拆入设置域；
- 移除页面内重复颜色定义；
- 保留音频生成进度、失败状态、播放、删除、转码入口；
- 保留 API Key 导入、验证、启停、删除、清空和会话绑定展示；
- 保留遥测隐私级别、清理、临时正文调试和云端会话链设置。

禁止修改圆桌和角色页面，也不修复音频协议、Seek 或 AAC 功能问题。

## PR07-F：回归门禁、架构文档与收口

分支：`test/pr-07f-ui-refactor-guardrails`

基线：PR07-C、PR07-D、PR07-E 全部合并后的 `main`

目标：统一检查页面域重构结果，补齐测试和文档，不继续重构业务。

主要工作：

- 增加关键 Compose UI 回归测试；
- 增加纯函数与导航测试；
- 检查 `MainActivity.kt`、页面包边界和重复主题定义；
- 更新 `AGENTS.md`、架构文档和后续 UI 设计入口；
- 完成全量 CI 与真机回归清单；
- 记录未验证项和后续 PR08 的输入。

---

## 6. 并行执行矩阵

```text
时间线 1：PR07-A（单对话，串行）
时间线 2：PR07-B（单对话，串行）
时间线 3：PR07-C ─┐
                   ├─ 三个独立对话并行
          PR07-D ─┤
          PR07-E ─┘
时间线 4：PR07-F（单对话，最终收口）
```

PR07-C/D/E 的独立边界：

| PR | 独占目录 | 允许共享读取 | 禁止写入 |
|---|---|---|---|
| PR07-C | `ui/screens/roundtable/` | `ui/theme/`、`ui/components/`、ViewModel | characters、library、settings、数据层、网络层 |
| PR07-D | `ui/screens/characters/` | `ui/theme/`、`ui/components/`、Repository/ViewModel | roundtable、library、settings、数据层 Schema |
| PR07-E | `ui/screens/library/`、`ui/screens/settings/` | `ui/theme/`、`ui/components/`、音频/API Key/遥测实现 | roundtable、characters、业务协议 |

共享文件如 `ui/App.kt`、`AppNavHost.kt`、`Theme.kt` 原则上在 PR07-B 后冻结。确有必要修改时，先在对应 PR 描述中声明，并等待协调，不得三个对话同时修改。

---

## 7. 行为冻结清单

重构前后必须保持：

### 圆桌

- 新建、切换、删除、重命名会话；
- 问题发送与停止生成；
- SSE 流式正文持续更新；
- Pending 思考状态；
- 自动/手动继续本轮与下一轮；
- 失败角色原位重试和忽略；
- 联网搜索模式切换；
- Markdown 复制和本地保存；
- TTS 合成与播放入口。

### 智囊

- 预设与自定义分组应用；
- 保存和删除自定义分组；
- 角色启用/旁听；
- 角色详情加载；
- 新增、编辑、删除自定义角色。

### 音频

- 合成状态展示；
- 失败提示；
- 播放、删除、展开；
- 现有转码入口。

### 设置与隐私

- Key 批量导入、去重、验证、启停、删除、清空；
- 当前会话 Key 展示；
- 遥测 OFF / METADATA_ONLY / CONTENT_DEBUG；
- 遥测清理；
- 云端会话链开关及风险确认。

---

## 8. 测试与验证策略

每个实现 PR 至少执行：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

PR07-A、PR07-B、PR07-F 还应执行：

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

若远端 GitHub CI 已运行，应读取并引用真实状态；没有真实输出时不得声称通过。

### 必须保留的测试标签

至少包括：

- `new_session_button`
- `chat_input`
- `send_button`
- `stop_button`
- `retry_failed_characters_button`
- `dismiss_failed_characters_button`
- 音频合成进度与失败状态标签

### 人工真机回归

重点使用 Xiaomi 14 Ultra 或等价 Android 14 真机：

1. 三个底部页面切换；
2. API Key 与遥测二级页进入、系统返回和顶部返回；
3. 会话抽屉开关、切换、重命名、删除；
4. 正常提问、流式回答、停止、再次提问；
5. 失败角色重试；
6. 智囊分组和详情 BottomSheet；
7. 音频合成进度、失败与播放；
8. 横竖屏、后台恢复和进程重建基础烟雾测试。

---

## 9. 完成标准

PR07 全阶段完成后应满足：

- `MainActivity.kt` 只负责 Activity 入口，建议不超过约 80 行；
- 顶层 App、导航、主题、页面域有明确文件边界；
- 页面文件不再自行定义重复全局调色板；
- 页面域可独立修改，不需要频繁触碰同一大文件；
- 现有业务、Room、网络和协议行为保持不变；
- 关键纯逻辑与导航有自动测试；
- GitHub CI 和本地/真机验证结果有真实证据；
- `main` 上没有未解决的同域重构 PR；
- 后续 PR08 可以逐屏重新设计，而不再进行大规模基础拆文件。

---

## 10. 需要用户确认的情况

以下情况必须暂停写入并向用户确认：

1. 重构必须改变现有页面布局、文案或操作路径；
2. 必须升级依赖或引入新的生产依赖；
3. 必须修改 ViewModel 公开行为、Room Schema 或网络协议；
4. 发现 PR07-C/D/E 需要同时修改同一共享文件；
5. 发现现有功能行为与文档冲突，且不同选择会影响用户体验；
6. 需要删除旧功能、旧数据或改变系统返回行为。

小范围命名、文件拆分和 import 调整可按风险最低方案自行决定并在 PR 中记录。

---

## 11. 回滚策略

- 每个 PR 独立、原子、可单独回滚；
- 不在同一 PR 中同时做视觉重设计和代码重构；
- 不修改数据库，回滚不涉及数据迁移；
- 若某页面域回归，可只回滚对应 PR07-C/D/E；
- 若导航或主题产生全局问题，优先回滚 PR07-B，而不是在多个页面域盲目打补丁。
