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
Room：v7
```

开始前已核验：

1. PR #34、#35、#36 已真实合并；
2. `main` 当前精确 SHA 为 `5ab0ac47e5e4de368a5771c6a5206f979dd8f1c9`；
3. 当前没有开放 PR；
4. 没有其他开放 PR 修改 Repository、DAO 或目标测试文件；
5. 目标测试文件在 PR #35 合并点与当前 `main` 的 Blob SHA 均为 `9c089bd0e3907846f027b5798021e20c10f13ace`，导航壳合并未改写该测试。

## 二、RED 证据与远端环境限制

当前 GitHub 插件环境没有 Android SDK、Gradle Android 构建工作区或 Emulator，且当前连接器没有工作流手动派发能力，因此本对话不能重新执行 Instrumentation RED。不得把静态阅读写成设备执行结果。

可核验的实际设备 RED 来自已合并 PR #35、#36 的严格只读验收：

```text
PR #35 全量 Instrumentation：84 项，82 通过，2 失败，退出码 1
PR #36 全量 Instrumentation：95 项，93 通过，2 失败，退出码 1
```

两次均只失败：

```text
RoomJianyuRepositoryDatabaseTest
#runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict

RoomJianyuRepositoryDatabaseTest
#closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue
```

已记录的失败位置：

```text
第一项：RoomJianyuRepositoryDatabaseTest.kt:111，AssertionError
第二项：RoomJianyuRepositoryDatabaseTest.kt:291，AssertionError
```

本分支完成后，本地 AI 必须在精确 Head 上重新执行两个目标方法、完整测试类和全量 `connectedDebugAndroidTest`，以新的设备输出完成 RED/GREEN 闭环。未获得该证据前，Draft PR 保持 Draft，PR09-07 不得启动。

## 三、问题一：Run 幂等冲突测试根因

### 3.1 当前输入关系图

当前测试先创建：

```text
ExecutionRun(id = run-1, idempotencyKey = run-key-1)
├── Participant(id = participant-1, runId = run-1)
└── Participant(id = participant-2, runId = run-1)
```

冲突请求只执行：

```kotlin
command.copy(run = command.run.copy(id = "run-other"))
```

因此实际冲突请求为：

```text
ExecutionRun(id = run-other, idempotencyKey = run-key-1)
├── Participant(id = participant-1, runId = run-1)  ×
└── Participant(id = participant-2, runId = run-1)  ×
```

### 3.2 生产约束与失败链

`IssueExecutionRepositoryComponent.createExecutionRun()` 在读取幂等键前保留以下合法前置约束：

```kotlin
require(command.participants.all { it.runId == command.run.id })
```

`JianyuRepositoryTransactions.execute()` 将该 `IllegalArgumentException` 映射为：

```text
RepositoryError.ConstraintViolation
```

因此原测试构造的是内部关系非法命令，结果在幂等比较前被拒绝；测试随后强制转换并断言 `IdempotencyConflict`，在断言处失败。生产关系约束正确，不删除、不绕过，也不把错配参与者写入数据库。

### 3.3 选定修复

测试夹具改为显式接收 `runId`，并让所有参与者使用同一个目标 `runId`。三种语义分开验证：

1. 完全相同命令重试：返回幂等成功；
2. 同一 `idempotencyKey`、不同 Run ID、参与者关系合法：返回 `RepositoryError.IdempotencyConflict`；
3. Run ID 与参与者 `runId` 不一致：返回 `RepositoryError.ConstraintViolation`，且没有新 Run、孤儿参与者或原记录变化。

合法冲突请求关系：

```text
ExecutionRun(id = run-other, idempotencyKey = run-key-1)
├── Participant(id = run-other-participant-1, runId = run-other)
└── Participant(id = run-other-participant-2, runId = run-other)
```

## 四、问题二：关闭数据库后的恢复错误根因

### 4.1 当前测试执行顺序

`setUp()` 只构建 Room 实例，没有访问 DAO、事务或 `openHelper.writableDatabase`。目标测试随即调用：

```kotlin
database.close()
repository.recoverIssue(ISSUE_ID)
```

Room 2.6.1 采用惰性打开；数据库尚未实际打开时，测试没有建立“已打开后关闭”的存储状态。后续 `recoverIssue()` 成为首次数据库访问，空内存数据库被打开，`getIssue()` 返回 `null`，最终得到 `RepositoryError.NotFound`。该结果符合“打开且为空”的语义，但不符合测试名称要求的“已关闭且不可操作”。

### 4.2 冻结语义

```text
打开且为空的数据库
→ RepositoryError.NotFound

已真实打开、随后关闭且不可继续操作的数据库
→ RepositoryError.StorageFailure
```

`recoverIssue()` 不得把关闭存储伪装为空议题，也不得创建空 Issue、Stage 或 Lifecycle。

### 4.3 方案比较

#### 方案 A：生产事务协调器预检查 `database.isOpen`

不采用。`isOpen == false` 同时覆盖“尚未首次打开”和“已关闭”，直接预检查会把正常首次访问错误映射为 `StorageFailure`；检查与事务执行之间仍存在竞态，不能替代 `withTransaction` 的真实异常映射。

#### 方案 B：修正测试环境，先真实打开再关闭

采用。测试在关闭前访问 `database.openHelper.writableDatabase`，断言 `database.isOpen == true`；关闭后断言 `database.isOpen == false`；再调用 `recoverIssue()`，由现有事务协调器把 Room 抛出的不可用状态异常映射为 `StorageFailure`。

同时增加打开空数据库测试，证明 `NotFound` 语义未被误伤；增加文件型数据库关闭测试，确认内存库和文件库使用同一关闭错误语义。

#### 方案 C：生产门禁与测试环境同时修改

不采用。当前生产协调器已经：

- 原样传播 `CancellationException`；
- 将 `SQLiteConstraintException` 映射为 `ConstraintViolation`；
- 将 `SQLiteException`、`IllegalStateException` 和其他存储异常映射为 `StorageFailure`。

当前缺口是测试没有真实进入关闭状态。没有证据支持增加生产分支或扩大修改范围。

## 五、测试增量

目标测试文件新增或拆分覆盖：

1. 相同 Run 创建命令幂等成功；
2. 同一幂等键、合法不同 payload 返回 `IdempotencyConflict`；
3. Run/参与者关系错配返回 `ConstraintViolation`；
4. 关系错配不写入新 Run 或孤儿参与者；
5. 原 Run 与参与者保持不变；
6. 打开空数据库返回 `NotFound`；
7. 已真实打开后关闭的内存数据库返回 `StorageFailure`；
8. 已真实打开后关闭的文件数据库返回 `StorageFailure`；
9. 关闭数据库后不返回成功空快照；
10. 常规 `SQLiteException` 映射为 `StorageFailure`；
11. `SQLiteConstraintException` 映射为 `ConstraintViolation`；
12. `CancellationException` 原样抛出；
13. 正常 `recoverIssue()` 的既有测试继续覆盖完整恢复；
14. 空库恢复不创建 Issue、Stage、Lifecycle；
15. `PRAGMA foreign_key_check` 继续为 0。

## 六、修改文件

允许并计划修改：

```text
docs/planning/pr-09-repository-baseline-fix-plan.md
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
docs/testing/pr-09-repository-baseline-local-readonly-acceptance-prompt.md
```

当前不修改：

```text
JianyuRepositoryTransactions.kt
IssueExecutionRepositoryComponent.kt
LifecycleRecoveryRepositoryComponent.kt
RoomJianyuRepository.kt
JianyuRepositoryDao.kt
RoundtableDatabase.kt
任何 Entity、Migration、Schema、UI、导航或 Skill Catalog 文件
```

若后续真实设备日志与本计划根因相反，停止追加修复并基于新证据重新分析，不在同一提交中叠加猜测性生产改动。

## 七、TDD 执行边界

### RED

基线实际 RED 证据来自 PR #35/#36；本地 AI 必须在本分支精确 Head 上重新执行目标方法并保留命令、设备、退出码和完整关键堆栈。

### GREEN

最小修改仅调整测试夹具、数据库打开/关闭顺序和错误映射边界测试。远端 CI 验证编译、JVM、Lint、Debug、Release/R8 和 Schema；设备 GREEN 由本地 AI 验证。

### REFACTOR

只有目标测试和完整测试类在本地设备绿色后，才允许进一步整理测试辅助函数。本远端 PR 不进行 Repository 无关重构。

## 八、完成条件

1. 合法不同 Run payload 真实进入幂等比较并返回 `IdempotencyConflict`；
2. 参与者 Run ID 错配继续被关系约束拒绝；
3. 错配请求不写入 Run 或参与者；
4. 打开空数据库返回 `NotFound`；
5. 已真实打开后关闭的内存和文件数据库返回 `StorageFailure`；
6. 取消、约束与普通存储异常映射保持原语义；
7. 正常恢复读取不变且无副作用；
8. Room 保持 v7；
9. `app/schemas/` 无变化且不生成 `8.json`；
10. 两项目标方法、完整测试类和全量 Instrumentation 零失败；
11. GitHub CI 通过；
12. 工作区最终干净，远端与本地 Head 一致；
13. Draft PR 保持 Draft；
14. PR09-07 仅在本地验收 PASS、用户授权并合并本修复 PR 后启动。

## 九、Commit 边界

```text
docs: 制定Repository基线失败修复计划
test: 修正ExecutionRun幂等冲突测试夹具
test: 完善Repository错误语义设备验证
docs: 增加Repository基线本地验收Prompt
```

如测试修改可以在一个文件内保持清晰原子差异，仍按两次顺序提交，分别记录 Run 夹具修正与数据库关闭/错误映射覆盖。

## 十、远端验证与 CI

远端完成后读取精确 Head 的 GitHub Actions：

- Secret scan；
- Kotlin 编译；
- JVM 单元测试；
- Lint；
- Debug APK；
- Release / R8；
- Room v7 与 Schema 无漂移；
- 当前 Head 状态。

普通 PR CI 不执行本任务目标 Instrumentation 时，PR 描述必须明确：

```text
目标设备测试尚未由远端 CI 执行；等待本地 AI 严格只读验收。
```

## 十一、本地验收

本地 AI 严格只读验收文件：

```text
docs/testing/pr-09-repository-baseline-local-readonly-acceptance-prompt.md
```

验收必须先运行两个目标方法，再运行完整 `RoomJianyuRepositoryDatabaseTest`，最后运行全量 `connectedDebugAndroidTest`。发现问题只输出日志、复现步骤和根因线索，不修改文件、不提交、不推送、不合并。

## 十二、风险与回滚

风险：

- Android Test Runner 对方法过滤格式在不同 Gradle/AGP 组合下存在差异，本地 AI 应根据 Runner 实际支持格式调整命令参数，不改测试源码；
- 文件型测试必须使用唯一临时数据库名并在 `finally` 中关闭和删除，避免测试间污染；
- 错误映射测试直接调用内部事务协调器，只验证稳定边界，不引入生产测试钩子。

回滚：

- 整体回滚本 PR 即可恢复原测试；
- 本 PR 不修改生产代码、Room Entity、Migration 或持久化格式；
- 不需要数据迁移或用户数据回滚。
