# PR09-06：首页与推荐实施计划

## 1. 基线与启动核验

- 实际开发基线：`main@54f458379359275973b3fff966fb512b1d5f6517`。
- 开发分支：`feat/pr-09-06-home-recommendation`。
- Room：v9，不修改 Database、DAO、Migration 或 Schema JSON。
- 以下提交均已核验为启动基线祖先：
  - PR09-09：`50a0fb401ebbdbc9ce26aba9a7dd40916b6ba610`；
  - 低 Token 工具：`b36f8222539d8f4d99de0321caac5f9ef3ec7de1`；
  - PR-A：`13cc1e6b8fef6f9c5882ec3ff8c5548026ca933a`；
  - PR-B：`54f458379359275973b3fff966fb512b1d5f6517`。
- 启动时没有开放 PR，不存在已知首页、推荐、上下文确认、根导航或执行接线冲突。
- 本计划首次写入时因连接器参数错误短暂进入 `main`，随后通过普通 revert `e66b0ef8d16f754efbf337debe72afd950d6ae9e` 删除；未强推、未改写历史。此文件当前由开发分支正式持有。

## 2. Superpowers 工作流

Superpowers 插件接口未调用；本任务读取仓库内保存的 Superpowers 6.2.0 Skill 文件，并按项目规则执行等价人工流程：

- `brainstorming`：比较推荐架构并冻结所有权；
- `writing-plans`：先形成文件级方案；
- `test-driven-development`：测试代码先于对应生产实现；
- `systematic-debugging`：仅根据真实 CI 或本地日志修复；
- `verification-before-completion`：区分静态检查、CI、设备测试和未验证项；
- `requesting-code-review`、`finishing-a-development-branch`：整理 Draft PR，不自动合并。

不使用 git worktree、并行子智能体、自动合并或自动删除分支。

## 3. 当前调用链与所有权

```text
MainActivity
→ MainAppContent
→ 唯一 JianyuAppRuntime
   ├─ JianyuRepository
   ├─ OfficialSkillCatalogRuntimeResult
   └─ ExecutionRunCoordinator
→ AppNavHost
→ HomeRoute
```

首页只消费公共接口：

- `JianyuRepository.saveIssue()`；
- `listMaterials()`、`listPersonalContexts()`；
- `prepareExecutionContext()`；
- `OfficialSkillCatalog`；
- `ExecutionRunCoordinator.start()`；
- `ContextConfirmationUiState`、`ContextCandidateUi`、`ContextConfirmationDialog`；
- `navigateToIssue(issueId, stageId)`。

不得创建第二个 Repository、Coordinator、Context Builder、Catalog Runtime、API Key 池、网络客户端或执行状态机。

## 4. 推荐方案比较

### A. 纯本地确定性推荐

依据官方 Catalog 的价值方向、领域、场景、输入输出、风险、联网、资料要求和执行资格生成候选与解释。优点是离线、无 Key、可确定性测试、设备自动化不依赖生产网络；限制是复杂自然语言理解能力有限。

### B. 模型推荐

通过可注入 Gateway 使用现有 BYOK 能力，再由 Catalog 校验。语义能力更强，但引入 Key、离线、成本、时延、不稳定输出和测试网络依赖。

### C. 混合推荐

本地召回、模型排序解释、Catalog 再校验。质量上限较高，但本 PR 会同时拥有两条失败路径和更多恢复语义。

### 正式结论

选择 **A：纯本地确定性推荐**。唯一生产实现为 `LocalCatalogHomeRecommendationGateway`，来源明确标记 `LOCAL_CATALOG`，不伪装为模型分析。推荐阶段不调用 Gemini、不读取 `.env`、不创建 Run、Pending Message、Usage Snapshot 或预算消耗。

## 5. 推荐领域模型

位于 `com.elio.jianyu.home`：

- `HomeQuestionDraft`；
- `ValueDirection`；
- `RecommendationMode`；
- `RecommendationSource`；
- `RecommendationRisk`；
- `HomeRecommendationRequest`；
- `RecommendedSkill`；
- `HomeRecommendation`；
- `HomeContextSelectionSnapshot`；
- `HomeFinalConfirmation`；
- `HomeWorkflowIds`；
- `HomeWorkflowState`；
- `HomeStartResult`。

要求：正式稳定 Skill ID、非空理由和职责、唯一成员、稳定 position、可执行资格校验、风险/时效/联网/资料/预期输出披露。人物视角必须保留非本人边界，高风险标签不参与自动降权。

## 6. 首页状态机

稳定步骤：

```text
EDITING_QUESTION
RECOMMENDATION_LOADING
RECOMMENDATION_FAILURE
RECOMMENDATION_READY
NO_SUITABLE_SKILL
NO_EXECUTABLE_SKILL
EDITING_RECOMMENDATION
CONTEXT_CONFIRMING
CONTEXT_NEEDS_CORRECTION
FINAL_REVIEW
SAVING_ISSUE
SAVED_NOT_STARTED
STARTING_EXECUTION
START_FAILURE
NAVIGATING_TO_ISSUE
RESTORED_DRAFT
```

稳定状态保存问题、方向、推荐、阵容、上下文草稿、确认状态、操作状态和稳定 ID。一次性导航使用 `HomeNavigationEvent`，不把 Snackbar 或导航当作执行事实源。

## 7. 问题优先与价值方向

- 用户先输入自然语言问题；
- 不强迫预先选择 Skill、模式、方向、资料或背景；
- `现实支持` 与 `思维拓展` 可跳过、单选或组合；
- 双方向仍只使用同一个 Issue、初始 Stage 和 Run；
- 修改问题使旧推荐、上下文确认和最终确认失效，但不修改已经保存的 Issue。

## 8. 推荐流程

```text
问题非空
→ 分配 request token
→ 本地 Catalog 候选排序
→ Catalog ID/唯一性/执行资格复核
→ 展示理由与边界
→ 等待用户确认
```

同一时刻只允许一个请求；双击复用进行中的 token；新问题清除旧 token；迟到结果仅在 token 仍匹配时应用。推荐失败保留问题和方向，可重试、修改问题、浏览 Skill 或仅保存议题。

## 9. 推荐调整

用户可：

- 选择或移除 Skill；
- 切换单/多 Skill；
- 调整顺序；
- 修改本次默认职责；
- 浏览官方 Skill。

任何调整都使推荐确认、上下文确认和最终确认失效。未知 ID、重复成员、空职责、空理由和不可执行启动成员被拒绝。默认职责不覆盖 System Prompt。

## 10. 上下文确认

复用 PR09-09 的 `ContextConfirmationDialog`，不创建第二套 Dialog、Builder 或 Usage Writer。

- 当前 Issue/Stage 资料与活跃个人背景作为候选；
- 所有候选默认 `selected=false`、`networkAllowed=false`、`sensitiveConfirmed=false`；
- 显示字符占用，硬边界 24,000；
- 不静默截断、不自动摘要；
- 敏感来源需要二次确认；
- 取消不创建 Run、不写 Usage、不消费预算；
- `baseContextCharacters` 包含问题、职责和固定模板预留。

## 11. 最终确认

展示：

- 问题摘要；
- 价值方向；
- 单/多 Skill 模式；
- Skill 阵容、职责和理由；
- 风险、时效、联网与资料要求；
- 已确认资料和个人背景；
- 预期输出；
- 将创建一个 Issue/初始 Stage 并开始模型调用的明确提示。

最终确认前保持零 Run、零 Pending、零预算、零模型回答调用。

## 12. 仅保存议题

```text
输入问题
→ 仅保存议题
→ saveIssue(稳定 issueId/stageId)
→ 导航到现有议题工作区
```

`HomeStartCoordinator.saveOnly()` 只调用 `saveIssue()`。推荐 Gateway、Coordinator、网络、Participant、Run、Usage Snapshot 和 Pending Message 调用次数均为零。失败保留草稿、方向和稳定 ID，重试复用同一命令。

## 13. 创建 Issue 与启动执行

```text
1. 校验最终推荐与可执行成员
2. saveIssue
3. prepareExecutionContext
4. 构造 ExecutionStartCommand
5. 同时传递 contributions 与 contextUsage
6. 调用唯一 ExecutionRunCoordinator.start()
7. 导航到 IssueExecutionRoute
```

资料归属需要真实 Issue/Stage，因此先保存再准备上下文。步骤 3 或之后失败时保留合法 Issue/Stage，进入 `SavedNotStarted`；不删除后重建。Runtime 原子失败、预算和网络安全继续由既有执行层负责。

## 14. 稳定 ID 与幂等

工作流首次创建：

- `workflowId`；
- `issueId`；
- `stageId`；
- `runId`；
- `saveIssueIdempotencyKey`；
- `executionIdempotencyKey`。

生产使用 UUID，测试注入固定值。重组、推荐重试、双击和 Activity 重建不重新生成；只有明确新问题工作流才生成新 ID。正文、姓名和资料内容不得进入 ID 或自动化标签。

## 15. 草稿恢复

`SavedStateHandle` 保存序列化 `HomeWorkflowState`。恢复问题、方向、推荐、调整阵容、上下文草稿、当前步骤、稳定 ID 与确认状态。恢复后统一进入 `RESTORED_DRAFT`，不自动推荐、保存、准备上下文或启动，并将推荐/上下文/最终确认保持为未确认。

## 16. UI 与导航

- 一个根 NavHost；
- 四个一级目的地不变；
- 首页仍为默认目的地；
- 设置入口不变；
- 成功后只把稳定 `issueId/stageId` 传给现有导航；
- `Scaffold` 根节点继续启用 `testTagsAsResourceId = true`；
- 页面使用可滚动 Material 3 结构，兼顾窄屏、长文本、多 Skill、键盘和大字体；
- 选中状态不只依赖颜色；
- 不实施 PR09-16 最终视觉。

## 17. 正式自动化标签

中央 `JianyuAutomationTags.Home` 冻结：

```text
home_question_input
home_question_clear_button
home_direction_reality_support
home_direction_thinking_expansion
home_save_issue_only_button
home_recommendation_request_button
home_recommendation_result
home_recommendation_loading
home_recommendation_failure
home_recommendation_confirm_button
home_context_confirmation_button
home_context_confirmed_summary
home_final_review
home_start_issue_button
home_draft_recovery
```

动态标签：

```text
home_recommendation_skill_<stableSkillId>
home_example_question_<stableExampleId>
```

动态部分统一调用 `normalizedStableId()`。删除 `home_question_placeholder`，保留既有冻结字符串。

## 18. 设备语义场景

真实示例问题提供稳定标签，用于：

```text
launch home_screen
→ assert home_question_input
→ tap home_example_question_<id>
→ expect home_question_clear_button
→ tap home_recommendation_request_button
→ expect home_recommendation_result
→ confirm recommendation
→ expect context_confirmation_dialog
→ tap context_confirmation_confirm
→ expect home_context_confirmed_summary / home_final_review
```

外部设备验收停在最终确认，不调用真实 Gemini；完整启动由 Instrumentation Fake 验证。禁止固定坐标、中文文案主选择器、OCR、卸载 App 或清除数据。

## 19. TDD 与测试

JVM：

- `HomeWorkflowTest`：初始、问题、方向、迟到结果、失败保留、确认失效、恢复；
- `HomeRecommendationPolicyTest`：单/多 Skill、理由/职责、去重、未知、不可执行、高风险不降权、无候选；
- `HomeStartCoordinatorTest`：仅保存零执行、稳定 ID、上下文失败、contributions + usage、SavedNotStarted、显式确认；
- 标签与架构测试：中央契约、占位删除、Scaffold 导出和动态 ID 安全。

Compose Instrumentation：

- 真实输入框；
- 双方向节点；
- 示例问题；
- 推荐结果与 Skill 标签；
- 上下文确认摘要；
- 最终确认与启动标签。

所有 UI 测试使用纯 UiState 或 Fake，不访问生产网络。

## 20. 修改文件

允许并实际涉及：

```text
app/src/main/java/com/elio/jianyu/home/**
app/src/main/java/com/elio/jianyu/ui/screens/home/**
app/src/main/java/com/elio/jianyu/ui/App.kt
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
app/src/test/java/com/elio/jianyu/home/**
app/src/test/java/com/elio/jianyu/ui/automation/**
app/src/androidTest/java/com/elio/jianyu/ui/screens/home/**
docs/planning/pr-09-06-*.md
docs/testing/pr-09-06-*.md
```

## 21. 禁止文件

不修改：

```text
RoundtableDatabase.kt
JianyuRepositoryDao.kt
MaterialContextMigration.kt
ResourceLifecycleEntities.kt
ExecutionStateMachine.kt
ExecutionBudgetPolicy.kt
ExecutionRuntimeRepositoryComponent.kt
tools/device/**
tools/local-verification/**
app/schemas/**/9.json
```

## 22. Commit 边界

- 文档计划；
- 失败测试；
- 首页领域与推荐；
- 上下文和启动编排；
- Compose 页面与导航装配；
- 自动化标签和 UI 测试；
- 接口交接与本地验收文档。

不添加 `Co-Authored-By`，不强推，不夹带无关重构。

## 23. CI 与远端验证

读取并记录：

- `git diff --check`；
- Secret scan；
- Kotlin Debug 编译；
- 全量 JVM；
- Lint；
- Debug/Release 构建；
- Room Schema 当前性；
- `:app:assembleDebugAndroidTest`；
- GitHub Actions、Review Thread 与当前 Head。

Room 必须保持 v9，`9.json` 不漂移。没有命令或 CI 证据时只写“尚未验证”。

## 24. 本地只读验收

使用 `tools/local-verification/Invoke-LocalVerification.ps1` 包装 compile、JVM、Lint、Debug、Release、AndroidTest APK 和 connected Instrumentation。原始日志、JUnit XML、截图、XML 与 JSON 保存到仓库外 `$env:TEMP`，成功只回传摘要、证据路径和 SHA-256。

使用 `tools/device/cli.py` 执行 doctor、launch、assert、find、tap、wait，关键首页路径使用 `--by tag`。不卸载、不清数据、不修改文件、不提交、不推送、不合并。

## 25. 风险与防护

- 迟到推荐覆盖新问题：request token；
- 双击重复 Issue/Run：操作锁 + 稳定 ID + 上游幂等；
- 双方向双主线：单一 `HomeWorkflowIds`；
- 推荐失败清空草稿：错误只改变推荐子状态；
- 仅保存误调用模型：独立 `saveOnly()`；
- 背景默认勾选：候选默认 false；
- 上下文确认跳过：最终启动要求 `confirmed=true`；
- Runtime 前网络调用：仅调用既有 Coordinator；
- Issue 已保存却显示全失败：`SavedNotStarted`；
- 重建自动开始：恢复只还原稳定状态；
- 标签泄露用户正文：中央静态标签与稳定 ID 校验；
- AndroidTest 未真实编译：以 `assembleDebugAndroidTest` 为硬门禁。

## 26. 回滚

可以整体回滚首页领域/UI、推荐策略、App 装配和新标签；保留已合法创建的 Issue、Stage 和 Run。不得降级 Room v9、删除数据、回滚 PR09-07/09、删除 PR-A/PR-B 基础、恢复长期占位标签或静默背景传递。

## 27. PR09-08 交接

冻结已确认 Issue、当前 Stage、Skill 阵容、当前 Run、现有工作区入口和单次协作指令接入点。PR09-08 不得重写首页推荐或 ExecutionRun 状态机。

## 28. PR09-10A 交接

冻结 Issue/Stage 工作区、当前消息和 Run、页面扩展插槽、返回与恢复契约。PR09-10A 不得依赖首页草稿作为正式成果。

## 29. 完成门禁

只有代码、测试、Draft PR、CI 证据、接口交接和本地验收 Prompt 完整后才报告开发完成。远端未执行的 Instrumentation、UIAutomator、TalkBack、360dp、200% 字号、明暗主题、键盘与返回栈必须明确列为等待本地严格只读验收。

未经用户明确授权，不标记 Ready、不合并、不删除分支、不启动 PR09-08 或 PR09-10A。
