# PR09-04 见域导航壳实施计划

> **执行方式：** 按仓库内固定保存的 Superpowers 6.2.0 `brainstorming`、`writing-plans`、`test-driven-development`、`executing-plans`、`verification-before-completion`、`requesting-code-review` 与 `finishing-a-development-branch` 执行等价人工流程。Superpowers 插件接口未调用；不使用 Worktree、子智能体或自动合并。

**目标：** 在不修改 Room v7、Repository 公共契约、Skill Catalog 内部实现和最终品牌视觉的前提下，建立首页、议题、Skill、资料与成果四个一级目的地，以及设置、返回栈、稳定深链、状态恢复和旧 Route 兼容层。

**架构：** `MainActivity → MainAppContent / AppNavHost → <Domain>Route → <Domain>Screen → Components`。`App.kt` 仅组装全局 Scaffold、底部导航、设置入口和 Route；议题页面通过导航专用 ViewModel 只读调用 `JianyuRepository.listIssueNavigation()` 与 `recoverIssue()`；动态 Route 参数只携带稳定 ID 或枚举值。

**技术栈：** Kotlin 2.0.21、Jetpack Compose、Material 3、Navigation Compose 2.8.4、Android ViewModel、StateFlow、Room v7、JUnit、Compose UI Test。

## 1. 基线与输入

- 仓库：`elio-zwd/AI-Skill-Roundtable`
- Base：`main`
- 实际 Base SHA：`78abf30b60d863ce0ac29323546e61971d50c9c9`
- PR09-03 / PR #34 合并 Commit：`78abf30b60d863ce0ac29323546e61971d50c9c9`
- Room：v7，不修改版本、Entity、DAO、Migration 或 `app/schemas/`
- 开发分支：`feat/pr-09-04-jianyu-navigation-shell`
- Draft PR 标题：`feat: 建立见域导航壳`

PR09-03 输入接口：

```kotlin
suspend fun JianyuRepository.listIssueNavigation(
    states: Set<IssueLifecycleState> = setOf(IssueLifecycleState.ACTIVE)
): RepositoryResult<List<IssueNavigationItem>>

suspend fun JianyuRepository.recoverIssue(
    issueId: String
): RepositoryResult<IssueRecoverySnapshot>
```

## 2. Figma 门禁与确认记录

用户已于 2026-08-03 明确确认导航布局。

- Figma 文件：`https://www.figma.com/design/NdAQ3kLREocQVmjd2Mxb2X`
- 已确认结构根节点：`https://www.figma.com/design/NdAQ3kLREocQVmjd2Mxb2X?node-id=5-2`
- 首页：节点 `5:18`
- 议题：节点 `5:49`
- Skill：节点 `5:80`
- 资料与成果：节点 `5:111`
- 360dp / 200% 字号：节点 `5:148`
- 设置：节点 `5:176`

确认范围：四个一级目的地、全局设置入口、资料/成果 Tab、基础状态和响应式结构。未确认也不实施最终品牌色、字体、Logo、正式图标、插画和精修动效。

## 3. PR09-05 sibling 状态

核验时 sibling 分支：`feat/pr-09-05-official-skill-catalog`。

- 相对 `main@78abf30...`：ahead 11、behind 0。
- 尚未发现开放 Draft PR。
- 当前差异仅涉及 `app/src/main/java/com/elio/jianyu/skill/catalog/**`、对应 JVM 测试和 `docs/planning/pr-09-05-official-skill-catalog-plan.md`。
- 未修改 `MainActivity.kt`、`ui/App.kt` 或 `ui/navigation/**`，当前不存在根导航文件冲突。
- PR09-04 先提供稳定 Skill Route 与占位装配点；不得复制 Catalog、ViewModel、筛选和详情业务。
- PR09-05 合并后，本分支使用普通 merge 同步最新 `main`，再以独立 Commit 接入其公共 Composable / Route；在此之前 Draft PR 不标记 Ready。

## 4. 当前导航结构

```text
ROUNDTABLE      route=roundtable      顶层
CHARACTERS      route=characters      顶层
AUDIO_LIBRARY   route=audio-library   顶层
API_KEYS        route=settings/api-keys
TELEMETRY       route=settings/telemetry
```

当前默认目的地是 `ROUNDTABLE`，顶层导航已采用 `saveState + launchSingleTop + restoreState`。

## 5. 目标导航结构

```text
首页            默认一级目的地
议题            一级目的地
Skill           一级目的地
资料与成果      一级目的地，默认资料 Tab
设置            全局顶部次级入口
```

设置不进入底部导航。四个一级目的地各自保存返回栈状态；重复点击当前项不创建副本。

## 6. 旧 Route 兼容方案

比较：

- 方案 A：旧 Route 重定向到新语义。风险是把“圆桌/人物/音频”静默解释成“首页/Skill/成果”，语义不等价。
- 方案 B：删除旧页面并仅保留重定向。风险是破坏旧 `testTag`、深链和设置返回链。
- 方案 C：保留旧页面为兼容隐藏目的地，从新底部导航移除。

采用 **方案 C**。

| 旧 Route | 兼容行为 | 返回栈 | 最晚删除阶段 |
|---|---|---|---|
| `roundtable` | 保留旧圆桌页面，隐藏于新底部导航 | 由调用来源返回 | PR09-12 完成旧会话兼容迁移并独立验收后 |
| `characters` | 保留旧智囊大厅，隐藏于新底部导航 | 由调用来源返回 | PR09-05 正式 Skill 页面和旧人物入口迁移完成后 |
| `audio-library` | 保留旧音频库，隐藏于新底部导航 | 由调用来源返回 | PR09-10B 音频成果接入完成后 |
| `settings/api-keys` | 保留 | 返回设置或原旧链 | 不在本 PR 删除 |
| `settings/telemetry` | 保留 | 返回 API Key 或设置来源 | 不在本 PR 删除 |

旧稳定 `testTag` 保留，不在本 PR 删除或改名：`new_session_button`、`chat_input`、`send_button`、`stop_button`、`retry_failed_characters_button`、`dismiss_failed_characters_button`、`character_hall`、`audio_library`、`api_key_manager`、`telemetry_screen`、`app_bottom_navigation`。

## 7. Route 表

| 目的地 | Route pattern | 导航用 Route | 层级 |
|---|---|---|---|
| 首页 | `home` | `home` | 一级 |
| 议题列表 | `issues` | `issues` | 一级 |
| 议题恢复 | `issues/{issueId}?stageId={stageId}` | 编码后的稳定 ID | 二级 |
| Skill | `skills` | `skills` | 一级 |
| Skill 详情接口 | `skills/{skillId}` | 编码后的正式 Skill ID | 二级 |
| 资料与成果 | `resources?tab={tab}` | `resources?tab=materials` | 一级 |
| 设置 | `settings` | `settings` | 二级 |
| API Key | `settings/api-keys` | 原 Route | 二级兼容 |
| Telemetry | `settings/telemetry` | 原 Route | 二级兼容 |
| 旧圆桌 | `roundtable` | 原 Route | 隐藏兼容 |
| 旧人物 | `characters` | 原 Route | 隐藏兼容 |
| 旧音频 | `audio-library` | 原 Route | 隐藏兼容 |

动态参数使用 `Uri.encode`；不得包含资料正文、个人背景、成果正文、Prompt、API Key 或其他敏感内容。

## 8. 深链表

应用内稳定 URI scheme 使用 `jianyu://`。本 PR 在 Navigation Compose 注册 `navDeepLink`，并支持显式 Intent / `NavController.handleDeepLink`；不修改 Manifest 的外部隐式 Intent 暴露范围。

| URI | 目标 | 无效参数行为 |
|---|---|---|
| `jianyu://issues/{issueId}` | 议题恢复定位 | 显示失败状态，不创建数据 |
| `jianyu://issues/{issueId}?stageId={stageId}` | 指定议题和 Stage | Stage 不属于议题时显示失败 |
| `jianyu://resources?tab=materials` | 资料 Tab | 正常 |
| `jianyu://resources?tab=artifacts` | 成果 Tab | 正常 |
| `jianyu://resources?tab={invalid}` | 资料 Tab | 回退资料 |
| `jianyu://skills/{skillId}` | Skill 详情公共接口 | 无效 ID 显示失败或占位，不泄露参数 |

## 9. 返回栈规则

1. 冷启动默认首页。
2. 一级切换使用 `popUpTo(startDestination) { saveState = true } + launchSingleTop + restoreState`。
3. 重复点击当前一级目的地不重复入栈。
4. 设置从任意一级目的地进入，系统返回和顶部返回均回到原目的地。
5. 议题详情返回议题列表；外部显式深链进入时返回行为由 NavHost 合成父级 `issues`。
6. 资料/成果 Tab 属于同一一级目的地，不创建两个顶层返回栈。
7. `rememberNavController()` 的 SavedState 负责 Activity / 进程重建；另以纯 Route 解析器验证恢复后不会跳到错误目的地。
8. 旧 Roundtable → API Key → Telemetry 返回链继续保留。

## 10. Repository 只读边界

- 议题列表仅调用 `listIssueNavigation(states = allLifecycleStates)`。
- 打开议题详情仅调用 `recoverIssue(issueId)`。
- 页面加载和深链解析不调用 `saveIssue`、`createStage`、`createExecutionRun` 或任何生命周期写接口。
- Screen、Components、navigation 不引用 DAO。
- ViewModel 通过唯一 `RoomJianyuRepository(RoundtableDatabase.getDatabase(...))` 装配公共接口。
- 不创建第二个 Repository，不修改 PR09-03 公共接口。

## 11. UI 分层

```text
MainActivity
  → MainAppContent
      → AppNavHost
          → HomeRoute → HomeScreen
          → IssuesRoute → IssuesScreen
          → IssueRecoveryRoute → IssueRecoveryScreen
          → SkillPlaceholderRoute → SkillPlaceholderScreen
          → ResourcesRoute → ResourcesScreen
          → SettingsRoute → SettingsScreen
```

- Route 收集状态和执行导航副作用。
- Screen 只接收不可变 UiState 与回调。
- 共享页面壳只展示标题、设置入口、状态和内容，不访问 ViewModel / Repository。
- `App.kt` 不持有页面专属 Dialog、表单或业务状态。

## 12. Skill 页面装配边界

本 PR 冻结：

```text
Top-level route: skills
Detail route: skills/{skillId}
Test tag: app_destination_skills
```

PR09-05 未合并时使用明确占位页。后续接线只替换 `skillsContent` / `skillDetailContent` 的公共 Composable，不修改 Catalog 数据源、查询、筛选、详情内部组件或 ViewModel。

## 13. 新增文件

计划新增：

```text
app/src/main/java/com/elio/jianyu/ui/navigation/JianyuNavigationRoutes.kt
app/src/main/java/com/elio/jianyu/ui/components/JianyuPageShell.kt
app/src/main/java/com/elio/jianyu/ui/screens/home/HomeRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/skillplaceholder/SkillPlaceholderRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/settings/SettingsRoute.kt
app/src/test/java/com/elio/jianyu/ui/navigation/JianyuNavigationRoutesTest.kt
app/src/test/java/com/elio/jianyu/ui/screens/issues/IssuesUiStateTest.kt
app/src/test/java/com/elio/jianyu/ui/screens/issues/IssuesViewModelTest.kt
```

## 14. 修改文件

```text
app/src/main/java/com/elio/jianyu/ui/App.kt
app/src/main/java/com/elio/jianyu/ui/navigation/AppDestination.kt
app/src/main/java/com/elio/jianyu/ui/navigation/AppNavHost.kt
app/src/test/java/com/elio/jianyu/ui/navigation/AppDestinationTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/AppBottomNavigationTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/navigation/AppNavHostTest.kt
app/src/test/java/com/elio/jianyu/architecture/UiArchitectureGuardrailTest.kt（仅存在对应断言时）
```

`MainActivity.kt` 当前已满足只挂载主题与 `MainAppContent`，预计不修改。

## 15. 禁止修改文件

严格不修改用户列出的 PR09-03 共享禁止区、Database、Entity、DAO、Room Schema、Migration、`app/schemas/`、PR09-05 Catalog 内部文件、Gemini 调度、音频任务、最终主题和品牌资产。

## 16. TDD 与测试文件

首个失败测试：更新 `AppDestinationTest`，要求一级目的地精确为 `HOME / ISSUES / SKILLS / RESOURCES`，默认目的地为 `HOME`，设置和旧目的地不进入底部导航。该测试在旧实现上应因枚举缺失和旧三目的地顺序而失败。

随后依次增加：

- Route、参数编码、Tab 回退与敏感参数拒绝的 JVM 测试。
- Issues UiState 映射、生命周期分区和错误映射测试。
- 使用 Fake `JianyuRepository` 的 ViewModel 测试，断言只调用 `listIssueNavigation()` / `recoverIssue()`，不调用写接口。
- Compose 导航测试：冷启动、四目的地、重复点击、设置返回、旧 Route、资料 Tab、深链和无效参数。
- 360dp、200% 字号和 contentDescription 由 Compose UI Test 与本地只读验收共同验证。

远端插件无法执行测试时，测试状态只记录为“已编写，尚未实际执行”；不得声明 RED 或 GREEN 已观察。

## 17. 首个失败测试预期

```text
AppDestinationTest.topLevelDestinations_areJianyuFourDestinations
Expected: HOME, ISSUES, SKILLS, RESOURCES
Actual before production change: ROUNDTABLE, CHARACTERS, AUDIO_LIBRARY
```

由于当前会话没有本地 Gradle / 模拟器环境，无法真实执行 RED；先提交测试代码并在 PR 中明确“尚未执行”。

## 18. Commit 边界

```text
docs: 制定PR09-04导航壳实施计划
test: 增加见域导航失败场景
feat: 建立见域四目的地导航壳
feat: 增加深链与返回栈恢复
test: 完善导航Compose与架构验证
feat: 接入官方Skill目录入口  # 仅 PR09-05 合并后
```

每个 Commit 保持原子性，不添加 `Co-Authored-By`。

## 19. CI

Draft PR 创建后读取当前 Head 对应 Actions，不复用旧 Head。目标检查：Kotlin 编译、JVM 测试、Lint、Debug APK、Release / R8、Room Schema 校验与身份门禁。设备 Compose Test 若 CI 未配置则标记未执行。

## 20. 本地验收

本地 AI 必须拉取精确 Head、严格只读，并执行：

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease
./gradlew.bat connectedDebugAndroidTest
```

另验证四个底部目的地、设置入口、资料/成果 Tab、旧 Route、返回栈、Activity / 进程重建、Issue / Stage / Skill / Resources 深链、360dp、200% 字号、TalkBack 顺序和最终工作区干净。

## 21. 风险

- PR09-05 后续新增 UI 文件可能改变公共入口；本 PR 在合并前不猜测其类型或包名。
- `RoomJianyuRepository` 首次在 UI 装配时会共享现有数据库实例；错误装配可能造成重复数据库或 DAO 直连，需架构测试防护。
- Navigation Compose 对动态 Route 与深链回退的行为需要真实设备验证。
- 旧页面仍可通过兼容 Route 打开，可能导致短期存在新旧入口并行；底部导航只显示新四目的地以避免主路径混乱。
- 外部隐式 URI Intent 未在 Manifest 注册；本 PR 只冻结并验证应用内/显式 Intent 深链接口，避免越过文件所有权。

## 22. 回滚

按 Commit 逆序回滚：先撤销深链与恢复，再撤销新导航壳，最后恢复旧三目的地测试。计划文档可保留为历史记录。回滚不得修改 Room、Schema 或用户数据。

## 23. 旧导航删除阶段

- `characters`：PR09-05 正式页面及迁移验收后独立处理。
- `audio-library`：PR09-10B 音频成果迁移后独立处理。
- `roundtable`：PR09-12 旧会话兼容迁移与回归完成后独立处理。
- 旧测试标签至少保留到对应兼容 Route 删除 PR 完成。

## 24. PR09-05 最终接线步骤

1. 确认 PR09-05 已合并且 CI / 本地验收通过。
2. 使用普通 merge 同步最新 `main`，不 rebase、不强推。
3. 读取 PR09-05 公共 Composable / Route 与文件清单。
4. 只修改 PR09-04 装配点，替换 Skill 占位内容。
5. 保持 `skills`、`skills/{skillId}` 和稳定 testTag 不变。
6. 增加接线测试并创建 `feat: 接入官方Skill目录入口` Commit。
7. 更新 Draft PR 描述后继续保持 Draft，等待本地只读验收。

## 25. 未验证项

当前仅完成仓库静态阅读、Figma 确认和计划编写。尚未执行 Gradle、Lint、APK、R8、Compose UI Test、模拟器、进程强停恢复、TalkBack 或 CI；尚未创建生产代码 Commit；尚未接入 PR09-05 正式 Skill 页面。
