# PR09-12 归档、回收站与彻底清理实施计划

> **执行方式**：当前会话未暴露可直接调用的 Superpowers 插件接口，已实际读取仓库内置 Superpowers 6.2.0 的 `brainstorming`、`writing-plans`、`test-driven-development`、`systematic-debugging`、`verification-before-completion`、`requesting-code-review` 与 `finishing-a-development-branch` 文档，并按 `tools/ai/superpowers/project-workflow.md` 执行等价人工流程。用户提供的 PR09-12 Prompt 视为已批准规格，本计划按串行 TDD 实施。

## 1. 实际 Base 与并行门禁

- 仓库：`elio-zwd/AI-Skill-Roundtable`
- Base：`main@8c4f3df5510ff7c3fb36088c0867c521fdb16980`
- 开发分支：`feat/pr-09-12-archive-trash`
- 开放 PR：开始时为 `0`
- Room：`v11`
- 上游：PR #48 合并提交 `5cb1df96b32fe964507218de41528ace5b357d52`；PR #49 合并提交 `8c4f3df5510ff7c3fb36088c0867c521fdb16980`
- PR09-13A、PR09-13B、PR09-14A、PR09-14B 在本 PR 合并前不得开始。

## 2. 当前能力与根因

现有 `IssueLifecycleEntity` 已包含 `ACTIVE / ARCHIVED / TRASHED`、`previousState` 和 `purgeRequestedAt`，`LifecycleRecoveryRepositoryComponent` 仅执行生命周期单表迁移。`requestIssuePurge()` 只写时间戳，无法表达任务取消、文件删除、数据库清理阶段、失败位置和进程恢复；归档也没有不可变简报、恢复变化记录或关联新议题关系。根因是 v11 缺少正式事实表和跨组件协调边界，而不是 UI 缺少按钮。

## 3. 文件所有权

### 3.1 新增数据文件

- `app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Entities.kt`：Archive、Resume、Relation、Purge Operation 实体与转换器。
- `app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Migration.kt`：唯一 `11→12` Migration。
- `app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Dao.kt`：事件、关系、操作、影响统计和最终删除 SQL。
- `app/src/main/java/com/elio/jianyu/data/IssueLifecycleRepositoryContract.kt`：命令、结果、影响模型、状态码和写入门禁模型。
- `app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12RepositoryComponent.kt`：归档、恢复、关联新议题、回收站和 Purge 持久化事务。
- `app/src/main/java/com/elio/jianyu/data/IssuePurgeDatabaseCleaner.kt`：单一 Room 事务删除图。

### 3.2 新增领域协调文件

- `app/src/main/java/com/elio/jianyu/lifecycle/IssueLifecycleWriteGate.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/IssueArchiveCoordinator.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeCoordinator.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeImpactCalculator.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeFileCleaner.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeScheduler.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeWorker.kt`

### 3.3 修改文件

- `RoundtableDatabase.kt`：v12 实体、Converters、DAO、Migration。
- `JianyuRepositoryContract.kt`、`RoomJianyuRepository.kt`：公开正式命令和门面委托，保留既有兼容方法。
- `JianyuRepositoryTransactions.kt`：新增 lifecycle 多 DAO 单事务作用域。
- `JianyuAppRuntime.kt`：组合协调器、Worker 依赖和音频服务。
- `AudioAssetLifecycleService.kt` / `AudioFileStore.kt`：仅增加按正式资产安全删除与可验证结果，不暴露目录递归删除。
- `IssuesRoute.kt`、`IssuesViewModel.kt`、`IssuesUiState.kt`：生命周期流程 UI；`App.kt` 仅负责 Route 组装。
- `JianyuAutomationTags.kt`：中央静态和动态标签。

## 4. Room v12 Schema

### 4.1 `IssueArchiveEventEntity`

表 `issue_archive_events`：

- `id` 主键；`issueId` FK `issues.id` / `RESTRICT`；`archiveOperationId` 唯一；
- `summaryMarkdown`、`currentStageIdSnapshot`、Stage/Run/Draft/Artifact/Audio 数量；
- `archivedAt`、`createdAt`；
- 同一议题可多次归档，旧事件只读；清除议题时显式删除。

### 4.2 `IssueResumeEventEntity`

表 `issue_resume_events`：

- `id` 主键；`issueId` FK `RESTRICT`；`archiveEventId` FK Archive Event / `RESTRICT`；
- `resumeOperationId` 唯一；`changeNote`、`resumedAt`、`createdAt`；
- 不修改 Archive Event，不创建 Stage/Run。

### 4.3 `IssueRelationEntity`

表 `issue_relations`：

- `id` 主键；`sourceIssueId` 可空 FK `issues.id` / `SET_NULL`；`targetIssueId` FK `issues.id` / `RESTRICT`；
- `sourceArchiveEventId` 可空 FK Archive Event / `SET_NULL`；
- `relationType = continuation`、`createdAt`、`sourcePurgedAt`；
- `targetIssueId + relationType` 不设全局唯一，幂等由创建命令 `operationId` 的独立列和唯一索引保证；
- 来源清除前先把来源字段置空并写 `sourcePurgedAt`，目标议题与关系保留；UI 只显示“来源议题已清除”，不保存清除后的标题或正文；目标被清除时删除其关系行。

### 4.4 `IssuePurgeOperationEntity`

表 `issue_purge_operations`：

- `id` 主键；`issueId` FK `RESTRICT` 且唯一；`operationId` 唯一；
- `payloadHash`、可空 `impactHash`；
- 状态：`REQUESTED / WAITING_FOR_TASKS / CANCELING_TASKS / DELETING_FILES / READY_FOR_DATABASE_PURGE / DATABASE_PURGING / FAILED_RETRYABLE / COMPLETED`；
- `requestedAt / startedAt / updatedAt / failedAt / failureCode / failurePhase / retryCount`；
- 最终事务提交前一直存在；事务失败时与 Issue 一起保留；成功时在同事务中删除。

### 4.5 Migration 11→12

- 只创建四张新表和索引，不修改历史 Schema；
- 不为历史 ARCHIVED/TRASHED 伪造 Archive Event；
- 对 `issue_lifecycle.purgeRequestedAt IS NOT NULL` 的历史行插入 `FAILED_RETRYABLE` 操作，错误码 `legacy_purge_request_requires_review`，`failurePhase = impact`，要求用户重新查看影响；
- Migration 测试覆盖 v1～v12 连续链、v11 数据完整和 `PRAGMA foreign_key_check = 0`；
- `12.json` 必须由 Room/KSP 真实生成。若远端环境只能通过 CI 生成，则在 Draft PR CI 中读取生成差异后提交，不手写 Schema JSON。

## 5. Archive Event 与本地简报

`IssueArchiveImpact` 从实际恢复快照、协作状态和 Audio 生命周期读取：当前 Stage、Stage 数、运行状态、未完成协作、Pending Message、草稿、正式成果和音频状态。`IssueArchiveSummaryFactory` 只使用确定性计数与状态生成 Markdown，不复制全部消息正文、不调用模型。用户编辑后的最终内容与 Lifecycle 在一个事务中写入。

## 6. 活动任务处理

`IssueArchiveCoordinator.inspect()` 检查：STANDARD、Directed、Cross Response、Synthesis、Pending Message、草稿保存状态、PENDING Audio 和既有 Purge Operation。打开、返回、取消均零写入。

- `WAIT`：只观察 Room/Work 状态，不 Stop、不归档；全部终态后重新读取并生成新确认版本。
- `STOP`：复用 Execution Stop、Collaboration Stop、Audio cancel；等待持久化终态；重新读取；旧 impact revision 与确认失效；用户再次确认后才能归档。
- Repository 事务不调用网络或 WorkManager。

## 7. 恢复原议题

`ResumeArchivedIssueCommand` 必须携带 `operationId`、`archiveEventId`、`changeNote` 或显式 `noChangeConfirmed`。事务验证当前状态为 ARCHIVED，原子插入 Resume Event 并恢复 ACTIVE。相同 operationId/相同 payload 幂等，不同 payload 返回 `IdempotencyConflict`。不创建 Stage、Run、模型调用、资料选择、个人背景选择或网络授权。

## 8. 关联新议题

`CreateRelatedIssueCommand` 携带来源归档事件、新 Issue、初始 Stage、可编辑标题和目标、operationId。单事务创建：

1. 新 `IssueEntity`；
2. 新 `IssueLifecycleEntity(ACTIVE)`；
3. 唯一初始 `StageEntity(sequenceIndex = 0)`；
4. `IssueRelationEntity(CONTINUATION)`。

不复制 Message、Run、Participant、Draft、Artifact、Material 正文、Personal Context 或授权。双击通过 operationId/payloadHash 只产生一个目标 Issue 和一个 Relation。

## 9. 回收站

ACTIVE/ARCHIVED 移入 TRASHED 前使用与归档相同的活动任务检查，用户必须选择等待或停止；不得自动 Stop。`previousState` 保留原状态，恢复到 ACTIVE 或 ARCHIVED。不存在自动过期字段、倒计时或定时 Purge Worker；容量不足只展示实际 Audio/App 私有文件占用和手动入口。

## 10. 写入门禁

统一 `IssueWriteAccessPolicy`：

- ACTIVE：允许现有业务写入；
- ARCHIVED：仅恢复、关联新议题、移入回收站和只读/播放已有 AVAILABLE Audio；
- TRASHED：仅影响查看、恢复、Purge；
- `purgeRequestedAt != null` 或存在 Purge Operation：冻结全部业务写入，只允许安全阶段取消、状态查看和重试。

门禁三层落地：

1. UI 禁用并解释；
2. ViewModel/Coordinator 拒绝；
3. Repository/Service 最终边界重新读取 Lifecycle 后拒绝。

至少覆盖 Run、Directed、Cross、AdvanceIssue、Draft、Artifact、Audio、Material/Personal Context Usage。旧 API 不得绕过最终门禁。

## 11. Purge 影响计算

`IssuePurgeImpactCalculator` 从真实 DAO 与 `AudioAssetLifecycleService.inspectPurgeImpact()` 统计：Stage、Advancement、Measures、Skill 阵容、Material/Artifact 继承、Run、Participant、Budget、Message/Pending、Cross、Usage、Material Reference/Usage、Personal Context Usage、Draft/Revision、Artifact/Source、AudioAsset、正式/临时文件、Pending Work、Relation 和兼容 ChatSession。

输出包含数据库对象数、文件数、字节数、Pending 数、Missing、Orphan 报告、关联目标数和不可删除外部对象。排序规范化后计算 SHA-256；不包含标题、正文、绝对路径或完整 Hash 的日志。Orphan 只报告，不计入本议题自动删除目标。

## 12. 双确认与幂等

第一次确认锁定 `impactHash`；第二次明确不可恢复后才创建操作并写 `purgeRequestedAt`。相同 `operationId + payloadHash` 返回已有操作；operationId 相同但 payload 不同返回冲突；影响变化返回 `purge_impact_changed`，要求重新查看和双确认。

## 13. 文件和 Audio 清理

严格调用：

- `listAudioAssetsForIssue`
- `reconcileFilesForIssue`
- `inspectPurgeImpact`
- `requestDelete`

每个资产先 `requestDelete`，再取消唯一 Work，随后通过 `AudioFileStore.removeCommitted(relativePath)` 删除受控正式文件，并通过 `removeTemporaryFilesForAsset(assetId, format)` 清理该资产 `.part`。路径拒绝、IO 失败或任务取消失败均持久化稳定错误码并停止数据库最终清理。不得 `deleteRecursively()`、不得按文件名猜 Issue、不得删除外部 URI或其他 Issue 文件。

## 14. Orphan 边界

全局 Orphan 扫描仅展示 `AudioOrphanReport`。本 PR 不提供“随议题一起删除 Orphan”的隐式行为；若未来提供独立 Orphan 管理，必须再次单独确认。`sourceLocator` 不视为 App 自有文件路径。

## 15. 清理状态机与进程恢复

Worker Data 只有 `purge_operation_id`。唯一 Work 名由 operationId 规范化生成并使用 `KEEP`。Worker 从 Room 恢复 Operation，不重建用户意图；可继续状态且 Work 不存在时恢复同一操作，不创建第二个操作。阶段顺序：

`REQUESTED → WAITING_FOR_TASKS → CANCELING_TASKS → DELETING_FILES → READY_FOR_DATABASE_PURGE → DATABASE_PURGING → 完成后事务删除`。

失败进入 `FAILED_RETRYABLE` 并保留 failurePhase/retryCount。文件已清理但数据库失败时 UI 显示“文件已清理，数据库收尾失败”，重试只进入数据库阶段，不伪装可恢复文件。

## 16. v12 FK 删除图与顺序

生产删除代码前以实体和 `12.json` 再次核对。计划顺序：

1. 删除 Audio 相关受控物理文件，数据库尚不删除；
2. 单一 Room 事务开始；
3. 将以当前 Issue 为来源、其他 Issue 为目标的 Relation 置 `sourceIssueId/sourceArchiveEventId = NULL` 并写 `sourcePurgedAt`；
4. 删除当前 Issue 作为目标的 Relation；
5. 删除 Artifact Source；
6. 删除 AudioAsset；
7. 删除 Message Usage、Cross Discussion；
8. 删除 Participant State、Participant Snapshot、Run Budget、Run；
9. 删除 Message；
10. 删除 Artifact；
11. 删除 Draft Revision、Draft；
12. 删除 Material Usage、Material Reference；
13. 删除 Personal Context Usage，保留全局 `PersonalContextEntry`；
14. 删除 Stage Advancement 子表与根；
15. 删除 Stage；
16. 删除 Resume Event、Archive Event；
17. 仅在兼容 ChatSession 没有其他合法引用时删除；
18. 删除 Lifecycle 与 Purge Operation；
19. 最后删除 Issue；
20. 任一 SQL 失败整体回滚，不关闭 foreign_keys。

官方 Skill Catalog、官方 Skill 组合、API Key、其他 Issue、备份文件和外部 URI永不在该事务中删除。

## 17. UI 与 UiState

保持四个一级导航。议题页继续显示活跃/归档/回收站，并提供更多操作。生命周期流程使用同域 Dialog/Sheet，不把页面专属状态放入 `App.kt`。

稳定状态覆盖：Archive impact/decision/wait/stop/edit/commit/failure，Resume edit/commit/failure，Related Issue edit/create/failure，Trash impact/move/restore，Purge impact/双确认/任务取消/文件删除/数据库清理/失败重试/完成。一次性导航和消息使用单独 Event Flow；完成状态不能只存在 Snackbar。

## 18. 自动化标签

在 `JianyuAutomationTags` 新增 `IssueLifecycle / Archive / Trash / Purge / RelatedIssue` 分组和 Prompt 指定的全部静态标签。动态标签 `archive_event_* / resume_event_* / issue_relation_* / purge_operation_* / purge_audio_asset_*` 必须调用 `normalizedStableId()`，不得包含标题、简报、正文、文件路径、Key 或完整 Hash。

## 19. TDD 顺序

1. 先写纯领域失败测试：状态转换、幂等、影响 Hash、写入门禁；
2. 再写 Room v12 实体/Migration/事务失败测试；
3. 再实现最小 Repository；
4. 写文件清理失败与路径穿越测试；
5. 接入 Audio 正式接口；
6. 写 Coordinator/进程恢复测试；
7. 写 Compose/Automation 标签测试；
8. 最后组装 Runtime 与 UI。

由于当前会话没有可执行 Android 工作区，不能真实观察 RED/GREEN；远端提交只标记“测试代码已先写、尚未实际执行”，以 CI 和本地严格只读验收补齐证据。

## 20. Commit 边界

按以下原子意图提交：计划；领域失败测试；Room v12；归档/恢复/关系事务；Purge 影响与状态机；Audio/文件清理；UI 与标签；恢复与无半删除测试；交接与本地验收文档。不得夹带依赖升级或无关重构。

## 21. CI 与验证

远端至少验证 `git diff --check`、Secret scan、应用身份门禁、`compileDebugKotlin`、`testDebugUnitTest`、`lintDebug`、Debug/Release、AndroidTest APK、Schema freshness 与 Migration 连续性。必须分别记录 CI、APK 编译、设备 Instrumentation 和外部 UIAutomator，不相互冒充。

## 22. 本地严格只读验收

创建 `docs/testing/pr-09-12-local-readonly-acceptance-prompt.md`，锁定精确 Head，使用 `Invoke-LocalVerification.ps1`，覆盖安装，不清数据，不使用生产网络/Key。真实不可恢复 Purge 只使用测试专用 Issue 和受控测试目录；日志放 `$env:TEMP`；结束时工作区和 Schema diff 必须干净。

## 23. 隐私

日志只记录稳定错误码和受控数量，不记录标题、简报、Change Note、Message/Draft/Artifact/Material/Personal Context 正文、Key、绝对路径、完整 Generation Key 或完整 Impact Hash。

## 24. 风险

- v11 现有 FK 全为 RESTRICT，删除图遗漏会导致事务失败；以失败回滚优先，不绕过外键。
- Audio 文件阶段不可回滚；因此数据库失败必须保留 Operation 并允许只重试数据库收尾。
- 兼容 ChatSession 与 Message 的关系需按 v12 Schema 实际核对，无法证明独占时保留 Session。
- 大范围 UI/Coordinator 接线容易绕过门禁，架构测试必须枚举所有正式写入口。

## 25. 回滚

合并前失败保持 Draft，在当前分支最小修复。未来合并后严重回归只关闭入口和停止新调度，保留 v12、Operation 和已存在数据，使用前向修复；不降级、不删除 `12.json`、不 destructive migration、不递归清音频目录。

## 26. PR09-13A 交接

完成时创建 `docs/planning/pr-09-12-interface-handoff.md`，冻结 v12 四类实体、生命周期状态、备份纳入边界、来源被清除后的关系语义、Audio 根目录、Orphan 边界、失败 Operation 备份策略，以及备份/替换与 Purge 的并发门禁。PR09-13A 在本 PR 合并前不得开始。
