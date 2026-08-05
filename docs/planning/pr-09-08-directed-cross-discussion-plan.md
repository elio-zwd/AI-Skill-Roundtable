# PR09-08 点名回应与交叉讨论实施计划

## 1. 结论

本任务采用 Room v9→v10 的最小前向迁移，在不创建第二套模型执行状态机的前提下，为现有 `ExecutionRunCoordinator` 增加可复用的“已原子创建 Runtime 后执行”入口，并新增薄协作编排层。

最终结构：

```text
IssueCollaborationCoordinator
    ├── 原子创建 Directed / Cross Response / Cross Synthesis 持久化事实
    ├── 调用唯一 ExecutionRunCoordinator 执行已创建 Runtime
    └── 根据 Room 事实收敛 Discussion 状态

CrossDiscussionSession
    ├── CROSS_DISCUSSION_RESPONSE Run（N 位成员独立回答）
    └── CROSS_DISCUSSION_SYNTHESIS Run（meeting-to-action 透明整合）
```

整合不自动投票、不隐藏少数观点、不自动多轮。进程恢复只读取 Room，绝不自动联网或自动开始整合。

## 2. 实际基线与仓库状态

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base 分支：main
实际 Base SHA：d3cc0aa6d61297d64280ee9be0b7adc185386d0c
开发分支：feat/pr-09-08-directed-cross-discussion
开始时开放 PR：0
当前 Room：v9
```

已核验 PR #45：

```text
PR：#45 feat: 发布首批可执行官方Skill
状态：closed / merged
Head：d1739836c0217159e9d38949b9ac8d664c1668db
Merge Commit：d3cc0aa6d61297d64280ee9be0b7adc185386d0c
```

`main` 未超出预期 Base，未发现开放 PR 或并行分支正在修改本任务范围。

## 3. GitHub 与 Superpowers 能力

当前 GitHub 连接具备：仓库读取、Commit/PR/CI 读取、分支创建、文件写入、Commit、Draft PR、Workflow Job 与日志读取。

Superpowers 插件没有可直接调用接口；已读取仓库内：

```text
tools/ai/superpowers/README.md
tools/ai/superpowers/project-workflow.md
```

后续按等价人工流程执行：

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

## 4. 前置能力与真实可执行清单

有效生产 Catalog 保持 44 项，当前真实可执行数量为 4：

```text
study-planner
meeting-to-action
report-proposal-writer
research-fact-checker
```

本 PR 不修改基础 Catalog、执行批次 Manifest 或四项 Skill 正文。

## 5. 上游交接结论

### PR09-06

复用 `IssueExecutionRoute`、问题优先工作区、Context Confirmation 与 `PreparedExecutionContext`。不从首页 `SavedStateHandle` 推导正式阵容，不重写首页推荐。

### PR09-07

复用唯一 `ExecutionRunCoordinator`、Participant 状态机、Stop、迟到回调防护、预算消费与失败成员重试。协作层不调用 Retrofit、不创建 Pending、不消费预算。

### PR09-09

复用 `prepareExecutionContext()`、`ExecutionContextContribution`、`ContextUsageWriteSet`、资料/个人背景显式确认、24,000 字符门禁、Hash 与敏感确认。模型输出不得伪装为资料或个人背景 Usage。

## 6. 当前阵容事实来源

最终采用：

> 当前 Stage 最近一个 `STANDARD` 根 Run（`retryOfRunId == null`）的 Participant Snapshot，按 `position` 排序。

排除：

```text
DIRECTED_RESPONSE
CROSS_DISCUSSION_RESPONSE
CROSS_DISCUSSION_SYNTHESIS
任何 retry 子 Run
```

理由：

- Participant Snapshot 已冻结 Prompt、配置、职责、身份和顺序；
- 跨进程可恢复；
- 不受 Catalog Prompt 更新漂移；
- 临时点名和整合 Run 不会变成长期阵容；
- 重试只延续原执行，不定义新阵容。

当前 Stage 没有合法 STANDARD 根 Run 时，点名与交叉入口禁用并显示“当前阶段尚无正式阵容”；不从全 Catalog 补成员。

## 7. 新调用的参与者方案

比较：

### 方案 A：从当前 Catalog 重新解析

会导致历史 System Prompt、默认职责和配置漂移，不采用。

### 方案 B：直接复制旧 Snapshot

无法阻止已撤回执行资格的 Skill 再次联网，不采用。

### 方案 C：当前资格重新校验 + 复制冻结 Snapshot

采用。

流程：

1. 根据 Snapshot `sourceId` 在当前 Catalog 校验存在；
2. 使用 PR09-05B `OfficialSkillExecutionEligibility` 重新审计；
3. 通过后复制旧 Snapshot 的 `systemPrompt`、`configurationJson`、`displayName`、`avatar`、`defaultResponsibility`、`skillAssetPath` 与 `sourceId`；
4. 为新 Run 生成新 Participant Snapshot ID；
5. 不使用 Catalog 当前 Prompt 覆盖冻结 Prompt。

## 8. 点名领域语义

新增 Run Kind：`DIRECTED_RESPONSE`。

规则：

- 精确一位当前阵容成员；
- 只影响本次请求；
- 用户问题写成正式 Stage Message；
- Run `triggerMessageId` 指向该用户 Message；
- 不改正式阵容；
- 下一次协作配置默认未点名；
- 失败不回退成全阵容；
- 重试继承原 Skill 与 Run Kind；
- 双击只创建一条用户 Message 和一个 Run。

## 9. 交叉讨论领域语义

交叉讨论必须由用户显式触发，至少两位当前阵容成员，只进行一轮。

第一阶段：

```text
Run Kind：CROSS_DISCUSSION_RESPONSE
历史范围：EXPLICIT_MESSAGES 或 NO_HISTORY
参与者：用户选中的当前阵容成员
行为：按 position 顺序执行，但每位成员模型上下文相同且不含同批输出
```

第二阶段：

```text
Run Kind：CROSS_DISCUSSION_SYNTHESIS
parentRunId：Response Run ID
retryOfRunId：仅在整合重试时使用
参与者：meeting-to-action
历史范围：EXPLICIT_MESSAGES
输入：本次焦点 + 本次成功 Response 输出快照 + 已确认资料/个人背景
```

不自动创建 Stage，不自动多轮，不自动追问。

## 10. 整合者方案比较

### 固定透明官方 Skill

身份明确、可资格审计、可冻结 Snapshot、可计预算、可恢复、易测试。

### 用户从参与者中选择

V1 会增加配置负担，并可能让不适合多成员输入的 Skill 取得所有输出。

### 隐藏工作流 Prompt

身份不透明，无法形成正式 Participant Snapshot，不采用。

### 本地规则拼接

无法生成自然且可编辑的整合结论，也不能复用模型安全与预算，不采用。

最终方案：固定透明 `meeting-to-action`。启动前必须通过当前执行资格和整合适格策略；失败时阻止启动，不静默切换。

UI 显示：

```text
整合由“会议行动助手（meeting-to-action）”完成
整合调用计入本次预算
```

## 11. 整合输出约束

Synthesis 固定附加约束：

```text
共识
分歧
适用条件
关键不确定性
明确建议
下一步
```

同时要求：

- 不投票裁决；
- 不将共识包装为事实；
- 不隐藏少数观点；
- 不替失败成员补写；
- 不改写参与者原意；
- 明确实际成功成员与失败成员。

## 12. 历史范围模型

新增：

```kotlin
ExecutionHistoryScope.FULL_STAGE
ExecutionHistoryScope.EXPLICIT_MESSAGES
ExecutionHistoryScope.NO_HISTORY
```

迁移规则：旧 v9 Run 一律 `FULL_STAGE`。

兼容规则：

- 既有 STANDARD 执行保持 `FULL_STAGE`；
- Directed 与 Cross 默认 `EXPLICIT_MESSAGES`；
- 显式列表为空时使用 `NO_HISTORY`；
- 空列表绝不解释成全阶段；
- Message ID 必须属于同 Issue/Stage、已完成、非 Pending、去重且稳定排序。

## 13. 实际消息使用快照

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

Hash 复用 `ContextContentHasher`：SHA-256、UTF-8、CRLF/CR→LF、不 trim、不截断。

规则：

- 保存实际发送正文；
- 后续 Message 修改不改历史快照；
- 不允许 Pending；
- 不允许其他 Issue/Stage；
- 同一 Run 不允许重复 sourceMessageId；
- Synthesis 只快照本次 Response 成功输出；
- 整合重试复制原整合 Run Usage，不回查 Stage 全文。

## 14. Schema 决策

保持 v9 无法稳定表达 Run Kind、父子关系、Discussion 状态、待整合恢复与消息正文快照，因此采用 v10。

### `execution_runs` 新字段

```text
runKind TEXT NOT NULL DEFAULT 'standard'
parentRunId TEXT NULL
 discussionId TEXT NULL
historyScope TEXT NOT NULL DEFAULT 'full_stage'
```

`retryOfRunId` 继续只表达重试；`parentRunId` 只表达非重试工作流父子关系。Synthesis 的 `parentRunId` 指向 Response Run。

### 新表

```text
cross_discussion_sessions
execution_message_usage_snapshots
```

`cross_discussion_sessions` 字段：

```text
id
issueId
stageId
triggerMessageId
responseRunId
synthesisRunId
integratorSkillId
status
idempotencyKey
successfulParticipantIdsJson
failedParticipantIdsJson
createdAt
updatedAt
failureCode
```

状态：

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

## 15. Migration

新增 `CollaborationMigration.MIGRATION_9_10`，注册到 `ALL_MIGRATIONS`。

要求：

- 旧 Run 回填 `standard` / `full_stage`；
- 新索引覆盖 `runKind`、`parentRunId`、`discussionId`；
- 新表外键全部 `RESTRICT`；
- 保留 `9.json`；
- `10.json` 由 Room 编译真实生成，不手写；
- 验证 v1→v10 至 v9→v10；
- 验证 v9 Run、预算、Participant、资料 Usage 与个人背景 Usage 完整；
- `PRAGMA foreign_key_check` 返回 0。

## 16. 原子事务

新增三个公共命令：

```text
CreateDirectedInteractionCommand
CreateCrossDiscussionResponseCommand
CreateCrossDiscussionSynthesisCommand
```

### Directed 事务

一次事务写入：

```text
用户 Message
ExecutionRun
Participant Snapshot
Participant State
Budget
Material Usage
Personal Context Usage
Message Usage
```

### Cross Response 事务

一次事务写入：

```text
用户 Message
CrossDiscussionSession
Response Run
N 个 Participant Snapshot/State
共享预算根
Material/Personal Usage
Message Usage
```

### Cross Synthesis 事务

一次事务写入：

```text
Synthesis Run
整合 Participant Snapshot/State
共享 Response 预算根
Response 成功输出 Message Usage Snapshot
Session 状态更新
```

网络调用永不进入事务。

## 17. 幂等

每个命令具有稳定 `idempotencyKey` 与完整 payload 比较。

- 相同键 + 相同 payload：返回既有事实，`idempotent=true`；
- 相同键 + 不同问题、成员、消息选择、Usage、整合范围或时间：`IdempotencyConflict`；
- 写入失败：事务回滚、零网络、零预算消费、无 Pending；
- ViewModel 操作锁与数据库唯一约束双重防护。

## 18. Coordinator 扩展

不创建第二套状态机。

新增兼容入口：

```kotlin
ExecutionRunCoordinator.startPrepared(command)
```

职责：

- 读取已原子创建 Runtime；
- 若已非 `NOT_STARTED`，只返回 Room 事实；
- 根据 Run `historyScope` 读取 Full Stage 或 Message Usage；
- 调用现有 `executeRuntime()`；
- 沿用 Pending、流式写入、预算消费、Stop、迟到回调与状态聚合；
- `keepBudgetOpenOnSuccess=true` 时，Cross Response 成功不关闭预算。

标准 `start()` 与 `retry()` 保持现有行为。

## 19. 预算

采用 Response Run 作为预算根。

初始预算必须满足：

```text
选中成员数量 + 1 个整合调用
```

创建 Response 时：

- `reservedRequiredCalls = N + 1`；
- 每位成员消费一次并保留剩余成员 + 整合调用；
- Response 成功后不关闭预算；
- 部分失败重试不返还；
- Synthesis 共享同一 rootRunId；
- Synthesis 终态成功/不可重试失败/用户停止整个讨论后关闭；
- 进程恢复不追加调用。

## 20. 部分失败

Response 全成功：Session→`AWAITING_SYNTHESIS`。

部分成功：Session→`PARTIAL_SUCCESS`，UI 展示成功成员、失败成员以及：

```text
重试失败成员
仅整合当前成功内容
停止讨论
```

只有用户明确选择“仅整合当前成功内容”才创建 Synthesis。

全部失败：Session→`FAILED`，不创建 Synthesis。

## 21. 整合失败与重试

整合失败：

- 保留 Response 输出；
- Session→`SYNTHESIS_RETRYABLE` 或 `FAILED`；
- 只重试 Synthesis；
- 不重复成员；
- 重试使用原 Synthesis Message Usage；
- 不切换整合 Skill；
- 预算不返还。

## 22. Stop

- 配置阶段取消：零持久化；
- Response 运行中：调用现有 `ExecutionRunCoordinator.stop()`，随后 Session→`STOPPED`；
- Synthesis 运行中：停止 Synthesis，保留 Response 输出，Session→`SYNTHESIS_RETRYABLE` 或 `STOPPED`；
- 未发出网络调用时也持久化可解释的 STOPPED 事实；
- 迟到回调继续由现有 `ensureAcceptsWrites()` 拒绝。

## 23. 进程恢复

恢复只读 Room，不自动联网。

可恢复状态：

```text
Directed 运行中断/失败/成功
Cross RESPONDING
Cross PARTIAL_SUCCESS
Cross AWAITING_SYNTHESIS
Cross SYNTHESIZING 中断
Cross SYNTHESIS_RETRYABLE
Cross SUCCEEDED
Cross STOPPED
Cross FAILED
```

若 Response 已完成但 Synthesis 未创建，显示“成员回应已完成，等待继续整合”，必须由用户点击。

## 24. 协作策略

新增纯策略：

```text
CurrentStageRosterPolicy
DirectedResponsePolicy
CrossDiscussionEligibilityPolicy
SynthesisSkillEligibilityPolicy
```

策略不访问 DAO、不调用网络、不记录正文。

## 25. 工作区 UI

在 `IssueExecutionRoute` 现有分层内增加：

```text
CollaborationComposer
DirectedResponseDialog
CrossDiscussionDialog
CrossDiscussionStatusCard
```

UiState 采用组合子状态，避免巨型枚举：

```text
CollaborationDraftUi
DirectedCollaborationUi
CrossDiscussionUi
```

稳定事实来自 Room，Dialog 草稿可在 ViewModel 内恢复但不作为执行事实源。

### Composer

- 本次问题输入；
- 当前阵容；
- 点名回应；
- 交叉讨论；
- 无阵容原因。

### Directed Dialog

- 精确一人；
- 当前职责；
- 本次仅该 Skill；
- 历史消息选择；
- 资料与个人背景确认入口；
- 联网说明；
- 最终确认。

### Cross Dialog

- 至少两人；
- 每人职责；
- 透明整合者；
- 只一轮；
- 预计调用 N+1；
- 历史消息选择；
- 资料与个人背景；
- 不投票裁决；
- 最终确认。

## 26. 自动化标签

在 `JianyuAutomationTags.Collaboration` 集中新增：

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

动态方法：

```text
directedParticipant(skillId)
crossParticipant(skillId)
crossMessage(messageId)
crossSession(discussionId)
```

全部通过 `normalizedStableId()`，不含中文名、问题正文或资料标题。真实生产 UI 存在并测试后加入 `frozenStaticTags`。

## 27. 无障碍与布局

- Composer 与 Dialog 使用可滚动 Column；
- 按钮最小触控尺寸沿用 Material 3；
- 状态同时使用文字与语义，不只靠颜色；
- 长问题和错误允许换行；
- 360dp 下按钮改为纵向或自适应；
- 200% 字号不使用固定高度；
- 键盘弹出时 Dialog 可滚动；
- TalkBack 顺序：焦点→成员→整合者→上下文→确认。

## 28. TDD 测试矩阵

先新增失败测试，再补生产实现。

### JVM

- Run Kind 与 History Scope；
- 当前阵容恢复；
- Snapshot 复制与资格撤回；
- Directed/Cross 配置策略；
- Context Builder Full/Explicit/None；
- Message Usage Hash/排序/边界；
- Synthesis Prompt 约束；
- 预算保留与关闭；
- 部分失败与恢复 reducer；
- 架构守卫。

### Instrumentation

- v1→v10 全连续迁移；
- v9→v10 数据保持；
- Directed 原子创建与双击；
- Cross Response 原子创建；
- 同批成员互不可见；
- 部分失败；
- 仅整合成功内容；
- Synthesis 独立重试；
- 共享预算；
- Stop 与迟到回调；
- 强停恢复不联网；
- Compose 360dp/大字体/标签。

### 外部 UIAutomator

只验证配置、标签与取消零副作用，不调用真实模型。

## 29. 架构守卫

静态测试确认：

- UI/ViewModel 不引用 DAO；
- collaboration 不引用 Retrofit、API Key 或旧 RoundtableOrchestrator；
- 唯一网络入口仍是 `ExecutionRunCoordinator`；
- 不新增 Participant/预算状态机；
- `retryOfRunId` 不表达 Synthesis；
- Run Kind 不通过文案或参与者数量推断；
- Cross 不读取 Full Stage；
- 不自动多轮。

## 30. 预计修改文件

### 新增

```text
collaboration/CollaborationModels.kt
collaboration/CollaborationPolicies.kt
collaboration/IssueCollaborationCoordinator.kt
data/CollaborationEntities.kt
data/CollaborationMigration.kt
data/CollaborationRepositoryContract.kt
data/CollaborationRepositoryComponent.kt
ui/screens/execution/IssueCollaborationComponents.kt
```

### 修改

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
data/RoomJianyuRepository.kt
data/RoundtableDatabase.kt
JianyuAppRuntime.kt
IssueExecutionRoute.kt
IssueExecutionViewModel.kt
IssueExecutionUiState.kt
IssueExecutionScreen.kt
JianyuAutomationTags.kt
相关 JVM / androidTest / Schema / 文档
```

## 31. 禁止修改

```text
首页推荐策略
44 项基础 Catalog
PR09-05B 四项资产正文
资料与个人背景生命周期语义
阶段推进/成果/音频/归档
设备工具
低 Token 工具
主题与最终视觉
旧 RoundtableOrchestrator 生产接线
```

## 32. Commit 边界

计划按以下原子意图提交，允许根据真实文件耦合合并相邻 Commit，但不夹带无关重构：

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

## 33. CI 与远端验证

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

远端 CI 不执行设备测试时，明确标记 Instrumentation 与外部 UIAutomator 等待本地 AI。

## 34. 真实 Room Schema 生成流程

1. 先提交 Entity、Migration 与数据库版本；
2. 使用分支 CI 的 Room/KSP 编译真实生成 `10.json`；
3. 将生成的 Schema 作为工件读取并提交；
4. 删除临时 Schema 生成工作流或步骤；
5. 再运行最终 CI。

不得手写或复制伪造 `10.json`。

## 35. 本地验收

创建：

```text
docs/testing/pr-09-08-local-readonly-acceptance-prompt.md
```

强制使用 `tools/local-verification/Invoke-LocalVerification.ps1`，证据放 `$env:TEMP`，禁止卸载、清数据、生产网络和真实 API Key。

## 36. 主要风险与覆盖

| 风险 | 覆盖 |
|---|---|
| Directed 改变正式阵容 | roster 查询只认 STANDARD 根 Run + 测试 |
| Cross 成员互见 | Response 使用同一预创建 Usage，忽略同批输出 + Gateway 输入测试 |
| Synthesis 读取全 Stage | `historyScope=EXPLICIT_MESSAGES` + Builder 测试 |
| 隐藏整合者 | 固定 `meeting-to-action` Snapshot + UI 标签测试 |
| 整合不计预算 | 共享 root + reserve N+1 + 事务测试 |
| 部分失败伪装完整 | Session 状态与显式按钮 + reducer 测试 |
| Message Usage 漂移 | 正文快照与 Hash + 修改后保持测试 |
| 重试关系滥用 | parentRunId/retryOfRunId 独立断言 |
| Response 成功关闭预算 | keepBudgetOpenOnSuccess 测试 |
| 恢复自动联网 | ViewModel/Coordinator 恢复测试 |
| Synthesis 重试重复成员 | 只创建 Synthesis retry + Fake Gateway 调用数 |
| 双击重复用户消息 | 单事务幂等 + 并发测试 |
| v10 破坏历史 | 连续 Migration、数据快照、foreign_key_check |
| 标签泄漏正文 | 动态 ID 校验与隐私扫描 |
| 接回旧 Orchestrator | 架构守卫 |

## 37. 回滚

- 可通过运行时开关/入口禁用协作 Composer；
- 保留合法 Message、Run、Discussion 与 Usage；
- 不删除用户内容；
- 不修改 STANDARD Run；
- v10 只能通过新前向 Migration 修复，不降级 v9；
- 不恢复旧 RoundtableOrchestrator；
- 不回滚 PR09-05B、06、07、09。

## 38. PR09-10A 交接

完成时创建 `docs/planning/pr-09-08-interface-handoff.md`，冻结：

```text
ExecutionRunKind
ExecutionHistoryScope
CreateDirectedInteractionCommand
CreateCrossDiscussionResponseCommand
CreateCrossDiscussionSynthesisCommand
CrossDiscussionSession
CrossDiscussionStatus
ExecutionMessageUsageSnapshot
CollaborationStartResult
```

PR09-10A 可读取普通、点名、Cross Response 与 Synthesis Message，并保留真实来源、分歧和 Run 关系；不得依赖 ViewModel 内存、不得自动升级成果、不得重写协作执行状态。
