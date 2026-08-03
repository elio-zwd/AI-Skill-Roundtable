# PR09-07：见域执行运行实施计划

> **执行方式：** 使用仓库内 Superpowers 6.2.0 的 `brainstorming`、`writing-plans`、`executing-plans`、`test-driven-development`、`systematic-debugging`、`verification-before-completion`、`requesting-code-review` 与 `finishing-a-development-branch` Skill 文件执行等价人工流程。Superpowers 插件接口当前不可调用；不使用 Worktree、并行代理、自动合并或自动删分支。

## 一、目标、基线与范围

**目标：** 建立以 Room 数据为唯一事实源的 `ExecutionRun` 执行引擎，使单 Skill 与多 Skill 能按冻结顺序流式执行，并支持持久化预算、停止、部分成功、失败分类、子 Run 重试和安全进程恢复。

**仓库：** `elio-zwd/AI-Skill-Roundtable`

**实际 Base：** `main@85e439508a060e7b4d4a0446ec5e5ecc0709107a`

**开发分支：** `feat/pr-09-07-execution-run`

**基线核验：** PR #37 已合并；开始开发时没有开放 PR。PR #37 的已记录绿色证据为 JVM 239/239、`RoomJianyuRepositoryDatabaseTest` 17/17、全量 Instrumentation 101/101、Lint 0 Error、Debug/Release 构建通过、Room v7 且 Schema 无变化。

**文档漂移：** 根 `AGENTS.md` 与 `README.md` 的 Room v5 表格已落后；实际代码、Schema 和 PR #33～#37 均为 Room v7。本 PR 以真实代码为准，不顺带重写无关历史文档。

本 PR 只完成执行运行状态机、预算、流式、停止、错误、部分/全部失败、原位重试、进程恢复、基础执行工作区与 PR09-09 上下文扩展接口。不实现首页推荐、资料与个人背景、点名、交叉讨论、成果、推进议题、音频迁移、归档清理、最终视觉或发布。

## 二、旧执行调用链与真实调用方

当前生产旧链：

```text
RoundtableViewModel
  ├─ activeRoundtableJob / RetryableRoundtableState
  ├─ isRoundtableRunning / typingCharacterIds
  ├─ RoundtableBudgetManager 的 ConcurrentHashMap
  └─ RoundtableOrchestrator
       ├─ RoundtableDatabaseGateway
       ├─ CharacterAnswerGateway
       ├─ ChatRepository
       └─ Gemini / Interactions / Search / ApiKeyScheduler
```

真实入口包括：

- `RoundtableViewModel.askQuestion()`；
- `triggerNextCharacterManual()`；
- `retryFailedCharacters()`；
- `cancelRoundtable()`；
- `RoundtableOrchestrator.runRoundtableSequence()`；
- 旧圆桌 Route 与 Screen。

已确认缺陷：Job、成功成员、冻结成员、预算和重试目标均为进程内状态；启动时旧 Pending 清理存在历史行为；旧 Orchestrator 删除 Pending 后再插入成功消息；仅有进程内 Mutex；恢复后无法证明是否已消费预算或是否应再次调用网络。

## 三、方案比较与最终架构

### 3.1 方案比较

**方案 A：继续扩展 `RoundtableViewModel`。** 不采用。它无法提供跨进程事实源，会继续扩大 God Object，并让 PR09-09、06、08 竞争同一 ViewModel。

**方案 B：重写旧 `RoundtableOrchestrator` 但继续使用 `ChatRepository` Gateway。** 不采用为新 Issue 的正式路径。它仍以旧 ChatSession/Message 关系为主，绕过 `JianyuRepository` 的 Run、CAS 与恢复契约。

**方案 C：Execution Coordinator + Gateway + JianyuRepository。** 采用。Coordinator 不依赖 Compose 或 DAO；网络、Clock、ID、延迟可注入；所有领域写入通过 `JianyuRepository`；数据库是最终事实源。

**方案 D：WorkManager 或后台 Service。** 本阶段不采用。实时流式、用户停止、BYOK Key 生命周期和 Android 后台限制会显著增加重复 Worker 与敏感数据风险。进程重建只恢复持久状态，不自动重新调用模型。

### 3.2 最终组件

```text
ExecutionRunCoordinator
  ├─ ExecutionStateMachine
  ├─ ExecutionBudgetPolicy
  ├─ ExecutionContextBuilder
  ├─ ExecutionNetworkGateway
  ├─ ExecutionClock / ExecutionIdGenerator
  └─ JianyuRepository
       └─ Room v8
```

UI 使用：

```text
IssueExecutionRoute
  → IssueExecutionViewModel
    → IssueExecutionScreen
      → IssueExecutionComponents
```

`IssueRecoveryRoute` 升级为工作区入口并委托执行域 Route。打开或恢复页面只读取，不创建 Run、不创建 Pending、不调用网络。

## 四、状态机冻结

### 4.1 Run 状态语义

| 状态 | 含义 | 活跃网络 | 终态 | 可停止 | 可创建子 Run 重试 |
|---|---|---:|---:|---:|---:|
| `NOT_STARTED` | Run 与成员已持久化，尚未进行网络尝试 | 否 | 否 | 是 | 否 |
| `RUNNING` | 至少一个成员 queued/running/streaming | 是 | 否 | 是 | 否 |
| `PARTIAL_SUCCESS` | 至少一位成功且仍有成员在主动执行 | 是 | 否 | 是 | 否 |
| `SUCCEEDED` | 本 Run 的全部目标成员成功 | 否 | 是 | 否 | 否 |
| `RETRYABLE` | 主动执行已停止，存在可重试成员 | 否 | 可恢复终态 | 否 | 是 |
| `FAILED` | 无成功结果且当前错误不可继续 | 否 | 是 | 否 | 否 |
| `STOPPED` | 用户明确停止 | 否 | 是 | 否 | 是，限未成功成员 |
| `COMPLETED` | 仅保留旧数据兼容；新 Coordinator 不产生 | 否 | 是 | 否 | 否 |

合法转换：

```text
NOT_STARTED -> RUNNING | STOPPED | FAILED
RUNNING -> PARTIAL_SUCCESS | SUCCEEDED | RETRYABLE | FAILED | STOPPED
PARTIAL_SUCCESS -> SUCCEEDED | RETRYABLE | STOPPED
```

相同状态与相同时间/错误字段的重复请求幂等；其他转换拒绝。所有更新使用 CAS，不允许旧回调覆盖更新状态。

### 4.2 Participant 状态

```text
QUEUED -> RUNNING -> STREAMING -> SUCCEEDED
QUEUED/RUNNING/STREAMING -> FAILED | TIMED_OUT | STOPPED | RETRYABLE
```

`RETRYABLE` 是进程恢复或预算/可恢复错误收敛后的可重试状态。成功成员不可回退。失败、超时、停止或未执行成员可被复制到子 Run；原 Run 历史不改写。

## 五、Schema 决策：最小 Room v8

### 5.1 为什么 v7 不足

v7 可保存 Run、不可变参与者快照和 Message，但无法无歧义区分参与者从未开始、正在执行、流式、超时、停止、进程中断、尝试次数、输出 Message、部分文本是否完整，也无法持久化调用额度与预留。仅从 Message 推导会把“无 Key 前未调用”“调用中进程死亡”“空响应失败”和“排队未开始”混为一类。

### 5.2 新表

新增 `execution_participant_states`：

- `participantSnapshotId` 主键；
- `runId`；
- `status`；
- `attemptCount`；
- `outputMessageId`；
- `startedAt`、`finishedAt`、`updatedAt`；
- `lastErrorCode`、`lastErrorMessage`；
- `hasIncompleteOutput`。

新增 `execution_run_budgets`：

- `rootRunId` 主键；
- `maxApiCalls`、`usedApiCalls`、`reservedRequiredCalls`；
- `maxCharacters`、`maxSearchQueriesPerCharacter`、`maxOutputTokensPerAnswer`；
- `closed`；
- `updatedAt`。

参与者快照保持不可变，不混入运行字段。预算按根 Run/重试链共享，子 Run 不获得新额度。每次真实网络尝试前通过事务原子消费；进程在调用中被杀时不返还。OPTIONAL 消费必须保留 REQUIRED 额度。

### 5.3 Migration

新增连续 `MIGRATION_7_8`，只创建上述两表、索引和外键。旧 Run 迁移时不伪造成功或预算消费：运行表由首次安全恢复按确定性规则补齐；旧 Run 的预算仅在显式继续/重试前创建，默认不自动调用网络。保留 `7.json`，由真实 Room 编译导出 `8.json`；禁止 destructive migration。Instrumentation 验证 v7→v8、v1→v8、`PRAGMA foreign_key_check = 0`。

## 六、Repository 扩展

新增公共数据结构：

- `ExecutionParticipantStateEntity`；
- `ExecutionRunBudgetEntity`；
- `ExecutionRuntimeSnapshot`；
- `CreateExecutionRuntimeCommand`；
- `TransitionParticipantCommand`；
- `ConsumeExecutionBudgetCommand`；
- `FinalizeParticipantCommand`；
- `RecoverInterruptedExecutionCommand`。

新增最小事务接口：

```kotlin
suspend fun createExecutionRuntime(command): RepositoryResult<ExecutionRuntimeSnapshot>
suspend fun getExecutionRuntime(runId: String): RepositoryResult<ExecutionRuntimeSnapshot>
suspend fun transitionParticipant(command): RepositoryResult<ExecutionParticipantStateEntity>
suspend fun consumeExecutionBudget(command): RepositoryResult<ExecutionRunBudgetEntity>
suspend fun finalizeParticipant(command): RepositoryResult<ExecutionRuntimeSnapshot>
suspend fun recoverInterruptedExecution(command): RepositoryResult<ExecutionRuntimeSnapshot>
```

启动事务同时创建 Run、冻结参与者、QUEUED 状态和根预算；同一 Stage 存在 `NOT_STARTED/RUNNING/PARTIAL_SUCCESS` 时拒绝新的根 Run。同一稳定命令返回幂等成功；同键不同 payload 返回冲突。

`finalizeParticipant` 原子更新参与者、Pending Message 和 Run 聚合状态。网络调用永不进入数据库事务。Repository 不调用 Gemini。

恢复快照增加成员运行状态与预算。原先仅从 Message 推导成功/可重试的辅助函数改为优先使用运行表；旧数据缺少运行表时使用兼容推导，但不自动发请求。

## 七、启动、Skill 快照与上下文

`ExecutionStartCommand` 接受：`issueId`、`stageId`、稳定 `commandId/idempotencyKey`、用户触发 Message、已确认且按顺序排列的官方 Skill ID、可选组合 ID、运行配置、确认时间和上下文贡献。

执行前从统一 `OfficialSkillCatalogRuntime` 校验：ID 存在、Catalog 加载成功、`availability.executable`、真实 `assetPath`、System Prompt 非空、发布与资源状态允许执行。风险人物仍可执行，不因风险级别丢失官方身份。

快照冻结 ID、名称、头像、资源路径、System Prompt、配置、默认职责、position 和时间。默认职责只作为本组合关注点，不覆盖 System Prompt 或安全边界。

`ExecutionContextBuilder` 只使用 Issue、Stage、该 Stage 已有 Message、当前用户输入、冻结参与者和显式贡献；`roundIndex` 仅表示响应批次。多成员默认不读取本批次其他成员的新输出。

为 PR09-09 冻结：

```kotlin
data class ExecutionContextContribution(
    val sourceId: String,
    val sourceType: String,
    val content: String,
    val contentHash: String,
    val userConfirmedAt: Long,
    val networkAllowed: Boolean,
    val sensitive: Boolean,
)
```

贡献按调用方确认顺序稳定加入；单项失败隔离并映射安全错误；总字符边界由运行配置限制。PR09-09 不修改状态机、预算或网络调用。

## 八、网络、Key 与错误

`ExecutionNetworkGateway` 只暴露可取消的流式事件，不暴露 Retrofit 单例或 API Key：

```kotlin
sealed interface ExecutionStreamEvent {
    data object AttemptStarted
    data class TextDelta(val accumulatedText: String)
}
```

生产 Adapter 复用现有 Gemini/Interactions 客户端与 `ApiKeyScheduler`。只使用用户导入 Key；Key、Key 索引、Header、完整 Prompt、资料正文和第三方原始敏感元数据均不写数据库或日志。无可用 Key 时在创建 Pending 前返回 `NO_API_KEY`。

稳定错误码：`no_api_key`、`offline`、`rate_limited`、`authentication_failed`、`timeout`、`empty_response`、`provider_error`、`safety_blocked`、`budget_exhausted`、`storage_failure`、`invalid_skill`、`invalid_state`、`user_stopped`、`process_interrupted`。UI 文案与错误码分离；`CancellationException` 原样传播；用户停止不展示为系统错误。

## 九、执行、流式与停止

单 Skill 与多 Skill 使用同一引擎；多 Skill 严格按冻结 position 顺序执行。每位成员：

```text
participant RUNNING
→ 原子消费 REQUIRED 预算
→ 创建稳定 Pending Message
→ participant STREAMING
→ 节流原位更新 Pending
→ 成功原位完成 keepPending=false
→ participant SUCCEEDED
→ 聚合 Run
```

流式写入以累计文本为准，最短 120ms 或文本增长 64 字后落库，最终文本无条件刷新。迟到片段必须同时通过 Run 与 Participant 状态门禁。

失败部分文本采用“保留并标记不完整”：若已有非空文本，Pending 原位收敛为完成消息，`hasIncompleteOutput=true`；空文本则收敛为空的安全失败占位，不伪造成功。任何失败都不得留下永久 Pending。

停止顺序：先持久化 Run=STOPPED 和当前/排队成员状态，再阻止后续持久化回调，然后取消协程/HTTP，最后收敛 Pending。已成功成员保留；未开始成员不调用；重复停止幂等。

Run 聚合：全部成功→SUCCEEDED；已有成功且仍在执行→PARTIAL_SUCCESS；主动执行结束且存在可恢复成员→RETRYABLE；无成功且不可恢复→FAILED；用户停止→STOPPED。

## 十、重试与进程恢复

原位重试创建子 Run：`retryOfRunId = previousRun.id`。只复制 FAILED、TIMED_OUT、STOPPED、RETRYABLE 或 QUEUED 且未成功的原始冻结快照；不读取当前 Catalog 替换历史配置；成功成员及 Message 数量保持不变。子 Run 使用新 Run ID 和新幂等键，但共享根预算。

恢复只读页面不自动改状态。用户显式执行“恢复中断”时，事务将 RUNNING/STREAMING 成员收敛为 RETRYABLE + `process_interrupted`，QUEUED 保持可重试，已成功保持成功，已预留/消费预算不返还，Pending 按部分文本策略关闭。之后用户显式重试才创建子 Run；不会自动创建 Stage、Run、Pending 或网络请求。

进程内 `per-run Mutex` 和 Coordinator Registry 只优化单进程并发；数据库唯一约束、活跃 Run 门禁、幂等键和 CAS 才是最终保证。

## 十一、旧链兼容与删除阶段

旧 ChatSession 仍由旧 Route/`RoundtableViewModel` 只服务 `legacy-chat-*` 兼容内容。新见域 Issue 工作区只使用 `ExecutionRunCoordinator` 和 `JianyuRepository`；不得调用旧 `RoundtableDatabaseGateway` 或 `RoundtableOrchestrator`。

本 PR 不删除旧会话读取、音频或旧 UI；新增架构测试证明新 Issue Route 不引用旧 Gateway。旧执行入口的删除阶段冻结为 PR09-06：当首页和正式 Issue 创建入口接入新 Coordinator 后，删除旧 UI 的新增模型调用入口；旧历史会话继续只读。若 PR09-06 无法完成删除，必须单独建立兼容清理 PR，不得扩散到 PR09-09。

## 十二、基础 UI 与 UiState

新增 `ui/screens/execution/`：

- `IssueExecutionRoute.kt`：收集 ViewModel 状态、处理一次性事件；
- `IssueExecutionViewModel.kt`：只调用 Coordinator/Repository；
- `IssueExecutionUiState.kt`：稳定映射和纯 reducer；
- `IssueExecutionScreen.kt`：无 IO；
- `IssueExecutionComponents.kt`：参与者、消息、预算、错误和恢复提示。

稳定状态覆盖 Loading、Idle、Ready、Running、PartialSuccess、Succeeded、Retryable、Stopped、Failed、Recovering、StorageFailure、NoApiKey、Offline、RateLimited、BudgetExhausted。Snackbar/导航为事件，不保存业务事实。

工作区显示 Issue、Stage、Run、成员顺序和状态、流式/成功/不完整文本、错误成员、停止、重试、恢复提示和预算。沿用 MaterialTheme；不改四个一级目的地、不新建 NavHost、不自动执行。

## 十三、文件清单

### 新增生产文件

```text
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeEntities.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeMigration.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionStateMachine.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionBudgetPolicy.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionContextBuilder.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionNetworkGateway.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionErrorMapper.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionComponents.kt
```

### 修改生产文件

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesRoute.kt
app/src/main/java/com/elio/jianyu/ui/App.kt
app/schemas/com.elio.jianyu.data.RoundtableDatabase/8.json
```

只在证据需要时修改旧 `RoundtableViewModel`/`ChatRepository`，且仅用于隔离新领域 Pending；不重构旧音频、标题或搜索链。

### 测试文件

```text
app/src/test/java/com/elio/jianyu/execution/ExecutionStateMachineTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionBudgetPolicyTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionContextBuilderTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionRetrySelectorTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionErrorMapperTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionRunCoordinatorTest.kt
app/src/test/java/com/elio/jianyu/ui/IssueExecutionArchitectureTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeDatabaseTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt
app/src/androidTest/java/com/elio/jianyu/execution/ExecutionRunCoordinatorIntegrationTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreenTest.kt
```

### 禁止文件

不修改官方 44 项 Catalog 定义、品牌 Token、App Icon、资料/背景实体、成果/音频生命周期、备份、官网、仓库设置和发布配置。

## 十四、TDD 顺序与首个失败测试

1. 先写 `ExecutionStateMachineTest`，证明 `RUNNING -> SUCCEEDED` 合法、`SUCCEEDED -> RUNNING` 非法、`COMPLETED` 禁止新写入；由于远端无 Gradle 工作区，首次提交只记录“测试尚未实际执行”，不得写成 RED 已运行。
2. 实现最小纯状态机；由 GitHub CI 实际验证 JVM。
3. 写预算策略、聚合、错误映射、上下文与重试选择测试，再实现纯逻辑。
4. 写 Room v8 Migration/事务测试，再实现实体、DAO、Migration 和 Repository。
5. 写 Fake Gateway Coordinator 测试，再实现启动、顺序流式、停止、错误、重试和恢复。
6. 写 UI reducer/架构/Compose 测试，再接入工作区。
7. 完整静态复核、CI 和本地只读验收。

首个测试的生产变更触发点：删除合法转换或允许终态回退时必须失败；它不通过 mock 验证实现细节。

## 十五、Commit 边界

```text
docs: 制定PR09-07执行运行实施计划
test: 增加执行状态机失败场景
feat: 建立执行状态与预算纯逻辑
feat: 增加执行运行Room v8迁移
test: 完善执行运行迁移验证
feat: 扩展执行运行Repository事务
feat: 接入单多Skill顺序流式执行
feat: 增加停止与失败收敛
feat: 增加子Run重试与进程恢复
refactor: 收口新议题执行入口
feat: 增加执行工作区基础界面
test: 完善执行设备与架构验证
docs: 增加PR09-07本地只读验收Prompt
```

每个 Commit 保持原子，不添加 `Co-Authored-By`。

## 十六、验证与 CI

远端可实际执行并读取：`git diff --check`、Secret scan、应用身份门禁、Kotlin 编译、JVM、Lint、Debug、Release/R8、Room Schema 当前性和 GitHub CI。普通 CI 不执行 Instrumentation 时明确记录未执行。

本地 AI 严格只读执行：环境记录、精确 Head/差异门禁、全量 JVM、定向状态机/预算/重试/上下文测试、`compileDebugAndroidTestKotlin`、Migration、Database、Fake Gateway、Stop、Streaming、Retry、Recovery、UI、全量 `connectedDebugAndroidTest`。基线 101 项不得以既有失败放行；报告总数、通过、失败、跳过和新增数。

外部强停分两阶段：A 成功、B streaming、C queued 后 `adb force-stop`；重启确认无自动网络、A 不重复、B/C 可恢复、预算不归零；显式重试只执行 B/C；外键检查为 0。

## 十七、风险与回滚

主要风险：Room v8 Migration 与 Schema 漂移；旧/新执行入口误混；流式节流导致最终文本遗漏；停止与迟到回调竞争；预算原子消费边界；Catalog 资产和 System Prompt 读取失败；基础 UI 大字体/窄屏回归。

防护：连续 Migration、稳定 ID、CAS、事务、状态门禁、Fake Gateway、Clock/ID 注入、最终无条件 flush、架构守卫和外部强停测试。

回滚顺序：关闭新 Issue 执行入口并保留 v8 数据；回滚 Coordinator/UI 不删除运行表；若 Migration 本身有缺陷，在合并前修正 7→8 并重新生成 8.json，禁止降级或 destructive migration；已创建的 v8 数据库不通过回退 App 版本处理。

## 十八、完成与未验证门禁

只有 GitHub CI 和本地设备验收均获得真实证据后，才可认为 PR09-07 完成。当前计划提交时：生产代码尚未修改；测试尚未执行；Room 仍为 v7；Draft PR 尚未创建。开发完成后 PR 保持 Draft，未经用户授权不得 Ready、合并、删分支或启动 PR09-09。
