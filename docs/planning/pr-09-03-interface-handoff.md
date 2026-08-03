# PR09-03：Repository 与恢复接口交接

## 一、状态与精确基线

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base：main@c8fda6979f07c619cd71210a0d58841adc9bfd88
开发分支：feat/pr-09-03-jianyu-repository-recovery
Draft PR：#34
公共契约首次定义 Commit：1e80dc1b2e687e5fa2eb2283634f1f50da94b6e2
恢复成员稳定标识扩展 Commit：f243deb41905460a76a044c369c8170635059da6
PR09-03 合并 Commit：不存在；PR #34 当前仍为 Draft，未合并
```

本交接文件记录 PR09-03 已冻结的消费边界。PR #34 合并前不得启动 PR09-04 或 PR09-05；合并后创建两个任务分支时，必须从当时最新稳定 `main` 开始，并记录 PR09-03 的实际合并 Commit。

## 二、最终 Repository 架构

```text
JianyuRepository（唯一公共业务数据门面）
        ↓
RoomJianyuRepository（纯委托门面）
        ↓
IssueExecutionRepositoryComponent
ResourceRepositoryComponent
LifecycleRecoveryRepositoryComponent
        ↓
JianyuRepositoryTransactions（唯一事务与错误映射协调器）
        ↓
JianyuRepositoryDao
        ↓
RoundtableDatabase v7
```

约束：

1. ViewModel、页面、导航和 Skill Catalog 不得直接访问 `CoreDomainDao`、`ResourceLifecycleDao` 或 `JianyuRepositoryDao`；
2. 跨表业务写入只能通过 `JianyuRepository`；
3. 事务协调器不得调用网络、Gemini、WorkManager、遥测或真实文件删除；
4. 协程 `CancellationException` 必须原样向上传播；
5. Room 继续为 v7，不允许 PR09-04/05 修改 Entity、Migration 或 Schema。

## 三、PR09-04 导航壳可消费接口

### 3.1 Issue 导航列表

```kotlin
suspend fun listIssueNavigation(
    states: Set<IssueLifecycleState> = setOf(IssueLifecycleState.ACTIVE)
): RepositoryResult<List<IssueNavigationItem>>
```

`IssueNavigationItem` 提供：

- 稳定 `Issue.id`；
- Issue 标题与更新时间；
- `IssueLifecycleEntity.state`；
- 当前 Stage；
- 活跃或可恢复 Run 数量。

导航壳只能读取该接口，不得自行拼接 Issue、Stage、Run 和 Lifecycle DAO。

### 3.2 深链与恢复目标定位

```kotlin
suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot>
```

可稳定定位：

- Issue ID；
- Stage ID 与顺序；
- 当前 Stage；
- Run ID；
- 生命周期状态；
- Pending Message；
- 草稿与成果存在性。

恢复入口只读，不创建 Stage/Run，不更新时间戳，不改变 Run 状态，不删除 Pending。

### 3.3 生命周期入口

```kotlin
suspend fun archiveIssue(issueId: String, changedAt: Long)
suspend fun restoreIssue(issueId: String, changedAt: Long)
suspend fun moveIssueToTrash(issueId: String, changedAt: Long)
suspend fun restoreIssueFromTrash(issueId: String, changedAt: Long)
suspend fun requestIssuePurge(issueId: String, requestedAt: Long)
```

PR09-04 仅可接入归档、回收站导航和只读定位；不得实现永久删除、文件删除、自动过期、后台清空或运行中归档决策。

### 3.4 PR09-04 独占文件

PR09-04 可独占：

- 根导航图；
- Route 定义；
- App 入口和导航 Host；
- 返回栈、深链和导航恢复测试；
- 仅用于导航壳的占位页面。

PR09-04 禁止修改：

- `JianyuRepositoryContract.kt`；
- `RoomJianyuRepository.kt`；
- 三个内部 Repository 组件；
- `JianyuRepositoryTransactions.kt`；
- `JianyuRepositoryDao.kt`；
- `RoundtableDatabase.kt`；
- Entity、Migration、Room Schema；
- Skill Catalog 数据与正式 Skill Validator。

## 四、PR09-05 Skill 目录可消费接口

### 4.1 官方组合读取

```kotlin
suspend fun listOfficialSkillCombinations():
    RepositoryResult<List<OfficialSkillCombinationSnapshot>>

suspend fun getOfficialSkillCombination(
    combinationId: String
): RepositoryResult<OfficialSkillCombinationSnapshot>
```

`OfficialSkillCombinationSnapshot.members` 已按 `position` 稳定排序，包含：

- `officialSkillId`；
- `position`；
- 可选 `defaultResponsibility`。

默认职责只属于组合配置，不得直接提升为 System Prompt，也不得改写历史参与者快照。

### 4.2 官方组合写入

```kotlin
suspend fun saveOfficialSkillCombination(
    command: SaveOfficialSkillCombinationCommand
): RepositoryResult<OfficialSkillCombinationSnapshot>

suspend fun deleteOfficialSkillCombination(
    command: DeleteOfficialSkillCombinationCommand
): RepositoryResult<OfficialSkillCombinationEntity>
```

边界：

- 创建使用稳定组合 ID；
- 更新使用 `expectedUpdatedAt` 防止覆盖较新版本；
- 重复 Skill ID 和重复 position 被拒绝；
- 删除为软删除，不修改历史 Run 参与者快照；
- PR09-05 不得绕过 Repository 直接写组合表。

### 4.3 正式官方 Skill ID 校验

```kotlin
fun interface OfficialSkillIdValidator {
    suspend fun isValid(officialSkillId: String): Boolean
}
```

当前默认实现 `RejectingOfficialSkillIdValidator` 拒绝所有未知 ID。PR09-05 必须提供基于正式 Skill Catalog 的实现，并在创建 `RoomJianyuRepository` 时注入。

禁止：

- 生产环境“全部允许”校验器；
- 伪造第二套平行 Catalog；
- 在数据库事务中进行网络校验；
- 由页面自行判断后绕过 Repository 写表。

### 4.4 PR09-05 独占文件

PR09-05 可独占：

- 正式 Skill Catalog 数据源；
- Skill 列表与详情；
- 目录检索、分类和展示；
- 官方组合展示；
- 正式 `OfficialSkillIdValidator` 实现及其测试。

PR09-05 禁止修改：

- Repository 公共接口和内部实现；
- Database、Entity、DAO、Migration、Room Schema；
- 根导航图、Route 和 App 导航入口；
- ExecutionRun 调度状态机。

## 五、PR09-07 可消费恢复信息

虽然 PR09-07 尚未允许启动，PR09-03 已提供以下只读恢复基础：

- `IssueRecoveryCore.runs`；
- `activeOrRecoverableRuns`；
- 参与者快照；
- Message 与 Pending Message；
- Run 的 stopped / failed / partial_success / retryable 状态和失败字段；
- `successfulParticipantSnapshotIds()`；
- `retryableParticipantSnapshotIds()`。

后两个扩展只根据已持久化消息和参与者快照计算稳定 ID，不决定模型调用、预算扣除、网络重试或调度策略。

## 六、旧 ChatRepository 兼容边界

旧 `ChatRepository` 继续负责：

- 旧 ChatSession；
- 旧 UI 消息流；
- 旧 Pending 更新；
- Message 音频字段；
- Session 删除；
- 真实音频文件删除。

新 `JianyuRepository` 负责：

- 新 Issue / Stage / Run；
- 新领域 Message；
- 草稿、成果和来源；
- 资料/背景使用快照；
- 官方组合；
- 生命周期和恢复。

由于当前 `Message.chatId` 非空，新领域首次写入 Message 时会在同一事务中按需创建兼容 ChatSession，并写入 `Issue.legacyChatSessionId`。Issue 始终是新领域事实源。

兼容 Session 最早在 PR09-07 新执行链稳定、且后续独立 Schema PR 获准解除 `Message.chatId` 强依赖后删除。真实音频字段和文件删除边界最早由 PR09-10B 迁移；永久清理由 PR09-12 处理。

## 七、双方共享禁止修改区

PR09-04 和 PR09-05 即使有限并行，也都禁止修改：

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/schemas/
```

任一任务确需修改上述共享区时：

1. 停止并行；
2. 报告具体缺口；
3. 单独创建 PR09-03 后续修正分支；
4. 修正合并后，另一个任务重新基于最新 `main`；
5. 不得在 PR09-04/05 内偷偷扩展 Repository 或 Schema。

## 八、合并后启动门禁

只有满足以下条件，才可启动 PR09-04 与 PR09-05：

1. PR #34 完成本地 AI 严格只读验收；
2. 用户明确授权标记 Ready；
3. Ready 状态触发的最新 CI 通过；
4. 用户授权并实际合并 PR #34；
5. 记录 PR09-03 实际合并 Commit SHA；
6. 两个新分支均从最新稳定 `main` 创建；
7. 开放 PR 与文件所有权重新核验无冲突。
