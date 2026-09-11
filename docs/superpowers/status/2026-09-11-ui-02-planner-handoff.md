# UI-02 角色页面｜规划师交接与剩余任务

> 日期：2026-09-11
>
> 仓库：`elio-zwd/AI-Skill-Roundtable`
>
> 分支：`codex/ui-02-role-spec`
>
> PR：#59（Draft，禁止自动合并）
>
> 本文用途：结束当前 UI-02 开发执行对话，将剩余工作交给新的“规划师 AI”拆分、排序和安排。聊天上下文不再作为唯一状态来源。

## 1. 当前阶段结论

UI-02 已完成主要代码实现和两轮视觉校准，但 **Task 7 尚未完成，PR #59 不可宣称完成**。

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

## 2. 已完成的 UI-02 主体

以下不应被重新设计，除非新的证据表明存在 Bug：

- 44 项 `OfficialSkillCatalog` 是角色全集权威。
- Presentation Manifest 只保存发现分类和编辑精选，不复制官方 Skill 身份/能力事实。
- 发现分类：`全部 / 思考方法 / 职业成长 / 研究学习 / 产品创造 / 沟通表达 / 办公事务 / 生活工具`。
- 固定推荐：纳瓦尔 → 理查德·费曼 → 纳西姆·塔勒布。
- 真人角色稳定头像 + `AI 模拟角色`；功能角色稳定非真人身份视觉。
- A 页：推荐 Hero + 两张次卡 + 最近使用（有数据才出现）+ 全部角色。
- B 页：分类说明 + 双列角色目录，窄屏/大字体安全降单列。
- 全屏详情：身份 Hero、能力/输入/输出/工作方式/边界/来源与能力依据、真人声明、固定双动作。
- raw `career_workplace / creator_business / personal_finance` 等内部 token 已从用户 UI 隐藏。
- `OfficialSkillConversationRoleAdapter`、`createNewSessionWithSkillRole`、`addSkillRoleToCurrentSessionAwait` 已实现。
- recent-use 代码意图是“真实 use action 返回 success 后才写”。
- UI-01 最终共享 `对话 / 角色 / 资料 / 我的` 导航仍是外部依赖；UI-02 不私建第二套底栏。

## 3. 第二轮验收暴露的真实剩余问题

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

## 4. 建议规划师的任务编排

建议按以下顺序，不要继续优先做视觉微调：

```text
P0 Spike：统一调查 start-new + add-current bridge 根因
  ↓
Bounded Fix：按已确认根因做最小修复 + 回归测试
  ↓
P1 Bounded：修复 LIFE_TOOLS 4/5
  ↓
本地 AI：验证 bridge + recent + model call + LIFE_TOOLS 5/5
  ↓
Drive：建立新的 visual run，重新上传关键截图/UI dump
  ↓
GPT：读取 Drive 做最后视觉审查（仅有证据再调视觉）
  ↓
可选独立 Bounded：修复 AndroidTest 基线阻塞
  ↓
等待/集成 UI-01
  ↓
最终 regression / PR review / Task 7 完成
```

## 5. 下一轮开发必须优先读取

开始任何修复前读取：

- `AGENTS.md`
- `app/src/main/java/com/elio/jianyu/ui/AGENTS.md`
- `docs/superpowers/plans/2026-09-10-ui-02-role-page.md`
- `docs/superpowers/status/2026-09-10-ui-02-role-page.md`
- 本文件
- `docs/product/重构/UI界面/实施规格/UI-02/角色主页面-双布局-实施规格.md`
- `docs/product/重构/UI界面/实施规格/UI-02/2026-09-11-Drive视觉校准审查.md`
- `docs/testing/ui-02-role-page-local-visual-round2.md`

根因调查重点代码：

- `app/src/main/java/com/elio/jianyu/ui/App.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailRoute.kt`
- `app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModelSkillRoleActions.kt`
- `app/src/main/java/com/elio/jianyu/skill/role/OfficialSkillConversationRoleAdapter.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleCatalogRoute.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleCatalogUiState.kt`
- `app/src/main/assets/skill_role_presentation_v1.json`

## 6. 已有测试/验证事实

第二轮本地结果：

```text
COMPILE_DEBUG_KOTLIN: PASS
JVM_TARGETED: PASS
LINT_DEBUG: PASS
ASSEMBLE_DEBUG: PASS
ASSEMBLE_DEBUG_ANDROID_TEST: BLOCKED_BASELINE
VISUAL_A_PAGE: PASS
VISUAL_B_PAGE: FAIL
VISUAL_DETAIL: PASS
RAW_DOMAIN_TAGS_HIDDEN: PASS
MANUAL_START_NEW_BRIDGE: FAIL
MANUAL_ADD_CURRENT_BRIDGE: FAIL
RECENT_USE_SEMANTICS: FAIL
MODEL_CALL: NOT_RUN
```

不要重复执行已证明无关的正常日志采集；只有代码变化后才重跑对应验证。

## 7. Drive 视觉证据

第二轮 runtime 根目录：

`https://drive.google.com/drive/folders/1dW5L7-grPB00N4l1iMgAKpN-l5W0rcr4`

其中已有：

- `00-environment`
- `01-role-catalog`
- `02-role-category`
- `03-role-detail`
- `04-conversation-bridge`
- `05-recent-empty`

本轮共 12 张 runtime screenshot，另有 UI dump / logcat 证据。

未来每轮修复继续新建独立 Drive run，禁止覆盖历史证据。

## 8. 分支 / PR 规则

- 当前 PR #59 保持 Draft。
- 不自动 merge。
- 如果规划师决定继续同一个 UI-02 修复，可继续当前 `codex/ui-02-role-spec`；不要为了形式重复开重叠 PR。
- 若单独修 AndroidTest 基线或 UI-01，使用独立 branch/PR，避免污染 UI-02 功能修复。
- 任何“完成”声明必须引用实际测试/截图/CI 证据。

## 9. UI-02 最终完成门禁

只有以下全部满足，Task 7 才可勾选完成：

- [ ] LIFE_TOOLS 5/5 在运行时可见。
- [ ] `meeting-to-action` start-new 真正进入 Dialog participant。
- [ ] `study-planner` add-current 真正进入同一 session participant。
- [ ] recent-use 成功/失败语义人工可归因验证通过。
- [ ] 至少一个 21～44 角色实际模型调用能走正式 Skill asset。
- [ ] A/B/详情关键视觉复验通过。
- [ ] raw internal tags 仍未泄漏。
- [ ] JVM / compile / lint / assemble 回归通过。
- [ ] AndroidTest 状态有明确结论（PASS 或已单独记录并处理基线阻塞）。
- [ ] UI-01 共享导航依赖有明确集成状态。
- [ ] GPT 自审 + 本地 AI 只读验收完成。
- [ ] Plan / Status / PR body 与事实同步。

在此之前：**不要把 PR #59 标记 ready，不要 merge，不要宣称 UI-02 完成。**
