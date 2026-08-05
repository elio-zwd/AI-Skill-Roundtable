# PR09-10A 阶段草稿与正式成果接口交接

## 1. 适用范围

本文记录 PR09-10A 完成后，PR09-10B、PR09-11 及后续工作区功能可以依赖的稳定接口与安全边界。

目标分支：

```text
feat/pr-09-10a-draft-result
```

目标基线：

```text
main@6379146e354cb0bf14365572bb4fa673cc88f727
PR09-08 / PR #46 已合并
Room v10
```

PR09-10A 不创建 Room v11，不修改 `MIGRATION_9_10`，不修改 `schemas/.../10.json`。

## 2. 持久化事实源

继续复用既有 Room 表：

```text
stage_summary_drafts
stage_summary_draft_revisions
confirmed_artifacts
artifact_message_sources
artifact_run_sources
artifact_draft_sources
artifact_material_sources
```

没有第二套 Draft、Artifact 或来源表。

### 2.1 当前可编辑草稿

```kotlin
StageSummaryDraftEntity
```

每个 Issue / Stage 只有一个当前可编辑草稿。保存时必须同时写入不可变 Revision。

### 2.2 草稿 Revision

```kotlin
StageSummaryDraftRevisionEntity
```

Revision 内容是正式成果确认的精确事实源。正式成果不得直接使用尚未保存的编辑器内存文本。

### 2.3 正式成果

```kotlin
ConfirmedArtifactEntity
```

正式成果不可原位覆盖。修订版使用新的 Artifact ID，并通过 `revisionOfArtifactId` 指向直接前序。

## 3. 公共 Repository 写接口

沿用：

```kotlin
suspend fun JianyuRepository.saveStageDraft(
    command: SaveStageDraftCommand,
): RepositoryResult<StageSummaryDraftEntity>

suspend fun JianyuRepository.abandonStageDraft(
    issueId: String,
    stageId: String,
): RepositoryResult<Unit>

suspend fun JianyuRepository.confirmArtifact(
    command: ConfirmArtifactCommand,
): RepositoryResult<ConfirmedArtifactEntity>
```

关键约束：

1. `saveStageDraft()` 原子写入 Draft 与 Revision；
2. Revision 必须连续；
3. 相同 payload 沿用幂等语义；
4. `abandonStageDraft()` 只移除当前 Draft；
5. 历史 Revision、正式 Artifact 和来源关系不会随 Draft 放弃而删除；
6. `confirmArtifact()` 原子写入 Artifact 与所有来源关系；
7. FK、Issue / Stage 同域和幂等冲突继续由 Repository 事务做最终门禁。

## 4. 新增只读成果来源能力

PR09-10A 新增：

```kotlin
data class ArtifactSourceRecoverySnapshot(
    val artifactId: String,
    val messages: List<ArtifactMessageSourceEntity>,
    val runs: List<ArtifactRunSourceEntity>,
    val draftRevisions: List<ArtifactDraftSourceEntity>,
    val materials: List<ArtifactMaterialSourceEntity>,
)

suspend fun JianyuRepository.listArtifactSourcesForIssue(
    issueId: String,
): RepositoryResult<List<ArtifactSourceRecoverySnapshot>>
```

实现位置：

```text
app/src/main/java/com/elio/jianyu/data/ArtifactSourceRecoveryRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/ArtifactSourceRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
```

性质：

- 只读 Room 既有来源表；
- 不调用网络；
- 不创建 Run；
- 不修改预算、执行、协作、Draft 或 Artifact；
- 不从标题、正文或 UI 状态猜测来源；
- 非 Room Repository 不支持时返回稳定 CompatibilityFailure。

后续调用方不得把“来源读取失败”解释为“成果没有来源”。

## 5. 阶段成果服务

稳定入口：

```kotlin
class StageResultService(
    private val repository: JianyuRepository,
)
```

主要接口：

```kotlin
suspend fun load(issueId: String, stageId: String): StageResultLoadResult
suspend fun saveDraft(command: SaveStageResultDraftCommand): StageDraftWriteResult
suspend fun abandonDraft(issueId: String, stageId: String): StageDraftAbandonResult
suspend fun confirmArtifact(
    command: ConfirmStageArtifactCommand,
): StageArtifactConfirmationResult
```

服务只编排持久化与来源校验，不拥有网络 Gateway，不调用 `ExecutionRunCoordinator` 或 `IssueCollaborationCoordinator`。

## 6. Room v10 协作来源适配

`StageResultService.load()` 读取当前 Stage 的：

```text
ExecutionRunEntity.runKind
ExecutionRunEntity.historyScope
ExecutionRunEntity.status
Message.executionRunId
Message.participantSnapshotId
ExecutionMessageUsageSnapshotEntity
MaterialUsageSnapshotEntity
```

支持四种 Run Kind：

```text
STANDARD
DIRECTED_RESPONSE
CROSS_DISCUSSION_RESPONSE
CROSS_DISCUSSION_SYNTHESIS
```

### 6.1 可选消息规则

只有同时满足以下条件的 Message 才能成为成果来源候选：

1. 属于当前 Issue；
2. 属于当前 Stage；
3. `isPending == false`；
4. 绑定真实 `executionRunId`；
5. 绑定真实 `participantSnapshotId`；
6. 对应 Run 也属于当前 Issue / Stage。

因此以下内容不可被直接选为成果消息来源：

```text
用户触发消息
Pending Message
孤立 Message
跨 Issue Message
跨 Stage Message
不存在 Run 的 Message
```

### 6.2 Message Usage

阶段面板展示每个 Run 的实际 Message Usage 数量，并显示：

```text
FULL_STAGE
EXPLICIT_MESSAGES
NO_HISTORY
```

Message Usage 只用于追溯和界面说明，不会被 PR09-10A 重新写入或重新计算。

### 6.3 未完整 Run

非 `SUCCEEDED / COMPLETED` Run 的消息如果已经是持久化完成 Message，只能显示为原始输出候选，并明确提示不代表完整结论。

## 7. 草稿创建与自动保存

通用草稿是本地确定性模板：

```markdown
## 阶段概述

## 已形成的判断

## 主要分歧

## 行动项

## 待确认事项
```

创建草稿不会：

```text
调用模型
调用网络
创建 Run
创建 Pending Message
消费共享预算
自动重试
自动整合
推进 Stage
```

自动保存策略：

```text
800 ms debounce
显式保存立即 flush
相同正文不创建新 Revision
保存操作串行化
Revision 冲突不覆盖较新版本
保存失败保留编辑器正文
```

## 8. 正式成果确认顺序

固定流程：

```text
编辑草稿
→ flush 当前草稿
→ 确认精确 Draft Revision 已持久化
→ 用户填写成果标题和类型
→ 预览来源
→ 用户最终确认
→ 原子 confirmArtifact(artifact + sources)
```

最终确认前：

```text
零 Artifact 写入
零 Stage 推进
零网络调用
零预算消耗
零导出
零音频任务
```

成果来源包含：

```text
精确 Draft Revision
用户选定的完成 Message
对应 Run
对应 Run 的真实 Material Usage Snapshot
```

## 9. 成果类型

```text
GENERAL_SUMMARY
ACTION_PLAN
DECISION_RECORD
KNOWLEDGE_NOTE
DELIVERABLE
```

默认：

```text
GENERAL_SUMMARY
contentFormat = markdown
```

文件格式、分享链接和音频不是 Artifact Type。

## 10. Revision 关系

解析器：

```kotlin
ArtifactRevisionResolver
```

会检测：

```text
孤儿引用
自循环
多节点循环
跨 Issue
跨 Stage
分叉
```

V1 写入策略禁止同一个直接前序生成第二个子版本。后续 PR 不得把异常历史静默压平成单链。

## 11. 应用与共享工作区接线

运行时单例链：

```text
JianyuAppRuntimeProvider
→ JianyuAppRuntime.stageResultService
→ App.kt
→ IssueExecutionRoute
→ StageResultViewModel
→ StageDraftResultPanel
```

`IssueExecutionScreen` 同时承载：

```text
执行状态
共享预算
资料与个人背景确认
点名回应
交叉讨论
阶段草稿与正式成果
```

PR09-10A 没有建立第二个执行工作区，也没有复制 PR09-08 Coordinator。

## 12. 恢复和安全边界

打开或恢复工作区时，阶段成果链只执行只读加载：

```text
recoverIssue()
getStageCollaboration()
listArtifactSourcesForIssue()
```

不会：

```text
自动调用网络
自动创建 Run
自动重试失败成员
自动继续点名回应
自动继续交叉讨论
自动执行整合
自动确认成果
自动推进 Stage
```

Stop、迟到回调和共享预算继续由 PR09-08 的执行 / 协作状态机负责。PR09-10A 不修改这些状态转换。

## 13. 自动化标签

中央契约：

```kotlin
JianyuAutomationTags.Artifacts
JianyuAutomationTags.StageResult
```

关键静态标签：

```text
artifact_library
artifact_library_empty
artifact_library_failure
artifact_detail
artifact_sources
stage_result_panel
stage_draft_empty
stage_draft_create_button
stage_draft_editor
stage_draft_save_button
stage_draft_saved
stage_draft_save_failure
stage_draft_conflict
stage_artifact_confirm_button
artifact_confirmation_dialog
artifact_confirmation_confirm
```

动态标签只接受稳定内部 ID：

```kotlin
JianyuAutomationTags.Artifacts.item(artifactId)
JianyuAutomationTags.StageResult.message(messageId)
JianyuAutomationTags.StageResult.artifact(artifactId)
```

不得传入：

```text
标题
正文
姓名
用户问题
Prompt
资料内容
个人背景内容
```

`normalizedStableId()` 会拒绝中文、空格和超长内容，不会将用户正文静默净化为标签。

## 14. 全局成果库

入口：

```text
资料与成果 → 成果
```

能力：

```text
Loading / Empty / Failure / PartialFailure / Content
搜索标题、摘要、Issue、Stage
类型筛选
默认只显示最新版本
可显式展示历史版本
详情中显示完整正文
详情中显示真实来源数量和稳定来源 ID
返回对应 Issue / Stage
修订异常提示
```

资料、个人背景与成果各自拥有独立状态。一个区域失败不得拖垮其他区域。

## 15. PR09-10B 可依赖内容

PR09-10B 若实现导出或音频等成果消费功能，可以依赖：

```text
ConfirmedArtifactEntity 是正式成果唯一事实源
ArtifactRevisionResolver 的修订关系
listArtifactSourcesForIssue() 的只读来源
ArtifactType 的稳定 storageValue
全局成果库只展示已确认 Artifact
```

不得依赖：

```text
当前编辑器内存正文
StageSummaryDraftEntity 等同正式成果
Pending Message
通过成果正文猜测来源
成果存在即表示 Stage 已推进
```

## 16. PR09-11 可依赖内容

生命周期、归档和备份后续功能必须保留：

```text
Draft Revision
Confirmed Artifact
Artifact Source 关系
Revision 前序关系
Room v10 Run Kind / Message Usage
```

放弃当前 Draft 不等于删除历史成果。

若后续引入真正的数据删除或导出，必须独立设计迁移、确认和回滚，不得复用 `abandonStageDraft()` 作为清理入口。

## 17. 已覆盖测试

JVM：

```text
StageResultDomainTest
StageResultServiceTest
StageDraftAutosavePolicyTest
StageResultOperationGatesTest
ArtifactLibraryLoaderTest
ArtifactLibraryUiStateTest
ArtifactLibraryAggregatorSourceTest
JianyuAutomationTagsTest
```

Instrumentation / Compose：

```text
ArtifactSourceRecoveryDatabaseTest
RoomJianyuRepositoryDatabaseTest
ArtifactLibraryComponentsTest
StageResultComponentsTest
IssueExecutionStageResultScreenTest
```

已有 PR09-08 测试继续负责：

```text
Room v1→v10 / v9→v10 Migration
共享预算
Stop
迟到回调
点名回应
交叉讨论
强停恢复
恢复后零自动网络、零自动重试、零自动整合
```

PR09-10A 的最终本地验收必须把两组测试一起回归。

## 18. 回滚建议

若需要回滚 PR09-10A：

1. 回滚工作区 UI 和 Runtime 接线；
2. 回滚成果来源只读能力与成果库展示；
3. 保留 Room v10、PR09-08 Migration 和协作实现；
4. 不删除已存在 Draft、Revision、Artifact 或来源关系；
5. 不降级数据库版本；
6. 不执行破坏性 Schema 回滚。

因为 PR09-10A 没有新增表或数据库版本，代码回滚不需要新增 Migration。
