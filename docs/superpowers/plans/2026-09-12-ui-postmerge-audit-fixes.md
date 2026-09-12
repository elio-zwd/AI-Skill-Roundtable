# UI-01 / UI-02 Post-Merge Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `4bb47dd..871057b` 合入后静态审查确认的 UI-01 / UI-02 规格偏移、角色会话动作一致性问题与失真的验收状态，并最终只以 Xiaomi 14 Ultra 1440×3200 portrait zh-CN 作为设备 UI 验收基准。

**Architecture:** 保持现有 App Root Navigation、Route → Screen 分层和 OfficialSkillCatalog → compatibility adapter → legacy conversation 链路不变。修复集中在三处：角色详情浏览不得产生会话副作用；Start New / Add Current 必须有失败补偿、单次 in-flight UI 状态与明确失败反馈；Mine B 必须只展示真实非敏感个人背景摘要并恢复冻结文案。最后重新对账状态文档，设备验证交给本地 AI 只读执行。

**Tech Stack:** Android / Kotlin / Jetpack Compose / Material 3 / Room / JUnit / AndroidX Compose Test。

## Global Constraints

- 基线：`main@871057b33d37396f9e560ac18887d3aa0083c6ef`。
- 唯一修复分支：`codex/ui-postmerge-audit-fixes`；本 Plan 阶段不创建额外分支/PR。
- 不修改 UI-03、Gemini transport、Room Schema、Gradle/依赖、`check-app-identity.ps1` 历史 baseline。
- 只针对个人使用场景，不新增面向公众发布的额外安全/账户/合规体系。
- UI 尺寸只以 Xiaomi 14 Ultra、1440×3200、Android 竖屏、zh-CN 为最终视觉验收目标；不为其他屏幕单独适配。
- Bug 修复遵循 `systematic-debugging` 与 TDD：先写能暴露当前问题的测试，再做最小修复。
- 在远端代码与测试修改完成前不调用本地 AI；最终本地 AI 只读验证，不改代码、不 commit/push/merge，并将截图/必要证据上传 Google Drive。

---

## Audit Findings Frozen for This Plan

1. `App.kt` 在进入 `SKILL_DETAIL_PATTERN` 时调用 `ensureConversationReady()`；无现有会话时仅浏览角色详情就会静默创建空会话，破坏“浏览 != 使用动作”语义，并使“增加到当前会话”失去真实禁用条件。
2. `createNewSessionWithSkillRole()` / `addSkillRoleToCurrentSessionAwait()` 在 participant/session 已持久化后若 settle 超时或最终 roster 校验失败，只返回 `false`，没有完整补偿；失败结果可能留下半完成 session / participant 状态。
3. 角色详情动作没有 in-flight 状态；快速重复点击可并发启动多个 Start New / Add Current 协程。动作失败时 UI 没有反馈，仅写日志。
4. `MineScreen` 固定展示 `职业目标 / 可用时间 / 表达偏好`，即使用户没有对应 PersonalContext；测试也把这些示例文案固化为“正确行为”。冻结 Spec 要求状态来自真实 repository，不得用静态示例冒充用户数据。
5. Mine 快捷卡冻结文案为 `模型与 API Key`，当前实现/测试写成 `AI 管理`。
6. UI-01 / UI-02 status/plan 中仍存在 `Open + Draft`、旧 Head，以及把 1080×2400 emulator 证据写成 Xiaomi 14 Ultra 目标验收完成等过期/过度结论；当前 main 实际已合并 #59/#60/#61。

---

### Task 1: 恢复“浏览角色详情不创建会话”契约

**Files:**
- Modify: `app/src/test/java/com/elio/jianyu/ui/JianyuNavigationArchitectureTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/App.kt`

**Interfaces:**
- Consumes: `DialogRoute` 继续在真正进入对话页时调用 `RoundtableViewModel.ensureConversationReady()`。
- Produces: 角色详情只读取 `currentSessionId` 决定 Add Current 是否可用，不再主动创建/选择会话。

- [ ] **Step 1: 写失败测试**

在 `JianyuNavigationArchitectureTest` 增加：

```kotlin
@Test
fun app_skillDetailDoesNotPrepareConversationAsBrowseSideEffect() {
    val appSource = uiRoot.resolve("App.kt").readText()
    assertFalse(
        "浏览 Skill 角色详情不得隐式创建/选择对话",
        appSource.contains("viewModel.ensureConversationReady()"),
    )
}
```

- [ ] **Step 2: 运行定向 JVM 测试并确认 RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.elio.jianyu.ui.JianyuNavigationArchitectureTest"
```

Expected: 新测试因 `App.kt` 当前包含 `viewModel.ensureConversationReady()` 而 FAIL。

- [ ] **Step 3: 最小修复**

删除 `App.kt` 中仅针对 `SKILL_DETAIL_PATTERN` 的 `LaunchedEffect`；保留：

```kotlin
canAddToCurrentConversation = currentSessionId != null
```

不要改 `DialogRoute` 的真正对话初始化行为。

- [ ] **Step 4: 重新运行定向 JVM 测试**

Expected: PASS。

---

### Task 2: 修复角色会话动作失败补偿与半完成状态

**Files:**
- Modify: `app/src/androidTest/java/com/elio/jianyu/viewmodel/RoundtableViewModelSkillRoleActionsTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModelSkillRoleActions.kt`
- Modify: `app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt`

**Interfaces:**
- Consumes: `ChatRepository.deleteSession()`, `ConversationSessionPreferences.clearSession()/setParticipantIds()`, `selectSession()`。
- Produces: Start New / Add Current 的 Boolean `false` 必须意味着本次业务 mutation 已补偿，不留下新 session 或新增 participant；测试可传极短 settle timeout 强制进入失败分支。

- [ ] **Step 1: 扩展 AndroidTest，先固定失败语义**

新增两个测试：

```kotlin
@Test
fun startNew_whenRosterDoesNotSettle_rollsBackCreatedSessionAndSelection() = runBlocking {
    val viewModel = RoundtableViewModel(application)
    val sessionsBefore = viewModel.allSessions.first().map { it.id }.toSet()
    val previousSessionId = viewModel.currentSessionId.value

    val created = viewModel.createNewSessionWithSkillRole(
        skillId = "meeting-to-action",
        title = "rollback-test",
        settleTimeoutMs = 0L,
    )

    assertFalse(created)
    assertEquals(sessionsBefore, viewModel.allSessions.first().map { it.id }.toSet())
    assertEquals(previousSessionId, viewModel.currentSessionId.value)
}

@Test
fun addCurrent_whenRosterDoesNotSettle_restoresOriginalParticipants() = runBlocking {
    val viewModel = RoundtableViewModel(application)
    assertTrue(viewModel.createNewSessionWithSkillRole("meeting-to-action"))
    val original = viewModel.currentParticipantIds.value

    val added = viewModel.addSkillRoleToCurrentSessionAwait(
        skillId = "study-planner",
        settleTimeoutMs = 0L,
    )

    assertFalse(added)
    assertEquals(original, ConversationSessionPreferences(application).getParticipantIds(
        requireNotNull(viewModel.currentSessionId.value),
        emptyList(),
    ))
}
```

测试实现时补齐必要 imports，并避免依赖跨测试残留：使用当前测试已有数据库/会话清理模式或唯一 session 标题/ID。

- [ ] **Step 2: 运行定向 AndroidTest，确认 RED**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.viewmodel.RoundtableViewModelSkillRoleActionsTest
```

Expected: 当前代码在强制 settle 失败后保留 mutation，因此新增断言 FAIL。

- [ ] **Step 3: 增加最小恢复入口**

在 `RoundtableViewModel` 增加仅供该业务动作使用的 `internal` 恢复函数：

```kotlin
internal fun restoreSessionSelectionAfterRoleAction(sessionId: Long?) {
    if (sessionId == null) {
        sessionNavigationVersion += 1
        _currentSessionId.value = null
        _currentSession.value = null
        _currentParticipantIds.value = emptyList()
    } else {
        selectSession(sessionId)
    }
}
```

- [ ] **Step 4: 为两条动作加入补偿**

`createNewSessionWithSkillRole`：捕获 `previousSessionId`；settle 失败时依次清理新 session 的 participant preference、删除新 session、恢复原选择，再返回 `false`。

`addSkillRoleToCurrentSessionAwait`：settle 失败时恢复 `originalParticipantIds`，重新选择同一 session 使 ViewModel roster 回到原状态，再返回 `false`。

两函数增加：

```kotlin
settleTimeoutMs: Long = ROLE_ACTION_SETTLE_TIMEOUT_MS
```

并传给 `refreshSessionRosterAndAwait(...)`。禁止吞掉补偿异常；记录 privacy-safe reason，同时最终仍返回失败。

- [ ] **Step 5: 重跑定向 AndroidTest**

Expected: 原成功用例 + 两个失败补偿用例全部 PASS。

---

### Task 3: 建立角色详情单次动作状态与失败反馈

**Files:**
- Modify: `app/src/androidTest/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailScreenTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailRoute.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailScreen.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/App.kt`

**Interfaces:**
- `SkillRoleDetailRoute` consumes suspend actions returning `Boolean`。
- `SkillRoleDetailScreen` consumes `actionInProgress: Boolean` and `actionMessage: String?`。
- 成功时 Route 调 `onConversationReady()`；失败时留在详情页并显示错误。

- [ ] **Step 1: 写 Screen RED 测试**

给 `SkillRoleDetailScreenTest` 增加：

```kotlin
@Test
fun actionInProgress_disablesBothConversationActionsAndFailureIsVisible() {
    // 使用现有 executable role fixture
    SkillRoleDetailScreen(
        role = role,
        isFavorite = false,
        canAddToCurrentConversation = true,
        actionInProgress = true,
        actionMessage = "操作未完成，请重试",
        ...
    )
    // “开始新对话”与“增加到当前会话”均 assertIsNotEnabled()
    // 错误文案 assertExists()
}
```

- [ ] **Step 2: 编译/运行定向测试确认 RED**

Expected: 当前 Screen 不存在两个新参数或按钮仍可点击。

- [ ] **Step 3: Route 管理单一 in-flight 动作**

`SkillRoleDetailRoute` 使用 `rememberSaveable(resolvedSkillId)` 保存：

```kotlin
var actionInProgress by rememberSaveable(resolvedSkillId) { mutableStateOf(false) }
var actionMessage by rememberSaveable(resolvedSkillId) { mutableStateOf<String?>(null) }
```

Start New / Add Current 点击时若已 in-flight 直接忽略；否则置 `true`，执行 suspend callback。`true` → `onConversationReady()`；`false`/异常 → `actionMessage = "操作未完成，请重试"`；最后恢复 `false`。

收藏写入失败同样显示明确错误，不再静默忽略。

- [ ] **Step 4: Screen 禁止重复提交并显示错误**

两个按钮 `enabled` 条件统一增加 `&& !actionInProgress`；在动作区下方显示 `actionMessage`，使用 `MaterialTheme.colorScheme.error`，不新增页面私有颜色。

- [ ] **Step 5: App 只提供业务动作和导航**

移除 `roleActionScope.launch`。改为向 Route 传 suspend lambda：

```kotlin
onStartNewConversation = { selectedSkillId ->
    val success = viewModel.createNewSessionWithSkillRole(selectedSkillId)
    if (success) runtime?.preferences?.recordSkillUsed(selectedSkillId, System.currentTimeMillis())
    success
}
```

Add Current 同理；`onConversationReady` 统一导航到 `HOME`。App 不持有页面专属动作状态。

- [ ] **Step 6: 重跑详情 Screen 与角色动作定向测试**

Expected: PASS。

---

### Task 4: 修复 Mine B 假摘要与冻结文案偏移

**Files:**
- Modify: `app/src/test/java/com/elio/jianyu/ui/screens/mine/MineUiStateTest.kt`
- Modify: `app/src/androidTest/java/com/elio/jianyu/ui/screens/mine/MineScreenTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/mine/MineUiState.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/mine/MineRoute.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/mine/MineScreen.kt`

**Interfaces:**
- `MineUiState.personalContextSummaryLabels: List<String>` 只承载 repository 返回的真实、`sensitive == false`、非空 title，去重后最多 3 项。
- 快捷卡展示名固定为 `模型与 API Key`，点击仍进入现有 AI Management 页面。

- [ ] **Step 1: 写 UiState/Screen RED 测试**

覆盖：

```kotlin
// 0 项背景时不得出现“职业目标 / 可用时间 / 表达偏好”示例 Chip。
// 提供 summaryLabels = listOf("职业方向", "学习计划") 时只显示这两个真实 label。
// 快捷卡必须显示“模型与 API Key”，不得把“AI 管理”作为一级卡片标题。
```

- [ ] **Step 2: 运行 Mine 定向 JVM / AndroidTest，确认 RED**

- [ ] **Step 3: 建立纯映射**

在 `MineUiState.kt` 增加纯函数，从 `List<PersonalContext>` 生成最多 3 个安全 title：

```kotlin
internal fun List<PersonalContext>.mineSummaryLabels(): List<String> =
    asSequence()
        .filterNot(PersonalContext::sensitive)
        .map { it.title.trim() }
        .filter(String::isNotEmpty)
        .distinct()
        .take(3)
        .toList()
```

- [ ] **Step 4: Route 保存真实 summary labels**

`MineRoute` 成功读取 `listPersonalContexts()` 后同时更新 count 与 `mineSummaryLabels()`；失败时 labels 清空。

- [ ] **Step 5: Screen 只渲染真实 labels 并恢复冻结文案**

删除硬编码 `listOf("职业目标", "可用时间", "表达偏好")`。仅当 `uiState.personalContextSummaryLabels` 非空时渲染 Chip。

将快捷卡 title 从 `AI 管理` 改为 `模型与 API Key`；内部 testTag / destination 可继续使用现有 AI management 命名。

- [ ] **Step 6: 重跑 Mine 定向测试**

Expected: PASS。

---

### Task 5: 远端收口、自审与状态对账

**Files:**
- Modify: `docs/superpowers/status/2026-09-10-ui-02-role-page.md`
- Modify: `docs/superpowers/status/2026-09-12-ui-01-mine-navigation.md`
- Create: `docs/superpowers/status/2026-09-12-ui-postmerge-audit-fixes.md`
- Modify this Plan checkbox state as tasks finish.

- [ ] **Step 1: 静态回读与净差异审查**

确认没有 UI-03、Room Schema、Gemini、Gradle/依赖、身份 CI 的无关改动。

- [ ] **Step 2: 远端可执行验证**

运行/检查可用的 JVM/compile/AndroidTest compile；若 GitHub `build` 仍只失败于已知 identity gate，记录为 `PRE_EXISTING_BASELINE`，不得写成修复分支测试失败或通过。

- [ ] **Step 3: 按 `requesting-code-review` 模板自审**

重点复查：
- 浏览详情零 session side effect；
- Boolean false 对应补偿完成；
- 重复点击被拦截；
- recent-use 仅成功动作写入；
- Mine 不显示静态假 PersonalContext；
- App 仍只负责导航/组装，不持有详情专属状态。

- [ ] **Step 4: 修正文档事实状态**

#59/#60/#61 已 merged；旧 emulator `1080×2400` 不能作为 UI-01 的 Xiaomi 14 Ultra 设备验收证据。新 status 明确：`LOCAL_XIAOMI14ULTRA_ACCEPTANCE: PENDING`。

- [ ] **Step 5: 代码初步完成后生成本地 AI 只读验收任务**

在 Google Drive 创建独立验收文档，要求本地 AI：
- 不改代码、不自动修复、不 commit/push/merge；
- 基于本修复分支最新 Head；
- compile / targeted JVM / `assembleDebugAndroidTest` / targeted instrumentation；
- 使用真实 Xiaomi 14 Ultra，1440×3200 portrait zh-CN；
- 验证角色详情浏览不建空会话、Start New/Add Current 单次动作与失败反馈、Mine 真实摘要、四项 Root Nav、Dialog/Issue 回归；
- 输出 PASS/FAIL + 精简关键日志；
- UI 截图与必要 UI dump 上传 Google Drive 并返回链接。

- [ ] **Step 6: 本地证据返回后再执行最终 verification-before-completion**

只有真实 Xiaomi 14 Ultra 证据与必要测试均满足后，才把 status 改为完成；否则根据证据继续修复，不宣布完成。
