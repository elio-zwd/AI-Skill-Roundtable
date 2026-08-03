# 前置修复 PR：见域 Repository 基线设备测试与错误语义计划

> 本计划只处理 `RoomJianyuRepositoryDatabaseTest` 的两项基线失败，恢复 PR09-07 前置设备测试基线。
>
> 本 PR 不实现 PR09-07 执行状态机，不修改导航、Skill Catalog、Room Schema、资料、成果、音频、最终视觉或发布能力。

## 一、工作流与实际基线

正在按照仓库内 `systematic-debugging`、`test-driven-development` 和 `verification-before-completion` 工作流，定位并修复 Repository 基线设备测试失败。

Superpowers 插件接口未调用；本任务读取仓库内保存的 Superpowers 6.2.0 Skill 文件，并按照项目规则执行等价人工流程。

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base 分支：main
实际 Base SHA：5ab0ac47e5e4de368a5771c6a5206f979dd8f1c9
开发分支：fix/pr-09-repository-baseline-instrumentation
Draft PR：#37
Room：v7
```

开始前和本轮续修前均已核验：

1. PR #34、#35、#36 已真实合并；
2. `main` 当前精确 SHA 仍为 `5ab0ac47e5e4de368a5771c6a5206f979dd8f1c9`；
3. PR #37 保持 Draft；
4. 当前没有其他开放 PR 修改 Repository、DAO 或目标测试文件；
5. 分支相对 `main` 无落后提交。

## 二、RED 证据

### 2.1 初始基线 RED

PR #35、PR #36 的本地严格只读验收均只失败以下两项：

```text
RoomJianyuRepositoryDatabaseTest
#runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict

RoomJianyuRepositoryDatabaseTest
#closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue
```

已记录：

```text
PR #35：84 项，82 通过，2 失败，退出码 1
PR #36：95 项，93 通过，2 失败，退出码 1
```

### 2.2 PR #37 第一轮本地验收 RED

精确验收 Head：

```text
6de37901b53ba2158c4f868c72c60e62c48d4b83
```

真实设备：

```text
emulator-5554
Android 9
API 28
```

结果：

```text
runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict：1/1 PASS
closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue：FAIL
closedFileDatabaseReturnsStorageFailureInsteadOfEmptyIssue：FAIL
RoomJianyuRepositoryDatabaseTest：15/17 通过，2/17 失败
全量 connectedDebugAndroidTest：99/101 通过，2/101 失败，退出码 1
```

失败断言：

```text
RoomJianyuRepositoryDatabaseTest.kt:344
RoomJianyuRepositoryDatabaseTest.kt:373
```

两个关闭测试的实际结果均为：

```text
RepositoryError.NotFound
```

而不是：

```text
RepositoryError.StorageFailure
```

该设备证据证明第一轮方案 B 不成立，必须继续修复，不能标记 Ready 或启动 PR09-07。

## 三、问题一：Run 幂等冲突

### 3.1 根因

原冲突请求只替换 `ExecutionRunEntity.id`，参与者仍指向原 Run：

```text
ExecutionRun(id = run-other, idempotencyKey = run-key-1)
├── Participant(runId = run-1) ×
└── Participant(runId = run-1) ×
```

生产实现正确保留：

```kotlin
require(command.participants.all { it.runId == command.run.id })
```

请求因此在幂等比较前被映射为 `ConstraintViolation`，原测试却断言 `IdempotencyConflict`。

### 3.2 修复

测试夹具现在让 Run 和参与者使用同一目标 `runId`，并分别覆盖：

1. 完全相同请求：幂等成功；
2. 同一幂等键、合法不同 payload：`IdempotencyConflict`；
3. Run/参与者关系错配：`ConstraintViolation`；
4. 错配请求不写入新 Run 或孤儿参与者；
5. 原 Run、参与者和 Stage 保持不变。

### 3.3 验证状态

第一轮本地验收已真实执行并通过：

```text
runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict：1/1 PASS
runParticipantRelationMismatchReturnsConstraintViolationWithoutWrites：PASS
```

本轮续修不得改写或削弱这部分断言。

## 四、问题二：关闭数据库后的恢复错误

### 4.1 第一轮判断

第一轮认为测试只需先真实打开数据库再关闭，由现有 `withTransaction` 抛出异常并映射为 `StorageFailure`。

该判断已被真实设备证伪。

### 4.2 真实根因

内存数据库与文件数据库均表现一致：

1. 数据库被打开；
2. `database.close()` 后 `database.isOpen == false`；
3. Repository 再次执行 `database.withTransaction`；
4. Room 的 OpenHelper 按需重新建立连接；
5. 内存数据库成为新的空库，文件数据库重新打开原文件；
6. `getIssue(issueId)` 返回 `null`；
7. Repository 将其解释为 `NotFound`。

因此仅依赖 Room 抛异常无法冻结“显式关闭后不可继续使用”的 Repository 契约。

### 4.3 冻结语义

```text
Repository 首次访问尚未打开的数据库
→ 允许 Room 惰性打开

打开且为空的可用数据库
→ RepositoryError.NotFound

同一 Repository 已成功进入过数据库事务，随后数据库关闭
→ RepositoryError.StorageFailure

关闭后不得由 Repository 自动重开并伪装为空议题
```

## 五、方案比较

### 方案 A：每次事务前直接检查 `database.isOpen`

不采用。

原因：

- 新建 Room 实例在首次访问前通常 `isOpen == false`；
- 直接检查会把正常首次惰性打开错误映射为 `StorageFailure`；
- 无法区分“从未打开”和“使用后关闭”。

### 方案 B：只修正测试，先打开后关闭

已在第一轮采用，现被设备证据否决。

真实结果：

```text
内存库关闭后自动重新打开 → NotFound
文件库关闭后自动重新打开 → NotFound
```

### 方案 C：事务协调器记录已进入事务状态

采用。

`JianyuRepositoryTransactions` 使用进程内原子状态记录该 Repository 是否已经真实进入过 Room 事务：

```text
初始 database.isOpen == false 且未进入事务
→ 允许首次访问

进入 withTransaction 后
→ databaseWasOpened = true

后续 databaseWasOpened == true 且 database.isOpen == false
→ 在进入 Room 前返回 StorageFailure
```

选用理由：

1. 保留 Room 首次惰性打开；
2. 不依赖反射或 Room 私有字段；
3. 不修改 `RoundtableDatabase`、Entity、Migration 或 Schema；
4. 不创建测试专用生产接口；
5. 同时适用于当前内存库与文件库测试；
6. 当前项目没有配置 Room auto-close；若未来引入 auto-close，必须重新评估该门禁。

### 方案 D：在 `RoundtableDatabase.close()` 中维护显式关闭标记

不采用。

原因：

- 会把 Repository 专属错误语义扩散到通用数据库基类；
- 修改范围大于当前根因所需；
- 当前测试和生产调用均使用同一 `RoomJianyuRepository` 实例，事务协调器可以建立足够明确的生命周期边界。

## 六、竞态与取消语义

### 6.1 关闭竞态

`databaseWasOpened` 在 `withTransaction` 已进入后、执行领域 block 前设置。

线性化规则：

- 已经进入事务的操作视为在关闭之前开始，可由 Room 自身锁完成或失败；
- `close()` 返回后发起的新 Repository 操作看到 `databaseWasOpened == true` 且 `isOpen == false`，直接返回 `StorageFailure`；
- 门禁不会主动重新打开数据库。

### 6.2 协程取消

关闭状态门禁返回普通 `RepositoryResult.Failure`，不捕获或转换协程取消。

进入事务后的 `CancellationException` 仍由 `execute()` 原样抛出。

### 6.3 其他异常映射

保持：

```text
SQLiteConstraintException → ConstraintViolation
SQLiteException → StorageFailure
IllegalArgumentException → ConstraintViolation
IllegalStateException → StorageFailure
CancellationException → 原样抛出
```

## 七、测试设计

目标类继续保持 17 项 `@Test`，不通过增加重复测试制造数量。

关键覆盖：

1. 首次 Repository 访问前数据库未打开；
2. 首次 `recoverIssue()` 允许惰性打开并返回 `NotFound`；
3. 该访问真实进入事务并建立已打开状态；
4. 关闭内存数据库后恢复返回 `StorageFailure`；
5. 关闭文件数据库后恢复返回 `StorageFailure`；
6. 两项关闭测试断言 Repository 调用后数据库仍未重新打开；
7. 正常恢复、异常映射、取消传播和外键检查保持不变；
8. Run 幂等修复继续通过。

## 八、修改文件

本轮允许且实际修改：

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
docs/planning/pr-09-repository-baseline-fix-plan.md
docs/testing/pr-09-repository-baseline-local-readonly-acceptance-prompt.md
```

继续禁止修改：

```text
RoundtableDatabase.kt 的版本、Entity、Migration 或构建配置
app/schemas/
IssueExecutionRepositoryComponent.kt
LifecycleRecoveryRepositoryComponent.kt
RoomJianyuRepository.kt
JianyuRepositoryDao.kt
App.kt
导航
Skill Catalog
ViewModel
Gemini
执行调度
资料、成果、音频、视觉或发布能力
```

## 九、TDD 执行

### RED

真实 RED 为本地验收 Head `6de37901...`：

```text
内存关闭测试失败
文件关闭测试失败
完整类 15/17
全量 99/101
```

### GREEN

最小生产修复：

```text
JianyuRepositoryTransactions 增加 databaseWasOpened 原子状态
首次事务内设置状态
使用后关闭时在 withTransaction 前返回 StorageFailure
```

测试只调整数据库打开方式，使状态由 Repository 自身建立，而不是绕过协调器直接调用 OpenHelper。

### REFACTOR

本轮不进行额外 Repository 重构。只有本地设备重新全绿后才评估是否需要整理注释或测试辅助函数。

## 十、Commit 边界

已完成：

```text
ed1e8a043883931d7f67e9ea18fcb65af486dc96
fix: 阻止Repository在数据库关闭后自动重开

4a823e4e3a8f03a317a10d1d6c5aed3dc83521c1
test: 验证Repository关闭状态门禁
```

文档更新单独提交，不与生产修复混合。

## 十一、远端验证

最新 Head 必须重新通过：

- Secret scan；
- `compileDebugKotlin`；
- `testDebugUnitTest`；
- `lintDebug`；
- `assembleDebug`；
- Release / R8；
- Room v7 与 Schema 当前性；
- 当前 Head 状态。

普通 GitHub CI 不执行目标 Instrumentation 时必须明确：

```text
目标设备测试尚未由远端 CI 执行；等待本地 AI 严格只读复验。
```

## 十二、本地复验

本地 AI 必须在最新精确 Head 上重新执行：

1. `compileDebugAndroidTestKotlin`；
2. Run 幂等冲突目标测试；
3. 内存数据库关闭目标测试；
4. 文件数据库关闭边界测试；
5. 其余新增错误映射边界测试；
6. 完整 `RoomJianyuRepositoryDatabaseTest`；
7. 全量 `connectedDebugAndroidTest`；
8. Room v7、Schema、外键、导航、Skill Catalog、Secret scan、工作区与 Head 复核。

上一轮结果不得复用为本轮 GREEN。

## 十三、完成条件

只有全部满足才算完成：

1. Run 合法不同 payload 返回 `IdempotencyConflict`；
2. Run/参与者关系错配返回 `ConstraintViolation` 且无写入；
3. 首次空库访问返回 `NotFound`；
4. 内存数据库关闭后返回 `StorageFailure`；
5. 文件数据库关闭后返回 `StorageFailure`；
6. 关闭后的 Repository 调用不重新打开数据库；
7. `CancellationException` 原样传播；
8. 其他异常映射不变；
9. Room 保持 v7；
10. `app/schemas/` 无变化且不存在 `8.json`；
11. 两项目标测试通过；
12. 完整测试类 17/17 通过；
13. 全量 Instrumentation 零失败；
14. GitHub CI 通过；
15. 工作区干净、Head 精确一致；
16. Draft PR 保持 Draft；
17. PR09-07 未启动。

## 十四、风险与回滚

风险：

- 当前门禁把“同一 Repository 已使用后数据库变为关闭”视为终止存储状态；未来若项目引入 Room auto-close，必须调整该契约；
- 新建另一个 Room 数据库实例属于新的存储生命周期，不受旧实例状态影响；
- 方法过滤格式继续以本地 Android Test Runner 实际支持为准。

回滚：

- 回滚本轮生产与测试两个 Commit 即可恢复第一轮状态；
- 本轮不修改 Entity、Migration、Schema 或用户数据格式；
- 不需要数据迁移或用户数据回滚。

## 十五、PR09-07 启动门禁

PR09-07 只能在以下条件全部满足后启动：

1. 最新 Head 的本地严格只读验收 PASS；
2. 用户明确授权标记 Ready；
3. Ready 状态触发的最新 CI 通过；
4. 用户明确授权并实际合并 PR #37；
5. 后续任务从合并后的最新 `main` 创建独立分支。
