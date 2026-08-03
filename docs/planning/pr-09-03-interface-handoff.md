# PR09-03：Repository 与恢复接口交接

## 一、状态与精确基线

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base：main@c8fda6979f07c619cd71210a0d58841adc9bfd88
开发分支：feat/pr-09-03-jianyu-repository-recovery
Draft PR：#34
公共契约首次定义 Commit：1e80dc1b2e687e5fa2eb2283634f1f50da94b6e2
Pending 消息 CAS 公共接口 Commit：fdbc398b761bb0706f9f92656d992a14bd39044f
恢复成员稳定标识扩展 Commit：f243deb41905460a76a044c369c8170635059da6
当前代码冻结 Commit：496895d3f35f2eca95fcccff4deacc9093be4315
PR09-03 合并 Commit：不存在；PR #34 当前仍为 Draft，未合并
```

本文件冻结 PR09-03 的公共接口和文件所有权。PR #34 合并前不得启动 PR09-04 或 PR09-05；合并后必须从当时最新稳定 `main` 创建两个独立分支，并补记 PR09-03 实际合并 Commit。

## 二、最终 Repository 架构

```text
JianyuRepository（唯一公共业务数据门面）
        ↓
RoomJianyuRepository（纯委托门面）
        ↓
IssueExecutionRepositoryComponent
PendingMessageRepositoryComponent
ResourceRepositoryComponent
UsageRepositoryComponent
LifecycleRecoveryRepositoryComponent
        ↓
JianyuRepositoryTransactions（唯一事务与错误映射协调器）
        ↓
JianyuRepositoryDao
        ↓
RoundtableDatabase v7
```

冻结约束：

1. ViewModel、页面、导航和 Skill Catalog 不得直接访问 `CoreDomainDao`、`ResourceLifecycleDao` 或 `JianyuRepositoryDao`；
2. 新见域业务写入只能通过 `JianyuRepository`；
3. 所有跨表业务操作使用 `JianyuRepositoryTransactions` 与 `RoundtableDatabase.withTransaction`；
4. Repository 不调用网络、Gemini、WorkManager、遥测或真实文件删除；
5. `CancellationException` 原样向上传播；
6. Room 继续为 v7，PR09-04/05 不得修改 Entity、Migration 或 Schema。

## 三、公共写入与恢复接口

### 3.1 议题与阶段

```kotlin
suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<SavedIssue>
suspend fun createStage(command: CreateStageCommand): RepositoryResult<StageEntity>
suspend fun undoLatestUnrunStage(issueId: String, stageId: String): RepositoryResult<Unit>
```

- `saveIssue` 只创建 Issue、初始 Stage 和 active Lifecycle；
- 不创建 Run、参与者、ChatSession、资料、草稿、成果或音频；
- `legacy-chat-` 为迁移专用 Issue ID 前缀，新议题不得占用；
- Stage 顺序由数据库事务内 `MAX(sequenceIndex) + 1` 计算，唯一索引兜底；
- 只能撤销最新、非初始、且没有任何运行或资源依赖的 Stage。

### 3.2 Run 与成员快照

```kotlin
suspend fun createExecutionRun(
    command: CreateExecutionRunCommand
): RepositoryResult<ExecutionRunSnapshot>

suspend fun transitionRun(
    command: TransitionRunCommand
): RepositoryResult<ExecutionRunEntity>
```

- Run 与参与者快照同事务；
- `idempotencyKey` 是创建幂等键；
- 重试创建不受后续 Run 状态变化影响；
- 状态更新使用 compare-and-set，不无条件覆盖新状态；
- Repository 不决定模型调用、预算或网络重试。

### 3.3 领域消息与 Pending CAS

```kotlin
suspend fun appendDomainMessage(
    command: AppendDomainMessageCommand
): RepositoryResult<Message>

suspend fun updatePendingDomainMessage(
    command: UpdatePendingDomainMessageCommand
): RepositoryResult<Message>
```

- 新领域 Message 使用稳定正数 ID 和 `OnConflictStrategy.ABORT`；
- Issue / Stage / Run / ParticipantSnapshot 关系必须一致；
- 用户消息可不关联参与者，成员消息必须关联正确参与者；
- `roundIndex` 只表示响应批次；
- Pending 原位更新使用数据库 CAS，只允许更新仍为 Pending 的同一领域消息；
- 消息完成后，重复相同完成请求幂等；迟到片段不得覆盖成功正文。

### 3.4 草稿、成果、资料与背景

```kotlin
suspend fun saveStageDraft(command: SaveStageDraftCommand)
suspend fun abandonStageDraft(issueId: String, stageId: String)
suspend fun confirmArtifact(command: ConfirmArtifactCommand)
suspend fun recordMaterialUsage(entity: MaterialUsageSnapshotEntity)
suspend fun recordPersonalContextUsage(entity: PersonalContextUsageSnapshotEntity)
```

- 当前草稿与不可变修订同事务；
- 放弃只删除当前草稿；
- 成果及消息、Run、草稿修订和资料来源同事务；
- 资料和个人背景只有 `userConfirmedAt > 0` 才能记录；
- 资料引用若仍存在，必须属于同一 Issue，Stage 级引用必须匹配 Stage；
- 当前对象编辑或删除不改写历史使用快照。

### 3.5 生命周期与恢复

```kotlin
suspend fun archiveIssue(issueId: String, changedAt: Long)
suspend fun restoreIssue(issueId: String, changedAt: Long)
suspend fun moveIssueToTrash(issueId: String, changedAt: Long)
suspend fun restoreIssueFromTrash(issueId: String, changedAt: Long)
suspend fun requestIssuePurge(issueId: String, requestedAt: Long)
suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot>
```

`requestPurge` 只记录请求，不删除数据库行、文件或停止 Run。恢复读取不创建数据、不更新时间戳、不改变 Run 状态、不删除 Pending。

恢复数据提供：

- Issue、Lifecycle、Stage 和当前 Stage；
- 全部 Run、活跃/可恢复 Run；
- 参与者快照；
- Message 和 Pending Message；
- 草稿、修订、成果；
- 资料/背景使用快照；
- 音频资产元数据；
- `successfulParticipantSnapshotIds()`；
- `retryableParticipantSnapshotIds()`。

## 四、PR09-04 导航壳可消费接口

### 4.1 Issue 导航列表

```kotlin
suspend fun listIssueNavigation(
    states: Set<IssueLifecycleState> = setOf(IssueLifecycleState.ACTIVE)
): RepositoryResult<List<IssueNavigationItem>>
```

`IssueNavigationItem` 提供稳定 Issue ID、标题、生命周期、当前 Stage 和活跃/可恢复 Run 数量。导航壳不得自行拼接 DAO。

### 4.2 深链和恢复定位

PR09-04 可通过 `recoverIssue(issueId)` 稳定定位 Issue、Stage、Run 与生命周期。该入口只读，不得用恢复动作隐式创建 Stage 或 Run。

### 4.3 PR09-04 独占文件

PR09-04 可独占：

- 根导航图；
- Route 定义；
- App 入口和导航 Host；
- 返回栈、深链和导航恢复测试；
- 导航壳占位页面。

PR09-04 不得修改 Repository、Database、Entity、DAO、Room Schema 或正式 Skill Catalog。

## 五、PR09-05 Skill 目录可消费接口

### 5.1 官方组合读写

```kotlin
suspend fun listOfficialSkillCombinations()
suspend fun getOfficialSkillCombination(combinationId: String)
suspend fun saveOfficialSkillCombination(command: SaveOfficialSkillCombinationCommand)
suspend fun deleteOfficialSkillCombination(command: DeleteOfficialSkillCombinationCommand)
```

组合成员按 position 稳定排序；重复 Skill ID 和重复 position 被拒绝；更新和删除使用 `expectedUpdatedAt`；删除为软删除，不修改历史 Run 快照。

### 5.2 正式官方 Skill ID 校验

```kotlin
fun interface OfficialSkillIdValidator {
    suspend fun isValid(officialSkillId: String): Boolean
}
```

当前默认 `RejectingOfficialSkillIdValidator` 拒绝所有未知 ID。PR09-05 必须注入正式 Catalog 的本地校验实现。禁止生产环境“全部允许”实现，也禁止在数据库事务中做网络校验。

### 5.3 PR09-05 独占文件

PR09-05 可独占：

- 正式 Skill Catalog 数据源；
- Skill 列表、详情、检索和分类；
- 官方组合展示；
- 正式 `OfficialSkillIdValidator` 实现及测试。

PR09-05 不得修改 Repository、Database、Entity、DAO、Room Schema、根导航或执行调度状态机。

## 六、旧 ChatRepository 兼容边界

旧 `ChatRepository` 继续负责：

- 旧 ChatSession；
- 旧 UI 消息流；
- 旧 Pending 更新和清理；
- 旧 Message 音频字段；
- 旧 Session 删除与真实音频文件删除。

新 `JianyuRepository` 负责所有新增 Issue、Stage、Run、领域 Message、Pending CAS、草稿、成果、使用快照、组合、生命周期和恢复。

当前 `Message.chatId` 非空，因此首次新领域消息写入会在同一事务中按需创建兼容 ChatSession，并写入 `Issue.legacyChatSessionId`。Issue 始终是新领域事实源。

为防止旧链破坏恢复数据，已冻结以下隔离：

1. 新领域兼容 Session 不出现在旧会话列表；
2. 旧 `deleteSession` 对新领域兼容 Session 直接拒绝；
3. 旧 Pending 更新和清理不作用于新领域兼容 Session；
4. 旧消息删除、会话改名和旧音频字段更新不作用于新领域兼容 Session；
5. 启动时旧 Pending 清理不会删除新领域 Pending。

兼容 Session 最早在 PR09-07 新执行链稳定、且独立 Schema PR 获准解除 `Message.chatId` 强依赖后删除。真实音频迁移最早由 PR09-10B 处理；永久清理由 PR09-12 处理。

## 七、PR09-04 / PR09-05 共享禁止修改区

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/PendingMessageRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/UsageRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/ChatSession.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/schemas/
```

任一后续任务确需修改上述共享区时，必须停止并行、报告具体缺口，并通过独立的 PR09-03 后续修正 PR 串行处理。

## 八、并行启动门禁

只有全部满足后，PR09-04 与 PR09-05 才可有限并行：

1. PR #34 完成本地 AI 严格只读验收；
2. 用户明确授权标记 Ready；
3. Ready 状态触发的最新 CI 通过；
4. 用户授权并实际合并 PR #34；
5. 记录 PR09-03 实际合并 Commit SHA；
6. 两个新分支均从最新稳定 `main` 创建；
7. 开放 PR、近期 Commit 和文件所有权重新核验无冲突。
