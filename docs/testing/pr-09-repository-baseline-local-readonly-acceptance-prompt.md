# 见域 Repository 基线设备测试续修：本地 AI 严格只读复验 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #37 最新 Head 进行第二轮严格只读验收。

目标分支：

```text
fix/pr-09-repository-baseline-instrumentation
```

本轮背景：

```text
第一轮本地验收 Head：6de37901b53ba2158c4f868c72c60e62c48d4b83
第一轮完整类：15/17 通过，2 失败
第一轮全量 Instrumentation：99/101 通过，2 失败
失败项：内存数据库关闭恢复、文件数据库关闭恢复
```

远端已基于该真实 RED 继续修改生产事务门禁和测试。你必须以远端分支当前最新 Head 为准，上一轮结果不得作为本轮 GREEN 证据。

## 一、严格只读纪律

你只能执行：

```text
读取
检出和同步
构建
安装与卸载测试包
运行测试
读取报告和日志
输出验收报告
```

严禁：

```text
修改任何源码、测试、文档、配置或 Schema
运行自动格式化
创建或修改 Commit
推送或强制推送
变基、合并、关闭 PR、标记 Ready
删除分支
修改 Git 配置
启动 PR09-07
以本地补丁代替反馈失败
```

发现问题时，只输出命令、退出码、设备信息、失败测试、关键堆栈、断言位置、复现步骤和根因线索。

## 二、记录环境

在 PowerShell 7 中执行并保留完整输出：

```powershell
Get-CimInstance Win32_OperatingSystem |
  Select-Object Caption, Version, BuildNumber, OSArchitecture

$PSVersionTable.PSVersion
git --version
java -version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

要求：

1. 使用 JDK 17；
2. 只能有一个目标 Android 设备在线；
3. 记录设备 ID、型号、Android 版本和 API Level；
4. 设备离线、多设备冲突或 JDK 错误时停止测试并报告。

设备信息命令：

```powershell
$DeviceLine = adb devices | Select-String "`tdevice$"
if ($DeviceLine.Count -ne 1) {
    throw "必须且只能有一个在线测试设备"
}
$Device = $DeviceLine.ToString().Split("`t")[0]

adb -s $Device shell getprop ro.product.model
adb -s $Device shell getprop ro.build.version.release
adb -s $Device shell getprop ro.build.version.sdk
```

## 三、同步并锁定最新远端 Head

```powershell
git fetch origin --prune
git checkout fix/pr-09-repository-baseline-instrumentation
git pull --ff-only origin fix/pr-09-repository-baseline-instrumentation

$ExpectedHead = git rev-parse origin/fix/pr-09-repository-baseline-instrumentation
$ActualHead = git rev-parse HEAD
$BaseHead = git rev-parse origin/main
$MergeBase = git merge-base HEAD origin/main

Write-Host "ExpectedHead=$ExpectedHead"
Write-Host "ActualHead=$ActualHead"
Write-Host "BaseHead=$BaseHead"
Write-Host "MergeBase=$MergeBase"

if ($ActualHead -ne $ExpectedHead) {
    throw "本地 HEAD 与远端目标分支不一致"
}
```

继续执行：

```powershell
git status --short
git branch --show-current
git log -12 --oneline --decorate
git diff --name-status origin/main...HEAD
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
```

门禁：

1. 分支必须精确为 `fix/pr-09-repository-baseline-instrumentation`；
2. `HEAD` 必须等于远端目标分支 Head；
3. 初始 `git status --short` 必须无输出；
4. Base 和 Merge Base 应为当前远端 `main`；
5. `git diff --check` 退出码必须为 0。

净差异只能涉及：

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
docs/planning/pr-09-repository-baseline-fix-plan.md
docs/testing/pr-09-repository-baseline-local-readonly-acceptance-prompt.md
```

不得出现：

```text
Entity
Migration
Database version
app/schemas/
RoundtableDatabase.kt
导航
Skill Catalog
ViewModel
Gemini
执行调度
资料、成果、音频、视觉或发布能力
```

若远端分支在验收期间发生变化，立即停止，记录旧 Head 和新 Head，不沿用旧结果。

## 四、读取规则与相关实现

完整读取：

```text
AGENTS.md
README.md
docs/planning/pr-09-jianyu-implementation-plan.md
docs/planning/pr-09-03-repository-recovery-plan.md
docs/planning/pr-09-03-interface-handoff.md
docs/planning/pr-09-repository-baseline-fix-plan.md
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
```

## 五、静态语义核验

### 5.1 Run 幂等语义

确认：

1. 合法冲突请求使用相同 `idempotencyKey` 和不同 Run ID；
2. 参与者 `runId` 与新 Run ID 一致；
3. 合法不同 payload 断言 `IdempotencyConflict`；
4. 独立关系错配断言 `ConstraintViolation`；
5. 错配请求不写入新 Run 或孤儿参与者；
6. 生产关系约束未删除或绕过。

### 5.2 数据库关闭门禁

确认 `JianyuRepositoryTransactions`：

1. 使用进程内原子状态记录数据库是否已真实进入过事务；
2. 初始未打开数据库仍允许首次惰性访问；
3. 原子状态在真实进入 `withTransaction` 后设置；
4. 已进入过事务且 `database.isOpen == false` 时，在再次进入 Room 前返回 `StorageFailure`；
5. 不通过反射访问 Room 私有字段；
6. 不修改 `RoundtableDatabase`；
7. 不捕获或转换 `CancellationException`；
8. 不改变 SQLite 约束和普通存储异常映射。

确认关闭测试：

1. 先通过同一个 Repository 执行一次 `recoverIssue()`；
2. 首次访问返回 `NotFound` 并使数据库真实打开；
3. 调用 `database.close()` 后 `isOpen == false`；
4. 第二次 Repository 调用返回 `StorageFailure`；
5. 第二次调用后数据库仍保持关闭，没有被自动重开；
6. 内存数据库和文件数据库均覆盖。

目标类静态预期仍为 17 个 `@Test` 方法，以最新源码和 Runner 实际输出为最终证据。

## 六、停止旧进程并清理构建输出

```powershell
.\gradlew.bat --stop
Write-Host "gradle_stop_exit=$LASTEXITCODE"

.\gradlew.bat :app:clean
Write-Host "clean_exit=$LASTEXITCODE"

git status --short
```

只允许清理 Gradle 构建产物，不运行 `git clean`，不删除未跟踪用户文件。

## 七、基础构建与 JVM 验证

逐条执行，记录开始时间、结束时间、退出码和关键摘要：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

要求：

- 每条命令单独记录结果；
- JVM 测试报告总数、通过、失败、跳过；
- Lint Error 和 Warning 数；
- Debug 与 Release APK 实际路径；
- 不以某一命令通过推断其他命令通过。

## 八、设备隔离

```powershell
adb -s $Device shell pm path com.elio.jianyu
adb -s $Device shell pm path com.elio.jianyu.test

adb -s $Device uninstall com.elio.jianyu.test
adb -s $Device uninstall com.elio.jianyu
```

包不存在时允许 `uninstall` 返回非零，但必须保留原始输出。

## 九、定向测试一：Run 幂等冲突

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -P"android.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest#runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict"

Write-Host "run_conflict_exit=$LASTEXITCODE"
```

要求：

```text
1 项执行
1 项通过
0 项失败
退出码 0
```

该项第一轮已经通过，但必须在最新 Head 上重新执行，不能复用旧结果。

## 十、定向测试二：内存数据库关闭

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -P"android.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest#closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue"

Write-Host "closed_memory_exit=$LASTEXITCODE"
```

必须记录：

- 测试统计；
- 退出码；
- 若失败，实际 `RepositoryError`；
- 关键堆栈与断言位置；
- 是否在第二次 Repository 调用后重新打开数据库。

完成条件：1/1 PASS，退出码 0。

## 十一、定向测试三：文件数据库关闭

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -P"android.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest#closedFileDatabaseReturnsStorageFailureInsteadOfEmptyIssue"

Write-Host "closed_file_exit=$LASTEXITCODE"
```

完成条件：1/1 PASS，退出码 0。

如当前 Runner 不接受 `Class#method`，只允许调整过滤参数的引号或等价 Runner 格式，不得修改测试源码。报告必须写出最终实际命令。

## 十二、其余边界测试

分别运行或在完整类中逐项确认：

```text
runParticipantRelationMismatchReturnsConstraintViolationWithoutWrites
openEmptyDatabaseReturnsNotFoundWithoutCreatingDomainRows
transactionExecutionMapsSQLiteExceptionToStorageFailure
transactionExecutionMapsSQLiteConstraintToConstraintViolation
transactionExecutionPropagatesCancellationException
foreignKeyCheckRemainsClean
```

每项都必须真实执行并通过。

重点确认：

- 首次空库访问仍为 `NotFound`；
- `CancellationException` 原样抛出；
- SQLite 错误映射未被关闭门禁覆盖；
- 外键检查为 0。

## 十三、完整 Repository 数据库测试类

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -P"android.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest"

Write-Host "repository_class_exit=$LASTEXITCODE"
```

要求：

```text
实际执行 17 项
通过 17 项
失败 0 项
跳过 0 项
退出码 0
```

若数量不是 17，必须对照源码逐项核对，不能直接放行。

## 十四、全量 Instrumentation

确保只有一个设备在线后执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
Write-Host "full_instrumentation_exit=$LASTEXITCODE"
```

必须记录：

```text
总数
通过
失败
跳过
退出码
设备 ID
报告路径
```

完成条件：

```text
失败数 = 0
退出码 = 0
```

上一轮为 101 项、99 通过、2 失败。本轮实际总数以最新 Head 为准，但两项关闭测试必须从失败变为通过。

不得使用：

```text
Base 既有失败
PASS WITH BASELINE FAILURES
已知失败不阻塞
```

重点回归：

- Repository 全部测试；
- 导航框架；
- Skill Catalog 检索和选择；
- DAO；
- Migration；
- 身份隔离；
- 进程恢复；
- 其他既有 Instrumentation。

## 十五、Room v7、Schema 与完整性

```powershell
Select-String -Path app\src\main\java\com\elio\jianyu\data\RoundtableDatabase.kt `
  -Pattern "version\s*=\s*7"

Get-ChildItem app\schemas -Recurse -Filter 8.json

git diff --exit-code origin/main...HEAD -- app/schemas
git status --short -- app/schemas
```

要求：

1. Room 继续为 v7；
2. 不存在 `8.json`；
3. `app/schemas/` 相对 `origin/main` 无差异；
4. 构建后没有生成未提交 Schema；
5. `foreignKeyCheckRemainsClean` 真实通过。

## 十六、Secret scan 与最终差异检查

```powershell
pwsh.exe -NoProfile -File .\tools\check-secrets.ps1 -IncludeHistory
Write-Host "secret_scan_exit=$LASTEXITCODE"

git diff --check origin/main...HEAD
git diff --name-status origin/main...HEAD
git status --short
git diff --exit-code
git diff --cached --exit-code
```

最终 Head：

```powershell
$FinalHead = git rev-parse HEAD
Write-Host "ExpectedHead=$ExpectedHead"
Write-Host "FinalHead=$FinalHead"

if ($FinalHead -ne $ExpectedHead) {
    throw "验收期间 HEAD 发生变化"
}
```

要求：

- Secret scan 退出码 0；
- `git diff --check` 退出码 0；
- 最终工作区无修改；
- 没有 staged 或 unstaged diff；
- 最终 Head 与开始时精确一致。

## 十七、最终报告格式

输出中文严格只读验收报告，至少包含：

1. 最终结论：`PASS` 或 `FAIL`；
2. 仓库、PR、分支、Base SHA、精确 Head SHA；
3. 操作系统、PowerShell、Git、JDK、Gradle、ADB、设备和 API Level；
4. 初始与最终工作区状态；
5. 净差异文件清单；
6. 事务关闭门禁静态核验；
7. `compileDebugKotlin`；
8. JVM 测试统计；
9. `compileDebugAndroidTestKotlin`；
10. Lint、Debug、Release；
11. Run 幂等冲突定向测试；
12. 内存数据库关闭定向测试；
13. 文件数据库关闭定向测试；
14. 其余边界测试；
15. 完整测试类 17 项统计；
16. 全量 Instrumentation 统计；
17. Room v7、Schema 和 `8.json`；
18. `PRAGMA foreign_key_check`；
19. 导航与 Skill Catalog 回归；
20. Secret scan 和 `git diff --check`；
21. 最终 Head 精确不变；
22. 尚未验证项；
23. 失败项、完整复现步骤和关键日志。

必须严格区分：

```text
已实际执行并通过
已实际执行但失败
GitHub CI 已通过
仅完成静态检查
尚未验证
```

只有以下条件全部满足才可输出 `PASS`：

```text
Run 幂等冲突通过
内存数据库关闭测试通过
文件数据库关闭测试通过
其余边界测试通过
完整测试类 17/17 通过
全量 connectedDebugAndroidTest 零失败
JVM、AndroidTest 编译、Lint、Debug、Release 全部通过
Room v7 且 Schema 无变化
foreign_key_check 无错误
导航和 Skill Catalog 无回归
工作区干净
Head 精确不变
```

即使验收 PASS，也不得标记 Ready、合并、删除分支或启动 PR09-07，必须等待用户明确授权。
