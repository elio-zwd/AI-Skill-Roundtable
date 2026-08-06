# PR09-13A 前置修复：Room v12 测试基线修复后本地严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 中 Draft PR #52 做第二轮严格只读验收。

仓库：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable
```

Draft PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/52
```

目标分支：

```text
fix/pr-09-13a-room-v12-test-baseline
```

本 PR 的唯一目标是：

> 同步 PR09-12 Room v12 与 PR09-05C 默认 Skill v2 发布后遗留的历史 Android Instrumentation 测试契约，使最新 `main` 的全量设备测试恢复为可验证绿色基线。

本 PR 不是 PR09-13A，不允许实现备份、快照、加密导出、KDF、AEAD、导入或恢复替换。

## 一、首轮验收事实

首轮本地验收锁定旧 Head：

```text
a70308d6eb91ced7e3d60a666d51e5b955ed66a8
```

已真实通过：

- `ExecutionRuntimeMigrationTest`：2/2；
- `ResourceLifecycleMigrationTest`：3/3；
- 10 个 Room v12 与生命周期重点类：36/36；
- JVM、Lint、Debug、Release、AndroidTest APK；
- GitHub CI。

首轮全量 `connectedDebugAndroidTest` 包含 195 项，运行到 152 项后卡挂；已完成部分为 147 PASS、5 Failure。失败为：

1. `RoomJianyuRepositoryDatabaseTest#lifecycleAndPurgeRequestNeverDeleteIssueOrStopRun`；
2. `RoomJianyuRepositoryExternalProcessRecoveryTest#step1SeedRecoveryStateBeforeExternalForceStop`；
3. `RoomJianyuRepositoryExternalProcessRecoveryTest#step2VerifyRecoveryStateAfterExternalForceStopAndAppRestart`；
4. `RoomJianyuRepositoryIdempotencyTest#saveIssueRetryRemainsIdempotentAfterMessageAndLifecycleChanges`；
5. `OfficialCatalogExecutionSkillResolverIntegrationTest#realResolverRejectsDuplicateUnknownAndNonExecutableSkills`。

远端根因复核已确认：

- 以上不是 5 个生产缺陷；
- 实际为 4 个独立历史测试合同滞后；
- External Process step2 是 step1 失败后的级联结果；
- 生产 v12 正确拒绝旧 `archiveIssue()` / `requestIssuePurge()` 快捷入口；
- 活动 Run/Pending Message 正确阻止 Archive/Trash；
- 默认 Skill v2 正确将固定 44 项全部发布为可执行。

本轮必须验证第二轮修复是否精确、是否没有掩盖生产缺陷，以及全量 195 项或当前实际套件是否完整执行为 0 Failure。

## 二、绝对只读纪律

全过程只允许：

```text
拉取
检出
读取
构建
测试
安装测试 APK
执行受控 force-stop
收集日志与 JUnit XML
生成验收报告
```

禁止：

```text
修改任何仓库文件
自动格式化
自动修复
提交
推送
变基
合并
关闭 PR
标记 Ready
启用自动合并
删除分支
强制更新分支
降低断言
删除测试
使用 @Ignore
修改生产代码
修改测试夹具
重新生成并保留 Schema 差异
adb uninstall
adb shell pm clear
清空模拟器用户数据
调用生产网络
使用真实 API Key
```

发现问题时只报告，不修改。任何失败必须保存测试类、方法、首个失败日志、复现命令、退出码、第一根因和 JUnit XML。

## 三、精确 Head 锁

打开 PR #52 描述，复制：

```text
最终验收锁定 Head：<40 位 SHA>
```

执行：

```powershell
$expectedHead = "<从 PR #52 描述复制>"

git fetch origin --prune
git checkout fix/pr-09-13a-room-v12-test-baseline
git pull --ff-only origin fix/pr-09-13a-room-v12-test-baseline

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/fix/pr-09-13a-room-v12-test-baseline
git rev-parse origin/main
git merge-base HEAD origin/main
git log -20 --oneline --decorate

$actualHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse origin/fix/pr-09-13a-room-v12-test-baseline).Trim()
if ($actualHead -ne $expectedHead) {
    throw "HEAD mismatch: expected=$expectedHead actual=$actualHead"
}
if ($remoteHead -ne $expectedHead) {
    throw "Remote mismatch: expected=$expectedHead remote=$remoteHead"
}
if ((git status --short).Length -ne 0) {
    throw "Working tree must be clean"
}
```

要求：

- PR 仍为 Draft；
- Base/merge-base 为 `main@4db7843a84911d7ad871a8aad5dd698a34b70b10`，或 PR 描述中明确记录的后续更新基线；
- Local、Remote、PR 描述三权一致；
- 未启动 `security/pr-09-13a-backup-design`。

任一不满足：

```text
BLOCKED_HEAD_NOT_LOCKED
```

## 四、环境和设备记录

```powershell
Get-CimInstance Win32_OperatingSystem |
  Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
javac -version
.\gradlew.bat --version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

显式指定设备：

```powershell
$device = "emulator-5554" # 若实际设备不同，填写真实序列号
adb -s $device shell getprop ro.build.version.sdk
adb -s $device shell getprop ro.build.version.release
adb -s $device shell getprop ro.product.model
adb -s $device shell wm size
adb -s $device shell wm density
adb -s $device shell settings get system font_scale
```

记录 Windows、PowerShell、Git、JDK、Gradle、Kotlin、ADB、设备、API、Android、分辨率、密度、字号和验收起止时间。

## 五、差异范围

执行：

```powershell
git diff --name-status 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD
git diff --stat 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD
git diff --check 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD
```

允许的测试文件仅为：

```text
app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryExternalProcessRecoveryTest.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryIdempotencyTest.kt
app/src/androidTest/java/com/elio/jianyu/execution/OfficialCatalogExecutionSkillResolverIntegrationTest.kt
```

允许的文档仅为：

```text
docs/planning/pr-09-13a-room-v12-test-baseline-plan.md
docs/testing/pr-09-13a-room-v12-test-baseline-local-readonly-acceptance-prompt.md
```

生产路径必须无差异：

```powershell
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- app/src/main
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- app/schemas
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- app/src/test
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- app/src/main/assets
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- .github
```

必须确认没有修改：

- `RoundtableDatabase.kt`；
- 任何 Entity、DAO、Migration；
- `12.json` 或历史 Schema；
- 生产 Repository、Lifecycle、Skill Catalog、Resolver、Manifest、资产或 UI；
- Gradle、Manifest、CI；
- destructive migration；
- `@Ignore`、删除测试、宽松版本断言或动态版本终点。

## 六、逐项静态合同检查

### 6.1 迁移注册序列

确认：

- 两个方法名精确同步到 `Version1ToVersion12`；
- 预期列表精确包含 `1→2` 至 `11→12`；
- 没有改动 v6→v7、v7→v8 等历史数据断言；
- `StageAdvancementMigrationTest` 仍有意以 v11 为终点。

### 6.2 Repository 生命周期

确认 `RoomJianyuRepositoryDatabaseTest`：

- 不再把旧 `archiveIssue()`、旧 `requestIssuePurge()` 强转为 Success；
- 精确断言：
  - `archive_event_required`；
  - `trash_active_work`；
  - `purge_operation_required`；
- 继续断言 Issue 存在、Run 仍 `RUNNING`、Lifecycle 为 `ACTIVE`、`purgeRequestedAt == null`。

### 6.3 外部进程恢复

确认：

- 种子不再伪造“活动任务 + ARCHIVED”非法状态；
- step1 与 step2 精确期待 `ACTIVE`；
- Run、Pending Message、Draft 和外键断言保留。

### 6.4 保存议题幂等

确认：

- Run 经过 `NOT_STARTED→RUNNING→SUCCEEDED`；
- 归档使用 `RoomIssueLifecycleV12Repository.archiveIssueWithEvent()`；
- Archive 快照精确为 1 Stage、1 Run、0 Draft/Artifact/Audio；
- 保存议题重试继续断言幂等、Lifecycle 为 `ARCHIVED`、兼容 Session 保留。

### 6.5 Skill v2/v1

确认：

- duplicate 与 unknown 仍使用默认 v2 Runtime；
- non-executable 显式加载 `V1_EXECUTION_PUBLICATION_ASSET_PATH`；
- `zhang_xuefeng` 只在历史 v1 Resolver 中期待 `skill_not_executable`；
- 未修改默认 v2 44 项全部可执行合同。

## 七、证据目录与低 Token 工具

优先使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
```

证据保存到仓库外：

```powershell
$EvidenceRoot = Join-Path $env:TEMP (
  "jianyu-pr52-retest-" + (Get-Date -Format "yyyyMMdd-HHmmss")
)
New-Item -ItemType Directory -Path $EvidenceRoot -Force | Out-Null
```

每个命令记录：

- 完整命令；
- 开始/结束时间；
- 退出码；
- 测试总数、失败、错误、跳过；
- 原始日志；
- JUnit XML。

## 八、构建与 JVM

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:compileDebugKotlin --stacktrace
.\gradlew.bat :app:testDebugUnitTest --stacktrace
.\gradlew.bat :app:lintDebug --stacktrace
.\gradlew.bat :app:assembleDebug --stacktrace
.\gradlew.bat :app:assembleRelease --stacktrace
.\gradlew.bat :app:assembleDebugAndroidTest --stacktrace
```

严格区分 JVM、Lint、Debug、Release/R8 和 AndroidTest APK 编译；`assembleDebugAndroidTest` 不等于设备测试通过。

## 九、六个修复类专项复验

分别运行：

```powershell
$classes = @(
  "com.elio.jianyu.data.ExecutionRuntimeMigrationTest",
  "com.elio.jianyu.data.ResourceLifecycleMigrationTest",
  "com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest",
  "com.elio.jianyu.data.RoomJianyuRepositoryIdempotencyTest",
  "com.elio.jianyu.execution.OfficialCatalogExecutionSkillResolverIntegrationTest"
)

foreach ($class in $classes) {
  .\gradlew.bat :app:connectedDebugAndroidTest `
    "-Pandroid.testInstrumentationRunnerArguments.class=$class" `
    --stacktrace
  if ($LASTEXITCODE -ne 0) {
    throw "Instrumentation failed: $class"
  }
}
```

`RoomJianyuRepositoryExternalProcessRecoveryTest` 先整类执行一次：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryExternalProcessRecoveryTest `
  --stacktrace
```

必须记录每类实际测试数量和 JUnit XML。

## 十、外部进程真实 force-stop 两阶段

先执行 step1：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryExternalProcessRecoveryTest#step1SeedRecoveryStateBeforeExternalForceStop" `
  --stacktrace
```

再执行：

```powershell
adb -s $device shell am force-stop com.elio.jianyu
adb -s $device shell monkey -p com.elio.jianyu -c android.intent.category.LAUNCHER 1
adb -s $device shell am force-stop com.elio.jianyu
```

最后执行 step2：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryExternalProcessRecoveryTest#step2VerifyRecoveryStateAfterExternalForceStopAndAppRestart" `
  --stacktrace
```

必须证明：

- step1 成功写入 ACTIVE Lifecycle、RUNNING Run、Pending Message、Draft；
- force-stop 和 App 启动后数据未丢失；
- step2 两次 Recovery 相等；
- Lifecycle 保持 ACTIVE；
- 外键检查为 0。

普通整类运行不能代替本节两阶段证据。

## 十一、Room v12 与 Skill 重点回归

至少逐类运行：

```powershell
$regressionClasses = @(
  "com.elio.jianyu.data.IssueLifecycleV12MigrationTest",
  "com.elio.jianyu.data.IssueLifecycleV12RepositoryDatabaseTest",
  "com.elio.jianyu.data.IssuePurgeDatabaseCleanerTest",
  "com.elio.jianyu.data.RoomJianyuRepositoryProcessRecoveryTest",
  "com.elio.jianyu.data.ArtifactSourceRecoveryDatabaseTest",
  "com.elio.jianyu.data.StageAdvancementMigrationTest",
  "com.elio.jianyu.data.StageAdvancementRepositoryDatabaseTest",
  "com.elio.jianyu.data.AudioAssetRepositoryDatabaseTest",
  "com.elio.jianyu.lifecycle.IssueArchiveCoordinatorDatabaseTest",
  "com.elio.jianyu.ui.screens.issues.IssueLifecycleUiTest",
  "com.elio.jianyu.skill.catalog.OfficialSkillExecutionManifestV2AndroidTest"
)

foreach ($class in $regressionClasses) {
  .\gradlew.bat :app:connectedDebugAndroidTest `
    "-Pandroid.testInstrumentationRunnerArguments.class=$class" `
    --stacktrace
  if ($LASTEXITCODE -ne 0) {
    throw "Regression failed: $class"
  }
}
```

重点证明：

- v1～v12 连续 Migration；
- v11→v12 数据、索引和外键；
- Archive Event、Resume Event、Relation、Purge Operation；
- 旧快捷入口不能绕过 v12；
- 活动任务阻止 Archive/Trash；
- 合法终态 Run 可以使用正式 Event 归档；
- 默认 v2 44 项可执行；
- 显式 v1 回滚仅四项可执行。

## 十二、全量设备 Instrumentation

在全部专项通过后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

要求：

- 必须完整执行到 Gradle 退出；
- 不接受只运行到 152 项后卡挂；
- 若当前套件仍为 195 项，应记录 195/195；
- 若测试数量变化，解释原因并以当前 Head 的 JUnit XML 为准；
- 失败数、错误数必须为 0；
- 保存所有 JUnit XML。

汇总：

```text
总测试数
通过数
失败数
错误数
跳过数
设备序列号
API Level
开始和结束时间
JUnit XML 路径
```

任何失败必须逐项记录，不得只给总数。

## 十三、静态门禁与 Secret Scan

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1

git grep -n -I -E "AIza[0-9A-Za-z_-]+|sk-[0-9A-Za-z_-]+|api[_-]?key\s*=|Authorization:\s*Bearer|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY" -- `
  app/src/main app/src/test app/src/androidTest docs
```

执行仓库现有 Secret Scan 等价检查。确认无真实密钥、Token、密码、用户正文或绝对敏感路径。

## 十四、GitHub CI

读取最终锁定 Head 的：

- Secret scan；
- Android UI Test Compile；
- Android CI。

要求全部 `success`。必须明确 AndroidTest APK 编译和 GitHub JVM/构建不能替代设备 Instrumentation。

## 十五、终检

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
git rev-parse origin/fix/pr-09-13a-room-v12-test-baseline
git diff --exit-code -- app/schemas
```

要求：

- 工作区干净；
- Schema 无漂移；
- Local、Remote、Expected Head 一致；
- 验收期间无修改、Commit、Push、Ready、Merge。

## 十六、结论规则

只能输出：

```text
PASS
PASS_WITH_NOTES
FAIL
INSUFFICIENT_EVIDENCE
```

### PASS

仅当同时满足：

- Head 三权锁定；
- 差异只含 6 个 AndroidTest 和 2 份文档；
- 生产代码、Schema、Migration、资产和 Manifest 无变化；
- 六个修复类专项全部通过；
- 外部 force-stop 两阶段通过；
- 11 个重点回归类全部通过；
- 全量设备测试完整执行且 0 Failure/0 Error；
- JVM、Lint、Debug、Release、AndroidTest APK 通过；
- Secret Scan、身份门禁、Schema freshness 通过；
- 同一 Head GitHub CI 全绿；
- 工作区最终干净。

### PASS_WITH_NOTES

仅允许不影响 Room/Repository/Skill 基线的人工视觉、TalkBack 等非阻断备注。任何测试未完成、卡挂、失败、外键问题或 CI 未完成都不能使用。

### FAIL

任一构建、测试、Lint、Secret Scan、Schema、外键、合法生产合同或 Head 锁失败。

### INSUFFICIENT_EVIDENCE

缺少设备、force-stop、全量 Instrumentation、JUnit XML、关键构建或同一 Head CI，且没有已知失败。

## 十七、失败格式

```text
测试类：
测试方法：
命令：
退出码：
第一条失败日志：
预期值：
实际值：
第一根因：
是否由本 PR 引入：
是否为历史测试合同滞后：
是否涉及生产行为：
是否涉及数据丢失、Schema mismatch 或 foreign_key_check：
建议后续：
```

只读验收 AI 不得自行修复。

## 十八、最终报告必须包含

1. 最终结论；
2. 仓库、PR、Base、Branch、Expected/Actual/Remote Head；
3. 环境和设备；
4. 差异文件；
5. 生产路径未变化证据；
6. 首轮 5 项 Failure 的修复后结果；
7. 六个修复类专项数量；
8. 外部 force-stop 两阶段；
9. 重点回归结果；
10. 全量设备测试完整数量和 JUnit XML；
11. JVM、Lint、Debug、Release、AndroidTest APK；
12. Secret Scan 和身份门禁；
13. GitHub CI；
14. 工作区最终状态；
15. 尚未验证项；
16. 风险和回归区域；
17. 是否允许建议 Ready；
18. PR09-13A 是否满足启动门禁。

即使最终 PASS，也不得自行标记 Ready、合并、删除分支或启动 PR09-13A；只把报告反馈给远端开发对话。
