# UI-02 角色页面｜本地 AI 只读验收 Prompt

> 目标分支：`codex/ui-02-role-spec`
>
> 本文只用于本地 Android/JVM/真机验收。**不得修改代码、自动修复、commit、push、merge。**

## 你的角色

你是 UI-02 的只读验收 AI。只执行验证和收集证据，不做开发。

必须遵守：

- 不修改任何源码、测试、Gradle、资源、文档；
- 不运行自动格式化或自动修复；
- 不 commit / push / merge；
- 任何命令失败后停止该验证项，只记录首个关键错误；
- 不回传大段正常日志；
- 最终只返回结构化 PASS / FAIL 与关键证据。

## 0. 工作区门禁

先执行：

```powershell
git fetch origin
git switch codex/ui-02-role-spec
git pull --ff-only
git branch --show-current
git rev-parse HEAD
git status --short
```

要求：

- branch 必须是 `codex/ui-02-role-spec`；
- 工作区开始前必须 clean；
- 记录 HEAD；
- 验收结束再次执行 `git status --short`，必须仍 clean。

如果不是该分支、`pull --ff-only` 失败或开始前不 clean：直接 FAIL，不做自动处理。

## 已知基线阻塞（不要修）

当前 `main` 与本分支都存在一个与 UI-02 无关的 AndroidTest 源码失配：

```text
app/src/androidTest/java/com/elio/jianyu/ui/screens/execution/IssueExecutionStopAvailabilityTest.kt:49
IssueExecutionBudgetUi(30, 1, 1, false)
```

而当前生产构造器为：

```text
IssueExecutionBudgetUi(usedApiCalls: Int, closed: Boolean)
```

GitHub `Android UI Test Compile` 已证明 UI-02 生产 `:app:compileDebugKotlin` 可通过；聚合 `assembleDebugAndroidTest` 随后仅被上述既有测试阻塞。

**本验收不得修改、注释、删除或临时排除这个旧测试。**

若本地 `assembleDebugAndroidTest` 也只出现上述已知错误：记录 `ASSEMBLE_ANDROID_TEST: BLOCKED_BASELINE`，不要把它记作 UI-02 FAIL；并将所有需要 AndroidTest APK 的 instrumentation 项记为 `BLOCKED_BASELINE`。若同时出现任何 UI-02 文件的新编译错误，则 UI-02 判 FAIL。

## 1. 精确 JVM 契约

执行 UI-02 新增的纯 JVM 测试：

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.elio.jianyu.skill.role.SkillRolePresentationCatalogTest" `
  --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" `
  --tests "com.elio.jianyu.skill.role.OfficialSkillConversationRoleAdapterTest"
```

验收：

- 44 项 Presentation Manifest 无遗漏/无重复；
- 固定 featured 精确为 `naval_ravikant / richard_feynman / nassim_taleb`；
- `LIFE_TOOLS` 为已批准 5 项；
- UiState 基数不被旧 20 Character 截断；
- 分类 + 搜索 + 收藏 + recent 互不污染；
- 官方 name/summary/assetPath/order 覆盖旧 Character 事实；
- 旧稳定头像/voice/vector 可保留；
- 功能角色 fallback 确定性、不生成虚构人脸。

## 2. 编译 / 单测 / lint / APK

依次执行；不要因为主 GitHub CI 的历史 identity gate 失败而跳过本地 Gradle：

```powershell
.\gradlew.bat --no-daemon :app:compileDebugKotlin
.\gradlew.bat --no-daemon :app:testDebugUnitTest
.\gradlew.bat --no-daemon :app:lintDebug
.\gradlew.bat --no-daemon :app:assembleDebug
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest
```

每条只记录：命令、exit code、PASS/FAIL；失败时补首个关键错误、文件、行号。

`assembleDebugAndroidTest` 按“已知基线阻塞”规则可记录为 `BLOCKED_BASELINE`。除此之外的命令必须按真实结果 PASS/FAIL。

## 3. 相关 Instrumentation

先确认设备：

```powershell
adb devices
```

需要至少一个 `device` 状态目标。

只有当 `assembleDebugAndroidTest` 成功时，才分别执行：

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.components.JianyuRoleAvatarTest

.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.skills.SkillRoleCatalogScreenTest

.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.skills.SkillRoleDetailScreenTest

.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.skill.role.OfficialSkillConversationRoleAdapterAndroidTest

.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.viewmodel.RoundtableViewModelSkillRoleActionsTest
```

若 `assembleDebugAndroidTest` 只被已知 `IssueExecutionStopAvailabilityTest.kt:49` 阻塞，则**不要尝试修改测试来运行 instrumentation**；五项均记录 `BLOCKED_BASELINE`，转到第 4 节做真机手工验收。

若可运行 instrumentation，重点证明：

- 主页面只出现 `推荐角色`，不存在 `为你推荐`；
- 最近使用为空时整段隐藏；
- 8 个发现 Chip 可达；
- 具体分类有说明卡且无结果不自动退回全部；
- 收藏点击不连带打开详情；
- 人物角色有 `AI 模拟角色`，详情有完整 disclaimer；
- 全屏详情有 `工作方式 / 来源与能力依据 / 边界`；
- 无当前会话时 `增加到当前会话` 禁用；
- `meeting-to-action` 能从真实官方 Catalog + APK asset 按需 materialize 为 legacy Character；
- 新会话 action 返回成功时，`currentSessionId` 已发布且唯一 participant 是目标角色；
- 再加入 `study-planner` 后，同一 session participant 包含两者；
- unknown official ID 返回失败且 participant 不变化。

## 4. 真机 UI / Xiaomi 14 Ultra 视觉与手工功能验收

目标：Xiaomi 14 Ultra，竖屏，1440×3200。

只要 `assembleDebug` 成功，即使 AndroidTest 被基线阻塞，也继续本节。

安装 Debug APK：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.elio.jianyu -c android.intent.category.LAUNCHER 1
```

对照：

- `docs/product/重构/UI界面/候选图片/01-角色主页面-A-推荐优先.png`
- `docs/product/重构/UI界面/候选图片/02-角色主页面-B-分类目录.png`
- `docs/product/重构/UI界面/实施规格/UI-02/角色主页面-双布局-实施规格.md`

检查并截图：

1. 【角色】→ `全部`：标题、搜索、8 Chips、推荐 Hero + 两张次卡、最近使用有数据才出现、全部角色单列。
2. 固定推荐必须是：纳瓦尔 → 理查德·费曼 → 纳西姆·塔勒布；页面不得出现 `为你推荐`。
3. 任意具体分类：分类说明 + 双列目录；大字体/窄屏时允许安全降单列。
4. `生活工具`：确认 5 个主发现角色可见。
5. 搜索 + 分类交集；无结果显示明确空态。
6. 真人头像稳定、真人卡 `AI 模拟角色`；功能型角色使用稳定非真人 fallback。
7. 点击角色进入真正二级全屏详情；一级 bottom nav 不应覆盖详情内容。
8. 详情正文可滚动，底部两个动作固定且至少 48dp 可点击；状态栏/底部手势区不遮挡。
9. 建立一个当前会话后验证 `增加到当前会话` 可用；加入后回【对话】确认角色真的出现在 participant 列表。
10. 用一个早期 20 之外的功能角色（优先 `meeting-to-action`）执行“开始新对话”，确认真实进入 Dialog participant，并实际发一条简单问题，确认能进入正式 Skill asset 的回答流程。无需评价模型内容质量，只确认没有“角色不存在/资产不存在/空 Prompt”类错误。
11. 快速切换一次会话后再执行“增加到当前会话”，确认角色不会出现在错误会话；若难以稳定制造竞态，记录 `NOT_REPRODUCED`，不得臆造 PASS。

### UI-01 外部依赖说明

当前 UI-02 分支没有实现最终共享 `对话 / 角色 / 资料 / 我的` Bottom Navigation。若运行时仍显示旧壳层 `首页 / 议题 / Skill / 资料与成果`：

- 记录为 **UI-01 dependency / NOT UI-02 regression**；
- 不修改 UI-02；
- 视觉对比时忽略一级底栏文案/图标本身，只检查 UI-02 内容区和二级详情是否不被底栏覆盖。

## 5. 最近使用语义

手工检查：

- 只打开详情，然后返回：不得新增最近使用；
- 收藏 / 搜索 / 筛选：不得新增最近使用；
- 成功“开始新对话”：应新增 recent；
- 成功“增加到当前会话”：应新增/刷新 recent；
- unknown/失败动作：不得写 recent。

## 6. 最终回报格式

只返回：

```text
UI-02 LOCAL ACCEPTANCE
HEAD: <sha>
WORKTREE_BEFORE: CLEAN / DIRTY
WORKTREE_AFTER: CLEAN / DIRTY

JVM_TARGETED: PASS / FAIL
COMPILE_DEBUG_KOTLIN: PASS / FAIL
UNIT_ALL: PASS / FAIL
LINT_DEBUG: PASS / FAIL
ASSEMBLE_DEBUG: PASS / FAIL
ASSEMBLE_ANDROID_TEST: PASS / FAIL / BLOCKED_BASELINE
INSTRUMENTATION_ROLE_AVATAR: PASS / FAIL / BLOCKED_BASELINE / NOT_RUN
INSTRUMENTATION_ROLE_CATALOG: PASS / FAIL / BLOCKED_BASELINE / NOT_RUN
INSTRUMENTATION_ROLE_DETAIL: PASS / FAIL / BLOCKED_BASELINE / NOT_RUN
INSTRUMENTATION_ROLE_ADAPTER: PASS / FAIL / BLOCKED_BASELINE / NOT_RUN
INSTRUMENTATION_ROLE_SESSION_ACTIONS: PASS / FAIL / BLOCKED_BASELINE / NOT_RUN
VISUAL_XIAOMI_14_ULTRA: PASS / FAIL / NOT_RUN
MANUAL_ROLE_CONVERSATION_BRIDGE: PASS / FAIL / NOT_RUN
RECENT_USE_SEMANTICS: PASS / FAIL / NOT_RUN
UI01_NAV_DEPENDENCY: PRESENT / RESOLVED

FIRST_UI02_FAILURE_COMMAND: <none or command>
BASELINE_BLOCKER: <none or IssueExecutionStopAvailabilityTest.kt:49>
ERROR_FILE_LINE: <none or path:line>
KEY_ERROR: <最多 8 行>
VISUAL_ISSUES: <none or concise bullets>
SCREENSHOT_PATHS: <paths only>
OVERALL_UI02: PASS / FAIL
```

规则：

- 如果唯一 AndroidTest 问题是已知基线阻塞，而 JVM、生产编译、单测、lint、APK 与手工 UI/功能均通过，可以把 `OVERALL_UI02` 判为 `PASS`，同时保留 `BASELINE_BLOCKER`。
- 不要附完整 Gradle 日志、logcat 或整份截图分析。
