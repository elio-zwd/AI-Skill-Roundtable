# PR09-06 首页与推荐接口交接

## 1. 交接范围

PR09-06 向 PR09-08 与 PR09-10A 提供问题优先首页、价值方向、正式 Skill 推荐、上下文确认、仅保存议题、最终启动和导航恢复契约。

后续 PR 可以消费这些公共模型和现有执行工作区，不得重写首页推荐、上下文确认或 `ExecutionRunCoordinator` 状态机。

## 2. 稳定领域模型

### `HomeQuestionDraft`

```kotlin
data class HomeQuestionDraft(
    val question: String,
    val directions: Set<ValueDirection>,
)
```

- `question` 是当前工作流问题草稿；
- `directions` 只能包含 `REALITY_SUPPORT`、`THINKING_EXPANSION`；
- 双方向仍属于同一 Issue/Stage 主线。

### `HomeRecommendation`

```kotlin
data class HomeRecommendation(
    val questionSummary: String,
    val directions: Set<ValueDirection>,
    val mode: RecommendationMode,
    val modeReason: String,
    val skills: List<RecommendedSkill>,
    val expectedOutput: String,
    val source: RecommendationSource,
)
```

- 当前唯一生产来源为 `LOCAL_CATALOG`；
- `skills` 包含可查看候选，正式启动成员使用 `selectedSkills`；
- 最终启动前仍需对当前 Catalog 进行 ID、唯一性和执行资格校验。

### `RecommendedSkill`

关键字段：

- `skillId`：正式 Catalog 稳定 ID；
- `responsibility`：本次协作职责，不覆盖 System Prompt；
- `reason`：本次推荐理由；
- `riskDisclosure`、`freshnessDisclosure`；
- `networkRequirement`、`materialRequirement`；
- `expectedOutput`；
- `executable`、`selected`、`position`。

后续页面必须按 `position` 稳定排序，不得用中文名称作为身份或路由参数。

### `HomeFinalConfirmation`

```kotlin
data class HomeFinalConfirmation(
    val ids: HomeWorkflowIds,
    val question: String,
    val directions: Set<ValueDirection>,
    val recommendation: HomeRecommendation,
    val contextSelection: HomeContextSelectionSnapshot,
    val confirmedAt: Long,
)
```

只有 `contextSelection.confirmed == true` 且推荐成员通过最终 Catalog 校验时，才能进入启动编排。

### `HomeWorkflowState`

事实字段：

- `ids`；
- `draft`；
- `step`；
- `recommendation`；
- `recommendationConfirmed`；
- `contextSelection`；
- `finalConfirmationReady`；
- 推荐 request token；
- 错误码和操作锁；
- `restored`。

恢复后状态进入 `RESTORED_DRAFT`，不会自动推荐、保存或启动，并将所有待确认内容保持为未确认。

### `HomeStartResult`

```text
SavedOnly(issueId, stageId)
Started(issueId, stageId, runId)
SavedNotStarted(issueId, stageId, errorCode)
Failure(errorCode)
```

`SavedNotStarted` 表示 Issue/Stage 已合法保留，但上下文准备或执行启动未完成。后续 UI 不得删除 Issue 后偷偷重建。

## 3. 稳定身份契约

`HomeWorkflowIds` 一次性生成：

```text
workflowId
issueId
stageId
runId
saveIssueIdempotencyKey
executionIdempotencyKey
```

以下操作必须复用：

- 重组；
- Activity 重建；
- 推荐重试；
- 保存失败重试；
- 已保存未开始后的启动重试；
- 重复点击被操作锁拦截后的继续。

只有用户明确开始一个新问题工作流时才创建新 ID。

## 4. 保存与启动接口

### 仅保存

```kotlin
HomeStartCoordinator.saveOnly(HomeSaveOnlyCommand)
```

唯一副作用是公共 `JianyuRepository.saveIssue()`。不得调用推荐 Gateway、Coordinator、模型或网络，不得创建 Run、Participant、Usage Snapshot 或 Pending Message。

### 确认后启动

```kotlin
HomeStartCoordinator.start(HomeFinalConfirmation)
```

顺序：

```text
saveIssue
→ prepareExecutionContext
→ ExecutionStartCommand(
     selections,
     contributions,
     contextUsage,
     stable IDs
  )
→ ExecutionRunCoordinator.start
```

`contributions` 与 `contextUsage` 必须来自同一个 `PreparedExecutionContext`，不得只传其中一个。

## 5. 导航契约

首页只通过：

```kotlin
navigateToIssue(issueId, stageId)
```

进入现有 `IssueExecutionRoute`。路由参数只包含稳定 ID，不包含问题正文、推荐理由、资料正文、个人背景或 Prompt。

页面导航不是执行事实源；进入工作区后继续以 Room 中 Issue、Stage、Run、Participant 和 Message 为事实源。

## 6. PR09-08 可消费接口

PR09-08 需要消费：

- 已确认 `issueId`；
- 当前 `stageId`；
- 当前正式 Skill 阵容：`HomeRecommendation.selectedSkills` 对应的官方 ID、position 和 responsibility；
- 当前 `runId` 与 Room 执行状态；
- 现有 `IssueExecutionRoute`；
- 单次协作指令应通过执行层的公开命令/消息入口接入。

PR09-08 不得：

- 重写本地 Catalog 推荐；
- 从首页草稿重新推导正式成员；
- 绕过上下文确认；
- 创建第二个 Coordinator；
- 把点名或单次指令实现为第二条执行状态机。

## 7. PR09-10A 可消费接口

PR09-10A 需要消费：

- 已持久化 Issue/Stage；
- 当前 Run、Participant 和 Message；
- `IssueExecutionRoute` 页面扩展点；
- `issueId/stageId` 返回与恢复契约；
- `SavedNotStarted`、运行中、成功、可重试、失败等已有执行语义。

PR09-10A 不得：

- 依赖首页 `SavedStateHandle` 草稿作为正式成果；
- 把导航参数扩展为正文或上下文载荷；
- 重写首页推荐、Skill 资格校验或 Context Builder；
- 修改 PR09-07 已冻结的运行状态含义。

## 8. UI 自动化契约

后续 PR 必须保留：

- `home_screen`；
- 首页正式静态标签；
- `context_confirmation_*`；
- `issue_execution_*`；
- Scaffold 根节点 `testTagsAsResourceId = true`；
- 动态标签只使用 `normalizedStableId()`。

不得恢复 `home_question_placeholder`，不得把用户正文、中文名称、姓名或资料标题拼入标签。

## 9. 数据与迁移边界

PR09-06 不新增 Room 表或字段，数据库保持 v9。后续任务若确实需要持久化新事实，必须独立提出 Schema PR，不得在 PR09-08/10A 内悄然修改 PR09-09 独占实现。

## 10. 失败与恢复边界

- 推荐失败：保留首页草稿；
- 上下文候选读取失败：显示错误，不默认选择；
- 上下文准备失败：Issue/Stage 保留，零启动调用；
- Coordinator 启动失败：`SavedNotStarted`，复用同一 ID；
- 无 Key/离线：由执行层以正式错误码表达；
- Activity 重建：不自动产生任何副作用；
- 导航重放：不得重复启动 Run。

## 11. 隐私边界

后续 PR 不得把以下内容放入日志、遥测、自动化标签、证据文件名或路由：

- 问题正文；
- 资料正文；
- 个人背景正文；
- 推荐 Prompt；
- API Key。

## 12. 交接状态

本文件冻结的是代码接口与所有权，不代表设备验收已通过。GitHub CI、设备 Instrumentation、外部 UIAutomator、360dp、大字体、明暗主题、TalkBack、键盘和返回栈结果以 PR #44 最新 Head 的真实证据为准。
