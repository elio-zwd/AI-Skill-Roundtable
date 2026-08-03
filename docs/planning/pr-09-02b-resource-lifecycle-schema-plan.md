# PR09-02B：见域资源与生命周期 Schema 实施计划

> **执行方式**：Superpowers 插件接口未调用；本计划读取仓库内固定保存的 Superpowers 6.2.0 `brainstorming`、`writing-plans`、`executing-plans`、`test-driven-development`、`systematic-debugging`、`verification-before-completion`、`requesting-code-review` 与 `finishing-a-development-branch` Skill 文件，并按项目适配规则执行等价人工流程。

**目标**：在保持 PR09-02A 核心领域语义、现有 Message 事实源和旧音频调用链不变的前提下，将 Room v6 连续升级为 v7，建立资料、个人背景使用、阶段总结草稿、正式成果、音频资产、官方 Skill 组合、议题归档与回收站的持久化契约。

**架构选择**：采用“当前对象与不可变使用快照分离、草稿当前态与修订分离、成果来源使用受约束连接表、音频资产不在本次迁移回填、议题生命周期使用独立一对一记录”的组合方案。v6→v7 只为既有 Issue 确定性补建 `active` 生命周期记录，不伪造资料、背景使用、草稿、成果、音频或 Skill 组合历史。

**技术栈**：Kotlin 2.0.21、Room、KSP、SQLite、JUnit 4、AndroidX Room Testing、Android Instrumentation。

## 1. 实际基线与能力核验

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base 分支：main
实际 Base SHA：3dc8733c074d6d96b55e71e8403efd5b0fbefd31
开发分支：feat/pr-09-02b-resource-lifecycle-schema
Room：v6 → v7
```

开始时已核验：

- PR #32 已合并，合并 Commit 为 `3dc8733c074d6d96b55e71e8403efd5b0fbefd31`；
- 当前没有开放 PR；
- 没有其他开放分支通过 PR 修改 `RoundtableDatabase.kt`、Entity、DAO、Schema、Migration 或数据库测试；
- GitHub 连接具备读取、创建分支、写文件、创建 Commit、创建 Draft PR、读取 CI 与 Artifact 的能力；
- 当前没有本地 Android/Gradle/模拟器执行环境，远端只能依靠 GitHub CI 和静态核对，设备测试交给本地 AI。

## 2. PR09-02A 输入契约

本任务保持以下不变量：

- `IssueEntity` 是持续议题容器；
- `StageEntity` 是 Issue 内按 `sequenceIndex` 排序的推进节点；
- `ExecutionRunEntity` 是显式一次运行，`idempotencyKey` 全局唯一；
- `ExecutionParticipantSnapshotEntity` 保存运行时参与者历史快照；
- `Message` 仍是唯一消息事实源；
- `roundIndex` 只表示响应批次，不表示 Stage；
- `Message.issueId / stageId / executionRunId / participantSnapshotId` 的可空兼容窗口保留到 PR09-03；
- `Message.audioFilePath / audioFormat / audioSizeBytes` 保留，当前音频生成、播放和删除调用链不在本 PR 改写；
- PR09-03 之前不新增完整 Repository 或业务状态机。

## 3. 当前关系图

```text
ChatSession（兼容容器）
    1 ── 0..1 Issue

Issue
    1 ── N Stage

Stage
    1 ── N ExecutionRun

ExecutionRun
    1 ── N ExecutionParticipantSnapshot

Message
    ├── 0..1 Issue
    ├── 0..1 Stage
    ├── 0..1 ExecutionRun
    └── 0..1 ExecutionParticipantSnapshot
```

## 4. 方案比较与最终选择

### 4.1 资料引用

**方案 A：当前资料实体 + 每次实际使用快照（采用）**

- 当前资料可编辑、停用、普通删除；
- 每次带入 Stage/Run 时写入完整不可变快照、来源元数据、内容哈希和用户确认时间；
- 当前资料编辑或普通删除不会改写历史；
- 彻底清除时可将快照正文置空并标记 `purged`，保留匿名占位、哈希和关系。

**方案 B：每次使用只保存独立资料快照（不采用）**

- 历史可靠，但无法表达全局资料库当前状态；
- 重复数据更多，PR09-09 的编辑、停用和复用成本更高。

最终采用方案 A，兼顾历史可解释性、PR09-09 接入和彻底清除。

### 4.2 个人背景使用

比较结果：

- 仅保存版本引用或哈希不足以解释历史消息；
- 永久保留全部敏感正文不符合彻底清除要求；
- 采用“当前背景条目 + 实际使用快照 + 可清除正文状态”。

使用快照只有用户明确确认后才可创建；当前背景编辑、停用或普通删除不会改写快照。彻底清除由后续事务将 `contentSnapshot` 置空、`contentState` 改为 `purged`，保留不泄露原文或敏感类别的占位关系。

### 4.3 阶段总结草稿

比较：

- 每 Stage 单一记录：简单，但没有修订历史；
- 只使用版本号：无法在放弃当前草稿后保留可追溯修订；
- 当前草稿 + 独立修订记录：可恢复、可审计、不会把草稿当成果。

采用第三种。`stage_summary_drafts` 每个 Stage 只有一个当前草稿；`stage_summary_draft_revisions` 保存不可变修订快照。Schema 不包含 `expiresAt`、TTL、自动清理触发器或后台过期字段。

### 4.4 正式成果来源链

比较：

- 在成果表直接放多个可空外键：简单，但难以表达多个来源；
- 通用多态来源表：扩展方便，但 SQLite 无法对不同源表建立真实外键；
- 按来源类型建立连接表：表较多，但可以通过复合外键拒绝跨 Issue 来源。

采用按来源类型连接表：消息、Run、草稿修订、资料使用快照分别建表。成果与来源共享 `issueId`，数据库拒绝把其他 Issue 的来源接入成果。

### 4.5 音频资产迁移

采用方案 B：建立 `audio_assets` 和关联能力，但 v6→v7 不回填旧 `Message` 音频字段。

原因：

- 当前旧音频由 `RoundtableViewModel`、`ChatRepository` 和转码任务直接读写 Message 字段及真实文件；
- Migration 不应检查、移动、删除或重命名文件；
- 立即回填会过早冻结 PR09-10B 的任务状态和缺失文件处理。

兼容边界：

- PR09-10B 合并前，旧 Message 字段仍是现有调用方的唯一事实源；
- 本 PR 新表没有生产写入方，不形成双写；
- PR09-10B 必须复用本 PR 的资产主键和关系，完成确定性导入、文件状态机与单一写入入口；
- 旧字段最早只能在 PR09-10B 完成迁移、回归和至少一个稳定验收周期后另行删除。

### 4.6 官方 Skill 组合

采用稳定官方 Skill ID，不建立到当前 `characters` 或未来 Skill Catalog 的数据库外键：

- V1 只保存组合名称、官方成员稳定 ID、顺序和可选默认职责；
- 成员表没有 System Prompt、自定义 Skill 正文或第三方脚本字段；
- `(combinationId, position)` 和 `(combinationId, officialSkillId)` 唯一；
- PR09-03 / PR09-05 通过唯一校验入口确认 ID 属于已发布官方目录；
- 本 PR 不创建平行 Skill Catalog。

### 4.7 生命周期状态

比较：

- 在 `issues` 增加字段：查询简单，但污染 PR09-02A 核心表并增加回滚成本；
- 独立一对一记录：保持核心表稳定，恢复语义清晰，PR09-12 可在单一事务更新；
- 通用事件表：审计能力强，但当前业务尚未需要完整事件溯源。

采用独立一对一 `issue_lifecycle`。状态为 `active / archived / trashed`；保留 `previousState`、状态时间戳和 `purgeRequestedAt`。没有自动过期、自动清空或触发器。实际归档、停止运行、恢复、回收站与永久删除事务由 PR09-12 实现。

### 4.8 状态存储方式

采用 Kotlin enum + Room TypeConverter，数据库存储稳定小写字符串。未知值由 Converter 明确拒绝，不静默降级。跨字段状态一致性由 PR09-03 / PR09-12 的事务入口保证。

## 5. 新关系图

```text
Issue
├── N MaterialReference
│   └── N MaterialUsageSnapshot ── 0..1 ExecutionRun
├── N Stage
│   ├── 0..1 StageSummaryDraft
│   ├── N StageSummaryDraftRevision
│   ├── N ConfirmedArtifact
│   │   ├── N ArtifactMessageSource
│   │   ├── N ArtifactRunSource
│   │   ├── N ArtifactDraftSource
│   │   └── N ArtifactMaterialSource
│   └── N AudioAsset ── exactly one of Message / ConfirmedArtifact
└── 1 IssueLifecycle

PersonalContextEntry
└── N PersonalContextUsageSnapshot ── Issue / Stage / 0..1 Run

OfficialSkillCombination
└── N OfficialSkillCombinationMember
```

## 6. Entity、字段与数据库约束

生产类型集中在：

```text
app/src/main/java/com/elio/jianyu/data/ResourceLifecycle.kt
```

Migration SQL 集中在：

```text
app/src/main/java/com/elio/jianyu/data/ResourceLifecycleMigration.kt
```

### 6.1 `material_references`

`MaterialReferenceEntity`：

- `id` 主键；
- `issueId`；
- `stageId?`；
- `title`；
- `sourceType`；
- `sourceLocator?`；
- `content`；
- `contentHash`；
- `sourcePublishedAt?`；
- `sourceCapturedAt?`；
- `createdAt / updatedAt`；
- `deletedAt? / purgeRequestedAt?`。

约束：Issue 外键；可空 Stage 复合外键；索引覆盖 Issue、Stage、来源定位和删除状态。

### 6.2 `material_usage_snapshots`

`MaterialUsageSnapshotEntity`：

- `id` 主键；
- `issueId / stageId / runId?`；
- `materialReferenceId?`，当前资料彻底删除时 `SET_NULL`；
- 标题、来源、内容、哈希快照；
- `contentState`；
- `userConfirmedAt` 非空；
- `createdAt`。

约束：Issue/Stage/Run 复合外键；唯一 `(runId, materialReferenceId)`；唯一 `(id, issueId)` 供成果来源连接。

### 6.3 `personal_context_entries`

`PersonalContextEntryEntity`：稳定 ID、标题、正文、哈希、启用状态、创建/更新时间、普通删除和彻底清除请求时间。表不含自动带入字段。

### 6.4 `personal_context_usage_snapshots`

`PersonalContextUsageSnapshotEntity`：

- 关联 Issue、Stage 和可空 Run；
- 可空 `personalContextEntryId`，当前条目删除时 `SET_NULL`；
- 标题、正文、哈希快照；
- `contentState`；
- `userConfirmedAt` 非空；
- `createdAt`。

约束：唯一 `(runId, personalContextEntryId)`；未确认背景不存在对应写入 API。

### 6.5 `stage_summary_drafts`

`StageSummaryDraftEntity`：ID、Issue、Stage、正文、当前 `revisionNumber`、创建/更新时间。唯一 `(issueId, stageId)`，不含过期字段。

### 6.6 `stage_summary_draft_revisions`

`StageSummaryDraftRevisionEntity`：ID、Issue、Stage、原草稿稳定 ID 快照、修订号、正文快照、创建时间。唯一 `(issueId, stageId, revisionNumber)`；唯一 `(id, issueId)` 供成果来源连接。修订表不对当前草稿建立删除级联外键，因此放弃当前草稿不会丢失历史修订。

### 6.7 `confirmed_artifacts`

`ConfirmedArtifactEntity`：

- ID、Issue、Stage；
- 标题、正文、成果类型、内容格式；
- `confirmedAt` 非空；
- `revisionOfArtifactId?` 自引用；
- 创建/更新时间。

约束：Stage 复合外键；修订父成果 `RESTRICT`；唯一 `(id, issueId)` 和 `(id, issueId, stageId)`。草稿和成果是不同表；导出文件不作为成果类型。

### 6.8 成果来源连接表

- `artifact_message_sources`；
- `artifact_run_sources`；
- `artifact_draft_sources`；
- `artifact_material_sources`。

每张表都携带 `artifactId + issueId`，并通过复合外键指向同一 Issue 的来源；唯一约束防止同一来源重复挂载。删除来源采用 `RESTRICT`，不得静默丢失成果历史。

### 6.9 `audio_assets`

`AudioAssetEntity`：

- 稳定 ID、Issue、Stage；
- `sourceMessageId? / sourceArtifactId?`；
- `storagePath` 唯一；
- MIME、格式、大小；
- `fileState`；
- 可空 `generationKey`，非空时唯一；
- 创建/更新时间；
- 普通删除与彻底清除请求时间。

数据库通过复合外键确保来源属于同一 Issue/Stage；Room DAO 事务在写入前强制两个来源恰好一个非空。文件缺失使用 `fileState=missing` 表达，不阻止数据库重开。

### 6.10 官方组合

- `official_skill_combinations`：ID、名称、启用状态、创建/更新时间、普通删除时间；
- `official_skill_combination_members`：组合 ID、官方 Skill ID、成员顺序、可空默认职责、创建时间。

成员没有 Prompt 正文字段；数据库唯一约束保证顺序唯一和 Skill 不重复。

### 6.11 `issue_lifecycle`

`IssueLifecycleEntity`：

- `issueId` 主键并外键指向 Issue；
- `state`，默认 `active`；
- `previousState?`；
- `stateChangedAt / updatedAt`；
- `archivedAt? / trashedAt? / purgeRequestedAt?`。

没有 `expiresAt`、清理计划、自动删除标记或 SQLite Trigger。

## 7. 需要修改的现有类型

### `CoreDomain.kt`

只新增供下游复合外键使用的唯一索引：

- `ExecutionRunEntity` 增加唯一 `(id, issueId)`。

不改变字段、状态和 DAO 语义。

### `ChatSession.kt`

只新增 Message 复合唯一索引：

- `(id, issueId)`；
- `(id, issueId, stageId)`。

不改变 Message 字段、`roundIndex`、音频字段或现有 DAO。

## 8. DAO 与事务边界

新增 `ResourceLifecycleDao`，提供：

- 当前资料、背景、草稿、修订、成果、来源、音频、组合和生命周期的插入与定向查询；
- `saveDraftWithRevision`：当前草稿和修订同事务写入；
- `createArtifactWithSources`：成果与四类来源连接同事务写入；
- `createAudioAsset`：校验恰好一个来源后插入；
- `createOfficialCombination`：校验成员 ID、顺序无重复后同事务插入；
- `get...` 查询供数据库测试和 PR09-03 消费。

数据库保证：父对象存在、Issue/Stage/Run 一致、来源跨 Issue 被拒绝、唯一约束、删除 RESTRICT、组合去重。

暂由 DAO/PR09-03 保证：

- 音频两个来源恰好一个；
- 成果修订只能指向较早成果，禁止业务更新制造循环；
- 官方 Skill ID 属于正式目录；
- 生命周期合法转换；
- `purgeRequestedAt` 之后的实际永久删除顺序；
- 当前资料/背景快照内容的最小化策略。

## 9. Room v6→v7 Migration

新增 `ResourceLifecycleMigration.MIGRATION_6_7`，并在 `RoundtableDatabase` 暴露 `MIGRATION_6_7`、注册新 Entity/DAO/Converter、更新 `ALL_MIGRATIONS`。

迁移顺序：

1. 创建当前资料和资料使用快照；
2. 创建个人背景和使用快照；
3. 创建当前草稿与草稿修订；
4. 创建成果；
5. 创建四类成果来源连接；
6. 创建音频资产；
7. 创建官方 Skill 组合与成员；
8. 创建议题生命周期；
9. 为每个既有 Issue 执行 `INSERT OR IGNORE` 的 `active` 生命周期回填，时间使用 `issues.updatedAt`；
10. 创建全部索引；
11. 不读取文件系统，不移动、删除或重命名音频文件；
12. 不清空或修改 Message 旧音频字段；
13. 不创建任何资料、背景、草稿、成果、音频或组合历史。

Migration 使用 `CREATE TABLE IF NOT EXISTS`、`CREATE INDEX IF NOT EXISTS` 与确定性回填；测试会在同一数据库上重复调用迁移对象，验证不产生重复生命周期记录。

## 10. TDD 与测试文件

### 首个失败测试

先创建：

```text
app/src/test/java/com/elio/jianyu/data/ResourceLifecycleModelTest.kt
```

首个 RED 场景引用尚不存在的 `IssueLifecycleState`、`SnapshotContentState`、`AudioFileState` 和输入校验函数，预期因生产类型缺失而编译失败。远端通过 Draft PR 当前 Head 的 GitHub CI 日志记录真实 RED；不把“已编写测试”写成“测试通过”。

### 单元与 Schema 契约测试

```text
app/src/test/java/com/elio/jianyu/data/ResourceLifecycleModelTest.kt
app/src/test/java/com/elio/jianyu/data/ResourceLifecycleSchemaContractTest.kt
```

覆盖：

- 状态存储值；
- 音频恰好一个来源；
- 组合成员重复 ID/顺序拒绝；
- 成果自修订拒绝；
- v7 表、列、索引、外键和默认值；
- 没有 `expiresAt`、TTL、自动清空 Trigger；
- v6 Schema 保持冻结；
- Message 旧音频字段仍存在。

### Instrumentation 数据库测试

```text
app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleDatabaseTest.kt
```

覆盖：

- 编辑/删除当前资料不改写历史快照；
- 编辑/删除背景不改写历史使用；
- 未确认背景没有写入入口；
- Issue/Stage/Run 错配被外键拒绝；
- 草稿关闭并重开数据库仍存在；
- 草稿无自动过期，不能自动成为成果；
- 放弃草稿不删除成果；
- 成果四类来源不能跨 Issue；
- 成果修订自引用拒绝；
- 音频合法来源、缺失文件状态、错误 Issue/Stage、重开关系；
- 组合顺序和 Skill 去重；
- 修改组合职责不改写参与者快照；
- 删除组合不改写历史 Run；
- active / archived / trashed 区分与恢复元数据；
- 回收站无自动过期；
- 所有孤儿记录拒绝；
- 事务失败回滚；
- `PRAGMA foreign_key_check` 为零。

### Migration 测试

```text
app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt
```

覆盖：

- 使用真实提交的 v6 Schema 执行 v6→v7；
- Character、Group、ChatSession、Message、Issue、Stage、Run、参与者快照全部保留；
- `roundIndex` 和 Message 旧音频字段和值不变；
- 新表、索引、外键和生命周期回填正确；
- 不伪造资料、背景、草稿、成果、音频和组合；
- v1/v2/v3/v4/v5 继续经 `ALL_MIGRATIONS` 到 v7；
- v6→v7 重复调用不产生重复生命周期记录；
- 数据库关闭重开和 Room Schema Validation；
- `PRAGMA foreign_key_check` 为零。

## 11. 文件级施工清单

### 新增

```text
docs/planning/pr-09-02b-resource-lifecycle-schema-plan.md
app/src/main/java/com/elio/jianyu/data/ResourceLifecycle.kt
app/src/main/java/com/elio/jianyu/data/ResourceLifecycleMigration.kt
app/src/test/java/com/elio/jianyu/data/ResourceLifecycleModelTest.kt
app/src/test/java/com/elio/jianyu/data/ResourceLifecycleSchemaContractTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleDatabaseTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt
app/schemas/com.elio.jianyu.data.RoundtableDatabase/7.json
```

### 修改

```text
app/src/main/java/com/elio/jianyu/data/CoreDomain.kt
app/src/main/java/com/elio/jianyu/data/ChatSession.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
tools/check-app-identity.ps1
```

### 禁止触碰

```text
app/src/main/java/com/elio/jianyu/viewmodel/
app/src/main/java/com/elio/jianyu/roundtable/
app/src/main/java/com/elio/jianyu/audio/
app/src/main/java/com/elio/jianyu/ui/
app/src/main/java/com/elio/jianyu/network/
app/src/main/res/
app/build.gradle.kts
根导航、正式 Skill 目录、Repository 业务事务、正式视觉与发布配置
```

`.github/workflows/android-ci.yml` 当前已具备编译、单测、Lint、APK、Release/R8 和 Schema 漂移门禁；除非真实 CI 证明必须适配，否则不修改工作流。

## 12. Commit 边界

```text
docs: 制定PR09-02B资源生命周期Schema计划
test: 增加资源生命周期Schema失败场景
feat: 建立见域资源与生命周期Schema
test: 完善Room资源生命周期迁移验证
fix: 对齐Room v7 Schema与身份门禁
```

GitHub Contents API 每次文件写入会产生文件级 Commit；同一逻辑阶段使用一致前缀和中文意图，PR 描述按上述逻辑边界汇总，不强制改写历史或强制推送。

## 13. 计划只读复核结果

已逐项复核：

- 没有 `TODO`、`TBD`、“实现时再看”或未选择方案；
- 每项目标语义都有明确 Entity、约束或后续事务边界；
- 资料与背景当前值和历史快照分离；
- 草稿与成果分表；
- 音频选择“不回填”，旧字段和文件不变；
- 官方组合没有自定义 Prompt 字段；
- 生命周期没有自动过期或自动清空；
- PR09-02A 核心表只增加下游复合外键所需索引；
- 没有完整 Repository、UI、导航、执行状态机或文件清理；
- 计划范围可由一个独立 Draft PR 完成。

## 14. CI 与验证

当前远端可实际读取：

- Secret scan；
- Android CI；
- 应用身份静态门禁；
- Kotlin 编译；
- Debug 单元测试；
- Lint；
- Debug / Release APK；
- R8；
- Room Schema 漂移；
- CI 上传的 Room Schema Artifact。

本任务将先提交测试取得真实 RED，再提交生产实现。KSP 生成的真实 `7.json` 必须从当前 Head 的 CI Artifact 读取并提交，不能手写 identityHash。

远端无法实际执行本地 Gradle、模拟器或设备测试；Instrumentation 与进程恢复等待本地 AI 严格只读验收，或在用户明确触发的 GitHub `workflow_dispatch` 中执行。

## 15. 兼容层与删除阶段

| 兼容项 | 当前唯一事实源 | 最早删除阶段 |
|---|---|---|
| Message 可空领域关联 | 旧 ChatRepository + PR09-02A Message | PR09-03 建立唯一写事务后另行收紧 |
| Message 音频三个字段 | 当前音频生成、播放、转码与删除调用链 | PR09-10B 完成资产导入、双写切换、回归和稳定验收后 |
| CharacterGroup | 当前兼容组合 | PR09-05 正式 Skill 目录与组合迁移完成后另行评估 |
| 新 ResourceLifecycleDao 直接调用 | 仅 Schema/测试接口 | PR09-03 封装为唯一 Repository 入口后禁止其他业务层直接写 |

## 16. 后续可消费接口

### PR09-03

可消费全部 Entity、`ResourceLifecycleDao`、v7 Schema、生命周期和来源约束，建立唯一业务事务、合法状态转换、彻底清除顺序与进程恢复。

### PR09-09

可消费当前资料、当前背景及实际使用快照；负责显式选择、上下文最小化、匿名占位和敏感内容清除，不重新设计主键。

### PR09-10A

可消费当前草稿、草稿修订、正式成果和四类来源连接；负责草稿保存/放弃、用户确认成果和成果修订业务，不合并草稿与成果对象。

### PR09-10B

可消费 `audio_assets`、来源复合外键和文件状态；负责旧 Message 音频确定性导入、真实文件状态机、受控清理与单一写入入口，不重新设计资产 ID。

### PR09-12

可消费 `issue_lifecycle`、各对象 `deletedAt / purgeRequestedAt` 和 RESTRICT 关系；负责运行中归档选择、回收站、恢复、二次确认及永久清理事务，不新增自动过期或后台静默清空。

## 17. 风险与回滚

主要风险：

- Entity 与手写 Migration 不一致：通过当前 Head 的 KSP `7.json` Artifact、MigrationTestHelper 和 Schema 漂移门禁核对；
- 表数量较多：按职责分表换取真实外键和历史可靠性，不引入通用多态弱约束；
- 复合外键索引缺失：在 Message/Run 增加最小唯一索引并由 Room/KSP 编译校验；
- 旧音频形成双事实源：本 PR 不回填、不双写，明确 PR09-10B 接管点；
- 敏感快照与彻底清除冲突：正文可空并带 `contentState=purged`，普通删除不改写历史，永久清除由后续显式事务执行；
- 生命周期非法跳转：Schema 只表达状态，PR09-03/12 建立单一合法转换入口。

回滚方式：在 PR09-03 尚未消费 v7 前普通 revert 本 PR Commit；不得删除数据库、不得 destructive migration。v7 已被后续长期数据消费后，任何调整必须继续增加 Room 版本，不能回退版本号或删除已发布 Schema。

## 18. 未验证项

- 本地 Gradle、Lint、APK、R8 尚未在远端对话执行；
- Instrumentation、MigrationTestHelper、真实 SQLite 外键和进程重开尚未在设备执行；
- KSP 真实 v7 identityHash 尚未生成；
- 旧音频真实文件不存在、重复路径和转码任务竞争留给 PR09-10B；
- 官方 Skill ID 的目录合法性留给 PR09-05；
- 永久清除真实文件和敏感快照清除事务留给 PR09-03/09/12。
