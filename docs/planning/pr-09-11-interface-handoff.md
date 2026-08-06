# PR09-11 → PR09-12 接口交接：推进议题与阶段继承

## 1. 适用范围

本文冻结 PR09-11 合并后 PR09-12 可以依赖的数据、Repository、工作区和生命周期边界。PR09-12 不得回退 Room 版本，不得重建平行的 Stage 当前节点或阵容事实源，也不得物理删除已经产生业务事实的 Stage。

## 2. Room 版本与迁移

- 数据库版本：`11`
- 新迁移：`StageAdvancementMigration.MIGRATION_10_11`
- 连续迁移链：`1→2→3→4→5→6→7→8→9→10→11`
- 新 Schema：`app/schemas/com.elio.jianyu.data.RoundtableDatabase/11.json`
- 迁移只创建新表和索引，不改写历史 Stage，不为 v10 Stage 伪造推进方向或继承关系。
- PR09-12 只能新增前向迁移，例如 `11→12`；禁止降级到 v10、删除历史 Schema 或使用 destructive migration。

## 3. Advancement 实体

### 3.1 `StageAdvancementEntity`

一条记录对应一个由“推进议题”创建的新 Stage，冻结：

- `stageId`：新 Stage；
- `issueId`：所属 Issue；
- `sourceStageId`：推进来源 Stage；
- `operationId`：稳定幂等键；
- `payloadHash`：规范化用户 payload 的 SHA-256；
- `realitySupport` / `thinkingExpansion`：推进方向；
- `objective`：用户确认的新阶段目标；
- `expectedOutput`：预期输出；
- `confirmedAt` / `createdAt`：确认与创建时间。

`confirmedAt` 是提交元数据，不参与 payloadHash。相同 `operationId`、相同业务内容即使恢复后时间戳变化，也必须返回既有 Stage；相同 `operationId`、不同业务内容返回 `IdempotencyConflict`。

### 3.2 子关系

- `StageAdvancementMeasureEntity`：稳定措施枚举和位置；
- `StageAdvancementSkillMemberEntity`：本阶段计划阵容；
- `StageAdvancementMaterialEntity`：继承 Material 稳定 ID；
- `StageAdvancementArtifactEntity`：继承正式 Artifact 稳定 ID。

所有子关系依赖 Advancement 根记录。Material 和 Artifact 只建立关系，不复制正文、Revision、Prompt 或授权状态。

## 4. 当前 Stage 规则

当前 Stage 仍由 Issue 下最高 `sequenceIndex` 的 Stage 决定：

```text
ORDER BY sequenceIndex DESC, id DESC LIMIT 1
```

规则：

1. “推进议题”只能从当前最新 Stage 创建新 Stage；
2. 查看历史 Stage 不改变当前节点；
3. 在历史 Stage 页面确认推进会被拒绝；
4. 创建成功后新 Stage 成为当前节点；
5. 撤销最新未运行 Stage 后，前一个 Stage 自动恢复为当前显示节点；
6. PR09-12 的归档、清理和恢复不得另建 `currentStageId` 平行事实源。

## 5. 原子命令与 Repository

正式命令：

```kotlin
AdvanceIssueCommand
AdvanceIssueResult
```

公共扩展：

```kotlin
suspend fun JianyuRepository.advanceIssue(command: AdvanceIssueCommand)
suspend fun JianyuRepository.getStageAdvancement(stageId: String)
suspend fun JianyuRepository.listStageAdvancements(issueId: String)
```

生产实现：

```text
RoomJianyuRepository
→ StageAdvancementRepositoryComponent
→ JianyuRepositoryTransactions.stageAdvancementTransaction
→ StageAdvancementDao
```

单事务写入：

1. `StageEntity`；
2. `StageAdvancementEntity`；
3. Measures；
4. Skill 计划阵容；
5. Material 继承关系；
6. Artifact 继承关系。

任一校验或写入失败时事务整体回滚。事务内没有网络调用、模型调用、Run、Pending Message、Draft、Artifact、AudioAsset 或预算消耗。

## 6. 阶段继承关系

### 6.1 Material

- 继承 `MaterialReferenceEntity.id`；
- 必须属于同一 Issue、处于 `ACTIVE` 且未 purge；
- 不复制 Material；
- 不继承 `networkAllowed`、敏感确认或上次执行选中状态；
- 新 Stage 每次执行仍走既有 Context 确认流程。

### 6.2 Artifact

- 只继承正式 `ConfirmedArtifactEntity.id`；
- 必须属于同一 Issue；
- 不复制 Artifact；
- 不覆盖 Revision 链；
- Draft 不作为 Artifact 继承，也不会自动确认。

### 6.3 Personal Context

Advancement Schema 没有 Personal Context 继承表。个人背景继续默认不选择；PR09-12 不得通过归档恢复逻辑隐式恢复上次选择、网络授权或敏感确认。

## 7. Stage 计划阵容

统一读取策略由 `CurrentStageRosterPolicy.resolveSource` 冻结：

```text
当前 Stage 存在最新 STANDARD 根 Run
→ 使用该 Run 的 Participant Snapshot

当前 Stage 没有 STANDARD 根 Run，但有 Advancement 计划阵容
→ 使用 StageAdvancementSkillMemberEntity

两者都不存在
→ CurrentStageRosterSource.NoRoster
```

限制：

- Directed Run、Cross Run、Retry Run 不改变长期阵容；
- 不创建假的 `NOT_STARTED` Run 存阵容；
- 不从全部 Catalog 自动补成员；
- 不从中文名称推断 Skill ID；
- 计划阵容保留官方 Skill ID、位置、责任、原始 STANDARD Snapshot 或 Catalog 版本依据；
- 新 Stage 尚未运行时，可继续沿用计划阵容记录的祖先 STANDARD Snapshot 依据；
- 新执行前必须再次校验官方 Skill 当前资格；资格被撤回时，不静默替换，用户需在第二步调整阵容。

PR09-12 如需展示、归档或清理阵容，必须继续使用该公共策略，不得新建第二套 roster provider。

## 8. 活动运行处理

最终创建前 Repository 会检查来源 Stage：

- `ExecutionRunStatus.NOT_STARTED` / `RUNNING`；
- Cross Discussion `RESPONDING` / `SYNTHESIZING`；
- Pending Message。

工作区行为：

1. “推进议题”入口可以打开；
2. 不自动 Stop；
3. 用户可以等待、取消或明确选择“停止当前运行后推进”；
4. 停止动作复用 `IssueExecutionViewModel.stop()` 与 `IssueCollaborationViewModel.stop()`；
5. 等待所有运行终态持久化；
6. 重新读取 Room；
7. 旧摘要确认失效，用户必须再次确认；
8. 才允许原子创建 Stage。

`AWAITING_SYNTHESIS`、`PARTIAL_SUCCESS` 可以保留在旧 Stage，推进不会自动 Synthesis。旧 Stage 的迟到回调仍受既有 runId/stageId 和终态门禁约束，不得修改新 Stage。

## 9. 未运行 Stage 撤销

公共入口沿用：

```kotlin
JianyuRepository.undoLatestUnrunStage(issueId, stageId)
```

生产实现由 `StageAdvancementRepositoryComponent` 接管。只有同时满足以下条件才可物理删除：

- 是当前 Issue 最新 Stage；
- `sequenceIndex > 0`；
- 存在 Advancement 根记录；
- 没有任何 Run；
- 没有 Message；
- 没有 Draft；
- 没有 Draft Revision；
- 没有 Artifact；
- 没有 Material Reference；
- 没有 Material Usage；
- 没有 Personal Context Usage；
- 没有 AudioAsset；
- 没有 Discussion；
- 没有 Message Usage。

撤销事务顺序：

1. 删除 Advancement Measure；
2. 删除计划阵容；
3. 删除 Material 继承关系；
4. 删除 Artifact 继承关系；
5. 删除 Advancement 根；
6. 删除 Stage。

Stage 一旦创建过任何 Run，即使 Run 失败或停止，也永久失去“未运行撤销”资格。PR09-12 必须使用正式归档/生命周期能力处理后续修改，不能物理删除。

## 10. IssueExecution 工作区

PR09-11 继续使用单一工作区：

```text
IssueExecutionRoute
├─ StageTimeline
├─ IssueExecutionScreen
└─ AdvanceIssueFlow
```

- `StageTimeline` 区分当前和历史 Stage；
- “推进议题”始终位于该工作区；
- 三步流程使用 Dialog，不建立第二个顶层页面状态机；
- `AdvanceIssueViewModel` 通过 `SavedStateHandle` 保存未确认表单；
- 进程恢复只恢复表单，`confirmedRevision` 不恢复，不会自动创建 Stage；
- 一次性导航与 Stop 请求通过 `AdvanceIssueEvent` 发送；
- 最终确认双击由 `CreatingStage` 操作锁和数据库幂等共同防护。

## 11. 自动化标签

中央标签位于：

```text
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
```

分组：

```text
JianyuAutomationTags.AdvanceIssue
JianyuAutomationTags.StageTimeline
```

动态标签必须调用：

```kotlin
JianyuAutomationTags.normalizedStableId(...)
```

禁止使用标题、正文、姓名或 Prompt 生成标签。

## 12. PR09-12 清理与归档约束

PR09-12 必须保留：

- Stage 与 source Stage 的 Advancement 关系；
- operationId 和 payloadHash；
- Measures 顺序；
- Stage 计划阵容及其来源依据；
- Material/Artifact 稳定引用；
- 已产生业务事实的 Stage；
- 所有 v1～v11 Schema 和连续迁移；
- 当前 Stage 判定规则；
- 未运行撤销的严格门禁。

允许的前向能力包括归档、恢复、显示过滤、生命周期标记和前向 Migration；不允许把 Advancement 编码回 `StageEntity.title/objective`，不允许复制上下文正文，也不允许物理降级 Room。
