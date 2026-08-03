# PR09-04 见域导航壳实施计划

> **执行方式：** Superpowers 插件接口未调用；本任务读取仓库内固定保存的 Superpowers 6.2.0 Skill 文件，并按照项目适配规则执行等价人工流程。不使用 Worktree、子智能体、自动合并或自动删除分支。

**目标：** 在不修改 Room v7、PR09-03 Repository 公共契约、PR09-05 Skill Catalog 内部实现和最终品牌视觉的前提下，建立首页、议题、Skill、资料与成果四个一级目的地，以及全局设置入口、返回栈、稳定深链、恢复定位和旧 Route 兼容层。

**架构：** `MainActivity → MainAppContent / AppNavHost → <Domain>Route → <Domain>Screen → Components`。`App.kt` 只负责顶层组合；议题列表和议题恢复分别通过独立 ViewModel 只读消费 `JianyuRepository`；动态 Route 只携带稳定 ID 或 Tab 枚举。

**技术栈：** Kotlin 2.0.21、Jetpack Compose、Material 3、Navigation Compose 2.8.4、Android ViewModel、StateFlow、Room v7、JUnit、Compose UI Test。

---

## 1. 实际 Base 与输入

- 仓库：`elio-zwd/AI-Skill-Roundtable`
- Base 分支：`main`
- 实际 Base SHA：`78abf30b60d863ce0ac29323546e61971d50c9c9`
- PR09-03 / PR #34 合并 Commit：`78abf30b60d863ce0ac29323546e61971d50c9c9`
- Room：v7
- 开发分支：`feat/pr-09-04-jianyu-navigation-shell`
- Draft PR：`#36 feat: 建立见域导航壳`

PR09-03 只读输入：

```kotlin
suspend fun JianyuRepository.listIssueNavigation(
    states: Set<IssueLifecycleState> = setOf(IssueLifecycleState.ACTIVE)
): RepositoryResult<List<IssueNavigationItem>>

suspend fun JianyuRepository.recoverIssue(
    issueId: String
): RepositoryResult<IssueRecoverySnapshot>
```

本 PR 不修改上述签名，也不新增第二套 Repository。

## 2. Figma 门禁与确认

用户已于 2026-08-03 明确确认导航结构：

- Figma 文件：`https://www.figma.com/design/NdAQ3kLREocQVmjd2Mxb2X`
- 已确认根节点：`5:2`
- 首页：`5:18`
- 议题：`5:49`
- Skill：`5:80`
- 资料与成果：`5:111`
- 360dp / 200% 字号：`5:148`
- 设置：`5:176`

确认范围只包括信息层级、组件位置、基础状态和响应式结构；不冻结最终品牌色、字体、Logo、正式图标、插画或精修动效。

## 3. PR09-05 sibling 状态

- sibling 分支：`feat/pr-09-05-official-skill-catalog`
- Draft PR：`#35 feat: 建立见域官方Skill目录`
- 已核验 Head：`e47841987b106bad892c48f31d9842c6a335aafb`
- Base：同为 `main@78abf30...`
- PR09-05 未修改 `MainActivity.kt`、`ui/App.kt`、`ui/navigation/**` 或本 PR 页面壳文件。
- PR09-05 已提供公共入口 `OfficialSkillCatalogRoute`、`OfficialSkillCatalogRuntime` 和正式 Validator 接线说明。
- PR09-05 尚未合并，因此本 PR 当前保留稳定 Skill Route 和明确占位页。
- PR09-05 合并后，本分支使用普通 merge 同步最新 `main`，删除占位实现并只通过其公共入口完成接线。
- 在正式 Skill 页面接入前，PR #36 保持 Draft，不标记 Ready。

## 4. 当前与目标导航结构

迁移前：

```text
ROUNDTABLE      roundtable
CHARACTERS      characters
AUDIO_LIBRARY   audio-library
API_KEYS        settings/api-keys
TELEMETRY       settings/telemetry
```

目标：

```text
首页            默认一级目的地
议题            一级目的地
Skill           一级目的地
资料与成果      一级目的地；默认资料 Tab
设置            全局顶部次级入口
```

设置不进入底部导航。一级目的地顺序固定为：首页、议题、Skill、资料与成果。

## 5. 旧 Route 兼容方案

比较结果：

- 方案 A：旧 Route 静默重定向到新页面。语义不等价，拒绝采用。
- 方案 B：直接删除旧页面。会破坏旧测试标签和现有设置返回链，拒绝采用。
- 方案 C：保留旧页面为隐藏兼容目的地，从新底部导航移除。

采用 **方案 C**。

| 旧 Route | 当前行为 | 返回关系 | 最晚删除阶段 |
|---|---|---|---|
| `roundtable` | 保留旧圆桌页面 | 返回调用来源 | PR09-12 旧会话兼容迁移并独立验收后 |
| `characters` | 保留旧智囊大厅 | 返回调用来源 | PR09-05 正式 Skill 入口及旧人物迁移完成后 |
| `audio-library` | 保留旧音频库 | 返回调用来源 | PR09-10B 音频成果接入完成后 |
| `settings/api-keys` | 保留现有页面 | 返回设置或旧调用链 | 本 PR 不删除 |
| `settings/telemetry` | 保留现有页面 | 返回 API Key 或设置来源 | 本 PR 不删除 |

以下稳定标签继续保留：

```text
new_session_button
chat_input
send_button
stop_button
retry_failed_characters_button
dismiss_failed_characters_button
character_hall
audio_library
api_key_manager
telemetry_screen
app_bottom_navigation
```

## 6. 新 Route 表

| 目的地 | Route pattern | 启动 Route | 层级 |
|---|---|---|---|
| 首页 | `home` | `home` | 一级 |
| 议题导航图 | `issues_graph` | `issues_graph` | 一级图 |
| 议题列表 | `issues` | 由议题图进入 | 一级叶节点 |
| 议题恢复 | `issues/{issueId}?stageId={stageId}` | 稳定 ID | 二级 |
| Skill 导航图 | `skills_graph` | `skills_graph` | 一级图 |
| Skill 列表 | `skills` | 由 Skill 图进入 | 一级叶节点 |
| Skill 详情 | `skills/{skillId}` | 稳定官方 Skill ID | 二级 |
| 资料与成果 | `resources?tab={tab}` | `resources?tab=materials` | 一级 |
| 设置 | `settings` | `settings` | 二级 |
| API Key | `settings/api-keys` | 原 Route | 二级兼容 |
| Telemetry | `settings/telemetry` | 原 Route | 二级兼容 |
| 旧圆桌 | `roundtable` | 原 Route | 隐藏兼容 |
| 旧人物 | `characters` | 原 Route | 隐藏兼容 |
| 旧音频 | `audio-library` | 原 Route | 隐藏兼容 |

稳定 ID 只允许 `[A-Za-z0-9._-]{1,128}`。Route 不包含资料正文、个人背景正文、成果正文、Prompt、API Key 或其他敏感内容。

## 7. 深链表与 Manifest 边界

使用自定义 scheme：`jianyu://`。

| URI | 目标 | 无效参数行为 |
|---|---|---|
| `jianyu://issues/{issueId}` | 议题恢复定位 | 显示失败状态，不创建数据 |
| `jianyu://issues/{issueId}?stageId={stageId}` | 指定议题和 Stage | Stage 不属于议题时显示失败 |
| `jianyu://skills/{skillId}` | Skill 详情公共接口 | 无效 ID 显示失败或占位 |
| `jianyu://resources?tab=materials` | 资料 Tab | 正常 |
| `jianyu://resources?tab=artifacts` | 成果 Tab | 正常 |
| `jianyu://resources?tab={invalid}` | 资料 Tab | 回退资料 |

实现边界：

- Navigation Compose 注册 `navDeepLink`。
- `AndroidManifest.xml` 注册 `VIEW + DEFAULT + BROWSABLE`。
- 对外只暴露 `issues`、`skills`、`resources` 三个 host。
- 不暴露设置、API Key、Telemetry 或旧页面 host。
- Manifest 与 Route 参数均不包含敏感字段。
- 外部深链只负责定位，不创建 Issue、Stage、Run、资料或成果。

## 8. 返回栈规则

1. 冷启动默认首页。
2. 一级切换使用 `popUpTo(startDestination) { saveState = true } + launchSingleTop + restoreState`。
3. 重复点击当前一级目的地时直接返回，不创建副本。
4. 一级目的地各自恢复已保存状态。
5. 设置从任意一级目的地进入，系统返回和顶部返回都回到原目的地。
6. 直接导航到 Issue 详情前先确保 `issues_graph` 为父级，详情返回议题列表。
7. 直接导航到 Skill 详情前先确保 `skills_graph` 为父级，详情返回 Skill 列表。
8. 资料与成果的两个 Tab 属于同一一级目的地，不创建两个顶层栈。
9. `rememberNavController()` 与 `rememberSaveable` 共同负责 Activity 重建状态。
10. 进程重建必须由系统 SavedState 和稳定 Route 恢复，不自动重复业务调用或越过确认门禁。
11. 旧 Roundtable → API Key → Telemetry 返回链继续保留。

## 9. Repository 只读接口与装配

议题列表路径：

```text
IssuesRoute
→ IssuesViewModel
→ IssuesNavigationLoader.load()
→ IssueNavigationReader.listIssueNavigation()
→ JianyuRepository.listIssueNavigation()
```

议题恢复路径：

```text
IssueRecoveryRoute
→ IssueRecoveryViewModel
→ IssuesNavigationLoader.recover()
→ IssueNavigationReader.recoverIssue()
→ JianyuRepository.recoverIssue()
```

约束：

- 列表 ViewModel 和恢复 ViewModel 分离，详情深链不隐式加载列表。
- 无效 Issue / Stage ID 在 Repository 调用前拒绝。
- 列表页面不调用恢复接口。
- 恢复页面不调用列表接口。
- 页面加载不调用任何 Repository 写接口。
- Screen、Components、navigation 不引用 DAO。
- 当前仅由 `IssuesViewModel.kt` 装配唯一 `RoomJianyuRepository`。
- PR09-05 合并后，将 Catalog runtime、正式 Validator 和 Repository 统一提升到 App 组合层，避免双事实源。

## 10. UI 分层

```text
MainActivity
  → MainAppContent
      → AppNavHost
          → HomeRoute → HomeScreen
          → IssuesRoute → IssuesScreen
          → IssueRecoveryRoute → IssueRecoveryScreen
          → SkillPlaceholderRoute
          → SkillDetailPlaceholderRoute
          → ResourcesRoute → ResourcesScreen
          → SettingsRoute → SettingsScreen
          → 旧兼容 Route
```

- `MainActivity` 继续只挂载主题和 `MainAppContent`。
- `App.kt` 只组装 Scaffold、底部导航、NavHost 和 Route。
- Route 收集状态并处理副作用。
- Screen 只接收不可变状态和回调。
- Components 不访问 ViewModel、Repository 或 DAO。
- `navigation/` 不引用页面内部实现。
- 页面专属 Dialog、表单和业务状态不放入 `App.kt`。

## 11. 响应式与无障碍结构

- 页面标题区使用可随字号增高的 Row/Column，不依赖固定高度 TopAppBar。
- 首页、议题、议题恢复、Skill 占位、资料与成果、设置内容均可纵向滚动。
- 底部导航标签允许两行并居中。
- 选中状态使用 Material 语义，不只依赖颜色。
- 每个导航图标提供 `contentDescription`。
- 设置与返回入口保持至少标准 IconButton 点击区域。
- 360dp、200% 字号、横竖屏与 TalkBack 由设备端验收。

## 12. Skill 页面装配边界

当前冻结：

```text
Top-level graph: skills_graph
List route: skills
Detail route: skills/{skillId}
Bottom tag: app_destination_skills
```

PR09-05 合并前：

- 使用明确占位页面。
- 不复制 Catalog、筛选、详情、收藏、组合或 ViewModel。
- 不记录最近使用。

PR09-05 合并后：

- 普通 merge 同步最新 `main`。
- 构造一次 `OfficialSkillCatalogRuntimeResult`。
- 页面和 Repository 使用同一 Catalog / Validator。
- 根导航只调用 `OfficialSkillCatalogRoute` 公共入口。
- `onUseSkill` 只上抛稳定 ID 和意图，不创建 Run、不调用 Gemini。
- 删除临时 Skill 占位文件，不保留两套页面。

## 13. 实际新增文件

```text
app/src/main/java/com/elio/jianyu/ui/components/JianyuPageShell.kt
app/src/main/java/com/elio/jianyu/ui/navigation/JianyuNavigationRoutes.kt
app/src/main/java/com/elio/jianyu/ui/screens/home/HomeRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesNavigationLoader.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/settings/SettingsRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/skillplaceholder/SkillPlaceholderRoute.kt
app/src/test/java/com/elio/jianyu/ui/JianyuNavigationArchitectureTest.kt
app/src/test/java/com/elio/jianyu/ui/navigation/JianyuDeepLinkManifestTest.kt
app/src/test/java/com/elio/jianyu/ui/navigation/JianyuNavigationRoutesTest.kt
app/src/test/java/com/elio/jianyu/ui/screens/issues/IssuesNavigationLoaderTest.kt
app/src/test/java/com/elio/jianyu/ui/screens/issues/IssuesUiStateTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/JianyuNavigationShellScreenTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/MainNavigationRestorationTest.kt
docs/testing/pr-09-04-local-readonly-acceptance-prompt.md
```

## 14. 实际修改文件

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/elio/jianyu/ui/App.kt
app/src/main/java/com/elio/jianyu/ui/navigation/AppDestination.kt
app/src/main/java/com/elio/jianyu/ui/navigation/AppNavHost.kt
app/src/test/java/com/elio/jianyu/ui/navigation/AppDestinationTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/AppBottomNavigationTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/navigation/AppNavHostTest.kt
docs/planning/pr-09-04-navigation-shell-plan.md
```

`MainActivity.kt` 已符合薄入口要求，不修改。

## 15. 严格禁止修改文件

禁止修改：

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/PendingMessageRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/UsageRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/ChatSession.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/schemas/
```

同时禁止修改 PR09-05 页面内部文件、Gemini 调度、音频任务、最终主题和品牌资产。

## 16. TDD 与失败测试顺序

首个失败测试：更新 `AppDestinationTest`，要求一级目的地精确为 `HOME / ISSUES / SKILLS / RESOURCES` 且默认首页。旧实现缺少这些枚举，因此应失败。

后续测试：

1. Route、稳定 ID、Tab 回退与敏感参数拒绝。
2. 议题列表生命周期分区和排序。
3. 无效 ID 在 Repository 调用前失败。
4. 列表只调用 `listIssueNavigation()`。
5. 恢复只调用 `recoverIssue()`。
6. 指定 Stage 必须属于目标 Issue。
7. 四个底部目的地及设置隐藏规则。
8. 重复点击当前一级目的地不重复入栈。
9. 详情返回所属列表。
10. 设置返回原目的地。
11. 资料/成果 Tab 切换及默认资料。
12. Activity 重建恢复目的地和 Tab。
13. Manifest 只暴露三个稳定深链 host。
14. 架构守卫禁止 DAO、写接口和页面内部跨层依赖。

远端 GitHub 插件环境无法实际观察本地 RED；提交测试后以 GitHub CI 的真实命令结果作为 JVM/编译证据。Instrumentation 由本地 AI 实际执行。

## 17. Commit 边界

建议与实际意图保持一致：

```text
docs: 制定PR09-04导航壳实施计划
test: 增加见域导航失败场景
feat: 建立见域四目的地导航壳
feat: 增加深链与返回栈恢复
test: 完善导航Compose与架构验证
fix: 隔离议题列表与恢复读取路径
docs: 对齐PR09-04深链与验收计划
feat: 接入官方Skill目录入口   # 仅在PR09-05合并后
```

不改写历史，不强制推送。

## 18. CI

每个最终 Head 至少核验：

- Secret scan
- 静态应用身份门禁
- Debug Kotlin 编译
- JVM 单元测试
- Lint
- Debug APK
- 包名、Migration、Schema 与 APK 校验
- Release signing 配置校验
- Optimized Release / R8
- Room Schema 当前性
- 报告与 APK 上传

不得复用旧 Head 的成功状态。被后续 Push 取消的 Run 只能作为中间证据，不作为最终结论。

## 19. 本地验收

本地 AI 按 `docs/testing/pr-09-04-local-readonly-acceptance-prompt.md` 严格只读执行：

- 精确检出 Head。
- 不修改、不提交、不推送、不合并。
- 执行编译、JVM、Lint、Debug/Release/R8。
- 执行 `connectedDebugAndroidTest`。
- 验证四个一级目的地、设置、资料/成果 Tab、旧 Route。
- 验证返回栈、Activity 与进程重建、外部深链。
- 验证 360dp、200% 字号、横竖屏和 TalkBack。
- 验证工作区最终干净。

## 20. 主要风险

- Navigation Compose 嵌套图与外部深链的父级返回栈行为。
- Activity 重建可由现有测试覆盖，真实进程强停恢复仍需设备验证。
- 议题恢复会读取真实 Room 数据，测试环境需准备稳定 fixture。
- 旧 Route 长期保留可能增加维护成本。
- 资料/成果长文案在极端字体比例下仍需设备检查。
- PR09-05 合并后 Repository 与 Catalog runtime 的统一装配可能影响当前 ViewModel provider。
- 自定义 `jianyu://` scheme 可被其他 App 声明；V1 不把它视为已验证的 Web App Link。

## 21. 回滚

- 整体回滚 PR #36 可恢复旧三目的地导航。
- 没有 Room Migration、Schema 或持久化格式变化。
- Manifest 深链可单独通过回滚对应 Commit 移除。
- Skill 最终接线采用独立 Commit，可单独回退到明确占位状态。
- 不删除旧页面和旧 Route，因此回滚不涉及数据迁移。

## 22. 旧导航删除阶段

- `characters`：PR09-05 正式页面接线和旧人物入口迁移独立验收后。
- `audio-library`：PR09-10B 音频成果接入后。
- `roundtable`：PR09-12 旧会话兼容迁移完成后。
- API Key / Telemetry Route 不在本 PR 删除。

删除兼容 Route 必须由独立 PR 更新调用方、测试标签和回滚说明。

## 23. PR09-05 最终接线步骤

1. 等待 PR #35 完成本地严格只读验收并合并。
2. 读取 PR #35 合并 Commit 和最终交接文档。
3. 普通 merge 最新 `main` 到本分支。
4. 在 App 组合层创建一次 Catalog runtime。
5. 将正式 Validator 注入唯一 `RoomJianyuRepository`。
6. 使用 `OfficialSkillCatalogRoute` 替换 Skill 占位页。
7. 删除临时 Skill 占位文件。
8. 保持 `skills`、`skills/{skillId}` 和稳定测试标签不变。
9. 重新执行 Catalog、导航、返回栈、深链和 Activity 重建回归。
10. 更新 PR 描述和本地验收 Prompt 后仍保持 Draft，等待用户授权。

## 24. 验收完成条件

非 Skill 接线部分完成条件：

- 四个一级目的地与设置结构落地。
- 旧 Route 兼容明确。
- 议题列表与恢复只读边界有测试。
- 深链、返回栈和 Activity 重建测试已提交。
- 最终 Head 的 GitHub CI 全部完成并通过。
- 本地只读验收 Prompt 已提交。

整个 PR 完成条件还包括：

- PR09-05 已合并。
- 正式 Skill 页面公共入口已接入。
- 本地 AI 完成设备端严格只读验收。
- 用户明确授权后才可标记 Ready 或合并。

## 25. 未验证项

在本地 AI 实际执行前，以下保持“尚未验证”：

- Compose Instrumentation 全量执行结果。
- 真正进程强停后的导航恢复。
- 外部 `adb shell am start` 深链冷启动和系统返回。
- 360dp、200% 字号、横竖屏切换。
- TalkBack 顺序、朗读和选中状态。
- PR09-05 正式页面接线。

不得把静态检查或已提交测试描述为这些项目已经通过。
