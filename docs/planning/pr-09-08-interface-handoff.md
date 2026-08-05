# PR09-08 → PR09-10A 协作消息接口交接

## 1. 结论

PR09-10A 必须在 PR09-08 合并后同步最新 `main`，再基于 Room v10 和本交接接口完成阶段成果候选、草稿来源和最终工作区接线。

PR09-10A 可以读取协作事实，但不得重写协作执行状态、不得依赖 PR09-08 ViewModel 内存、不得自动把交叉整合升级为正式成果，也不得删除原参与者的分歧消息。

## 2. 基线与合并顺序

```text
PR09-08 Base：main@d3cc0aa6d61297d64280ee9be0b7adc185386d0c
PR09-08 Branch：feat/pr-09-08-directed-cross-discussion
PR09-08 Draft PR：#46
目标 Room：v10
```

顺序冻结为：

```text
PR09-08 完成、只读验收并合并
→ PR09-10A 同步最新 main
→ PR09-10A 适配 Room v10 与本接口
→ PR09-10A 完成最终 IssueExecution 工作区接线
→ 全量构建、测试、设备验收
→ PR09-10A 才可申请合并
```

不得从 PR09-08 未合并分支复制代码到 PR09-10A，也不得由 PR09-10A 反向修改 PR09-08 分支。

## 3. Room 与迁移

```text
数据库：RoundtableDatabase
版本：10
迁移：CollaborationMigration.MIGRATION_9_10
迁移链：1→2→3→4→5→6→7→8→9→10
```

v9 历史 Run 保守迁移为：

```text
runKind = STANDARD
historyScope = FULL_STAGE
parentRunId = null
discussionId = null
```

`parentRunId` 由字段、索引和 Repository 应用级关系校验维护；为保持 SQLite `ALTER TABLE` 前向迁移可执行性，没有重建 `execution_runs` 添加新的自引用外键。

PR09-10A 不得修改 v9→v10 Migration 或 `10.json`；后续 Schema 调整必须使用新的前向 Migration。

## 4. ExecutionRunKind

路径：

```text
app/src/main/java/com/elio/jianyu/data/CoreDomain.kt
```

冻结值：

```kotlin
ExecutionRunKind.STANDARD
ExecutionRunKind.DIRECTED_RESPONSE
ExecutionRunKind.CROSS_DISCUSSION_RESPONSE
ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS
```

识别规则：

- `STANDARD`：普通阶段执行；可作为正式阵容事实来源；
- `DIRECTED_RESPONSE`：本次仅一位当前阵容成员回应；不改变阵容；
- `CROSS_DISCUSSION_RESPONSE`：一轮交叉讨论的独立成员回应阶段；同批输出互不可见；
- `CROSS_DISCUSSION_SYNTHESIS`：透明整合阶段；只读取本次讨论实际成功输出和显式允许的必要上下文。

禁止通过参与者数量、Message 中文文案、Run ID 前缀或 UI 内存推断 Run Kind；必须读取 `ExecutionRunEntity.runKind`。

## 5. ExecutionHistoryScope

路径：

```text
app/src/main/java/com/elio/jianyu/data/CoreDomain.kt
```

冻结值：

```kotlin
ExecutionHistoryScope.FULL_STAGE
ExecutionHistoryScope.EXPLICIT_MESSAGES
ExecutionHistoryScope.NO_HISTORY
```

- 旧 STANDARD 默认 `FULL_STAGE`；
- Directed/Cross 默认 `EXPLICIT_MESSAGES`；
- 没有显式选择历史时必须使用 `NO_HISTORY`；
- 空显式选择不得解释成整个阶段历史。

## 6. Message 与 Run 的关系

### 用户触发 Message

Directed 与 Cross Response 的用户输入会先在原子事务中写入正式 Stage Message：

```text
Message.issueId = Run.issueId
Message.stageId = Run.stageId
Message.executionRunId = null
Message.participantSnapshotId = null
Run.triggerMessageId = Message.id
```

该 Message 是协作请求的稳定触发事实。双击相同命令只能产生一条触发 Message 和一个 Run。

### 参与者输出 Message

模型输出仍由唯一 `ExecutionRunCoordinator` 创建：

```text
Message.executionRunId = ExecutionRunEntity.id
Message.participantSnapshotId = ExecutionParticipantSnapshotEntity.id
Message.senderId = ParticipantSnapshot.sourceId
```

PR09-10A 必须保留 Message 的 `executionRunId` 与 `participantSnapshotId`，不得把整合内容伪装成某个原参与者原话。

## 7. Discussion 与 Response/Synthesis 关系

实体路径：

```text
app/src/main/java/com/elio/jianyu/data/CollaborationEntities.kt
```

核心实体：

```kotlin
CrossDiscussionSessionEntity
CrossDiscussionStatus
```

关系：

```text
CrossDiscussionSession.id
    ├── ExecutionRunEntity.discussionId
    ├── responseRunId → CROSS_DISCUSSION_RESPONSE 根 Run
    └── synthesisRunId → 当前 Synthesis Run（包含整合重试时的最新 Run）
```

Synthesis 与 Response 的非重试父子关系：

```text
SynthesisRun.parentRunId = ResponseRootRun.id
SynthesisRun.retryOfRunId = null
```

Synthesis 重试：

```text
RetrySynthesis.runKind = CROSS_DISCUSSION_SYNTHESIS
RetrySynthesis.retryOfRunId = PreviousSynthesisRun.id
RetrySynthesis.parentRunId = ResponseRootRun.id
RetrySynthesis.discussionId = Session.id
```

Response 失败成员重试：

```text
RetryResponse.runKind = CROSS_DISCUSSION_RESPONSE
RetryResponse.retryOfRunId = PreviousResponseRun.id
RetryResponse.discussionId = Session.id
```

只重试未成功成员，已成功成员不重新调用；整合 Repository 会按正式根阵容的稳定 `sourceId` 聚合所有 Response/Retry Run 的实际成功输出。

## 8. CrossDiscussionStatus

冻结状态：

```kotlin
RESPONDING
PARTIAL_SUCCESS
AWAITING_SYNTHESIS
SYNTHESIZING
SYNTHESIS_RETRYABLE
SUCCEEDED
STOPPED
FAILED
```

语义：

- `RESPONDING`：成员独立回应中或失败成员重试中；
- `PARTIAL_SUCCESS`：至少一位成功、至少一位未成功；不得自动整合；
- `AWAITING_SYNTHESIS`：全部成员成功，进程恢复后等待用户明确继续；
- `SYNTHESIZING`：透明整合运行中；
- `SYNTHESIS_RETRYABLE`：成员输出保留，只允许重试整合；
- `SUCCEEDED`：整合成功；
- `STOPPED`：用户停止；不自动联网；
- `FAILED`：当前阶段无可继续的自动动作，需用户显式处理。

`successfulParticipantIdsJson` 与 `failedParticipantIdsJson` 保存稳定官方 Skill ID，不保存显示名或 Participant Snapshot ID。

## 9. ExecutionMessageUsageSnapshot

表与实体：

```text
execution_message_usage_snapshots
ExecutionMessageUsageSnapshotEntity
```

路径：

```text
app/src/main/java/com/elio/jianyu/data/CollaborationEntities.kt
```

字段：

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

- `contentSnapshot` 是模型实际看到的正文；
- `contentHash` 使用 `ContextContentHasher`，UTF-8、CRLF/CR→LF、不 trim、不截断；
- 后续 Message 更新不会改变历史快照；
- Pending、其他 Issue、其他 Stage、重复 Message 被拒绝；
- Directed/Cross Response 只快照用户显式选择的历史消息；
- Synthesis 快照本次讨论实际成功输出，以及用户为整合额外显式选择的必要消息；
- Synthesis retry 复制原 Usage，不回查当前阶段全文。

不得把模型输出塞入 Material/Personal Context Usage 或 `ExecutionContextContribution` 伪装成资料。

## 10. PR09-10A 可安全读取的接口

### Repository 公共接口

```kotlin
JianyuRepository.recoverIssue(issueId)
JianyuRepository.getExecutionRuntime(runId)
JianyuRepository.getStageCollaboration(stageId)
JianyuRepository.listExecutionMessageUsage(runId)
JianyuRepository.listRunContextUsage(runId)
```

相关路径：

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/CollaborationRepositoryContract.kt
```

### Recovery 读取

`recoverIssue()` 可读取：

```text
Issue
Stage
ExecutionRun
Participant Snapshot
Participant State
Message
Material/Personal Usage
Draft/Artifact/Audio 等既有资源
```

`getStageCollaboration()` 可读取 Discussion 与 Response/Synthesis 的 Message Usage 索引。

`listExecutionMessageUsage(runId)` 返回按 `usageOrder` 排序的不可变实际消息快照。

## 11. PR09-10A 可作为阶段总结候选的内容

可候选：

- `STANDARD` 成功参与者输出；
- `DIRECTED_RESPONSE` 成功输出；
- `CROSS_DISCUSSION_RESPONSE` 各成员原始输出；
- `CROSS_DISCUSSION_SYNTHESIS` 成功整合输出。

但必须：

- 保留原参与者 Message；
- 保留分歧来源；
- 草稿来源绑定真实 `Message.id`、`executionRunId` 和 `participantSnapshotId`；
- 交叉整合只是候选输入，不自动升级为 Confirmed Artifact；
- 部分成功的整合必须保留未成功成员清单；
- STOPPED/FAILED/RETRYABLE 的不完整输出不得伪装成完整结论。

## 12. 不得依赖的 UI 内存

PR09-10A 不得依赖：

```text
IssueCollaborationViewModel
IssueCollaborationUiState
SavedStateHandle 中的协作 input / dialog / selected IDs / operationId
Compose Dialog 是否打开
Snackbar 或一次性事件
当前页面是否仍在前台
```

这些只保存配置草稿和展示状态，不是执行事实源。

## 13. 进程恢复

恢复原则：

- 只读取 Room；
- 不自动调用网络；
- 不自动重试；
- 不自动开始 Synthesis；
- 不重复写 Message、Run、Participant 或 Usage。

若 Response 全成功但 Synthesis 尚未开始：

```text
Session.status = AWAITING_SYNTHESIS
UI 显示“成员回应已完成，等待继续整合”
用户点击后才联网
```

## 14. Hash 与隐私

- Message Usage Hash：`ContextContentHasher.hash(actualContent)`；
- Material/Personal Context Hash 沿用 PR09-09；
- 日志、异常码、自动化标签和证据文件名不得包含问题正文、消息正文、资料正文、个人背景正文、完整 Prompt 或 API Key；
- 动态 UI 标签只使用 `JianyuAutomationTags.normalizedStableId()`。

## 15. 幂等键

稳定 operationId 派生：

```text
directed-<operationId>
cross-response-<operationId>
cross-discussion-<operationId>
cross-synthesis-<operationId>
collaboration-retry-<operationId>
```

同键同 payload 返回既有事实；同键不同 payload 返回 `RepositoryError.IdempotencyConflict`。

PR09-10A 不得解析幂等键推断 Run Kind 或 Discussion 关系。

## 16. 文件所有权

PR09-08 最终独占和实际修改的共享范围包括：

```text
app/src/main/java/com/elio/jianyu/collaboration/
app/src/main/java/com/elio/jianyu/data/Collaboration*.kt
app/src/main/java/com/elio/jianyu/data/CrossDiscussionSynthesisRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/CoreDomain.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/data/LegacyDatabaseMigrationSupport.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionContextBuilder.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionPersistenceGateway.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueCollaboration*.kt
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt
app/src/main/java/com/elio/jianyu/ui/App.kt
app/schemas/com.elio.jianyu.data.RoundtableDatabase/10.json
```

PR09-10A 在 PR09-08 合并前不得修改上述文件。PR09-08 合并后，PR09-10A 只能基于最新 `main` 做必要适配，不得复制旧分支版本覆盖。
