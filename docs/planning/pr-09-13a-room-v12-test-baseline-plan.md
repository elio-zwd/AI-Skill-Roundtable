# PR09-13A 前置修复：Room v12 测试基线收口计划

## 1. 目标与边界

本 PR 只收口 PR09-12 将 Room 升级到 v12、PR09-05C 将默认官方 Skill 发布升级为 v2 后遗留的历史 Android Instrumentation 测试契约与外部设备验收编排。

本 PR 不实现 PR09-13A/13B 的备份、快照、加密导出、KDF、AEAD、导入或恢复替换，也不修改生产数据库、Repository、Lifecycle、Skill Catalog、Resolver、资产、UI、Schema 或 Migration。

## 2. 精确基线

- 仓库：`elio-zwd/AI-Skill-Roundtable`
- Base：`main@4db7843a84911d7ad871a8aad5dd698a34b70b10`
- 分支：`fix/pr-09-13a-room-v12-test-baseline`
- Room：v12
- PR09-12 Merge SHA：`8b7d49cbdc10b59753a5017a056e68559f3bd183`
- PR09-05C Merge SHA：`4db7843a84911d7ad871a8aad5dd698a34b70b10`

## 3. 已确认的正式生产合同

### 3.1 Room v12

`RoundtableDatabase` 已正式注册 `1→2` 至 `11→12` 的完整迁移链，`12.json` 为当前 committed Schema。历史全局迁移列表测试必须精确包含 `11→12`，不能继续停留在 v11，也不能改成动态终点或宽松断言。

### 3.2 生命周期 v12

`RoomJianyuRepository` 的旧生命周期快捷入口被正式封闭：

- `archiveIssue()` → `archive_event_required`
- `restoreIssue()` → `resume_event_required`
- `requestIssuePurge()` → `purge_operation_required`

正式归档入口为 `RoomIssueLifecycleV12Repository.archiveIssueWithEvent()`。活动 Run、Pending Message、活动讨论或 Pending Audio 不能被伪造成已归档或已进入回收站。

### 3.3 官方 Skill v2

默认发布清单为 v2，固定 44 项全部可执行。历史 v1 发布仍可显式加载，仅用于验证历史回滚和 non-executable 分支。

## 4. 第一轮修复

已修复：

1. `ExecutionRuntimeMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion12`
2. `ResourceLifecycleMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion12`

首轮设备证据：

- 两类合计 5/5 PASS；
- 10 个 Room v12 / 生命周期重点类 36/36 PASS；
- JVM、Lint、Debug、Release、AndroidTest APK PASS；
- 全量运行暴露 5 项历史测试失败，并在 152/195 后卡挂。

## 5. 第二轮修复

首轮 5 项 Failure 经源码核对后分类为：

- 4 个独立历史测试合同滞后；
- 1 个由前置失败造成的级联结果；
- 没有证据支持把它们认定为 5 个生产缺陷。

已同步：

- Repository 旧快捷入口应返回精确 v12 Failure；
- 活动任务阻止 Trash；
- 幂等测试使用正式 Archive Event；
- 外部恢复夹具保持合法 `ACTIVE` 状态；
- 默认 v2 与显式历史 v1 的 Resolver 测试分离。

第二轮设备证据：

- 前五个修改类 28/28 PASS；
- 11 个重点类 39/39 PASS；
- JVM、Lint、Debug、Release、AndroidTest APK PASS；
- 精确 Head GitHub CI 全绿；
- External Process step1 PASS；
- step2 在第二次 `connectedDebugAndroidTest` 中返回 `RepositoryResult.Failure`；
- 普通全量仍稳定卡在 152/195，7.5 小时无推进。

## 6. 第二轮失败的根因假设

### 6.1 两阶段 step2

第二轮验收使用了两次独立 `connectedDebugAndroidTest`：

```text
Gradle step1
→ force-stop / 启动 App / force-stop
→ Gradle step2
```

该流程会再次进入 Gradle 的安装与测试环境准备阶段，不能证明 step1 写入的应用数据目录一定未被重置。当前异常只显示测试把 `RepositoryResult.Failure` 强转为 Success，未记录具体 `RepositoryError`。

因此第三轮需要：

- 一次性安装 App APK 与 AndroidTest APK；
- step1、step2 均直接使用 `adb shell am instrument`；
- 两阶段之间不得重新安装 APK、执行 Gradle connected task 或清除数据；
- step2 在恢复前直接检查 Issue 与 Lifecycle 是否仍存在；
- Repository Failure 必须输出具体错误。

### 6.2 全量 152/195 卡挂

`RoomJianyuRepositoryExternalProcessRecoveryTest` 使用生产数据库名 `roundtable_database`，step1 会删除该数据库，并要求测试进程之外的 ADB 在两个方法之间重启 App。

普通全量 Instrumentation 不具备外部 ADB 协调条件；同一测试进程中其他 UI / Runtime 测试还可能已经打开生产数据库单例。因此，把该类无条件混入普通全量套件存在数据库争用与编排不成立风险。

该判断目前是基于代码结构和两次稳定卡点形成的高可信根因假设，仍需第三轮设备证据确认。

## 7. 第三轮最小修复

仅修改 `RoomJianyuRepositoryExternalProcessRecoveryTest`：

1. 增加显式 Instrumentation 参数：
   - `jianyuExternalProcessRecovery=true`
2. 未传参数时通过 JUnit Assume 跳过两项外部协调测试：
   - 普通全量套件不执行数据库删除和外部恢复步骤；
   - 不使用 `@Ignore`；
   - 不删除测试；
   - 显式专项仍真实执行。
3. step2 增加前置存在性检查：
   - Issue 必须存在；
   - Lifecycle 必须存在；
   - 若不存在，明确提示两阶段之间发生了重新安装、清数据或数据目录变化。
4. `successValue()` 改为在 Failure 时输出具体 `RepositoryError`，不再只产生无信息的 `ClassCastException`。

## 8. 修改范围

允许修改：

- `app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryExternalProcessRecoveryTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryIdempotencyTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/execution/OfficialCatalogExecutionSkillResolverIntegrationTest.kt`
- 本计划；
- 对应本地严格只读验收 Prompt。

禁止修改：

- `app/src/main/`
- `app/src/test/`
- `app/src/main/assets/`
- `app/schemas/`
- Manifest、Gradle、CI
- 生产 Repository、Lifecycle、Skill、UI

## 9. 第三轮验证顺序

### 9.1 静态与编译

```text
精确 Head 锁定
→ 差异范围审查
→ AndroidTest 编译
→ JVM / Lint / Debug / Release
```

### 9.2 普通全量

运行普通：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

要求：

- 套件完整结束；
- External Process 两项只能以 Assume 跳过；
- 跳过原因必须为缺少 `jianyuExternalProcessRecovery=true`；
- 其余测试 0 Failure / 0 Error；
- 不再卡在 152/195。

### 9.3 外部进程专项

只允许一次安装后直接执行两次 Instrumentation：

```text
安装 App APK
安装 AndroidTest APK
清理一次目标 App 数据
adb instrument step1（显式参数）
force-stop → 启动 App → force-stop
adb instrument step2（显式参数）
```

两阶段之间禁止：

- 再次运行 `connectedDebugAndroidTest`；
- 再次安装 APK；
- `pm clear`；
- `adb uninstall`。

## 10. 完成门禁

PASS 必须同时满足：

- 六个修改测试类的普通可执行部分通过；
- 普通全量完整结束；
- 普通全量只有 External Process 两项被明确 Assume 跳过；
- 外部进程 step1 / step2 通过直接 ADB Instrumentation 真实执行；
- step2 Issue、Lifecycle、Run、Pending Message、Draft 均恢复；
- `PRAGMA foreign_key_check = 0`；
- JVM、Lint、Debug、Release、AndroidTest APK 全绿；
- 精确 Head GitHub CI 全绿；
- 工作区 Clean；
- Schema 无漂移。

任一 External Process 真实专项失败、全量再次卡挂、生产路径出现差异或合法生产流程失败，结论必须为 FAIL。

## 11. 当前状态

第三轮修改完成后、设备复验前，结论只能是：

```text
INSUFFICIENT_EVIDENCE
```

PR 保持 Draft。未经用户明确授权，不标记 Ready、不合并、不启用自动合并、不删除分支，也不启动 PR09-13A。

## 12. PR09-13A 启动门禁

PR09-13A 只能在以下条件全部满足后启动：

1. PR #52 最新精确 Head 本地严格只读验收 PASS；
2. GitHub CI 全绿；
3. 用户明确授权 Ready 与合并；
4. PR #52 已实际合并；
5. 从合并后的最新 `main` 创建 `security/pr-09-13a-backup-design`。
