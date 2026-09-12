# UI-01 / UI-02 Post-Merge Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> Plan review: completed against Google Drive project source **Superpowers v6.3.0** (`writing-plans` + `plan-document-reviewer-prompt`). The original draft was revised before production-code work.

**Goal:** 修复 `4bb47dd..871057b` 合入后静态审查确认的 UI-01 / UI-02 规格偏移、角色会话动作一致性问题与失真的验收状态，并最终只以 Xiaomi 14 Ultra 1440×3200 portrait zh-CN 作为设备 UI 验收基准。

**Architecture:** 保持现有 App Root Navigation、Route → Screen 分层和 OfficialSkillCatalog → compatibility adapter → legacy conversation 链路不变。修复集中在三处：角色详情浏览不得产生会话副作用；Start New / Add Current 的失败必须补偿本次 session/participant mutation，并在详情页具有单次 in-flight 状态与明确失败反馈；Mine B 只展示 repository 返回的真实个人背景摘要并恢复冻结文案。最后对账历史状态文档，设备验证交给本地 AI 只读执行。

**Tech Stack:** Android / Kotlin / Jetpack Compose / Material 3 / Room / JUnit / AndroidX Compose Test。

## Global Constraints

- 基线：`main@871057b33d37396f9e560ac18887d3aa0083c6ef`。
- 唯一修复分支：`codex/ui-postmerge-audit-fixes`；不创建第二个修复分支。
- 在远端代码初步完成、需要本地验证前不创建 PR；用户未要求 merge，禁止自动 merge。
- 不修改 UI-03、Gemini transport、Room Schema、Gradle/依赖、`check-app-identity.ps1` 历史 baseline。
- 只针对个人使用场景，不新增面向公众发布的额外安全/账户/合规体系。
- UI 尺寸只以 Xiaomi 14 Ultra、1440×3200、Android 竖屏、zh-CN 为最终视觉验收目标；不为其他屏幕单独适配。
- Bug 修复遵循 Google Drive Superpowers v6.3.0 `systematic-debugging` 与 `test-driven-development`：先固定根因，再写能暴露问题的测试，再做最小修复。
- RED 可以是缺失新接口导致的测试编译失败；必须证明失败由目标行为缺失造成，不得用拼写/测试夹具错误制造 RED。
- 本地 AI 最终只读验证：不得改代码、自动修复、commit、push、merge；截图和必要证据上传 Google Drive。

---

## Audit Findings Frozen for This Plan

1. `App.kt` 在 `SKILL_DETAIL_PATTERN` 上调用 `ensureConversationReady()`；只浏览角色详情就可能静默创建空会话，破坏“浏览 != 使用动作”语义，并使 Add Current 的禁用条件失真。
2. `createNewSessionWithSkillRole()` / `addSkillRoleToCurrentSessionAwait()` 在 participant/session 已持久化后若 settle 超时或最终 roster 校验失败，只返回 `false`；失败可能留下新 session 或新增 participant。
3. 详情动作没有单次 in-flight 门禁；快速重复点击可并发启动多个动作。失败仅写日志，页面无反馈。
4. `MineScreen` 固定展示 `职业目标 / 可用时间 / 表达偏好`，即使 repository 没有这些 PersonalContext；这违反 UI-01 Spec“状态投影必须来自真实 repository”。
5. Mine 快捷卡冻结文案为 `模型与 API Key`，当前实现/测试写成 `AI 管理`。
6. UI-01 / UI-02 status/plan 仍保留 `Open + Draft`、旧 Head，并把 `1080×2400` emulator 证据描述成已完成 Xiaomi 14 Ultra 目标验收；当前 main 实际已合并 #59/#60/#61。

## Plan Reviewer Corrections

- 不用 `viewModel.allSessions.first()` 验证回滚；其 `StateFlow` 使用 `SharingStarted.WhileSubscribed` 且有初始空列表，不能作为可靠数据库事实。测试直接从同一 `ChatRepository` 的 Room Flow 读取。
- `actionInProgress` 是短生命周期操作状态，使用 `remember`，**不得**使用 `rememberSaveable`，避免配置/进程恢复后恢复 `true` 而执行协程已不存在。
- 失败补偿不得覆盖用户在等待期间主动切换到的新 session；恢复选择前必须检查当前 session 仍等于本动作预期 session。
- 不扩大到收藏动作错误提示、UI-03 或无关重构；只修已确认根因。

---

### Task 1: 恢复“浏览角色详情不创建会话”契约

**Files:**
- Modify: `app/src/test/java/com/elio/jianyu/ui/JianyuNavigationArchitectureTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/App.kt`

**Interfaces:**
- `DialogRoute` 继续在真正进入对话页时调用 `RoundtableViewModel.ensureConversationReady()`。
- `App.kt` 仅组装页面，不主动准备角色详情的会话。
- `canAddToCurrentConversation = currentSessionId != null` 继续作为真实 Add Current 门禁。

- [ ] **Step 1: 写失败测试**

在 `JianyuNavigationArchitectureTest` 增加：

```kotlin
@Test
fun app_doesNotPrepareConversationFromNavigationShell() {
    val appSource = uiRoot.resolve("App.kt").readText()
    assertFalse(
        "App 导航壳不得因浏览 Skill 角色详情隐式创建/选择会话",
        appSource.contains("viewModel.ensureConversationReady()"),
    )
}
```

**为什么当前应失败：** `App.kt` 目前包含 `SKILL_DETAIL_PATTERN` → `viewModel.ensureConversationReady()` 的 `LaunchedEffect`。

- [ ] **Step 2: 在 RED commit 上由最终本地验证补跑定向 JVM 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.elio.jianyu.ui.JianyuNavigationArchitectureTest"
```

Expected: 仅新增测试因上述调用存在而 FAIL。

- [ ] **Step 3: 最小修复**

删除 `App.kt` 中只针对 `SKILL_DETAIL_PATTERN` 的 `LaunchedEffect`。保留：

```kotlin
canAddToCurrentConversation = currentSessionId != null
```

不要改 `DialogRoute` 的真正对话初始化行为。

- [ ] **Step 4: GREEN 验证目标**

同一 JVM 测试应 PASS。

---

### Task 2: 修复角色会话动作失败补偿

**Files:**
- Modify: `app/src/androidTest/java/com/elio/jianyu/viewmodel/RoundtableViewModelSkillRoleActionsTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModelSkillRoleActions.kt`
- Modify: `app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt`

**Interfaces:**
- `createNewSessionWithSkillRole(skillId, title, settleTimeoutMs)`：`false` 时不得留下本次创建的 ChatSession / participant preference。
- `addSkillRoleToCurrentSessionAwait(skillId, settleTimeoutMs)`：`false` 时 target session 的 participant preference 必须恢复原值。
- `restoreSessionSelectionAfterRoleAction(expectedCurrentSessionId, restoreSessionId)`：只有用户当前仍停留在本动作 session 时才恢复选择；不得覆盖较新的用户导航。
- `settleTimeoutMs` 默认仍为 `ROLE_ACTION_SETTLE_TIMEOUT_MS`，仅作为可重复触发失败分支的测试 seam，不改变生产调用方语义。

- [ ] **Step 1: 写失败 AndroidTest**

在 `runBlocking` 中使用同一数据库的真实 `ChatRepository`，不要从 `viewModel.allSessions` 初始 StateFlow 取证：

```kotlin
val database = RoundtableDatabase.getDatabase(application, this)
val chatRepository = ChatRepository(database.chatDao())
```

新增：

```kotlin
@Test
fun startNew_whenSettleFails_removesCreatedSessionAndRestoresSelection() = runBlocking {
    val viewModel = RoundtableViewModel(application)
    val database = RoundtableDatabase.getDatabase(application, this)
    val chatRepository = ChatRepository(database.chatDao())
    val sessionsBefore = chatRepository.allSessions.first().map { it.id }.toSet()
    val previousSessionId = viewModel.currentSessionId.value

    val success = viewModel.createNewSessionWithSkillRole(
        skillId = "meeting-to-action",
        title = "rollback-test",
        settleTimeoutMs = 0L,
    )

    assertFalse(success)
    val sessionsAfter = chatRepository.allSessions.first().map { it.id }.toSet()
    assertEquals(sessionsBefore, sessionsAfter)
    assertEquals(previousSessionId, viewModel.currentSessionId.value)
}
```

```kotlin
@Test
fun addCurrent_whenSettleFails_restoresOriginalParticipants() = runBlocking {
    val viewModel = RoundtableViewModel(application)
    assertTrue(viewModel.createNewSessionWithSkillRole("meeting-to-action"))
    val sessionId = requireNotNull(viewModel.currentSessionId.value)
    val original = viewModel.currentParticipantIds.value

    val success = viewModel.addSkillRoleToCurrentSessionAwait(
        skillId = "study-planner",
        settleTimeoutMs = 0L,
    )

    assertFalse(success)
    assertEquals(
        original,
        ConversationSessionPreferences(application).getParticipantIds(sessionId, emptyList()),
    )
    withTimeout(5_000L) {
        viewModel.currentParticipantIds.first { it == original }
    }
}
```

**为什么当前应 RED：** 当前两个函数没有 `settleTimeoutMs` seam；即使补上 seam，当前失败路径也不会完整补偿 mutation。

- [ ] **Step 2: 实现最小恢复入口**

在 `RoundtableViewModel` 增加：

```kotlin
internal fun restoreSessionSelectionAfterRoleAction(
    expectedCurrentSessionId: Long,
    restoreSessionId: Long?,
) {
    if (_currentSessionId.value != expectedCurrentSessionId) return
    if (restoreSessionId == null) {
        sessionNavigationVersion += 1
        _currentSessionId.value = null
        _currentSession.value = null
        _currentParticipantIds.value = emptyList()
    } else {
        selectSession(restoreSessionId)
    }
}
```

- [ ] **Step 3: Start New 失败补偿**

捕获 `previousSessionId`。创建新 session 后若 settle 失败：

```kotlin
conversationPreferences.clearSession(sessionId)
chatRepository.deleteSession(sessionId)
restoreSessionSelectionAfterRoleAction(
    expectedCurrentSessionId = sessionId,
    restoreSessionId = previousSessionId,
)
return roleActionFailure("start_new", "session_roster_not_settled")
```

`deleteSession`/清理异常必须记录 privacy-safe 日志并仍返回失败；不能改为 success。

- [ ] **Step 4: Add Current 失败补偿**

若 settle 失败：

```kotlin
conversationPreferences.setParticipantIds(sessionId, originalParticipantIds)
restoreSessionSelectionAfterRoleAction(
    expectedCurrentSessionId = sessionId,
    restoreSessionId = sessionId,
)
return roleActionFailure("add_current", "session_roster_not_settled")
```

如果用户已切换到其他 session，helper 不覆盖该新选择；target session preference 仍必须恢复。

- [ ] **Step 5: GREEN 验证目标**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.viewmodel.RoundtableViewModelSkillRoleActionsTest
```

Expected: 原成功测试 + 两个失败补偿测试全部 PASS。

---

### Task 3: 建立详情动作单次 in-flight 状态与失败反馈

**Files:**
- Modify: `app/src/androidTest/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailScreenTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailRoute.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailScreen.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/App.kt`

**Interfaces:**
- `SkillRoleDetailRoute` 接收两个 `suspend (String) -> Boolean` 业务动作和 `onConversationReady: () -> Unit`。
- `SkillRoleDetailScreen` 接收 `actionInProgress: Boolean`、`actionMessage: String?`。
- `actionInProgress` 用 `remember(resolvedSkillId)`，不得 saveable。
- 成功后 Route 调 `onConversationReady()`；失败留在详情并显示 `操作未完成，请重试`。

- [ ] **Step 1: 写 Screen RED 测试**

新增：

```kotlin
@Test
fun actionInProgress_disablesBothConversationActionsAndShowsFailure() {
    val role = projection.allRoles.first { it.isExecutable && !it.isPersonSimulation }
    composeRule.setContent {
        SkillRoundtableTheme {
            SkillRoleDetailScreen(
                role = role,
                isFavorite = false,
                canAddToCurrentConversation = true,
                actionInProgress = true,
                actionMessage = "操作未完成，请重试",
                onBack = {},
                onToggleFavorite = {},
                onStartNewConversation = {},
                onAddToCurrentConversation = {},
            )
        }
    }

    composeRule.onNodeWithText("开始新对话").assertIsNotEnabled()
    composeRule.onNodeWithText("增加到当前会话").assertIsNotEnabled()
    composeRule.onNodeWithText("操作未完成，请重试").assertExists()
}
```

- [ ] **Step 2: Route 管理单一动作**

使用：

```kotlin
var actionInProgress by remember(resolvedSkillId) { mutableStateOf(false) }
var actionMessage by remember(resolvedSkillId) { mutableStateOf<String?>(null) }
```

共用 helper 的语义：

```kotlin
if (actionInProgress) return@...
actionInProgress = true
actionMessage = null
scope.launch {
    try {
        if (action(resolvedSkillId)) {
            onConversationReady()
        } else {
            actionMessage = "操作未完成，请重试"
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        actionMessage = "操作未完成，请重试"
    } finally {
        actionInProgress = false
    }
}
```

不得用 `runCatching` 吞掉 `CancellationException`。

- [ ] **Step 3: Screen 门禁与错误文案**

- `开始新对话`：`enabled = role.isExecutable && !actionInProgress`
- `增加到当前会话`：`enabled = role.isExecutable && canAddToCurrentConversation && !actionInProgress`
- 动作区下方仅在 `actionMessage != null` 时显示错误色文案。

- [ ] **Step 4: App 只提供业务动作，不持有页面状态**

删除 `roleActionScope`。传入：

```kotlin
onStartNewConversation = { selectedSkillId ->
    val success = viewModel.createNewSessionWithSkillRole(selectedSkillId)
    if (success) {
        runtime?.preferences?.recordSkillUsed(selectedSkillId, System.currentTimeMillis())
    }
    success
}
```

Add Current 同理；`onConversationReady` 统一调用：

```kotlin
navController.navigateToTopLevel(AppDestination.HOME)
```

- [ ] **Step 5: GREEN 验证目标**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.skills.SkillRoleDetailScreenTest
```

并重跑 Task 2 角色动作测试。

---

### Task 4: 修复 Mine B 假摘要与冻结文案

**Files:**
- Modify: `app/src/test/java/com/elio/jianyu/ui/screens/mine/MineUiStateTest.kt`
- Modify: `app/src/androidTest/java/com/elio/jianyu/ui/screens/mine/MineScreenTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/mine/MineUiState.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/mine/MineRoute.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/mine/MineScreen.kt`

**Interfaces:**
- `MineUiState.personalContextSummaryLabels: List<String>` 只承载当前 ACTIVE 查询实际返回的 PersonalContext 中 `sensitive == false`、title trim 后非空的去重标题，最多 3 项。
- `personalContextCount` 仍表示 repository 当前 ACTIVE 查询返回的真实总数；敏感项只是不在摘要 Chip 展示标题。
- 快捷卡标题固定为 `模型与 API Key`；点击仍进入现有 `AiManagementRoute`。

- [ ] **Step 1: 写 UiState RED 测试**

新增纯映射测试：

```kotlin
@Test
fun summaryLabels_onlyUseRealNonSensitiveUniqueTitles() {
    val contexts = listOf(
        personalContext(title = "职业方向", sensitive = false),
        personalContext(title = " 职业方向 ", sensitive = false),
        personalContext(title = "隐私背景", sensitive = true),
        personalContext(title = "学习计划", sensitive = false),
    )

    assertEquals(
        listOf("职业方向", "学习计划"),
        contexts.toMineSummaryLabels(),
    )
}
```

测试 fixture 必须构造真实 `PersonalContext`，不得 mock repository 行为。

- [ ] **Step 2: 写 Screen RED 测试**

更新 `MineScreenTest`：

```kotlin
MineUiState(
    personalContextCount = 2,
    personalContextSummaryLabels = listOf("职业方向", "学习计划"),
    ...
)
```

断言：

```kotlin
composeRule.onNodeWithText("职业方向").assertIsDisplayed()
composeRule.onNodeWithText("学习计划").assertIsDisplayed()
composeRule.onNodeWithText("职业目标").assertDoesNotExist()
composeRule.onNodeWithText("可用时间").assertDoesNotExist()
composeRule.onNodeWithText("表达偏好").assertDoesNotExist()
composeRule.onNodeWithText("模型与 API Key").assertIsDisplayed()
```

另加 0 项状态，确认没有任何示例 Chip。

- [ ] **Step 3: 最小实现真实摘要映射**

在 `MineUiState.kt` 增加：

```kotlin
internal fun List<PersonalContext>.toMineSummaryLabels(): List<String> =
    asSequence()
        .filterNot(PersonalContext::sensitive)
        .map { it.title.trim() }
        .filter(String::isNotEmpty)
        .distinct()
        .take(3)
        .toList()
```

`MineRoute` 成功读取后同时设置真实 count 和该 labels；失败时 count=null、labels=emptyList()。

- [ ] **Step 4: Screen 只渲染真实 labels**

删除固定：

```kotlin
listOf("职业目标", "可用时间", "表达偏好")
```

仅当 `uiState.personalContextSummaryLabels.isNotEmpty()` 时渲染 Chip row。快捷卡标题改成：

```kotlin
"模型与 API Key"
```

- [ ] **Step 5: GREEN 验证目标**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.elio.jianyu.ui.screens.mine.MineUiStateTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.mine.MineScreenTest
```

Expected: PASS。

---

### Task 5: 远端自审、状态对账与本地 AI 最终验证

**Files:**
- Modify: `docs/superpowers/status/2026-09-10-ui-02-role-page.md`
- Modify: `docs/superpowers/status/2026-09-12-ui-01-mine-navigation.md`
- Create: `docs/superpowers/status/2026-09-12-ui-postmerge-audit-fixes.md`
- Update this Plan checkbox state as tasks are completed.

**Interfaces:**
- 历史 UI-01/UI-02 status 只记录当时证据；不得继续写当前 PR Open/Draft 或当前 merge-ready。
- 新 status 是本轮事实来源，明确区分“远端静态审查/代码已改”“本地构建/测试”“真机 UI”。

- [ ] **Step 1: 远端代码自审**

对 `871057b..HEAD` 检查：

- 无 UI-03、Room Schema、Gradle/依赖、Gemini transport 修改；
- `App.kt` 不再从角色详情准备会话；
- role action false 路径有补偿且不覆盖新的用户 session 选择；
- detail action 不能重复提交且失败可见；
- Mine 不再伪造摘要 Chip，文案为 `模型与 API Key`；
- 不新增第二套旧实现或无调用方兼容层。

- [ ] **Step 2: 修正历史状态文档中的当前态措辞**

至少将：

- PR #59 / #61 `Open + Draft` → 已于 2026-09-11 merge，标明 merge commit；
- `1080×2400 emulator` → 仅历史 emulator 证据，不得写成 Xiaomi 14 Ultra 1440×3200 最终视觉通过；
- 本轮发现并修复的角色详情副作用、失败补偿和 Mine 假摘要，记录为“post-merge audit found/fixed”，不能继续写“未发现”。

- [ ] **Step 3: 远端阶段只报告未验证项，不提前宣布完成**

在新 status 写：

```text
REMOTE_CODE_REVIEW: PASS/FAIL
LOCAL_BUILD: NOT_RUN
TARGETED_JVM: NOT_RUN
TARGETED_ANDROID_TEST: NOT_RUN
XIAOMI_14_ULTRA_UI: NOT_RUN
```

- [ ] **Step 4: 创建 Google Drive 本地 AI 只读验收文档**

必须要求本地 AI：

1. checkout `codex/ui-postmerge-audit-fixes` 最新 HEAD；不修改源码；
2. 验证 RED commit 中新增测试确实按预期失败（只需定向命令，不做全量）；
3. 回到最终 HEAD，运行 `compileDebugKotlin`、定向 JVM、定向 AndroidTest、必要回归；
4. 使用真实 Xiaomi 14 Ultra，1440×3200、portrait、zh-CN 验证 Root Nav、Mine B、角色页/详情、无会话浏览详情、Start New、Add Current、失败/重复提交不产生多会话；
5. 截图上传 Google Drive；只返回 PASS/FAIL、失败命令、关键错误、截图链接/文件名和必要 UI dump，不返回大段正常日志。

- [ ] **Step 5: 本地证据返回后执行 `verification-before-completion`**

只有新鲜命令输出与真实 Xiaomi 14 Ultra 证据满足要求后，才能把新 status 改为完成。任何失败先回到 `systematic-debugging`，不得宣布“所有测试通过”。
