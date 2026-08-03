# PR09-03：见域 Repository、事务边界与进程恢复实施计划

> **执行方式：** Superpowers 插件接口未调用；本任务读取仓库内固定保存的 Superpowers 6.2.0 Skill 文件，并按照项目适配规则执行等价人工流程。使用 `executing-plans` 串行实施，不使用 Worktree、子智能体、并行代理、Visual Companion、自动合并或自动删除分支。

**目标：** 在 Room v7 不发生 Schema 漂移的前提下，建立见域领域唯一业务写入口、数据库级跨 DAO 事务、幂等写入、统一错误模型和无副作用进程恢复接口。

**架构：** 选择“公共领域门面 + Repository 专用内部 DAO + 单一数据库事务协调器”。公共 `JianyuRepository` 只暴露领域命令与稳定恢复模型；`RoomJianyuRepository` 使用 `RoundtableDatabase.withTransaction` 协调 `JianyuRepositoryDao` 的跨表读写。旧 `ChatRepository` 保留旧 Session、旧消息流、Pending 更新和真实音频文件删除职责，新见域写入不得经由旧 Repository。

**技术栈：** Kotlin 2.0.21、Room v7、Room KTX `withTransaction`、协程、JUnit 4、Android Instrumentation。

## 一、实际基线与上游输入

- 仓库：`elio-zwd/AI-Skill-Roundtable`
- Base 分支：`main`
- 实际 Base SHA：`c8fda6979f07c619cd71210a0d58841adc9bfd88`
- 开发分支：`feat/pr-09-03-jianyu-repository-recovery`
- PR #32 合并 Commit：`3dc8733c074d6d96b55e71e8403efd5b0fbefd31`
- PR #33 合并 Commit：`c8fda6979f07c619cd71210a0d58841adc9bfd88`
- Room：v7，禁止升级 v8。
- 开放 PR：开始实施时为 0；未发现 Repository、DAO、Database、Entity、Schema、ViewModel、执行链或根导航冲突。

PR #32 输入契约：`IssueEntity`、`StageEntity`、`ExecutionRunEntity`、`ExecutionParticipantSnapshotEntity`、Message 四个可空领域关联、`roundIndex` 仅表示响应批次。

PR #33 输入契约：资料与个人背景当前对象/不可变使用快照、当前草稿/不可变修订、确认成果/来源链、音频资产元数据、官方 Skill 组合、Issue 生命周期。

## 二、现有调用链与直接 DAO 审计

当前 `RoundtableViewModel` 在构造阶段直接创建 `ChatRepository(database.chatDao())`，并通过 `RoundtableDatabaseGateway` 委托旧消息读取、`REPLACE` 插入、Pending 更新和删除。启动时还调用 `removeAllPendingMessages()`，该行为属于旧执行链，PR09-07 前不迁移。

当前生产代码未直接调用 `coreDomainDao()` 或 `resourceLifecycleDao()`；这两个 DAO 目前主要供 Schema、Migration 和数据库测试使用。`ChatRepository` 负责：

1. `ChatSession` 创建、标题更新和删除；
2. 旧消息读写与 Pending 文本更新；
3. Session 删除时清除 `InteractionChainStore`；
4. Session 删除时删除 Message 音频真实文件；
5. 消息音频字段更新。

PR09-03 不改写上述旧 UI/执行链，也不把文件删除迁入新 Repository。

## 三、Repository 方案比较与选择

### 方案 A：单一大型 Repository

优点是调用入口单一、事务集中；缺点是接口会同时拥有 Issue、Stage、Run、消息、资料、背景、草稿、成果、Skill 组合和生命周期，迅速形成 God Object，后续 PR09-04、05、07、09、10A、12 修改冲突大，测试替身过重。否决。

### 方案 B：按领域拆分多个公共 Repository

优点是职责清晰；缺点是调用方可能重新组合多个 Repository 完成一次业务写入，跨 Repository 原子性、幂等和错误模型容易漂移，违反“调用方不能自行组合 DAO/Repository 完成业务写入”。否决作为公共 API。

### 方案 C：公共门面 + 内部领域组件 + 单一事务协调器

公共 `JianyuRepository` 按业务操作暴露方法；内部使用单个 `JianyuRepositoryDao` 承载必要查询、CAS 更新和安全插入；`RoomJianyuRepository` 是唯一事务协调器。接口按命令分组但不让调用方组合 DAO。选择该方案。

## 四、恢复方案比较与选择

- 单次巨大 DTO：一致性最好，但首次加载和测试过重。
- 完全分层读取：灵活，但多个调用容易跨时间点拼出不一致状态。
- 核心恢复快照 + 分组资源集合：在一次 `withTransaction` 中读取 Issue、Lifecycle、Stage、Run、参与者、消息和 Pending；同时读取草稿、成果、资料/背景使用和音频元数据，按 `IssueRecoverySnapshot.core` 与 `resources` 分组返回。选择该方案。

恢复读取不创建或更新任何行，不改变 Run 状态，不删除 Pending，不创建 Stage/Run；重复读取只要数据库未变化必须结构相等。

## 五、公共接口与错误模型

创建 `JianyuRepositoryContract.kt`：

- `RepositoryResult<T>`：`Success(value, idempotent)` / `Failure(error)`。
- `RepositoryError`：`NotFound`、`AlreadyExists`、`IdempotencyConflict`、`InvalidState`、`ConstraintViolation`、`StorageFailure`、`CompatibilityFailure`。
- 错误对象只包含资源类型、稳定 ID、错误代码和是否可重试，不包含消息正文、资料正文、个人背景正文、成果全文、Key 或 Prompt。
- `OfficialSkillIdValidator`：PR09-05 注入正式目录校验；测试注入确定集合。生产默认不得“全部允许”。
- 命令由调用方提供稳定 ID、时间戳和幂等标识；Repository 不依赖真实时间等待和随机碰撞。

公共操作：

1. `saveIssue(command)`；
2. `createStage(command)`；
3. `undoLatestUnrunStage(issueId, stageId)`；
4. `createExecutionRun(command)`；
5. `appendDomainMessage(command)`；
6. `transitionRun(command)`；
7. `saveStageDraft(command)` / `abandonStageDraft(issueId, stageId)`；
8. `confirmArtifact(command)`；
9. `recordMaterialUsage(entity)` / `recordPersonalContextUsage(entity)`；
10. `saveOfficialSkillCombination(command)` / `deleteOfficialSkillCombination(command)`；
11. `archiveIssue`、`restoreIssue`、`moveIssueToTrash`、`restoreIssueFromTrash`、`requestIssuePurge`；
12. `recoverIssue(issueId)`；
13. `listIssueNavigation(states)`；
14. `getOfficialSkillCombination(id)` / `listOfficialSkillCombinations()`。

## 六、事务与幂等策略

所有跨表写操作使用 `RoundtableDatabase.withTransaction`。事务中禁止网络、Gemini、文件复制/删除、遥测和日志正文。

无新增命令表时，幂等使用现有唯一键与调用方稳定 ID：

- Issue：`issueId`；相同 ID 同 payload 返回幂等成功，不同 payload 返回冲突。
- Stage：`stageId`；`sequenceIndex` 在事务内读取 `MAX + 1`，唯一索引兜底。
- Run：`idempotencyKey`；同 key 同 Run/参与者 payload 返回原记录，不同 payload 冲突。
- Message：要求新领域消息提供大于 0 的稳定 `messageId`，使用 `ABORT` 插入，不使用 `REPLACE`。
- 草稿：`revisionId` + `(issueId, stageId, revisionNumber)`；当前修订必须连续。
- 成果：`artifactId`；同 ID 同正文和来源幂等，不同内容冲突。
- 资料/背景使用：快照 `id`；只有 `userConfirmedAt > 0` 才允许。
- 生命周期：重复目标转换返回幂等成功。
- Skill 组合：创建使用组合 ID；更新使用 `expectedUpdatedAt` 做 CAS；删除为软删除。

## 七、仅保存议题

`saveIssue` 在单一事务内插入：

1. `IssueEntity`；
2. `StageEntity(sequenceIndex = 0)`；
3. `IssueLifecycleEntity(state = active)`。

不创建 Run、参与者、资料/背景使用、草稿、成果、音频或 `ChatSession`。任何一步失败全部回滚。

`Issue` 是新领域事实源。旧 `ChatSession` 只在首次新领域消息写入时，为满足现有 `Message.chatId NOT NULL` 兼容约束，在同一事务内按需创建，并写回 `Issue.legacyChatSessionId`；消息写入失败时会话与 Issue 更新一并回滚。兼容层最晚在 PR09-07 完成新执行链、且后续 Schema PR 允许移除 `chatId` 强依赖后删除。

## 八、Stage 创建与撤销

`createStage`：

- 校验 Issue 存在；
- 同 `stageId` 同 payload 幂等；
- 在事务内读取当前最大 `sequenceIndex` 并加一；
- 不创建 Run，不修改上一 Stage；
- 唯一索引防并发重复顺序。

`undoLatestUnrunStage`：

- 目标必须是 Issue 最新 Stage；
- 统计 Run、Message、当前草稿、草稿修订、成果、资料引用/使用、背景使用、音频资产；
- 任一依赖存在则返回 `InvalidState`；
- 删除 Stage，不重排历史；
- Issue 初始 Stage（sequenceIndex 0）禁止撤销。

## 九、Run、参与者、消息与状态

`createExecutionRun` 同事务插入 Run 与参与者快照。参与者 position、sourceType/sourceId 必须唯一并稳定排序。只持久化，不调用模型。

`appendDomainMessage`：

- 校验 Issue/Stage；
- 有 Run 时校验 Run 属于同 Issue/Stage；
- 成员消息必须提供且匹配参与者快照；用户消息必须不提供参与者；
- `roundIndex >= 0`，仅保存响应批次；
- 使用稳定 Message ID + `ABORT`；重试不能覆盖成功消息；
- 按需创建兼容 ChatSession，但事实源仍是 Issue。

`transitionRun` 使用单条 CAS SQL：只有数据库旧状态属于 `expectedStatuses` 才更新。0 行时重新读取，区分不存在、已到目标状态幂等、旧状态冲突。Repository 不自动把 `running` 改为 `failed`。

## 十、草稿、成果、资料、背景与组合

草稿保存：先检查当前修订号；首版必须为 1，后续必须 `current + 1`；修订与当前草稿同事务。放弃只删除当前草稿，修订和成果不动，重复放弃幂等。

成果确认：验证 Issue/Stage、消息、Run、草稿修订和资料快照全部属于同一 Issue，成果和全部来源连接同事务；失败不删除草稿。

资料/背景使用：只接受 `userConfirmedAt > 0`；当前对象编辑不会改写已保存快照；日志不记录正文。

官方 Skill 组合：保存前通过注入的 `OfficialSkillIdValidator` 校验全部 ID；拒绝重复 ID/position；默认职责只保存在组合成员，不自动写入 Run 参与者快照。更新用 `expectedUpdatedAt`；删除只设置 `deletedAt`，不修改历史 Run 快照。

## 十一、生命周期转换表

- `active -> archived`
- `active -> trashed`，`previousState = active`
- `archived -> active`
- `archived -> trashed`，`previousState = archived`
- `trashed -> previousState`，previous 为空时回 active
- 重复 archive、重复 trash、重复恢复到当前目标为幂等成功
- `requestPurge` 仅允许 trashed，只设置 `purgeRequestedAt`

不自动过期、不后台清空、不停止 Run、不删除数据库行、不删除音频文件。

## 十二、文件级实施清单

### 新增生产文件

- `app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt`：公共命令、结果、错误和恢复模型。
- `app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt`：Repository 唯一内部 DAO，安全插入、查询、依赖统计、CAS 和恢复读取。
- `app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt`：公共门面实现、事务协调、幂等和错误映射。

### 修改生产文件

- `app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt`：仅增加 `jianyuRepositoryDao()` getter；版本、Entity、Migration、Schema 不变。

### 新增测试文件

- `app/src/test/java/com/elio/jianyu/data/JianyuRepositoryContractTest.kt`：错误模型、生命周期纯函数、成员校验和敏感信息边界。
- `app/src/test/java/com/elio/jianyu/data/JianyuRepositoryArchitectureTest.kt`：Room v7、无新 Schema、禁止 ViewModel/UI 直接访问领域 DAO、Repository 不引用网络/Gemini/文件删除。
- `app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt`：事务、幂等、故障回滚、恢复、生命周期和外键检查。

### 文档

- `docs/planning/pr-09-03-repository-recovery-plan.md`：本计划。
- `docs/planning/pr-09-03-interface-handoff.md`：完成后冻结 PR09-04/05 接口、共享 Commit SHA 和文件所有权。

## 十三、TDD 顺序与首个失败测试

首个失败测试创建 `RoomJianyuRepositoryDatabaseTest.saveIssueCreatesOnlyIssueStageAndLifecycleAtomically`，在生产类型不存在时应编译失败；随后补公共契约、DAO 和最小实现。

后续测试顺序：

1. saveIssue 三表事务、幂等、payload 冲突、故障回滚；
2. Stage 顺序、并发唯一、撤销依赖门禁；
3. Run/参与者事务和 idempotencyKey；
4. Message 关系、ABORT、不覆盖成功、Pending 重开；
5. Run CAS 与恢复无副作用；
6. 草稿修订、放弃和成果来源回滚；
7. 资料/背景确认、组合校验；
8. 生命周期转换和 requestPurge；
9. 数据库关闭、外键/唯一约束、中途异常、`PRAGMA foreign_key_check`；
10. 架构守卫。

远端无可用 Android 本地环境时，RED/GREEN 只记录“测试文件先于生产实现提交”和 GitHub CI 编译/单测证据，不虚构本地执行。

## 十四、Commit 边界

1. `docs: 制定PR09-03仓储与恢复实施计划`
2. `test: 增加Repository事务与幂等失败场景`
3. `feat: 建立见域领域Repository`
4. `feat: 增加进程恢复与生命周期事务`
5. `test: 完善Repository设备与架构约束`
6. `docs: 冻结PR09-04与PR09-05消费接口`

若 GitHub API 为保持单次文件创建原子性产生更少 Commit，仍要求每个 Commit 只有一个清晰意图，不夹带无关格式化。

## 十五、验证与 CI

远端静态核对：

- `RoundtableDatabase.version == 7`；
- Entity、Migration、Schema JSON 无差异；
- 新业务写入只经 `JianyuRepository`；
- 事务使用 `withTransaction`；
- Repository 不引用 network、Gemini、WorkManager、`java.io.File.delete`；
- 恢复方法只调用 SELECT；
- Message 新入口使用 ABORT；
- 无敏感正文日志。

GitHub CI 必须读取精确 Head 的 Secret scan、Android CI、身份门禁、Kotlin 编译、JVM 单测、Lint、Debug、Release/R8、Room Schema 漂移和 Artifact。Instrumentation 未由 CI 真实运行时明确列为未验证。

本地验收执行定向 Repository/Recovery/Transaction/Idempotency/Architecture 测试、完整构建、模拟器 Instrumentation、强制停止重启和外键检查。

## 十六、PR09-04 / PR09-05 消费接口与所有权

PR09-04 可消费：

- `listIssueNavigation(states)`；
- `recoverIssue(issueId)` 中的生命周期、Stage 列表、当前 Stage、稳定 Issue/Stage/Run ID；
- archive/trash/restore 的 Repository 操作；
- 只读定位不得创建数据。

PR09-05 可消费：

- `listOfficialSkillCombinations()`；
- `getOfficialSkillCombination(id)`；
- `saveOfficialSkillCombination(command)`；
- `deleteOfficialSkillCombination(command)`；
- `OfficialSkillIdValidator` 正式实现注入点。

PR09-04 独占：根导航、Route、App 入口、返回栈测试。

PR09-05 独占：Skill Catalog、列表/详情、目录数据、组合展示和正式 ID Validator。

双方禁止修改：Repository 公共接口、Repository 实现、Database、Entity、DAO、Room Schema。需要修改共享接口时恢复串行并回到 PR09-03 后续修正 PR。

## 十七、禁止触碰文件与范围

不修改 Entity 字段、Migration、Schema JSON、Gradle 依赖、Manifest、导航、首页、Skill Catalog、Gemini 调度、ViewModel 执行状态机、音频生成/转码/文件删除、永久清理、正式视觉和发布配置。

## 十八、风险、回滚与未验证项

风险：

1. Message 仍有非空 `chatId`，通过按需兼容 Session 暂时桥接；不能让旧 Session 成为新 Issue 事实源。
2. 无命令表时部分幂等依赖调用方稳定 ID；接口文档必须要求重试复用 ID。
3. Stage 并发依赖 SQLite 写事务串行化和唯一索引；设备端需真实并发验证。
4. 恢复聚合查询表较多；首次加载性能由 PR09-04/07 在真实数据量下测量。
5. 官方 Skill 正式 Validator 尚未实现；PR09-05 必须注入，不能使用允许全部的生产实现。

回滚：整 PR 回滚即可；无 Schema、Migration 或文件系统副作用，不需要数据降级脚本。

未验证：远端未实际执行本地 Gradle、模拟器或设备测试；相关项目等待本地 AI 严格只读验收。GitHub CI 结果仅在精确 Head 产生后记录。
