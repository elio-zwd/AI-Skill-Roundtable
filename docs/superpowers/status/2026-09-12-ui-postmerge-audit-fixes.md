# UI-01 / UI-02 Post-Merge Audit Fixes 状态

> 日期：2026-09-12
>
> 基线：`main@871057b33d37396f9e560ac18887d3aa0083c6ef`
>
> 唯一修复分支：`codex/ui-postmerge-audit-fixes`
>
> RED 边界：`512c84f98cb681781efd4ad055c5462b1319f96b`
>
> 本地验收所验证的 GREEN：`88cb5014fdd84a70311f4e09e2ec1fb5493565c3`
>
> 本轮尚未创建 PR。

## 当前目标

重新审查 UI-02 / AndroidTest baseline / UI-01 合入后的 `4bb47dd..871057b`，修复已确认的规格漂移和角色会话动作一致性问题。产品目标设备仍是 Xiaomi 14 Ultra 1440×3200 portrait zh-CN；但用户于 2026-09-12 明确批准当前 1080×2400 emulator 作为本轮 UI 布局/比例与 connected AndroidTest 的验收设备，因为两者同为 9:20 纵横比。本轮不做其他屏幕适配。

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

## 初步审查确认的问题

1. `App.kt` 在进入 Skill 角色详情时调用 `ensureConversationReady()`，导致仅浏览详情也可能静默创建/选择会话。
2. Start New / Add Current 在 session/participant mutation 后 settle 失败时缺少完整补偿。
3. 角色详情动作缺少单次 in-flight 状态，可能重复提交；失败无页面反馈。
4. 进一步 review 发现取消竞态：页面 coroutine 在 mutation 后被取消时会跳过普通失败补偿；旧 `runCatching` 也可能吞 `CancellationException`。
5. Mine B 固定展示 `职业目标 / 可用时间 / 表达偏好`，把设计示例冒充实际 PersonalContext 摘要。
6. Mine 快捷卡冻结文案应为 `模型与 API Key`，合入实现为 `AI 管理`。
7. UI-01/UI-02 历史状态文档仍保留已合并 PR 的 Open/Draft 状态，并把 emulator 证据过度解释为目标 Xiaomi 真机完成。

## TDD / RED 边界

`512c84f98cb681781efd4ad055c5462b1319f96b` 只增加针对上述问题的回归测试，尚未写对应 production 修复。

新增/更新的 RED 覆盖：

- `JianyuNavigationArchitectureTest`：App 导航壳不得调用 `viewModel.ensureConversationReady()`。
- `RoundtableViewModelSkillRoleActionsTest`：Start New settle 失败清理新 session；Add Current settle 失败恢复原 participant。
- `SkillRoleDetailScreenTest`：in-flight 时两动作均禁用，失败文案可见。
- `MineUiStateTest`：PersonalContext 摘要只取真实、非敏感、trim/去重 title。
- `MineScreenTest`：不显示三项静态假摘要；显示 `模型与 API Key`。

### 本地 RED 证据（2026-09-12）

本地只读验收已经回切 RED SHA 并提供以下新鲜证据：

- `git grep -n "viewModel.ensureConversationReady()" app/src/main/java/com/elio/jianyu/ui/App.kt` 在 RED 命中 `App.kt:246`。
- 定向 JVM RED 按预期在测试编译阶段失败：`MineUiStateTest.kt:53:22` 无法解析 `toMineSummaryLabels`。
- `:app:compileDebugAndroidTestKotlin` 按预期失败，缺失的正是本轮待实现接口：`personalContextSummaryLabels`、`actionInProgress`、`actionMessage`、`settleTimeoutMs`。

这些错误属于计划允许的“目标接口尚不存在” RED，不是 GREEN 缺陷。

## 远端已完成的代码修改

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

## 本地验收反馈裁决（2026-09-12）

本地报告给出了四项行动建议。按 Superpowers v6.3 `receiving-code-review` 先核对 GREEN 代码与验收证据后，裁决如下：

1. **“修复 `toMineSummaryLabels` 接口不一致”——不执行。** 该 unresolved reference 只发生在 RED `512c84f...`，用于证明实现前接口不存在。GREEN `88cb501...` 已定义 `List<PersonalContext>.toMineSummaryLabels()`，且定向 JVM 测试在 GREEN 通过。
2. **“修复 `personalContextSummaryLabels` / `actionInProgress` / `actionMessage` / `settleTimeoutMs` 参数不一致”——不执行。** 这些编译错误同样只属于 RED。GREEN 已具有全部对应 production 接口，并且 `:app:compileDebugKotlin` 与 `:app:assembleDebugAndroidTest` 均通过。
3. **设备阻塞裁决已被用户后续指令覆盖。** 首轮报告因当前设备为 `1080×2400 emulator` 而阻塞；用户随后明确确认 1080×2400 与 1440×3200 比例一致，可作为本轮 UI/测试验收设备。两者均为 9:20，因此本轮不再因绝对像素或非真机型号直接 BLOCK。
4. **不需要新的 production GREEN SHA。** 行动项 1/2 不构成 GREEN 缺陷；设备门禁变化只是验收策略变化。生产/测试复验仍基于 `88cb5014fdd84a70311f4e09e2ec1fb5493565c3`。本状态文档更新产生 docs-only branch HEAD，不改变待测代码。

## 设备验收口径修订（用户 2026-09-12 明确批准）

- 产品目标设备仍记录为 Xiaomi 14 Ultra 1440×3200。
- 本轮 Post-Merge Audit 的 UI 布局/比例验收允许使用当前 `1080×2400` emulator；1080×2400 与 1440×3200 同为 9:20。
- 不要求通过 `adb wm size` 伪造 1440×3200；保持 emulator 原生 1080×2400 即可。
- connected AndroidTest 可直接在该 emulator 上执行。
- 该批准仅解除本轮“设备必须是真实 Xiaomi 14 Ultra / 绝对像素必须 1440×3200”的门禁，不代表要新增其他尺寸适配，也不把旧 APK/旧截图当作当前 GREEN 的新鲜证据。
- Android 版本、density 与真实机不同不再自动判本轮 FAIL；若它们导致具体功能/Compose 测试失败，则按实际失败调查。

## 远端审查范围

相对 `main@871057b...`，生产代码只触碰：

- `ui/App.kt`
- `ui/screens/mine/MineRoute.kt`
- `ui/screens/mine/MineScreen.kt`
- `ui/screens/mine/MineUiState.kt`
- `ui/screens/skills/SkillRoleDetailRoute.kt`
- `ui/screens/skills/SkillRoleDetailScreen.kt`
- `viewmodel/RoundtableViewModel.kt`
- `viewmodel/RoundtableViewModelSkillRoleActions.kt`

对应 5 个测试文件与本轮 Plan/Status 同步更新。

没有修改 UI-03、Room Schema、Gradle/依赖、Gemini transport 或历史 `check-app-identity.ps1` baseline。

## 历史状态对账

- PR #59：已合并，merge commit `6540bd8c55d0463248493c8e810298385d1c8e9d`。
- PR #60：已合并，merge commit `e742008b38603ccb1d531e1972adb44e3037971b`。
- PR #61：已合并，merge commit `871057b33d37396f9e560ac18887d3aa0083c6ef`。
- UI-01 / UI-02 历史 status 已改为历史证据，不再写 Open/Draft 当前态。
- UI-01 Plan 先前重新打开的 Xiaomi 真机门禁，由本状态中的 2026-09-12 用户新指令覆盖：本轮可用同 9:20 的 1080×2400 emulator 完成 Post-Merge Audit 验收。
- 历史 emulator 证据本身仍只是历史证据；必须对 GREEN `88cb501...` 重新执行 connected tests 和 UI 流程，不能直接沿用旧结果。

## 当前验证状态

```text
REMOTE_CODE_REVIEW: PASS_WITH_RUNTIME_VERIFICATION_PENDING
RED_STATIC_APP_SIDE_EFFECT: PASS
RED_JVM: EXPECTED_FAIL
RED_ANDROIDTEST_COMPILE: EXPECTED_FAIL
COMPILE_DEBUG_KOTLIN: PASS
TARGETED_JVM: PASS
ASSEMBLE_DEBUG_ANDROID_TEST: PASS
TARGETED_ANDROID_TEST: NOT_RUN_READY_ON_APPROVED_EMULATOR
APP_UI_ACCEPTANCE: NOT_RUN_READY_ON_APPROVED_EMULATOR
MODEL_CALL: NOT_RUN_ENV_DEPENDENT
MANUAL_CANCEL_COMPENSATION: NOT_RUN/AUTOMATED_TEST_PENDING
PR_CREATED: NO
MERGED: NO
```

证据来源：2026-09-12 本地只读验收报告 `result.md`。其中 GREEN `compileDebugKotlin`、两个指定 JVM 测试类、`assembleDebugAndroidTest` 均报告 `BUILD SUCCESSFUL`；六组 `connectedDebugAndroidTest` 和 UI 流程尚未执行，因此不得提前写成 PASS。

## 下一步门禁

当前不做新的生产代码修改。由本地 AI 在用户已批准的当前 1080×2400 emulator 上继续只读验收：

1. checkout/detach 到生产/测试基线 `88cb5014fdd84a70311f4e09e2ec1fb5493565c3`，确认 worktree clean；不需要再次执行 RED。
2. 为保证新鲜证据，重跑 `:app:compileDebugKotlin`、两个指定 JVM 测试类、`:app:assembleDebugAndroidTest`。
3. 在当前 emulator 上执行六组指定 `connectedDebugAndroidTest`。
4. 安装该 GREEN 对应 APK，完成 Root Nav、Mine B、角色 Catalog/详情、Start New、Add Current、快速重复点击等 A-F 手工流程。
5. 真实 Gemini/model-call 仅在网络/API Key 环境可用时验收；环境不可用则单独记录 `BLOCKED_ENV`，不得等同代码失败。
6. 截图/UI hierarchy XML 上传 Google Drive，并返回压缩 PASS/FAIL 与关键失败证据。
7. 收到上述证据后再次执行 `verification-before-completion`。在此之前，本轮不能标记“全部完成”，也不创建 PR。
