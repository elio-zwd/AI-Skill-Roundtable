# PR09-13B Runtime Lifecycle 接口交接

> 本文件是 PR09-13B 使用数据库关闭窗口和 Runtime 重建能力的施工合同。
>
> 本前置 PR 只提供安全生命周期基础设施，不提供备份格式、加密、SAF、Snapshot 文件、导入或恢复执行。

## 一、上游基线与启动门禁

```text
Prerequisite Base：main@3a6668b100945a250fdb1ef3ac760144d58bb25b
Prerequisite Branch：refactor/pr-09-13b-runtime-lifecycle-prep
Room：v12
Upstream Security Design：PR #53
```

PR09-13B 必须等待本前置 PR 完成本地严格只读验收、用户授权并实际合并后，从最新 `main` 创建：

```text
security/pr-09-13b-encrypted-export-snapshot
```

## 二、唯一生命周期所有者

`JianyuAppRuntimeProvider` 是 App 内唯一允许执行以下操作的组件：

- 创建 `RoundtableDatabase`；
- 创建完整 `JianyuAppRuntime` 对象图；
- 发布 Runtime 世代；
- 阻止新 Runtime 消费者；
- 等待现有消费者释放；
- 关闭并清空当前 Room 单例；
- 重建数据库和完整 Runtime；
- 发布维护、就绪或不可用状态。

PR09-13B 禁止：

- 再创建第二个 Runtime Provider；
- 在 backup 包中直接调用 `Room.databaseBuilder`；
- 使用反射读写 `RoundtableDatabase.INSTANCE`；
- 强制清空租约计数；
- 将已关闭或未验证 Runtime 发布为 `Ready`；
- 在 `whileClosed` 中调用 Repository、DAO、Audio 或 Lifecycle 服务。

## 三、Runtime 状态

```kotlin
sealed interface JianyuRuntimeState {
    data object Uninitialized : JianyuRuntimeState

    data class Ready(
        val generation: Long,
        val runtime: JianyuAppRuntime,
    ) : JianyuRuntimeState

    data class Maintenance(
        val generation: Long,
    ) : JianyuRuntimeState

    data class Unavailable(
        val generation: Long,
        val stage: DatabaseMaintenanceStage,
    ) : JianyuRuntimeState
}
```

语义：

- `Uninitialized`：尚未创建 Runtime；
- `Ready`：允许获取普通消费者租约；
- `Maintenance`：停止发放新租约，等待或正在执行数据库维护；
- `Unavailable`：数据库重新打开或重开后验证失败，不允许回退到旧 Runtime。

状态不得包含：用户正文、标题、密码、API Key、文件路径、异常消息、Snapshot ID 或备份目标 URI。

## 四、普通 Runtime 使用接口

Worker、Service 和非 Compose 调用方使用：

```kotlin
suspend fun <T> JianyuAppRuntimeProvider.withRuntime(
    context: Context,
    block: suspend (JianyuAppRuntime) -> T,
): T
```

行为：

- `Ready` 时获取当前世代租约；
- `Maintenance` 时等待维护结束；
- `Unavailable` 时抛出稳定的 `JianyuRuntimeUnavailableException`；
- block 正常、失败或取消后均释放租约；
- 租约不得保存到字段、Bundle、Room、WorkManager Data 或文件。

Compose 根宿主使用：

```kotlin
fun JianyuAppRuntimeProvider.tryAcquireReady(
    expectedGeneration: Long,
): JianyuRuntimeLease?
```

PR09-13B 页面不得绕过根宿主长期缓存独立 Runtime。

## 五、闭库维护接口

PR09-13B Snapshot 创建必须调用：

```kotlin
internal suspend fun <T> JianyuAppRuntimeProvider.withDatabaseClosed(
    context: Context,
    beforeClose: suspend (RoundtableDatabase) -> Unit,
    whileClosed: suspend (databaseFile: File) -> T,
    afterReopen: suspend (JianyuAppRuntime) -> Unit,
): DatabaseMaintenanceOutcome<T>
```

### 5.1 `beforeClose`

运行条件：

- Runtime 已进入 `Maintenance`；
- 不再发放新租约；
- 所有既有 Runtime 租约已经释放；
- 当前数据库仍打开。

PR09-13B 应在这里完成：

- 最终活动工作复核；
- 数据库完整性预检；
- `PRAGMA wal_checkpoint(TRUNCATE)`；
- WAL checkpoint 结果检查。

不得在此阶段发起模型网络调用、自动停止 Run、自动取消 Purge/Audio、创建明文数据库副本或开始写最终 `.jysnap`。

### 5.2 `whileClosed`

运行条件：

- 原 `RoundtableDatabase` 已关闭；
- 静态 Room 单例已清空；
- 参数是受控数据库主文件路径；
- Repository、DAO、Audio 和 Lifecycle 对象均不可使用。

PR09-13B 应在这里完成：

- 流式读取数据库主文件；
- 加密写入 Snapshot 临时文件；
- 读取受控 AVAILABLE 音频；
- 计算并核对数据库与音频 Hash；
- 完成密文临时文件。

不得修改数据库主文件、复制 WAL/SHM、用第二个 Room 实例打开数据库、调用旧 Runtime、发布 Snapshot Index，或把绝对路径写入 Manifest、日志或 UI。

### 5.3 `afterReopen`

运行条件：

- 新 `RoundtableDatabase` 和完整 Runtime 已创建；
- 新 Runtime 尚未发布给普通消费者；
- 原 Runtime 保持关闭且不会复活。

PR09-13B 必须在这里完成：

- 最小 Repository 查询；
- 数据库可写性检查；
- `PRAGMA foreign_key_check`；
- 其他必要的新 Runtime 健康验证。

只有 `afterReopen` 成功后，Provider 才发布新的 `Ready` 世代。

若 `afterReopen` 失败：

1. 候选数据库必须关闭并从单例清空；
2. 状态进入 `Unavailable(AFTER_REOPEN)`；
3. `Failure.reopened=false`，表示没有通过验证的 Runtime 对外可用；
4. 不得把未验证候选继续交给 UI、Worker 或 Repository；
5. 用户显式重试必须创建新的候选 Runtime。

## 六、维护结果

```kotlin
sealed interface DatabaseMaintenanceOutcome<out T> {
    data class Success<T>(
        val value: T,
        val generation: Long,
    ) : DatabaseMaintenanceOutcome<T>

    data class Failure(
        val stage: DatabaseMaintenanceStage,
        val cause: Throwable,
        val reopened: Boolean,
        val generation: Long,
    ) : DatabaseMaintenanceOutcome<Nothing>
}
```

`stage`：

```text
QUIESCE
BEFORE_CLOSE
CLOSE
WHILE_CLOSED
REOPEN
AFTER_REOPEN
```

`reopened` 精确定义：

- `true`：新 Runtime 已完成 `afterReopen` 验证并已安全发布；
- `false`：没有经过验证的 Runtime 对普通消费者可用。

PR09-13B 必须把底层失败映射为冻结稳定错误码，禁止直接显示 `cause.message`。

建议映射：

| Runtime 阶段 | PR09-13B 稳定错误 |
|---|---|
| `BEFORE_CLOSE` / checkpoint | `database_checkpoint_failed` 或 `database_integrity_failed` |
| `CLOSE` | `verification_failed` |
| `WHILE_CLOSED` | 保留实际备份阶段错误，如 `target_write_failed`、`source_changed`、`operation_canceled` |
| `REOPEN` | `verification_failed`，App 进入 `Unavailable` |
| `AFTER_REOPEN` | `database_integrity_failed` 或 `verification_failed`，App 进入 `Unavailable` |

认证、KDF、SAF 等错误继续按 PR09-13A 冻结错误模型映射。

## 七、显式重试

```kotlin
suspend fun JianyuAppRuntimeProvider.retryOpen(context: Context): Boolean
```

重试要求：

1. 只能从 `Unavailable` 状态执行；
2. 必须重新创建完整候选 Runtime；
3. 必须执行 `SELECT 1`；
4. 必须执行 `PRAGMA foreign_key_check`；
5. 验证失败时关闭并清空候选数据库；
6. 验证成功后才发布同一待恢复世代的 `Ready`；
7. 不复用旧 Runtime，不复活先前失败候选；
8. 必须在 IO 协程中调用，不能阻塞主线程。

## 八、取消语义

- 在租约归零前取消：恢复原 `Ready`，不关闭数据库；
- 在 `beforeClose` 中取消：恢复原 `Ready`，不关闭数据库；
- 在数据库关闭后取消：先在 `NonCancellable` 收尾区尝试重开和验证，再传播取消；
- 重开或验证失败：发布对应 `Unavailable`；
- PR09-13B 必须把用户取消映射为 `operation_canceled`；
- 取消不得映射为成功，不得保留可识别为有效的最终 Snapshot。

## 九、锁顺序

PR09-13B 固定锁顺序：

```text
BackupOperationGate 写锁
→ JianyuAppRuntimeProvider Maintenance Mutex
→ 等待 Runtime 租约归零
→ WAL checkpoint
→ Room close
```

禁止：

- 普通业务写在持有 Runtime 租约时反向申请 Backup 写锁；
- 在 `beforeClose` 或 `whileClosed` 内升级锁；
- 持有数据库维护 Mutex 等待模型网络；
- 删除 `operation.lock` 规避竞争；
- 强制释放其他调用方租约。

普通业务写和正式 Worker 在 PR09-13B 中仍必须接入 `BackupOperationGate` 读锁；Runtime 租约不能替代该门禁。

## 十、Compose 与密码边界

Runtime 世代切换会：

- 清理旧 `ViewModelStore`；
- 销毁旧页面 ViewModel；
- 重建 NavController；
- 返回新安全导航根。

因此 PR09-13B：

- 不能从备份页面 `viewModelScope` 驱动整个闭库维护；
- 必须使用应用级操作作用域；
- 密码不得写入 SavedStateHandle、Bundle、Room、SharedPreferences 或 WorkManager Data；
- 页面销毁后密码必须清空；
- 操作完成后可以通过不含密码的稳定结果状态重新打开备份页面；
- 不得自动重新开始已取消或因进程重建中断的备份。

## 十一、Worker 边界

已接入 Runtime 租约的生产 Worker：

- `AudioAssetGenerationWorker`；
- `IssuePurgeWorker`；
- `AudioTranscodeWorker`。

PR09-13B 仍必须在进入维护前证明：

- 没有活动 Audio Worker；
- 没有活动 Purge Worker；
- 没有 Running Run；
- 没有 Pending Message；
- 没有相关 WorkManager 工作。

本接口不会自动停止、取消或重试这些工作。

## 十二、Room 与数据边界

本前置 PR 冻结：

- 数据库名仍为 `roundtable_database`；
- Room 仍为 v12；
- Migration 链保持 v1→v12；
- 无 v13 Schema；
- 无 destructive migration；
- 无数据库替换；
- 无 Candidate Import Database；
- 无导入恢复。

PR09-13B 只可在 `whileClosed` 读取主文件，不能替换、修改或删除当前数据库。

## 十三、PR09-13B 测试门禁

至少增加真实设备测试证明：

1. Snapshot 前已提交数据在重开后仍可读取；
2. 旧 Repository 在关闭后受控失败；
3. 新 Repository 可以继续创建 Issue、Run、Message、Draft、Artifact 和 Audio；
4. Root UI 世代切换后旧 ViewModel 被清理；
5. Worker 在维护前持有租约，维护不能越过活动 Worker；
6. 取消和异常后数据库重新打开；
7. `afterReopen` 执行最小查询和 `foreign_key_check`；
8. `afterReopen` 失败候选被关闭，状态为 Unavailable；
9. 重试创建不同候选并通过健康检查；
10. External Process Recovery 两阶段继续通过；
11. Room Schema、Manifest 和系统备份 XML 无变化。

## 十四、PR09-13B 禁止重新选择的决定

- Runtime Provider 是唯一数据库生命周期所有者；
- 使用世代和租约隔离旧对象图；
- Compose 世代切换清理旧 ViewModelStore；
- 闭库后的重开位于不可取消收尾区；
- 重开或验证失败进入 `Unavailable`，不复活旧 Runtime；
- 显式重试必须完成最小查询和外键检查；
- 不使用第二套 Room Builder；
- Runtime 租约不替代 `BackupOperationGate`；
- 本前置 PR 不承担备份格式、加密或恢复协议设计。