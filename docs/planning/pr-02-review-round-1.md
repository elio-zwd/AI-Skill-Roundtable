# PR 02 第一轮代码审查整改单

> 审查对象：`e87bffea1dbdf061d9922f203a6a88cef7d60093`
>
> 审查结论：**Changes Required / 暂不验收**
>
> 处理建议：由原执行 AI 在当前 PR02 分支继续整改，形成一个新的修复提交；不要进入 PR03。

---

## 1. 已确认做对的部分

以下方向正确，应保留：

1. 主圆桌流程已从多组并发改为串行遍历。
2. 每个角色开始前重新读取最新消息。
3. 新增 `ApiKeyLease`，网络请求显式使用租约中的 secret。
4. Key 尝试计划会去重，不再在 A/B 间循环。
5. 新增 HTTP/网络/序列化错误分类与有限重试框架。
6. 新增角色数、搜索数、请求数、输出 Token 默认预算。
7. Interaction ID 改为 `(sessionId, characterId)` 维度，消除了不同角色直接覆盖同一 ID 的竞态。
8. 搜索 Query 已在代码中去重并截断。

这些改动说明重构方向正确，本轮不要求推倒重来。

---

## 2. 阻断问题：必须整改后才能验收

### B01：预算不是“每次用户提问”的硬预算

当前 `RequestBudgetTracker` 在每次 `runRoundtableSequence()` 调用时重新创建。用户点击“催促剩余智囊作答”后，会为同一个用户问题获得新的 6 角色和 30 请求预算，因此可以突破：

- `maxCharactersPerQuestion = 6`
- `maxApiCallsPerQuestion = 30`

同时标题生成仍调用旧的 `callBrokerRouterWithFallback()`，该函数自行创建独立 Tracker，标题请求不计入本次问题的 30 次预算。

#### 必须修改

1. 为每一条用户问题建立稳定的 `questionRunId`，建议直接使用用户消息 ID。
2. 预算 Tracker 生命周期必须绑定 `questionRunId`，不能绑定某次函数调用。
3. 同一问题再次点击“催促”时：
   - 不得重置角色上限和请求预算；或
   - 明确作为用户主动扩展预算，并要求 UI 显式确认，不能静默重置。
4. 标题、Embedding、Broker、搜索、主回答、续写、同 Key 重试、换 Key 请求必须共享同一个 Tracker。
5. 删除主路径对旧 fallback API 的调用；`generateSessionTitle()` 也必须使用同一个 Lease Plan 和 Tracker。
6. 预算必须预留主回答额度。不能先把额度全部花在标题、Broker、搜索上，最后让主回答失败。

#### 推荐降级顺序

1. 跳过标题。
2. 跳过续写。
3. 减少或跳过搜索。
4. 跳过可选 Broker，使用主 Skill Prompt。
5. 最后才停止后续角色。

#### 必须新增测试

- 同一问题连续调用两次编排器，角色总数仍不超过 6。
- 同一问题连续调用两次，API 调用总数仍不超过 30。
- 标题生成计入同一 Tracker。
- 预算不足时先取消搜索/续写，仍保留当前角色主回答额度。

---

### B02：角色级 Interaction 链在后续轮次丢失其他角色上下文

当前每个角色维护自己的 `InteractionChainKey(sessionId, characterId)`，这是正确的隔离方向；但当 `useChain == true` 时，只发送一段通用“现在轮到你”的通知，没有发送该角色上次发言之后新增的其他角色回复。

例子：

- 第一轮 A 最先回答，其链只包含用户问题和 A 的回答。
- 随后 B、C、D 的回答不会自动进入 A 的云端链。
- 第二轮 A 使用自己的 `previousInteractionId` 时，看不到 B、C、D 第一轮发言。

这违反严格圆桌“后一个阶段能够看到全部已完成发言”的核心语义。

#### 必须修改，二选一

**优先方案：PR02 暂停云端链复用**

- 主回答始终发送 `TranscriptBuilder` 生成的完整当前问题上下文。
- `previousInteractionId = null`。
- 保留角色级链数据结构，为 PR03 的 `store` 隐私设置预留，但默认不启用。

**可选方案：实现可靠的增量游标**

- 每个角色链额外记录上次已同步到云端的本地消息 ID。
- 下一轮调用时，把该游标之后所有已完成的其他角色消息作为增量发送。
- 必须有测试证明 A 第二轮能看到 B/C/D 第一轮发言。

能力一般的执行 AI应采用优先方案，不要实现复杂增量同步。

#### 必须新增测试

- A、B、C 第一轮完成后，A 第二轮的实际请求包含 B、C 第一轮内容。
- 清除/关闭链后仍可用完整 Transcript 正常回答。

---

### B03：链式请求失败时对“任何异常”都执行全量重发

当前 `useChain` 分支捕获所有 `Exception`，随后清除链并用完整历史再次请求。

这会在以下情况错误重发：

- 网络超时，但服务端可能已经执行成功。
- 5xx 已完成有限重试。
- 预算耗尽。
- 401/403 所有 Key 都失败。
- 序列化错误。
- 其他客户端代码错误。

结果可能是重复调用、重复计费、掩盖根因，甚至在预算异常后再次消耗预算。

#### 必须修改

1. 网络执行器必须抛出结构化异常，例如：

```kotlin
class ApiExecutionException(
    val failure: ApiCallFailure,
    val operationName: String,
    val keyId: String?,
    cause: Throwable
) : Exception(...)
```

2. 仅在明确判断为“previous interaction 无效/已过期”的 400 或特定服务端错误码时：
   - 清除对应角色链；
   - 允许一次无链完整 Transcript 重试。
3. 网络超时、5xx、预算耗尽、序列化、鉴权失败不得触发链兜底重发。
4. 无链兜底也必须使用剩余预算，并保证最多一次。

#### 必须新增测试

- 链 ID 无效 400：清链，并且只进行一次无链兜底。
- 网络超时：不进行全量兜底重发。
- 预算耗尽：不进行任何额外请求。
- 序列化错误：不换 Key、不重发。

---

### B04：当前“编排测试”没有测试生产编排代码

`RoundtableOrchestratorTest` 内部重新手写了：

- `FakeChatRepository`
- `FakeGeminiApi`
- `MockRoundtableOrchestrator`

测试只证明这个测试文件里的 Mock 循环能够串行运行，不能证明 `RoundtableViewModel.runRoundtableSequence()` 或生产 Orchestrator 正确。

因此“核心编排已被 JVM 单测覆盖”的交付声明不成立。

#### 必须修改

1. 把生产编排流程从 `RoundtableViewModel` 抽成真实的 `RoundtableOrchestrator`。
2. 通过接口注入：
   - Chat repository/gateway
   - Character answer gateway
   - Budget session/store
   - Clock/Delay（如仍需要）
3. ViewModel 只负责 UI StateFlow 和调用生产 Orchestrator。
4. 测试必须直接实例化生产 `RoundtableOrchestrator`，禁止在测试文件内复制一份业务实现。

#### 必须新增测试

- 第二角色实际接收到包含第一角色回答的 Transcript。
- 某个角色失败后 Pending 被清理，并继续下一个角色。
- 同一问题重复触发不重置预算。
- 重入时返回明确结果，不是静默丢弃。

---

## 3. 重要问题：建议本轮一并修复

### M01：Key 调度没有实现不同会话的负载分散

无绑定、无 preferred Key 时，计划始终按随机 UUID 形式的 `keyId` 字典序排序，因此所有新会话长期从同一个 Key 开始。

#### 修改要求

- 使用 `lastUsedKeyId` 做确定性轮转：上次成功 Key 放到计划末尾，其余按稳定顺序排列。
- session 已绑定且可用时仍优先绑定 Key。
- preferred Key 优先级高于默认轮转。

#### 新增测试

- preferred Key 第一。
- bound Key 第一。
- 新会话会避开 lastUsed Key 优先尝试其他 Key。
- A/B/C 每个最多出现一次。

---

### M02：测试用例未覆盖任务文档规定的最低场景

必须补齐：

#### Transcript

- 第一轮 A 只看用户问题。
- 第一轮 B 看用户问题 + A。
- 第一轮 C 看用户问题 + A + B。
- Pending 排除。
- 新用户问题切断上一问。
- 中文和 Markdown 不丢失。

#### Key Plan

- preferred、bound、lastUsed 的优先级。
- disabled、INVALID、冷却 Key 不出现。
- 失效绑定被忽略或清理。

#### Retry

- 400、404、401、403、408、429、5xx、IO、序列化。
- Retry-After。
- 同 Key最大重试数。
- 总尝试不超过 Plan 和预算。

#### Budget

- 搜索 Query 去重与截断是实际生产代码测试，不只是检查配置默认值。
- 角色上限是同一问题生命周期限制。
- 标题和重试计数。
- 降级顺序。

---

### M03：仍保留随机 2～5 秒延迟

PR02 明确禁止继续用随机延迟表达“反检测/防屏蔽”。当前串行循环仍随机等待 2～5 秒。

#### 修改要求

- 默认删除随机延迟。
- 如果确实需要请求节流，改为明确命名的可配置固定最小间隔，例如 `minIntervalMs`，默认值由产品策略决定。
- 测试中通过注入 Delay Provider，禁止真实等待。
- 文案统一使用“速率限制保护/配额保护”。

---

### M04：5xx 使用固定 1 秒重试，不是指数退避

#### 修改要求

- 实现有上限的退避，例如：第一次 1 秒、第二次 2 秒。
- 通过注入 Delay Provider 测试，不在 JVM Test 中真实等待。
- IO 仍最多同 Key 重试 1 次。

---

### M05：用户看不到“本问题只执行前 6 位”

当前只写 Log，UI 仍显示“催促剩余智囊作答”，会诱导用户为同一问题重置预算继续执行。

#### 修改要求

- 编排结果返回：完成角色、失败角色、API Calls Used、是否因预算停止。
- UI 明确提示“本问题按安全预算执行前 6 位角色”。
- 达到角色上限后，不再把其他 14 位显示成普通“未作答”。
- 如保留手动扩展，必须明确显示将增加预算/调用量，并作为独立产品行为，不得静默重置。

---

## 4. 清理项

1. 主路径完成迁移后，删除或限制旧的：
   - `createInteractionWithFallback()`
   - `generateContentWithFallback()`
   - `callBrokerRouterWithFallback()`
   - `embedContentWithFallback()`
2. `disableBan` 参数当前已无实际作用，不得继续保留误导性 API。
3. 删除无调用方的 `assignRandomGroups()`；同步删除依赖它的旧测试。
4. 删除 `RoundtableViewModel` 中已无用途的旧辅助函数，例如旧 Key fallback/Base64 helper（确认无调用后再删）。
5. 搜索并清理：

```powershell
git grep -n -I 'assignRandomGroups\|createInteractionWithFallback\|generateContentWithFallback\|callBrokerRouterWithFallback\|embedContentWithFallback\|反检测\|防屏蔽\|组间并发\|组内串行'
```

---

## 5. 提交前必须执行

```powershell
./gradlew.bat clean
./gradlew.bat testDebugUnitTest
./gradlew.bat compileDebugKotlin
./gradlew.bat assembleDebug
./tools/check-secrets.ps1
```

不得只报告 `testDebugUnitTest`。

至少进行以下设备/模拟器烟雾测试：

1. 激活 3 个角色，确认 A → B → C 严格顺序。
2. 展开 B 的遥测/请求预览，确认包含 A 的回答。
3. 进入第二轮，确认 A 能看到 B、C 第一轮回答。
4. 激活超过 6 个角色，确认 UI 明确说明预算限制，且同一问题不能静默突破 6。
5. 模拟一个角色失败，确认后续角色继续、Pending 被清理。
6. 连续点击发送/下一位，不产生重复回答或预算重置。

---

## 6. 修复提交交付报告必须提供

1. 新提交 SHA 和父提交 SHA。
2. 本整改单 B01～B04、M01～M05 的逐项处理结果。
3. 每项对应修改文件与核心符号。
4. 每项对应测试名称。
5. 五条 Gradle/Secret 命令的原始摘要输出。
6. 设备烟雾测试的实际观察结果。
7. 仍未完成或延期内容，禁止用“基本完成”掩盖。
8. `git diff --check` 结果。
9. `git status --short` 结果，确认工作树干净。

---

## 7. 验收门槛

只有同时满足以下条件才能进入 PR03：

- [ ] B01～B04 全部完成。
- [ ] 同一用户问题的角色数和请求数不能通过重复入口静默重置。
- [ ] 第二轮角色能看到上一轮所有已完成角色发言。
- [ ] 只有链失效错误允许无链兜底。
- [ ] 测试直接覆盖生产 Orchestrator，而不是测试内复制实现。
- [ ] 标题、Embedding、Broker、搜索、主回答、续写和重试共享预算。
- [ ] 用户可见预算停止原因。
- [ ] 完成 compile、test、assemble、secret scan 和设备烟雾测试。

当前提交在上述条件满足前，状态保持 **Changes Required**。
