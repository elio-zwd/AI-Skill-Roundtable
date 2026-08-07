# PR09-13B 数据库与运行时生命周期前置实施计划

> **执行要求：** 使用仓库内 `Superpowers:executing-plans`、`Superpowers:test-driven-development`、`Superpowers:systematic-debugging` 与 `Superpowers:verification-before-completion` 的等价人工流程。
>
> 本对话只负责本前置 PR，不实现 PR09-13B 的密码学、导出、SAF、Snapshot 文件、导入、恢复或设置 UI。

## 一、目标与基线

**目标：** 建立可验证的 Room 单例关闭、清空和重开能力，以及 Runtime 世代、消费者租约、Compose 旧世代清理和 Worker 静默屏障，使后续 PR09-13B 能在数据库关闭期间读取 Snapshot 来源，而不会继续暴露旧 DAO 或未验证 Runtime。

```text
Repository：elio-zwd/AI-Skill-Roundtable
Base：main@3a6668b100945a250fdb1ef3ac760144d58bb25b
Branch：refactor/pr-09-13b-runtime-lifecycle-prep
Room：v12
Upstream：PR #53
```

## 二、全局约束

- Room 保持 v12，不新增 Entity、DAO、Migration、Schema 或 destructive migration。
- 不修改 `AndroidManifest.xml`、`backup_rules.xml`、`data_extraction_rules.xml`。
- 不实现 `.jybak`、`.jysnap`、KDF、AEAD、Canonical CBOR、Record Stream、SAF 或 Snapshot Index。
- 不自动停止 Run、Purge、Audio 或 WorkManager；PR09-13B 仍必须先执行冻结预检和 `BackupOperationGate`。
- 维护操作不得由会随旧世代销毁的页面 `viewModelScope` 驱动。
- 闭库后异常或取消必须先尝试重开，再传播原失败。
- 新 Runtime 只有完成 `afterReopen` 健康验证后才能发布为 `Ready`。
- `afterReopen` 失败时，候选数据库必须关闭并清空，状态进入 `Unavailable(AFTER_REOPEN)`。
- 显式重试必须重新创建候选 Runtime，并通过 `SELECT 1` 与 `PRAGMA foreign_key_check` 后才能发布。
- 未实际执行的测试、设备验证和 CI 不得描述为通过。

## 三、阻断根因

1. `RoundtableDatabase.INSTANCE` 原本会在 `close()` 后继续返回已关闭实例。
2. `JianyuAppRuntimeProvider` 原本永久缓存 Repository、Execution、Collaboration、Audio 和 Lifecycle 对象图。
3. Repository、Audio、Purge 与旧 ViewModel 持有构造时的固定数据库或 DAO 引用。
4. Compose 根节点原本不会在数据库重开后清理旧 ViewModelStore 和 NavController。
5. Purge、正式 Audio 与旧 Transcode Worker 原本没有完整 Runtime 使用租约。

## 四、生产架构

### 4.1 Room 单例

`RoundtableDatabase` 增加：

```kotlin
internal fun closeAndClear(expected: RoundtableDatabase)
```

要求：

- 只关闭当前预期实例；
- 在同一 companion 临界区先摘除 `INSTANCE` 再关闭；
- `getDatabase()` 不返回 `isExplicitlyClosed=true` 的实例；
- 数据库名、Migration 链和 Callback 保持不变。

### 4.2 Runtime 状态与世代

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

要求：

- 世代单调递增；
- `Maintenance` 后停止发放新租约；
- 不提供强制清零、强制解锁或删除他人租约接口；
- 旧世代租约释放不影响新世代；
- 状态不包含正文、密码、Key、路径或原始异常消息。

### 4.3 普通 Runtime 租约

```kotlin
suspend fun <T> JianyuAppRuntimeProvider.withRuntime(
    context: Context,
    block: suspend (JianyuAppRuntime) -> T,
): T
```

- `Ready` 时获取当前世代租约；
- `Maintenance` 时等待下一状态；
- `Unavailable` 时受控失败；
- 正常、异常和取消均释放租约。

### 4.4 闭库维护

```kotlin
internal suspend fun <T> JianyuAppRuntimeProvider.withDatabaseClosed(
    context: Context,
    beforeClose: suspend (RoundtableDatabase) -> Unit,
    whileClosed: suspend (databaseFile: File) -> T,
    afterReopen: suspend (JianyuAppRuntime) -> Unit,
): DatabaseMaintenanceOutcome<T>
```

固定顺序：

```text
停止新租约
→ 发布 Maintenance
→ 等待旧租约归零
→ beforeClose
→ closeAndClear
→ whileClosed
→ NonCancellable 创建候选 Runtime
→ afterReopen
→ 发布新 Ready 世代
```

失败语义：

- 租约等待或 `beforeClose` 取消：恢复原 `Ready`，不关闭数据库；
- `beforeClose` 普通失败：恢复原 `Ready`；
- `whileClosed` 失败或取消：先重开并验证；验证成功后发布新世代，再返回原失败或传播取消；
- `REOPEN` 失败：进入 `Unavailable(REOPEN)`；
- `afterReopen` 失败：关闭候选数据库，进入 `Unavailable(AFTER_REOPEN)`；
- 任何情况下都不得重新发布已关闭旧 Runtime。

### 4.5 Compose 世代宿主

- App 根节点观察 Runtime 状态；
- 每个 `Ready` 世代创建独立 `ViewModelStoreOwner` 和 NavController；
- 进入维护时移除正常页面树；
- `onDispose` 先 `ViewModelStore.clear()`，再释放根 Runtime 租约；
- 重开后重新构造 `RoundtableViewModel` 及其 DAO Repository；
- `Unavailable` 只显示稳定说明与重试，不显示异常、路径或正文。

### 4.6 Worker 租约

以下 Worker 的完整正式操作必须位于 `withRuntime` 内：

- `IssuePurgeWorker`；
- `AudioAssetGenerationWorker`；
- `AudioTranscodeWorker`。

不得改变其输入键、唯一任务、业务状态机和既有稳定错误语义。

### 4.7 自动化标签

维护、不可用和重试标签必须进入：

```text
JianyuAutomationTags.App
JianyuAutomationTags.frozenStaticTags
```

禁止建立第二套标签清单或把异常、路径、密码或用户内容写入标签。

## 五、文件范围

### 新增

```text
app/src/main/java/com/elio/jianyu/runtime/JianyuRuntimeLifecycle.kt
app/src/test/java/com/elio/jianyu/runtime/RuntimeLeaseRegistryTest.kt
app/src/androidTest/java/com/elio/jianyu/JianyuRuntimeLifecycleDatabaseTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/RuntimeMaintenanceHostTest.kt
docs/planning/pr-09-13b-runtime-lifecycle-interface-handoff.md
docs/testing/pr-09-13b-runtime-lifecycle-local-readonly-acceptance-prompt.md
```

### 最小修改

```text
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt
app/src/main/java/com/elio/jianyu/ui/App.kt
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
app/src/main/java/com/elio/jianyu/audio/AudioTranscodeWorker.kt
app/src/main/java/com/elio/jianyu/audio/work/AudioAssetGenerationWorker.kt
app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeWorker.kt
app/src/test/java/com/elio/jianyu/ui/automation/JianyuAutomationTagsTest.kt
```

### 删除

```text
app/src/main/java/com/elio/jianyu/ui/screens/issues/IssuesRouteRuntimeBridge.kt
```

删除原因：避免页面在 Runtime 世代切换期间再次查询全局 Provider，改由 App 组合层显式传入 `lifecycleRuntime`。

## 六、TDD 顺序与完成条件

### Task A：租约登记表

测试：并发获取、停止新租约、等待释放、幂等 close、跨世代隔离、取消不清除他人租约。

完成条件：纯 JVM 行为可重复，无负计数、强制释放或丢失唤醒。

### Task B：Room 重开

测试：预期实例校验、旧实例关闭、重新创建不同实例、旧 Repository 失败、新 Repository 保留数据并继续写入。

完成条件：Room v12 与 Schema 无变化。

### Task C：维护管线

测试：

- 租约释放前不进入 `beforeClose`；
- `beforeClose` 失败恢复同一世代；
- `whileClosed` 失败仍重开；
- 闭库期间取消仍先重开；
- `afterReopen` 失败关闭候选并进入 Unavailable；
- 显式重试生成不同候选并完成最小查询与外键检查；
- 维护期间同步 `get()` 不返回旧 Runtime。

### Task D：Compose 与标签

测试：Maintenance 不显示正常 App 内容；Unavailable 显示中央冻结重试标签；中央标签唯一、ASCII、lower_snake_case。

### Task E：Worker 接入

静态和设备验证三个 Worker 的完整业务操作持有租约，完成、失败和取消均释放。

### Task F：回归

- JVM 全量；
- Compile、Lint、Debug、Release、AndroidTest；
- API 26/28 新增 Instrumentation；
- 全量普通 Instrumentation；
- External Process Recovery 两阶段；
- Room v1→v12；
- Issue、Run、Message、Draft、Artifact、Audio、Purge 回归。

## 七、验证命令

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

专项：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.elio.jianyu.runtime.RuntimeLeaseRegistryTest"
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.JianyuRuntimeLifecycleDatabaseTest
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.RuntimeMaintenanceHostTest
```

## 八、风险与控制

- **UI 协程残留：** PR09-13B 的业务写仍必须使用 `BackupOperationGate` 读锁；Runtime 根租约不替代正式写门禁。
- **锁反转：** 固定顺序为 `BackupOperationGate 写锁 → Maintenance Mutex → 租约归零 → checkpoint/close`。
- **验证失败：** 未通过 `afterReopen` 的候选不发布，关闭后进入 Unavailable。
- **重试泄漏：** `createValidatedRuntime` 在健康检查失败时关闭并清空候选数据库。
- **导航状态重置：** 世代切换重建 NavController，属于安全优先行为；备份密码不得随导航恢复。
- **设备差异：** API 26/28 与 External Process Recovery 缺少真实证据时不得给出完整 PASS。

## 九、回滚

- 本 PR 不创建备份文件、不修改 Room Schema、不替换数据库。
- 可以整体回滚 Runtime 状态、租约、Compose 宿主和 Worker 接入。
- 不得删除用户数据库、音频、API Key 或其他现有数据。
- 不得通过反射、第二套 Room Builder 或 destructive migration 绕过生命周期门禁。

## 十、PR09-13B 门禁

只有本前置 PR 完成：

- 最终 Head GitHub CI；
- API 26/28 真实设备关闭/重开验证；
- 全量 Instrumentation；
- External Process Recovery；
- 本地严格只读验收；
- 用户授权并实际合并；

之后，才能从最新 `main` 创建：

```text
security/pr-09-13b-encrypted-export-snapshot
```

PR09-13B 必须复用本计划冻结的生命周期接口，不得再次引入第二套 Room 生命周期或绕过 Runtime 租约。