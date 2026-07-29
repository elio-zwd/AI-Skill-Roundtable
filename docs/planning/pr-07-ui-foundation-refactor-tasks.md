# PR 07：UI 基础架构重构任务清单

> 总计划：`docs/planning/pr-07-ui-foundation-refactor-plan.md`
>
> 规划基线：`main@743027f29dcc144341c2ac77ec4600796aa4cdcb`

本清单供执行型 AI 使用。每个对话只负责一个分支、一个任务和一个 PR。

---

## 通用开始检查

每个执行对话开始前必须：

- [ ] 确认 GitHub 与 Superpowers 在当前对话中的真实可用能力；
- [ ] 阅读根目录 `AGENTS.md`；
- [ ] 阅读 `docs/planning/pr-execution-master-plan.md`；
- [ ] 阅读本文件和总计划；
- [ ] 检查开放 PR，确认没有同域修改；
- [ ] 确认目标基线分支和 SHA；
- [ ] 从最新目标基线创建独立分支；
- [ ] 列出预计修改文件、行为冻结点和验证命令；
- [ ] 不直接修改 `main`；
- [ ] 不假设其他对话已经完成或合并。

---

# PR07-A：机械拆分与包结构

## 元数据

```text
分支：refactor/pr-07a-ui-file-extraction
Base：main
前置：规划文档已合并，或明确读取规划 PR
Commit 标题建议：refactor: 拆分 Compose UI 文件结构
PR 标题：refactor: 拆分 Compose UI 文件结构
```

## 必须先读

- `app/src/main/java/com/elio/skillroundtable/MainActivity.kt`
- `app/src/main/java/com/elio/skillroundtable/AudioLibraryScreen.kt`
- `app/src/main/java/com/elio/skillroundtable/ApiKeyManagerScreen.kt`
- `app/src/main/java/com/elio/skillroundtable/viewmodel/RoundtableViewModel.kt`
- 与 `testTag`、TTS、遥测、角色分组直接相关的测试

## 任务

- [ ] 记录 `MainActivity.kt` 中全部顶层类型、函数和 Composable；
- [ ] 按总计划目录建立真实需要的包；
- [ ] 移动公共头像组件；
- [ ] 移动按压反馈 Modifier；
- [ ] 移动 Markdown 展示组件；
- [ ] 移动圆桌页面及其子组件；
- [ ] 移动会话抽屉和会话重命名 Dialog；
- [ ] 移动智囊大厅、详情、分组和角色编辑 Dialog；
- [ ] 移动遥测页面；
- [ ] 移动 Markdown 导出与 MediaStore 保存工具；
- [ ] 将音频库和 API Key 页面纳入清晰页面包；
- [ ] 修正 package、import、可见性和调用路径；
- [ ] 保持函数参数、视觉参数、文案和业务调用不变；
- [ ] 保持现有 `testTag` 不变；
- [ ] 删除移动后遗留的重复实现；
- [ ] 将 `MainActivity.kt` 缩减为入口和顶层 App 调用；
- [ ] 不在本 PR 引入正式 NavHost 或新 UI State 架构。

## 禁止

- [ ] 不修改颜色值、尺寸、圆角、动画、文案；
- [ ] 不修改 ViewModel、Repository、Room、网络和音频协议；
- [ ] 不升级或新增生产依赖；
- [ ] 不改变返回行为；
- [ ] 不顺手重新设计页面。

## 验证

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat connectedDebugAndroidTest
```

人工核对：

- [ ] 三个顶层页面均可进入；
- [ ] API Key 与遥测页面可进入和返回；
- [ ] 会话抽屉、提问、停止、重试、智囊详情、音频页可操作；
- [ ] 重构前后截图无计划外差异。

---

# PR07-B：统一主题、导航与顶层状态

## 元数据

```text
分支：refactor/pr-07b-ui-theme-navigation
Base：PR07-A 合并后的 main
前置：PR07-A 已合并并验证
Commit 标题建议：refactor: 统一 Compose 主题与顶层导航
PR 标题：refactor: 统一 Compose 主题与顶层导航
```

## 必须先读

- PR07-A 最终 Diff 和 PR 描述；
- `ui/App.kt` 或等价顶层入口；
- 所有页面 Route 入口；
- `app/build.gradle.kts` 中现有 Navigation Compose 依赖；
- 现有系统返回与顶部返回实现。

## 任务

- [ ] 建立 `ui/theme/Color.kt`；
- [ ] 建立 `Theme.kt` 并迁移现有深色 `ColorScheme`；
- [ ] 建立实际使用的 Typography、Shapes、Spacing；
- [ ] 替换页面中重复的全局调色板定义；
- [ ] 建立 `AppDestination`；
- [ ] 建立 `AppNavHost`；
- [ ] 配置圆桌、智囊、音频顶层目标；
- [ ] 配置 API Key、遥测二级目标；
- [ ] 明确底部导航在二级页面是否隐藏；
- [ ] 明确顶部返回和系统返回路径一致；
- [ ] 将 Drawer、Dialog、BottomSheet 状态留在所属页面；
- [ ] 为导航目标增加纯 JVM 测试或可验证契约；
- [ ] 保持当前视觉值和主要操作路径不变。

## 决策约束

- 使用仓库现有 `androidx.navigation.compose`，不新增生产依赖；
- 不引入 Material 3 Adaptive；
- 不改变底部三个页面的名称，除非用户明确批准；
- 若系统返回行为与现有自定义返回冲突，暂停并询问用户。

## 验证

除通用四项外：

- [ ] 冷启动默认进入圆桌；
- [ ] 顶层页面切换不产生重复目的地；
- [ ] API Key → 遥测 → 返回路径正确；
- [ ] 二级页系统返回与顶部返回结果一致；
- [ ] 进程重建后不出现非法导航状态；
- [ ] 页面不再声明重复全局调色板。

---

# PR07-C：圆桌页面架构重构

## 元数据

```text
分支：refactor/pr-07c-roundtable-ui-architecture
Base：PR07-B 合并后的 main
并行：允许与 PR07-D、PR07-E 并行
独占目录：ui/screens/roundtable/
PR 标题：refactor: 重构圆桌页面 UI 架构
```

## 任务

- [ ] 建立 `RoundtableRoute`，集中收集 ViewModel Flow；
- [ ] 建立不可变 `RoundtableUiState`；
- [ ] `RoundtableScreen` 不直接查找全局 ViewModel；
- [ ] 拆分顶栏、席位状态、会话抽屉、对话列表、轮次、消息气泡、Typing、动作条、失败重试条和输入区；
- [ ] 将导出动作封装为页面事件；
- [ ] 将 `ChatItem` 与 `groupMessages` 移到可测试位置；
- [ ] 为消息分组补充边界测试；
- [ ] 保留所有圆桌 `testTag`；
- [ ] 保留流式 Pending、停止、继续、重试和 TTS 行为。

## 建议测试场景

- [ ] 用户消息切断上一轮角色分组；
- [ ] 同一 `roundIndex` 的角色消息保持顺序；
- [ ] 纯“正在思考中...” Pending 不进入正式轮次；
- [ ] 有流式正文的 Pending 可显示；
- [ ] 多轮消息顺序稳定；
- [ ] 无会话空状态；
- [ ] 运行中显示停止按钮；
- [ ] 失败角色状态显示重试与忽略。

## 禁止写入

- `ui/screens/characters/`
- `ui/screens/library/`
- `ui/screens/settings/`
- Room、网络、TTS 协议和圆桌编排算法

确需修改 `ui/App.kt`、导航或主题时，先在 PR 中记录阻塞，不直接写入。

---

# PR07-D：智囊页面架构重构

## 元数据

```text
分支：refactor/pr-07d-character-ui-architecture
Base：PR07-B 合并后的 main
并行：允许与 PR07-C、PR07-E 并行
独占目录：ui/screens/characters/
PR 标题：refactor: 重构智囊页面 UI 架构
```

## 任务

- [ ] 建立 `CharacterHallRoute`；
- [ ] 建立不可变 `CharacterHallUiState`；
- [ ] 拆分标题区、分组栏、角色条目、更多菜单；
- [ ] 拆分角色详情 BottomSheet；
- [ ] 拆分保存分组、删除分组、角色编辑 Dialog；
- [ ] 将详情加载、启用切换、编辑、删除等动作变成明确事件；
- [ ] 保留现有文案、列表顺序、Toast 和视觉值；
- [ ] 为自定义角色表单映射或纯逻辑增加测试；
- [ ] 检查用户自定义角色缺省字段保持一致。

## 建议测试场景

- [ ] 预设分组不可删除；
- [ ] 自定义分组可触发删除确认；
- [ ] 当前有激活角色时允许保存分组；
- [ ] 编辑角色保留原 ID、order、skillAssetPath、isActive 等字段；
- [ ] 新角色生成自定义 ID；
- [ ] 详情关闭后清理已加载内容。

## 禁止写入

- `ui/screens/roundtable/`
- `ui/screens/library/`
- `ui/screens/settings/`
- Room Schema、Migration 和 Repository 业务语义

---

# PR07-E：音频库与设置域架构重构

## 元数据

```text
分支：refactor/pr-07e-library-settings-ui-architecture
Base：PR07-B 合并后的 main
并行：允许与 PR07-C、PR07-D 并行
独占目录：ui/screens/library/、ui/screens/settings/
PR 标题：refactor: 重构音频库与设置页面架构
```

## 音频库任务

- [ ] 建立 `AudioLibraryRoute`；
- [ ] 建立音频库不可变 UI State；
- [ ] 拆分合成状态卡、音频条目和空状态；
- [ ] 保留合成状态标签和真实生成时长展示；
- [ ] 保留播放、删除、展开与转码入口；
- [ ] 不处理 Seek、时间轴、AAC 新功能。

## 设置域任务

- [ ] 建立 `ApiKeyManagerRoute` 和 Screen；
- [ ] 建立 `TelemetryRoute` 和 Screen；
- [ ] 拆分指标卡、导入卡、Key 行、遥测事件卡与风险 Dialog；
- [ ] 保留 Key 安全存储与显示掩码；
- [ ] 保留导入、验证、启停、删除、清空；
- [ ] 保留遥测级别、清理、CONTENT_DEBUG 风险确认；
- [ ] 保留云端会话链风险确认。

## 建议测试场景

- [ ] 合成状态标题与描述映射；
- [ ] 音频大小和格式文案；
- [ ] Key 状态到文案/颜色语义映射；
- [ ] Key 导入结果摘要；
- [ ] 遥测级别展示；
- [ ] 二级页面返回事件。

## 禁止写入

- `ui/screens/roundtable/`
- `ui/screens/characters/`
- 音频协议、WebSocket、Key 存储和遥测 Repository 行为

---

# PR07-F：回归门禁与最终收口

## 元数据

```text
分支：test/pr-07f-ui-refactor-guardrails
Base：PR07-C/D/E 全部合并后的 main
PR 标题：test: 增加 UI 重构回归门禁
```

## 任务

- [ ] 检查开放 PR 均已合并且无冲突；
- [ ] 审查最终目录和依赖方向；
- [ ] 检查 `MainActivity.kt` 仅保留入口；
- [ ] 搜索并清理重复全局调色板；
- [ ] 搜索遗留旧 package 和重复 Composable；
- [ ] 增加必要 Compose UI Test 依赖，必须沿用现有 Compose BOM；
- [ ] 增加顶层导航测试；
- [ ] 增加关键标签存在与交互测试；
- [ ] 增加或整理截图回归步骤；
- [ ] 更新 `AGENTS.md` 与系统架构文档；
- [ ] 新增 PR08 UI 设计前置检查清单；
- [ ] 运行完整 CI、Instrumentation 和真机回归。

## 最终验收命令

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

## 最终交付必须说明

- 已实际执行并通过的命令；
- GitHub CI 状态与 Run；
- 真机设备、Android 版本和场景；
- 未验证项；
- 已知风险；
- PR08 可直接使用的稳定基线 SHA。

---

## 多对话冲突处理

出现以下情况时停止写入：

- 发现其他开放 PR 正在修改本任务独占目录；
- 当前分支基线不是任务要求的最新合并基线；
- 必须修改被冻结的共享文件；
- 必须改变业务行为才能继续；
- GitHub 上目标文件已在本对话读取后发生变化。

处理方式：

1. 在当前 PR 描述或评论记录冲突；
2. 向用户说明涉及文件和依赖；
3. 等待前置 PR 合并或由最终收口 PR 统一处理；
4. 不复制、覆盖或合并其他对话的代码。
