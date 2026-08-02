# PR09-02A：见域核心领域 Schema 实施计划

> **执行方式**：Superpowers 插件接口未调用；本计划读取仓库内固定保存的 Superpowers 6.2.0 `brainstorming`、`writing-plans`、`test-driven-development`、`verification-before-completion`、`requesting-code-review` 与 `finishing-a-development-branch` Skill 文件，并按项目适配规则执行等价人工流程。

**目标**：在不破坏现有 `ChatSession` / `Message` 调用链的前提下，将 Room v5 连续升级为 v6，建立可持久化、可恢复、可约束的 Issue、Stage、ExecutionRun、参与者快照与 Message 领域关联。

**架构选择**：采用“方案 A：增量新增核心表”。保留现有 `chat_sessions`、`messages`、`characters` 与现有 Repository 作为当前 UI 的唯一写入事实源；新增独立核心领域表，并为 `messages` 增加可空领域关联字段。v5→v6 对已有合法 ChatSession 确定性回填一个 Issue 和一个初始 Stage，对已有 Message 回填对应领域关联；不把 ChatSession、用户消息 ID 或 `roundIndex` 解释为新领域对象。

**技术栈**：Kotlin 2.0.21、Room、KSP、SQLite、JUnit 4、AndroidX Room Testing、Android Instrumentation。

## 1. 基线与范围

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base 分支：main
实际 Base SHA：ae4cad13cc5cfd840143eeab56e9d72255665833
开发分支：feat/pr-09-02a-core-domain-schema
Room：v5 → v6
```

当前没有开放 PR。PR #31 已合并并冻结 `com.elio.jianyu` 应用身份；本任务不修改应用身份、导航、首页、Skill 目录、正式视觉、备份导入或发布配置。

## 2. 方案比较

### 方案 A：增量新增核心表（采用）

- 新增 `issues`、`stages`、`execution_runs`、`execution_participant_snapshots`；
- 为现有 `messages` 增加 `issueId`、`stageId`、`executionRunId`、`participantSnapshotId`；
- 保留现有 `chat_sessions` 与 `ChatRepository`，不建立第二套 Message 表；
- 迁移时为已有 ChatSession 创建确定性领域对象；
- PR09-03 接管业务事务后，再逐步停止旧 UI 直接创建无领域关联消息。

优点：语义清晰、迁移可回滚、旧调用方继续编译运行、后续 PR09-02B/03/07 有稳定接口。缺点：PR09-03 完成前，新旧模型会短期并存；旧 `ChatRepository` 新写入的消息领域字段仍可为空。

### 方案 B：直接演进或重命名旧表（不采用）

- 将 `chat_sessions` 直接改为 Issue，或把 `roundIndex` / 用户消息 ID 复用为 Stage / Run；
- 改动少，但会继承旧聊天会话的一次性语义，并使当前删除、流式占位、失败重试和消息排序调用链产生歧义；
- 需要一次性重写 Repository、ViewModel 与执行状态机，越过 PR09-02A 边界；
- 回滚风险高，容易形成数据语义不可逆混淆。

因此不采用。

## 3. 实体关系

```text
ChatSession（旧兼容容器）
    1 ── 0..1 Issue（通过 legacyChatSessionId 确定性映射）

Issue
    1 ── N Stage（RESTRICT，禁止孤儿与意外级联删除）

Stage
    1 ── N ExecutionRun（复合外键保证 Run 的 issueId/stageId 一致）

ExecutionRun
    1 ── N ExecutionParticipantSnapshot

Message（现有单一消息表）
    ├── 0..1 Issue
    ├── 0..1 Stage（与 issueId 复合校验）
    ├── 0..1 ExecutionRun（与 issueId/stageId 复合校验）
    └── 0..1 ParticipantSnapshot（与 executionRunId 复合校验）
```

删除策略统一采用 `RESTRICT`，避免删除父对象时静默丢失历史。`messages.chatId` 不新增限制型外键，因为现有 `ChatRepository.deleteSession()` 按“先删会话、后删消息”的顺序执行；本 PR 不改写该旧业务事务。

## 4. 数据模型

### 4.1 IssueEntity / `issues`

字段：

- `id: String`：稳定领域 ID；
- `title: String`；
- `createdAt: Long`；
- `updatedAt: Long`；
- `legacyChatSessionId: Long?`：旧会话映射，唯一且可空。

### 4.2 StageEntity / `stages`

字段：

- `id: String`；
- `issueId: String`；
- `sequenceIndex: Int`；
- `title: String`；
- `objective: String`；
- `createdAt: Long`；
- `updatedAt: Long`。

约束：

- 外键 `issueId → issues.id`，`ON DELETE RESTRICT`；
- 唯一 `(issueId, sequenceIndex)`；
- 唯一 `(id, issueId)`，供下游复合外键校验。

### 4.3 ExecutionRunEntity / `execution_runs`

字段：

- `id: String`；
- `issueId: String`；
- `stageId: String`；
- `triggerMessageId: Long?`；
- `idempotencyKey: String`；
- `status: ExecutionRunStatus`；
- `retryOfRunId: String?`；
- `createdAt / updatedAt / startedAt / finishedAt / stoppedAt`；
- `failureCode / failureMessage`。

状态：

```text
not_started
running
partial_success
succeeded
stopped
failed
retryable
completed
```

约束：

- `(stageId, issueId) → stages(id, issueId)`；
- `retryOfRunId → execution_runs.id`；
- `idempotencyKey` 全局唯一；
- 唯一 `(id, issueId, stageId)`，供 Message 复合校验。

### 4.4 ExecutionParticipantSnapshotEntity / `execution_participant_snapshots`

字段：

- `id: String`；
- `runId: String`；
- `sourceType / sourceId`；
- `displayName / avatar`；
- `skillAssetPath / systemPrompt`；
- `configurationJson`；
- `defaultResponsibility`；
- `position: Int`；
- `createdAt: Long`。

约束：

- `runId → execution_runs.id`，`ON DELETE RESTRICT`；
- 唯一 `(runId, position)`；
- 唯一 `(runId, sourceType, sourceId)`；
- 唯一 `(id, runId)`，供 Message 复合校验。

快照保存当次显示名、顺序、Prompt、资源路径和配置文本；后续实时 Character / Skill 修改不会改写历史。

### 4.5 Message 扩展

在现有字段末尾增加：

```text
issueId: String?
stageId: String?
executionRunId: String?
participantSnapshotId: String?
```

`roundIndex` 保持原字段、默认值与排序语义，只表示响应批次；迁移不改变其值。

## 5. DAO 契约

新增 `CoreDomainDao`：

- `insertIssue`、`insertStage`、`insertExecutionRun`、`insertParticipantSnapshots`；
- `createIssueWithInitialStage`：同一事务写入 Issue 与初始 Stage；
- `createRunWithParticipants`：同一事务写入 Run 与参与者快照；
- `bindMessageToDomain`：只绑定现有 Message，不建立第二个 Message 插入入口；
- `getIssue`、`getStagesForIssue`、`getExecutionRun`、`getParticipantSnapshots`、`getMessagesForStage`；
- `getActiveRunsForStage`，供 PR09-07 恢复运行；
- `deleteIssueForTest`，用于验证父对象删除被 RESTRICT 阻止。

PR09-02A 不新增完整 Repository，不改变现有 ViewModel 和执行器调用方式。

## 6. v5→v6 Migration

执行顺序：

1. 创建 `issues`、索引；
2. 创建 `stages`、外键与索引；
3. 创建 `execution_runs`、外键与索引；
4. 创建 `execution_participant_snapshots`、外键与索引；
5. 从 `chat_sessions` 回填 Issue：`legacy-chat-<chatId>`；
6. 为每个旧会话回填初始 Stage：`legacy-chat-<chatId>-stage-0`；
7. 重建 `messages`，保持全部旧字段和值不变，并为存在 ChatSession 的消息回填 Issue / Stage；
8. 对历史孤儿 Message 保留原数据，但领域关联保持 `NULL`，不制造伪造父对象；
9. 创建 Message 新索引；
10. Room 验证 v6 Schema。

迁移不删除旧表、不清空数据、不使用 destructive migration、不触碰旧包 `com.elio.skillroundtable` 的 Schema。

## 7. 文件级实施清单

### 新增生产文件

- `app/src/main/java/com/elio/jianyu/data/CoreDomain.kt`
  - Entity、状态枚举、TypeConverter、DAO 与事务入口。

### 修改生产文件

- `app/src/main/java/com/elio/jianyu/data/ChatSession.kt`
  - 为 Message 增加领域关联、外键和索引；现有构造调用通过默认空值兼容。
- `app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt`
  - 注册新 Entity / DAO / TypeConverter；版本 5→6；新增 `MIGRATION_5_6`。

### 新增/修改测试

- `app/src/test/java/com/elio/jianyu/data/CoreDomainModelTest.kt`
  - `roundIndex != Stage`、状态集合、快照值语义。
- `app/src/androidTest/java/com/elio/jianyu/data/CoreDomainDatabaseTest.kt`
  - 多 Stage、孤儿约束、幂等冲突、快照稳定、事务回滚、进程重开、删除限制、Message 绑定。
- `app/src/androidTest/java/com/elio/jianyu/data/RoundtableDatabaseMigrationTest.kt`
  - 现有 1/2/3/4→6；新增真实 v5 Schema→6；校验旧数据、回填、索引、外键和 `roundIndex`。

### Schema 与 CI

- 保留 `app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json`；
- 新增真实生成的 `app/schemas/com.elio.jianyu.data.RoundtableDatabase/6.json`；
- 修改 `.github/workflows/android-ci.yml`，冻结旧 v5，并校验当前 v6 的版本、核心表、Message 字段、索引与外键；
- 不修改旧包 Schema `app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json`。

## 8. TDD 顺序

1. 先提交模型与数据库失败场景测试；
2. 验证预期 RED 原因应为新 Entity / DAO / v6 Migration 尚不存在；远端无可执行 Gradle 工作区时只记录“测试已编写、尚未实际执行”；
3. 实现最小 Entity、DAO、Migration；
4. 通过 GitHub CI 编译、单测、Lint 与构建；
5. 从 CI `room-schema` Artifact 读取 KSP 实际生成的 v6 Schema，提交精确文件；
6. 完善 Migration 与约束测试；
7. 读取精确 Head CI，不复用旧 Head 结果。

## 9. Commit 边界

```text
docs: 制定PR09-02A核心领域Schema实施计划
test: 增加核心领域Schema失败场景
feat: 建立见域核心领域Schema
test: 完善Room迁移与约束验证
```

如果 Schema 必须先由 CI 生成，可追加一个仅包含真实 `6.json` 与 CI 校验修正的原子 Commit，并在 PR 描述解释原因。

## 10. 验证命令

远端 CI / 本地只读验收使用：

```powershell
git diff --check origin/main...HEAD
pwsh.exe -NoProfile -File .\tools\check-secrets.ps1 -IncludeHistory
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoundtableDatabaseMigrationTest
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.CoreDomainDatabaseTest
```

## 11. 风险与回滚

主要风险：

- 手写 Migration 与 KSP Schema 不一致；通过 CI 生成 Artifact 和 `MigrationTestHelper` 验证；
- Message 复合外键顺序错误；通过 DAO 约束测试和 Room Schema Validation 验证；
- 旧删除调用链回归；不对 `messages.chatId` 添加外键，不修改 `ChatRepository`；
- 旧会话迁移出现孤儿消息；孤儿保留但领域字段为空，不制造错误父关系；
- PR09-03 前旧 UI 新消息仍可能没有领域绑定；在 PR09-03 由单一业务事务接管，并删除该兼容窗口。

回滚方式：普通 revert 本 PR Commit；不得删除用户数据库或改用 destructive migration。v6 已被真实用户数据消费后，后续修复必须创建 v7 Migration，不回写 v5/v6 Schema 历史。

## 12. 后续可消费接口

### PR09-02B

可复用 `IssueEntity.id`、`StageEntity.id`、`ExecutionRunEntity.id` 和参与者快照稳定 ID，为资料、成果、音频与生命周期资源建立外键；不得修改本 PR 的运行身份语义。

### PR09-03

可在 `CoreDomainDao` 之上建立唯一 Repository 事务入口，接管 Issue/Stage/Run 创建、Message 绑定与进程恢复；完成后禁止 UI / ViewModel 直接组合 DAO 写入，并结束旧消息领域字段可空的兼容窗口。

### PR09-07

可使用 `ExecutionRunStatus`、`idempotencyKey`、时间戳、`retryOfRunId`、参与者快照顺序和 Message 的 Run 关联实现执行状态机与原位重试；不得重新使用用户消息 ID或 `roundIndex` 充当 Run ID。

## 13. 禁止触碰

- UI、导航、首页、Skill 目录、正式视觉与 App Icon；
- PR09-02B 资源表；
- PR09-03 完整 Repository；
- Gemini 执行状态机；
- 备份、导入、快照、归档、回收站；
- 应用身份、仓库名、官网、DNS、签名和发布；
- 依赖版本升级和无关格式化。

## 14. 未验证项

计划提交时尚未在远端环境实际执行 Gradle、模拟器或真机测试；生产实现完成后以当前精确 Head 的 GitHub CI 与本地 AI 严格只读验收结果为准。
