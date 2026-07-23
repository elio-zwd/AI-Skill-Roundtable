# PR05-C：支持失败角色原位重试 实施说明书

## 一、概述

本文档定义 PR05-C 的实现方案，支持在一轮圆桌脑暴中部分角色失败或超时后，用户可以在当前会话中仅重试这些未完成的角色，而不影响已成功回复和会话结构。

## 二、架构设计

### 1. 内存状态模型 (`RetryableRoundtableState`)

在 ViewModel 层引入 `_retryableRoundtableState`：

```kotlin
data class RetryableRoundtableState(
    val sessionId: Long,
    val characterIds: List<String>
)

internal fun buildRetryableCharacterIds(
    failedCharacters: List<String>,
    timedOutCharacters: List<String>
): List<String> {
    val combined = ArrayList<String>(failedCharacters.size + timedOutCharacters.size)
    for (id in failedCharacters) {
        if (id !in combined) combined.add(id)
    }
    for (id in timedOutCharacters) {
        if (id !in combined) combined.add(id)
    }
    return combined
}
```

- 不修改 Room Schema 或数据库版本。
- 绑定 `sessionId`，在会话隔离与删除时正确维护。
- 应用重启后自然清除。

### 2. 编排器目标角色过滤 (`RoundtableOrchestrator`)

`runRoundtableSequence` 增加可选参数 `targetCharacterIds: List<String>? = null`：

```kotlin
suspend fun runRoundtableSequence(
    sessionId: Long,
    questionRunId: Long,
    isSemanticRoutingEnabled: Boolean,
    targetCharacterIds: List<String>? = null
): OrchestrationResult
```

- 当 `targetCharacterIds` 为 `null` 时，按正常模式选择/路由角色。
- 当 `targetCharacterIds` 非空时，仅选取 `targetCharacterIds` 中存在且已启用的角色（保持 `targetCharacterIds` 首次出现的绝对顺序）。
- 复用现有 `resolveCurrentRound`、`pendingCharacters` 过滤、SSE 流式、Pending 消息更新、超时控制与 `CancellationException` 清理机制。
- 不重新插入用户消息，不修改或覆盖已完成角色的回复。

### 3. ViewModel 重试入口 (`RoundtableViewModel`)

- 新增 `retryFailedCharacters()` 与 `dismissRetryableState()`。
- 复用 `launchRoundtableJob`、`activeRoundtableJob` 与 `cancelRoundtable()` 机制。
- 重试中用户主动取消时，精确保留尚未完成的角色集合，允许后续再次点击重试。
- 只有在新问题通过前置校验（非空、有 Key、有角色等）正式准备启动时，才清除旧重试状态。

### 4. UI 界面重试条 (`MainActivity.kt`)

在圆桌聊天页面输入框上方放置紧凑操作条，仅在：
1. `retryableState != null`
2. `retryableState.sessionId == currentSession.id`
3. `characterIds` 非空
4. 当前没有运行中的圆桌任务 (`!isRoundtableRunning`)

成立时显示。包含重试按钮 (`retry_failed_characters_button`) 与忽略按钮 (`dismiss_failed_characters_button`)。

## 三、测试计划

1. `buildRetryableCharacterIds` 顺序与去重合并测试。
2. 目标角色过滤与只执行失败角色测试。
3. 重试时不重复插入用户消息与已成功消息保留测试。
4. 全部/部分重试成功与超时保留测试。
5. 取消重试保留可重试能力测试。
6. 新问题通过校验后清除旧重试状态测试。
7. 会话隔离与删除测试。
8. 正常圆桌无 target 时的兼容回归测试。
