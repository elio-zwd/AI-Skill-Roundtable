# UI-01 / UI-02 Post-Merge Audit Fixes 状态

> 日期：2026-09-12
>
> 基线：`main@871057b33d37396f9e560ac18887d3aa0083c6ef`
>
> 唯一修复分支：`codex/ui-postmerge-audit-fixes`
>
> RED 边界：`512c84f98cb681781efd4ad055c5462b1319f96b`
>
> 已完成主体生产代码验收的 GREEN：`88cb5014fdd84a70311f4e09e2ec1fb5493565c3`
>
> ROLE_ACTION test-only 修复待验 SHA：`69ea7f7e4d9ea9b1a6d0597cf028ccead109da95`
>
> 本轮尚未创建 PR。

## 当前目标

重新审查 UI-02 / AndroidTest baseline / UI-01 合入后的 `4bb47dd..871057b`，修复已确认的规格漂移和角色会话动作一致性问题。产品目标设备仍是 Xiaomi 14 Ultra 1440×3200 portrait zh-CN；用户于 2026-09-12 明确批准当前原生 1080×2400 emulator 作为本轮 Post-Merge Audit 的 UI 布局/比例与 connected AndroidTest 验收设备，因为两者同为 9:20。本轮不做其他屏幕适配。

## Superpowers 来源

本轮从用户 Google Drive 项目来源读取 **Superpowers v6.3.0**，使用：

- `systematic-debugging`
- `writing-plans`
- `plan-document-reviewer`
- `test-driven-development`
- `writing-good-tests`
- `executing-plans`
- `receiving-code-review`
- `verification-before-completion`

仓库内旧 Superpowers 快照不作为本轮方法论权威来源。

## 初步审查确认并修复的问题

1. `App.kt` 在进入 Skill 角色详情时调用 `ensureConversationReady()`，导致仅浏览详情也可能静默创建/选择会话。
2. Start New / Add Current 在 session/participant mutation 后 settle 失败时缺少完整补偿。
3. 角色详情动作缺少单次 in-flight 状态，可能重复提交；失败无页面反馈。
4. 进一步 review 发现取消竞态：页面 coroutine 在 mutation 后被取消时会跳过普通失败补偿；旧 `runCatching` 也可能吞 `CancellationException`。
5. Mine B 固定展示 `职业目标 / 可用时间 / 表达偏好`，把设计示例冒充实际 PersonalContext 摘要。
6. Mine 快捷卡冻结文案应为 `模型与 API Key`，合入实现为 `AI 管理`。
7. UI-01/UI-02 历史状态文档仍保留已合并 PR 的 Open/Draft 状态，并把旧 emulator 证据过度解释为目标设备最终验收。

## TDD / RED 证据

`512c84f98cb681781efd4ad055c5462b1319f96b` 是只增加回归测试、尚未写对应 production 修复的 RED 边界。本地只读验收已回切该 SHA 并确认：

- `git grep -n "viewModel.ensureConversationReady()" app/src/main/java/com/elio/jianyu/ui/App.kt` 在 RED 命中 `App.kt:246`。
- 定向 JVM RED 按预期在测试编译阶段失败：`MineUiStateTest.kt:53:22` 无法解析 `toMineSummaryLabels`。
- `:app:compileDebugAndroidTestKotlin` 按预期失败，缺失的正是本轮待实现接口：`personalContextSummaryLabels`、`actionInProgress`、`actionMessage`、`settleTimeoutMs`。

这些错误属于计划允许的“目标接口尚不存在” RED，不是 GREEN 缺陷。

## 已完成的 production 修复

### Task 1 — 浏览详情无会话副作用

- 删除 `App.kt` 中 Skill detail route → `ensureConversationReady()` 的副作用。
- `DialogRoute` 真正进入对话时的会话准备逻辑保持不变。
- Add Current 继续只由真实 `currentSessionId != null` 决定。

### Task 2 — 失败/取消补偿

- `createNewSessionWithSkillRole` / `addSkillRoleToCurrentSessionAwait` 增加可测试的 `settleTimeoutMs`，生产默认仍为 5 秒。
- Start New settle 失败：清理 participant preference、删除本次新建 ChatSession，并在没有更新用户导航时恢复之前选择。
- Add Current settle 失败：恢复原 participant preference 并重新 hydrate 目标 session。
- 补偿运行于 `NonCancellable`；动作取消时先补偿，再继续抛出 `CancellationException`。
- `restoreSessionSelectionAfterRoleAction(expectedCurrentSessionId, restoreSessionId)` 带当前 session 守卫，不覆盖用户后来切换的新会话。

说明：如果底层 `ChatRepository.deleteSession()` 自身发生非取消异常，当前实现会记录错误并返回失败，不能宣称数据库级强原子事务；本轮不新增 Room Schema/事务架构。

### Task 3 — 详情动作状态

- `SkillRoleDetailRoute` 接收 suspend Boolean 动作并持有短生命周期 `remember` in-flight 状态。
- 两个会话动作在 in-flight 时均不可重复点击。
- false/普通异常显示 `操作未完成，请重试`；取消继续传播。
- 成功后才统一导航 HOME；recent-use 仍只在业务动作成功后记录。

### Task 4 — Mine B 真实摘要与冻结文案

- `MineUiState` 增加真实 `personalContextSummaryLabels`。
- 摘要只取 repository 返回的非敏感、非空、去重 title，最多 3 项。
- 0 项/失败时不渲染假摘要 Chip。
- 快捷卡恢复正式文案 `模型与 API Key`。

## 本地验收反馈裁决

首轮报告把 RED 缺失接口误列为 GREEN 待修复项。按 Superpowers v6.3 `receiving-code-review` 核对后已驳回：GREEN 已有这些接口，且 compile/JVM/AndroidTest assemble 均通过。用户随后批准原生 1080×2400 emulator 作为本轮同 9:20 比例的替代验收设备，因此不再因非 Xiaomi 型号或绝对像素不同直接 BLOCK。

## 2026-09-12 续验实际证据

固定主体 GREEN：`88cb5014fdd84a70311f4e09e2ec1fb5493565c3`；本地 worktree clean。

### GREEN 构建

- `:app:compileDebugKotlin`：PASS。
- 指定 `JianyuNavigationArchitectureTest` + `MineUiStateTest`：PASS。
- `:app:assembleDebugAndroidTest`：PASS。
- `:app:installDebug`：PASS；`MainActivity` 可启动。

### connected AndroidTest

- `SkillRoleDetailScreenTest`：PASS，3 tests。
- `MineScreenTest`：PASS，3 tests。
- `AppBottomNavigationTest`：PASS，1 test。
- `AppNavHostTest`：PASS，10 tests。
- `SkillRoleCatalogScreenTest`：PASS，4 tests。
- `RoundtableViewModelSkillRoleActionsTest`：旧 GREEN `88cb501...` 未进入业务断言，runner 初始化失败；该问题后续已定位为测试方法签名缺陷，见下节。

### A–F UI / 真实动作

用户批准的原生 `emulator-5554 / 1080×2400 / 9:20` 上：

- Root Nav：PASS；唯一四项 `对话 / 角色 / 资料 / 我的`，未出现第二套底栏。
- Mine B：PASS；`模型与 API Key` 正确，0 PersonalContext 时没有 `职业目标 / 可用时间 / 表达偏好` 假 Chip，不可用项保持禁用。
- Role UI：PASS；目录、分类、详情和动作区可见。
- Start New：PASS；快速操作后进入一个新会话，截图显示 `当前会话 · 1 个 Skill 角色`，participant 为张雪峰。
- Add Current：PASS；同一会话增加第二角色后截图显示 `当前会话 · 2 个 Skill 角色`，张雪峰与纳瓦尔同时存在。
- 手工快速操作未观察到重复 participant；自动化双提交/补偿最终门禁仍等待 `ROLE_ACTION` 修复后的 3-test 定向结果。
- 本轮截图/UI XML 已上传 Google Drive；网页版 AI 已实际抽查 Mine、角色首页、Start New、Add Current 与补充 Gemini 最终回复截图，未见本轮目标相关明显遮挡/第二套导航/角色数量漂移。

### 真实 Gemini/model-call

用户补充 API Key 后，在不清数据/不卸载 APP 的前提下重新验证：

- 请求真实进入 Gemini Interactions API，API Key 日志已脱敏。
- HTTP 200。
- UI 最终完整显示两位 Skill 角色回复，生成结束后输入区恢复可发送状态。
- `MODEL_CALL` 对本轮用户可见链路判定为 PASS。

同时日志多次出现：

- `BrokerDecision` → `category=SERIALIZATION`
- `MainAnswer` → `category=SERIALIZATION`

这些异常没有阻断最终用户可见回复，但属于真实的新发现。它们与本轮 UI-01/UI-02 Post-Merge 修复不同域，且当前 Plan 明确禁止顺手修改 Gemini transport；记录为 **OPEN_NON_BLOCKING_FOLLOWUP**，后续应单独按 `systematic-debugging` 调查，不能在本轮静默忽略，也不能因此伪称 Gemini 内部链路完全无异常。

## ROLE_ACTION runner 根因与 test-only 修复

旧 GREEN `88cb501...` 的：

`app/src/androidTest/java/com/elio/jianyu/viewmodel/RoundtableViewModelSkillRoleActionsTest.kt`

第三个测试：

`addCurrent_whenSettleFails_restoresOriginalParticipants() = runBlocking { ... }`

最后一条表达式原为：

```kotlin
withTimeout(5_000L) {
    viewModel.currentParticipantIds.first { it == original }
}
```

`first { ... }` 返回 `List<String>`，因此表达式体 `@Test` 方法被 Kotlin 推断为非 `Unit/void` 返回类型。JUnit4 要求测试方法返回 void，导致 `AndroidJUnit4ClassRunner` 在 class validation / 初始化阶段失败，表现为：

`Failed to instantiate test runner class androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner`

这解释了为什么同一 emulator 上其他五组 AndroidJUnit4 测试正常，而该 class 单独初始化失败；`[EmulatorConsole]: Failed to start Emulator console for 5554` 不是足以解释该单类失败的 production 根因。

最小修复已提交：

`69ea7f7e4d9ea9b1a6d0597cf028ccead109da95` — `test: make role action instrumentation test return Unit`

只在该测试末尾增加一行 `Unit`。GitHub compare 已确认相对前一 HEAD 只有该 AndroidTest 文件 `+1/-0`，**没有 production code 修改**。

## 历史状态对账

- PR #59：已合并，merge commit `6540bd8c55d0463248493c8e810298385d1c8e9d`。
- PR #60：已合并，merge commit `e742008b38603ccb1d531e1972adb44e3037971b`。
- PR #61：已合并，merge commit `871057b33d37396f9e560ac18887d3aa0083c6ef`。
- UI-01 / UI-02 历史 status 已改为历史证据，不再写 Open/Draft 当前态。

## 当前验证状态

```text
REMOTE_CODE_REVIEW: PASS_WITH_ONE_TARGETED_TEST_PENDING
RED_STATIC_APP_SIDE_EFFECT: PASS
RED_JVM: EXPECTED_FAIL
RED_ANDROIDTEST_COMPILE: EXPECTED_FAIL
COMPILE_DEBUG_KOTLIN@88cb501: PASS
TARGETED_JVM@88cb501: PASS
ASSEMBLE_DEBUG_ANDROID_TEST@88cb501: PASS
ROLE_DETAIL_ANDROIDTEST@88cb501: PASS (3)
MINE_ANDROIDTEST@88cb501: PASS (3)
ROOT_NAV_ANDROIDTEST@88cb501: PASS (1)
APP_NAVHOST_ANDROIDTEST@88cb501: PASS (10)
ROLE_CATALOG_ANDROIDTEST@88cb501: PASS (4)
ROLE_ACTION_ANDROIDTEST@69ea7f7: NOT_RUN_AFTER_TEST_FIX
APPROVED_EMULATOR_ENV: PASS
ROOT_NAV_UI: PASS
MINE_B_UI: PASS
ROLE_UI: PASS
START_NEW_REAL: PASS
ADD_CURRENT_REAL: PASS
DOUBLE_SUBMIT_GUARD: MANUAL_PASS / AUTOMATED_FINAL_PENDING
MODEL_CALL: PASS_WITH_NON_BLOCKING_SERIALIZATION_FOLLOWUP
MANUAL_CANCEL_COMPENSATION: AUTOMATED_FINAL_PENDING
SERIALIZATION_FOLLOWUP: OPEN_NON_BLOCKING
PR_CREATED: NO
MERGED: NO
```

## 下一步唯一门禁

不再重复 A–F UI，不再重复已经 PASS 的另外五组 AndroidTest，也不修改 production code。由本地 AI只读执行 Google Drive 验收文档第十一节：

1. checkout exact `69ea7f7e4d9ea9b1a6d0597cf028ccead109da95`，确认 worktree clean；
2. 运行 `:app:assembleDebugAndroidTest`；
3. 定向运行 `RoundtableViewModelSkillRoleActionsTest`；
4. 预期实际运行 3 tests：原成功链路、Start New settle 失败补偿、Add Current settle 失败补偿；
5. 若 PASS，则本轮代码/UI 修复可进入最终 `verification-before-completion` 收口；若 FAIL，返回完整 cause chain / test report，不自行修复。

在 `69ea7f7...` 的定向结果返回前，不宣布“全部完成”，不创建 PR，不 merge。
