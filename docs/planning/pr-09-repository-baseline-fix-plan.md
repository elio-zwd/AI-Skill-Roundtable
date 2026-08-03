# 前置修复 PR：见域 Repository 基线设备测试与错误语义计划

> 本计划只修复 Repository 基线设备测试与对应错误语义，为 PR09-07 提供稳定前置基线。
>
> 本 PR 不实现 PR09-07 执行状态机，不修改导航、Skill Catalog、Room Schema、资料、成果、音频、最终视觉或发布能力。

## 一、工作流与基线

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

已核验：

1. PR #34、#35、#36 已真实合并；
2. `main` 仍为上述 Base SHA；
3. PR #37 保持 Draft；
4. 没有其他开放 PR 修改相同 Repository、DAO 或测试文件；
5. 开发分支相对 `main` 无落后提交。

## 二、RED 证据

### 2.1 初始基线

PR #35 与 PR #36 的本地严格只读验收均只失败：

```text
RoomJianyuRepositoryDatabaseTest
#runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict

RoomJianyuRepositoryDatabaseTest
#closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue
```

实际统计：

```text
PR #35：84 项，82 通过，2 失败，退出码 1
PR #36：95 项，93 通过，2 失败，退出码 1
```

### 2.2 PR #37 第一轮本地验收

精确 Head：

```text
6de37901b53ba2158c4f868c72c60e62c48d4b83
```

设备：

```text
emulator-5554
Android 9
API 28
```

结果：

```text
Run 幂等冲突目标测试：1/1 PASS
内存数据库关闭测试：FAIL
文件数据库关闭测试：FAIL
完整 RoomJianyuRepositoryDatabaseTest：15/17 通过
全量 connectedDebugAndroidTest：99/101 通过，2 失败，退出码 1
```

失败断言：

```text
RoomJianyuRepositoryDatabaseTest.kt:344
RoomJianyuRepositoryDatabaseTest.kt:373
```

两项关闭测试实际均返回：

```text
RepositoryError.NotFound
```

而不是：

```text
RepositoryError.StorageFailure
```

该结果真实证明“只调整测试打开顺序”不足，PR 不得标记 Ready，也不得启动 PR09-07。

## 三、问题一：Run 幂等冲突

### 3.1 根因

原测试只替换 Run ID，参与者仍指向原 Run：

```text
ExecutionRun(id = run-other, idempotencyKey = run-key-1)
├── Participant(runId = run-1) ×
└── Participant(runId = run-1) ×
```

生产实现正确保留：

```kotlin
require(command.participants.all { it.runId == command.run.id })
```

因此请求在幂等比较前被映射为 `ConstraintViolation`，原测试却断言 `IdempotencyConflict`。

### 3.2 修复

测试夹具现在让 Run 与参与者使用同一目标 `runId`，并分别验证：

1. 完全相同请求：幂等成功；
2. 同一幂等键、合法不同 payload：`IdempotencyConflict`；
3. Run/参与者关系错配：`ConstraintViolation`；
4. 错配请求不写入新 Run 或孤儿参与者；
5. 原 Run、参与者和 Stage 保持不变。

### 3.3 已有验证

第一轮本地验收已真实通过：

```text
runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict：1/1 PASS
runParticipantRelationMismatchReturnsConstraintViolationWithoutWrites：PASS
```

最新 Head 仍需重新执行，不能复用旧结果。

## 四、问题二：关闭数据库后的恢复错误

### 4.1 真实根因

内存数据库与文件数据库行为一致：

1. 调用 `database.close()`；
2. `database.isOpen` 变为 `false`；
3. Repository 随后调用 `withTransaction`；
4. Room 的 OpenHelper 按需重新建立连接；
5. 内存库成为新的空库，文件库重新打开原文件；
6. `getIssue(issueId)` 返回 `null`；
7. Repository 将关闭状态误解释为 `NotFound`。

这说明 `isOpen` 只能表示当前连接是否打开，不能证明该数据库实例是否已经被调用方显式关闭。

### 4.2 冻结语义

```text
新建但尚未首次打开的数据库实例
→ 允许 Room 惰性打开

打开且为空的可用数据库
→ RepositoryError.NotFound

数据库实例已经显式调用 close()
→ RepositoryError.StorageFailure

显式关闭后 Repository 不得触发 OpenHelper 自动重开
```

显式关闭是该数据库实例的终止状态。需要继续使用存储时，应创建新的数据库实例，而不是复用已关闭实例。

## 五、方案比较

### 方案 A：事务前直接检查 `database.isOpen`

不采用。

新建 Room 实例在首次访问前通常 `isOpen == false`，直接检查会误伤正常首次惰性打开，也无法区分“尚未打开”和“已经关闭”。

### 方案 B：只修测试，先打开再关闭

已被第一轮真实设备验收否决。

```text
内存库关闭后被自动重开 → NotFound
文件库关闭后被自动重开 → NotFound
```

### 方案 C：事务协调器只记录是否曾进入事务

曾作为中间方案，但不作为最终方案。

它能覆盖“使用后关闭”，却不能严格覆盖“数据库在 Repository 首次访问前就已经显式关闭”的原始契约，也不能让多个 Repository 实例共享同一数据库关闭状态。

### 方案 D：数据库实例记录显式关闭状态，事务入口统一门禁

最终采用。

`RoundtableDatabase` 增加进程内原子关闭标记：

```text
构建完成：isExplicitlyClosed = false
调用 close()：先原子设置 true，再执行 super.close()
```

`JianyuRepositoryTransactions.transactionRaw()` 在进入 Room 前检查：

```text
isExplicitlyClosed == true
→ 抛出内部 RepositoryStorageUnavailableAbort
→ 外层 execute(operation) 映射为 StorageFailure
```

选用理由：

1. 精确区分“尚未打开”和“已经显式关闭”；
2. 覆盖关闭发生在 Repository 首次访问之前的情况；
3. 多个 Repository 共享同一数据库实例时看到一致关闭状态；
4. 门禁位于唯一原始事务入口，不存在 `transactionRaw` 旁路；
5. 保留准确的 Repository operation；
6. 不依赖反射或 Room 私有字段；
7. 不修改 Entity、Migration、数据库版本或 Schema；
8. 不创建测试专用生产接口。

## 六、生产实现边界

### 6.1 `RoundtableDatabase`

仅增加：

```text
AtomicBoolean 显式关闭标记
internal 只读 isExplicitlyClosed
final override close()
```

不得修改：

```text
@Database entities
version = 7
TypeConverters
Migration 1→7
DAO 声明
数据库名称
种子数据
Schema
```

### 6.2 `JianyuRepositoryTransactions`

`transactionRaw()` 是所有 Repository 数据库事务的最低入口。

关闭时抛出的内部异常由 `execute(operation)` 映射为：

```text
RepositoryError.StorageFailure(operation, retryable = true)
```

普通 `transaction()` 路径和 `ResourceRepositoryComponent` 中“外部校验后调用 `transactionRaw()`”的路径均受同一门禁保护。

### 6.3 异常和取消语义

保持不变：

```text
CancellationException → 原样抛出
SQLiteConstraintException → ConstraintViolation
SQLiteException → StorageFailure
IllegalArgumentException → ConstraintViolation
IllegalStateException → StorageFailure
```

关闭门禁不捕获或转换协程取消。

## 七、测试设计

目标类保持 17 项 `@Test`，不通过增加重复测试制造通过数量。

关键覆盖：

1. 新建数据库 `isOpen == false` 且 `isExplicitlyClosed == false`；
2. 首次 `recoverIssue()` 允许惰性打开并返回 `NotFound`；
3. 首次访问不会设置显式关闭状态；
4. 内存数据库在 Repository 首次访问前显式关闭；
5. 关闭后恢复返回 `StorageFailure`；
6. 关闭后的 Repository 调用不重新打开数据库；
7. 文件数据库先正常打开并返回 `NotFound`；
8. 文件数据库显式关闭后返回 `StorageFailure`；
9. 文件数据库关闭后保持未打开；
10. Run 幂等和关系约束继续通过；
11. SQLite 错误映射、取消传播和外键检查继续通过；
12. 正常 `recoverIssue()` 和其他 Repository 操作不变。

## 八、修改文件

允许且实际修改：

```text
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
docs/planning/pr-09-repository-baseline-fix-plan.md
docs/testing/pr-09-repository-baseline-local-readonly-acceptance-prompt.md
```

`RoundtableDatabase.kt` 只允许生命周期标记变化，不允许版本、Entity、Migration 或 Schema 变化。

继续禁止：

```text
IssueExecutionRepositoryComponent.kt
LifecycleRecoveryRepositoryComponent.kt
RoomJianyuRepository.kt
JianyuRepositoryDao.kt
app/schemas/
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

最新可核验设备 RED：

```text
Head 6de37901...
内存关闭测试失败
文件关闭测试失败
完整类 15/17
全量 99/101
```

### GREEN

最小生产修复：

```text
数据库实例记录显式关闭状态
唯一原始事务入口拒绝显式关闭实例
内部关闭异常映射为 StorageFailure
```

测试恢复并强化原始契约：内存库在 Repository 首次访问前关闭，文件库在正常使用后关闭。

### REFACTOR

本轮不进行其他 Repository 重构。只有最新 Head 的设备测试全部绿色后，才考虑任何非必要整理。

## 十、Commit 边界

续修主要 Commit：

```text
1709b071ea1fd0ca236c3c29f29f267c3ceaef28
fix: 记录Room数据库显式关闭状态

9c3a4b03f9a976b650580d03793473a25fe0a67e
fix: 使用数据库显式关闭状态阻止事务重开

91a8e9c5fef4ea268eb29d94facc9d42c41aca74
test: 覆盖数据库首次访问前显式关闭语义
```

此前中间方案 Commit 保留在 Draft PR 历史中，最终净差异以当前 Head 为准，不以中间提交内容作为最终设计。

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
5. 其余错误映射边界测试；
6. 完整 `RoomJianyuRepositoryDatabaseTest`；
7. 全量 `connectedDebugAndroidTest`；
8. Room v7、Schema、外键、导航、Skill Catalog、Secret scan、工作区与 Head 复核。

上一轮结果不得复用为最新 Head 的 GREEN。

## 十三、完成条件

只有全部满足才算完成：

1. 合法不同 Run payload 返回 `IdempotencyConflict`；
2. Run/参与者关系错配返回 `ConstraintViolation` 且无写入；
3. 首次空库访问返回 `NotFound`；
4. Repository 首次访问前关闭的内存数据库返回 `StorageFailure`；
5. 使用后关闭的文件数据库返回 `StorageFailure`；
6. 关闭后的 Repository 调用不重新打开数据库；
7. `CancellationException` 原样传播；
8. 其他异常映射不变；
9. Room 保持 v7；
10. Entity、Migration 和 `app/schemas/` 无变化；
11. 不存在 `8.json`；
12. 目标测试全部通过；
13. 完整测试类 17/17 通过；
14. 全量 Instrumentation 零失败；
15. GitHub CI 通过；
16. 工作区干净、Head 精确一致；
17. Draft PR 保持 Draft；
18. PR09-07 未启动。

## 十四、风险与回滚

风险：

- 显式 `close()` 被定义为数据库实例终止状态；后续若需要恢复存储，必须创建新实例；
- 当前实现没有启用 Room auto-close；未来若引入自动关闭机制，必须区分框架自动关闭与调用方显式关闭并重新评估；
- 本地方法过滤格式仍以实际 Android Test Runner 支持为准。

回滚：

- 整体回滚 PR #37 可恢复原基线；
- 仅回滚本轮三个主要代码/测试 Commit 可恢复第一轮 Draft 状态；
- 本轮没有修改 Entity、Migration、Schema 或用户数据格式；
- 不需要数据迁移或用户数据回滚。

## 十五、PR09-07 启动门禁

PR09-07 只能在以下条件全部满足后启动：

1. 最新 Head 的本地严格只读验收 PASS；
2. 用户明确授权标记 Ready；
3. Ready 状态触发的最新 CI 通过；
4. 用户明确授权并实际合并 PR #37；
5. 后续任务从合并后的最新 `main` 创建独立分支。
