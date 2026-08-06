# PR09-13A 前置修复：Room v12 测试基线收口计划

## 1. 目标与边界

本 PR 只同步 PR09-12 将 Room 升级到 v12、PR09-05C 将默认执行发布升级到 v2 后遗留的历史 Android Instrumentation 测试契约，使最新 `main` 的全量设备测试重新具备可验证的绿色基线。

本 PR 不实现 PR09-13A/13B 的备份、快照、加密导出、KDF、AEAD、导入或恢复替换，也不修改任何生产数据库、Repository、UI、Schema、Migration、Skill 资产或执行 Manifest。

## 2. 精确基线

- 仓库：`elio-zwd/AI-Skill-Roundtable`
- Base：`main@4db7843a84911d7ad871a8aad5dd698a34b70b10`
- 开发分支：`fix/pr-09-13a-room-v12-test-baseline`
- Room：v12
- PR09-12 Merge SHA：`8b7d49cbdc10b59753a5017a056e68559f3bd183`
- PR09-05C Merge SHA：`4db7843a84911d7ad871a8aad5dd698a34b70b10`
- 分支创建前开放 PR：0

## 3. 正式生产契约

### 3.1 Room v12

`RoundtableDatabase.kt` 明确：

- `@Database(version = 12)`；
- `MIGRATION_11_12 = IssueLifecycleV12Migration.MIGRATION_11_12`；
- `ALL_MIGRATIONS` 精确注册 `1→2` 至 `11→12` 的 11 个连续步骤。

`12.json` 明确：

- `database.version = 12`；
- `identityHash = 933e93291334a0fe8a73fa6f7dc527c0`。

`IssueLifecycleV12MigrationTest` 已覆盖：

- v1～v4 连续迁移到 v12；
- v5～v11 committed Schema 连续迁移到 v12；
- v11→v12 数据保留、表与唯一索引；
- `PRAGMA foreign_key_check = 0`。

### 3.2 生命周期 v12

`RoomJianyuRepository` 明确拒绝旧快捷入口：

- `archiveIssue()` → `archive_event_required`；
- `restoreIssue()` → `resume_event_required`；
- `requestIssuePurge()` → `purge_operation_required`。

正式入口为：

- `RoomIssueLifecycleV12Repository.archiveIssueWithEvent()`；
- `resumeArchivedIssue()`；
- `requestIssuePurgeOperation()`。

归档和移入回收站均不得绕过活动 Run、Pending Message、活动 Cross Discussion 或 Pending Audio。测试不能再构造“Run 仍活动但 Issue 已归档/回收”的非法组合。

### 3.3 官方 Skill 默认发布

PR09-05C 后默认执行发布为 v2：

- 固定 Catalog 的 44 项全部可执行；
- `zhang_xuefeng` 在默认 v2 Runtime 中不再是 non-executable；
- 历史 v1 发布仍可通过 `OfficialSkillCatalogParser.V1_EXECUTION_PUBLICATION_ASSET_PATH` 显式加载，并保持仅四项可执行。

因此，验证 `skill_not_executable` 分支时必须显式使用历史 v1 Catalog，不能继续把默认 v2 中的人物 Skill 当作不可执行项。

## 4. 第一阶段已锁定并修复的失败

首次静态根因分析锁定以下两项确定性失败：

1. `ExecutionRuntimeMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion11`
   - 旧预期只到 `10→11`；
   - 实际 `ALL_MIGRATIONS` 已包含 `11→12`；
   - 修复：方法名同步到 v12，精确列表追加 `11 to 12`。

2. `ResourceLifecycleMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion11`
   - 根因与修复相同。

首次本地严格只读验收已在精确 Head `a70308d6eb91ced7e3d60a666d51e5b955ed66a8` 上真实证明：

- `ExecutionRuntimeMigrationTest`：2/2 PASS；
- `ResourceLifecycleMigrationTest`：3/3 PASS；
- 10 个 Room v12 与生命周期重点类：36/36 PASS；
- JVM、Lint、Debug、Release、AndroidTest APK：PASS；
- 全量设备测试运行到 152 项时出现 5 个 Failure，并因后续超时未完成 195 项全量。

## 5. 第一轮全量设备 Failure 根因分类

### 5.1 `RoomJianyuRepositoryDatabaseTest#lifecycleAndPurgeRequestNeverDeleteIssueOrStopRun`

- 现象：`archiveIssue()` 返回 `RepositoryResult.Failure`，测试强转 `Success`。
- 第一根因：测试仍调用 v12 明确禁止的旧 Archive 快捷入口，并继续期待活动 Run 下归档、移入回收站和请求 Purge 均成功。
- 生产行为：正确；旧入口必须失败，活动任务必须阻止 Trash。
- 分类：历史测试合同滞后，不是生产缺陷。
- 最小修复：
  - 将 Archive、Trash、Purge 三个结果保留为 Failure；
  - 精确断言 `archive_event_required`、`trash_active_work`、`purge_operation_required`；
  - 继续证明 Issue 未删除、Run 仍 `RUNNING`、Lifecycle 仍 `ACTIVE`、未写入 `purgeRequestedAt`。

### 5.2 `RoomJianyuRepositoryExternalProcessRecoveryTest#step1SeedRecoveryStateBeforeExternalForceStop`

- 现象：旧 `archiveIssue()` 返回 Failure，测试强转 Success。
- 第一根因：测试同时要求 Run 为 `RUNNING`、Message 为 Pending、Draft 存在且 Issue 为 `ARCHIVED`；该组合在 v12 下被正式禁止。
- 生产行为：正确；活动任务不能归档。
- 分类：历史测试夹具滞后，不是生产缺陷。
- 最小修复：删除旧 Archive 快捷入口调用，明确种子和重启后 Lifecycle 均保持 `ACTIVE`，继续验证 Run、Pending Message、Draft 和外键完整性。

### 5.3 `RoomJianyuRepositoryExternalProcessRecoveryTest#step2VerifyRecoveryStateAfterExternalForceStopAndAppRestart`

- 现象：预期 `ARCHIVED`，实际 `ACTIVE`。
- 第一根因：step1 在 Archive 强转处失败，未写入归档状态；这是 step1 的连锁结果，不是独立 Repository 恢复错误。
- 分类：由 5.2 导致的级联测试失败。
- 最小修复：与合法 v12 种子一致，重启后精确期待 `ACTIVE`。

### 5.4 `RoomJianyuRepositoryIdempotencyTest#saveIssueRetryRemainsIdempotentAfterMessageAndLifecycleChanges`

- 现象：旧 `archiveIssue()` 返回 Failure，测试强转 Success。
- 第一根因：测试仍使用旧 Archive 快捷入口。
- 生产行为：正确。
- 分类：历史测试合同滞后，不是生产缺陷。
- 最小修复：
  - 先把 Run 从 `NOT_STARTED→RUNNING→SUCCEEDED`，满足无活动任务门禁；
  - 使用 `RoomIssueLifecycleV12Repository.archiveIssueWithEvent()`；
  - 使用精确 Stage/Run/Draft/Artifact/Audio 快照计数；
  - 保留“保存议题重试在消息和生命周期变化后仍幂等”的原业务断言。

### 5.5 `OfficialCatalogExecutionSkillResolverIntegrationTest#realResolverRejectsDuplicateUnknownAndNonExecutableSkills`

- 现象：默认 Runtime 下 `zhang_xuefeng` 成功解析，旧测试期待 `skill_not_executable`。
- 第一根因：PR09-05C 已将默认 v2 发布升级为 44 项全部可执行，旧测试仍假设人物 Skill 默认不可执行。
- 生产行为：正确。
- 分类：历史 Skill 发布测试合同滞后，不是生产缺陷。
- 最小修复：
  - duplicate 与 unknown 继续使用默认 v2 Resolver；
  - non-executable 分支显式加载历史 v1 发布清单；
  - 使用 v1 Resolver 验证 `zhang_xuefeng` 返回 `skill_not_executable`；
  - 不修改默认 v2、Catalog、资产或生产 Resolver。

## 6. 修改文件

测试文件：

- `app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryExternalProcessRecoveryTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryIdempotencyTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/execution/OfficialCatalogExecutionSkillResolverIntegrationTest.kt`

文档：

- `docs/planning/pr-09-13a-room-v12-test-baseline-plan.md`
- `docs/testing/pr-09-13a-room-v12-test-baseline-local-readonly-acceptance-prompt.md`

## 7. 禁止修改的生产范围

本 PR 不修改：

- `app/src/main/`；
- `app/schemas/`；
- `app/src/test/`；
- `app/src/main/assets/`；
- `AndroidManifest.xml`；
- Gradle 依赖；
- `.github/`；
- Room 版本、Migration 和 `12.json`；
- 生产 Repository、生命周期协调器、Skill Catalog、Resolver、UI。

任何新复验若出现 Schema mismatch、外键失败、迁移丢数据、生产异常或合法 v12 流程失败，立即停止本测试修复 PR，不得继续修改断言掩盖问题。

## 8. 测试驱动顺序

### 第一轮

```text
旧迁移列表失败
→ 证明 ALL_MIGRATIONS 正式包含 11→12
→ 最小同步两个迁移列表断言
→ 两类专项设备复验通过
```

### 第二轮

```text
全量设备测试暴露 5 个 Failure
→ 逐项读取生产合同与测试源码
→ 确认 4 个独立测试合同滞后 + 1 个级联结果
→ 仅修改四个 AndroidTest 文件
→ 四类专项复验
→ Room v12 重点回归
→ 全量 195 项设备复验
```

## 9. 专项复验命令

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeMigrationTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ResourceLifecycleMigrationTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryIdempotencyTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.execution.OfficialCatalogExecutionSkillResolverIntegrationTest
```

外部进程恢复必须两阶段执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryExternalProcessRecoveryTest#step1SeedRecoveryStateBeforeExternalForceStop

adb -s emulator-5554 shell am force-stop com.elio.jianyu
adb -s emulator-5554 shell monkey -p com.elio.jianyu -c android.intent.category.LAUNCHER 1
adb -s emulator-5554 shell am force-stop com.elio.jianyu

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryExternalProcessRecoveryTest#step2VerifyRecoveryStateAfterExternalForceStopAndAppRestart
```

同时允许先整类运行以验证普通 Instrumentation 顺序，但整类运行不能替代真实 force-stop 两阶段证据。

## 10. 重点回归

至少复验：

- `IssueLifecycleV12MigrationTest`
- `IssueLifecycleV12RepositoryDatabaseTest`
- `IssuePurgeDatabaseCleanerTest`
- `RoomJianyuRepositoryProcessRecoveryTest`
- `ArtifactSourceRecoveryDatabaseTest`
- `StageAdvancementMigrationTest`
- `StageAdvancementRepositoryDatabaseTest`
- `AudioAssetRepositoryDatabaseTest`
- `IssueArchiveCoordinatorDatabaseTest`
- `IssueLifecycleUiTest`
- `OfficialSkillExecutionManifestV2AndroidTest`

重点检查：

- v1～v12 连续 Migration；
- v11→v12 专项和 `PRAGMA foreign_key_check`；
- 旧生命周期快捷入口不能绕过 v12 事实表；
- 活动任务阻止 Archive/Trash；
- 终态 Run 可以按正式入口归档；
- 外部进程恢复不伪造归档状态；
- 默认 v2 44 项可执行；
- 显式 v1 回滚仅四项可执行。

## 11. 完整验证命令

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

同时执行：

- `tools/check-app-identity.ps1`；
- Secret Scan 等价检查；
- `git diff --check`；
- `git diff --exit-code -- app/schemas`；
- 最终工作区 Clean 和精确 Head 锁定。

必须分别报告 JVM、AndroidTest APK 编译、设备 Instrumentation、外部 force-stop、UIAutomator 与人工点检，不能互相替代。

## 12. Commit 边界

已建立：

1. `docs: 制定 Room v12 测试基线修复计划`
2. `test: 同步 Room v12 历史设备测试断言`
3. `docs: 添加 Room v12 测试基线验收说明`

第二轮失败收口：

4. `test: 同步 Room v12 Repository 生命周期测试契约`
5. `test: 同步 Room v12 生命周期恢复测试契约`
6. `test: 同步 Room v12 幂等恢复测试契约`
7. `test: 同步官方 Skill v2 执行测试契约`
8. `docs: 补充 Room v12 全量设备失败根因计划`
9. `docs: 更新 Room v12 修复后严格只读复验说明`
10. `docs: 修正本地验收阶段状态表述`

GitHub Contents API 按文件创建 Commit；所有 Commit 均服务于同一测试基线修复任务，不混入生产修改。

## 13. 验证状态与完成门禁

当前已经有真实证据：

- 第一阶段两个迁移类 5/5 PASS；
- 10 个 Room v12 重点类 36/36 PASS；
- 首轮 GitHub CI 全绿；
- 首轮全量设备测试发现的 5 项 Failure 已取得精确类、方法和第一根因。

第二轮测试修改完成后，在新精确 Head 上必须重新执行：

- GitHub CI；
- 四个独立失败类专项；
- 外部恢复真实 force-stop 两阶段；
- 重点 Room/生命周期/Skill 回归；
- 全量 `connectedDebugAndroidTest`，必须完成全部测试，不接受运行到 152 项后卡挂；
- JUnit XML 汇总必须失败数为 0。

在上述证据完成前，状态仍为 `INSUFFICIENT_EVIDENCE`，不得宣布全量设备基线绿色，也不得启动 PR09-13A。

## 14. 风险与回滚

主要风险：

- 首轮全量套件未完成 195 项，后 43 项可能仍存在独立失败；
- 外部进程恢复必须真实经过 force-stop，普通整类运行不足以覆盖该路径；
- 测试若错误地伪造 Archive/Trash 或把默认 v2 降回 v1，会削弱正式产品合同。

控制方式：

- 旧快捷入口改为精确失败码断言；
- 合法 Archive 使用 v12 Event 正式入口；
- 活动恢复夹具保持 `ACTIVE`；
- non-executable 只在显式历史 v1 Catalog 中验证；
- 全量设备测试必须跑完并读取 JUnit XML。

回滚方式：整体回滚本 PR 的测试和文档 Commit；不得回滚 Room v12、PR09-12、PR09-05C、`12.json`、v2 Manifest 或使用 destructive migration。

## 15. PR09-13A 交接门禁

PR09-13A 必须满足：

1. 本 Draft PR 的最终精确 Head 完成本地严格只读验收；
2. 全量 `connectedDebugAndroidTest` 完整执行且失败数为 0；
3. 外部进程恢复两阶段通过；
4. GitHub CI 全绿；
5. 用户明确授权 Ready 与合并；
6. 本 PR 已实际合并；
7. 从合并后的最新 `main` 创建 `security/pr-09-13a-backup-design`，不得复用本分支或未合并 Head。
