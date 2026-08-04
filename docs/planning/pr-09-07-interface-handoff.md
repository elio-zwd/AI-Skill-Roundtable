# PR09-07 → PR09-09 执行接口交接

## 1. 目标

PR09-09 只负责把用户明确确认的资料与个人背景转换为执行上下文贡献，不得重写 ExecutionRun 状态机、预算、网络调用、参与者重试或 Room v8 运行表。

## 2. 稳定接口

```text
ExecutionStartCommand
ExecutionContextContribution
ExecutionRunCoordinator
ExecutionRecoverySnapshot
ExecutionParticipantResult
ExecutionError
ExecutionBudgetSnapshot
```

代码位置：

```text
app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionContextBuilder.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
```

## 3. ExecutionContextContribution

字段语义：

```text
sourceId：稳定来源 ID；不得使用展示标题代替。
sourceType：稳定来源类型，例如 material、personal_context。
content：用户明确确认允许进入本次请求的文本快照。
contentHash：对实际使用 content 计算的稳定哈希，用于审计快照。
userConfirmedAt：用户确认该内容用于本次执行的时间。
networkAllowed：用户是否明确允许该内容发送到模型服务。
sensitive：调用方标记的敏感级别；不得写入日志。
```

约束：

- `sourceId`、`sourceType`、`contentHash` 非空；
- `userConfirmedAt > 0`；
- 未经用户确认不得构造贡献；
- `networkAllowed=false` 的贡献不得传给生产网络 Gateway；
- `sensitive=true` 的正文不得进入日志、异常、遥测或普通 UI 调试信息；
- 数据库使用快照表记录实际来源、哈希和确认时间，不保存 API Key；
- 资料删除或编辑不得改写已经冻结的历史贡献。

## 4. 上下文顺序

`ExecutionContextBuilder` 使用固定顺序：

```text
1. 冻结 Skill System Prompt（独立 systemInstruction）
2. Issue 标题
3. 当前 Stage 标题与目标
4. roundIndex（响应批次，不是 Stage sequenceIndex）
5. 组合默认职责（仅作为本组合关注点）
6. 当前阶段既有、已完成的历史 Message
7. 用户明确确认的 ExecutionContextContribution
8. 当前用户问题
9. 独立作答约束
```

PR09-09 不得：

- 把默认职责替换为 System Prompt；
- 自动读取同一批次其他成员的输出；
- 自动注入全部资料库或全部个人背景；
- 使用 Catalog 当前 Prompt 改写历史快照；
- 把 Stage `sequenceIndex` 当作 `roundIndex`；
- 直接调用网络或 DAO。

## 5. 贡献排序

PR09-09 在传入 Coordinator 前必须确定性排序：

```text
1. 用户确认顺序；
2. 相同确认时间按 sourceType；
3. 再按 sourceId。
```

同一来源在同一次执行命令中只允许一个内容哈希版本。重复 `sourceType + sourceId` 且哈希不同必须拒绝，不得静默覆盖。

## 6. 最大上下文边界

当前 `ExecutionContextInput.maxContextCharacters` 默认：

```text
24_000 characters
```

PR09-09 应在进入 Builder 前完成：

- 资料选择数量限制；
- 单项内容限制；
- 总字符预算计算；
- 超限时向用户展示明确选择或缩减结果。

不得在 Builder 超限后静默截断正文。当前 Builder 会拒绝超过稳定边界的输入。

## 7. 错误隔离

资料或个人背景读取失败时：

- 不得破坏 ExecutionRun 状态机；
- 不得自动改为“不带资料继续执行”；
- 用户确认前不得调用网络；
- 使用稳定资料错误状态，由调用 UI 提示重新选择或移除失败来源；
- 不把资料正文写入错误摘要；
- 不把资料错误映射成模型 `empty_response`。

执行层稳定错误由 `ExecutionError` / `ExecutionErrorCode` 表达；PR09-09 不新增第二套执行错误枚举。

## 8. 网络允许门禁

调用 `ExecutionRunCoordinator.start()` 或 `retry()` 前，调用方必须确认：

```text
contributions.all { it.networkAllowed }
```

若存在不允许联网的贡献：

- 不创建 Run；
- 不创建 Pending；
- 不消费预算；
- 不调用网络；
- UI 明确提示用户重新确认或移除该来源。

## 9. 使用快照

PR09-09 应复用现有：

```text
MaterialUsageSnapshotEntity
PersonalContextUsageSnapshotEntity
```

记录至少包括：

- Issue ID；
- Stage ID；
- ExecutionRun ID；
- 来源 ID；
- 内容哈希；
- 用户确认时间；
- 实际使用时间；
- 是否允许联网；
- 敏感标记。

如果现有 Schema 字段不足，只允许 PR09-09 做资料使用快照所需的最小连续迁移；不得修改 `execution_participant_states`、`execution_run_budgets` 或状态转换语义。

## 10. 恢复读取

`ExecutionRecoverySnapshot` 提供：

```text
runId
runStatus
participants
budget
requiresExplicitRetry
```

`ExecutionParticipantResult` 提供：

```text
participantSnapshotId
status
attemptCount
outputMessageId
hasIncompleteOutput
error
```

PR09-09 可结合资料使用快照展示“本次实际使用了哪些资料/背景”，但不得：

- 自动重发模型请求；
- 返还已消费或预留预算；
- 改写成功成员结果；
- 使用当前资料内容替换历史内容哈希；
- 直接更新参与者运行状态。

## 11. 禁止依赖

PR09-09 不得依赖：

```text
JianyuRepositoryDao
RoundtableDatabaseGateway
RoundtableOrchestrator
InteractionStreamingClient
ApiKeyScheduler
RetrofitClient
```

允许依赖：

```text
JianyuRepository 公共资料/使用快照接口
ExecutionContextContribution
ExecutionStartCommand
ExecutionRunCoordinator
ExecutionRecoverySnapshot
```

## 12. 兼容与演进

- 新增上下文来源类型时保持 `ExecutionContextContribution` 的字段语义不变；
- 新增字段优先使用向后兼容默认值；
- 不改变现有贡献顺序；
- 不删除 `contentHash`、`userConfirmedAt`、`networkAllowed` 或 `sensitive`；
- 不把正文加入 `toString()`、日志或错误；
- 所有执行状态与预算仍以 Room v8 运行表为唯一事实源。
