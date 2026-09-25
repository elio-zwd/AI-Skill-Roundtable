# UI-01 / UI-02 Post-Merge Audit Fixes 状态

> 日期：2026-09-12
>
> 状态：**COMPLETED / VERIFIED**
>
> 基线：`main@871057b33d37396f9e560ac18887d3aa0083c6ef`
>
> 修复分支：`codex/ui-postmerge-audit-fixes`
>
> RED 边界：`512c84f98cb681781efd4ad055c5462b1319f96b`
>
> 主体 production GREEN：`88cb5014fdd84a70311f4e09e2ec1fb5493565c3`
>
> 最终 test-only 验收 SHA：`69ea7f7e4d9ea9b1a6d0597cf028ccead109da95`
>
> PR：未创建
>
> Merge：未执行

## 本轮完成内容

Post-Merge Audit 已确认并修复：

1. 浏览 Skill 角色详情不再通过 `App.kt` 隐式准备/创建会话。
2. Start New / Add Current settle 失败与取消路径增加补偿，且不会覆盖用户后来切换的新 session。
3. 角色详情动作增加单次 in-flight 门禁与失败反馈，取消异常继续传播。
4. Mine B 只展示 repository 返回的真实 PersonalContext 摘要，不再展示固定假 Chip。
5. Mine 快捷卡恢复冻结文案 `模型与 API Key`。
6. UI-01 / UI-02 历史状态完成对账，不再把已合并 PR 写成 Open/Draft，也不把旧 emulator 证据冒充真机证据。
7. ROLE_ACTION runner 初始化失败最终定位为 AndroidTest 方法签名问题，并以 test-only 一行 `Unit` 修复。

## 最终验证证据

### RED

- `RED_STATIC_APP_SIDE_EFFECT: PASS`：RED SHA 的 `App.kt` 确实仍含 `viewModel.ensureConversationReady()`。
- `RED_JVM: EXPECTED_FAIL`：目标新接口尚不存在导致预期 RED。
- `RED_ANDROIDTEST_COMPILE: EXPECTED_FAIL`：目标新参数/接口尚不存在导致预期 RED。

### 构建 / JVM

主体 GREEN `88cb501...`：

- `:app:compileDebugKotlin`：PASS。
- 指定 `JianyuNavigationArchitectureTest` + `MineUiStateTest`：PASS。
- `:app:assembleDebugAndroidTest`：PASS。

最终 test-only SHA `69ea7f7...`：

- `:app:assembleDebugAndroidTest`：PASS。

### connected AndroidTest

- `RoundtableViewModelSkillRoleActionsTest@69ea7f7`：PASS，3 tests，0 failures，0 errors，0 skipped。
- `SkillRoleDetailScreenTest@88cb501`：PASS，3 tests。
- `MineScreenTest@88cb501`：PASS，3 tests。
- `AppBottomNavigationTest@88cb501`：PASS，1 test。
- `AppNavHostTest@88cb501`：PASS，10 tests。
- `SkillRoleCatalogScreenTest@88cb501`：PASS，4 tests。

最终 ROLE_ACTION XML 已由网页版 AI 独立读取确认：`tests=3 failures=0 errors=0 skipped=0`。

## UI / 真实动作验收

用户明确批准原生 `emulator-5554 / 1080×2400 / 9:20` 作为本轮 Post-Merge Audit 的 UI 布局/比例与 connected AndroidTest 验收设备；产品目标设备仍记录为 Xiaomi 14 Ultra 1440×3200，两者同为 9:20。本轮不增加其他尺寸适配要求。

- `APPROVED_EMULATOR_ENV: PASS`
- `ROOT_NAV_UI: PASS` — 唯一四项 `对话 / 角色 / 资料 / 我的`，无第二套底栏。
- `MINE_B_UI: PASS` — `模型与 API Key` 正确；0 PersonalContext 时无固定假 Chip。
- `ROLE_UI: PASS`
- `START_NEW_REAL: PASS` — 一个新会话、1 个 Skill 角色。
- `ADD_CURRENT_REAL: PASS` — 同一会话增加第二角色，未写入错误会话。
- `DOUBLE_SUBMIT_GUARD: PASS` — 手工快速操作未观察到重复；ROLE_ACTION 自动化最终 3/3 PASS。
- `MANUAL_CANCEL_COMPENSATION: AUTOMATED_ONLY / AUTOMATED_ROLE_ACTION_PASS` — 未人为破坏数据库/进程制造失败；settleTimeoutMs=0 自动化补偿用例已通过。

网页版 AI 已实际抽查 Mine、角色首页、Start New、Add Current 和最终 Gemini 回复截图；与本轮目标相关的布局、导航和 participant 数量证据成立。

## Gemini/model-call

用户补充 API Key 后完成真实调用：

- 请求进入 Gemini Interactions API；Key 日志已脱敏。
- HTTP 200。
- 同一会话中两位 Skill 角色最终完整回复可见。
- 生成结束后输入区恢复可发送状态。

因此：

`MODEL_CALL: PASS_WITH_NON_BLOCKING_SERIALIZATION_FOLLOWUP`

同时 logcat 多次出现：

- `BrokerDecision` → `category=SERIALIZATION`
- `MainAnswer` → `category=SERIALIZATION`

这没有阻断最终 UI 回复，但是真实内部异常。本轮 Plan 明确不修改 Gemini transport，因此记录为独立非阻断 follow-up，后续应单独按 `systematic-debugging` 调查。

## ROLE_ACTION runner 根因与修复

旧 GREEN `88cb501...` 的 `RoundtableViewModelSkillRoleActionsTest` 第三个测试使用表达式体：

```kotlin
fun addCurrent_whenSettleFails_restoresOriginalParticipants() = runBlocking { ... }
```

其最后表达式为 `withTimeout { currentParticipantIds.first { ... } }`；`first()` 返回 `List<String>`，导致整个 `@Test` 方法被 Kotlin 推断为非 `Unit/void` 返回类型。JUnit4 在 class validation / runner 初始化阶段因此失败。

最小修复：

`69ea7f7e4d9ea9b1a6d0597cf028ccead109da95`

仅在测试末尾增加 `Unit`。相对上一 HEAD 只有该 AndroidTest 文件 `+1/-0`，无 production code 修改。

最终定向复验：

```text
HEAD: 69ea7f7e4d9ea9b1a6d0597cf028ccead109da95
WORKTREE_CLEAN: PASS
ASSEMBLE_DEBUG_ANDROID_TEST: PASS
ROLE_ACTION_ANDROIDTEST: PASS
ROLE_ACTION_TEST_COUNT: 3
ROLE_ACTION_FAILURES: 0
ROLE_ACTION_ERRORS: 0
ROLE_ACTION_SKIPPED: 0
```

## 测试后环境副作用

最终 ROLE_ACTION connected test 后，本地只读回读发现：

- `adb shell pm path com.elio.jianyu` 无包路径返回；
- 前台 Activity 回到 `com.android.launcher3/.Launcher`；
- 本地 AI 没有执行 `pm clear`、`pm uninstall`、重装或清库。

这不影响 AndroidTest XML 的 3/3 PASS，也不推翻此前 API Key/Gemini UI 验收证据；但说明当前模拟器的 App 安装/数据状态不能继续假定为仍保留。后续若继续使用该模拟器，应先单独确认 UTP/connected test 的测试后清理行为以及是否需要恢复安装。

## 最终矩阵

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
DOUBLE_SUBMIT_GUARD: PASS
MODEL_CALL: PASS_WITH_NON_BLOCKING_SERIALIZATION_FOLLOWUP
MANUAL_CANCEL_COMPENSATION: AUTOMATED_ONLY / AUTOMATED_ROLE_ACTION_PASS
SERIALIZATION_FOLLOWUP: OPEN_NON_BLOCKING
CONNECTED_TEST_APP_CLEANUP_FOLLOWUP: OPEN_NON_BLOCKING
PR_CREATED: NO
MERGED: NO
```

## Evidence

- 最终 ROLE_ACTION Markdown 报告：Google Drive `result-sha69-role-action.md`。
- 最终 ROLE_ACTION XML：Google Drive `sha69-role-action-test-results.xml`。
- 主 UI/A-F 报告：Google Drive `result-final.md`。
- API Key/Gemini 补充报告：Google Drive `supplemental-result-api-key.md`。
- 截图 / UI hierarchy / 精简日志：同一验收结果目录。

## 当前结论

本轮 **UI-01 / UI-02 Post-Merge Audit 修复与验收目标已经闭环完成**。仍有两个明确的非阻断 follow-up：Gemini serialization 日志异常，以及 connected test 后主 App 包被清理的环境行为。它们不阻断本轮结果，也不在本 Plan 中继续顺手修改。

当前分支仍为 `codex/ui-postmerge-audit-fixes`。**未创建 PR，未 merge。** 是否创建 PR/进入合并流程由用户后续明确决定。
