# PR09-09 → PR09-06 资料与上下文接口交接

> 状态：冻结  
> 所属 PR：#39 `feat: 建立见域资料与个人背景`  
> 基线：`main@228ec6f972684512fb6287d89c253da6c4aebd91`  
> 所有者：PR09-09 资料、个人背景、上下文确认与实际使用快照

## 1. 交接结论

PR09-06 首页与推荐只能消费本文件列出的领域接口，不得访问 DAO、Room Entity 或自行拼接上下文全文。首页可以保存问题和议题，也可以进入资料/个人背景选择；只有用户完成显式确认并得到 `PreparedExecutionContext` 后，才允许把 `contributions` 与 `usage` 交给现有 `ExecutionRunCoordinator`。

个人背景默认全部不选。资料即使已关联当前 Issue/Stage，也只是候选，不代表自动发送。推荐失败不得丢失首页问题草稿，也不得绕过确认门禁改为无资料执行。

## 2. 公共文件与所有权

### 数据与领域接口

```text
app/src/main/java/com/elio/jianyu/data/MaterialContextModels.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryContract.kt
```

### 执行接口

```text
app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionContextGate.kt
```

### 可复用确认 UI

```text
app/src/main/java/com/elio/jianyu/ui/screens/context/ContextConfirmationUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/context/ContextConfirmationComponents.kt
```

### PR09-09 独占实现文件

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
```

PR09-06 不得直接修改上述独占实现文件。需要扩展时，在独立 PR 中先更新本交接契约并说明兼容性。

## 3. 资料 Repository 命令

通过单一 `JianyuRepository` 调用：

```kotlin
suspend fun createMaterial(command: CreateMaterialCommand): RepositoryResult<Material>
suspend fun updateMaterial(command: UpdateMaterialCommand): RepositoryResult<Material>
suspend fun getMaterial(materialId: String): RepositoryResult<Material>
suspend fun listMaterials(filter: MaterialFilter = MaterialFilter()): RepositoryResult<List<Material>>
suspend fun changeMaterialLifecycle(command: ChangeMaterialLifecycleCommand): RepositoryResult<Material>
suspend fun getMaterialPurgeImpact(materialId: String): RepositoryResult<ContextPurgeImpact>
suspend fun purgeMaterial(command: PurgeMaterialCommand): RepositoryResult<Material>
```

`CreateMaterialCommand` 必须提供稳定 `id`、`issueId`、可选 `stageId`、标题、来源类型、可选来源定位、正文和本地创建时间。来源发布日期 `sourcePublishedAt` 与本地采集时间 `sourceCapturedAt` 分离，未知时保持 `null`。

`UpdateMaterialCommand` 使用 `expectedUpdatedAt` 做乐观并发；来源在选择后被编辑时，准备上下文和 Runtime 创建事务都会拒绝陈旧版本。

## 4. 个人背景 Repository 命令

```kotlin
suspend fun createPersonalContext(command: CreatePersonalContextCommand): RepositoryResult<PersonalContext>
suspend fun updatePersonalContext(command: UpdatePersonalContextCommand): RepositoryResult<PersonalContext>
suspend fun getPersonalContext(personalContextId: String): RepositoryResult<PersonalContext>
suspend fun listPersonalContexts(
    filter: PersonalContextFilter = PersonalContextFilter(),
): RepositoryResult<List<PersonalContext>>
suspend fun changePersonalContextLifecycle(
    command: ChangePersonalContextLifecycleCommand,
): RepositoryResult<PersonalContext>
suspend fun getPersonalContextPurgeImpact(
    personalContextId: String,
): RepositoryResult<ContextPurgeImpact>
suspend fun purgePersonalContext(
    command: PurgePersonalContextCommand,
): RepositoryResult<PersonalContext>
```

个人背景可以跨议题复用，但每次执行和每次重试都必须重新选择与确认。应用启动、首页打开、议题创建和推荐完成均不得自动勾选任何个人背景。

## 5. 生命周期

公共枚举：

```kotlin
ContextSourceLifecycle.ACTIVE
ContextSourceLifecycle.DISABLED
ContextSourceLifecycle.ARCHIVED
ContextSourceLifecycle.DELETED
ContextSourceLifecycle.PURGE_REQUESTED
ContextSourceLifecycle.PURGED
```

首页候选只读取 `ACTIVE`。`DISABLED`、`ARCHIVED`、`DELETED`、`PURGE_REQUESTED` 和 `PURGED` 均不得用于新 Run。

普通删除只改变当前来源，不改写历史 Run、Message 或 Usage Snapshot。彻底清除会受控擦除当前和历史快照正文，并保留不泄密关系占位；PR09-06 不得自行实现清除 SQL。

## 6. 选择草稿

公共草稿：

```kotlin
data class ContextSelectionDraft(
    val issueId: String,
    val stageId: String,
    val runId: String,
    val baseContextCharacters: Int,
    val items: List<ConfirmedContextItem> = emptyList(),
    val confirmed: Boolean = false,
)
```

`items` 默认空，`confirmed` 默认 `false`。草稿可以在 Activity 重建后恢复，但恢复后仍是未确认状态，不得自动开始运行。

每个 `ConfirmedContextItem` 包含：

```text
sourceType
sourceId
title
sourceKind
sourceLocator
sourcePublishedAt
sourceCapturedAt
content
contentHash
expectedSourceHash
expectedSourceUpdatedAt
confirmationOrder
userConfirmedAt
networkAllowed
sensitive
sensitiveConfirmed
```

`content` 是用户最终确认的全文或摘录，不一定等于资料库当前全文。`expectedSourceHash` 和 `expectedSourceUpdatedAt` 是确认时看到的来源版本，用于阻止选择后编辑竞态。

## 7. Hash 规范

统一使用 `ContextContentHasher`：

```text
算法：SHA-256
字符编码：UTF-8
换行：CRLF 与单独 CR 统一为 LF
trim：不执行
截断：不执行
摘要：不执行
```

`contentHash` 必须对应 UI 预览并实际发送的精确 `content`。用户修改摘录后必须重新计算 Hash。空白正文不得进入准备结果。日志、异常、遥测和匿名占位不得输出正文或原始 Hash。

## 8. 排序规则

`ContextSelectionValidator` 使用确定性顺序：

```text
1. confirmationOrder
2. userConfirmedAt
3. sourceType.storageValue
4. sourceId
```

首页必须把用户实际确认顺序写入 `confirmationOrder`，不得按数据库返回顺序、标题或推荐分数重新排序。

## 9. 字符边界

常量：

```kotlin
MAX_EXECUTION_CONTEXT_CHARACTERS = 24_000
```

首页在打开确认 UI 前先计算固定上下文、历史消息、当前问题和必要模板预估，写入 `baseContextCharacters`。确认 UI 再加总选中正文长度。

小于或等于 24,000 允许准备；超过时返回 `ContextValidationError.CONTEXT_TOO_LARGE`。禁止：

```text
静默截断
静默丢弃最后一项
自动摘要
自动调用模型压缩
忽略历史消息或当前问题占用
```

用户可以手动缩短摘录或移除来源，再重新确认。

## 10. 联网授权

`networkAllowed` 只表示：

> 用户允许本次把该条确认正文发送给生产模型服务。

它不表示允许自动访问 URL、自动搜索、自动更新来源或未来永久授权。

任一选中项 `networkAllowed == false` 时，准备结果必须失败为 `NETWORK_NOT_ALLOWED`。不得静默移除该项后继续，也不得先创建 Run 再提示。

## 11. 敏感确认

`sensitive == true` 不代表禁止使用；但必须同时满足 `sensitiveConfirmed == true`。否则准备结果为 `SENSITIVE_CONFIRMATION_REQUIRED`。

首页候选列表和可复用组件默认不显示敏感全文。TalkBack 在未进入详情或确认摘录区域时不得自动朗读完整敏感正文。

## 12. 准备上下文

唯一业务入口：

```kotlin
suspend fun JianyuRepository.prepareExecutionContext(
    command: PrepareExecutionContextCommand,
): RepositoryResult<PreparedExecutionContext>
```

`PrepareExecutionContextCommand` 包含草稿、准备时间和默认 24,000 上限。Repository 会：

1. 验证草稿已确认；
2. 验证重复来源与不同 Hash 冲突；
3. 验证正文非空、Hash、联网和敏感确认；
4. 重新读取来源；
5. 验证来源属于正确 Issue/Stage 或为可复用个人背景；
6. 验证生命周期为 `ACTIVE`；
7. 验证 `expectedUpdatedAt` 和 `expectedSourceHash`；
8. 构造有序 `ExecutionContextContribution`；
9. 构造实际使用 `ContextUsageWriteSet`；
10. 返回 `PreparedExecutionContext`。

`PreparedExecutionContext` 是唯一允许进入执行层的上下文对象：

```kotlin
data class PreparedExecutionContext(
    val preparation: ContextPreparationResult.Ready,
    val usage: ContextUsageWriteSet,
)
```

首页不得自己创建 Usage Snapshot Entity，也不得只传 Contribution 而丢弃 `usage`。

## 13. 执行调用

首次执行：

```kotlin
ExecutionStartCommand(
    ...,
    contributions = prepared.preparation.contributions,
    contextUsage = prepared.usage,
)
executionRunCoordinator.start(command)
```

重试：

```kotlin
ExecutionRetryCommand(
    ...,
    contributions = prepared.preparation.contributions,
    contextUsage = prepared.usage,
)
executionRunCoordinator.retry(command)
```

`ExecutionContextGate` 在 Runtime 创建前核对联网授权、Contribution 与 Usage 的正文/Hash/确认时间/敏感标记一致性。`ExecutionRuntimeRepositoryComponent` 再在单一 Room 事务中写入：

```text
ExecutionRun
Participant Snapshot
Participant State
Budget
Material Usage Snapshot
Personal Context Usage Snapshot
```

并在事务内再次验证来源仍为 ACTIVE、`updatedAt` 与 Hash 未变化。任何失败都整笔回滚，Coordinator 不创建 Pending、不消费预算、不调用网络。

## 14. 幂等键

`ExecutionStartCommand.idempotencyKey` 和 `ExecutionRetryCommand.idempotencyKey` 继续由调用方提供稳定值。相同幂等键只有在 Run、参与者、预算及完整 `ContextUsageWriteSet` 全部一致时才返回既有 Runtime。

相同幂等键但来源集合、正文、Hash、联网授权、敏感标记、确认时间或预期来源版本不同，必须返回幂等冲突；不得用后一次内容覆盖第一次。

## 15. 重试语义

重试是新的网络发送。

```text
原 Run Usage Snapshot：只读
原 Run 成功成员：不重试
来源当前版本：不自动读取替换历史
历史使用快照：只用于展示
重试选择：默认未确认
新子 Run：写自己的 Usage Snapshot
预算：不返还
```

可通过：

```kotlin
suspend fun JianyuRepository.listRunContextUsage(
    runId: String,
): RepositoryResult<List<ContextUsageSnapshot>>
```

展示原 Run 实际使用来源。历史正文已 PURGED 时只能显示统一匿名占位，不得直接重新发送。

## 16. 恢复语义

进程恢复只读取已存在 Run 和 Usage Snapshot：

```text
不自动联网
不自动重新确认
不自动创建新快照
不自动读取来源当前正文
不修改成功成员
不修改预算
```

用户主动点击重试后，再进入新的确认流程。

## 17. 错误模型

上下文校验使用 `ContextValidationError`：

```text
confirmation_required
source_not_found
source_disabled
source_archived
source_deleted
source_purged
source_stale
content_hash_mismatch
duplicate_source
network_not_allowed
sensitive_confirmation_required
context_too_large
content_empty
usage_snapshot_conflict
```

数据层继续通过 `RepositoryError` 返回稳定错误；UI 文案与内部错误码分离。错误消息不得包含资料或个人背景正文。

## 18. 协程与线程

所有 `JianyuRepository` 接口均为 `suspend`，调用方必须从 ViewModel 的受控协程启动。Screen 与 Components 只接收 `UiState` 和回调，不持有 Repository、DAO 或 Coordinator。

Room 事务位于 Repository 内部。网络调用不在数据库事务中。首页不得创建第二个 CoroutineScope、第二个 Repository、第二个 Context Builder 或第二个 Coordinator。

## 19. 可复用确认组件

PR09-06 可复用：

```kotlin
ContextConfirmationUiState
ContextCandidateUi
ContextConfirmationDialog(...)
```

组件提供：

```text
资料与个人背景候选
默认不选
正文/摘录编辑
来源与时间
逐项联网授权
敏感二次确认
字符占用与剩余
历史 Usage 只读展示
确认与取消
局部错误
```

组件不会访问 Repository，也不会调用网络。PR09-06 负责把首页问题草稿、Issue/Stage、候选来源和 `baseContextCharacters` 转为 UiState，并把确认动作交给 ViewModel 调用 `prepareExecutionContext()`。

## 20. PR09-06 接入步骤

1. 保存或恢复首页问题草稿；
2. 创建或读取目标 Issue 与 Stage，但不创建 Run；
3. `listMaterials()` 读取当前 Issue/Stage 的 ACTIVE 资料候选；
4. `listPersonalContexts()` 读取 ACTIVE 背景候选；
5. 构造全部未选、未授权的 `ContextConfirmationUiState`；
6. 用户选择、编辑摘录、授权并确认；
7. 调用 `prepareExecutionContext()`；
8. 校验失败时保留问题和选择草稿；
9. 成功后把 `PreparedExecutionContext` 交给现有 `ExecutionRunCoordinator`；
10. 若用户只保存议题，不确认上下文，则不创建 Run、Usage、Pending，不消费预算、不联网。

## 21. 禁止行为

PR09-06 不得：

```text
直接访问 JianyuRepositoryDao / ResourceLifecycleDao
把全部个人背景设为默认选中
把 Issue 内资料自动发送
把背景藏进 System Prompt
自行拼接完整 Prompt
修改 ExecutionContextBuilder 顺序
把 Stage sequenceIndex 当 roundIndex
调用 Gemini 或网络 Gateway
在未授权来源存在时静默移除后继续
在超限时截断或摘要
在推荐失败时清空问题草稿
在 Activity 重建后自动确认或自动开始
修改 PR09-07 状态机、预算、Stop 或迟到回调语义
```

## 22. 兼容性与演进

本交接冻结的是 V1 稳定接口。未来支持 PDF、Office、云盘、网页抓取、Embedding 或自动更新时，必须新增明确来源能力与授权，不得改变 `networkAllowed` 的现有语义，也不得把历史 `ContextUsageSnapshot` 回查成来源当前正文。
