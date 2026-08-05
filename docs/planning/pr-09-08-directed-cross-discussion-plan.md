# PR09-08 点名回应与交叉讨论实施计划

## 1. 结论

PR09-08 采用 Room v9→v10 的最小前向迁移，在不建立第二套模型执行状态机的前提下，为现有 `ExecutionRunCoordinator` 增加“执行已原子创建 Runtime”的兼容入口，并新增薄协作编排层。

```text
IssueCollaborationCoordinator
    ├── 校验正式阵容、点名与交叉配置
    ├── 调用 Repository 原子创建 Message / Run / Usage / Discussion / Budget
    ├── 调用唯一 ExecutionRunCoordinator 执行已创建 Runtime
    └── 根据 Room 事实收敛 Discussion 状态

CrossDiscussionSession
    ├── CROSS_DISCUSSION_RESPONSE Run：N 位成员独立回应
    └── CROSS_DISCUSSION_SYNTHESIS Run：meeting-to-action 透明整合
```

整合不投票裁决、不隐藏少数观点、不自动多轮。进程恢复只读取 Room，不自动联网、不自动重试、不自动开始整合。

## 2. 实际基线

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base：main@d3cc0aa6d61297d64280ee9be0b7adc185386d0c
开发分支：feat/pr-09-08-directed-cross-discussion
Draft PR：#46
开始时开放 PR：0
Base Room：v9
目标 Room：v10
```

已核验 PR #45：

```text
状态：merged
Head：d1739836c0217159e9d38949b9ac8d664c1668db
Merge Commit：d3cc0aa6d61297d64280ee9be0b7adc185386d0c
```

当前正式可执行 Skill：

```text
study-planner
meeting-to-action
report-proposal-writer
research-fact-checker
```

本 PR 不修改 44 项基础 Catalog、执行批次 Manifest 或四项 Skill 资产正文。

## 3. GitHub 与 Superpowers

GitHub 当前具备仓库读取、Commit/PR/CI 读取、独立分支、文件写入、Commit、Draft PR、Workflow Job 与日志读取能力。

Superpowers 插件无可直接调用 Skill 接口；已读取仓库内：

```text
tools/ai/superpowers/README.md
tools/ai/superpowers/project-workflow.md
tools/ai/superpowers/skills/test-driven-development/SKILL.md
```

按等价人工流程执行：

```text
brainstorming
writing-plans
test-driven-development
systematic-debugging
verification-before-completion
requesting-code-review
finishing-a-development-branch
```

不使用 Worktree、并行子智能体、自动合并或自动删除分支。

## 4. PR09-08 与 PR09-10A 条件并行

用户已决定：PR09-08 与 PR09-10A 可从相同稳定 `main` 基线创建独立分支并行开发，但必须严格隔离文件所有权。

PR09-10A 在 PR09-08 合并前：

- 可以完成不依赖 PR09-08 共享文件的领域设计、纯模型、纯策略、独立测试和文档；
- 不得修改 PR09-08 独占的数据库、执行、协作和工作区共享文件；
- 不得进入最终 `IssueExecution` 工作区接线；
- 不得申请合并；
- 不得从 PR09-08 未合并分支复制代码；
- 不得反向修改 PR09-08 分支。

合并顺序：

```text
PR09-08 完成、验收并合并
→ PR09-10A 同步最新 main
→ PR09-10A 适配 Room v10 和 PR09-08 交接接口
→ PR09-10A 完成最终 IssueExecution 工作区接线
→ 重新执行全量测试和本地严格只读验收
→ PR09-10A 才可申请合并
```

## 5. PR09-08 独占文件所有权

PR09-08 继续独占以下范围，实际新增同类共享文件也自动归入本 PR：

```text
app/src/main/java/com/elio/jianyu/collaboration/
app/src/main/java/com/elio/jianyu/data/Collaboration*.kt
app/src/main/java/com/elio/jianyu/data/CoreDomain.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeEntities.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
任何 Migration 注册文件与 app/schemas/**/10.json
app/src/main/java/com/elio/jianyu/execution/ExecutionContextBuilder.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionPersistenceGateway.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionComponents.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueCollaborationComponents.kt
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt 中协作标签
app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt 中协作装配
```

如本 PR 新增共享文件，必须同步更新 Draft PR #46 描述与最终接口交接。

## 6. PR09-10A 并行期间禁止修改

PR09-10A 在 PR09-08 合并前不得修改：

```text
CoreDomain.kt
CollaborationEntities.kt
ExecutionContextBuilder.kt
ExecutionRunCoordinator.kt
ExecutionRuntimeEntities.kt
ExecutionRuntimeRepositoryContract.kt
ExecutionRuntimeRepositoryComponent.kt
RoundtableDatabase.kt
JianyuRepositoryDao.kt
RoomJianyuRepository.kt
任何 Migration
任何 Room Schema JSON
IssueExecutionRoute.kt
IssueExecutionViewModel.kt
IssueExecutionUiState.kt
IssueExecutionScreen.kt
IssueExecutionComponents.kt
JianyuAutomationTags.kt
JianyuAppRuntime.kt
```

## 7. 上游交接

### PR09-06

复用 `IssueExecutionRoute`、问题优先工作区、Context Confirmation 与 `PreparedExecutionContext`。不从首页 `SavedStateHandle` 推导正式阵容，不重写首页推荐。

### PR09-07

复用唯一 `ExecutionRunCoordinator`、Participant 状态机、Stop、迟到回调防护、预算消费和失败成员重试。协作层不调用 Retrofit、不自行流式写 Pending、不自行消费预算。

### PR09-09

复用 `prepareExecutionContext()`、`ExecutionContextContribution`、`ContextUsageWriteSet`、资料/个人背景显式确认、24,000 字符门禁、Hash 与敏感确认。模型输出不得伪装为资料或个人背景 Usage。

## 8. 当前阵容事实来源

采用当前 Stage 最近一个 `STANDARD` 根 Run 的 Participant Snapshot：

```text
runKind == STANDARD
retryOfRunId == null
parentRunId == null
```

按 `position` 排序，排除 Directed、Cross Response、Cross Synthesis 和任何 retry 子 Run。

理由：

- Snapshot 冻结 Prompt、配置、职责、身份和顺序；
- 跨进程可恢复；
- 不受 Catalog Prompt 更新漂移；
- 临时点名和整合不会变成长期阵容；
- retry 只延续原执行，不定义新阵容。

无合法 STANDARD 根 Run 时，点名与交叉入口禁用并显示明确原因，不从全 Catalog 补成员。

## 9. 新调用参与者方案

采用方案 C：当前执行资格重新校验 + 复制冻结 Snapshot。

1. 使用 Snapshot `sourceId` 在当前 Catalog 中定位；
2. 通过 `OfficialSkillExecutionEligibility` 重新审计；
3. 复制冻结的 `systemPrompt`、`configurationJson`、`displayName`、`avatar`、`defaultResponsibility`、`skillAssetPath` 与 `sourceId`；
4. 创建新的 Participant Snapshot ID；
5. 不用 Catalog 当前 Prompt 覆盖冻结 Prompt；
6. 已撤回、未知或不再可执行的 Skill 阻止启动。

## 10. 点名语义

Run Kind：`DIRECTED_RESPONSE`。

- 精确一位当前阵容成员；
- 只影响本次请求；
- 用户问题写成正式 Stage Message；
- `triggerMessageId` 指向该用户 Message；
- 不改变正式阵容；
- 下一次输入恢复未点名；
- 失败不回退全阵容；
- 重试仍使用原冻结 Skill；
- 双击只创建一条用户 Message 和一个 Run；
- Activity 重建不重复发送。

## 11. 交叉讨论语义

交叉讨论只能由用户显式触发，至少两位当前阵容成员，只进行一轮。

### Response Run

```text
runKind：CROSS_DISCUSSION_RESPONSE
historyScope：EXPLICIT_MESSAGES 或 NO_HISTORY
参与者：用户明确选择的当前阵容成员
```

所有成员使用相同预创建历史快照，每位成员不得读取同一 Response Run 的其他成员输出。

### Synthesis Run

```text
runKind：CROSS_DISCUSSION_SYNTHESIS
parentRunId：Response Run ID
retryOfRunId：仅整合重试时使用
historyScope：EXPLICIT_MESSAGES
参与者：meeting-to-action
```

Synthesis 只读取本次成功 Response 输出快照和用户确认的必要上下文，不读取整个 Stage。

## 12. 整合者决策

比较后采用固定透明官方 Skill `meeting-to-action`：

- 身份可见；
- 当前执行资格可审计；
- 可冻结 Participant Snapshot；
- 调用计入预算；
- 可跨进程恢复；
- 便于自动化验收。

不采用用户任意选择整合者、隐藏内部 Prompt 或本地规则拼接。若 `meeting-to-action` 不再可执行，启动前阻止，不静默切换。

UI 必须显示：

```text
整合由“会议行动助手（meeting-to-action）”完成
整合调用计入本次预算
```

## 13. 整合输出约束

固定要求：

```text
共识
分歧
适用条件
关键不确定性
明确建议
下一步
```

同时要求：不投票裁决、不把多数意见包装成事实、不隐藏少数观点、不替失败成员补写、不改写参与者原意。

## 14. 历史范围

新增：

```kotlin
ExecutionHistoryScope.FULL_STAGE
ExecutionHistoryScope.EXPLICIT_MESSAGES
ExecutionHistoryScope.NO_HISTORY
```

- 旧 STANDARD 保持 `FULL_STAGE`；
- Directed/Cross 默认显式消息；
- 显式列表为空时为 `NO_HISTORY`；
- 空选择绝不解释成全阶段；
- Message 必须属于同 Issue/Stage、非 Pending、已存在、去重且顺序稳定；
- Builder 仍拒绝 `sourceExecutionRunId == currentRunId` 的同批内容。

## 15. Message Usage Snapshot

新增 `execution_message_usage_snapshots`：

```text
id
runId
sourceMessageId
sourceExecutionRunId
sourceParticipantSnapshotId
senderIdSnapshot
senderNameSnapshot
contentSnapshot
contentHash
usageOrder
usedAt
```

规则：

- 保存实际发送正文，不回查可变 Message；
- Hash 使用 UTF-8、CRLF/CR→LF、不 trim、不截断；
- 不允许 Pending、其他 Issue、其他 Stage 或重复 Message；
- 后续 Message 变化不改历史快照；
- Synthesis 快照本次成功输出；
- 整合重试复制原 Usage，不读取当前 Stage 全文。

## 16. Schema 决策

采用 Room v10。

`execution_runs` 新增：

```text
runKind TEXT NOT NULL DEFAULT 'standard'
parentRunId TEXT NULL
discussionId TEXT NULL
historyScope TEXT NOT NULL DEFAULT 'full_stage'
```

`retryOfRunId` 只表达重试；`parentRunId` 只表达非重试父子关系。为保持 SQLite `ALTER TABLE` 可执行性，`parentRunId` 使用显式字段、索引和 Repository 应用级关系校验，不在 v9→v10 中重建核心表添加自引用外键。

新增表：

```text
cross_discussion_sessions
execution_message_usage_snapshots
```

Discussion 状态：

```text
RESPONDING
PARTIAL_SUCCESS
AWAITING_SYNTHESIS
SYNTHESIZING
SYNTHESIS_RETRYABLE
SUCCEEDED
STOPPED
FAILED
```

## 17. Migration

新增 `CollaborationMigration.MIGRATION_9_10` 并注册 `ALL_MIGRATIONS`。

- 旧 Run 默认迁移为 `STANDARD/FULL_STAGE`；
- 保留 `9.json`；
- `10.json` 必须由 Room/KSP 编译真实生成；
- 不使用 destructive migration；
- 验证 v1→v10 至 v9→v10；
- 验证 v9 Run、预算、Participant、Material/Personal Usage 不漂移；
- `PRAGMA foreign_key_check = 0`。

## 18. 原子事务

公共命令：

```text
CreateDirectedInteractionCommand
CreateCrossDiscussionResponseCommand
CreateCrossDiscussionSynthesisCommand
```

Directed 事务一次写入：用户 Message、Run、Participant Snapshot/State、Budget、Material/Personal Usage、Message Usage。

Cross Response 事务一次写入：用户 Message、Discussion、Response Run、N 个 Participant Snapshot/State、共享预算根、Material/Personal Usage、Message Usage。

Cross Synthesis 事务一次写入：Synthesis Run、整合 Participant Snapshot/State、共享预算关系、成功 Response 输出 Usage、Discussion 状态。

网络调用永不进入数据库事务。

## 19. 幂等

- 相同键 + 相同 payload：返回既有事实，`idempotent=true`；
- 相同键 + 不同问题、成员、消息选择、Usage 或整合范围：`IdempotencyConflict`；
- 写入失败整笔回滚；
- 写入失败零网络、零预算消费、无悬挂 Pending；
- ViewModel 操作锁和数据库唯一约束双重防护。

## 20. Coordinator 扩展

新增：

```kotlin
ExecutionRunCoordinator.startPrepared(command)
```

它只执行已原子创建 Runtime：

- 非 `NOT_STARTED` 只返回 Room 事实；
- 根据 `historyScope` 使用 Full Stage、Message Usage 或空历史；
- 复用现有 Pending、流式写入、预算消费、Stop、迟到回调和聚合；
- Cross Response 成功时可保持预算开放；
- 普通 `start()` 与 STANDARD `retry()` 保持兼容；
- 协作 Run 不使用旧通用 retry 伪装 Discussion 关系。

## 21. 预算

Response Run 作为预算根，初始必须覆盖：

```text
N 个独立回应 + 1 个整合调用
```

- 初始 `reservedRequiredCalls = N + 1`；
- 每位成员消费一次并保留剩余成员和整合；
- Response 成功不关闭预算；
- 部分失败重试不返还调用；
- Synthesis 沿 `parentRunId` 共享 Response root；
- Synthesis 终态或用户停止整个讨论后关闭；
- 进程恢复不追加调用。

## 22. 部分失败

- 全部成功：`AWAITING_SYNTHESIS`；
- 部分成功：`PARTIAL_SUCCESS`；
- 全部失败：`FAILED`，不创建 Synthesis。

部分成功 UI 提供：

```text
重试失败成员
仅整合当前成功内容
停止讨论
```

只有用户明确选择“仅整合当前成功内容”后才创建 Synthesis，并在整合中注明成功与失败成员。

## 23. 整合失败与重试

- 保留所有 Response 原始输出；
- 只重试 Synthesis；
- 不重复成功成员；
- 使用原 Synthesis Message Usage；
- 不切换整合 Skill；
- 不返还预算；
- `retryOfRunId` 仅用于新的 Synthesis retry 子 Run。

## 24. Stop

- 配置取消：零持久化；
- Response 运行中：调用现有 Coordinator Stop，保留完成输出，Discussion→STOPPED，不启动 Synthesis；
- Synthesis 运行中：停止 Synthesis，保留 Response 输出和不完整整合文本；
- 尚未发出网络时仍持久化可解释的 STOPPED；
- 迟到回调继续由 `ensureAcceptsWrites()` 拒绝。

## 25. 进程恢复

恢复只读 Room，不自动联网。

必须恢复：Directed 中断/失败/成功，Cross RESPONDING、PARTIAL_SUCCESS、AWAITING_SYNTHESIS、SYNTHESIZING 中断、SYNTHESIS_RETRYABLE、SUCCEEDED、STOPPED、FAILED。

Response 完成但 Synthesis 未创建时显示“成员回应已完成，等待继续整合”，必须由用户点击。

## 26. 纯策略

```text
CurrentStageRosterPolicy
DirectedResponsePolicy
CrossDiscussionPolicy
CrossDiscussionProgressPolicy
SynthesisSkillEligibilityPolicy
```

策略不访问 DAO、不调用网络、不记录正文。

## 27. 工作区 UI

在现有 Route→ViewModel→Screen→Components 分层中增加：

```text
CollaborationComposer
DirectedResponseDialog
CrossDiscussionDialog
CrossDiscussionStatusCard
```

稳定事实来自 Room，Dialog 草稿可用 ViewModel/SavedStateHandle，但不作为执行事实源。

Composer：问题输入、当前阵容、点名、交叉讨论、无阵容原因。

Directed Dialog：精确一人、本次仅该 Skill、职责、历史消息、资料与个人背景、联网说明、最终确认。

Cross Dialog：至少两人、职责、透明整合者、一轮、预计 N+1 调用、历史消息、资料与个人背景、不投票裁决、最终确认。

结果必须区分 STANDARD、Directed、Cross Response、Synthesis、部分失败、待整合、整合失败和停止。

## 28. 自动化标签

集中加入 `JianyuAutomationTags.Collaboration`：

```text
issue_collaboration_input
issue_directed_response_button
issue_cross_discussion_button
issue_collaboration_roster
directed_response_dialog
directed_response_confirm
directed_response_failure
cross_discussion_dialog
cross_discussion_focus_input
cross_discussion_integrator
cross_discussion_confirm
cross_discussion_status
cross_discussion_retry_failed
cross_discussion_synthesize_available
cross_discussion_resume_synthesis
cross_discussion_failure
```

动态标签统一调用 `normalizedStableId()`，不得包含中文名、问题正文、消息正文或资料标题。`testTagsAsResourceId = true` 保持在 Scaffold 根节点。

## 29. 无障碍与布局

覆盖 360dp、200% 字号、明暗主题、键盘、长问题、长 Skill 名、三个以上参与者、长错误、TalkBack、焦点顺序和触控目标。状态不只靠颜色；Dialog 可滚动；不使用固定高度阻塞大字体。

## 30. TDD 测试矩阵

JVM：Run Kind/History Scope、正式阵容恢复、Snapshot 资格审计、Directed/Cross 策略、Message Usage Hash/顺序、Synthesis Prompt、预算保留、部分失败 reducer、架构守卫。

Instrumentation：v1→v10 与 v9→v10、Directed 原子/双击、Cross Response 原子、同批互不可见、部分失败、仅整合成功内容、Synthesis 独立重试、共享预算、Stop、迟到回调、强停恢复、Compose 与标签。

外部 UIAutomator：只验证配置、语义标签、透明整合者和取消零副作用，不调用真实模型。

## 31. 架构守卫

- UI/ViewModel 不引用 DAO；
- collaboration 不引用 Retrofit 或 API Key；
- 网络只经过 `ExecutionRunCoordinator`；
- 不建立第二套 Participant/预算状态机；
- 不接入旧 `RoundtableOrchestrator`；
- `retryOfRunId` 不表达 Synthesis 父子关系；
- 不通过中文文案或参与者数量推断 Run Kind；
- Cross 不读取 Full Stage；
- 不自动多轮。

## 32. 预计修改文件

新增：

```text
collaboration/CollaborationPolicies.kt
collaboration/IssueCollaborationCoordinator.kt
data/CollaborationDao.kt
data/CollaborationEntities.kt
data/CollaborationMigration.kt
data/CollaborationRepositoryContract.kt
data/CollaborationRepositoryComponent.kt
data/CollaborationRuntimeRepositoryComponent.kt
data/LegacyDatabaseMigrationSupport.kt
ui/screens/execution/IssueCollaborationComponents.kt
```

修改：

```text
execution/ExecutionModels.kt
execution/ExecutionContextBuilder.kt
execution/ExecutionRunCoordinator.kt
execution/ExecutionPersistenceGateway.kt
data/CoreDomain.kt
data/ExecutionRuntimeRepositoryContract.kt
data/ExecutionRuntimeRepositoryComponent.kt
data/JianyuRepositoryContract.kt
data/JianyuRepositoryDao.kt
data/JianyuRepositoryTransactions.kt
data/RoomJianyuRepository.kt
data/RoundtableDatabase.kt
JianyuAppRuntime.kt
IssueExecutionRoute.kt
IssueExecutionViewModel.kt
IssueExecutionUiState.kt
IssueExecutionScreen.kt
IssueExecutionComponents.kt
JianyuAutomationTags.kt
相关 JVM/androidTest/Schema/文档/门禁脚本
```

## 33. 禁止无关修改

```text
首页推荐策略
44 项基础 Catalog
PR09-05B 四项资产正文
资料与个人背景生命周期语义
阶段推进、成果、音频、归档
设备工具与低 Token 工具
主题和最终视觉
旧 RoundtableOrchestrator 生产接线
```

## 34. Commit 边界

```text
docs: 制定PR09-08点名与交叉讨论计划
test: 增加协作领域与上下文失败场景
feat: 增加协作运行类型与消息使用快照
feat: 增加协作Room v10迁移
feat: 实现临时点名回应
feat: 实现一轮交叉讨论与透明整合
feat: 接入议题工作区协作入口
test: 冻结协作UI自动化标签
test: 完善协作恢复与设备验证
docs: 冻结PR09-10A协作消息交接
```

允许按真实耦合合并相邻原子意图，不夹带无关重构，不添加 `Co-Authored-By`。

## 35. CI 与远端验证

实际执行并记录：

```text
git diff --check
Secret scan
应用身份门禁
compileDebugKotlin
testDebugUnitTest
lintDebug
assembleDebug
assembleRelease
assembleDebugAndroidTest
Room Schema 当前性
Migration 连续性
Catalog 执行资格
```

`assembleDebugAndroidTest` 只代表测试 APK 编译，不代表设备 Instrumentation 已通过。

## 36. Room Schema 生成

1. 提交 Entity、Migration 与数据库版本；
2. 使用分支 CI 的 Room/KSP 编译真实生成 `10.json`；
3. 下载并提交该工件；
4. 确认 Schema 与 Entity/Migration 一致；
5. 运行最终 CI。

不得手写或复制伪造 `10.json`。

## 37. 本地严格只读验收

创建：

```text
docs/testing/pr-09-08-local-readonly-acceptance-prompt.md
```

强制使用 `tools/local-verification/Invoke-LocalVerification.ps1`，证据放仓库外 `$env:TEMP`，禁止卸载、清数据、生产网络和真实 API Key。

## 38. 风险与覆盖

| 风险 | 覆盖 |
|---|---|
| Directed 改变阵容 | 只认 STANDARD 根 Run + 测试 |
| Cross 成员互见 | 固定预创建 Usage + Builder 同批过滤 |
| Synthesis 读取全 Stage | `EXPLICIT_MESSAGES` + 测试 |
| 隐藏整合者 | 固定 Snapshot + UI 明示 |
| 整合不计预算 | Response root + N+1 reserve |
| 部分失败伪装完整 | Discussion 状态 + 显式确认 |
| Message Usage 漂移 | 正文快照 + Hash |
| retry/parent 混用 | 两字段独立断言 |
| Response 提前关闭预算 | `keepBudgetOpenOnSuccess` 测试 |
| 恢复自动联网 | 恢复测试 |
| Synthesis retry 重复成员 | 调用数测试 |
| 双击重复 Message | 原子幂等事务 |
| v10 破坏历史 | 连续 Migration + 数据保持 + FK check |
| 标签泄漏正文 | 稳定 ID 测试与隐私扫描 |
| 接回旧 Orchestrator | 架构守卫 |
| PR09-10A 并行冲突 | 文件所有权清单 + 合并顺序 |

## 39. 回滚

- 可关闭协作入口；
- 保留合法 Message、Run、Discussion 和 Usage；
- 不删除用户内容；
- 不修改 STANDARD Run；
- v10 只能通过新前向 Migration 修复，不能降回 v9；
- 不恢复旧 RoundtableOrchestrator；
- 不回滚 PR09-05B、06、07、09。

## 40. PR09-10A 接口交接

完成时创建 `docs/planning/pr-09-08-interface-handoff.md`，明确冻结：

```text
ExecutionRunKind
ExecutionHistoryScope
Message 与 Run 关系
CrossDiscussionSession 与 Response/Synthesis 关系
ExecutionMessageUsageSnapshot 读取接口
部分成功、停止、失败和恢复状态
PR09-10A 可安全读取的 Repository/Recovery 接口
PR09-10A 不得依赖的 ViewModel/SavedStateHandle 状态
PR09-08 最终实际修改文件
```

PR09-10A 可读取普通、点名、Cross Response 和 Synthesis Message，保留真实来源与分歧；不得依赖 PR09-08 UI 内存、不得重写协作执行状态、不得自动把整合升级为成果。
