# PR09-13A 前置修复：Room v12 测试基线收口计划

## 1. 目标与边界

本 PR 只同步 PR09-12 将 Room 升级到 v12 后仍停留在 v11 的历史 Android Instrumentation 测试契约，使迁移注册序列继续保持精确、连续、可审计。

本 PR 不实现 PR09-13A/13B 的备份、快照、加密导出、KDF、AEAD、导入或恢复替换，也不修改任何生产数据库、Repository、UI、Schema 或 Migration。

## 2. 精确基线

- 仓库：`elio-zwd/AI-Skill-Roundtable`
- Base：`main@4db7843a84911d7ad871a8aad5dd698a34b70b10`
- 开发分支：`fix/pr-09-13a-room-v12-test-baseline`
- Room：v12
- PR09-12 Merge SHA：`8b7d49cbdc10b59753a5017a056e68559f3bd183`
- PR09-05C Merge SHA：`4db7843a84911d7ad871a8aad5dd698a34b70b10`
- 分支创建前开放 PR：0

## 3. 根因证据

### 3.1 正式生产契约

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

因此，迁移注册序列新增 `11→12` 是正式生产合同，而不是待选择行为。

### 3.2 已锁定的精确失败

当前可从最新 `main` 源码直接证明以下两项会失败：

1. `ExecutionRuntimeMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion11`
   - 旧预期：精确列表只到 `10→11`；
   - 实际值：`ALL_MIGRATIONS` 已额外包含 `11→12`；
   - 第一根因：当前数据库版本升级后，历史全局迁移注册序列断言未同步；
   - 分类：纯测试合同滞后，不涉及 v7→v8 生产行为。

2. `ResourceLifecycleMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion11`
   - 旧预期：精确列表只到 `10→11`；
   - 实际值：`ALL_MIGRATIONS` 已额外包含 `11→12`；
   - 第一根因：当前数据库版本升级后，历史全局迁移注册序列断言未同步；
   - 分类：纯测试合同滞后，不涉及 v6→v7 或 v5→v7 生产行为。

PR09-05C 本地验收报告汇总为 7 项历史 Room v11 基线失败，但公开 PR、Review、评论和当前可读取报告没有保存其余类/方法或 JUnit XML。当前计划不把“7 项”拆成未经证据支持的文件名；本地全量复验必须用于确认是否还有其他独立失败。

### 3.3 明确排除的历史测试

`StageAdvancementMigrationTest` 的两个方法有意从 v10 创建 fixture，并只运行 `MIGRATION_10_11` 验证历史迁移。它们仍应以 v11 为终点，不改名、不追加 `MIGRATION_11_12`。

`IssueLifecycleV12MigrationTest` 的 v11 fixture 有意验证 `MIGRATION_11_12`，同样不改写为动态版本或其他终点。

## 4. 修改文件

计划修改：

- `app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt`
- `docs/testing/pr-09-13a-room-v12-test-baseline-local-readonly-acceptance-prompt.md`

本计划文件：

- `docs/planning/pr-09-13a-room-v12-test-baseline-plan.md`

## 5. 最小实现

对两个全局迁移注册序列测试分别执行相同的精确同步：

1. 测试方法名从 `Version1ToVersion11` 改为 `Version1ToVersion12`；
2. 期望列表末尾增加精确对 `11 to 12`；
3. 保留从 `1→2` 开始的所有历史步骤；
4. 保留精确列表相等断言，不改成 `>= 11`、动态终点或仅检查相邻性；
5. 不修改同文件内各领域历史 Migration fixture、数据、索引、外键和幂等断言。

## 6. 禁止修改的生产文件

本 PR 不修改：

- `app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt`
- `app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Migration.kt`
- `app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12*.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/`
- `app/src/main/java/com/elio/jianyu/skill/`
- `app/src/main/assets/`
- `app/schemas/com.elio.jianyu.data.RoundtableDatabase/12.json`
- Manifest、Gradle、CI、生产 Repository 与生产 UI。

如果真实设备复验出现 Schema mismatch、外键失败、迁移丢数据或生产异常，本 PR 不修改测试掩盖问题，而是停止并建议独立生产修复 PR。

## 7. 测试驱动顺序

由于当前远端对话没有 Android SDK、模拟器或本地仓库工作区，本轮 RED 证据来自源码中的确定性期望/实际差异；真实 Gradle 和设备 RED/GREEN 由独立本地只读验收执行。

本地验收顺序：

1. 精确锁定 Draft PR 当前 Head；
2. 在未修改源码的情况下，读取原始 Base 对应方法，确认旧期望缺少 `11→12`；
3. 在 PR Head 上逐类执行：
   - `ExecutionRuntimeMigrationTest`
   - `ResourceLifecycleMigrationTest`
4. 执行：
   - `IssueLifecycleV12MigrationTest`
   - `StageAdvancementMigrationTest`
   - PR09-12 生命周期与恢复重点类；
5. 执行全量 `connectedDebugAndroidTest`；
6. 对 JUnit XML 逐项核对失败数；
7. 如果仍出现历史 v11 断言，记录精确类、方法、第一条根因，不在只读验收中修改。

## 8. 单类复验命令

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeMigrationTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ResourceLifecycleMigrationTest
```

重点迁移回归：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.IssueLifecycleV12MigrationTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.StageAdvancementMigrationTest
```

## 9. 全量验证命令

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

同时执行仓库现有应用身份、Secret Scan、Schema freshness 与 `git diff --check` 门禁。必须分别报告 JVM、AndroidTest APK 编译、设备 Instrumentation、外部 UIAutomator 与人工点检，不能互相替代。

## 10. Commit 边界

1. `docs: 制定 Room v12 测试基线修复计划`
   - 只提交本计划。
2. `test: 同步 Room v12 历史设备测试断言`
   - 只修改两个已锁定迁移序列测试；
   - 添加本地严格只读验收 Prompt。

不得混入生产代码、格式化、依赖升级、Schema 或无关测试重构。

## 11. 验证状态与完成门禁

远端对话可完成：

- 仓库、Base、开放 PR、PR #50/#51、源码、Schema 和测试合同的静态核对；
- 差异范围审查；
- GitHub CI 状态读取。

远端对话不能完成：

- JVM、Lint、Debug/Release、AndroidTest APK 的本地执行；
- 模拟器上的单类和全量 Instrumentation；
- JUnit XML 实际统计；
- 外部 UIAutomator 或人工设备点检。

在独立本地设备验收完成前，结论只能是 `INSUFFICIENT_EVIDENCE`，不得宣布全量设备基线绿色，也不得启动 PR09-13A。

## 12. 风险与回滚

主要风险：

- 汇总报告所称 7 项中可能还有未保存明细的独立失败；
- 仅凭源码静态证据不能证明真实设备环境全部通过；
- 测试修改若错误地扩大到历史专项 Migration，会削弱版本边界。

控制方式：

- 仅修改两个全局注册序列断言；
- 明确保留所有历史专项 Migration 的原始终点；
- 全量设备验收必须读取 JUnit XML；
- 任何生产缺陷立即停止本 PR。

回滚方式：整体回滚本 PR 的测试和文档 Commit；不得回滚 Room v12、PR09-12、`12.json` 或使用 destructive migration。

## 13. PR09-13A 交接门禁

PR09-13A 必须满足：

1. 本 Draft PR 的精确 Head 完成本地严格只读验收；
2. 全量 `connectedDebugAndroidTest` 失败数为 0；
3. GitHub CI 全绿；
4. 用户明确授权 Ready 与合并；
5. 本 PR 已实际合并；
6. 从合并后的最新 `main` 创建 `security/pr-09-13a-backup-design`，不得复用本分支或未合并 Head。
