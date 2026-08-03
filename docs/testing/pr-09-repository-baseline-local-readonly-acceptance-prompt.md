# 见域 Repository 基线设备测试最终续修：本地 AI 严格只读复验 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #37 最新 Head 进行严格只读复验。

目标分支：

```text
fix/pr-09-repository-baseline-instrumentation
```

本轮背景：

```text
第一轮验收 Head：6de37901b53ba2158c4f868c72c60e62c48d4b83
Run 幂等冲突：PASS
内存数据库关闭测试：FAIL
文件数据库关闭测试：FAIL
完整类：15/17 通过
全量 Instrumentation：99/101 通过，2 失败
```

远端已根据上述真实 RED 继续修复。你必须以远端分支当前最新 Head 为准，上一轮结果不得复用为本轮 GREEN。

## 一、严格只读纪律

你只能执行：

```text
读取
同步和检出
构建
安装或卸载测试包
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

发现问题时，只输出命令、退出码、设备信息、失败测试、实际返回结果、关键堆栈、断言位置、复现步骤和根因线索。

## 二、记录环境

在 PowerShell 7 中执行并记录完整输出：

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
4. 设备离线、多设备冲突或 JDK 错误时停止并报告。

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

## 三、同步并锁定最新 Head

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
git log -16 --oneline --decorate
git diff --name-status origin/main...HEAD
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
```

门禁：

1. 分支必须精确为目标分支；
2. `HEAD` 必须等于远端目标分支 Head；
3. 初始工作区必须干净；
4. Merge Base 必须是当前 `origin/main`；
5. `git diff --check` 退出码为 0。

净差异只能涉及：

```text
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
docs/planning/pr-09-repository-baseline-fix-plan.md
docs/testing/pr-09-repository-baseline-local-readonly-acceptance-prompt.md
```

`RoundtableDatabase.kt` 只允许显式关闭生命周期标记相关变化。不得出现：

```text
Entity 变化
Migration 变化
version 变化
DAO 接口变化
数据库名变化
app/schemas/ 变化
导航或 Skill Catalog 变化
ViewModel、Gemini、执行调度变化
资料、成果、音频、视觉或发布变化
```

若远端分支在验收期间变化，立即停止并记录新旧 Head。

## 四、读取规则与相关文件

完整读取：

```text
AGENTS.md
README.md
docs/planning/pr-09-jianyu-implementation-plan.md
docs/planning/pr-09-03-repository-recovery-plan.md
docs/planning/pr-09-03-interface-handoff.md
docs/planning/pr-09-repository-baseline-fix-plan.md
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
```

## 五、静态语义核验

### 5.1 Run 幂等冲突

确认：

1. 合法冲突请求使用相同 `idempotencyKey` 和不同 Run ID；
2. 参与者全部指向新 Run；
3. 合法不同 payload 断言 `IdempotencyConflict`；
4. 关系错配独立断言 `ConstraintViolation`；
5. 错配不写入新 Run 或孤儿参与者；
6. 生产关系约束未删除或绕过。

### 5.2 数据库实例显式关闭状态

确认 `RoundtableDatabase.kt`：

1. 使用线程安全的进程内标记记录显式关闭；
2. 新建数据库实例初始不是显式关闭；
3. `close()` 在调用 `super.close()` 前设置显式关闭；
4. 标记一旦设置不会在同一实例上复位；
5. `version` 仍精确为 7；
6. Entity、Migration、DAO、数据库名称和 Schema 契约未改变。

### 5.3 唯一原始事务门禁

确认 `JianyuRepositoryTransactions.kt`：

1. `transactionRaw()` 在进入 `database.withTransaction` 前检查显式关闭；
2. 显式关闭时抛出内部存储不可用中止异常；
3. `execute(operation)` 将其映射为 `StorageFailure`；
4. operation 名称保持调用方实际操作名称；
5. 普通 `transaction()` 路径受保护；
6. `ResourceRepositoryComponent` 中先 `execute()`、再 `transactionRaw()` 的路径也受保护；
7. 不依赖反射或 Room 私有字段；
8. `CancellationException` 仍原样抛出；
9. SQLite 约束和普通存储异常映射未改变。

### 5.4 关闭测试

确认：

1. 打开空库测试在首次访问前 `isOpen == false`、显式关闭为 false；
2. 首次 Repository 恢复允许惰性打开并返回 `NotFound`；
3. 内存关闭测试在 Repository 首次访问前直接 `close()`；
4. 内存关闭测试随后返回 `StorageFailure`；
5. 调用后数据库仍保持关闭；
6. 文件库先正常打开并得到 `NotFound`；
7. 文件库显式关闭后恢复返回 `StorageFailure`；
8. 文件库调用后仍未重开。

目标测试类静态预期为 17 个 `@Test`，以最新源码和 Runner 实际输出为准。

## 六、停止进程并清理构建输出

```powershell
.\gradlew.bat --stop
Write-Host "gradle_stop_exit=$LASTEXITCODE"

.\gradlew.bat :app:clean
Write-Host "clean_exit=$LASTEXITCODE"

git status --short
```

只允许清理 Gradle 构建产物，不运行 `git clean`。

## 七、基础构建与 JVM 验证

逐条执行，分别记录开始时间、结束时间、退出码和摘要：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

报告：

```text
JVM 总数、通过、失败、跳过
Lint Error 和 Warning
Debug APK 路径
Release APK 路径
每条命令退出码
```

不得用一项通过推断其他项通过。

## 八、设备隔离

```powershell
adb -s $Device shell pm path com.elio.jianyu
adb -s $Device shell pm path com.elio.jianyu.test

adb -s $Device uninstall com.elio.jianyu.test
adb -s $Device uninstall com.elio.jianyu
```

包不存在时允许卸载返回非零，但必须记录原始输出。

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

即使上一轮已经通过，也必须在最新 Head 重跑。

## 十、定向测试二：首次访问前关闭内存数据库

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -P"android.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest#closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue"

Write-Host "closed_memory_exit=$LASTEXITCODE"
```

完成条件：

```text
1/1 PASS
退出码 0
实际错误为 StorageFailure
Repository 调用后数据库没有重新打开
```

若失败，必须记录实际 `RepositoryError`、关键堆栈和断言位置。

## 十一、定向测试三：使用后关闭文件数据库

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -P"android.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest#closedFileDatabaseReturnsStorageFailureInsteadOfEmptyIssue"

Write-Host "closed_file_exit=$LASTEXITCODE"
```

完成条件：

```text
1/1 PASS
退出码 0
关闭前空库为 NotFound
关闭后为 StorageFailure
第二次调用后数据库没有重新打开
```

如 Runner 不接受 `Class#method`，只允许调整过滤参数的引号或等价 Runner 格式，不得修改测试源码。

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

每项必须真实执行并通过。

重点确认：

- 首次空库访问仍为 `NotFound`；
- 取消原样传播；
- SQLite 错误映射未受影响；
- 外键检查为 0。

## 十三、完整 Repository 测试类

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

数量不是 17 时必须对照源码逐项核验，不能直接放行。

## 十四、全量 Instrumentation

```powershell
.\gradlew.bat connectedDebugAndroidTest
Write-Host "full_instrumentation_exit=$LASTEXITCODE"
```

记录：

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

上一轮为 101 项、99 通过、2 失败。本轮总数以最新 Head 为准，但两项关闭测试必须变为通过。

不得使用：

```text
Base 既有失败
PASS WITH BASELINE FAILURES
已知失败不阻塞
```

重点回归：

- Repository；
- 导航框架；
- Skill Catalog；
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
3. `app/schemas/` 无差异；
4. 构建后没有生成未提交 Schema；
5. `foreignKeyCheckRemainsClean` 真实通过；
6. `RoundtableDatabase.kt` 除关闭生命周期标记外没有数据库契约变化。

## 十六、Secret scan 与最终状态

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
- 最终 Head 与开始时一致。

## 十七、最终报告格式

输出中文严格只读验收报告，至少包含：

1. 最终结论：`PASS` 或 `FAIL`；
2. 仓库、PR、分支、Base SHA、精确 Head SHA；
3. 环境、工具、设备和 API Level；
4. 初始与最终工作区；
5. 净差异文件清单；
6. 数据库显式关闭标记静态核验；
7. 事务原始入口门禁静态核验；
8. `compileDebugKotlin`；
9. JVM 测试统计；
10. `compileDebugAndroidTestKotlin`；
11. Lint、Debug、Release；
12. Run 幂等定向测试；
13. 内存数据库关闭定向测试；
14. 文件数据库关闭定向测试；
15. 其余边界测试；
16. 完整测试类统计；
17. 全量 Instrumentation 统计；
18. Room v7、Schema 和 `8.json`；
19. `PRAGMA foreign_key_check`；
20. 导航与 Skill Catalog 回归；
21. Secret scan 和 `git diff --check`；
22. 最终 Head 一致性；
23. 尚未验证项；
24. 失败项、复现步骤和关键日志。

必须严格区分：

```text
已实际执行并通过
已实际执行但失败
GitHub CI 已通过
仅完成静态检查
尚未验证
```

只有以下全部满足才可输出 `PASS`：

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

即使 PASS，也不得标记 Ready、合并、删除分支或启动 PR09-07，等待用户明确授权。
