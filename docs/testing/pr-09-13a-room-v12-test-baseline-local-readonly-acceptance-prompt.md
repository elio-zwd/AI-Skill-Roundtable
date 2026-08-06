# PR09-13A 前置修复：Room v12 测试基线本地严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 中分支 `fix/pr-09-13a-room-v12-test-baseline` 对应的 Draft PR 做严格只读验收。

仓库：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable
```

本 PR 的唯一目标是：

> 同步 PR09-12 将 Room 升级到 v12 后仍停留在 v11 的历史 Android Instrumentation 迁移注册序列断言，并证明最新基线的全量设备测试是否恢复为绿色。

本 PR 不是 PR09-13A，不允许实现备份、快照、加密导出、KDF、AEAD、导入或恢复替换。

## 一、绝对纪律

全过程只允许：

```text
拉取
检出
读取
构建
测试
安装测试 APK
收集日志与 JUnit XML
生成验收报告
```

禁止：

```text
修改任何仓库文件
自动格式化
自动修复
重新生成或保留 Schema 差异
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
adb uninstall
adb shell pm clear
清空模拟器用户数据
使用真实 API Key 或生产网络
```

发现问题时只报告，不修改。任何失败必须保存首个失败日志、测试类、测试方法、复现命令、退出码、所属领域和第一条根因。

## 二、精确验收锁

PR 文档不能硬编码自身最终 Commit SHA，否则文档更新会产生新 SHA。因此，以 Draft PR 描述中的字段为唯一验收锁：

```text
最终验收锁定 Head：<40 位 SHA>
```

开始前打开 Draft PR，复制该值：

```powershell
$expectedHead = "<从 Draft PR 描述复制的 40 位最终验收 SHA>"
```

执行：

```powershell
git fetch origin --prune
git checkout fix/pr-09-13a-room-v12-test-baseline
git pull --ff-only origin fix/pr-09-13a-room-v12-test-baseline

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/fix/pr-09-13a-room-v12-test-baseline
git rev-parse origin/main
git merge-base HEAD origin/main
git log -12 --oneline --decorate

$actualHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse origin/fix/pr-09-13a-room-v12-test-baseline).Trim()
if ($actualHead -ne $expectedHead) {
    throw "HEAD mismatch: expected=$expectedHead actual=$actualHead"
}
if ($remoteHead -ne $expectedHead) {
    throw "Remote branch mismatch: expected=$expectedHead remote=$remoteHead"
}
if ((git status --short).Length -ne 0) {
    throw "Working tree must be clean before acceptance"
}
```

要求：

- 分支精确为 `fix/pr-09-13a-room-v12-test-baseline`；
- Head 与远端分支、PR 描述三者一致；
- Base/merge-base 精确为 `main@4db7843a84911d7ad871a8aad5dd698a34b70b10`，除非 PR 描述明确记录了开发期间的更新基线；
- PR 仍为 Draft；
- 未创建或启动 `security/pr-09-13a-backup-design`；
- 工作区开始时干净。

任一条件不满足，停止并输出：

```text
BLOCKED_HEAD_NOT_LOCKED
```

## 三、环境记录

记录真实版本和时间：

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

选定唯一设备并记录：

```powershell
$device = "<明确填写在线设备序列号>"
adb -s $device shell getprop ro.build.version.sdk
adb -s $device shell getprop ro.build.version.release
adb -s $device shell getprop ro.product.model
adb -s $device shell wm size
adb -s $device shell wm density
adb -s $device shell settings get system font_scale
```

必须记录：

- Windows、PowerShell、Git、JDK/Javac、Gradle；
- Android SDK、adb；
- 设备序列号、型号、API Level、Android 版本、分辨率、密度、字号；
- 验收开始与完成时间。

存在多个在线设备时必须显式指定，不得自动任选。

## 四、差异范围与生产文件保护

执行：

```powershell
git diff --name-status 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD
git diff --stat 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD
git diff --check 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD
git diff 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- `
  app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt `
  app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt `
  docs/planning/pr-09-13a-room-v12-test-baseline-plan.md `
  docs/testing/pr-09-13a-room-v12-test-baseline-local-readonly-acceptance-prompt.md
```

预期差异只能包含：

```text
app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt
docs/planning/pr-09-13a-room-v12-test-baseline-plan.md
docs/testing/pr-09-13a-room-v12-test-baseline-local-readonly-acceptance-prompt.md
```

确认两个测试文件分别只有：

1. 方法名 `Version1ToVersion11` 改为 `Version1ToVersion12`；
2. 精确期望列表增加 `11 to 12`。

必须确认以下路径与 Base 完全相同：

```powershell
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- app/src/main
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- app/schemas
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- app/src/test
git diff --exit-code 4db7843a84911d7ad871a8aad5dd698a34b70b10...HEAD -- .github
```

特别确认没有：

- 修改 `RoundtableDatabase.kt`；
- 修改 `IssueLifecycleV12Migration.kt` 或其他 Migration；
- 修改 `12.json` 或历史 Schema；
- 修改生产 Repository、生命周期、Skill、UI、Manifest、Gradle 或 CI；
- destructive migration；
- 删除测试、`@Ignore`、宽松版本断言或动态读取终点；
- 把历史 v10→v11 专项测试错误改成 v12。

## 五、静态根因复核

读取 Base 版本：

```powershell
git show 4db7843a84911d7ad871a8aad5dd698a34b70b10:app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeMigrationTest.kt
git show 4db7843a84911d7ad871a8aad5dd698a34b70b10:app/src/androidTest/java/com/elio/jianyu/data/ResourceLifecycleMigrationTest.kt
git show 4db7843a84911d7ad871a8aad5dd698a34b70b10:app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
```

必须证明：

- Base 中两个旧方法都只期待到 `10→11`；
- Base 的正式 `ALL_MIGRATIONS` 已包含 `11→12`；
- 因此旧期望与正式生产合同确定性不一致；
- PR Head 保留完整精确列表，不是把断言放宽为 `version >= 11`；
- `StageAdvancementMigrationTest` 仍有意验证 v10→v11，不属于本次修改。

## 六、Room v12 与 Schema 静态门禁

核对：

```text
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Migration.kt
app/schemas/com.elio.jianyu.data.RoundtableDatabase/12.json
```

记录：

- Room `version = 12`；
- `ALL_MIGRATIONS` 精确为 `1→2` 至 `11→12`；
- `12.json.database.version = 12`；
- `identityHash = 933e93291334a0fe8a73fa6f7dc527c0`；
- Schema 文件 SHA-256；
- PR 差异未修改上述生产合同。

测试完成后执行：

```powershell
git diff --exit-code -- app/schemas
git status --short
```

不得保留 KSP 或 Room 自动生成差异。

## 七、证据目录与低 Token 工具

优先使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
```

把原始日志和 JUnit 副本保存到仓库外：

```powershell
$EvidenceRoot = Join-Path $env:TEMP (
  "jianyu-room-v12-baseline-" + (Get-Date -Format "yyyyMMdd-HHmmss")
)
New-Item -ItemType Directory -Path $EvidenceRoot -Force | Out-Null
```

每条命令必须记录：

- 完整命令；
- 开始/结束时间；
- 退出码；
- 测试总数、失败、错误、跳过；
- 原始日志路径；
- JUnit XML 路径。

不得只保留终端摘要后删除原始证据。

## 八、两个原失败类专项复验

先停止旧 Daemon：

```powershell
.\gradlew.bat --stop
```

逐类执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeMigrationTest `
  --stacktrace

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ResourceLifecycleMigrationTest `
  --stacktrace
```

必须确认并记录：

```text
ExecutionRuntimeMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion12
ResourceLifecycleMigrationTest#allMigrationsRemainContinuousFromVersion1ToVersion12
```

两项都应继续精确验证 11 个迁移步骤；同时同类中的 v7→v8、v6→v7、v5→v7 数据、索引、外键与幂等测试不得回归。

任一测试未实际执行，不能记为通过。

## 九、Room 与生命周期重点回归

至少逐类执行：

```powershell
$classes = @(
  "com.elio.jianyu.data.IssueLifecycleV12MigrationTest",
  "com.elio.jianyu.data.IssueLifecycleV12RepositoryDatabaseTest",
  "com.elio.jianyu.data.IssuePurgeDatabaseCleanerTest",
  "com.elio.jianyu.data.RoomJianyuRepositoryProcessRecoveryTest",
  "com.elio.jianyu.data.ArtifactSourceRecoveryDatabaseTest",
  "com.elio.jianyu.data.StageAdvancementMigrationTest",
  "com.elio.jianyu.data.StageAdvancementRepositoryDatabaseTest",
  "com.elio.jianyu.data.AudioAssetRepositoryDatabaseTest",
  "com.elio.jianyu.lifecycle.IssueArchiveCoordinatorDatabaseTest",
  "com.elio.jianyu.ui.screens.issues.IssueLifecycleUiTest"
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

重点核对：

- v1～v12 连续 Migration；
- v5～v12 committed Schema Migration；
- v11→v12 专项结构与数据保留；
- `PRAGMA foreign_key_check = 0`；
- Issue Lifecycle v12 Entity、表、索引和状态；
- Stage、Run、Message、Participant Snapshot；
- Draft、Artifact 与全部 Source；
- Material Usage 与 Personal Context Usage；
- AudioAsset；
- Stage Advancement；
- Archive、Resume、Relation、Purge Operation；
- 进程恢复和 UI 生命周期流程。

`StageAdvancementMigrationTest` 的 v10→v11 终点必须保持不变，不能把它的历史契约误判为旧基线失败。

## 十、完整构建与 JVM 验证

按顺序执行并记录退出码：

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:compileDebugKotlin --stacktrace
.\gradlew.bat :app:testDebugUnitTest --stacktrace
.\gradlew.bat :app:lintDebug --stacktrace
.\gradlew.bat :app:assembleDebug --stacktrace
.\gradlew.bat :app:assembleRelease --stacktrace
.\gradlew.bat :app:assembleDebugAndroidTest --stacktrace
```

必须严格区分：

- Kotlin 编译；
- JVM 单元测试；
- Lint；
- Debug APK；
- Release/R8；
- AndroidTest APK 编译。

`assembleDebugAndroidTest` 不能描述为设备 Instrumentation 通过。

## 十一、全量设备 Instrumentation

在两个原失败类和重点回归通过后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

必须从实际控制台输出和 JUnit XML 汇总：

```text
总测试数
通过数
失败数
错误数
跳过数
设备序列号
API Level
开始与结束时间
```

PR09-05C 的历史报告曾汇总“7 项 Room v11 基线失败”，但没有保存可复核的类/方法明细。此次验收必须以当前全量 JUnit XML 为准：

- 如果失败数为 0，记录真实总数并说明旧汇总已由当前 Head 重新验证；
- 如果仍有失败，逐项列出精确测试类、测试方法、第一条根因和是否属于本 PR；
- 不得为了凑成 7 项而重复计数、猜测文件或把历史 v10→v11 专项测试当失败；
- 任何 Schema mismatch、外键失败、数据丢失、生产异常或生命周期错误都按潜在生产缺陷处理，结论必须为 `FAIL`，不得建议修改断言掩盖。

## 十二、静态门禁与 Secret Scan

执行：

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1
```

执行仓库当前 Secret Scan 等价检查，并额外只读搜索：

```powershell
git grep -n -I -E "AIza[0-9A-Za-z_-]+|sk-[0-9A-Za-z_-]+|api[_-]?key\s*=|Authorization:\s*Bearer|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY" -- `
  app/src/main app/src/test app/src/androidTest docs
```

确认本 PR 没有引入密钥、Token、密码、用户正文、绝对路径或敏感数据。

## 十三、UIAutomator 与人工点检边界

本 PR 不改变生产 UI、自动化标签或交互，因此不要求新增 UIAutomator 场景。

如果执行既有外部 UIAutomator：

- 单独记录其命令和结果；
- 不得用它代替 Room Instrumentation；
- 不得使用固定坐标、OCR 或易变中文文案作为主要定位方式。

TalkBack、人工视觉和物理手势点检与本 PR 无直接变化；未执行时明确写“未验证”，不能影响 Room 设备基线的事实判定。

## 十四、工作区与 Head 终检

全部测试后执行：

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
- 无 KSP/Schema 残留；
- 本地 Head、远端 Head 与 `$expectedHead` 精确一致；
- 验收期间没有 Commit、Push、Ready、Merge 或分支变化。

## 十五、结论规则

只能输出以下之一：

```text
PASS
PASS_WITH_NOTES
FAIL
INSUFFICIENT_EVIDENCE
```

### PASS

仅当同时满足：

- 精确 Head 锁定；
- 差异只包含两个测试文件和两份文档；
- 生产代码、Migration、Schema 完全未变；
- 两个原失败类真实通过；
- Room v12 与生命周期重点类真实通过；
- 全量 `connectedDebugAndroidTest` 失败数为 0；
- JVM、Lint、Debug、Release、AndroidTest APK 全部真实通过；
- Secret Scan 与静态门禁通过；
- 工作区最终干净；
- GitHub CI 对同一 Head 全绿。

### PASS_WITH_NOTES

只用于不影响 Room 基线的非阻断事项，例如未做人工视觉/TalkBack；不得用于放行任何 JVM、Migration、Instrumentation、Schema、外键或 CI 失败。

### FAIL

任一实际测试、构建、Lint、Secret Scan、Schema、外键、生产行为或 Head 锁失败。

### INSUFFICIENT_EVIDENCE

缺少设备、全量 Instrumentation、JUnit XML、关键构建或同一 Head CI 证据，且没有已知失败时使用。

## 十六、失败报告格式

每个失败必须单独记录：

```text
测试类：
测试方法：
命令：
退出码：
第一条失败日志：
预期值：
实际值：
第一条根因：
是否由 v11→v12 基线变化直接导致：
是否涉及生产行为：
是否涉及数据丢失、Schema mismatch 或 foreign_key_check：
是否属于本 PR 可修测试合同：
建议后续：
```

只读验收 AI 不得自行修复。

## 十七、最终报告必须包含

1. 最终结论；
2. 仓库、Draft PR、Base、Branch、Expected/Actual/Remote Head；
3. 环境和设备版本；
4. 精确差异文件；
5. 生产文件、Migration 与 Schema 未变化证据；
6. 两个原失败类与方法结果；
7. Room v12 与生命周期重点类结果；
8. 全量 Instrumentation 总数与 JUnit XML 路径；
9. JVM、Lint、Debug、Release、AndroidTest APK；
10. Secret Scan 与静态门禁；
11. GitHub CI；
12. 工作区最终状态；
13. 尚未验证内容；
14. 风险和重点回归区域；
15. 是否允许远端开发对话建议 Ready；
16. PR09-13A 是否满足启动门禁。

即使最终 `PASS`，本地验收 AI 也不得自行标记 Ready、合并、删除分支或启动 PR09-13A；只把报告反馈给本远端开发对话。
