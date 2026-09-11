# UI-02 角色页面｜规划师交接与剩余任务

> 日期：2026-09-11
>
> 仓库：`elio-zwd/AI-Skill-Roundtable`
>
> 分支：`codex/ui-02-role-spec`
>
> PR：#59（Draft，禁止自动合并）
>
> UI-02 功能/验收基线 HEAD：`49b9f1a5ad6cedb775ad45f230f4fb5aeb3e85a3`
>
> 本文用途：向总规划 AI 提供 UI-02 最终收口事实。聊天上下文不再作为唯一状态来源。

## 1. 当前阶段结论

UI-02 主体实现、P0 bridge、LIFE_TOOLS 5/5、recent-use 与最小真实模型调用门禁均已有最终证据；**Task 7 仍保持未完成，PR #59 继续 Draft**。

当前未关闭项只有：

1. UI-01 shared Root Navigation（`对话 / 角色 / 资料 / 我的`）仍为外部依赖；
2. `assembleDebugAndroidTest` 仍受仓库既有 AndroidTest baseline compile blocker 影响；
3. 最终 merge-readiness 仍等待总规划 AI 决策。

因此不要再把 Start New / Add Current / recent-use / LIFE_TOOLS 4/5 / `MODEL_CALL: NOT_RUN` 当作当前问题，也不要把 AndroidTest baseline blocker 写成 UI-02 功能失败。

## 2. 已完成的 UI-02 主体与最终门禁

以下不应被重新设计或重新作为 Bug 排查，除非出现新的反证：

- [x] 44 项 `OfficialSkillCatalog` 是角色全集权威。
- [x] Presentation Manifest 只保存发现分类和编辑精选，不复制官方 Skill 身份/能力事实。
- [x] 发现分类：`全部 / 思考方法 / 职业成长 / 研究学习 / 产品创造 / 沟通表达 / 办公事务 / 生活工具`。
- [x] 固定推荐：纳瓦尔 → 理查德·费曼 → 纳西姆·塔勒布。
- [x] 真人角色稳定头像 + `AI 模拟角色`；功能角色稳定非真人身份视觉。
- [x] A 页：推荐 Hero + 两张次卡 + 最近使用（有数据才出现）+ 全部角色。
- [x] B 页：分类说明 + 双列角色目录，窄屏/大字体安全降单列。
- [x] 全屏详情：身份 Hero、能力/输入/输出/工作方式/边界/来源与能力依据、真人声明、固定双动作。
- [x] raw `career_workplace / creator_business / personal_finance` 等内部 token 已从用户 UI 隐藏。
- [x] `OfficialSkillConversationRoleAdapter`、`createNewSessionWithSkillRole`、`addSkillRoleToCurrentSessionAwait` 已实现并获得运行时证据。
- [x] P0 Start New：PASS；真实进入新 session，并确认 `meeting-to-action` participant。
- [x] P0 Add Current：PASS；保持 captured current session，participant 增加成功。
- [x] recent-use：PASS；成功 use action 后可归因刷新。
- [x] R-03 LIFE_TOOLS：PASS；批准 5 项全部存在，第三行滚动可见 `culture-fortune-entertainment`。
- [x] R-05 model call：PASS；`meeting-to-action → 详情 → 开始新对话 → hello → 真实回复`。
- [x] `compileDebugKotlin`：PASS。
- [x] targeted JVM：PASS。
- [x] `testDebugUnitTest`：PASS。
- [x] `assembleDebug`：PASS。

### R-03 最终澄清

第二轮 `生活工具` 4/5 是首屏视口误判，不是 Catalog / Query / projection / Presentation Manifest 的数据缺陷。

批准的 LIFE_TOOLS 始终是：

1. `budget-consumption-coach`
2. `habit-wellbeing-coach`
3. `relationship-dialogue-practice`
4. `chinese-social-etiquette`
5. `culture-fortune-entertainment`

`skill_role_presentation_v1.json` 已包含全部 5 项；双列网格的第 5 项位于第三行、第二轮截图首屏下方。后续实际滚动后已确认第三行可达。因此禁止再通过“补 Manifest”伪造修复。

## 3. 当前未关闭项

### U-01：UI-01 shared Root Navigation

最终共享 `对话 / 角色 / 资料 / 我的` Bottom Navigation 仍属于 UI-01。UI-02 没有私建第二套底栏。

当前动作：等待 UI-01 的共享 Root Navigation 集成状态明确，再由总规划 AI决定是否满足 UI-02 最终 merge-readiness。

### U-02：AndroidTest baseline compile blocker

`assembleDebugAndroidTest` 仍被：

`app/src/androidTest/java/com/elio/jianyu/ui/screens/execution/IssueExecutionStopAvailabilityTest.kt:49`

旧 `IssueExecutionBudgetUi` 构造参数失配阻塞。

该项为 **BLOCKED_BASELINE**，不是 UI-02 功能失败。本轮禁止修改、删除、跳过或弱化该 AndroidTest baseline。

### U-03：Final merge-readiness

PR #59 继续保持 Open + Draft + 未合并。

- 不标记 Ready；
- 不自动 merge；
- 不把 Task 7 勾选完成；
- 等待总规划 AI 对 UI-01 / baseline / PR 集成状态作最终 merge-readiness 决策。

## 4. 第二轮验收历史快照（原文保留，不代表当前状态）

> 以下保留第二轮失败原文，供审计与根因追溯。当前状态以本文第 1～3 节为准。

第二轮本地只读验收基线：

- 验收 HEAD：`7599755022448bf365411a94568d7963cdc48bd4`
- 设备：Android Emulator `1080x2400`，density 420，font scale 1.0
- Drive run：`https://drive.google.com/drive/folders/1dW5L7-grPB00N4l1iMgAKpN-l5W0rcr4`
- 工作区验收前后均 clean

已通过：

- `COMPILE_DEBUG_KOTLIN: PASS`
- `JVM_TARGETED: PASS`
- `LINT_DEBUG: PASS`
- `ASSEMBLE_DEBUG: PASS`
- A 页视觉：PASS
- 角色详情视觉：PASS
- raw `domainTags` 不再直接显示：PASS

已知非 UI-02 基线阻塞：

- `assembleDebugAndroidTest` 被 `app/src/androidTest/java/com/elio/jianyu/ui/screens/execution/IssueExecutionStopAvailabilityTest.kt:49` 的旧 `IssueExecutionBudgetUi` 构造参数阻塞。
- 本任务不得通过修改/删除/跳过该旧测试制造 AndroidTest 绿灯；除非规划师单独创建独立基线修复任务。

### R-01 / P0：开始新对话桥失败

现象：

`meeting-to-action` 详情点击 `开始新对话`，连续等待 10 秒仍稳定停留详情页，没有进入正式 Dialog，也无法证明 participant。

当前代码路径：

```text
SkillRoleDetailRoute
→ App.kt onStartNewConversation
→ RoundtableViewModel.createNewSessionWithSkillRole(skillId)
→ Boolean success
→ success 才 recordSkillUsed + navigateToTopLevel(HOME)
```

因此“停留详情”首先说明 `createNewSessionWithSkillRole()` 很可能返回了 `false`，或 callback/action 链未完成；**不要先假设只是 Navigation BackStack 问题。**

规划师应优先安排一个 systematic-debugging Spike，要求找到动作在哪个门禁返回 false：

1. `resolveExecutableOfficialSkill(skillId)`；
2. `OfficialSkillConversationRoleAdapter.ensureCompatibleCharacter()`；
3. `ChatRepository.createSession()`；
4. `ConversationSessionPreferences.setParticipantIds()`；
5. `selectSession(sessionId)` 后的 `currentSessionId/currentParticipantIds` settle；
6. App 回调是否真正进入 success 分支。

验收用角色优先继续使用：`meeting-to-action`。

完成条件：

- 开始新对话后进入 Dialog；
- UI dump / participant 证明确有 `meeting-to-action`；
- 可发送 1 条简单消息并进入正式 Skill asset 回答链；
- 失败路径有足够诊断证据，但不以吞异常或无条件返回 true 修复。

### R-02 / P0：增加到当前会话桥失败

现象：

`study-planner` 点击 `增加到当前会话` 后仍停留详情页，无法证明当前 Dialog 同时包含原角色和学习规划师。

当前代码路径：

```text
SkillRoleDetailRoute
→ App.kt onAddToCurrentConversation
→ RoundtableViewModel.addSkillRoleToCurrentSessionAwait(skillId)
→ Boolean success
→ success 才 recordSkillUsed + navigateToTopLevel(HOME)
```

建议与 R-01 共用同一个根因调查 Spike：两者可能共享 `resolveExecutableOfficialSkill / adapter / ViewModel publish` 根因，也可能分别失败。不要拆成两轮猜测式修复。

完成条件：

- 先有有效 current session；
- 加入 `study-planner` 后仍是同一 captured session；
- participant 同时包含原角色 + `study-planner`；
- 会话切换竞态仍保持安全；
- success 后才导航和写 recent。

### R-03 / P1：`生活工具` 运行时只显示 4/5

批准的 LIFE_TOOLS 明确为 5 项：

1. `budget-consumption-coach`
2. `habit-wellbeing-coach`
3. `relationship-dialogue-practice`
4. `chinese-social-etiquette`
5. `culture-fortune-entertainment`

`skill_role_presentation_v1.json` 已包含全部 5 项，因此不要直接改 Manifest 伪造修复。

第二轮 UI dump 只看到前四类角色，缺失项应首先按 `culture-fortune-entertainment` 调查。

推荐调查顺序：

```text
OfficialSkillCatalog 实际 runtime 中该项是否存在
→ availability.discoverable / searchable / executable
→ OfficialSkillCatalogQuery.apply()
→ 当前 filters/query 是否意外裁剪
→ projectSkillRoleCatalog.visibleRoles
→ SkillRolePageScreen 分类过滤
→ Compose 实际 composition / scroll 证据
```

完成条件：

- `生活工具` 在无搜索、无筛选时稳定显示 5 项；
- 单元测试增加精确 5 项可见断言；
- 第二轮/第三轮截图和 UI dump 同时证明 5 项。

### R-04 / P1：recent-use 语义尚未获得有效人工证据

本轮 `recent` 中已有 `meeting-to-action` 历史记录，但两条 use action 均未成功完成，因此不能将现有 recent 作为成功语义证据。

不要仅因为列表里已有 recent 就判 PASS。

应在 R-01/R-02 修复后，用干净、可归因的验收流程验证：

- 仅打开详情：不新增 recent；
- 搜索/筛选/收藏：不新增 recent；
- 成功 start-new：新增/刷新 recent；
- 成功 add-current：新增/刷新 recent；
- 失败/unknown：不写 recent。

如需要清理测试状态，应使用测试/验收环境允许的明确方式；不要在生产代码中加入“为了验收清 recent”的临时接口。

### R-05 / P1：模型调用尚未验证

因为 R-01 未进入正式 Dialog，本轮 `MODEL_CALL: NOT_RUN`。

R-01 修复后发送一句最小问题，只验证：

- participant 真正存在；
- 正式 `skillAssetPath` 能加载；
- 无“角色不存在 / asset 不存在 / 空 Prompt”错误；
- 不需要评价回答质量。

### R-06 / P2：AndroidTest 基线阻塞

独立于 UI-02：

`IssueExecutionStopAvailabilityTest.kt:49`

旧测试调用与当前 `IssueExecutionBudgetUi(usedApiCalls: Int, closed: Boolean)` 不一致。

规划师应决定：

- 如果项目后续 UI 页面都需要 Instrumentation，单独开一个 **基线测试维护 Bounded Task** 修复此旧测试；
- 不要混入 UI-02 功能 Bug 修复 commit；
- 修复时按当前 IssueExecution 契约验证真实期望，不能只改参数让它编译。

### R-07 / P2：UI-01 共享导航依赖

当前底栏仍可能是旧：

`首页 / 议题 / Skill / 资料与成果`

UI-02 已明确不负责私建最终 `对话 / 角色 / 资料 / 我的`。

规划师应根据 UI-01 的实际状态决定：

- UI-01 先合入后再做 UI-02 最终集成回归；或
- UI-02 保持 Draft 等待 UI-01。

不要把当前旧底栏作为 UI-02 Bug。

## 5. 第三轮/最终证据对第二轮问题的关闭映射

- [x] R-01 / Start New：关闭，PASS。
- [x] R-02 / Add Current：关闭，PASS。
- [x] R-03 / LIFE_TOOLS 4/5：关闭；根因是首屏视口误判，第三行滚动 5/5 PASS。
- [x] R-04 / recent-use：关闭，PASS。
- [x] R-05 / model call：关闭，真实回复 PASS。
- [ ] R-06 / AndroidTest baseline：仍为独立 BLOCKED_BASELINE，不在 UI-02 修复。
- [ ] R-07 / UI-01 shared navigation：仍为外部依赖。

## 6. 当前 Task 7 门禁

Task 7 仍保持未完成。当前细粒度状态：

- [x] LIFE_TOOLS 5/5 在运行时可见。
- [x] `meeting-to-action` start-new 真正进入 Dialog participant。
- [x] `study-planner` add-current 真正进入同一 session participant。
- [x] recent-use 成功语义已获得可归因人工证据。
- [x] 至少一个正式角色模型调用能走 Skill asset 并返回真实回复。
- [x] A/B/详情关键视觉已有通过证据。
- [x] raw internal tags 仍未泄漏。
- [x] `compileDebugKotlin` / targeted JVM / `testDebugUnitTest` / `assembleDebug` 已有最终通过证据。
- [ ] AndroidTest 状态仍为 `BLOCKED_BASELINE`，待独立处理/总规划裁决。
- [ ] UI-01 shared Root Navigation 集成状态未关闭。
- [ ] final merge-readiness 尚未由总规划 AI 决定。

## 7. 分支 / PR 规则

- 继续使用 `codex/ui-02-role-spec`；不创建新 branch。
- PR #59 继续保持 Draft、Open、未合并。
- 不自动 merge，不标记 Ready，不 close。
- 不修改 `main`。
- 本次收口只允许状态文档与 PR body 变化；生产代码、Compose、Catalog、Manifest、Room、Gradle、测试逻辑和 AndroidTest baseline 均不得修改。

## 8. 下一步

下一步仅等待总规划 AI 的 merge-readiness 决策，并根据 UI-01 shared Root Navigation 与 AndroidTest baseline 的最终处理状态决定 Task 7 是否可以完成。

在此之前：**不要把 PR #59 标记 ready，不要 merge，不要宣称 Task 7 完成。**