# UI-01 / UI-02 Post-Merge Audit Fixes Implementation Plan

> 日期：2026-09-12
>
> 状态：**COMPLETED / VERIFIED**
>
> 基线：`main@871057b33d37396f9e560ac18887d3aa0083c6ef`
>
> 分支：`codex/ui-postmerge-audit-fixes`
>
> RED 边界：`512c84f98cb681781efd4ad055c5462b1319f96b`
>
> 主体 production GREEN：`88cb5014fdd84a70311f4e09e2ec1fb5493565c3`
>
> 最终 test-only 验收 SHA：`69ea7f7e4d9ea9b1a6d0597cf028ccead109da95`

本 Plan 已按 Google Drive 项目来源 **Superpowers v6.3.0** 的 `writing-plans`、`plan-document-reviewer-prompt`、`executing-plans`、`systematic-debugging`、`test-driven-development`、`receiving-code-review` 与 `verification-before-completion` 执行并收口。

## Goal

修复 `4bb47dd..871057b` 合入后确认的 UI-01 / UI-02 规格偏移、角色会话动作一致性问题与失真的验收状态；在不扩大到 UI-03、Room Schema、Gradle/依赖或 Gemini transport 的前提下，完成本轮 Post-Merge Audit。

产品目标设备仍记录为 Xiaomi 14 Ultra 1440×3200 portrait zh-CN。用户于 2026-09-12 明确批准原生 `1080×2400` emulator 作为本轮 Post-Merge Audit 的 UI 布局/比例与 connected AndroidTest 验收设备，因为两者同为 9:20。该批准仅适用于本轮验收，不新增其他屏幕适配要求。

## Frozen findings

1. `App.kt` 在 Skill 角色详情调用 `ensureConversationReady()`，浏览行为可能静默创建/选择会话。
2. Start New / Add Current 在 mutation 后 settle 失败时缺少完整补偿。
3. 角色详情动作没有单次 in-flight 门禁，失败无页面反馈。
4. 取消竞态可能跳过普通失败补偿；旧 `runCatching` 可能吞 `CancellationException`。
5. Mine B 固定展示 `职业目标 / 可用时间 / 表达偏好`，把示例冒充真实 PersonalContext。
6. Mine 快捷卡正式文案应为 `模型与 API Key`，合入实现为 `AI 管理`。
7. UI-01 / UI-02 历史 status 仍把已合并 PR 写成 Open/Draft，并过度解释旧 emulator 证据。

## Plan reviewer corrections retained

- 回滚 DB 事实直接从同一 `ChatRepository` / Room Flow 读取，不使用 `viewModel.allSessions.first()` 初始 StateFlow 取证。
- `actionInProgress` 使用短生命周期 `remember`，不使用 `rememberSaveable`。
- 补偿恢复 session 前检查当前 session 仍为本动作预期 session，不覆盖更新的用户导航。
- 不扩大到收藏动作、UI-03、Room Schema、依赖升级或无关重构。

---

## Task 1 — 恢复“浏览角色详情不创建会话”契约

- [x] 写 `JianyuNavigationArchitectureTest` RED，确认旧 `App.kt` 仍含 `viewModel.ensureConversationReady()`。
- [x] 本地 RED 证据确认目标行为在 RED SHA 尚未修复。
- [x] 删除 Skill detail route 的隐式会话准备副作用；真实 `DialogRoute` 初始化保持不变。
- [x] GREEN 定向 JVM 测试通过。

证据：RED `512c84f...`；production GREEN `88cb501...`。

## Task 2 — 修复角色会话动作失败/取消补偿

- [x] 增加 Start New settle 失败删除新 session / 恢复选择的 AndroidTest。
- [x] 增加 Add Current settle 失败恢复原 participant preference 的 AndroidTest。
- [x] 增加 `settleTimeoutMs` 测试 seam，生产默认仍为 5 秒。
- [x] 增加 guarded `restoreSessionSelectionAfterRoleAction(...)`，不覆盖较新的用户导航。
- [x] Start New / Add Current 补偿在 `NonCancellable` 中执行；`CancellationException` 继续传播。
- [x] 最终 `RoundtableViewModelSkillRoleActionsTest` 3 tests 全部通过。

最终测试证据：`69ea7f7e4d9ea9b1a6d0597cf028ccead109da95`，XML 为 `tests=3 failures=0 errors=0 skipped=0`。

## Task 3 — 建立详情动作单次 in-flight 状态与失败反馈

- [x] 增加 Screen RED：in-flight 时两个动作均禁用，失败文案可见。
- [x] Route 使用 `remember(resolvedSkillId)` 管理 `actionInProgress` / `actionMessage`。
- [x] false / 普通异常显示 `操作未完成，请重试`；取消继续传播。
- [x] `App.kt` 只提供 suspend Boolean 业务动作，不持有详情页面状态。
- [x] `SkillRoleDetailScreenTest` 3 tests PASS。
- [x] 手工 Start New / Add Current 快速操作未观察到重复 participant；ROLE_ACTION 自动化补偿测试最终 3/3 PASS。

## Task 4 — 修复 Mine B 假摘要与冻结文案

- [x] `MineUiStateTest` 使用真实 `PersonalContext` fixture 验证非敏感、trim、去重、最多 3 项摘要映射。
- [x] `MineScreenTest` 验证真实 labels、0 项不显示假 Chip、`模型与 API Key` 文案。
- [x] `MineRoute` 使用 repository 实际结果生成 count / labels。
- [x] `MineScreen` 删除固定 `职业目标 / 可用时间 / 表达偏好` 示例 Chip。
- [x] 定向 JVM 与 `MineScreenTest` 3 tests PASS。

## Task 5 — 远端自审、历史状态对账与本地最终验证

- [x] 远端自审确认没有 UI-03、Room Schema、Gradle/依赖或 Gemini transport 的无关 production 修改。
- [x] 历史 UI-01 / UI-02 status 对账：PR #59/#60/#61 已合并，旧 emulator 证据仅作为历史证据。
- [x] 远端阶段在证据不足时保持 NOT_RUN/PENDING，没有提前宣称完成。
- [x] 创建并执行本地 AI 只读验收流程；用户后续批准原生 1080×2400 同 9:20 emulator 作为本轮验收设备。
- [x] GREEN `compileDebugKotlin`、指定 JVM、`assembleDebugAndroidTest` PASS。
- [x] 另外五组 connected AndroidTest PASS：3 + 3 + 1 + 10 + 4 tests。
- [x] Root Nav / Mine B / Role UI / Start New / Add Current 等 A-F 主流程验收 PASS，并上传截图/UI XML。
- [x] API Key 补测：Gemini Interactions API HTTP 200，双角色最终 UI 回复完成，`MODEL_CALL=PASS`。
- [x] 定位 ROLE_ACTION runner 初始化根因：第三个表达式体 `@Test` 被推断为非 `Unit/void`。
- [x] test-only 最小修复：`69ea7f7...` 仅增加一行 `Unit`，无 production code 修改。
- [x] 最终 `assembleDebugAndroidTest` PASS；`ROLE_ACTION` 3 tests / 0 failures / 0 errors / 0 skipped。
- [x] 执行 `verification-before-completion`，本轮目标验收闭环。

## Final verification matrix

```text
REMOTE_CODE_REVIEW: PASS
RED_STATIC_APP_SIDE_EFFECT: PASS
RED_JVM: EXPECTED_FAIL
RED_ANDROIDTEST_COMPILE: EXPECTED_FAIL
COMPILE_DEBUG_KOTLIN@88cb501: PASS
TARGETED_JVM@88cb501: PASS
ASSEMBLE_DEBUG_ANDROID_TEST@69ea7f7: PASS
ROLE_ACTION_ANDROIDTEST@69ea7f7: PASS (3)
ROLE_DETAIL_ANDROIDTEST@88cb501: PASS (3)
MINE_ANDROIDTEST@88cb501: PASS (3)
ROOT_NAV_ANDROIDTEST@88cb501: PASS (1)
APP_NAVHOST_ANDROIDTEST@88cb501: PASS (10)
ROLE_CATALOG_ANDROIDTEST@88cb501: PASS (4)
APPROVED_EMULATOR_ENV: PASS
ROOT_NAV_UI: PASS
MINE_B_UI: PASS
ROLE_UI: PASS
START_NEW_REAL: PASS
ADD_CURRENT_REAL: PASS
DOUBLE_SUBMIT_GUARD: PASS (manual + automated role-action coverage)
MODEL_CALL: PASS_WITH_NON_BLOCKING_SERIALIZATION_FOLLOWUP
MANUAL_CANCEL_COMPENSATION: AUTOMATED_ONLY / AUTOMATED_ROLE_ACTION_PASS
PR_CREATED: NO
MERGED: NO
```

## Non-blocking follow-ups outside this Plan

1. **Gemini serialization logging**：真实 HTTP 200 / UI 最终回复成功时仍观察到 `BrokerDecision` 与 `MainAnswer` 的 `category=SERIALIZATION`。本轮不修改 Gemini transport；后续应作为独立 Bounded Debug 使用 `systematic-debugging` 调查。
2. **connected test 后 App 安装状态**：最终 ROLE_ACTION connected test 后，本地只读回读发现 `adb shell pm path com.elio.jianyu` 无结果、前台回到 Launcher。该现象不影响测试 XML 的 3/3 PASS，但应在需要继续使用当前模拟器/App 数据前单独确认 UTP/connected test 的测试后清理行为。

## Delivery state

本 Plan 的开发与验收任务全部完成。当前仍在 `codex/ui-postmerge-audit-fixes` 分支；**没有创建 PR，没有 merge**。是否创建 PR/进入合并流程由用户另行决定。
