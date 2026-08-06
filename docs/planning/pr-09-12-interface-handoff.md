# PR09-12 接口交接：归档、回收站与彻底清理

> 状态：PR09-12 Draft 交接文档。PR09-13A 只有在 PR09-12 完成本地严格只读验收、获得用户授权并实际合并后才能开始正式设计或开发。

## 1. 基线与 Schema 所有权

- PR09-12 开发基线：`main@8c4f3df5510ff7c3fb36088c0867c521fdb16980`。
- PR09-12 是本轮唯一 Room Schema 所有者。
- Room 从 v11 连续升级到 v12；禁止降级、删除历史 Schema 或使用 destructive migration。
- v12 新增正式表：
  - `issue_archive_events`
  - `issue_resume_events`
  - `issue_relations`
  - `issue_purge_operations`
- KSP 真实生成的 `12.json` 必须作为 PR09-12 合并事实保留。
- PR09-13A 及后续 PR 不得重新定义第二套 Issue 生命周期、归档事件、关系或 Purge 状态。

## 2. Issue Lifecycle 正式语义

### 2.1 生命周期状态

- `ACTIVE`：允许正常业务写入。
- `ARCHIVED`：默认只读；允许恢复原议题、创建关联新议题、移入回收站和播放已有 AVAILABLE 音频。
- `TRASHED`：只允许影响查看、恢复、彻底清除与清理状态处理。
- `purgeRequestedAt != null` 或存在 `IssuePurgeOperationEntity`：冻结该 Issue 的全部新业务写入。

回收站没有自动过期字段、倒计时、定时清理 Worker 或容量不足自动清除逻辑。

### 2.2 写入门禁

最终门禁不只存在于 UI：

- `LifecycleGatedRepositoryComponent` 覆盖 Stage、Run、点名、交叉讨论、草稿、成果、Context Usage 与 Trash/Restore。
- `LifecycleAwareAudioAssetRepository` 覆盖音频生成、重试和迟到成功。
- UI 只用于解释和禁用入口；深链、旧状态、迟到回调和非 UI 调用仍必须通过 Repository/Service 门禁。
- STOPPED、FAILED、CANCELED 等终态收敛操作允许继续写入，避免生命周期冻结阻止任务安全结束。

PR09-13A 的备份创建、导入或恢复操作必须复用同一生命周期判定，不得绕过。

## 3. Archive Event

`IssueArchiveEventEntity` 每次成功归档创建一条不可变记录：

- 同一 Issue 可多次归档；每次使用独立 `archiveOperationId`。
- 相同 operationId + payloadHash 幂等返回既有事件。
- 相同 operationId + 不同 payloadHash 返回 IdempotencyConflict。
- 简报由本地确定性事实生成，可由用户在最终确认前编辑。
- 打开、返回、取消、等待活动任务均零写入。
- Archive Event 与 Lifecycle `ACTIVE → ARCHIVED` 在同一 Room 事务中提交。
- 旧 Archive Event 不覆盖、不改写。
- 简报不包含 API Key、未确认编辑器内存或默认全量 Message 正文。

备份策略：未被 Purge 的 Archive Event 属于 Issue 正式历史，可进入备份；已彻底清除的 Event 不得通过旧备份索引或缓存重新出现。

## 4. Resume Event

`IssueResumeEventEntity` 记录归档议题恢复时的“现在有什么变化”：

- Change Note 必填，或用户显式选择“暂无变化”。
- 恢复不改写 Archive Event。
- Resume Event 与 Lifecycle `ARCHIVED → ACTIVE` 在同一 Room 事务中提交。
- 恢复不创建 Stage、Run、模型调用、音频任务、资料选择、个人背景选择或网络授权。
- 进程重建不得自动恢复；只恢复编辑或已持久化状态。

备份策略：未被 Purge 的 Resume Event 可随 Issue 进入备份。

## 5. Issue Relation

`IssueRelationEntity` 当前至少支持 `CONTINUATION`：

- 目标 Issue 是独立主线，原子创建目标 Issue、唯一初始 Stage、ACTIVE Lifecycle 与 Relation。
- 不复制全部 Message、Run、Participant Snapshot、Draft、Artifact、Material 正文或 Personal Context。
- Relation 保存 `sourceIssueId`、`targetIssueId` 与 `sourceArchiveEventId`。
- `targetIssueId` 使用 RESTRICT，避免来源清理误删目标。
- 来源 Issue 被彻底清除时：
  - `sourceIssueId = null`
  - `sourceArchiveEventId = null`
  - `sourcePurgedAt` 记录清理时间
  - 目标 Issue 与 Relation 继续存在
- 来源被清除后，UI 只能显示“来源议题已清除”，不得缓存或显示已清除标题、简报或正文。

备份策略：

- 备份目标 Issue 时可保存降级后的 Relation。
- 若来源已清除，只导出 `sourcePurgedAt` 与关系类型，不导出历史来源标题或内容。
- 导入不得根据空 source ID 重新创建来源 Issue。

## 6. Purge Operation

`IssuePurgeOperationEntity` 是不可恢复清理的唯一持久化状态机：

- `REQUESTED`
- `WAITING_FOR_TASKS`
- `CANCELING_TASKS`
- `DELETING_FILES`
- `READY_FOR_DATABASE_PURGE`
- `DATABASE_PURGING`
- `FAILED_RETRYABLE`
- `COMPLETED`（正式数据库成功后 Operation 与 Issue 同时消失，枚举仅用于状态表达兼容）

正式字段包括 operationId、payloadHash、impactHash、失败阶段、稳定错误码与 retryCount。

### 6.1 Idempotency

- 相同 operationId + 相同 payload：复用已有 Operation。
- 相同 operationId + 不同 payload：IdempotencyConflict。
- Worker Data 只包含 `purge_operation_id`。
- WorkManager 使用唯一 Work + `ExistingWorkPolicy.KEEP`。
- 进程恢复只根据 Room 中既有 Operation 恢复同一 Work，不重新计算用户意图、不创建第二个 Operation。

### 6.2 安全取消

- 只允许在正式文件删除开始前取消。
- `REQUESTED`、`WAITING_FOR_TASKS`、`CANCELING_TASKS`，以及文件阶段前的特定可重试失败可取消。
- `DELETING_FILES`、`READY_FOR_DATABASE_PURGE`、`DATABASE_PURGING` 或文件/数据库失败不得伪装为可完整取消。
- 取消在 WorkManager 与 Room 之间使用两次持久化状态核验；若竞态已推进，恢复调度同一 Operation。

## 7. 影响预览

影响预览由 `IssuePurgeImpactCalculator` 从实际 Room 与正式 Audio 生命周期服务读取：

- Stage、Stage Advancement、Measure、Skill 阵容、Material/Artifact 继承关系。
- ExecutionRun、Participant Snapshot/State、Budget、Message、Pending Message。
- Cross Discussion、Message/Material/Personal Context Usage。
- Draft、Revision、Confirmed Artifact、Artifact Source。
- AudioAsset、正式文件、临时文件、Pending Work、Missing 文件。
- Archive/Resume/Relation。
- 兼容 ChatSession。
- 外部或不可删除对象数量。

Impact Hash 使用确定性排序和稳定字段；影响变化要求重新查看并确认。

PR09-13A 备份预览不得复用 Purge Impact Hash 作为备份内容 Hash；两者用户意图和数据范围不同。

## 8. 音频文件所有权

正式音频文件根由 `AudioFileStore(File(context.filesDir, "jianyu-audio"))` 管理。

Purge 必须复用：

- `listAudioAssetsForIssue()`
- `reconcileFilesForIssue()`
- `inspectPurgeImpact()`
- `requestDelete()`
- `AudioFileStore.removeCommitted()`
- `AudioFileStore.removeTemporaryFilesForAsset()`

禁止：

- `deleteRecursively()`
- 根据文件名猜 Issue
- 信任绝对路径或外部输入路径
- 清空整个音频根目录
- 接回旧 Message.audioFilePath 清理链
- 迟到 Gateway 成功把 CANCELED/PURGE_REQUESTED/DELETED 恢复为 AVAILABLE

PR09-13A 备份若包含正式音频文件，必须从 AudioAsset 正式记录和受控相对路径枚举；不得扫描目录猜关联。

## 9. Orphan 边界

- Orphan 文件只进入独立报告。
- Orphan 不自动绑定到某个 Issue 的 Purge。
- Orphan 不在 Issue Purge 中自动删除。
- Orphan 不因容量不足自动删除。
- PR09-13A 不得默认把 Orphan 视为可备份对象；只有未来独立的用户确认流程可处理。

## 10. App 自有附件与外部 URI

PR09-12 未发现独立的正式 App 附件实体；本轮实际物理文件类型是 AudioAsset 正式文件和受控 `.part` 临时文件。

- `MaterialReference.sourceLocator` 不是 App 自有文件所有权证明。
- 不读取、不复制、不删除用户外部 URI 或原始文件。
- PR09-13A 若需要备份外部引用，只能备份引用元数据和授权状态，不得擅自读取外部内容。

## 11. 数据库清理事务

`IssuePurgeDatabaseCleaner` 在单一 Room 事务中按实际 FK 图删除：

1. 降级来源 Relation，删除当前 Issue 作为目标的 Relation。
2. Artifact Source、Message Usage、Cross Discussion、AudioAsset。
3. Stage Advancement 子表与主表。
4. Participant State、Budget、Message、Participant Snapshot、Run 自引用与 Run。
5. Artifact、Draft Revision、Draft。
6. Material Usage、Material Reference、Personal Context Usage。
7. Stage。
8. Resume Event、Archive Event。
9. 仅在无其他合法引用时删除兼容 ChatSession。
10. Lifecycle、Purge Operation、Issue。
11. 事务提交前执行 `PRAGMA foreign_key_check`。

任一 SQL 失败整体回滚。正式文件已删除但数据库失败时：

- Issue、Lifecycle、Operation 和数据库事实保留。
- Operation 记录 `DATABASE_PURGE` 与稳定错误码。
- 重试只进入数据库阶段，不重复正式文件删除。
- UI 明确显示“文件已清理，数据库收尾失败”。

## 12. 全局数据保护

Issue Purge 不得删除：

- `PersonalContextEntry`
- 官方 Skill Catalog
- 官方 Skill 组合
- API Key
- 其他 Issue
- 其他 Issue 的 AudioAsset/文件
- 备份文件
- 用户外部 URI 或原始文件

只删除 `PersonalContextUsageSnapshot` 等被清除 Issue 的使用事实。

## 13. 备份期间并发门禁

PR09-13A 必须建立显式互斥：

- 备份创建期间，目标 Issue 不得开始新的 Purge。
- Purge 已请求或执行中的 Issue 不得进入新备份。
- `FAILED_RETRYABLE` Purge Operation 默认不得作为“可完整恢复 Issue”备份；若未来允许导出诊断，只能导出稳定状态码和阶段，不导出已删除文件的虚假存在状态。
- 备份读取开始前与完成提交前必须重新核对 Lifecycle/Purge Operation 与数据版本。
- 备份过程中影响范围变化必须失败并要求用户重试，不得产生混合快照。

## 14. 恢复替换期间并发门禁

PR09-13A 的恢复替换期间：

- 禁止启动 Purge。
- 已存在的 Purge Work 必须先安全终止或等待明确终态。
- 不得覆盖或删除 `FAILED_RETRYABLE` Operation 后伪装完整恢复。
- 不得将备份中的旧 `purgeRequestedAt` 直接恢复为新用户意图。
- 不得恢复已彻底清除的 Issue、Archive/Resume Event、正文或文件。
- 不得根据 Relation 中已清除来源重建来源 Issue。

## 15. 不应导出的敏感字段

备份设计默认排除或加密隔离：

- API Key、Keystore 材料和密文实现细节。
- 完整 generation key。
- 完整 impact hash、payload hash 与内部幂等键，除非恢复协议明确需要且经过重新命名/隔离。
- 绝对文件路径。
- 未确认编辑器内存。
- 隐私日志与异常堆栈中的用户内容。
- 外部 URI 的授权令牌。

Archive Summary、Resume Change Note、Message、Draft、Artifact、Material 和 Personal Context 正文均属于用户数据，若进入备份必须受备份加密、用户确认和数据范围说明保护。

## 16. 已清除数据不可复活

PR09-13A 必须保证：

- 已成功 Purge 的数据不出现在新备份中。
- 备份索引、缓存、缩略图、音频清单或关系标题不得保留已清除内容。
- 恢复旧备份时必须应用明确冲突策略；不得静默覆盖现有数据或恢复已清除事实。
- 来源 Relation 已降级时，备份/恢复继续保持“来源议题已清除”。

## 17. PR09-13A 启动门禁

PR09-13A 正式开始前必须同时满足：

1. PR09-12 Draft PR 完成本地严格只读验收。
2. Room v1→v12 与 v11→v12 连续 Migration 真实通过。
3. 全量 Instrumentation、设备语义场景和 CI 证据无阻断失败。
4. 用户明确授权 PR09-12 标记 Ready 与合并。
5. PR09-12 已实际合并到 main。
6. 新对话重新读取本交接、PR #50 最终 Head、合并 Commit 与 main 当前状态。

在以上条件满足前，PR09-13A 不得创建生产分支、Schema 设计或正式 PR。
