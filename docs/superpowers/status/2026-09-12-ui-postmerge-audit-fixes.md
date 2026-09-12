# UI-01 / UI-02 Post-Merge Audit Fixes 状态

> 日期：2026-09-12
>
> 基线：`main@871057b33d37396f9e560ac18887d3aa0083c6ef`
>
> 唯一修复分支：`codex/ui-postmerge-audit-fixes`
>
> RED 边界：`512c84f98cb681781efd4ad055c5462b1319f96b`
>
> 当前远端 HEAD：以本文件提交后的 branch HEAD 为准；本轮尚未创建 PR。

## 当前目标

重新审查 UI-02 / AndroidTest baseline / UI-01 合入后的 `4bb47dd..871057b`，修复已确认的规格漂移和角色会话动作一致性问题；最终 UI 只以 Xiaomi 14 Ultra 1440×3200 portrait zh-CN 为验收目标。

## Superpowers 来源

本轮从用户 Google Drive 项目来源读取 **Superpowers v6.3.0**，使用：

- `systematic-debugging`
- `writing-plans`
- `plan-document-reviewer`
- `test-driven-development`
- `writing-good-tests`
- `executing-plans`
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

RED 命令尚未在本地执行；最终本地 AI 必须回切此 SHA 补齐 RED 证据。

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
- UI-01 Plan 的 Xiaomi 真机门禁已重新置为未完成。
- 历史 `emulator-5554 / 1080×2400` 只能作为 emulator 证据，不能替代 Xiaomi 14 Ultra 1440×3200 真机最终验收。

## 当前验证状态

```text
REMOTE_CODE_REVIEW: PASS_WITH_LOCAL_VERIFICATION_PENDING
RED_TEST_EXECUTION: NOT_RUN
LOCAL_BUILD: NOT_RUN
TARGETED_JVM: NOT_RUN
TARGETED_ANDROID_TEST: NOT_RUN
ASSEMBLE_DEBUG_ANDROID_TEST: NOT_RUN
XIAOMI_14_ULTRA_UI: NOT_RUN
PR_CREATED: NO
MERGED: NO
```

“REMOTE_CODE_REVIEW PASS”仅表示代码范围/调用链/静态 diff 已审查，不表示可编译或测试通过。

## 下一步门禁

由本地 AI **只读**完成：

1. 在 RED SHA 上跑定向测试，证明新增测试确实因目标行为缺失而失败；
2. 回到最新修复 HEAD，执行 compile + 定向 JVM + 定向 AndroidTest + AndroidTest assemble；
3. 使用真实 Xiaomi 14 Ultra 1440×3200 portrait zh-CN 完成 Root Nav、Mine B、角色浏览/Start New/Add Current/重复提交等验证；
4. 截图/UI dump 上传 Google Drive；只返回压缩 PASS/FAIL 和关键证据；
5. 任一失败都回到 `systematic-debugging`，不得直接宣布完成。

只有收到这些新鲜证据并通过 `verification-before-completion` 后，才能把本轮状态改为完成。
