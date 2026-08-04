# PR09-06：首页与推荐实施计划

> **目标**：在 Room v9 与现有导航、官方 Skill Catalog、资料确认和执行运行能力之上，建立问题优先首页、双价值方向、可解释 Skill 推荐、上下文确认、仅保存议题及确认后开始运行闭环。
>
> **开发分支**：`feat/pr-09-06-home-recommendation`  
> **实际 Base**：`main@54f458379359275973b3fff966fb512b1d5f6517`  
> **目标 PR**：Draft PR `feat: 建立见域首页与推荐`

## 1. 启动核验

已通过 GitHub 远端事实核验：

- `origin/main` 为 `54f458379359275973b3fff966fb512b1d5f6517`；
- `50a0fb401ebbdbc9ce26aba9a7dd40916b6ba610` 是 `main` 祖先；
- `b36f8222539d8f4d99de0321caac5f9ef3ec7de1` 是 `main` 祖先；
- `13cc1e6b8fef6f9c5882ec3ff8c5548026ca933a` 是 `main` 祖先；
- `54f458379359275973b3fff966fb512b1d5f6517` 与 `main` 相同；
- 开始时没有开放 PR，因此不存在已知首页、推荐、上下文确认、根导航或执行接线冲突；
- 开发分支已从精确 Base 创建，不直接修改 `main`。

Superpowers 插件接口未安装。本任务读取仓库内保存的 Superpowers 6.2.0 `brainstorming`、`writing-plans`、`executing-plans`、`test-driven-development`、`systematic-debugging`、`verification-before-completion`、`requesting-code-review` 和 `finishing-a-development-branch` Skill 文件，并按项目规则执行等价人工流程。远端 GitHub 插件不等于本地 Android 工作区；未取得命令或 CI 证据前，不把测试、构建或设备场景描述为通过。

## 2. 当前调用链与所有权

当前唯一生产调用链：

```text
MainActivity
→ MainAppContent
→ 唯一 JianyuAppRuntime
   ├─ 唯一 JianyuRepository
   ├─ 唯一 OfficialSkillCatalogRuntimeResult
   └─ 唯一 ExecutionRunCoordinator
→ AppNavHost
→ HomeRoute（当前占位）
```

首页只消费公共接口：

- `JianyuRepository.saveIssue()`；
- `JianyuRepository.listMaterials()`；
- `JianyuRepository.listPersonalContexts()`；
- `JianyuRepository.prepareExecutionContext()`；
- `OfficialSkillCatalog`；
- `ExecutionRunCoordinator.start()`；
- `ContextConfirmationUiState`、`ContextCandidateUi`、`ContextConfirmationDialog`；
- `NavHostController.navigateToIssue()`。

不得新增第二个 Repository、ExecutionRunCoordinator、Context Builder、Catalog Runtime、API Key 池、网络客户端、DAO 入口或执行状态机。

## 3. 推荐方案比较与结论

### 方案 A：纯本地确定性推荐

- 从官方 Catalog 的价值方向、场景、领域、输入输出、执行资格、风险、联网和资料要求生成候选及理由；
- 无 Key、离线和设备自动化环境均可运行；
- 结果可稳定测试；
- 自然语言理解深度有限。

### 方案 B：模型推荐

- 通过可注入 Gateway 调用模型后再校验 Catalog；
- 语义理解更强；
- 引入 Key、离线、成本、时延、输出稳定性和推荐调用与执行调用混淆风险。

### 方案 C：混合推荐

- 本地召回、模型排序解释、Catalog 再校验；
- 理论质量最高；
- 当前 PR 会同时引入两种运行路径、额外失败恢复和测试所有权，超出建立稳定首页闭环的最小范围。

### 正式选择

本 PR 选择 **方案 A：纯本地确定性推荐**，唯一生产实现为 `LocalCatalogHomeRecommendationGateway`。推荐来源明确标记为 `LOCAL_CATALOG`，不伪装成模型分析，不调用 Gemini，不读取 `.env`，不创建 Pending Message、Run 或 Usage Snapshot。后续若引入模型排序，必须通过独立 PR 将 Gateway 替换为单一拥有者实现，仍以正式 Catalog ID 和最终 Catalog 校验为准。

## 4. 领域模型

创建 `app/src/main/java/com/elio/jianyu/home/`：

- `HomeModels.kt`
  - `HomeQuestionDraft`
  - `ValueDirection`
  - `RecommendationMode`
  - `RecommendationSource`
  - `RecommendationRisk`
  - `RecommendedSkill`
  - `HomeRecommendationRequest`
  - `HomeRecommendation`
  - `HomeFinalConfirmation`
  - `HomeWorkflowIds`
  - `HomeStartResult`
- `HomeRecommendationGateway.kt`
  - `HomeRecommendationGateway`
  - `LocalCatalogHomeRecommendationGateway`
- `HomeRecommendationPolicy.kt`
  - 对 Catalog 候选评分、去重、模式选择、理由、职责和披露进行纯函数处理；
  - 风险只进入披露，不作为人物型 Skill 自动降权依据；
  - 不可执行候选可以显示，但最终启动成员只允许正式、唯一、可执行 ID。
- `HomeWorkflow.kt`
  - 纯 Kotlin 状态转换和确认失效规则；
  - 不发起 IO。
- `HomeStartCoordinator.kt`
  - 只编排公共 Repository 与现有 Coordinator；
  - 不访问 DAO，不创建网络客户端，不实现执行状态机。

## 5. 首页状态机

创建 `HomeWorkflowState` 组合状态，覆盖：

```text
EditingQuestion
RecommendationLoading
RecommendationFailure
RecommendationReady
NoSuitableSkill
NoExecutableSkill
EditingRecommendation
ContextConfirming
ContextNeedsCorrection
FinalReview
SavingIssue
SavedNotStarted
StartingExecution
StartFailure
NavigatingToIssue
RestoredDraft
```

环境错误通过稳定错误码区分 `catalog_unavailable`、`no_api_key`、`offline`、`context_too_large`、`source_stale`、`storage_failure` 和 `execution_failure`。一次性导航使用独立 `HomeEvent.NavigateToIssue(issueId, stageId)`，不把导航当作执行事实源。

合法转换：

```text
编辑问题
→ 请求推荐
→ 推荐结果/失败
→ 用户调整并确认推荐
→ 打开上下文确认
→ 用户显式确认
→ 最终确认
→ 保存 Issue/Stage
→ 准备上下文
→ 启动唯一 ExecutionRunCoordinator
→ 导航到现有议题执行工作区
```

独立保存路径：

```text
编辑问题
→ 仅保存议题
→ saveIssue
→ 导航到已保存、尚未运行的议题工作区
```

## 6. 稳定 ID 与恢复

`HomeIdProvider` 可注入并在工作流首次创建时生成：

```text
workflowId
issueId
stageId
runId
saveIssueIdempotencyKey
executionIdempotencyKey
```

生产默认使用 UUID；测试使用固定 ID。`HomeDraftSnapshot` 通过 `SavedStateHandle` 保存为 JSON 字符串，包含：

- 问题文本；
- 价值方向；
- 推荐结果；
- 调整后的 Skill 阵容；
- 上下文选择草稿；
- 当前步骤；
- 稳定 ID；
- 推荐和上下文确认状态。

恢复后不自动推荐、不自动保存 Issue、不自动准备上下文、不自动启动 Run，并把待确认状态保持为待确认。用户明确重新开始新问题时才生成新 ID。

## 7. 问题与双价值方向

首页输入为第一主任务。`现实支持` 与 `思维拓展` 是两个独立可选项：

- 可跳过；
- 可单选；
- 可组合；
- 组合仍只使用同一个 `issueId`、`stageId` 和 `runId`；
- 更改问题会使旧推荐、上下文确认和最终确认失效，但不会修改已经保存的 Issue。

提供少量真实示例问题，以稳定示例 ID 填充输入；示例入口不是隐藏测试后门。

## 8. 推荐与调整

`LocalCatalogHomeRecommendationGateway`：

1. 只读取 `availability.recommendable == true` 的正式 Catalog 项；
2. 根据用户方向、问题中与 Catalog 中文摘要/场景/领域相匹配的词项及默认顺序评分；
3. 先按匹配分数，再按 `defaultOrder` 和稳定 ID 排序；
4. 不按人物风险标签降低排序；
5. 根据 `useMode`、方向组合和互补价值选择单 Skill 或多 Skill；
6. 生成非空职责、非空理由、风险/能力边界、时效、联网、资料、预期输出、执行资格和来源；
7. 结果再次校验 Catalog ID、唯一性、稳定 position 和执行资格。

用户可删除、增加、替换、调整顺序、修改职责和切换单/多 Skill。每次修改都会使推荐确认、上下文确认和最终确认失效。未知 ID、重复 ID、空职责或不可执行启动成员被拒绝。

当 Catalog 没有推荐候选时进入 `NoSuitableSkill`；有候选但没有可执行成员时进入 `NoExecutableSkill`。两种状态都保留问题和方向，允许修改问题、浏览 Skill 或仅保存议题。

## 9. 上下文确认

首页复用 `ContextConfirmationDialog`，不创建同义 Dialog 或 Builder。

候选读取：

- 当前稳定 `issueId`/`stageId` 下的活跃资料；
- 活跃个人背景；
- 所有候选默认 `selected=false`、`networkAllowed=false`、`sensitiveConfirmed=false`。

新 Issue 尚未保存时通常没有关联资料，因此首页仍展示个人背景候选和明确空资料状态；不跨议题静默复用其他 Issue 的资料。

`baseContextCharacters` 使用稳定纯函数计算：问题、Issue/Stage 标题和目标、Skill 职责、固定模板预估之和。用户确认时构造 `ContextSelectionDraft`，但在最终开始前不写 Usage Snapshot、不创建 Run、不调用网络。

取消确认保留候选草稿，保持零 Run、零 Usage、零预算和零网络。

## 10. 最终确认与启动顺序

最终确认展示问题摘要、方向、模式、Skill 阵容、职责、理由、风险/时效、联网/资料要求、用户选择的资料与背景、预期输出，以及“将创建一个 Issue/初始 Stage 并开始模型调用”。

本 PR采用以下可恢复顺序：

```text
1. 校验最终推荐与可执行 Skill
2. saveIssue（稳定 issueId/stageId，可幂等重试）
3. prepareExecutionContext（显式确认草稿）
4. 构造 ExecutionStartCommand
5. 同时传入 contributions 与 contextUsage
6. 调用唯一 ExecutionRunCoordinator.start()
7. 导航到现有 IssueExecutionRoute
```

选择该顺序的原因：资料上下文必须属于真实 Issue/Stage；`prepareExecutionContext()` 会重新核验来源关系和版本。步骤 3 或之后失败时，合法 Issue/Stage 保留并进入 `SavedNotStarted`，Run 不存在或保持执行层可解释状态；重试复用同一 Issue/Stage 和稳定命令，不删除后重建。

保证：

- 最终确认前零 Run、零 Pending、零预算、零模型回答调用；
- 上下文准备失败不调用 Coordinator；
- Runtime 原子创建失败由现有执行层保证零 Pending、零预算和零网络；
- 双击由 ViewModel 操作锁、稳定 ID 和 Repository/Coordinator 幂等共同防护；
- 导航成功不触发第二次启动。

## 11. 仅保存议题

`HomeStartCoordinator.saveOnly()` 只执行：

```kotlin
repository.saveIssue(SaveIssueCommand(...))
```

不读取推荐 Gateway，不调用 Coordinator，不创建 Participant、Run、Usage Snapshot 或 Pending Message，不访问网络。失败时保留问题、方向和稳定 ID；重试使用同一命令。成功后导航到现有 Issue 工作区，页面依赖 Room 显示“尚未开始”。

## 12. UI 分层与文件

创建或修改：

```text
app/src/main/java/com/elio/jianyu/home/HomeModels.kt
app/src/main/java/com/elio/jianyu/home/HomeRecommendationGateway.kt
app/src/main/java/com/elio/jianyu/home/HomeRecommendationPolicy.kt
app/src/main/java/com/elio/jianyu/home/HomeWorkflow.kt
app/src/main/java/com/elio/jianyu/home/HomeStartCoordinator.kt
app/src/main/java/com/elio/jianyu/ui/screens/home/HomeUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/home/HomeViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/home/HomeRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/home/HomeScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/home/HomeComponents.kt
app/src/main/java/com/elio/jianyu/ui/App.kt
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
```

分层：

```text
App：只注入 runtime 与导航回调
Route：创建/收集 ViewModel、处理一次性导航、显示复用 Dialog
ViewModel：发起受控协程与 IO，保存 SavedStateHandle
Screen：不可变 UiState + 回调
Components：局部展示
```

Material 3 页面使用可滚动布局，支持窄屏、长理由、多 Skill、大字体、明暗主题、键盘和 TalkBack；选中状态同时使用控件状态与文字，不只依赖颜色。本 PR 不实施最终品牌视觉。

## 13. UI 自动化标签

在 `JianyuAutomationTags.Home` 中新增并在真实节点落地：

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

动态部分统一调用 `normalizedStableId()`。删除 `home_question_placeholder`，更新 `HomeTestTags` 为中央契约兼容别名。只有真实节点加入 `frozenStaticTags`；保留所有既有冻结字符串值和 Scaffold 的 `testTagsAsResourceId = true`。

## 14. TDD 与测试文件

测试代码先于对应生产实现提交。远端没有本地 Android 命令环境时，先提交失败场景和预期失败原因，再提交最小生产实现；实际 RED/GREEN 由 GitHub CI 和本地只读验收提供证据。

JVM：

```text
app/src/test/java/com/elio/jianyu/home/HomeWorkflowTest.kt
app/src/test/java/com/elio/jianyu/home/HomeRecommendationPolicyTest.kt
app/src/test/java/com/elio/jianyu/home/HomeStartCoordinatorTest.kt
app/src/test/java/com/elio/jianyu/home/HomePrivacyTest.kt
app/src/test/java/com/elio/jianyu/ui/automation/JianyuAutomationTagsTest.kt
```

覆盖问题/方向、单多 Skill、理由/职责、去重、未知与不可执行 ID、高风险不降权、迟到结果、重复点击、仅保存零推荐/零 Coordinator、上下文默认不选、确认失效、稳定 ID、SavedNotStarted、隐私和标签安全。

Compose Instrumentation：

```text
app/src/androidTest/java/com/elio/jianyu/ui/screens/home/HomeScreenTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/screens/home/HomeRouteTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/automation/JianyuUiAutomationNavigationTest.kt
```

使用 Fake Repository、Catalog、Recommendation Gateway 和 Coordinator 或页面纯 UiState，不访问生产网络。覆盖输入、双方向、示例问题、推荐状态、调整、复用上下文 Dialog、最终确认、仅保存、恢复语义、窄屏、大字体和正式标签。

## 15. CI 与验证

远端提交后读取并记录：

```text
git diff --check
Secret scan
Android CI
Android UI Test Compile
Review Thread
当前 Head
```

GitHub CI 至少应覆盖 JVM、Lint、Debug/Release 和 `:app:assembleDebugAndroidTest`。Room 保持 v9，`app/schemas/.../9.json` 不修改，禁止 Database/DAO/Migration 漂移。

本地严格只读验收使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
tools/device/cli.py
```

全量优先执行 compile、JVM、Lint、Debug、Release、AndroidTest APK 和 connected Instrumentation；JUnit XML 缺失或零测试判定为 `NOT_VERIFIED`。设备关键路径使用 `--by tag`，不使用固定坐标、中文文案、OCR 或生产网络；不卸载 App、不清除数据。

## 16. 禁止文件

不得修改：

```text
MaterialContextRepositoryComponent.kt
MaterialContextMigration.kt
ResourceLifecycleEntities.kt
JianyuRepositoryDao.kt
RoundtableDatabase.kt
ResourcesRoute.kt
ResourcesViewModel.kt
ResourcesScreen.kt
ResourcesComponents.kt
ExecutionStateMachine.kt
ExecutionBudgetPolicy.kt
ExecutionRuntimeRepositoryComponent.kt
ExecutionRunCoordinator.kt 的既有状态语义
tools/device/
tools/local-verification/
app/schemas/**/9.json
```

## 17. Commit 边界

```text
docs: 制定PR09-06首页与推荐实施计划
test: 增加首页工作流失败场景
feat: 建立首页问题与本地Catalog推荐
feat: 接入上下文确认与开始运行闭环
test: 冻结首页UI自动化语义标签
docs: 冻结首页接口与本地验收步骤
```

每个 Commit 只表达一个意图，不添加 `Co-Authored-By`，不强制推送。

## 18. 风险与防护

- 旧推荐覆盖新问题：请求 token 与问题 revision 校验；
- 双击创建重复 Issue/Run：操作锁、稳定 ID、幂等键；
- 双方向创建双主线：单一 `HomeWorkflowIds`；
- 推荐失败丢草稿：错误状态只替换推荐子状态；
- 保存议题误调用模型：独立 `saveOnly()` 单入口测试；
- 背景默认勾选：候选构造默认 false；
- 上下文确认被跳过：最终确认要求 `confirmedForStart`；
- Runtime 前调用网络：只由现有 Coordinator 在原子 Runtime 后调用；
- Issue 已保存但启动失败：正式 `SavedNotStarted`；
- Activity 重建自动开始：恢复只还原稳定状态；
- 标签泄露用户内容：中央前缀 + 稳定 ID 校验；
- AndroidTest 未真实编译：以 `assembleDebugAndroidTest` 和 CI 为硬门禁；
- 外部验收假阳性：点击后期待此前不存在的新状态标签。

## 19. 回滚

可以整体回滚首页领域/UI、推荐策略、App 装配和新标签；保留已经合法创建的 Issue、Stage 和 Run。不得降级 Room v9、删除数据、回滚 PR09-07/09、删除 PR-A/PR-B 基础、恢复长期 `home_question_placeholder` 或恢复静默全量背景传递。

## 20. 后续交接

完成后创建 `docs/planning/pr-09-06-interface-handoff.md`：

- 向 PR09-08 冻结已确认 Issue/Stage、Skill 阵容、当前 Run、工作区入口和单次协作指令接入点；
- 向 PR09-10A 冻结 Issue/Stage 工作区、消息/Run、页面扩展插槽及返回恢复契约；
- 后续任务不得重写首页推荐、上下文确认或 ExecutionRun 状态机。

## 21. 完成门禁

只有在代码、测试、Draft PR、CI 证据和本地验收交接均完整后，才报告开发完成。远端未执行的 Instrumentation、设备语义、TalkBack、360dp、200% 字号和明暗主题场景必须明确列为等待本地 AI 严格只读验收。未经用户明确授权，不标记 Ready、不合并、不删除分支、不启动 PR09-08 或 PR09-10A。
