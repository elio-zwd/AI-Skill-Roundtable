# Draft PR #52 第三轮本地严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #52 做第三轮严格只读验收。

仓库：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable
```

PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/52
```

目标分支：

```text
fix/pr-09-13a-room-v12-test-baseline
```

本轮只验证 Room v12 / Skill v2 历史测试基线和外部进程恢复测试编排，不实现 PR09-13A，不允许修改任何文件。

## 一、严格只读纪律

只允许：

```text
fetch / checkout / pull --ff-only
读取
构建
测试
安装 APK
受控 adb force-stop / start / am instrument
读取日志与 JUnit XML
输出报告
```

禁止：

```text
修改文件
格式化或自动修复
提交、推送、变基、合并
修改 PR 状态
删除测试或降低断言
使用 @Ignore
修改生产代码、Schema、Migration、Gradle、Manifest、CI
两阶段之间重新安装 APK或清除数据
```

## 二、精确 Head 锁

先从 PR #52 描述读取：

```text
最终验收锁定 Head：<SHA>
```

执行：

```powershell
$expectedHead = "<从 PR 描述复制>"

git fetch origin --prune
git checkout fix/pr-09-13a-room-v12-test-baseline
git pull --ff-only origin fix/pr-09-13a-room-v12-test-baseline

$localHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse origin/fix/pr-09-13a-room-v12-test-baseline).Trim()
$base = (git merge-base HEAD origin/main).Trim()

git status --short
git diff --exit-code
git diff --cached --exit-code

if ($localHead -ne $expectedHead) { throw "HEAD mismatch" }
if ($remoteHead -ne $expectedHead) { throw "Remote HEAD mismatch" }
```

必须记录：

- PR 状态仍为 Draft；
- Local Head；
- Remote Head；
- PR 描述锁定 Head；
- Merge-base；
- 三权是否一致。

不一致则停止，结论为：

```text
BLOCKED_HEAD_NOT_LOCKED
```

## 三、环境记录

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

设置实际设备：

```powershell
$device = "emulator-5554"
adb -s $device shell getprop ro.product.model
adb -s $device shell getprop ro.build.version.sdk
adb -s $device shell getprop ro.build.version.release
adb -s $device shell wm size
adb -s $device shell wm density
adb -s $device shell settings get system font_scale
```

## 四、差异范围

Base：

```text
4db7843a84911d7ad871a8aad5dd698a34b70b10
```

执行：

```powershell
$baseSha = "4db7843a84911d7ad871a8aad5dd698a34b70b10"
git diff --name-status "$baseSha...HEAD"
git diff --stat "$baseSha...HEAD"
git diff --check "$baseSha...HEAD"

git diff --exit-code "$baseSha...HEAD" -- app/src/main
git diff --exit-code "$baseSha...HEAD" -- app/src/test
git diff --exit-code "$baseSha...HEAD" -- app/src/main/assets
git diff --exit-code "$baseSha...HEAD" -- app/schemas
git diff --exit-code "$baseSha...HEAD" -- .github
```

允许差异只能包含 6 个 AndroidTest 文件和 2 份文档：

```text
app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryExternalProcessRecoveryTest.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryIdempotencyTest.kt
app/src/androidTest/java/com/elio/jianyu/execution/OfficialCatalogExecutionSkillResolverIntegrationTest.kt
docs/planning/pr-09-13a-room-v12-test-baseline-plan.md
docs/testing/pr-09-13a-room-v12-test-baseline-local-readonly-acceptance-prompt.md
```

出现生产路径差异立即判定 `FAIL_SCOPE_VIOLATION`。

## 五、静态核对第三轮修复

检查 `RoomJianyuRepositoryExternalProcessRecoveryTest`：

1. 存在显式参数：

```text
jianyuExternalProcessRecovery=true
```

2. 未传参数时使用 JUnit Assume 跳过，不是 `@Ignore`；
3. step1 / step2 方法和全部恢复断言仍保留；
4. step2 先检查 Issue 与 Lifecycle 是否存在；
5. Repository Failure 会输出具体 `RepositoryError`；
6. 未修改任何生产文件。

## 六、基础构建

依次执行并记录退出码：

```powershell
.\gradlew.bat --stop
.\gradlew.bat --no-daemon :app:compileDebugKotlin --stacktrace
.\gradlew.bat --no-daemon :app:testDebugUnitTest --stacktrace
.\gradlew.bat --no-daemon :app:lintDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleRelease --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

任一非 0 即 FAIL。

## 七、五个普通专项类

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
  .\gradlew.bat --no-daemon :app:connectedDebugAndroidTest `
    "-Pandroid.testInstrumentationRunnerArguments.class=$class" `
    --stacktrace
  if ($LASTEXITCODE -ne 0) { throw "FAILED: $class" }
}
```

记录每类测试数、通过、失败、Error、Skipped、耗时与退出码。

## 八、普通全量 Instrumentation

先运行普通全量：

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

本轮必须确认：

- 全量任务完整结束，不再停在 152/195；
- `RoomJianyuRepositoryExternalProcessRecoveryTest` 两项以 Assume 跳过；
- 跳过原因明确为未传 `jianyuExternalProcessRecovery=true`；
- 除这两项外 0 Failure、0 Error；
- Gradle Exit Code = 0；
- JUnit XML 正常生成。

注意：

- 这两项跳过不等于专项通过；
- 专项必须在下一节通过直接 ADB Instrumentation 真实执行；
- 如果全量仍卡挂，记录最后测试类/方法、logcat、线程或进程信息，结论 FAIL。

## 九、外部进程恢复真实两阶段

### 9.1 一次性安装

使用前面构建出的 APK，一次性安装：

```powershell
$appApk = "app/build/outputs/apk/debug/app-debug.apk"
$testApk = "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

adb -s $device install -r -t $appApk
adb -s $device install -r -t $testApk
```

确认 Instrumentation 组件：

```powershell
adb -s $device shell pm list instrumentation
```

预期组件通常为：

```text
com.elio.jianyu.test/androidx.test.runner.AndroidJUnitRunner
```

以设备真实输出为准：

```powershell
$runner = "com.elio.jianyu.test/androidx.test.runner.AndroidJUnitRunner"
```

### 9.2 只在开始前清理一次

```powershell
adb -s $device shell am force-stop com.elio.jianyu
adb -s $device shell pm clear com.elio.jianyu
```

从这里开始到 step2 完成前，严禁：

```text
再次安装 APK
再次运行 connectedDebugAndroidTest
pm clear
adb uninstall
清空模拟器数据
```

### 9.3 直接运行 step1

```powershell
adb -s $device shell am instrument -w -r `
  -e jianyuExternalProcessRecovery true `
  -e class "com.elio.jianyu.data.RoomJianyuRepositoryExternalProcessRecoveryTest#step1SeedRecoveryStateBeforeExternalForceStop" `
  $runner
```

必须 PASS，并证明：

- Issue ACTIVE；
- Run RUNNING；
- Pending Message 存在；
- Draft 存在。

### 9.4 真实重启目标 App

```powershell
adb -s $device shell am force-stop com.elio.jianyu
adb -s $device shell monkey -p com.elio.jianyu -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 3
adb -s $device shell am force-stop com.elio.jianyu
```

记录每条输出。

### 9.5 直接运行 step2

```powershell
adb -s $device shell am instrument -w -r `
  -e jianyuExternalProcessRecovery true `
  -e class "com.elio.jianyu.data.RoomJianyuRepositoryExternalProcessRecoveryTest#step2VerifyRecoveryStateAfterExternalForceStopAndAppRestart" `
  $runner
```

必须 PASS，并验证：

- step1 写入的 Issue 和 Lifecycle 仍存在；
- 两次 `recoverIssue()` 结果一致；
- Stage = 1；
- Run = 1 且状态 RUNNING；
- Pending Message 保留；
- Draft 保留；
- Lifecycle = ACTIVE；
- 成功 Participant 集合为空；
- 可重试 Participant 集合正确；
- `PRAGMA foreign_key_check = 0`。

若失败，必须保存新的明确错误：

- Issue/Lifecycle 不存在提示；或
- `RepositoryResult.Failure(error=...)` 的具体错误。

不得再只报告 `ClassCastException`。

## 十、重点回归

运行：

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.IssueLifecycleV12MigrationTest,com.elio.jianyu.data.IssueLifecycleV12RepositoryDatabaseTest,com.elio.jianyu.data.IssuePurgeDatabaseCleanerTest,com.elio.jianyu.data.RoomJianyuRepositoryProcessRecoveryTest,com.elio.jianyu.data.ArtifactSourceRecoveryDatabaseTest,com.elio.jianyu.data.StageAdvancementMigrationTest,com.elio.jianyu.data.StageAdvancementRepositoryDatabaseTest,com.elio.jianyu.data.AudioAssetRepositoryDatabaseTest,com.elio.jianyu.lifecycle.IssueArchiveCoordinatorDatabaseTest,com.elio.jianyu.ui.screens.issues.IssueLifecycleUiTest,com.elio.jianyu.skill.catalog.OfficialSkillExecutionManifestV2AndroidTest" `
  --stacktrace
```

记录类数、测试数、通过、失败、Error、Skipped、耗时、退出码。

## 十一、JUnit XML

```powershell
$xmlFiles = Get-ChildItem `
  -Path app/build/outputs/androidTest-results `
  -Recurse `
  -Filter "*.xml"

$xmlFiles | Select-Object FullName,Length,LastWriteTime
```

汇总所有最新 XML：

```powershell
$summary = foreach ($file in $xmlFiles) {
  [xml]$xml = Get-Content $file.FullName
  foreach ($suite in @($xml.testsuites.testsuite) + @($xml.testsuite)) {
    if ($null -ne $suite) {
      [pscustomobject]@{
        File = $file.FullName
        Suite = $suite.name
        Tests = [int]$suite.tests
        Failures = [int]$suite.failures
        Errors = [int]$suite.errors
        Skipped = [int]$suite.skipped
        Time = $suite.time
      }
    }
  }
}

$summary | Format-Table -AutoSize
$summary | Measure-Object Tests,Failures,Errors,Skipped -Sum
```

普通全量允许且只允许 External Process 两项 Assume Skipped；其他 Failure / Error 必须为 0。

## 十二、身份、Secret 与终检

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1

git.exe grep -n -I -E `
  "AIza[0-9A-Za-z_-]+|sk-[0-9A-Za-z_-]+|api[_-]?key\s*=|Authorization:\s*Bearer|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY" `
  -- app/src/main app/src/test app/src/androidTest docs

git diff --exit-code -- app/schemas
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
git rev-parse origin/fix/pr-09-13a-room-v12-test-baseline
```

`git grep` 退出码 1 且无输出表示未发现匹配，不要误判。

## 十三、GitHub CI

检查精确 Head 对应：

```text
Secret scan
Android UI Test Compile
Android CI
```

记录 Run 编号、状态、结论和绑定 SHA。

## 十四、判定标准

### PASS

必须全部满足：

- Head 三权一致；
- 差异范围正确；
- 生产路径、Schema 无变化；
- 五个普通专项类通过；
- 普通全量完整结束；
- 普通全量仅 External Process 两项 Assume Skipped；
- 外部进程 step1 / step2 通过直接 ADB Instrumentation 真实执行；
- 重点回归通过；
- JVM、Lint、Debug、Release、AndroidTest APK 通过；
- CI 全绿；
- 工作区 Clean。

### FAIL

任一成立即 FAIL：

- 普通测试 Failure / Error；
- 全量再次卡挂；
- External Process 真实专项失败；
- 两阶段之间数据消失；
- `RepositoryResult.Failure`；
- 外键失败；
- 生产路径或 Schema 漂移；
- CI 失败。

### INSUFFICIENT_EVIDENCE

适用于设备不可用、全量未完成、XML 缺失、专项未真实执行或 Head 无法锁定。

## 十五、最终报告结构

```markdown
# Draft PR #52 第三轮严格只读验收报告

## 一、最终结论
## 二、精确 Head 与 PR 状态
## 三、环境与设备
## 四、差异范围
## 五、基础构建与 JVM
## 六、五个普通专项类
## 七、普通全量 Instrumentation
## 八、External Process 直接 ADB 两阶段
## 九、重点回归
## 十、JUnit XML
## 十一、身份与 Secret Scan
## 十二、GitHub CI
## 十三、工作区终检
## 十四、失败项或未验证项
## 十五、风险与建议
```

即使 PASS，本地 AI 只能建议 Ready，不得修改 PR、合并或启动 PR09-13A。
