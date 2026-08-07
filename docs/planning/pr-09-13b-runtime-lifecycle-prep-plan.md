# PR09-13B 数据库与运行时生命周期前置实施计划

> **执行要求：** 使用仓库内 `Superpowers:executing-plans` 与 `Superpowers:test-driven-development` 等价流程逐项实施；本对话只负责本前置 PR，不实现 PR09-13B 的密码学、导出、SAF、Snapshot 文件或设置 UI。

**目标：** 建立可验证的 Room 单例关闭/清空/重开能力、运行时世代切换、消费者静默期和失败后恢复能力，使后续 PR09-13B 能在数据库关闭期间生成 Snapshot，而不会让 Repository、DAO、ViewModel 或 Worker 永久持有失效实例。

**架构：** `JianyuAppRuntimeProvider` 成为唯一运行时生命周期所有者，公开可观察的 `Ready / Maintenance / Unavailable` 状态，并以租约计数阻止维护期间仍有正式消费者使用旧世代。数据库维护流程先切换到 `Maintenance`、等待全部租约释放，再关闭并清空 Room 单例；闭库操作结束后在不可取消收尾区重新创建整套 Runtime，执行最小重开验证并发布新世代。Compose 根节点按世代创建独立 `ViewModelStore` 和 `NavController`，离开旧世代时主动清理旧 ViewModel；WorkManager 入口统一通过运行时租约执行。

**技术栈：** Kotlin、Coroutines、StateFlow、Mutex、Room v12、Jetpack Compose、WorkManager、Android Instrumentation。

## 全局约束

- Base：`main@3a6668b100945a250fdb1ef3ac760144d58bb25b`。
- 分支：`refactor/pr-09-13b-runtime-lifecycle-prep`。
- Room 必须保持 v12；不得新增 Entity、DAO、Migration 或 Schema。
- 不修改 `AndroidManifest.xml`、`backup_rules.xml`、`data_extraction_rules.xml`。
- 不实现 `.jybak`、`.jysnap`、KDF、AEAD、SAF、Snapshot Index、导入或恢复。
- 不在维护流程中自动停止 Run、Purge 或 Audio；PR09-13B 仍必须先完成冻结预检和 `BackupOperationGate`。
- 维护调用不得从会随旧世代销毁的页面 `viewModelScope` 驱动；PR09-13B 应使用应用级操作作用域。
- 任何闭库后异常都必须尝试重开；只有新 Runtime 完成最小读写/外键验证后才能发布为 `Ready`。
- 未实际执行的命令、设备测试和 CI 不得描述为通过。

---

## 当前调用链与阻断根因

1. `RoundtableDatabase.INSTANCE` 是静态单例；`close()` 只设置 `isExplicitlyClosed=true`，旧 `getDatabase()` 会继续返回关闭实例。
2. `JianyuAppRuntimeProvider.runtime` 永久缓存 Repository、Execution、Collaboration、Audio 和 Lifecycle 对象图。
3. `RoomJianyuRepository`、`JianyuRepositoryTransactions`、Audio Repository、Purge 服务都持有构造时传入的固定数据库引用。
4. `RoundtableViewModel` 持有固定数据库与 DAO Repository。
5. `MainAppContent` 使用 `remember` 固定旧 Runtime；Navigation BackStack 与 ViewModelStore 不随数据库重开自动失效。
6. `IssuePurgeWorker`、`AudioAssetGenerationWorker` 直接获取全局 Runtime；`AudioTranscodeWorker` 直接获取 Room 单例。

## 文件清单

### 新增

- `app/src/main/java/com/elio/jianyu/runtime/JianyuRuntimeLifecycle.kt`
  - Runtime 状态、租约、租约登记表、维护结果和稳定阶段码。
- `app/src/test/java/com/elio/jianyu/runtime/RuntimeLeaseRegistryTest.kt`
  - 纯 JVM 并发、世代和释放测试。
- `app/src/androidTest/java/com/elio/jianyu/JianyuRuntimeLifecycleDatabaseTest.kt`
  - 真实 Room 关闭、重开、失败恢复、旧引用失效与新引用可读写测试。
- `app/src/androidTest/java/com/elio/jianyu/ui/RuntimeMaintenanceHostTest.kt`
  - Compose 维护/不可用状态语义测试。
- `docs/planning/pr-09-13b-runtime-lifecycle-interface-handoff.md`
  - 后续 PR09-13B 可调用接口和禁止边界。
- `docs/testing/pr-09-13b-runtime-lifecycle-local-readonly-acceptance-prompt.md`
  - 本地严格只读验收流程。

### 修改

- `app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt`
  - 增加预期实例校验的 `closeAndClear`；关闭实例不再作为新调用者的有效单例。
- `app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt`
  - Runtime 暴露内部数据库句柄；Provider 改为状态化世代所有者；增加 `withRuntime` 与 `withDatabaseClosed`。
- `app/src/main/java/com/elio/jianyu/ui/App.kt`
  - 根节点观察 Runtime 状态；按世代创建和清理 ViewModelStore/NavController；维护期间移除数据库消费者。
- `app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt`
  - 增加不含用户内容的维护、不可用和重试标签。
- `app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesRouteRuntimeBridge.kt`
  - 删除全局 Runtime 再查询桥接，改由 App 组合层显式传入 Lifecycle Runtime；若无调用方则删除文件。
- `app/src/main/java/com/elio/jianyu/audio/AudioTranscodeWorker.kt`
  - 全流程持有 Runtime 租约，不在闭库期间自行创建 Room。
- `app/src/main/java/com/elio/jianyu/audio/work/AudioAssetGenerationWorker.kt`
  - Worker 通过 `withRuntime` 持有租约。
- `app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeWorker.kt`
  - Worker 通过 `withRuntime` 持有租约。

## 冻结接口草案

```kotlin
sealed interface JianyuRuntimeState {
    data object Uninitialized : JianyuRuntimeState
    data class Ready(
        val generation: Long,
        val runtime: JianyuAppRuntime,
    ) : JianyuRuntimeState
    data class Maintenance(val generation: Long) : JianyuRuntimeState
    data class Unavailable(
        val generation: Long,
        val stage: DatabaseMaintenanceStage,
    ) : JianyuRuntimeState
}
```

```kotlin
suspend fun <T> JianyuAppRuntimeProvider.withRuntime(
    context: Context,
    block: suspend (JianyuAppRuntime) -> T,
): T
```

```kotlin
internal suspend fun <T> JianyuAppRuntimeProvider.withDatabaseClosed(
    context: Context,
    beforeClose: suspend (RoundtableDatabase) -> Unit,
    whileClosed: suspend (databaseFile: File) -> T,
    afterReopen: suspend (JianyuAppRuntime) -> Unit,
): DatabaseMaintenanceOutcome<T>
```

约束：

- `beforeClose` 在消费者租约归零且旧数据库仍打开时执行，供 PR09-13B 做完整性检查和 `wal_checkpoint(TRUNCATE)`。
- `whileClosed` 是唯一允许访问数据库主文件的阶段，不允许调用 Repository/DAO。
- `afterReopen` 在新 Runtime 已创建但尚未发布给普通消费者时执行，供 PR09-13B 做最小查询和 `PRAGMA foreign_key_check`。
- 关闭后无论取消或异常都在 `NonCancellable` 收尾区尝试重开。
- 重开失败时状态为 `Unavailable`，不能退回旧 Runtime 或伪装成功。

## TDD 实施顺序

### Task 1：纯 JVM 租约登记表

**测试先行：**

- 同一世代可并发获取多个普通租约。
- 切换到维护状态后拒绝新租约。
- 维护等待已有租约全部释放。
- 重复关闭租约不会重复扣减。
- 旧世代租约不能影响新世代计数。
- 取消等待不会强制清除他人租约。

**实现：**

- 使用公平语义的同步登记表和 `StateFlow` 变更信号。
- 租约只包含世代、Runtime 引用和幂等释放动作。
- 禁止公开强制解锁、计数清零或删除他人租约的接口。

**完成条件：** JVM 测试能证明维护屏障和世代隔离；本对话无本地终端时标记“测试代码已编写、尚未实际执行”。

### Task 2：Room 单例可控关闭与重建

**测试先行：**

- `closeAndClear(expected)` 只关闭当前预期实例。
- 清空后 `getDatabase()` 创建不同的新实例。
- 旧 Repository 返回存储失败，不会复活。
- 新 Repository 可读取关闭前提交的数据。
- 新 Repository 可继续创建 Issue。
- Room 仍为 v12，Schema 无差异。

**实现：**

- 在同一 companion 锁内先摘除 `INSTANCE`，再关闭旧实例，阻止并发调用者拿到半关闭实例。
- `getDatabase()` 遇到已显式关闭实例时必须进入同步重建路径。
- 不修改数据库文件名、Migration 或 Callback 语义。

### Task 3：状态化 App Runtime 与闭库维护管线

**测试先行：**

- 初次读取发布 `Ready(generation=1)`。
- `withRuntime` 在执行期间持有租约并在取消/异常后释放。
- 维护先发布 `Maintenance`，等待租约归零后才调用 `beforeClose`。
- `whileClosed` 成功后创建新 Runtime 并发布新世代。
- `whileClosed` 抛异常或取消后仍重开并发布新世代，结果保留原失败。
- `afterReopen` 失败时数据库仍保持可用，但维护结果失败。
- 重开本身失败时发布 `Unavailable`，不能返回旧 Runtime。
- 维护期间 `get()` 不返回旧 Runtime。

**实现：**

- `Mutex` 串行化数据库维护。
- 运行时状态和租约变更使用单一内部锁保证无竞态。
- 新 Runtime 创建期间不自动恢复 Purge；普通冷启动保持原恢复行为。
- `DatabaseMaintenanceOutcome` 区分操作阶段失败与重开失败。

### Task 4：Compose 根宿主按世代清理旧消费者

**测试先行：**

- `Ready` 显示正常 App 内容。
- `Maintenance` 不创建 NavHost/页面 ViewModel，显示稳定维护标签。
- Runtime 世代变化后旧 `ViewModelStore` 被 `clear()`，新世代使用新 Store。
- `Unavailable` 显示稳定错误与重试按钮，不包含异常、路径或用户正文。

**实现：**

- `MainAppContent` 仅负责 Runtime 状态宿主和组合层切换。
- 每个 Ready 世代创建独立 `ViewModelStoreOwner`；`DisposableEffect` 中先清理 Store，再释放 UI Runtime 租约。
- 现有 Scaffold/NavHost 保持在 Ready 内容函数中。
- App 组合层显式传入 `lifecycleRuntime`，移除 `IssuesRouteRuntimeBridge` 的第二次全局查询。

### Task 5：Worker 运行时租约接入

**测试先行：**

- Purge Worker、正式 Audio Worker 和旧 Transcode Worker 执行期间都持有租约。
- Worker 完成、失败和取消均释放租约。
- 维护期间新 Worker 等待新世代，不创建第二个 Room 实例。
- Worker 输入和错误输出不新增正文、密码或路径。

**实现：**

- 使用 `JianyuAppRuntimeProvider.withRuntime` 包裹完整正式工作。
- `AudioTranscodeWorker` 从租约 Runtime 获取数据库，不再直接调用 `RoundtableDatabase.getDatabase()`。
- 不改变 WorkManager 唯一任务名、输入键、重试次数或业务状态机。

### Task 6：真实数据库维护验收

**Instrumentation 场景：**

1. 创建 Issue 和 Stage。
2. 获取 UI/Worker 模拟租约并启动维护。
3. 证明租约释放前未进入闭库阶段。
4. 释放租约，执行关闭并在闭库回调中确认旧数据库不可用。
5. 重开后确认新 Runtime/数据库实例不同。
6. 新 Repository 恢复原 Issue，并创建第二个 Issue。
7. 执行最小查询和 `PRAGMA foreign_key_check`。
8. 在闭库回调抛异常、取消和 `afterReopen` 失败三种情况下重复验证 App 可继续读写。
9. 验证旧 Repository 继续失败，不能跨世代写入。

### Task 7：文档、静态核对与 Draft PR

- 提交接口交接和本地严格只读验收 Prompt。
- 检查生产代码不存在第二个数据库 Builder、反射清空单例、强制租约清零或自动任务停止。
- 检查 Room v12、Schema、Manifest 和系统备份 XML 无变化。
- 创建 Draft PR：`refactor: 建立备份所需的可重启运行时生命周期`。
- 严格区分静态检查、GitHub CI、本地 JVM、设备测试和未验证项。

## 验证命令

本地验收至少执行：

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest

git diff --exit-code -- app/schemas
git diff --exit-code main...HEAD -- app/src/main/AndroidManifest.xml app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml
git status --short
```

专项建议：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.elio.jianyu.runtime.RuntimeLeaseRegistryTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.JianyuRuntimeLifecycleDatabaseTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.RuntimeMaintenanceHostTest
```

External Process Recovery 仍按 PR #52 的直接 ADB 两阶段流程执行。

## 风险与控制

- **维护调用与 UI ViewModel 同源取消：** PR09-13B 必须使用应用级作用域；闭库后的重开位于不可取消收尾区。
- **旧 ViewModel 残留：** Ready 世代使用独立 ViewModelStore，并在维护状态切换时主动清理。
- **Worker 竞态：** 正式 Worker 全流程持有租约；PR09-13B 仍需在进入维护前按冻结合同拒绝活动 Worker。
- **锁顺序：** 后续固定为 `BackupOperationGate 写锁 → Runtime Maintenance Mutex → Runtime 租约归零 → Room checkpoint/close`；普通业务不得反向获取写锁。
- **重开失败：** 发布 `Unavailable` 并允许显式重试，不回退旧实例、不清空数据库、不使用 destructive migration。
- **导航状态丢失：** 世代切换会重建 NavController，属于安全优先的已知行为；PR09-13B 可在不持久化密码的前提下重新打开备份页。

## 回滚

- 本前置 PR 不写新格式、不创建用户备份文件、不修改数据库内容或 Schema。
- 可整体回滚 Runtime 状态宿主、租约和 Worker 接入。
- 不得通过回滚删除用户数据库、音频或 API Key。
- 若上线后发现维护不可用，可在 PR09-13B 隐藏备份入口，但不得用反射或直写文件绕过生命周期门禁。

## PR09-13B 后续门禁

只有本前置 PR 完成：

- GitHub CI；
- 本地严格只读验收；
- Room 关闭/重开真实设备测试；
- External Process Recovery 回归；
- 用户授权并实际合并；

之后，才能从最新 `main` 创建：

```text
security/pr-09-13b-encrypted-export-snapshot
```

PR09-13B 必须复用本计划冻结的维护接口，不得再次引入第二套 Room 生命周期或绕过 Runtime 租约。