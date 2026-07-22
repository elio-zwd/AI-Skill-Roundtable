# PR 02：圆桌调度与 API Key 编排重构

> 状态：Planned  
> 优先级：P0  
> 前置依赖：PR 01 已合并并可稳定构建  
> 后续依赖：PR 03、PR 04、PR 05  
> 覆盖审查项：F02、F03、F08、F09、F12（调度与重试部分）

---

## 1. 任务目标

把当前“看起来按 Key 分组、实际网络层重新选 Key；看起来顺序圆桌、实际第一轮并发”的实现，重构成一套**行为确定、可测试、调用量可控**的圆桌编排系统。

本 PR 完成后必须满足：

1. 默认产品语义是**严格顺序圆桌**。
2. 后一个角色发言前，必须重新读取数据库，并看到前一个角色已经成功入库的发言。
3. 一个网络请求实际使用哪个 Key，由调度层明确决定，网络层不得偷偷换成其他 Key。
4. Key 重试顺序不重复；同一请求中每个 Key 最多尝试一次。
5. 400/401/403/404/429/5xx/网络异常按不同策略处理。
6. 每次用户提问有硬性角色数、搜索数、请求数和输出 Token 预算。
7. 核心调度和重试逻辑可以通过 JVM 单元测试验证，不依赖 Compose UI。

---

## 2. 本 PR 的固定产品决策

为避免执行 AI 自行摇摆，本 PR 不再“二选一”，固定采用：

## **严格顺序圆桌（Serial Roundtable）**

标准流程：

```text
用户提问入库
  ↓
读取当前激活角色并确定顺序
  ↓
角色 A 读取最新消息 → 请求 → 回答入库
  ↓
角色 B 重新读取最新消息（包含 A）→ 请求 → 回答入库
  ↓
角色 C 重新读取最新消息（包含 A、B）→ 请求 → 回答入库
  ↓
达到角色上限或全部完成
```

禁止继续保留“不同 Key 组并发、同组串行”的主流程。

原因：

- README 的核心卖点就是后一个角色看到前序发言。
- 当前并发会造成数据库完成顺序和 Interaction ID 竞态。
- 严格串行最容易验证，也更容易控制配额和费用。
- 多 Key 在本 PR 中仍可用于**失败切换和不同会话的负载分散**，但不用于同一轮角色并发轰炸。

未来如需“并行专家面板”，必须作为独立模式和独立 PR，实现单独 UI、预算和文档，不能混入本 PR。

---

## 3. 本 PR 明确不做什么

- 不大改 Compose UI 风格。
- 不新增角色。
- 不升级模型或 SDK 版本。
- 不实现真正的服务端 Key Provider。
- 不在本 PR 完成全部隐私遥测改造；PR 03 负责。
- 不修改 Release 包名和签名；PR 04 负责。
- 不通过随机延迟或多 Key 轮换规避服务商限制。
- 不删除现有对话、音频和 Skill 功能。

---

## 4. 执行 AI 必须先读的文件

1. `AGENTS.md`
2. `docs/planning/pr-execution-master-plan.md`
3. 本文件
4. `app/src/main/java/com/elio/skillroundtable/viewmodel/RoundtableViewModel.kt`
5. `app/src/main/java/com/elio/skillroundtable/network/GeminiApi.kt`
6. `app/src/main/java/com/elio/skillroundtable/network/ApiKeyPool.kt`
7. `app/src/main/java/com/elio/skillroundtable/network/ApiKeyModels.kt`
8. `app/src/main/java/com/elio/skillroundtable/network/EncryptedApiKeyStore.kt`
9. `app/src/main/java/com/elio/skillroundtable/data/ChatRepository.kt`
10. `app/src/main/java/com/elio/skillroundtable/data/ChatDao.kt`
11. `app/src/main/java/com/elio/skillroundtable/data/Message.kt`
12. `app/src/main/java/com/elio/skillroundtable/data/Character.kt`
13. `app/src/test/java/com/elio/skillroundtable/network/ApiKeyProviderTest.kt`
14. `docs/architecture/system-architecture.md`
15. `README.md` 的圆桌调度描述

必须搜索以下符号的全部调用方：

```powershell
git grep -n -I 'runRoundtableSequence\|executeCharacterAnswer\|callGeminiApi\|createInteractionWithFallback\|generateContentWithFallback\|callBrokerRouterWithFallback\|embedContentWithFallback\|getOrBindSessionKey\|bindSessionKey\|assignRandomGroups\|lastInteractionIds'
```

---

## 5. 修改前必须记录的现状

执行 AI 应在交付报告中记录：

1. 当前激活角色默认数量。
2. `runRoundtableSequence()` 是否并发启动多个角色。
3. `apiKey` 参数从 `keyInfo.key` 传到哪里后失去控制作用。
4. `sessionId -> lastInteractionId` 的写入位置。
5. 当前一次角色回答可能触发多少次 API 请求。
6. 当前 429、401、400、5xx 分别如何处理。

不得在未理解现状前直接替换整个 ViewModel。

---

## 6. 推荐目标结构

本 PR 允许根据现有包结构微调文件名，但职责必须清晰。

建议新增：

```text
app/src/main/java/com/elio/skillroundtable/roundtable/
├── RoundtableOrchestrator.kt
├── RoundtableBudget.kt
├── RoundtableRunResult.kt
└── TranscriptBuilder.kt

app/src/main/java/com/elio/skillroundtable/network/keys/
├── ApiKeyLease.kt
├── ApiKeyScheduler.kt
└── ApiKeyAttemptPlan.kt

app/src/main/java/com/elio/skillroundtable/network/retry/
├── ApiCallFailure.kt
├── ApiRetryDecision.kt
└── ApiRetryPolicy.kt
```

如果执行 AI 认为新目录过多，可以保留在现有 `network/` 和 `viewmodel/`，但至少要抽出以下纯逻辑类：

- `RoundtableBudget`
- `ApiKeyAttemptPlan` 或等价实现
- `ApiRetryPolicy`
- `TranscriptBuilder`

这些类必须可在 JVM 单测中直接测试。

---

## 7. 详细实施步骤

### 7.1 建立圆桌运行预算

新增不可变配置，例如：

```kotlin
data class RoundtableBudget(
    val maxCharactersPerQuestion: Int = 6,
    val maxSearchQueriesPerCharacter: Int = 3,
    val maxApiCallsPerQuestion: Int = 30,
    val maxOutputTokensPerAnswer: Int = 4096
)
```

说明：

- 默认角色上限固定为 6，防止默认激活 20 人时产生过高调用量。
- 用户未来可在 UI 调整，但本 PR 至少要有安全默认值。
- `maxApiCallsPerQuestion` 是硬上限，不是提示词建议。
- 所有 Broker、搜索、Embedding、标题、主回答、续写都必须计数。
- 达到预算时停止后续可选请求，并给出可理解状态，不得继续偷偷调用。

建议增加运行期计数器：

```kotlin
data class RequestBudgetTracker(
    val limit: Int,
    val used: Int = 0
) {
    fun tryConsume(count: Int = 1): RequestBudgetTracker?
}
```

也可以使用线程安全类，但顺序圆桌主流程不应再需要复杂并发锁。

#### 搜索数量必须代码截断

不能只在 Prompt 写“建议 1～3 个”。解析完成后必须：

```kotlin
val finalQueries = decision.searchQueries
    .asSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .take(budget.maxSearchQueriesPerCharacter)
    .toList()
```

#### 角色数量必须代码截断

在排序后：

```kotlin
val selectedCharacters = sortedCharacters
    .take(budget.maxCharactersPerQuestion)
```

如果用户激活角色超过限制，UI 或错误/状态信息应明确显示“本轮执行前 N 位角色”。

---

### 7.2 把圆桌主流程改为严格串行

当前的 `coroutineScope { keyGroups.forEach { launch { ... } } }` 必须移除或退出主调用路径。

建议流程伪代码：

```kotlin
suspend fun runRoundtable(
    sessionId: Long,
    orderedCharacters: List<Character>,
    roundIndex: Int,
    budget: RoundtableBudget
): RoundtableRunResult {
    val completed = mutableListOf<String>()
    val failures = mutableListOf<CharacterFailure>()

    for (character in orderedCharacters.take(budget.maxCharactersPerQuestion)) {
        val latestMessages = chatRepository.getMessages(sessionId)
        val transcript = transcriptBuilder.build(
            messages = latestMessages,
            currentCharacter = character,
            roundIndex = roundIndex
        )

        val result = answerCharacter(...)
        when (result) {
            is Success -> completed += character.id
            is Failure -> failures += ...
        }
    }

    return RoundtableRunResult(completed, failures, ...)
}
```

关键要求：

- 每个角色开始前重新读取 `chatRepo.getMessages(sessionId)`。
- 上一个角色成功回答必须先入库，才能轮到下一个。
- 某个角色失败时，删除该角色 Pending 消息并记录失败；默认继续下一个角色，不让整轮永久卡死。
- `_isRoundtableRunning` 只由最外层一次设置和清理。
- 禁止递归调用 `runRoundtableSequence()` 自动补齐；使用明确循环，避免栈和状态难追踪。
- 用户重复点击“开始/下一位”时，应避免同一会话同时运行两套圆桌。可用 `Mutex` 或会话级运行标记。

建议：

```kotlin
private val roundtableMutex = Mutex()
```

或按 sessionId 管理 Mutex，但不得引入无法清理的静态锁表。

---

### 7.3 修复 Transcript 构建

将 `buildTranscript()` 抽到纯逻辑 `TranscriptBuilder`。

行为要求：

#### 第一轮

角色 A 只看到用户问题。

角色 B 必须看到：

```text
用户问题
角色 A 第 1 轮发言
```

角色 C 必须看到：

```text
用户问题
角色 A 第 1 轮发言
角色 B 第 1 轮发言
```

因此禁止保留“roundIndex == 1 时只加入用户问题”的旧逻辑。

#### 后续轮次

应包含本次用户问题之后所有已完成、非 Pending 的角色发言，并保留轮次编号。

#### 排除内容

- `isPending == true` 的占位消息。
- 当前角色本次尚未完成的 Pending。
- 上一个用户问题之前的旧会话内容，除非产品明确要多问题长会话上下文；当前默认以最近用户问题为边界。

#### 测试必须覆盖

- 第一轮 A/B/C 的可见上下文逐步增加。
- Pending 不进入 Prompt。
- 新用户问题会切断上一问的圆桌记录。
- 中文和 Markdown 文本不丢失。

---

### 7.4 引入明确的 Key Lease

新增：

```kotlin
data class ApiKeyLease internal constructor(
    val keyId: String,
    val displayName: String,
    internal val secret: String,
    val source: ApiKeySource
)
```

原则：

- UI 仍只能读取掩码摘要。
- 完整 Key 只在网络边界使用。
- 调度层拿到 Lease 后，网络层必须使用该 Lease 的 secret。
- 网络层不能再调用 `getOrBindSessionKey()` 偷偷改用别的 Key。

建议把当前 API 拆成：

```kotlin
fun createAttemptPlan(
    context: Context,
    preferredKeyId: String? = null
): List<ApiKeyLease>
```

尝试顺序规则：

1. 过滤禁用、无效和仍在冷却中的 Key。
2. 首先使用当前会话绑定 Key（如果仍可用）。
3. 其余 Key 按确定性轮转顺序排列。
4. `distinctBy { keyId }`。
5. 同一请求计划内每个 Key 最多出现一次。

禁止：

```kotlin
available.firstOrNull { it.id != current.id }
```

这种写法可能在 A、B 之间来回，永远不尝试 C。

成功后可以更新 session binding 和 last used；失败后是否换绑由错误策略决定。

---

### 7.5 把网络调用改成“指定 Key 执行”

网络层应有不负责选 Key 的底层函数：

```kotlin
suspend fun createInteraction(
    lease: ApiKeyLease,
    request: CreateInteractionRequest
): Interaction
```

或保留 Retrofit Service，但上层 wrapper 必须显式接收 Lease。

重试 wrapper 示例：

```kotlin
suspend fun <T> executeWithKeyFallback(
    attemptPlan: List<ApiKeyLease>,
    operationName: String,
    block: suspend (ApiKeyLease) -> T
): T
```

要求：

- 每次调用记录当前尝试的 `keyId`，不记录完整 secret。
- 实际发请求的 Key ID 与调度日志完全一致。
- `apiKey` 不再作为一个“看似指定、实际可能被忽略”的 String 在多层传递。
- 旧的 `createInteractionWithFallback()`、`generateContentWithFallback()` 等可以逐步替换，但 PR 完成时不能有主路径仍使用旧的隐式选 Key 逻辑。

---

### 7.6 实现错误分类和重试策略

新增纯逻辑类型，例如：

```kotlin
sealed interface ApiCallFailure {
    data class Http(val code: Int, val retryAfterMs: Long? = null) : ApiCallFailure
    data class Network(val cause: Throwable) : ApiCallFailure
    data class Serialization(val cause: Throwable) : ApiCallFailure
    data class Unknown(val cause: Throwable) : ApiCallFailure
}

enum class ApiRetryDecision {
    STOP_REQUEST,
    RETRY_SAME_KEY,
    TRY_NEXT_KEY,
    COOLDOWN_AND_TRY_NEXT_KEY
}
```

固定策略：

| 失败类型 | Key 状态处理 | 是否换 Key | 是否重试 |
|---|---|---|---|
| HTTP 400 | 不修改 Key | 否 | 立即停止，说明请求格式错误 |
| HTTP 404 | 不修改 Key | 否 | 立即停止，说明模型/端点错误 |
| HTTP 401 | 标记 Key INVALID | 是 | 尝试下一个 Key |
| HTTP 403 | 标记 Key INVALID 或权限不足 | 是 | 尝试下一个 Key；记录权限错误 |
| HTTP 429 | 标记临时冷却 | 是 | 尝试下一个 Key；同 Key本请求不再试 |
| HTTP 408 | 不永久禁用 | 可先同 Key 重试 1 次 | 超过后停止或换 Key，必须有限 |
| HTTP 5xx | 不永久禁用 | 同 Key最多重试 1～2 次 | 指数退避后停止 |
| Socket/IO | 不永久禁用 | 同 Key最多重试 1 次 | 防止服务端已执行却重复计费 |
| 序列化错误 | 不修改 Key | 否 | 立即停止，属于客户端代码错误 |

#### 429 冷却

优先读取响应中的 `Retry-After`。没有时采用有上限的退避，例如：

```text
第一次：60 秒
第二次：5 分钟
再次：30 分钟
最大：24 小时
```

不得所有 429 一律固定封禁 24 小时，也不得为绕过限流快速轮换大量 Key。

#### 请求重试次数

- 每个 Key 每个逻辑请求最多进入一次 attempt plan。
- 网络/5xx 的“同 Key 重试”必须有独立小计数，最大 1～2 次。
- 整个请求总尝试数受 `RequestBudgetTracker` 限制。

---

### 7.7 消除 Interaction ID 竞态

当前 `ConcurrentHashMap<Long, String>` 以 sessionId 为唯一键，不适合多个角色共享不同 system instruction。

本 PR 的低风险目标：

1. 不允许多个角色并发写同一个 Interaction ID。
2. 角色回答的正确性优先于云端链复用。

推荐方案：

- 默认角色主回答使用显式完整 Transcript。
- `previousInteractionId` 暂时设为 `null`，或仅使用 `(sessionId, characterId)` 作为链键。
- 如果使用角色级链：

```kotlin
data class InteractionChainKey(
    val sessionId: Long,
    val characterId: String
)
```

- 每个角色的 chain 只在该角色自己的后续轮次复用。
- 一旦服务端返回 400/404 或链失效，清除对应角色链并进行一次无链全量上下文请求。
- 删除会话时清理该 sessionId 下所有角色链。

禁止继续：

```kotlin
lastInteractionIds[sessionId] = response.id
```

让不同角色覆盖同一条链。

PR 03 会进一步决定 `store=true/false` 的隐私默认值；本 PR 必须先让状态模型支持关闭云端链。

---

### 7.8 明确各类请求是否消耗预算

以下每一次网络请求都必须计数：

- 标题生成。
- Embedding。
- Broker 决策。
- 每条搜索 Query。
- 角色主回答。
- 续写。
- 失败后的网络重试。
- 换 Key 后的再次请求。

预算不足时的降级顺序建议：

1. 取消续写。
2. 减少搜索 Query。
3. 跳过可选 Broker，使用最小 Skill Prompt。
4. 停止后续角色。

不能在预算不足时跳过用户明确要求的主回答，却继续做标题或遥测类非必要调用。

建议结果对象包含：

```kotlin
data class RoundtableRunResult(
    val completedCharacterIds: List<String>,
    val failedCharacters: List<CharacterFailure>,
    val apiCallsUsed: Int,
    val stoppedByBudget: Boolean
)
```

UI 至少能通过状态或消息告诉用户本轮为何提前停止。

---

### 7.9 清理旧逻辑和术语

完成后搜索并处理：

```powershell
git grep -n -I 'assignRandomGroups\|反检测\|防屏蔽\|组间并发\|组内串行\|内置 Key 池'
```

要求：

- 主路径不再调用 `assignRandomGroups()`；若函数无其他用途，删除并更新测试。
- “内置 Key”改成“用户导入 Key”或“可用 Key”。
- “反检测/防屏蔽”改成“速率限制保护/配额保护”。
- README 和架构文档的最终全面同步放到 PR 05，但本 PR 修改到的代码注释必须立即准确。

---

## 8. 单元测试要求

建议新增：

```text
app/src/test/java/com/elio/skillroundtable/roundtable/TranscriptBuilderTest.kt
app/src/test/java/com/elio/skillroundtable/roundtable/RoundtableBudgetTest.kt
app/src/test/java/com/elio/skillroundtable/network/ApiKeyAttemptPlanTest.kt
app/src/test/java/com/elio/skillroundtable/network/ApiRetryPolicyTest.kt
app/src/test/java/com/elio/skillroundtable/roundtable/RoundtableOrchestratorTest.kt
```

最低测试场景：

### Transcript

- [ ] 第一角色只看到用户问题。
- [ ] 第二角色看到第一角色回答。
- [ ] 第三角色看到前两个回答。
- [ ] Pending 不进入 Transcript。
- [ ] 新用户问题切断上一问题上下文。

### Key Attempt Plan

- [ ] preferred Key 排第一。
- [ ] A/B/C 每个只出现一次。
- [ ] 禁用 Key 不出现。
- [ ] INVALID Key 不出现。
- [ ] 冷却中的 Key 不出现。
- [ ] 当前绑定 Key失效时自动移除绑定或忽略。

### Retry Policy

- [ ] 400/404 立即停止且不换 Key。
- [ ] 401/403 标记无效并换 Key。
- [ ] 429 冷却并换 Key。
- [ ] 5xx 有限重试。
- [ ] 序列化错误不换 Key。
- [ ] 尝试次数不会超过计划长度和预算。

### Budget

- [ ] 搜索 Query 去重并最多 3 条。
- [ ] 默认最多执行 6 个角色。
- [ ] 达到总请求上限后停止。
- [ ] 标题、Embedding、Broker、搜索、主回答、续写均计数。

### Orchestrator

使用 Fake Repository 和 Fake API Client 验证：

- [ ] 调用顺序严格 A → B → C。
- [ ] B 的输入包含 A 的已入库回复。
- [ ] A 失败后 Pending 被清理，B 仍可继续。
- [ ] 同一会话重复启动不会并行运行两套流程。
- [ ] 交付结果包含成功、失败和预算使用量。

---

## 9. 必须执行的验证

```powershell
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
```

人工/设备验收：

1. 只激活 3 个角色。
2. 提问一个容易看出引用关系的问题。
3. 确认角色 B 明确能引用角色 A 已完成内容。
4. 确认角色 C 能引用 A 和 B。
5. 在 Key 管理中准备至少 3 个测试 Key 或 Fake 环境。
6. 模拟 A Key 401，确认只标记 A 无效并尝试 B。
7. 模拟 400，确认不轮询所有 Key。
8. 模拟 429，确认按冷却策略处理。
9. 查看本轮调用统计没有超过预算。

若无法真实触发不同 HTTP 状态，必须通过 Fake API 单测覆盖，不允许只靠人工猜测。

---

## 10. 预计修改文件

可能新增：

```text
app/src/main/java/com/elio/skillroundtable/roundtable/*
app/src/main/java/com/elio/skillroundtable/network/keys/*
app/src/main/java/com/elio/skillroundtable/network/retry/*
app/src/test/java/com/elio/skillroundtable/roundtable/*
app/src/test/java/com/elio/skillroundtable/network/*
```

必然修改：

```text
app/src/main/java/com/elio/skillroundtable/viewmodel/RoundtableViewModel.kt
app/src/main/java/com/elio/skillroundtable/network/ApiKeyPool.kt
app/src/main/java/com/elio/skillroundtable/network/ApiKeyModels.kt
app/src/main/java/com/elio/skillroundtable/network/GeminiApi.kt
```

可能修改：

```text
README.md（只更新明显错误的核心一句话，完整整理留给 PR 05）
docs/architecture/system-architecture.md（同上）
```

---

## 11. 验收清单

- [ ] 主流程是严格顺序圆桌。
- [ ] 后一角色每次重新读取最新数据库消息。
- [ ] 第一轮也包含同轮前序角色发言。
- [ ] 主路径不存在角色组并发请求。
- [ ] 实际网络 Key 与 Lease 的 keyId 一致。
- [ ] 网络层不再隐式调用 `getOrBindSessionKey()` 重新选 Key。
- [ ] A/B/C 尝试计划不重复。
- [ ] 400/404 不换 Key。
- [ ] 401/403 标记无效并换 Key。
- [ ] 429 使用临时冷却，不默认全部封禁 24 小时。
- [ ] 5xx/网络错误有限重试。
- [ ] Interaction 链不再由不同角色共享 sessionId 单键覆盖。
- [ ] 搜索 Query 硬限制最多 3 条。
- [ ] 默认最多执行 6 个角色。
- [ ] 有总 API 请求预算。
- [ ] 核心调度和策略有 JVM 单测。
- [ ] 编译、测试、Lint、Debug 构建通过。

---

## 12. 禁止事项

- 禁止继续用随机延迟、多 Key 并发描述为“反检测”。
- 禁止为了测试通过而删除错误处理。
- 禁止把所有异常统一 catch 成“换下一个 Key”。
- 禁止执行 AI 自行改回并行模式。
- 禁止在 UI 或日志显示完整 Key。
- 禁止把 20 个角色全部作为不可配置的默认调用数量。
- 禁止通过递归反复调用圆桌函数补齐失败角色。
- 禁止保留两套主调度路径，由布尔值随机切换但没有完整测试。

---

## 13. 交付报告必须额外回答

1. 严格顺序圆桌在哪个类中实现？
2. 角色 B 如何保证看到角色 A 已入库回答？
3. Key Lease 从哪里创建，在哪一层被实际使用？
4. 同一请求的 Key 尝试顺序示例是什么？
5. 400、401、403、404、429、500、SocketTimeout 分别如何处理？
6. Interaction ID 的键是什么？是否支持完全关闭链式存储？
7. 默认角色上限、搜索上限、输出 Token 和总请求上限分别是多少？
8. 哪些单元测试证明没有并发竞态和重复 Key 尝试？
9. 删除了哪些旧函数或旧注释？
10. 对调用次数、响应速度和费用有什么可预期影响？
